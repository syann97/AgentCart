# Backend Rules

## General Rules

- Follow layered architecture strictly.
- Do not bypass service layer from controllers.
- Do not place business logic in repositories.

---

## Data Source Rules

- MySQL handles business data only.
- PostgreSQL handles vector data only.
- Do not mix data sources.

---

## Hybrid Search Rules

Execution order:

1. SQL filtering
2. BM25 + Vector reranking

Score formula:

score = 0.4 * BM25 + 0.6 * Vector

Rules:

- Normalize all scores to range 0~1
- SQL filtering must execute before vector search

---

## Kafka Rules

- Always implement both Producer and Consumer
- Include eventId using UUID v4
- Consumers must ensure idempotency
- Event processing must be retry-safe

---

## Redis Rules

- TTL must be 5 minutes
- Distributed lock timeout must be 5 seconds
- Use Redisson for distributed locks

If lock acquisition fails:

- throw exception
- retry request

---

## LLM Rules

- LLM must be optional
- System must work without LLM response
- Always implement fallback logic
- LLM timeout must be 5 seconds

---

## Evaluator Rules

Validation order:

1. DbValidator
2. ConsistencyValidator
3. RuleFilterValidator

Rules:

- Step 1~3 failure → discard result

---

## Recommendation Rules

Recommendation results must include:

- reason
- conditions
- score