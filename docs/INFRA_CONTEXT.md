# Infrastructure Context

## Kafka

- Image: apache/kafka:4.1.2
- Port: 19092:19092

---

## Redis

- Image: redis:7.4-alpine
- Port: 6379:6379

---

## PostgreSQL + pgvector

- Image: pgvector/pgvector:0.8.2-pg17
- Port: 5432:5432

---

## MySQL

- Image: mysql:8.4
- Port: 3307:3306

---

## Redis UI

- Image: redis/redisinsight:latest
- Port: 8002:5540

---

## Test Infrastructure

Testcontainers images:

- mysql:8.0
- redis:7-alpine