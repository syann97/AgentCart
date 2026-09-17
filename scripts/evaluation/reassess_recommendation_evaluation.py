"""Version 2 offline scoring. Never updates a source run or version 1 judgment."""

from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path


METRIC_VERSION = "2.0"
GRADES = ("relevant", "acceptable", "irrelevant", "unjudged")
RULES = ("price", "category", "stock", "recentOrder", "status")


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def content_sha256(path: Path) -> str:
    # Git's Windows checkout may change line endings, but not the frozen content.
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def source_path(root: Path, relative: str) -> Path:
    path = (root / relative).resolve()
    if Path(relative).is_absolute():
        raise ValueError(f"source must be inside the repository: {relative}")
    try:
        path.relative_to(root.resolve())
    except ValueError:
        raise ValueError(f"source must be inside the repository: {relative}") from None
    return path


def stable_key(product: dict) -> str:
    return f"{product['name']}|{product['brand']}"


def ratio(numerator: int, denominator: int):
    return round(numerator / denominator, 6) if denominator else None


def relevance_summary(counts: dict) -> dict:
    returned = sum(counts.values())
    judged = returned - counts["unjudged"]
    accepted = counts["relevant"] + counts["acceptable"]
    return {
        **counts,
        "returned": returned,
        "judged": judged,
        "accepted": accepted,
        "judgedAcceptedRate": ratio(accepted, judged),
        "acceptedShareLowerBound": ratio(accepted, returned),
        "acceptedShareUpperBound": ratio(accepted + counts["unjudged"], returned),
        "unjudgedRate": ratio(counts["unjudged"], returned),
    }


def hit_at_five(rows: list[dict], grades: set[str], require_policy: bool = False):
    eligible = [row for row in rows[:5] if not require_policy or not any(row["violations"].values())]
    if any(row["label"] in grades for row in eligible):
        return True
    if any(row["label"] == "unjudged" for row in eligible):
        return None
    return False


def hit_summary(values: list) -> dict:
    hits = sum(value is True for value in values)
    unknown = sum(value is None for value in values)
    return {
        "hits": hits,
        "misses": sum(value is False for value in values),
        "unjudged": unknown,
        "eligibleQueries": len(values),
        "rateLowerBound": ratio(hits, len(values)),
        "rateUpperBound": ratio(hits + unknown, len(values)),
    }


def product_policy(product: dict, constraints: dict, fixtures: dict, policies: dict) -> dict:
    minimum = constraints.get("minPrice", {}).get("value")
    maximum = constraints.get("maxPrice", {}).get("value")
    categories = constraints.get("categories", {}).get("value", [])
    fixture_id = constraints.get("excludeRecentFixture", {}).get("value")
    recent = {row["stableProductKey"] for row in fixtures["fixtures"][fixture_id]["orders"]} if fixture_id else set()
    return {
        "price": int((minimum is not None and product["price"] < minimum)
                     or (maximum is not None and product["price"] > maximum)),
        "category": int(bool(categories) and product["category"] not in categories),
        "stock": int(policies["positiveStock"] and product["stock"] <= 0),
        "recentOrder": int(stable_key(product) in recent),
        "status": int(product.get("status") is not None and product["status"] != policies["requiredStatus"]),
    }


