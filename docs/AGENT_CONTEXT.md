# Agent Context

## Pipeline (Strict Order)

Planner → Executor → Evaluator

Do not change execution order.

---

## Planner

- Extract user intent
- Generate structured query conditions

LLM: Claude

---

## Executor

- Perform SQL filtering
- Perform Hybrid Search (BM25 + Vector)

LLM usage is optional and should be minimized.

---

## Evaluator

Validation order:

1. Product existence & ACTIVE status (inline in EvaluatorChain via findAllById)
2. ConsistencyValidator
3. RuleFilterValidator
4. PriceConstraintValidator

Rules:

- Step 1~4 failures → discard result

---

## LLM Constraints

- Timeout: 5 seconds
- Must implement fallback logic