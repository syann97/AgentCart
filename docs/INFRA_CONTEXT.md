# Infrastructure Context

현재 로컬 인프라의 기준은 [docker-compose.yml](../docker-compose.yml)입니다. 애플리케이션 설정과 테스트용 container는 별도 출처를 사용하므로 버전과 수명을 한 값으로 합치지 않습니다.

## 로컬 Compose

| service | image | host port | 용도 | volume |
|---|---|---:|---|---|
| MySQL | `mysql:8.4` | 3307 | 회원·상품·장바구니·주문·결제·추천 이력 | `mysql-data` |
| Redis | `redis:7.4-alpine` | 6379 | refresh token, 질의 cache, 이벤트 중복 억제, 재고 lock | 없음 |
| PostgreSQL | `pgvector/pgvector:0.8.2-pg17` | 5432 | 상품 embedding | `postgres-data` |
| Kafka | `apache/kafka:4.1.2` | 19092 | `recommendation.served` 이벤트 | 없음 |
| RedisInsight | `redis/redisinsight:latest` | 8002 | 로컬 Redis 확인 | 없음 |
| Ollama | `ollama/ollama:latest` | 11434 | `bge-m3` embedding | `ollama-data` |

`latest` image는 고정 버전이 아닙니다. 재현 가능한 환경이 필요하면 별도 변경에서 검증한 tag로 고정합니다. 현재 Compose에는 healthcheck와 애플리케이션 service가 없습니다. 실행 순서는 [LOCAL_SETUP](LOCAL_SETUP.md)을 따릅니다.

## Redis key와 시간 설정

| key / 기능 | 현재 값 | 의미 | 출처 |
|---|---:|---|---|
| `rt:member:{memberId}` | 7일 | 회원 → refresh token | [RefreshTokenRedisRepository](../backend/AgentCart/src/main/java/com/agentcart/auth/redis/RefreshTokenRedisRepository.java) |
| `rt:token:{token}` | 7일 | refresh token → 회원 | 같은 파일 |
| `rec:query:{sha256}` | 5분 | 질의 확장 결과 cache | [QueryEnrichmentService](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/QueryEnrichmentService.java) |
| `rec:event:{eventId}` | 24시간 | Kafka event 중복 처리 억제 | [RecommendationServedConsumer](../backend/AgentCart/src/main/java/com/agentcart/recommendation/consumer/RecommendationServedConsumer.java) |
| `inventory:product:{productId}` | 획득 대기 5초 | 주문 상품별 Redisson lock | [InventoryLockService](../backend/AgentCart/src/main/java/com/agentcart/order/service/InventoryLockService.java) |

재고 lock은 `tryLock(waitTime, unit)` overload를 사용하고 고정 lease time을 코드에 지정하지 않습니다. 획득 대기 5초를 cache TTL이나 lock 유지 시간으로 설명하지 않습니다.

Access token 30분, SSE emitter 60초, 상품 embedding 5초, 후보별 추천 이유 10초도 서로 다른 경계입니다. 인증 값은 [AUTH](AUTH.md), 추천 값은 [현재 추천 파이프라인](RECOMMENDATION_PIPELINE.md)이 담당합니다. [목표 설계](AGENTIC_RAG_PLAN.md)의 요청 전체 30초는 아직 구현되지 않은 값입니다.

## 데이터 연결과 migration

- Spring의 기본 `DataSource`와 Flyway는 MySQL을 사용합니다.
- pgvector는 [PgVectorJdbcConfig](../backend/AgentCart/src/main/java/com/agentcart/config/PgVectorJdbcConfig.java)의 별도 JDBC template과 [PgVectorDataSourceConfig](../backend/AgentCart/src/main/java/com/agentcart/config/PgVectorDataSourceConfig.java)의 별도 Flyway runner를 사용합니다.
- pgvector migration 위치는 `classpath:db/pgvector-migration`입니다. V4에서 1536차원 데이터를 비우고 1024차원으로 변경합니다.
- Redis의 event key 기록과 MySQL 추천 이력 저장은 하나의 원자적 transaction이 아니므로 exactly-once 저장을 보장하지 않습니다.

## Testcontainers

통합·migration 테스트는 필요한 조합으로 `mysql:8.0`, `redis:7-alpine`, `pgvector/pgvector:0.8.2-pg17`을 선언합니다. Compose의 MySQL·Redis tag와 같다고 가정하지 않습니다. 실제 선언은 `backend/AgentCart/src/test`의 각 테스트가 기준입니다.
