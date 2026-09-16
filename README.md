# AgentCart

Agentic RAG 기반 상품 추천을 구현하는 개인 실습 프로젝트입니다. 자연어 질의로 상품을 찾고, 추천 이유와 가격을 확인하는 커머스 서비스를 만듭니다.

**현재 HTTP 추천 경로는 단일 에이전트가 제한된 `searchCatalog`를 호출하고 필요할 때 한 번 재검색하는 Agentic RAG 흐름입니다.** Backend와 Frontend는 `status | result | done | error` SSE 계약을 사용하며 연결 종료와 timeout을 에이전트 취소 상태로 전달합니다.

## 현재 구현

- MySQL FULLTEXT와 pgvector 검색 결과를 순위 기반으로 결합합니다.
- OpenAI 채팅 모델로 질의를 확장하고 후보별 추천 이유를 생성합니다.
- 상품 상태, 카테고리, 최근 주문, 가격 조건을 검증합니다. 현재 fallback의 한계는 [추천 파이프라인](docs/RECOMMENDATION_PIPELINE.md)에 기록합니다.
- 추천 전체 계산 후 상품별 SSE 메시지를 전송하고 Kafka로 추천 이력을 적재합니다.
- 회원·인증, 상품 CRUD, 장바구니, 주문·재고 처리, Mock 결제가 구현되어 있습니다.

## 기술 구성

| 영역 | 현재 구성 | 기준 파일 |
|---|---|---|
| Backend | Java 25, Spring Boot 4.0.6, Spring AI 2.0.1 | [build.gradle](backend/AgentCart/build.gradle) |
| Frontend | Next.js 15.5.18, React 19.1.0, TypeScript | [package.json](frontend/package.json) |
| Chat / Embedding | 로컬 설정 기준 OpenAI `gpt-4o-mini` / Ollama `bge-m3` | [Backend Context](docs/BACKEND_CONTEXT.md) |
| 저장소 | MySQL, PostgreSQL + pgvector, Redis | [Compose](docker-compose.yml) |
| 이벤트 | Kafka, 추천 이력 Consumer | [Infrastructure Context](docs/INFRA_CONTEXT.md) |
| 테스트 | JUnit·Mockito·Testcontainers, Vitest·Testing Library | [개발 가이드](docs/DEVELOPMENT.md) |

라이브러리의 세부 버전은 빌드 파일과 lockfile을 기준으로 확인합니다. Spring AI는 안정 버전 `2.0.1`을 사용합니다.

## 추천 흐름

```text
GET /api/recommendations/stream?query=...
  → RecommendationAgentService: 명시 조건 보존과 검색 판단
  → searchCatalog: 제한된 Hybrid 검색과 정책 검증
  → 필요할 때 검색어를 바꿔 한 번 재검색
  → status / result* / done 또는 error 전송
  → 전송에 성공한 result만 recommendation.served 이벤트로 이력 저장
```

현재 브라우저 SSE는 `EventSource`와 토큰 query parameter를 사용합니다. 일반 API의 Bearer 헤더 방식과 구분해야 합니다. [인증 가이드](docs/AUTH.md)

검색 공식·임계값·timeout·SSE의 현행 계약은 [추천 파이프라인](docs/RECOMMENDATION_PIPELINE.md)을 참조합니다.

## Agentic RAG 진행 상태

단일 추천 에이전트가 읽기 전용 `searchCatalog`를 최대 두 번 호출하고 모델이 검색어와 재검색 여부를 결정하는 실행 계약을 구현했습니다. 기존 검색·상품 조회·검증은 도구 내부에서 재사용하며 HTTP/SSE 요청도 이 서비스에 연결되었습니다.

명시 조건 보존, 근거가 없을 때 결과 없음 처리, 실행 상한과 평가 기준은 [Agentic RAG 구현 계획](docs/AGENTIC_RAG_PLAN.md)이 기준입니다.

고정 snapshot의 실제 Agent 평가 harness와 호출·token·지연 계측을 추가했습니다. 2026-09-16 실행은 OpenAI credit 부족으로 모델 의존 질의를 완료하지 못했으므로 품질 비교와 기본 경로 승인은 [평가 실행 기록](evaluation/recommendation/AGENTIC_RAG_EVALUATION_2026-09-16.md)에 따라 재실행해야 합니다.

## 데이터와 실행

- [상품 JSON](scripts/data/) 8개 파일에 합계 500개 항목이 있습니다. 실제 DB 적재 수는 별도 확인이 필요합니다.
- 1차 지원 시나리오와 평가 계획: [추천 시나리오](docs/RECOMMENDATION_SCENARIOS.md)
- 인프라·로컬 설정·실행 순서: [로컬 실행 가이드](docs/LOCAL_SETUP.md)
- 프런트엔드 작업 안내: [Frontend README](frontend/README.md)

현재 임베딩 재생성은 Backend의 `bge-m3` 경로를 기준으로 합니다. 이전 OpenAI 방식인 `scripts/generate_embeddings.py`와 혼용하지 않도록 [실행 가이드](docs/LOCAL_SETUP.md)를 확인합니다.

## 문서와 개발 지침

[문서 인덱스](docs/README.md)에서 각 문서의 책임과 읽는 순서를 확인할 수 있습니다.

- 공통 개발 규칙: [DEVELOPMENT.md](docs/DEVELOPMENT.md)
- Codex 진입 지침: [AGENTS.md](AGENTS.md)
- Claude Code 진입 지침: [CLAUDE.md](CLAUDE.md)
- 작업별 공통 가이드: [docs/skills](docs/skills/README.md)
- 공유 작업 템플릿: [docs/prompts](docs/prompts/README.md)
