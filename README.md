# AgentCart

> LLM을 *호출하는* 서비스가 아니라, **역할이 분리된 LLM 파이프라인으로 구매 의사결정을 보조하는** AI 커머스 백엔드.

자연어 질의(예: `"돌잔치 선물 5만원 이하"`)를 입력하면 **Planner → Executor → Evaluator** 파이프라인이 의도를 구조화하고, Hybrid 검색(키워드 + 벡터)으로 후보를 찾고, 규칙 기반으로 검증한 뒤, **재현 가능한 추천 근거**와 함께 SSE로 스트리밍합니다.

---

## 핵심 특징

- **Hybrid RAG 검색** — MySQL FULLTEXT(키워드) + pgvector(의미 유사도)를 RRF로 융합
- **역할 분리 파이프라인** — 의도 분석 / 검색 / 검증을 독립 컴포넌트로 분리해 단계별 디버깅·테스트 가능
- **Explainability** — 추상 점수가 아닌 추천 이유 문장 + 조건 매칭(conditions)을 함께 제공
- **이벤트 기반 이력 적재** — 추천 결과를 Kafka로 비동기 발행, Consumer가 멱등 처리 후 이력 저장
- **SSE 스트리밍** — 검색 결과를 도착하는 대로 클라이언트로 전송
- **장애 격리** — LLM 호출은 timeout + fallback으로 감싸 LLM 실패 시에도 시스템이 동작

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.6 / Spring Framework 7.0.7 / Spring Security 7.0.5 |
| AI | Spring AI 2.0.0-M5 (OpenAI · Anthropic · Ollama starter) |
| Chat LLM | OpenAI `gpt-4o-mini` (Planner / 추천 근거 생성) |
| Embedding | Ollama `bge-m3` (한국어 임베딩 품질 목적) |
| Vector Store | PostgreSQL + pgvector 0.8.2 |
| RDB | MySQL 8.4 (상품 / 주문 / 회원 / 추천 이력) |
| Cache / Lock | Redis 7.4 + Redisson 4.3.1 (분산 락) |
| Messaging | Apache Kafka 4.1 (Spring Kafka) |
| Auth | JJWT 0.13.0 (JWT) |
| Migration | Flyway (MySQL + PostgreSQL) |
| Observability | Spring Actuator + Micrometer (Prometheus registry) |
| Serialization | Jackson 3.x (`tools.jackson`) |
| Test | JUnit 5 + Testcontainers |
| Frontend | Next.js 15.5 / React 19 / TypeScript / TanStack Query |

> Spring AI는 OpenAI·Anthropic·Ollama 스타터를 모두 구성해 두었으며, 현재 채팅 추론은 OpenAI, 임베딩은 Ollama 모델을 사용합니다.

---

## 시스템 아키텍처

```
                        ┌─────────────────────────────┐
   Browser (Next.js)    │  Authorization: Bearer <AT> │
        │  SSE          └─────────────────────────────┘
        ▼
┌───────────────────────────────────────────────────────────┐
│                  Spring Boot 4 / Java 25                    │
│                                                            │
│  Auth · Member · Product · Cart · Order · Payment          │
│                                                            │
│  ┌──────────────── Recommendation ─────────────────────┐  │
│  │  Planner ──► Executor ──► Evaluator ──► Reasoning    │  │
│  │  (LLM)       (Hybrid)     (Rule chain)  (LLM)        │  │
│  └─────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────┘
        │           │            │            │
        ▼           ▼            ▼            ▼
     MySQL       pgvector      Redis        Kafka
   (도메인)     (임베딩)    (캐시/락/멱등)  (추천 이벤트)
```

---

## 도메인 구성

| 도메인 | 백엔드 | 프론트엔드 | 설명 |
|--------|:------:|:----------:|------|
| auth / member | ✅ | ✅ | JWT 인증, 회원 관리 |
| product | ✅ | ✅ | 상품 CRUD, pgvector 임베딩 |
| cart | ✅ | ✅ | 장바구니 |
| order | ✅ | ✅ | 주문 생성/상태, 재고 분산 락 |
| payment | ✅ | ✅ | Mock 결제 |
| recommendation | ✅ | ✅ | Agent 파이프라인, SSE, 이력 |

---

## 추천 파이프라인 상세

엔트리: `GET /api/recommendations/stream?query=...` (SSE) → `RecommendationService.recommend()`