def score_run(run: dict, definition: dict, labels: dict, products: dict, fixtures: dict) -> dict:
    evaluations = {row["queryId"]: row for row in run["evaluations"]}
    judgments = {row["queryId"]: {p["productKey"]: p for p in row["products"]}
                 for row in labels["judgments"]}
    query_metrics = []
    for query in definition["queries"]:
        evaluation = evaluations[query["id"]]
        expected = query["expectedRelatedCategories"]["values"]
        rows = []
        for result in evaluation["results"]:
            key = stable_key(result)
            product = products[key]
            judgment = judgments.get(query["id"], {}).get(key)
            rows.append({
                "productKey": key,
                "label": judgment["label"] if judgment else "unjudged",
                "violations": product_policy(product, query["explicitConstraints"], fixtures, definition["policies"]),
                "expectedCategoryMismatch": bool(expected) and product["category"] not in expected,
                "activeStatusUnknown": product.get("status") is None,
            })
        counts = {grade: sum(row["label"] == grade for row in rows) for grade in GRADES}
        eligible = query["targetOutcome"] == "recommendations"
        query_metrics.append({
            "queryId": query["id"],
            "hitEligible": eligible,
            "strictHitAt5": hit_at_five(rows, {"relevant"}) if eligible else None,
            "acceptedHitAt5": hit_at_five(rows, {"relevant", "acceptable"}) if eligible else None,
            "observedPolicyCompliantAcceptedHitAt5": hit_at_five(rows, {"relevant", "acceptable"}, True) if eligible else None,
            "relevance": relevance_summary(counts),
            "policyViolations": {rule: sum(row["violations"][rule] for row in rows) for rule in RULES},
            "expectedCategoryMismatches": sum(row["expectedCategoryMismatch"] for row in rows),
            "activeStatusUnknownProducts": sum(row["activeStatusUnknown"] for row in rows),
            "products": rows,
        })
    totals = {grade: sum(row["relevance"][grade] for row in query_metrics) for grade in GRADES}
    violations = {rule: sum(row["policyViolations"][rule] for row in query_metrics) for rule in RULES}
    latencies = sorted(row["latencyMillis"] for row in run["evaluations"])

    def optional_sum(field: str):
        return (sum(row[field] for row in run["evaluations"])
                if all(row.get(field) is not None for row in run["evaluations"]) else None)

    return {
        "hitAt5": {key: hit_summary([row[key] for row in query_metrics if row["hitEligible"]])
                   for key in ("strictHitAt5", "acceptedHitAt5", "observedPolicyCompliantAcceptedHitAt5")},
        "relevance": relevance_summary(totals),
        "policyViolations": {**violations, "total": sum(violations.values())},
        "expectedCategoryMismatches": sum(row["expectedCategoryMismatches"] for row in query_metrics),
        "activeStatusUnknownProducts": sum(row["activeStatusUnknownProducts"] for row in query_metrics),
        "outOfCatalogQueriesReturningProducts": sum(bool(evaluations[q["id"]]["results"])
                                                     for q in definition["queries"] if q["targetOutcome"] == "empty"),
        "outOfCatalogProducts": sum(len(evaluations[q["id"]]["results"])
                                    for q in definition["queries"] if q["targetOutcome"] == "empty"),
        "clarificationFailures": sum(evaluations[q["id"]]["outcome"] not in {"clarification", "CLARIFICATION_REQUIRED"}
                                     or bool(evaluations[q["id"]]["results"])
                                     for q in definition["queries"] if q["targetOutcome"] == "clarification"),
        "execution": {
            "resultCount": totals["relevant"] + totals["acceptable"] + totals["irrelevant"] + totals["unjudged"],
            "llmCallAttempts": optional_sum("llmCallAttempts"),
            "searchCount": optional_sum("searchCount"),
            "promptTokens": optional_sum("promptTokens"),
            "completionTokens": optional_sum("completionTokens"),
            "totalTokens": optional_sum("totalTokens"),
            "latencyMillis": {
                "average": ratio(sum(latencies), len(latencies)),
                "p95": latencies[math.ceil(0.95 * len(latencies)) - 1] if latencies else None,
                "maximum": max(latencies) if latencies else None,
            },
        },
        "queries": query_metrics,
    }


