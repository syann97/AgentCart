#!/usr/bin/env python3
"""Validate one immutable schema-v2 raw run and write a separate assessment."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
from datetime import datetime
from decimal import Decimal
from pathlib import Path

from reassess_recommendation_evaluation import (
    RULES, content_sha256, hit_summary, load_json, ratio, score_run, stable_key,
)


METRIC_VERSION = "2.1-observed-policy"
RUN_ID_PATTERN = re.compile(r"agentic-rag-([0-9a-f]{7,40})-(\d{8}T\d{6}Z)-r([1-9]\d*)")
OBSERVATION_SOURCE = "POST_RESPONSE_DATABASE_READ"


def parse_timestamp(value: str) -> datetime:
    """Parse ISO-8601 timestamps on Python versions that do not accept a trailing Z."""
    if isinstance(value, str) and value.endswith("Z"):
        value = value[:-1] + "+00:00"
    parsed = datetime.fromisoformat(value)
    if parsed.utcoffset() is None:
        raise ValueError("timestamp must include a UTC offset")
    return parsed


def normalized_price(value) -> str:
    decimal = Decimal(str(value))
    text = format(decimal, "f")
    return text.rstrip("0").rstrip(".") if "." in text else text


def catalog_fingerprint(products: dict[str, dict]) -> str:
    digest = hashlib.sha256()
    for key in sorted(products):
        product = products[key]
        fields = (product["name"], product.get("brand") or "", product.get("description") or "",
                  normalized_price(product["price"]), product["category"])
        for field in fields:
            encoded = str(field).encode("utf-8")
            digest.update(struct.pack(">I", len(encoded)))
            digest.update(encoded)
    return digest.hexdigest()


def load_products(root: Path, manifest_path: Path) -> tuple[dict[str, dict], dict]:
    manifest = load_json(manifest_path)
    products = {}
    for entry in manifest["files"]:
        product_path = root / entry["path"]
        if content_sha256(product_path) != entry["sha256"]:
            raise ValueError(f"snapshot file hash differs from manifest: {entry['path']}")
        file_products = load_json(product_path)
        if len(file_products) != entry["items"]:
            raise ValueError(f"snapshot file item count differs from manifest: {entry['path']}")
        for product in file_products:
            key = stable_key(product)
            if key in products:
                raise ValueError(f"duplicate snapshot product key: {key}")
            products[key] = product
    if len(products) != manifest["totals"]["jsonProducts"]:
        raise ValueError("snapshot product total differs from manifest")
    return products, manifest


def validate_run(run: dict, definition: dict, products: dict, manifest: dict) -> None:
    errors = []
    match = RUN_ID_PATTERN.fullmatch(str(run.get("runId", "")))
    commit = str(run.get("agentCommit", ""))
    if run.get("schemaVersion") != 2 or run.get("kind") != "real-model-agentic-rag-raw-run":
        errors.append("raw run must use schemaVersion 2 and the raw-run kind")
    if not match or not commit or not commit.startswith(match.group(1)):
        errors.append("runId must include the target commit, UTC capture time and positive repetition")
    if "metrics" in run:
        errors.append("raw run must not contain derived metrics")
    try:
        parse_timestamp(run["capturedAt"])
    except (KeyError, TypeError, ValueError):
        errors.append("capturedAt must be an offset-aware ISO-8601 timestamp")
    if run.get("snapshotId") != definition["snapshotId"] or run.get("snapshotId") != manifest["snapshotId"]:
        errors.append("raw run snapshot does not match definition and manifest")
    catalog = run.get("catalogSnapshot", {})
    if (catalog.get("source") != "MYSQL_PRE_RUN_READ"
            or catalog.get("immutableFactsSha256") != catalog_fingerprint(products)
            or catalog.get("productCount") != len(products)):
        errors.append("raw run immutable catalog facts do not match the frozen snapshot")

    queries = definition["queries"]
    evaluations = run.get("evaluations")
    if not isinstance(evaluations, list) or len(evaluations) != len(queries):
        errors.append("raw run must contain every definition query once in order")
        evaluations = []
    elif [(e.get("queryId"), e.get("query")) for e in evaluations] != [
            (q["id"], q["query"]) for q in queries]:
        errors.append("raw run query IDs, text or order differ from the definition")

    for evaluation in evaluations:
        results = evaluation.get("results")
        if not isinstance(results, list):
            errors.append(f"results must be an array: {evaluation.get('queryId')}")
            continue
        if evaluation.get("resultCount") != len(results) or len(results) > 5:
            errors.append(f"invalid result count: {evaluation.get('queryId')}")
        if evaluation.get("searchCount", 0) > 2 or evaluation.get("llmCallAttempts", 0) > 3:
            errors.append(f"execution limit exceeded: {evaluation.get('queryId')}")
        seen_ids = set()
        for result in results:
            key = stable_key(result)
            product = products.get(key)
            product_id = result.get("productId")
            if product_id in seen_ids:
                errors.append(f"duplicate product ID: {evaluation.get('queryId')} {product_id}")
            seen_ids.add(product_id)
            if product is None:
                errors.append(f"unknown result product: {evaluation.get('queryId')} {key}")
                continue
            for field in ("name", "brand", "description", "category"):
                if result.get(field) != product.get(field):
                    errors.append(f"result fact differs: {evaluation.get('queryId')} {key} {field}")
            try:
                if normalized_price(result.get("price")) != normalized_price(product["price"]):
                    errors.append(f"result fact differs: {evaluation.get('queryId')} {key} price")
            except (ValueError, TypeError):
                errors.append(f"invalid result price: {evaluation.get('queryId')} {key}")
            observation = result.get("policyObservation")
            if observation is None:
                continue
            if (not isinstance(observation, dict) or observation.get("source") != OBSERVATION_SOURCE
                    or observation.get("productId") != product_id
                    or observation.get("productKey") != key):
                errors.append(f"invalid policy observation identity: {evaluation.get('queryId')} {key}")
                continue
            try:
                parse_timestamp(observation["observedAt"])
            except (KeyError, TypeError, ValueError):
                errors.append(f"invalid policy observation time: {evaluation.get('queryId')} {key}")
            if observation.get("status") is not None and not isinstance(observation["status"], str):
                errors.append(f"invalid observed status: {evaluation.get('queryId')} {key}")
            if observation.get("stock") is not None and type(observation["stock"]) is not int:
                errors.append(f"invalid observed stock: {evaluation.get('queryId')} {key}")
    if errors:
        raise ValueError("\n".join(errors))


def apply_observed_policy(metrics: dict, run: dict, definition: dict) -> dict:
    evaluations = {row["queryId"]: row for row in run["evaluations"]}
    totals = {"statusKnown": 0, "statusUnknown": 0, "stockKnown": 0, "stockUnknown": 0}
    violations = dict(metrics["policyViolations"])
    violations["status"] = 0
    violations["stock"] = 0
    policy_hits = []
    query_metrics = {row["queryId"]: row for row in metrics["queries"]}

    for query in definition["queries"]:
        query_metric = query_metrics[query["id"]]
        result_by_key = {stable_key(row): row for row in evaluations[query["id"]]["results"]}
        for row in query_metric["products"]:
            observation = result_by_key[row["productKey"]].get("policyObservation") or {}
            status = observation.get("status")
            stock = observation.get("stock")
            totals["statusKnown" if status is not None else "statusUnknown"] += 1
            totals["stockKnown" if stock is not None else "stockUnknown"] += 1
            row["violations"]["status"] = int(status is not None and status != definition["policies"]["requiredStatus"])
            row["violations"]["stock"] = int(stock is not None and definition["policies"]["positiveStock"] and stock <= 0)
            row["policyEvidenceUnknown"] = status is None or stock is None
        query_metric["policyViolations"]["status"] = sum(r["violations"]["status"] for r in query_metric["products"])
        query_metric["policyViolations"]["stock"] = sum(r["violations"]["stock"] for r in query_metric["products"])
        query_metric["activeStatusUnknownProducts"] = sum(
            result_by_key[r["productKey"]].get("policyObservation", {}).get("status") is None
            for r in query_metric["products"])
        violations["status"] += query_metric["policyViolations"]["status"]
        violations["stock"] += query_metric["policyViolations"]["stock"]
        if query_metric["hitEligible"]:
            eligible = [r for r in query_metric["products"][:5]
                        if not any(r["violations"].values()) and not r["policyEvidenceUnknown"]]
            if any(r["label"] in {"relevant", "acceptable"} for r in eligible):
                policy_hits.append(True)
            elif any(r["label"] == "unjudged" for r in eligible) or any(
                    r["policyEvidenceUnknown"] for r in query_metric["products"][:5]):
                policy_hits.append(None)
            else:
                policy_hits.append(False)

    violations["total"] = sum(violations[rule] for rule in RULES)
    metrics["policyViolations"] = violations
    metrics["activeStatusUnknownProducts"] = totals["statusUnknown"]
    metrics["hitAt5"]["observedPolicyCompliantAcceptedHitAt5"] = hit_summary(policy_hits)
    returned = metrics["execution"]["resultCount"]
    metrics["policyEvidence"] = {
        **totals,
        "returnedProducts": returned,
        "fullyObservedProducts": sum(
            not row["policyEvidenceUnknown"] for query in metrics["queries"] for row in query["products"]),
        "fullyObservedRate": ratio(sum(
            not row["policyEvidenceUnknown"] for query in metrics["queries"] for row in query["products"]), returned),
        "source": OBSERVATION_SOURCE,
    }
    return metrics


def build_assessment(root: Path, run_path: Path, definition_path: Path, labels_path: Path,
                     manifest_path: Path, fixtures_path: Path) -> dict:
    definition = load_json(definition_path)
    labels = load_json(labels_path)
    fixtures = load_json(fixtures_path)
    products, manifest = load_products(root, manifest_path)
    run = load_json(run_path)
    validate_run(run, definition, products, manifest)
    failed = sum(row.get("outcome") == "FAILED" for row in run["evaluations"])
    assessment = {
        "schemaVersion": 2,
        "kind": "offline-run-assessment",
        "runId": run["runId"],
        "source": str(run_path.relative_to(root)).replace("\\", "/"),
        "sourceSha256": content_sha256(run_path),
        "sourceCommit": run["agentCommit"],
        "capturedAt": run["capturedAt"],
        "snapshotId": run["snapshotId"],
        "definitionVersion": definition["definitionVersion"],
        "labelVersion": labels["labelVersion"],
        "metricVersion": METRIC_VERSION,
        "definitionSha256": content_sha256(definition_path),
        "labelsSha256": content_sha256(labels_path),
        "includedInQualityComparison": failed == 0,
        "failedQueries": failed,
    }
    if failed:
        assessment["exclusionReason"] = "failed queries are not included in quality comparison"
        assessment["metrics"] = None
    else:
        assessment["metrics"] = apply_observed_policy(
            score_run(run, definition, labels, products, fixtures), run, definition)
    return assessment


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--definition", type=Path, required=True)
    parser.add_argument("--labels", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = args.root.resolve()

    def resolved(path: Path) -> Path:
        return path if path.is_absolute() else (root / path).resolve()

    def within_root(path: Path) -> Path:
        path = resolved(path).resolve()
        try:
            path.relative_to(root)
        except ValueError:
            parser.error(f"path must be inside evaluation root: {path}")
        return path

    paths = [within_root(path) for path in (
        args.run, args.definition, args.labels, args.manifest, args.fixtures)]
    output = within_root(args.output)
    if output.exists():
        parser.error(f"assessment output already exists: {output}")
    try:
        assessment = build_assessment(root, *paths)
    except (ValueError, KeyError, TypeError, OSError, json.JSONDecodeError) as error:
        parser.error(str(error))
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        with output.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(json.dumps(assessment, ensure_ascii=False, indent=2) + "\n")
    except FileExistsError:
        parser.error(f"assessment output already exists: {output}")
    print(json.dumps(assessment, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
