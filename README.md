# AgentCart

Agentic RAG 기반 상품 추천을 구현하는 개인 실습 프로젝트입니다. 자연어 질의로 상품을 찾고, 추천 이유와 가격을 확인하는 커머스 서비스를 만듭니다.

**현재 구현은 질의 확장 → Hybrid 검색 → 규칙 검증 → 추천 이유 생성의 고정형 RAG 파이프라인입니다.** 승인된 다음 목표는 단일 에이전트가 검색 도구를 사용하고 필요할 때 한 번 재검색하는 구조입니다. 도구 호출 루프와 단계별 진행 이벤트는 아직 구현되지 않았습니다.

## 현재 구현

- MySQL FULLTEXT와 pgvector 검색 결과를 순위 기반으로 결합합니다.
- OpenAI 채팅 모델로 질의를 확장하고 후보별 추천 이유를 생성합니다.
- 상품 상태, 카테고리, 최근 주문, 가격 조건을 검증합니다. 현재 fallback의 한계는 [추천 파이프라인](docs/RECOMMENDATION_PIPELINE.md)에 기록합니다.
- 추천 전체 계산 후 상품별 SSE 메시지를 전송하고 Kafka로 추천 이력을 적재합니다.
- 회원·인증, 상품 CRUD, 장바구니, 주문·재고 처리, Mock 결제가 구현되어 있습니다.

## 기술 구성

| 영역 | 현재 구성 | 기준 파일 |
|---|---|---|
| Backend | Java 25, Spring Boot 4.0.6, Spring AI 2.0.0-M5 | [build.gradle](backend/AgentCart/build.gradle) |
| Frontend | Next.js 15.5.18, React 19.1.0, TypeScript | [package.json](frontend/package.json) |
| Chat / Embedding | 로컬 설정 기준 OpenAI `gpt-4o-mini` / Ollama `bge-m3` | [Backend Context](docs/BACKEND_CONTEXT.md) |
| 저장소 | MySQL, PostgreSQL + pgvector, Redis | [Compose](docker-compose.yml) |
| 이벤트 | Kafka, 추천 이력 Consumer | [Infrastructure Context](docs/INFRA_CONTEXT.md) |
| 테스트 | JUnit·Mockito·Testcontainers, Vitest·Testing Library | [개발 가이드](docs/DEVELOPMENT.md) |

라이브러리의 세부 버전은 빌드 파일과 lockfile을 기준으로 확인합니다. Spring AI 안정 버전 전환은 후속 호환성 검증 작업이며 현재 적용된 상태가 아닙니다.

## 추천 흐름

```text
GET /api/recommendations/stream?query=...
  → QueryEnrichmentService: 키워드·가격·카테고리 추출
  → 원본 질의 임베딩 + HybridSearchService
  → EvaluatorChain: 후보 검증, 최대 5개 선택
  → LlmReasoningService: 후보별 추천 이유 생성
  → 상품별 complete 메시지 전송 후 SSE 연결 종료
  → recommendation.served 이벤트 → 추천 이력 저장
```

현재 브라우저 SSE는 `EventSource`와 토큰 query parameter를 사용합니다. 일반 API의 Bearer 헤더 방식과 구분해야 합니다. [인증 가이드](docs/AUTH.md)

검색 공식·임계값·timeout·SSE의 현행 계약은 [추천 파이프라인](docs/RECOMMENDATION_PIPELINE.md)을 참조합니다.

## 승인된 다음 목표

단일 추천 에이전트와 읽기 전용 `searchCatalog` 도구를 도입합니다. 기존 검색·상품 조회·검증을 도구 내부에서 재사용하고, 모델이 검색어와 재검색 여부를 결정합니다.

명시 조건 보존, 근거가 없을 때 결과 없음 처리, 실행 상한, 평가 기준은 [Agentic RAG 구현 계획](docs/AGENTIC_RAG_PLAN.md)이 기준입니다. [#173](https://github.com/syann97/AgentCart/issues/173)은 이 설계를 기준으로 문서의 정합성을 먼저 맞추는 작업입니다.

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