def validate_inputs(root: Path, definition: dict, labels: dict, products: dict, fixtures: dict) -> dict:
    errors = []
    if definition.get("schemaVersion") != 2 or definition.get("metricVersion") != METRIC_VERSION:
        errors.append("unsupported v2 schema/metric version")
    if (labels.get("schemaVersion") != 2 or not labels.get("labelVersion")
            or labels.get("definitionVersion") != definition.get("definitionVersion")
            or labels.get("snapshotId") != definition.get("snapshotId")):
        errors.append("label version/snapshot does not match the definition")
    history = {row["path"]: row["sha256"] for row in definition["preservedHistory"]}
    required_history = {
        "evaluation/recommendation/queries.json", "evaluation/recommendation/labels.json",
        "evaluation/recommendation/snapshot-manifest.json", "evaluation/recommendation/fixtures.json",
        "evaluation/recommendation/AGENTIC_RAG_EVALUATION_2026-09-16.md",
        *(row["path"] for row in definition["runs"]),
    }
    if not required_history.issubset(history) or len(history) != len(definition["preservedHistory"]):
        errors.append("preserved history must uniquely cover the v1 inputs, report and all runs")
    for relative, expected_hash in history.items():
        if content_sha256(source_path(root, relative)) != expected_hash:
            errors.append(f"preserved history changed: {relative}")
    old_queries = load_json(root / "evaluation/recommendation/queries.json")["queries"]
    fields = ("id", "family", "query", "targetOutcome")
    if ([tuple(row[field] for field in fields) for row in definition["queries"]]
            != [tuple(row[field] for field in fields) for row in old_queries]):
        errors.append("v2 must retain the 20 original query texts, order and target outcomes")
    known_categories = {p["category"] for p in products.values()}
    if (definition["policies"].get("source") != "APPLICATION_POLICY"
            or definition["policies"].get("positiveStock") is not True
            or definition["policies"].get("requiredStatus") != "ACTIVE"
            or definition["policies"].get("recentOrderWindowDays") != 7):
        errors.append("application policy must retain positive stock, ACTIVE and the 7-day window")
    for query in definition["queries"]:
        expected = query["expectedRelatedCategories"]
        if (expected.get("source") != "SCENARIO_EXPECTATION" or expected.get("hardConstraint") is not False
                or not set(expected["values"]).issubset(known_categories)):
            errors.append(f"invalid related category annotation: {query['id']}")
        intent = query["intent"]
        if (intent.get("source") != "QUERY_INTENT" or not intent.get("criterion", "").strip()
                or not intent.get("evidence") or intent["evidence"] not in query["query"]):
            errors.append(f"intent needs verbatim query evidence: {query['id']}")
        constraints = query["explicitConstraints"]
        for field, constraint in constraints.items():
            if (constraint.get("source") != "EXPLICIT" or not constraint.get("evidence")
                    or constraint["evidence"] not in query["query"]):
                errors.append(f"explicit condition needs verbatim query evidence: {query['id']} {field}")
            value = constraint.get("value")
            if field in {"minPrice", "maxPrice"}:
                if type(value) is not int or value < 0:
                    errors.append(f"invalid price annotation: {query['id']}")
            elif field == "categories":
                if not isinstance(value, list) or not value or not set(value).issubset(known_categories):
                    errors.append(f"invalid explicit categories: {query['id']}")
            elif field == "excludeRecentFixture":
                if value not in fixtures["fixtures"] or fixtures["fixtures"][value]["recentWindowDays"] != 7:
                    errors.append(f"invalid recent order fixture: {query['id']}")
            else:
                errors.append(f"unknown explicit condition: {query['id']} {field}")
    query_ids = [q["id"] for q in definition["queries"]]
    if [row["queryId"] for row in labels["judgments"]] != query_ids:
        errors.append("v2 labels must cover every query once in query order")
    if (not labels["review"].get("reviewer") or not labels["review"].get("reviewerType")
            or type(labels["review"].get("humanValidated")) is not bool):
        errors.append("label review provenance is required")
    reviewed_pairs = set()
    for judgment in labels["judgments"]:
        for row in judgment["products"]:
            pair = (judgment["queryId"], row["productKey"])
            if pair in reviewed_pairs:
                errors.append(f"duplicate v2 label: {pair}")
            reviewed_pairs.add(pair)
            if (row["productKey"] not in products or row["label"] not in GRADES
                    or not row.get("reason", "").strip()):
                errors.append(f"invalid v2 label or missing reason: {pair}")
    runs = {}
    pool_pairs = set()
    included = []
    for source in definition["runs"]:
        if source["id"] in runs:
            errors.append(f"duplicate input run: {source['id']}")
        run = load_json(source_path(root, source["path"]))
        runs[source["id"]] = run
        if run.get("snapshotId") != definition["snapshotId"]:
            errors.append(f"run snapshot mismatch: {source['id']}")
        if source["includeInQualityComparison"]:
            included.append(source["id"])
            if run.get("evaluationStatus") == "blocked" or any(e["outcome"] == "FAILED" for e in run["evaluations"]):
                errors.append(f"blocked/failed run cannot enter quality comparison: {source['id']}")
            pool_pairs.update((e["queryId"], stable_key(p)) for e in run["evaluations"] for p in e["results"])
        elif not source.get("exclusionReason"):
            errors.append(f"excluded run needs a reason: {source['id']}")
    if set(included) != set(labels["review"]["poolRunIds"]):
        errors.append("review pool must match the compared runs")
    if reviewed_pairs != pool_pairs:
        errors.append(f"review every pooled query/product pair (missing={len(pool_pairs - reviewed_pairs)}, extra={len(reviewed_pairs - pool_pairs)})")
    if errors:
        raise ValueError("\n".join(errors))
    return runs


