"""Coverage and provenance regressions for the expanded offline review."""

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from reassess_recommendation_evaluation import load_json
from review_recommendation_evaluation import build_expanded_review, reason_hash, validate_expanded_review


ROOT = Path(__file__).resolve().parents[2]
V3 = "evaluation/recommendation/v3/"


class ExpandedReviewTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        for relative in ("evaluation/recommendation", "scripts/data", "scripts/evaluation"):
            shutil.copytree(ROOT / relative, self.root / relative)

    def mutate(self, name, mutation):
        path = self.root / V3 / name
        document = load_json(path)
        mutation(document)
        path.write_text(json.dumps(document, ensure_ascii=False), encoding="utf-8")

    def test_replays_all_occurrences_and_retains_history(self):
        paths = list((self.root / "evaluation/recommendation").rglob("*"))
        before = {p: p.read_bytes() for p in paths if p.is_file()}
        result = build_expanded_review(self.root)
        self.assertEqual(result, build_expanded_review(self.root))
        validate_expanded_review(self.root)
        self.assertEqual({"pairs": 92, "newPairs": 13, "notReviewed": 0,
                          "insufficientEvidencePairs": 3, "reasonOccurrences": 197,
                          "distinctReasons": 189}, result["pool"])
        self.assertEqual((170, 22, 5), tuple(result["reasonSummary"][k]
                                           for k in ("supported", "unsupported", "unjudged")))
        for run in result["runs"]:
            for key in ("returned", "supported", "unsupported", "unjudged", "reviewed", "notReviewed"):
                self.assertEqual(run[key], sum(q[key] for q in run["queries"]))
        self.assertEqual(before, {p: p.read_bytes() for p in before})

    def test_missing_new_pair_is_rejected(self):
        def remove_new(document):
            for q in document["judgments"]:
                for row in q["products"]:
                    if row["previousLabel"] is None:
                        q["products"].remove(row)
                        return
        self.mutate("labels.json", remove_new)
        with self.assertRaisesRegex(ValueError, "pool coverage"):
            build_expanded_review(self.root)

    def test_review_cannot_claim_human_validation(self):
        for name in ("labels.json", "grounding.json"):
            with self.subTest(name=name):
                path = self.root / V3 / name
                original = path.read_bytes()
                self.mutate(name, lambda d: d["review"].update(humanValidated=True))
                with self.assertRaisesRegex(ValueError, "assistant review provenance"):
                    build_expanded_review(self.root)
                path.write_bytes(original)

    def test_evidence_and_deferral_are_checked(self):
        for field, value, expected in (("evidence", {"name": "invented"}, "evidence differs"),
                                       ("deferralReason", "NOT_REVIEWED", "deferral reason"),
                                       ("previousLabel", "irrelevant", "previous relevance")):
            path = self.root / V3 / "labels.json"
            original = path.read_bytes()
            def change(document):
                row = next(p for q in document["judgments"] for p in q["products"] if p["label"] == "unjudged")
                row[field] = value
            self.mutate("labels.json", change)
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, expected):
                build_expanded_review(self.root)
            path.write_bytes(original)

    def test_reason_occurrence_must_match_run_and_exact_text(self):
        path = self.root / V3 / "grounding.json"
        original = path.read_bytes()
        for field in ("text", "hash", "run", "coverage", "claim"):
            def change(document):
                row = document["judgments"][0]
                if field == "text":
                    row["reasonText"] += " changed"
                    row["reasonSha256"] = reason_hash(row["reasonText"])
                elif field == "hash":
                    row["reasonSha256"] = "0" * 64
                elif field == "run":
                    row["occurrences"][0]["runId"] = "another-run"
                elif field == "coverage":
                    document["judgments"].pop()
                else:
                    next(r for r in document["judgments"] if r["label"] == "unsupported")["unsupportedClaim"] = "invented"
            self.mutate("grounding.json", change)
            with self.subTest(field=field), self.assertRaises(ValueError):
                build_expanded_review(self.root)
            path.write_bytes(original)

    def test_source_hash_and_missing_manifest_entry_are_rejected(self):
        path = self.root / V3 / "manifest.json"
        original = path.read_bytes()
        for group in ("inputs", "runs"):
            for change in ("hash", "coverage"):
                def alter(document):
                    if change == "hash":
                        document[group][0]["sha256"] = "0" * 64
                    else:
                        document[group].pop()
                self.mutate("manifest.json", alter)
                with self.subTest(group=group, change=change), self.assertRaises(ValueError):
                    build_expanded_review(self.root)
                path.write_bytes(original)

    def test_unknowns_are_not_assumed_accepted_or_supported(self):
        result = build_expanded_review(self.root)
        before = next(a for a in result["fullAssessments"] if "fe2766f" in a["runId"])
        relevance = before["metrics"]["relevance"]
        self.assertEqual((2, 0.9625, 0.9875), tuple(relevance[k] for k in (
            "unjudged", "acceptedShareLowerBound", "acceptedShareUpperBound")))
        after = next(r for r in result["runs"] if r["scope"] == "full" and r["phase"] == "after")
        self.assertEqual((5, 67, 0.902778, 0.972222), tuple(after[k] for k in (
            "unjudged", "judged", "supportedShareLowerBound", "supportedShareUpperBound")))
        self.assertEqual([17, 17], [a["metrics"]["hitAt5"]["acceptedHitAt5"]["eligibleQueries"]
                                  for a in result["fullAssessments"]])
        self.assertEqual(2, len(result["fullAssessments"]))  # focused runs have no full-suite denominator

    def test_tampered_output_and_overwrite_are_rejected(self):
        self.mutate("reassessment.json", lambda d: d["reasonSummary"].update(supported=197))
        with self.assertRaisesRegex(ValueError, "deterministic recomputation"):
            validate_expanded_review(self.root)
        output = self.root / V3 / "reassessment.json"
        original = output.read_bytes()
        result = subprocess.run([sys.executable, "-B", str(self.root / "scripts/evaluation/review_recommendation_evaluation.py"),
                                 "--root", str(self.root), "--output", V3 + "reassessment.json"],
                                capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("output already exists", result.stderr)
        self.assertEqual(original, output.read_bytes())


if __name__ == "__main__":
    unittest.main()
