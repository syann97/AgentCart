"""Replay the expanded assistant review without model calls or source mutations."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from reassess_recommendation_evaluation import content_sha256, load_json, ratio, source_path, stable_key
from score_recommendation_run import build_assessment, load_products


PREFIX = "evaluation/recommendation/"
LABEL_VERSION = "3.0-expanded-review"
RUBRIC_VERSION = "2.0-expanded-reasons"


def reason_hash(reason: str) -> str:
    return hashlib.sha256(reason.encode("utf-8")).hexdigest()


def review_provenance(review: dict) -> None:
    if (review.get("reviewer") != "Codex" or review.get("reviewerType") != "assistant"
            or review.get("humanValidated") is not False or not review.get("reviewedAt")
            or not review.get("limitations") or not review.get("method")):
        raise ValueError("assistant review provenance is incomplete")


def evidence_matches(row: dict, product: dict) -> None:
    if row.get("evidence") != {field: product[field] for field in ("name", "description")}:
        raise ValueError("review evidence differs from snapshot")
    if not row.get("reason"):
        raise ValueError("review rationale is missing")


def reason_summary(rows: list[dict]) -> dict:
    counts = {label: sum(row["label"] == label for row in rows)
              for label in ("supported", "unsupported", "unjudged")}
    returned = len(rows)
    judged = returned - counts["unjudged"]
    return {**counts, "returned": returned, "reviewed": returned, "notReviewed": 0,
            "judged": judged, "judgedSupportedRate": ratio(counts["supported"], judged),
            "supportedShareLowerBound": ratio(counts["supported"], returned),
            "supportedShareUpperBound": ratio(counts["supported"] + counts["unjudged"], returned)}


def build_expanded_review(root: Path) -> dict:
    directory = root / PREFIX / "v3"
    manifest = load_json(directory / "manifest.json")
    labels = load_json(directory / "labels.json")
    rubric = load_json(directory / "grounding.json")
    if (manifest.get("kind") != "expanded-review-manifest" or manifest.get("schemaVersion") != 1
            or labels.get("labelVersion") != LABEL_VERSION
            or rubric.get("rubricVersion") != RUBRIC_VERSION):
        raise ValueError("unsupported expanded review version")
    review_provenance(labels["review"])
    review_provenance(rubric["review"])
    for group in ("inputs", "runs"):
        entries = manifest[group]
        if len({row["path"] for row in entries}) != len(entries):
            raise ValueError("duplicate manifest source")
        for entry in entries:
            path = source_path(root, entry["path"])
            digest = (hashlib.sha256(path.read_bytes()).hexdigest() if group == "runs"
                      else content_sha256(path))
            if digest != entry["sha256"]:
                raise ValueError(f"expanded review source hash differs: {entry['path']}")
    required_inputs = {PREFIX + path for path in (
        "v2/definition.json", "v2/labels.json", "snapshot-manifest.json", "fixtures.json",
        "grounding-v1.json", "grounding-comparison.json", "v2.2/manifest.json")}
    required_inputs |= {"scripts/evaluation/" + name for name in (
        "score_recommendation_run.py", "reassess_recommendation_evaluation.py")}
    previous = load_json(root / PREFIX / "v2.2/manifest.json")
    required_inputs |= {row["output"] for row in previous["assessments"]}
    if {row["path"] for row in manifest["inputs"]} != required_inputs:
        raise ValueError("expanded review inputs are incomplete")
    comparison = load_json(root / PREFIX / "grounding-comparison.json")
    expected_runs = {row["path"] for row in comparison["sources"]
                     if "/raw/" in row["path"] or "/focused/" in row["path"]}
    if {row["path"] for row in manifest["runs"]} != expected_runs:
        raise ValueError("expanded review must cover the six archived runs")
    products, snapshot = load_products(root, root / PREFIX / "snapshot-manifest.json")
    definition = load_json(root / PREFIX / "v2/definition.json")
    queries = {q["id"]: q for q in definition["queries"]}
    if (labels.get("definitionVersion") != definition["definitionVersion"]
            or labels.get("snapshotId") != snapshot["snapshotId"]
            or rubric.get("snapshotId") != snapshot["snapshotId"]):
        raise ValueError("expanded review definition or snapshot mismatch")
    old_labels = load_json(root / PREFIX / "v2/labels.json")
    old = {(q["queryId"], p["productKey"]): p for q in old_labels["judgments"] for p in q["products"]}
    pool, occurrences, runs = set(), {}, []
    for entry in manifest["runs"]:
        run = load_json(root / entry["path"])
        phase = "before" if run["agentCommit"].startswith("fe2766f") else "after"
        scope = "full" if run["kind"] == "real-model-agentic-rag-raw-run" else "focused"
        if (entry["runId"], entry["phase"], entry["scope"]) != (run["runId"], phase, scope):
            raise ValueError("incorrect run provenance")
        runs.append((entry, run))
        for evaluation in run["evaluations"]:
            qid = evaluation["queryId"]
            if evaluation["query"] != queries[qid]["query"]:
                raise ValueError("review query differs from definition")
            for result in evaluation["results"]:
                key = stable_key(result)
                for field in ("name", "description"):
                    if result[field] != products[key][field]:
                        raise ValueError("run evidence differs from snapshot")
                pair = (qid, key)
                pool.add(pair)
                identity = (run["runId"], qid, key)
                if identity in occurrences:
                    raise ValueError("duplicate returned occurrence")
                occurrences[identity] = result["reason"]
    reviewed = {}
    for query in labels["judgments"]:
        qid = query["queryId"]
        if query.get("query") != queries[qid]["query"]:
            raise ValueError("label query differs from definition")
        for row in query["products"]:
            pair = (qid, row["productKey"])
            if pair in reviewed or pair not in pool or row["label"] not in old_labels["labelMeaning"]:
                raise ValueError("duplicate or invalid relevance pair")
            evidence_matches(row, products[pair[1]])
            if row.get("previousLabel") != old.get(pair, {}).get("label"):
                raise ValueError("previous relevance provenance differs from v2")
            origin = row.get("origin")
            if origin == "CARRIED_FORWARD_V2":
                if pair not in old or any(row[field] != old[pair][field] for field in ("label", "reason")):
                    raise ValueError("carried relevance differs from v2")
            elif origin != "REVIEWED_V3":
                raise ValueError("missing relevance provenance")
            if row.get("deferralReason") != ("INSUFFICIENT_EVIDENCE" if row["label"] == "unjudged" else None):
                raise ValueError("reviewed relevance deferral reason is invalid")
            reviewed[pair] = row
    if set(reviewed) != pool:
        raise ValueError("relevance pool coverage is incomplete")
    reasons, covered = {}, {}
    for row in rubric["judgments"]:
        qid, key = row["queryId"], row["productKey"]
        if (qid, key) not in pool or row.get("query") != queries[qid]["query"]:
            raise ValueError("grounding pair or query is invalid")
        evidence_matches(row, products[key])
        if (row.get("reasonSha256") != reason_hash(row["reasonText"])
                or row["label"] not in {"supported", "unsupported", "unjudged"}
                or not row.get("occurrences")):
            raise ValueError("invalid grounding judgment")
        if row.get("deferralReason") != ("INSUFFICIENT_EVIDENCE" if row["label"] == "unjudged" else None):
            raise ValueError("invalid reason deferral")
        if row["label"] == "unsupported" and (not row.get("unsupportedClaim")
                or row["unsupportedClaim"] not in row["reasonText"]):
            raise ValueError("unsupported reason must quote its claim")
        signature = (qid, key, row["reasonText"])
        if signature in reasons:
            raise ValueError("duplicate reason judgment")
        reasons[signature] = row
        for occurrence in row["occurrences"]:
            identity = (occurrence["runId"], qid, key)
            if identity in covered or occurrences.get(identity) != row["reasonText"]:
                raise ValueError("reason occurrence text or identity differs")
            covered[identity] = row
    if set(covered) != set(occurrences):
        raise ValueError("reason occurrence coverage is incomplete")
    run_summaries, assessments, changes = [], [], []
    for entry, run in runs:
        selected = [row for identity, row in covered.items() if identity[0] == run["runId"]]
        per_query = [{"queryId": evaluation["queryId"], **reason_summary([
            covered[(run["runId"], evaluation["queryId"], stable_key(p))]
            for p in evaluation["results"]])} for evaluation in run["evaluations"]]
        run_summaries.append({"runId": run["runId"], "phase": entry["phase"], "scope": entry["scope"],
                              **reason_summary(selected), "queries": per_query})
        if entry["scope"] != "full":
            continue
        assessment = build_assessment(root, root / entry["path"], root / PREFIX / "v2/definition.json",
                                      directory / "labels.json", root / PREFIX / "snapshot-manifest.json",
                                      root / PREFIX / "fixtures.json")
        assessments.append(assessment)
        legacy_entry = next(row for row in previous["assessments"] if row["source"] == entry["path"])
        legacy = load_json(root / legacy_entry["output"])
        returned_pairs = {(e["queryId"], stable_key(p)) for e in run["evaluations"] for p in e["results"]}
        changes.append({"runId": run["runId"], "previousAssessment": legacy_entry["output"],
                        "previousRelevance": legacy["metrics"]["relevance"],
                        "previousUnknownReasons": {
                            "NOT_REVIEWED": len(returned_pairs - set(old)),
                            "INSUFFICIENT_EVIDENCE": sum(old[pair]["label"] == "unjudged"
                                                         for pair in returned_pairs & set(old))},
                        "expandedRelevance": assessment["metrics"]["relevance"],
                        "changedPairs": [{"queryId": qid, "productKey": key,
                                          "previousLabel": old.get((qid, key), {}).get("label", "unjudged"),
                                          "expandedLabel": reviewed[(qid, key)]["label"]}
                                         for qid, key in sorted({(e["queryId"], stable_key(p))
                                             for e in run["evaluations"] for p in e["results"]})
                                         if old.get((qid, key), {}).get("label", "unjudged") != reviewed[(qid, key)]["label"]]})
    return {"schemaVersion": 1, "kind": "expanded-offline-review", "labelVersion": LABEL_VERSION,
            "rubricVersion": RUBRIC_VERSION, "manifestSha256": content_sha256(directory / "manifest.json"),
            "labelsSha256": content_sha256(directory / "labels.json"),
            "rubricSha256": content_sha256(directory / "grounding.json"),
            "reviewScorerSha256": content_sha256(root / "scripts/evaluation/review_recommendation_evaluation.py"),
            "pool": {"pairs": len(pool), "newPairs": len(pool - set(old)), "notReviewed": 0,
                     "insufficientEvidencePairs": sum(row["label"] == "unjudged" for row in reviewed.values()),
                     "reasonOccurrences": len(occurrences), "distinctReasons": len(reasons)},
            "reasonSummary": reason_summary(list(covered.values())), "runs": run_summaries,
            "fullAssessments": assessments, "changesFromV2_2": changes}


def validate_expanded_review(root: Path) -> None:
    if load_json(root / PREFIX / "v3/reassessment.json") != build_expanded_review(root):
        raise ValueError("stored expanded review differs from deterministic recomputation")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path, help="write to a new repository path; never overwrite")
    args = parser.parse_args()
    root = args.root.resolve()
    if args.output:
        output = source_path(root, str(args.output))
        if output.exists():
            parser.error("expanded review output already exists")
        result = build_expanded_review(root)
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    else:
        validate_expanded_review(root)
    print("Expanded assistant review valid; no model calls performed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