def build_reassessment(root: Path, products: dict, fixtures: dict) -> dict:
    evaluation_dir = root / "evaluation/recommendation/v2"
    definition = load_json(evaluation_dir / "definition.json")
    labels = load_json(evaluation_dir / "labels.json")
    runs = validate_inputs(root, definition, labels, products, fixtures)
    assessments = []
    for source in definition["runs"]:
        run = runs[source["id"]]
        assessment = {
            "runId": source["id"],
            "source": source["path"],
            "sourceSha256": content_sha256(source_path(root, source["path"])),
            "sourceCommit": run.get("agentCommit", run.get("sourceCommit")),
            "capturedAt": run["capturedAt"],
            "chatModelConfigured": run.get("chatModelConfigured", run.get("chatModel")),
            "responseModels": sorted({model for e in run["evaluations"] for model in e.get("chatModels", [])}),
            "includedInQualityComparison": source["includeInQualityComparison"],
        }
        if source["includeInQualityComparison"]:
            assessment["metrics"] = score_run(run, definition, labels, products, fixtures)
        else:
            assessment["exclusionReason"] = source["exclusionReason"]
            assessment["failedQueries"] = sum(e["outcome"] == "FAILED" for e in run["evaluations"])
            assessment["metrics"] = None
        assessments.append(assessment)
    return {
        "schemaVersion": 2,
        "kind": "offline-reassessment",
        "definitionVersion": definition["definitionVersion"],
        "labelVersion": labels["labelVersion"],
        "metricVersion": METRIC_VERSION,
        "snapshotId": definition["snapshotId"],
        "definitionSha256": content_sha256(evaluation_dir / "definition.json"),
        "labelsSha256": content_sha256(evaluation_dir / "labels.json"),
        "scorerSha256": content_sha256(root / "scripts/evaluation/reassess_recommendation_evaluation.py"),
        "review": labels["review"],
        "comparisonLimits": [
            "서로 다른 chat 모델의 단회 실행 비교이며 pipeline 변경만의 인과 효과가 아니다.",
            "Labels are an assistant review of the pooled returned products, not a human-validated full-catalog ground truth.",
            definition["policies"]["evidenceLimit"],
        ],
        "runs": assessments,
    }
