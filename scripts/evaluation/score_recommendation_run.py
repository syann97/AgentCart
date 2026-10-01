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


METRIC_VERSION = "2.2-observed-policy"
RUN_ID_PATTERN = re.compile(r"agentic-rag-([0-9a-f]{7,40})-(\d{8}T\d{6}Z)-r([1-9]\d*)")
OBSERVATION_SOURCE = "POST_RESPONSE_DATABASE_READ"


def parse_timestamp(value: str) -> datetime:
    """Parse ISO-8601 timestamps on Python versions that do not accept a trailing Z."""
    if isinstance(value, str):
        value = re.sub(
            r"(?<=\d{2}:\d{2}:\d{2})\.(\d{6})\d+(?=Z|[+-]\d{2}:\d{2}$)",
            r".\1", value)
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


def observed_policy_hit(rows: list[dict]):
    """True: proven hit; None: possible hit with missing evidence; False: impossible hit."""
    possible = [row for row in rows[:5]
                if not any(row["violations"].values()) and row["label"] != "irrelevant"]
    if any(row["label"] in {"relevant", "acceptable"} and not row["policyEvidenceUnknown"]
           for row in possible):
        return True
    if any(row["label"] == "unjudged" or row["policyEvidenceUnknown"] for row in possible):
        return None
    return False


def apply_observed_policy(metrics: dict, run: dict, definition: dict) -> dict:
    evaluations = {row["queryId"]: row for row in run["evaluations"]}
    query_metrics = {row["queryId"]: row for row in metrics["queries"]}

    for query in definition["queries"]:
        query_metric = query_metrics[query["id"]]
        result_by_key = {stable_key(row): row for row in evaluations[query["id"]]["results"]}
        for row in query_metric["products"]:
            observation = result_by_key[row["productKey"]].get("policyObservation") or {}
            status = observation.get("status")
            stock = observation.get("stock")
            row["violations"]["status"] = int(status is not None and status != definition["policies"]["requiredStatus"])
            row["violations"]["stock"] = int(stock is not None and definition["policies"]["positiveStock"] and stock <= 0)
            row["activeStatusUnknown"] = status is None
            row["stockUnknown"] = stock is None
            row["policyEvidenceUnknown"] = row["activeStatusUnknown"] or row["stockUnknown"]
        rows = query_metric["products"]
        query_metric["policyViolations"] = {
            rule: sum(row["violations"][rule] for row in rows) for rule in RULES}
        query_metric["activeStatusUnknownProducts"] = sum(
            row["activeStatusUnknown"] for row in rows)
        query_metric["stockUnknownProducts"] = sum(row["stockUnknown"] for row in rows)
        query_metric["policyEvidenceUnknownProducts"] = sum(row["policyEvidenceUnknown"] for row in rows)
        query_metric["observedPolicyCompliantAcceptedHitAt5"] = (
            observed_policy_hit(rows) if query_metric["hitEligible"] else None)

    queries = metrics["queries"]
    violations = {rule: sum(query["policyViolations"][rule] for query in queries) for rule in RULES}
    violations["total"] = sum(violations[rule] for rule in RULES)
    metrics["policyViolations"] = violations
    status_unknown = sum(query["activeStatusUnknownProducts"] for query in queries)
    stock_unknown = sum(query["stockUnknownProducts"] for query in queries)
    metrics["activeStatusUnknownProducts"] = status_unknown
    metrics["hitAt5"]["observedPolicyCompliantAcceptedHitAt5"] = hit_summary([
        query["observedPolicyCompliantAcceptedHitAt5"] for query in queries if query["hitEligible"]])
    returned = metrics["execution"]["resultCount"]
    fully_observed = returned - sum(query["policyEvidenceUnknownProducts"] for query in queries)
    metrics["policyEvidence"] = {
        "statusKnown": returned - status_unknown, "statusUnknown": status_unknown,
        "stockKnown": returned - stock_unknown, "stockUnknown": stock_unknown,
        "returnedProducts": returned,
        "fullyObservedProducts": fully_observed,
        "fullyObservedRate": ratio(fully_observed, returned),
        "source": OBSERVATION_SOURCE,
    }
    return metrics


def validate_observed_metrics(metrics: dict) -> None:
    """Check product -> query -> run invariants independently of artifact equality."""
    errors = []
    queries = metrics["queries"]
    rows = [row for query in queries for row in query["products"]]
    for query in queries:
        products = query["products"]
        for row in products:
            flags = (row["activeStatusUnknown"], row["stockUnknown"], row["policyEvidenceUnknown"])
            if any(type(flag) is not bool for flag in flags) or flags[2] != (flags[0] or flags[1]):
                errors.append(f"inconsistent product evidence: {query['queryId']} {row['productKey']}")
        for rule in RULES:
            if query["policyViolations"][rule] != sum(row["violations"][rule] for row in products):
                errors.append(f"inconsistent query violations: {query['queryId']} {rule}")
        for flag, count in (("activeStatusUnknown", "activeStatusUnknownProducts"),
                            ("stockUnknown", "stockUnknownProducts"),
                            ("policyEvidenceUnknown", "policyEvidenceUnknownProducts")):
            if query[count] != sum(row[flag] for row in products):
                errors.append(f"inconsistent query evidence: {query['queryId']} {count}")
        expected = observed_policy_hit(products) if query["hitEligible"] else None
        if query["observedPolicyCompliantAcceptedHitAt5"] is not expected:
            errors.append(f"inconsistent query policy hit: {query['queryId']}")
    for rule in RULES:
        if metrics["policyViolations"][rule] != sum(query["policyViolations"][rule] for query in queries):
            errors.append(f"inconsistent aggregate violations: {rule}")
    if metrics["policyViolations"]["total"] != sum(metrics["policyViolations"][rule] for rule in RULES):
        errors.append("inconsistent aggregate violation total")
    returned = len(rows)
    status_unknown = sum(row["activeStatusUnknown"] for row in rows)
    stock_unknown = sum(row["stockUnknown"] for row in rows)
    fully_observed = sum(not row["policyEvidenceUnknown"] for row in rows)
    expected_evidence = {
        "statusKnown": returned - status_unknown, "statusUnknown": status_unknown,
        "stockKnown": returned - stock_unknown, "stockUnknown": stock_unknown,
        "returnedProducts": returned, "fullyObservedProducts": fully_observed,
        "fullyObservedRate": ratio(fully_observed, returned), "source": OBSERVATION_SOURCE,
    }
    if (metrics["policyEvidence"] != expected_evidence or metrics["execution"]["resultCount"] != returned
            or metrics["activeStatusUnknownProducts"] != status_unknown):
        errors.append("inconsistent aggregate evidence")
    key = "observedPolicyCompliantAcceptedHitAt5"
    if metrics["hitAt5"][key] != hit_summary([query[key] for query in queries if query["hitEligible"]]):
        errors.append("inconsistent aggregate policy hit")
    if errors:
        raise ValueError("\n".join(errors))


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
        "scorerSha256": content_sha256(root / "scripts/evaluation/score_recommendation_run.py"),
        "baseScorerSha256": content_sha256(root / "scripts/evaluation/reassess_recommendation_evaluation.py"),
        "includedInQualityComparison": failed == 0,
        "failedQueries": failed,
    }
    if failed:
        assessment["exclusionReason"] = "failed queries are not included in quality comparison"
        assessment["metrics"] = None
    else:
        assessment["metrics"] = apply_observed_policy(
            score_run(run, definition, labels, products, fixtures), run, definition)
        validate_observed_metrics(assessment["metrics"])
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