```
질의 "돌잔치 선물 5만원 이하"
   │
   ▼ ① Planner — QueryEnrichmentService (LLM)
   │   · 한국어 키워드 확장(enrichedQuery / bm25Keywords)
   │   · 가격 제약(minPrice/maxPrice) 구조화 추출
   │   · 결과 Redis 캐시(TTL 5분), 실패 시 원본 질의로 fallback
   │
   ▼ ② Embedding — Ollama bge-m3 로 enrichedQuery 벡터화
   │
   ▼ ③ Executor — HybridSearchService
   │   · BM25계열: MySQL FULLTEXT MATCH...AGAINST (상위 50)
   │   · Vector  : pgvector 코사인 유사도 (상위 50, 최소 0.5)
   │   · 융합    : RRF(k=60) → score = 1/(k+bm25rank) + 1/(k+vecrank)·similarity
   │             → 임계치 필터 후 max 점수로 정규화
   │
   ▼ ④ Evaluator — EvaluatorChain (규칙 기반 4단계, 실패 시 해당 후보 제외)
   │   1. 상품 존재 & ACTIVE 상태 (findAllById 인라인)
   │   2. ConsistencyValidator
   │   3. RuleFilterValidator   (SOLD_OUT / 최근 7일 주문 상품 제외)
   │   4. PriceConstraintValidator (가격 제약 hard filter)
   │   → 상위 5개(TOP_N)
   │
   ▼ ⑤ Reasoning — LlmReasoningService (LLM)
   │   · 후보별 추천 이유 + 조건(conditions) 생성
   │   · 가상 스레드 병렬(동시 3) · 후보당 10초 timeout · 실패 시 fallback 문구
   │   · 가격 숫자 노출 방지를 위해 가격대 레이블(저가~프리미엄)로 변환
   │
   ▼ SSE 로 RecommendationResult 스트리밍
   │   { productId, productName, price, reason, conditions[], score }
   │
   ▼ ⑥ Kafka 발행 — RecommendationEventProducer → topic "recommendation.served"
       └─ RecommendationServedConsumer: eventId 멱등 처리(Redis, 24h) → 추천 이력 저장
```

이력 조회: `GET /api/recommendations/history` (최근 20건)

---

## 인증

JWT 기반 Stateless 인증. AccessToken은 `sessionStorage`, RefreshToken은 `HttpOnly` 쿠키 + Redis(TTL 7일)에 저장하며 재발급 시 Rotation을 적용합니다.

> 흐름 다이어그램·Redis 키 구조·토큰 저장 전략 등 상세 내용은 **[docs/AUTH.md](docs/AUTH.md)** 참고.

---

## 동시성 · 이벤트

- **재고 분산 락** — `InventoryLockService`가 주문 시 상품 ID 단위로 Redisson 분산 락(`tryLock`, 5초 대기)을 적용해 재고 차감 동시성을 제어합니다.
- **Kafka 이벤트** — 추천 결과를 동기 응답 경로에서 분리해 비동기로 발행하고, Consumer가 `eventId` 기반 멱등 처리 후 이력을 적재합니다.

---

## 데이터셋

- LLM 합성 **한국어 상품 데이터 500개** (추천 시나리오 역설계 기반: 정답 상품 + 노이즈 혼합)
- 각 상품에 대한 pgvector 임베딩(`bge-m3`, 한국어)
- 시딩/임베딩 스크립트: [`scripts/`](scripts/)

---

## 로컬 실행

```bash
# 1. 인프라 (MySQL, Redis, PostgreSQL+pgvector, Kafka, Ollama)
docker compose up -d

# 2. 백엔드
cd backend/AgentCart
./gradlew bootRun --args='--spring.profiles.active=local'

# 3. 프론트엔드
cd frontend
npm install
npm run dev
```

LLM 사용을 위해 환경변수 `OPENAI_API_KEY`(필요 시 `ANTHROPIC_API_KEY`)를 설정하고, Ollama에 임베딩 모델을 준비합니다.

```bash
docker exec -it <ollama-container> ollama pull bge-m3
```

---

## 문서

| 문서 | 내용 |
|------|------|
| [docs/AUTH.md](docs/AUTH.md) | 인증 시스템 상세 가이드 |
| [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md) | 프로젝트 개요 |
| [docs/BACKEND_CONTEXT.md](docs/BACKEND_CONTEXT.md) | 백엔드 스택 |
| [docs/AGENT_CONTEXT.md](docs/AGENT_CONTEXT.md) | Agent 파이프라인 규칙 |
