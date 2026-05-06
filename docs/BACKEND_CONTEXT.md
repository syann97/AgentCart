# Backend Context

## Architecture

- Layered architecture
    - controller
    - service
    - repository
    - domain

---

## DataSource Rules

- MySQL → business data (Product, Order, Member, Recommendation)
- PostgreSQL → vector data (pgvector)

Do not mix data sources.

---

## Hybrid Search

Step 1: SQL filtering  
Step 2: BM25 + Vector reranking

Score formula:

score = 0.4 * BM25 + 0.6 * Vector

Both scores must be normalized (0~1).

---

## Kafka

Topics:

- recommendation-topic
- result-topic
- click-topic

Rules:

- Always implement both Producer and Consumer
- Include eventId (UUID v4)
- Consumer must ensure idempotency

---

## Redis

- TTL: 5 minutes (fixed)
- Use Redisson for distributed lock
- Lock timeout: 5 seconds

If lock fails:
→ throw exception and request retry

---

## Recommendation Result

Must include:

- reason
- conditions
- score

---

## Observability

- Log LLM token usage and cost
- Measure execution time for each agent step