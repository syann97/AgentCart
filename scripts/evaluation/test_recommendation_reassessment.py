"""Regression coverage for policy/relevance separation and immutable run history."""

import copy
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from reassess_recommendation_evaluation import (
    build_reassessment, content_sha256, load_json, score_run, stable_key, validate_inputs,
)


ROOT = Path(__file__).resolve().parents[2]


class ScoringTest(unittest.TestCase):
    def setUp(self):
        self.query = {
            "id": "S07", "targetOutcome": "recommendations", "explicitConstraints": {},
            "expectedRelatedCategories": {"values": ["패션·의류"]},
        }
        self.policies = {"positiveStock": True, "requiredStatus": "ACTIVE"}
        self.product = {
            "name": "휴대 손난로", "brand": "브랜드", "category": "디지털·IT기기",
            "price": 23000, "stock": 5,
        }
        self.fixtures = {"fixtures": {}}

    def score(self, grades=("relevant",), target=None):
        products = {}
        results = []
        judgments = []
        for i, grade in enumerate(grades):
            product = {**self.product, "name": f"{self.product['name']}{i}"}
            key = stable_key(product)
            products[key] = product
            results.append(product)
            if grade != "missing":
                judgments.append({"productKey": key, "label": grade})
        query = {**self.query, "targetOutcome": target or self.query["targetOutcome"]}
        run = {"evaluations": [{"queryId": query["id"], "results": results,
                                "latencyMillis": 10, "outcome": "SUCCESS", "llmCallAttempts": 2}]}
        return score_run(run, {"queries": [query], "policies": self.policies},
                         {"judgments": [{"queryId": query["id"], "products": judgments}]},
                         products, self.fixtures)

    def test_expected_category_mismatch_is_not_an_explicit_violation(self):
        scored = self.score()
        self.assertEqual(1, scored["expectedCategoryMismatches"])
        self.assertEqual(0, scored["policyViolations"]["category"])
        self.assertEqual(1, scored["hitAt5"]["acceptedHitAt5"]["hits"])

    def test_explicit_category_is_enforced_even_for_a_relevant_product(self):
        self.query["explicitConstraints"]["categories"] = {"value": ["패션·의류"]}
        scored = self.score()
        self.assertEqual(1, scored["policyViolations"]["category"])
        self.assertEqual(1, scored["hitAt5"]["acceptedHitAt5"]["hits"])
        self.assertEqual(0, scored["hitAt5"]["observedPolicyCompliantAcceptedHitAt5"]["hits"])

    def test_price_boundaries_are_inclusive_and_price_is_separate_from_relevance(self):
        self.query["explicitConstraints"] = {"minPrice": {"value": 23000}, "maxPrice": {"value": 23000}}
        self.assertEqual(0, self.score()["policyViolations"]["price"])
        self.product["price"] = 23001
        scored = self.score()
        self.assertEqual(1, scored["policyViolations"]["price"])
        self.assertEqual(1, scored["relevance"]["relevant"])
        self.product["price"] = 22999
        self.assertEqual(1, self.score()["policyViolations"]["price"])

    def test_conflicting_price_counts_one_product_rule_and_requires_clarification(self):
        self.query["explicitConstraints"] = {"minPrice": {"value": 50000}, "maxPrice": {"value": 30000}}
        self.product["price"] = 40000
        scored = self.score(target="clarification")
        self.assertEqual(1, scored["policyViolations"]["price"])
        self.assertEqual(1, scored["clarificationFailures"])
        self.assertEqual(0, scored["hitAt5"]["acceptedHitAt5"]["eligibleQueries"])

    def test_application_stock_and_recent_order_rules_survive_label_changes(self):
        self.product["stock"] = 0
        self.fixtures["fixtures"]["C04"] = {"orders": [{"stableProductKey": "휴대 손난로0|브랜드"}]}
        self.query["explicitConstraints"]["excludeRecentFixture"] = {"value": "C04"}
        scored = self.score()
        self.assertEqual(1, scored["policyViolations"]["stock"])
        self.assertEqual(1, scored["policyViolations"]["recentOrder"])

    def test_missing_status_is_not_evidence_of_active_status(self):
        self.assertEqual(1, self.score()["activeStatusUnknownProducts"])
        self.product["status"] = "ACTIVE"
        self.assertEqual(0, self.score()["activeStatusUnknownProducts"])
        self.product["status"] = "SOLD_OUT"
        self.assertEqual(1, self.score()["policyViolations"]["status"])

    def test_category_match_does_not_make_an_irrelevant_result_a_hit(self):
        self.product["category"] = "패션·의류"
        scored = self.score(("irrelevant",))
        self.assertEqual(0, scored["expectedCategoryMismatches"])
        self.assertEqual(1, scored["relevance"]["irrelevant"])
        self.assertEqual(1, scored["hitAt5"]["acceptedHitAt5"]["misses"])

    def test_missing_and_explicitly_unjudged_labels_do_not_become_false_hits_or_misses(self):
        for grade in ("missing", "unjudged"):
            with self.subTest(grade=grade):
                scored = self.score((grade,))
                relevance = scored["relevance"]
                self.assertEqual(1, relevance["unjudged"])
                self.assertEqual(0, relevance["irrelevant"])
                self.assertIsNone(relevance["judgedAcceptedRate"])
                self.assertEqual((0.0, 1.0), (relevance["acceptedShareLowerBound"], relevance["acceptedShareUpperBound"]))
                self.assertEqual(1, scored["hitAt5"]["acceptedHitAt5"]["unjudged"])
                self.assertEqual(0, scored["hitAt5"]["acceptedHitAt5"]["misses"])

    def test_mixed_labels_expose_coverage_and_conditional_accuracy(self):
        scored = self.score(("relevant", "acceptable", "irrelevant", "unjudged"))
        relevance = scored["relevance"]
        self.assertEqual(4, relevance["returned"])
        self.assertEqual(3, relevance["judged"])
        self.assertEqual(0.666667, relevance["judgedAcceptedRate"])
        self.assertEqual(0.25, relevance["unjudgedRate"])
        self.assertEqual((0.5, 0.75), (relevance["acceptedShareLowerBound"], relevance["acceptedShareUpperBound"]))
        self.assertEqual(1, scored["hitAt5"]["strictHitAt5"]["hits"])

    def test_empty_and_short_results_do_not_use_five_as_the_product_denominator(self):
        empty = self.score(())
        self.assertIsNone(empty["relevance"]["unjudgedRate"])
        self.assertIsNone(empty["relevance"]["judgedAcceptedRate"])
        self.assertEqual(1, empty["hitAt5"]["acceptedHitAt5"]["misses"])
        self.assertEqual(1.0, self.score(("acceptable",))["relevance"]["judgedAcceptedRate"])
        self.assertEqual(0, self.score((), target="empty")["outOfCatalogQueriesReturningProducts"])

    def test_missing_baseline_measurements_are_null_not_zero(self):
        execution = self.score()["execution"]
        self.assertIsNone(execution["searchCount"])
        self.assertIsNone(execution["totalTokens"])
        self.assertEqual(2, execution["llmCallAttempts"])


class FrozenInputsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.definition = load_json(ROOT / "evaluation/recommendation/v2/definition.json")
        cls.labels = load_json(ROOT / "evaluation/recommendation/v2/labels.json")
        cls.fixtures = load_json(ROOT / "evaluation/recommendation/fixtures.json")
        manifest = load_json(ROOT / "evaluation/recommendation/snapshot-manifest.json")
        cls.products = {stable_key(p): p for entry in manifest["files"] for p in load_json(ROOT / entry["path"])}

    def validate(self, definition=None, labels=None):
        return validate_inputs(ROOT, definition or self.definition, labels or self.labels, self.products, self.fixtures)

    def test_complete_pool_and_historical_hashes_validate(self):
        self.assertEqual(3, len(self.validate()))
        self.assertEqual(124, sum(len(q["products"]) for q in self.labels["judgments"]))

    def test_annotation_requires_actual_query_evidence(self):
        definition = copy.deepcopy(self.definition)
        definition["queries"][0]["explicitConstraints"]["maxPrice"]["evidence"] = "100만원 이하"
        with self.assertRaisesRegex(ValueError, "verbatim query evidence"):
            self.validate(definition=definition)

    def test_related_category_cannot_be_silently_promoted_to_a_hard_constraint(self):
        definition = copy.deepcopy(self.definition)
        definition["queries"][6]["expectedRelatedCategories"]["hardConstraint"] = True
        with self.assertRaisesRegex(ValueError, "related category"):
            self.validate(definition=definition)

    def test_incomplete_pool_duplicate_label_and_missing_rationale_are_rejected(self):
        for change, message in (("missing", "review every pooled"), ("duplicate", "duplicate v2 label"), ("reason", "missing reason")):
            with self.subTest(change=change):
                labels = copy.deepcopy(self.labels)
                rows = labels["judgments"][0]["products"]
                if change == "missing":
                    rows.pop()
                elif change == "duplicate":
                    rows.append(copy.deepcopy(rows[0]))
                else:
                    rows[0]["reason"] = ""
                with self.assertRaisesRegex(ValueError, message):
                    self.validate(labels=labels)

    def test_credit_blocked_run_cannot_enter_quality_comparison(self):
        definition = copy.deepcopy(self.definition)
        definition["runs"][1]["includeInQualityComparison"] = True
        with self.assertRaisesRegex(ValueError, "blocked/failed run"):
            self.validate(definition=definition)

    def test_offline_reassessment_keeps_blocked_metrics_null(self):
        result = build_reassessment(ROOT, self.products, self.fixtures)
        blocked = next(r for r in result["runs"] if r["runId"] == "agentic-rag-c795264")
        self.assertIsNone(blocked["metrics"])
        self.assertEqual(19, blocked["failedQueries"])

    def test_recorded_claude_category_mismatches_are_not_explicit_violations(self):
        result = build_reassessment(ROOT, self.products, self.fixtures)
        agent = next(r for r in result["runs"] if r["runId"] == "agentic-rag-fd7971a")["metrics"]
        self.assertEqual(2, agent["expectedCategoryMismatches"])
        self.assertEqual(0, agent["policyViolations"]["category"])
        self.assertEqual(80, agent["activeStatusUnknownProducts"])
        self.assertGreater(agent["relevance"]["irrelevant"], 0)
        self.assertGreater(agent["relevance"]["unjudged"], 0)

    def test_removed_legacy_writer_cannot_rewrite_source_commits(self):
        before = {row["path"]: content_sha256(ROOT / row["path"]) for row in self.definition["preservedHistory"]}
        result = subprocess.run([sys.executable, "-B", str(ROOT / "scripts/evaluation/validate_recommendation_evaluation.py"),
                                 "--write-agent-metrics", "--agent-commit", "0" * 40], capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("frozen", result.stderr)
        self.assertEqual(before, {path: content_sha256(ROOT / path) for path in before})

    def test_writer_rejects_changed_history_without_overwriting_existing_assessment(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copytree(ROOT / "evaluation/recommendation", root / "evaluation/recommendation")
            shutil.copytree(ROOT / "scripts/data", root / "scripts/data")
            shutil.copytree(ROOT / "scripts/evaluation", root / "scripts/evaluation")
            run_path = root / "evaluation/recommendation/runs/agentic-rag-fd7971a.json"
            run = load_json(run_path)
            run["capturedAt"] = "changed-history"
            run_path.write_text(json.dumps(run, ensure_ascii=False), encoding="utf-8")
            output = root / "evaluation/recommendation/v2/reassessment.json"
            output.write_text("existing assessment", encoding="utf-8")
            result = subprocess.run([sys.executable, "-B", str(root / "scripts/evaluation/validate_recommendation_evaluation.py"),
                                     "--write-reassessment"], capture_output=True, text=True, encoding="utf-8")
            self.assertNotEqual(0, result.returncode)
            self.assertIn("preserved history changed", result.stderr)
            self.assertEqual("existing assessment", output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
