#!/usr/bin/env python3
"""Validate frozen v1 history and the versioned v2 offline reassessment."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from pathlib import Path

from reassess_recommendation_evaluation import build_reassessment


EXPECTED_QUERY_IDS = [
    *(f"S{i:02d}" for i in range(1, 11)),
    *(f"C{i:02d}" for i in range(1, 5)),
    *(f"R{i:02d}" for i in range(1, 4)),
    "N01", "N02", "A01",
]


def load_json(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def stable_key(product: dict) -> str:
    return f"{product['name']}|{product['brand']}"


def validate_grounding_rubric(rubric: dict, queries: dict, products: dict,
                              labels: dict, source_run: dict) -> list[str]:
    errors = []
    if rubric.get("schemaVersion") != 1 or rubric.get("rubricVersion") != "grounding-1.0":
        errors.append("unsupported grounding rubric version")
    review = rubric.get("review", {})
    if (review.get("reviewerType") != "assistant" or review.get("humanValidated") is not False
            or not review.get("method", "").strip()):
        errors.append("grounding rubric must preserve assistant review provenance")
    if set(rubric.get("labels", {})) != {"supported", "unsupported", "unjudged"}:
        errors.append("grounding rubric must define supported, unsupported and unjudged")

    query_by_id = {row["id"]: row["query"] for row in queries["queries"]}
    judgments = {row["queryId"]: {p["productKey"]: p["label"] for p in row["products"]}
                 for row in labels["judgments"]}
    source_results = {row["queryId"]: {stable_key(p): p for p in row["results"]}
                      for row in source_run["evaluations"]}
    cases = rubric.get("cases", [])
    case_ids = [row.get("caseId") for row in cases]
    if len(cases) != 5 or len(case_ids) != len(set(case_ids)):
        errors.append("grounding rubric must contain five unique cases")
    if sum(row.get("role") == "problem" for row in cases) != 3 or sum(
            row.get("role") == "contrast" for row in cases) != 2:
        errors.append("grounding rubric must retain three problem and two contrast cases")
    for case in cases:
        query_id = case.get("queryId")
        product_key = case.get("productKey")
        source = source_results.get(query_id, {}).get(product_key)
        if case.get("query") != query_by_id.get(query_id):
            errors.append(f"grounding query differs from the frozen query: {case.get('caseId')}")
        if product_key not in products:
            errors.append(f"unknown grounding product: {case.get('caseId')}")
        if judgments.get(query_id, {}).get(product_key) != case.get("relevanceBefore"):
            errors.append(f"grounding relevance differs from v2 label: {case.get('caseId')}")
        if source is None or source.get("reason") != case.get("reasonBefore"):
            errors.append(f"grounding reason differs from the source run: {case.get('caseId')}")
        if case.get("reasonLabelBefore") not in {"supported", "unsupported", "unjudged"}:
            errors.append(f"invalid grounding reason label: {case.get('caseId')}")
        if not case.get("requiredEvidence", "").strip() or not case.get("judgment", "").strip():
            errors.append(f"grounding case needs evidence and rationale: {case.get('caseId')}")
    return errors


def percentile_nearest_rank(values: list[int], percentile: float) -> int:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(percentile * len(ordered)) - 1)]


def calculate_metrics(baseline: dict, queries: dict, labels: dict, products: dict, fixtures: dict) -> dict:
    """Historical v1 formula; retain it to verify the original stored metrics."""
    label_by_id = {row["queryId"]: row for row in labels["judgments"]}
    baseline_by_id = {row["queryId"]: row for row in baseline["evaluations"]}
    fixture_by_id = fixtures["fixtures"]
    query_metrics = []
    strict_hits = 0
    accepted_hits = 0
    eligible_hits = 0
    violations = {"price": 0, "category": 0, "stock": 0, "recentOrder": 0}

    for query in queries["queries"]:
        query_id = query["id"]
        evaluation = baseline_by_id[query_id]
        judgment = label_by_id[query_id]
        result_keys = [stable_key(row) for row in evaluation["results"]]
        relevant = set(judgment["relevant"])
        accepted = relevant | set(judgment["acceptableAlternatives"])
        strict_hit = any(key in relevant for key in result_keys[:5])
        accepted_hit = any(key in accepted for key in result_keys[:5])
        if query["family"] in {"supported", "constraint", "rephrased"}:
            eligible_hits += 1
            strict_hits += strict_hit
            accepted_hits += accepted_hit

        query_violations = {name: 0 for name in violations}
        constraints = query.get("constraints", {})
        recent_keys = set()
        fixture_id = constraints.get("excludeRecentFixture")
        if fixture_id:
            recent_keys = {row["stableProductKey"] for row in fixture_by_id[fixture_id]["orders"]}

        if query["targetOutcome"] == "recommendations":
            for result in evaluation["results"]:
                key = stable_key(result)
                product = products[key]
                price = product["price"]
                if constraints.get("minPrice") is not None and price < constraints["minPrice"]:
                    query_violations["price"] += 1
                if constraints.get("maxPrice") is not None and price > constraints["maxPrice"]:
                    query_violations["price"] += 1
                categories = constraints.get("categories", [])
                if categories and product["category"] not in categories:
                    query_violations["category"] += 1
                if product["stock"] <= 0:
                    query_violations["stock"] += 1
                if key in recent_keys:
                    query_violations["recentOrder"] += 1

        for name, count in query_violations.items():
            violations[name] += count
        query_metrics.append({
            "queryId": query_id,
            "strictHitAt5": strict_hit if query["targetOutcome"] == "recommendations" else None,
            "acceptedHitAt5": accepted_hit if query["targetOutcome"] == "recommendations" else None,
            "conditionViolations": sum(query_violations.values()),
            "returnedHumanIrrelevant": sum(key in set(judgment["irrelevant"]) for key in result_keys),
        })

    latencies = [row["latencyMillis"] for row in baseline["evaluations"]]
    out_of_catalog = [baseline_by_id[query_id] for query_id in ("N01", "N02")]
    clarification = baseline_by_id["A01"]
    total_results = sum(row["resultCount"] for row in baseline["evaluations"])
    total_calls = sum(row["llmCallAttempts"] for row in baseline["evaluations"])
    return {
        "eligibleHitAt5Queries": eligible_hits,
        "strictHitAt5Count": strict_hits,
        "strictHitAt5Rate": round(strict_hits / eligible_hits, 6),
        "acceptedHitAt5Count": accepted_hits,
        "acceptedHitAt5Rate": round(accepted_hits / eligible_hits, 6),
        "conditionViolations": {**violations, "total": sum(violations.values())},
        "outOfCatalogQueriesReturningResults": sum(row["resultCount"] > 0 for row in out_of_catalog),
        "outOfCatalogFalsePositiveProducts": sum(row["resultCount"] for row in out_of_catalog),
        "clarificationFailures": int(clarification["outcome"] not in {"clarification", "CLARIFICATION_REQUIRED"}),
        "totalResultCount": total_results,
        "totalLlmCallAttempts": total_calls,
        "latencyMillis": {
            "total": sum(latencies),
            "median": percentile_nearest_rank(latencies, 0.5),
            "p95": percentile_nearest_rank(latencies, 0.95),
            "maximum": max(latencies),
        },
        "queries": query_metrics,
    }


def calculate_agent_metrics(run: dict, queries: dict, labels: dict, products: dict, fixtures: dict) -> dict:
    metrics = calculate_metrics(run, queries, labels, products, fixtures)
    evaluations = run["evaluations"]
    searches = [row["searchCount"] for row in evaluations]
    llm_calls = [row["llmCallAttempts"] for row in evaluations]
    total_tokens = [row["totalTokens"] for row in evaluations]
    eligible = [row for row in evaluations if row["family"] in {"supported", "constraint", "rephrased"}]
    completed_eligible = [row for row in eligible if row["outcome"] != "FAILED"]
    researched = [row for row in completed_eligible if row["searchCount"] == 2]
    query_metrics = {row["queryId"]: row for row in metrics["queries"]}
    metrics.update({
        "failedQueries": sum(row["outcome"] == "FAILED" for row in evaluations),
        "searchLimitViolations": sum(count > 2 for count in searches),
        "llmLimitViolations": sum(count > 3 for count in llm_calls),
        "duplicateSearchExecutions": sum(row["actionCode"] == "DUPLICATE_SEARCH_BLOCKED"
                                         and row["searchCount"] > 1 for row in evaluations),
        "firstSearchTermination": {
            "eligibleCompletedQueries": len(completed_eligible),
            "count": sum(row["searchCount"] <= 1 for row in completed_eligible),
            "rate": (round(sum(row["searchCount"] <= 1 for row in completed_eligible)
                           / len(completed_eligible), 6) if completed_eligible else None),
        },
        "research": {
            "queryCount": len(researched),
            "strictHitAt5Count": sum(bool(query_metrics[row["queryId"]]["strictHitAt5"])
                                     for row in researched),
            "acceptedHitAt5Count": sum(bool(query_metrics[row["queryId"]]["acceptedHitAt5"])
                                       for row in researched),
        },
        "searchCount": {
            "total": sum(searches),
            "average": round(sum(searches) / len(searches), 6),
            "maximum": max(searches),
        },
        "llmCallAttempts": {
            "total": sum(llm_calls),
            "average": round(sum(llm_calls) / len(llm_calls), 6),
            "maximum": max(llm_calls),
        },
        "tokenUsage": {
            "prompt": sum(row["promptTokens"] for row in evaluations),
            "completion": sum(row["completionTokens"] for row in evaluations),
            "total": sum(total_tokens),
            "averageTotal": round(sum(total_tokens) / len(total_tokens), 6),
        },
        "latencyMillis": {
            **metrics["latencyMillis"],
            "average": round(metrics["latencyMillis"]["total"] / len(evaluations), 6),
        },
    })
    return metrics


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--print-metrics", action="store_true")
    parser.add_argument("--write-reassessment", action="store_true",
                        help="write only v2/reassessment.json after validating all inputs")
    parser.add_argument("--write-agent-metrics", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--blocked-reason", help=argparse.SUPPRESS)
    parser.add_argument("--agent-commit", help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.write_agent_metrics or args.blocked_reason or args.agent_commit:
        parser.error("v1 execution artifacts are frozen; use --write-reassessment to write a separate v2 assessment")
    root = args.root.resolve()
    evaluation_dir = root / "evaluation" / "recommendation"
    manifest = load_json(evaluation_dir / "snapshot-manifest.json")
    queries = load_json(evaluation_dir / "queries.json")
    labels = load_json(evaluation_dir / "labels.json")
    fixtures = load_json(evaluation_dir / "fixtures.json")
    grounding = load_json(evaluation_dir / "grounding-v1.json")
    v2_labels = load_json(evaluation_dir / "v2" / "labels.json")
    baseline = load_json(evaluation_dir / "baselines" / "fixed-rag-79627c0.json")
    agent_runs = [load_json(path) for path in sorted((evaluation_dir / "runs").glob("*.json"))]
    errors: list[str] = []

    if manifest["totals"] != {"files": 8, "jsonProducts": 500}:
        errors.append("manifest totals must be exactly 8 files and 500 products")

    products: dict[str, dict] = {}
    item_total = 0
    for entry in manifest["files"]:
        path = root / entry["path"]
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        items = load_json(path)
        if digest != entry["sha256"]:
            errors.append(f"SHA-256 mismatch: {entry['path']}")
        if len(items) != entry["items"]:
            errors.append(f"item count mismatch: {entry['path']}")
        item_total += len(items)
        for product in items:
            key = stable_key(product)
            if key in products:
                errors.append(f"duplicate stable product key: {key}")
            products[key] = product
    if item_total != 500 or len(products) != 500:
        errors.append(f"snapshot product totals differ: items={item_total}, stableKeys={len(products)}")

    query_ids = [row["id"] for row in queries["queries"]]
    label_ids = [row["queryId"] for row in labels["judgments"]]
    baseline_ids = [row["queryId"] for row in baseline["evaluations"]]
    if query_ids != EXPECTED_QUERY_IDS:
        errors.append(f"query IDs/order differ: {query_ids}")
    if set(label_ids) != set(EXPECTED_QUERY_IDS) or len(label_ids) != len(set(label_ids)):
        errors.append("labels must cover each query ID exactly once")
    if set(baseline_ids) != set(EXPECTED_QUERY_IDS) or len(baseline_ids) != len(set(baseline_ids)):
        errors.append("baseline must cover each query ID exactly once")

    for judgment in labels["judgments"]:
        seen = set()
        for field in ("relevant", "acceptableAlternatives", "irrelevant"):
            for key in judgment[field]:
                if key not in products:
                    errors.append(f"unknown label key for {judgment['queryId']}: {key}")
                if key in seen:
                    errors.append(f"label classes overlap for {judgment['queryId']}: {key}")
                seen.add(key)
    for fixture_id, fixture in fixtures["fixtures"].items():
        for order in fixture["orders"]:
            if order["stableProductKey"] not in products:
                errors.append(f"unknown fixture key for {fixture_id}: {order['stableProductKey']}")

    query_text_by_id = {row["id"]: row["query"] for row in queries["queries"]}
    for evaluation in baseline["evaluations"]:
        if evaluation["query"] != query_text_by_id.get(evaluation["queryId"]):
            errors.append(f"baseline query text mismatch: {evaluation['queryId']}")
        if evaluation["resultCount"] != len(evaluation["results"]):
            errors.append(f"baseline resultCount mismatch: {evaluation['queryId']}")
        ids = [row["productId"] for row in evaluation["results"]]
        if len(ids) != len(set(ids)):
            errors.append(f"duplicate baseline product ID: {evaluation['queryId']}")
        for result in evaluation["results"]:
            key = stable_key(result)
            if key not in products:
                errors.append(f"unknown baseline result key for {evaluation['queryId']}: {key}")
            elif (result["price"], result["category"]) != (products[key]["price"], products[key]["category"]):
                errors.append(f"baseline product facts differ from snapshot: {evaluation['queryId']} {key}")

    for run in agent_runs:
        run_ids = [row["queryId"] for row in run["evaluations"]]
        if run_ids != EXPECTED_QUERY_IDS:
            errors.append(f"agent run query IDs/order differ: {run_ids}")
        runtime = run["runtimeSnapshot"]
        if runtime["mysqlProducts"] != 500 or runtime["pgvectorRows"] != 500:
            errors.append("agent runtime snapshot must contain 500 MySQL and 500 pgvector rows")
        if runtime["sortedCommaSeparatedProductIdsSha256"] != manifest["mysqlSnapshot"]["sortedCommaSeparatedProductIdsSha256"]:
            errors.append("agent runtime MySQL product ID digest differs from manifest")
        for evaluation in run["evaluations"]:
            if evaluation["query"] != query_text_by_id.get(evaluation["queryId"]):
                errors.append(f"agent query text mismatch: {evaluation['queryId']}")
            if evaluation["resultCount"] != len(evaluation["results"]):
                errors.append(f"agent resultCount mismatch: {evaluation['queryId']}")
            if evaluation["searchCount"] > 2 or evaluation["llmCallAttempts"] > 3:
                errors.append(f"agent execution limit exceeded: {evaluation['queryId']}")
            ids = [row["productId"] for row in evaluation["results"]]
            if len(ids) != len(set(ids)):
                errors.append(f"duplicate agent product ID: {evaluation['queryId']}")
            for result in evaluation["results"]:
                key = stable_key(result)
                if key not in products:
                    errors.append(f"unknown agent result key for {evaluation['queryId']}: {key}")
                    continue
                if (result["price"], result["category"]) != (products[key]["price"], products[key]["category"]):
                    errors.append(f"agent product facts differ from snapshot: {evaluation['queryId']} {key}")
                expected_evidence = f"product:{result['productId']}"
                if result["evidenceIds"] != [expected_evidence]:
                    errors.append(f"agent evidence differs: {evaluation['queryId']} {expected_evidence}")

        agent_metrics = calculate_agent_metrics(run, queries, labels, products, fixtures)
        if run.get("metrics") != agent_metrics:
            errors.append(f"stored agent metrics differ: {run.get('agentCommit')}")

    grounding_source = next((run for run in agent_runs
                             if run.get("agentCommit", "").startswith("fd7971a")), None)
    if grounding_source is None:
        errors.append("grounding source run is missing")
    else:
        errors.extend(validate_grounding_rubric(
            grounding, queries, products, v2_labels, grounding_source))

    serialized = json.dumps(
        [manifest, queries, labels, fixtures, grounding, baseline, agent_runs], ensure_ascii=False)
    if re.search(r"[A-Za-z]:[/\\]|(?:api[_-]?key|jwt[_-]?secret)\s*[:=]", serialized, re.IGNORECASE):
        errors.append("evaluation artifacts contain a local absolute path or secret-like assignment")

    metrics = calculate_metrics(baseline, queries, labels, products, fixtures)
    if baseline.get("metrics") != metrics:
        errors.append("stored baseline metrics differ from deterministic recomputation")

    reassessment = None
    output = evaluation_dir / "v2" / "reassessment.json"
    try:
        reassessment = build_reassessment(root, products, fixtures)
        if not args.write_reassessment and (not output.exists() or load_json(output) != reassessment):
            errors.append("stored v2 reassessment differs; review inputs and run --write-reassessment")
    except (ValueError, KeyError, TypeError, OSError) as error:
        errors.append(f"v2 reassessment: {error}")
    if errors:
        print("Evaluation validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    if args.write_reassessment:
        with output.open("w", encoding="utf-8", newline="\n") as handle:
            handle.write(json.dumps(reassessment, ensure_ascii=False, indent=2) + "\n")
    if args.print_metrics:
        print(json.dumps(reassessment, ensure_ascii=False, indent=2))
    print("Recommendation evaluation valid: 8 files, 500 products, 20 queries; v1 preserved and v2 verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
