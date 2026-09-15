#!/usr/bin/env python3
"""Validate the frozen recommendation evaluation set and recompute baseline metrics."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from pathlib import Path


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


def percentile_nearest_rank(values: list[int], percentile: float) -> int:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(percentile * len(ordered)) - 1)]


def calculate_metrics(baseline: dict, queries: dict, labels: dict, products: dict, fixtures: dict) -> dict:
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
        "clarificationFailures": int(clarification["outcome"] != "clarification"),
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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--print-metrics", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    evaluation_dir = root / "evaluation" / "recommendation"
    manifest = load_json(evaluation_dir / "snapshot-manifest.json")
    queries = load_json(evaluation_dir / "queries.json")
    labels = load_json(evaluation_dir / "labels.json")
    fixtures = load_json(evaluation_dir / "fixtures.json")
    baseline = load_json(evaluation_dir / "baselines" / "fixed-rag-79627c0.json")
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

    serialized = json.dumps([manifest, queries, labels, fixtures, baseline], ensure_ascii=False)
    if re.search(r"[A-Za-z]:[/\\]|(?:api[_-]?key|jwt[_-]?secret)\s*[:=]", serialized, re.IGNORECASE):
        errors.append("evaluation artifacts contain a local absolute path or secret-like assignment")

    metrics = calculate_metrics(baseline, queries, labels, products, fixtures)
    if baseline.get("metrics") != metrics:
        errors.append("stored baseline metrics differ from deterministic recomputation")

    if args.print_metrics:
        print(json.dumps(metrics, ensure_ascii=False, indent=2))
    if errors:
        print("Evaluation validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("Recommendation evaluation snapshot valid: 8 files, 500 products, 20 queries.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
