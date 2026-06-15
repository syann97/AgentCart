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
2. CategoryValidator (질의에서 추출한 categories와 상품 카테고리 일치; categories 비었으면 통과)
3. RuleFilterValidator
4. PriceConstraintValidator

Rules:

- Step 1~4 failures → discard result
- 카테고리 필터가 결과를 전부 비우면 카테고리 없이 1회 재시도(graceful fallback); 그 외 하드 규칙(재고·가격)은 유지

---

## LLM Constraints

- Timeout: 5 seconds
- Must implement fallback logic