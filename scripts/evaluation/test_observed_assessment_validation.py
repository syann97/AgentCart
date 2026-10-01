"""Corrected assessments must preserve history and pass independent layer checks."""

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from reassess_recommendation_evaluation import load_json
from validate_recommendation_evaluation import validate_observed_assessments


ROOT = Path(__file__).resolve().parents[2]


class ObservedAssessmentValidationTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        for relative in ("evaluation/recommendation", "scripts/data", "scripts/evaluation"):
            shutil.copytree(ROOT / relative, self.root / relative)
        self.manifest_path = self.root / "evaluation/recommendation/v2.2/manifest.json"
        self.manifest = load_json(self.manifest_path)
        self.entry = self.manifest["assessments"][0]

    def write(self, relative, document):
        (self.root / relative).write_text(json.dumps(document, ensure_ascii=False), encoding="utf-8")

    def test_corrected_assessments_replay_without_changing_historical_bytes(self):
        before = {row["path"]: (self.root / row["path"]).read_bytes()
                  for row in self.manifest["preservedHistory"]}
        validate_observed_assessments(self.root)
        validate_observed_assessments(self.root)
        self.assertEqual(before, {path: (self.root / path).read_bytes() for path in before})

    def test_changed_historical_assessment_or_raw_run_is_rejected(self):
        for relative in (self.entry["supersedes"], self.entry["source"]):
            with self.subTest(relative=relative):
                path = self.root / relative
                before = path.read_bytes()
                path.write_bytes(before + b"\n")
                with self.assertRaisesRegex(ValueError, "preserved observed-policy history changed"):
                    validate_observed_assessments(self.root)
                path.write_bytes(before)

    def test_product_query_and_aggregate_tampering_is_rejected(self):
        path = self.entry["output"]
        original = load_json(self.root / path)
        for change, expected in (("product", "product evidence"), ("query", "query policy hit"),
                                 ("aggregate", "aggregate policy hit"), ("metadata", "assessment differs")):
            with self.subTest(change=change):
                document = json.loads(json.dumps(original))
                metrics = document["metrics"]
                if change == "product":
                    metrics["queries"][0]["products"][0]["activeStatusUnknown"] = True
                elif change == "query":
                    metrics["queries"][0]["observedPolicyCompliantAcceptedHitAt5"] = False
                elif change == "aggregate":
                    metrics["hitAt5"]["observedPolicyCompliantAcceptedHitAt5"]["hits"] = 16
                else:
                    document["sourceSha256"] = "0" * 64
                self.write(path, document)
                with self.assertRaisesRegex(ValueError, expected):
                    validate_observed_assessments(self.root)
                self.write(path, original)

    def test_omitted_history_entry_is_rejected(self):
        self.manifest["preservedHistory"] = [row for row in self.manifest["preservedHistory"]
                                             if row["path"] != self.entry["supersedes"]]
        self.write(self.manifest_path.relative_to(self.root), self.manifest)
        with self.assertRaisesRegex(ValueError, "history is incomplete"):
            validate_observed_assessments(self.root)

    def test_unindexed_or_missing_corrected_assessment_is_rejected(self):
        path = self.root / self.entry["output"]
        extra = path.with_name("unindexed.json")
        extra.write_bytes(path.read_bytes())
        with self.assertRaisesRegex(ValueError, "indexed outputs"):
            validate_observed_assessments(self.root)
        extra.unlink()
        path.unlink()
        with self.assertRaisesRegex(ValueError, "indexed outputs"):
            validate_observed_assessments(self.root)

    def test_full_validator_accepts_git_crlf_checkout_without_rewriting_history(self):
        snapshot = load_json(self.root / "evaluation/recommendation/snapshot-manifest.json")
        paths = {row["path"] for row in snapshot["files"]} | {
            row["path"] for row in self.manifest["preservedHistory"]}
        for relative in paths:
            if "/executions/" in relative:
                continue  # .gitattributes marks these immutable artifacts -text.
            path = self.root / relative
            path.write_bytes(path.read_bytes().replace(b"\r\n", b"\n").replace(b"\n", b"\r\n"))
        result = subprocess.run([sys.executable, "-B", str(self.root / "scripts/evaluation/validate_recommendation_evaluation.py"),
                                 "--root", str(self.root)], capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
