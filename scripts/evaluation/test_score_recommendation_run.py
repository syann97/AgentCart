import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from score_recommendation_run import (
    build_assessment, catalog_fingerprint, load_products, validate_run,
)
from reassess_recommendation_evaluation import load_json, stable_key


ROOT = Path(__file__).resolve().parents[2]
EVALUATION = ROOT / "evaluation/recommendation"


class ScoreRecommendationRunTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.definition_path = EVALUATION / "v2/definition.json"
        cls.labels_path = EVALUATION / "v2/labels.json"
        cls.manifest_path = EVALUATION / "snapshot-manifest.json"
        cls.fixtures_path = EVALUATION / "fixtures.json"
        cls.definition = load_json(cls.definition_path)
        cls.products, cls.manifest = load_products(ROOT, cls.manifest_path)

    def raw_run(self):
        run = copy.deepcopy(load_json(EVALUATION / "runs/agentic-rag-fd7971a.json"))
        run.pop("metrics", None)
        run.update({
            "schemaVersion": 2,
            "kind": "real-model-agentic-rag-raw-run",
            "runId": "agentic-rag-fd7971a-20260917T120000Z-r1",
            "capturedAt": "2026-09-17T12:00:00Z",
            "catalogSnapshot": {
                "source": "MYSQL_PRE_RUN_READ",
                "observedAt": "2026-09-17T11:59:00Z",
                "productCount": len(self.products),
                "immutableFactsSha256": catalog_fingerprint(self.products),
            },
        })
        for evaluation in run["evaluations"]:
            for result in evaluation["results"]:
                product = self.products[stable_key(result)]
                result["description"] = product.get("description")
                result["policyObservation"] = {
                    "source": "POST_RESPONSE_DATABASE_READ",
                    "observedAt": "2026-09-17T12:00:01Z",
                    "productId": result["productId"],
                    "productKey": stable_key(result),
                    "status": "ACTIVE",
                    "stock": product["stock"],
                }
        return run

    def validate(self, run):
        validate_run(run, self.definition, self.products, self.manifest)

    def test_raw_run_has_no_metrics_and_scores_observed_policy(self):
        run = self.raw_run()
        self.validate(run)
        with tempfile.TemporaryDirectory(dir=ROOT) as directory:
            path = Path(directory) / "raw.json"
            path.write_text(json.dumps(run, ensure_ascii=False), encoding="utf-8")
            assessment = build_assessment(ROOT, path, self.definition_path, self.labels_path,
                                          self.manifest_path, self.fixtures_path)
        self.assertTrue(assessment["includedInQualityComparison"])
        evidence = assessment["metrics"]["policyEvidence"]
        self.assertEqual(evidence["returnedProducts"], evidence["fullyObservedProducts"])
        self.assertEqual(assessment["metrics"]["activeStatusUnknownProducts"], 0)

    def test_missing_observation_is_unknown_and_static_stock_is_not_used(self):
        run = self.raw_run()
        first = run["evaluations"][0]["results"][0]
        first["policyObservation"]["status"] = None
        first["policyObservation"]["stock"] = None
        with tempfile.TemporaryDirectory(dir=ROOT) as directory:
            path = Path(directory) / "raw.json"
            path.write_text(json.dumps(run, ensure_ascii=False), encoding="utf-8")
            metrics = build_assessment(ROOT, path, self.definition_path, self.labels_path,
                                       self.manifest_path, self.fixtures_path)["metrics"]
        self.assertEqual(metrics["policyEvidence"]["statusUnknown"], 1)
        self.assertEqual(metrics["policyEvidence"]["stockUnknown"], 1)

    def test_observed_inactive_and_zero_stock_are_violations(self):
        run = self.raw_run()
        first = run["evaluations"][0]["results"][0]["policyObservation"]
        first.update(status="INACTIVE", stock=0)
        with tempfile.TemporaryDirectory(dir=ROOT) as directory:
            path = Path(directory) / "raw.json"
            path.write_text(json.dumps(run, ensure_ascii=False), encoding="utf-8")
            metrics = build_assessment(ROOT, path, self.definition_path, self.labels_path,
                                       self.manifest_path, self.fixtures_path)["metrics"]
        self.assertEqual(metrics["policyViolations"]["status"], 1)
        self.assertEqual(metrics["policyViolations"]["stock"], 1)

    def test_rejects_embedded_metrics_duplicate_and_snapshot_mismatch(self):
        cases = []
        with_metrics = self.raw_run(); with_metrics["metrics"] = {}; cases.append(with_metrics)
        duplicate = self.raw_run(); duplicate["evaluations"][0]["results"][1] = copy.deepcopy(
            duplicate["evaluations"][0]["results"][0]); cases.append(duplicate)
        mismatch = self.raw_run(); mismatch["snapshotId"] = "other"; cases.append(mismatch)
        for run in cases:
            with self.subTest(run=run.get("snapshotId")):
                with self.assertRaises(ValueError):
                    self.validate(run)

    def test_failed_run_is_excluded_without_metrics(self):
        run = self.raw_run()
        run["evaluations"][0]["outcome"] = "FAILED"
        with tempfile.TemporaryDirectory(dir=ROOT) as directory:
            path = Path(directory) / "raw.json"
            path.write_text(json.dumps(run, ensure_ascii=False), encoding="utf-8")
            assessment = build_assessment(ROOT, path, self.definition_path, self.labels_path,
                                          self.manifest_path, self.fixtures_path)
        self.assertFalse(assessment["includedInQualityComparison"])
        self.assertIsNone(assessment["metrics"])

    def test_cli_writes_assessment_once_and_preserves_existing_output(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as directory:
            directory = Path(directory)
            run_path = directory / "raw.json"
            output_path = directory / "assessment.json"
            run_path.write_text(json.dumps(self.raw_run(), ensure_ascii=False), encoding="utf-8")
            command = [
                sys.executable, "-B", str(ROOT / "scripts/evaluation/score_recommendation_run.py"),
                "--root", str(ROOT), "--run", str(run_path),
                "--definition", str(self.definition_path), "--labels", str(self.labels_path),
                "--manifest", str(self.manifest_path), "--fixtures", str(self.fixtures_path),
                "--output", str(output_path),
            ]

            first = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(first.returncode, 0, first.stderr)
            original = output_path.read_bytes()
            second = subprocess.run(command, capture_output=True, text=True)

            self.assertNotEqual(second.returncode, 0)
            self.assertIn("assessment output already exists", second.stderr)
            self.assertEqual(output_path.read_bytes(), original)


if __name__ == "__main__":
    unittest.main()
