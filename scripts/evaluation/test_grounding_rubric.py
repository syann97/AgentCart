import copy
import unittest
from pathlib import Path

from reassess_recommendation_evaluation import load_json, stable_key
from validate_recommendation_evaluation import validate_grounding_rubric


ROOT = Path(__file__).resolve().parents[2]
EVALUATION = ROOT / "evaluation/recommendation"


class GroundingRubricTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        manifest = load_json(EVALUATION / "snapshot-manifest.json")
        cls.products = {
            stable_key(product): product
            for entry in manifest["files"]
            for product in load_json(ROOT / entry["path"])
        }
        cls.queries = load_json(EVALUATION / "queries.json")
        cls.labels = load_json(EVALUATION / "v2/labels.json")
        cls.source_run = load_json(EVALUATION / "runs/agentic-rag-fd7971a.json")
        cls.rubric = load_json(EVALUATION / "grounding-v1.json")

    def validate(self, rubric=None):
        return validate_grounding_rubric(
            rubric or self.rubric, self.queries, self.products, self.labels, self.source_run)

    def test_frozen_problem_and_contrast_cases_match_source_evidence(self):
        self.assertEqual([], self.validate())

    def test_changed_reason_or_relevance_is_rejected(self):
        for field, value, message in (
                ("reasonBefore", "바뀐 이유", "source run"),
                ("relevanceBefore", "relevant", "v2 label")):
            with self.subTest(field=field):
                rubric = copy.deepcopy(self.rubric)
                rubric["cases"][0][field] = value
                self.assertTrue(any(message in error for error in self.validate(rubric)))

    def test_assistant_review_cannot_be_marked_human_validated(self):
        rubric = copy.deepcopy(self.rubric)
        rubric["review"]["humanValidated"] = True
        self.assertTrue(any("provenance" in error for error in self.validate(rubric)))


if __name__ == "__main__":
    unittest.main()
