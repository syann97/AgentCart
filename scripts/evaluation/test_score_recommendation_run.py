import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from score_recommendation_run import (
    apply_observed_policy, build_assessment, catalog_fingerprint, load_products, parse_timestamp,
    validate_observed_metrics, validate_run,
)
from reassess_recommendation_evaluation import RULES, hit_summary, load_json, score_run, stable_key


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

    def observed_metrics(self, run, labels=None):
        metrics = score_run(run, self.definition, labels or load_json(self.labels_path),
                            self.products, load_json(self.fixtures_path))
        return apply_observed_policy(metrics, run, self.definition)

    def assert_consistent(self, metrics):
        queries = metrics["queries"]
        rows = [row for query in queries for row in query["products"]]
        self.assertEqual(sum(row["activeStatusUnknown"] for row in rows),
                         metrics["activeStatusUnknownProducts"])
        for query in queries:
            self.assertEqual(sum(row["activeStatusUnknown"] for row in query["products"]),
                             query["activeStatusUnknownProducts"])
            self.assertEqual(sum(row["stockUnknown"] for row in query["products"]),
                             query["stockUnknownProducts"])
            self.assertEqual(sum(row["policyEvidenceUnknown"] for row in query["products"]),
                             query["policyEvidenceUnknownProducts"])
        for rule in RULES:
            self.assertEqual(sum(row["violations"][rule] for row in rows),
                             metrics["policyViolations"][rule])
            self.assertEqual(sum(query["policyViolations"][rule] for query in queries),
                             metrics["policyViolations"][rule])
        self.assertEqual(sum(row["stockUnknown"] for row in rows), metrics["policyEvidence"]["stockUnknown"])
        self.assertEqual(sum(row["policyEvidenceUnknown"] for row in rows),
                         len(rows) - metrics["policyEvidence"]["fullyObservedProducts"])
        key = "observedPolicyCompliantAcceptedHitAt5"
        self.assertEqual(hit_summary([query[key] for query in queries if query["hitEligible"]]),
                         metrics["hitAt5"][key])

    def test_healthy_observations_update_every_product_and_aggregate(self):
        metrics = self.observed_metrics(self.raw_run())
        self.assertFalse(any(row["activeStatusUnknown"] for query in metrics["queries"] for row in query["products"]))
        self.assert_consistent(metrics)

    def test_all_top_five_policy_violations_are_query_and_aggregate_miss(self):
        run = self.raw_run()
        for result in run["evaluations"][0]["results"]:
            result["policyObservation"].update(status="INACTIVE", stock=0)
        metrics = self.observed_metrics(run)
        first = metrics["queries"][0]
        self.assertIs(first["observedPolicyCompliantAcceptedHitAt5"], False)
        self.assertEqual(5, first["policyViolations"]["status"])
        self.assertEqual(5, first["policyViolations"]["stock"])
        self.assertEqual(1, metrics["hitAt5"]["observedPolicyCompliantAcceptedHitAt5"]["misses"])
        self.assert_consistent(metrics)

    def test_missing_observations_do_not_borrow_snapshot_status_or_stock(self):
        for missing in ("status", "stock", "observation", "null-observation"):
            with self.subTest(missing=missing):
                run = self.raw_run()
                for result in run["evaluations"][0]["results"]:
                    if missing == "observation":
                        result.pop("policyObservation")
                    elif missing == "null-observation":
                        result["policyObservation"] = None
                    else:
                        result["policyObservation"].pop(missing)
                metrics = self.observed_metrics(run)
                self.assertIsNone(metrics["queries"][0]["observedPolicyCompliantAcceptedHitAt5"])
                self.assertEqual(1, metrics["hitAt5"]["observedPolicyCompliantAcceptedHitAt5"]["unjudged"])
                self.assert_consistent(metrics)

    def test_entire_run_without_observations_keeps_all_evidence_unknown(self):
        run = self.raw_run()
        for evaluation in run["evaluations"]:
            for result in evaluation["results"]:
                result.pop("policyObservation")
        metrics = self.observed_metrics(run)
        returned = metrics["execution"]["resultCount"]
        self.assertEqual(returned, metrics["policyEvidence"]["statusUnknown"])
        self.assertEqual(returned, metrics["policyEvidence"]["stockUnknown"])
        self.assertEqual(0, metrics["policyEvidence"]["fullyObservedProducts"])
        self.assertEqual(17, metrics["hitAt5"]["observedPolicyCompliantAcceptedHitAt5"]["unjudged"])
        self.assertEqual(0, metrics["policyViolations"]["status"])
        self.assertEqual(0, metrics["policyViolations"]["stock"])
        self.assert_consistent(metrics)

    def test_proven_hit_takes_precedence_over_another_unknown_candidate(self):
        run = self.raw_run()
        first = run["evaluations"][0]
        first["results"][1]["policyObservation"] = None
        labels = copy.deepcopy(load_json(self.labels_path))
        known_key = stable_key(first["results"][0])
        judgment = next(row for row in labels["judgments"] if row["queryId"] == first["queryId"])
        next(row for row in judgment["products"] if row["productKey"] == known_key)["label"] = "relevant"
        metrics = self.observed_metrics(run, labels)
        self.assertIs(metrics["queries"][0]["observedPolicyCompliantAcceptedHitAt5"], True)
        self.assertEqual(1, metrics["queries"][0]["policyEvidenceUnknownProducts"])
        self.assert_consistent(metrics)

    def test_unknown_only_matters_when_a_policy_compliant_accepted_hit_is_possible(self):
        for label, status, stock, expected in (
                ("relevant", "ACTIVE", 1, True), ("relevant", None, 1, None),
                ("acceptable", "ACTIVE", None, None), ("unjudged", "ACTIVE", 1, None),
                ("irrelevant", None, None, False), ("relevant", None, 0, False),
                ("unjudged", "INACTIVE", None, False)):
            with self.subTest(label=label, status=status, stock=stock):
                run = self.raw_run()
                first = run["evaluations"][0]
                first["results"] = first["results"][:1]
                first["resultCount"] = 1
                result = first["results"][0]
                result["policyObservation"].update(status=status, stock=stock)
                labels = copy.deepcopy(load_json(self.labels_path))
                judgment = next(row for row in labels["judgments"] if row["queryId"] == first["queryId"])
                next(row for row in judgment["products"] if row["productKey"] == stable_key(result))["label"] = label
                metrics = self.observed_metrics(run, labels)
                self.assertIs(metrics["queries"][0]["observedPolicyCompliantAcceptedHitAt5"], expected)
                self.assert_consistent(metrics)

    def test_empty_results_and_missing_relevance_share_the_same_hit_denominator(self):
        run = self.raw_run()
        run["evaluations"][0]["results"] = []
        run["evaluations"][0]["resultCount"] = 0
        labels = copy.deepcopy(load_json(self.labels_path))
        labels["judgments"][1]["products"] = []
        metrics = self.observed_metrics(run, labels)
        self.assertIs(metrics["queries"][0]["observedPolicyCompliantAcceptedHitAt5"], False)
        self.assertIsNone(metrics["queries"][1]["observedPolicyCompliantAcceptedHitAt5"])
        self.assertEqual(17, metrics["hitAt5"]["observedPolicyCompliantAcceptedHitAt5"]["eligibleQueries"])
        self.assert_consistent(metrics)

    def test_assessment_is_deterministic_and_preserves_raw_bytes(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as directory:
            path = Path(directory) / "raw.json"
            path.write_text(json.dumps(self.raw_run(), ensure_ascii=False), encoding="utf-8")
            before = path.read_bytes()
            first = build_assessment(ROOT, path, self.definition_path, self.labels_path,
                                     self.manifest_path, self.fixtures_path)
            second = build_assessment(ROOT, path, self.definition_path, self.labels_path,
                                      self.manifest_path, self.fixtures_path)
            self.assertEqual(first, second)
            self.assertEqual(before, path.read_bytes())

    def test_invariant_validator_rejects_product_query_and_aggregate_drift(self):
        healthy = self.observed_metrics(self.raw_run())
        validate_observed_metrics(healthy)
        for change, message in (("product", "product evidence"), ("query-hit", "query policy hit"),
                                ("query-count", "query evidence"), ("query-violation", "query violations"),
                                ("total", "aggregate evidence"), ("total-hit", "aggregate policy hit"),
                                ("total-violation", "aggregate violations")):
            with self.subTest(change=change):
                metrics = copy.deepcopy(healthy)
                query = metrics["queries"][0]
                if change == "product":
                    query["products"][0]["activeStatusUnknown"] = True
                elif change == "query-hit":
                    query["observedPolicyCompliantAcceptedHitAt5"] = False
                elif change == "query-count":
                    query["stockUnknownProducts"] = 1
                elif change == "query-violation":
                    query["policyViolations"]["stock"] = 1
                elif change == "total":
                    metrics["activeStatusUnknownProducts"] = 1
                elif change == "total-hit":
                    metrics["hitAt5"]["observedPolicyCompliantAcceptedHitAt5"]["eligibleQueries"] = 20
                else:
                    metrics["policyViolations"]["status"] = 1
                with self.assertRaisesRegex(ValueError, message):
                    validate_observed_metrics(metrics)

    def test_java_nanosecond_timestamp_is_accepted_on_python_38(self):
        parsed = parse_timestamp("2026-09-18T09:52:04.123456789+09:00")
        self.assertEqual(123456, parsed.microsecond)
        self.assertIsNotNone(parsed.utcoffset())

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
