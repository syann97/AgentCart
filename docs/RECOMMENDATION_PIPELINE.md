# 현재 추천 파이프라인

상태: **현재 HTTP 요청 경로는 제한된 단일 에이전트 흐름**입니다. 에이전트는 `searchCatalog`를 최대 두 번 호출하며 Backend와 Frontend는 명시적인 진행·결과·종료·오류 SSE 계약을 사용합니다.

## 진입점

- `GET /api/recommendations/stream?query=...`: 인증된 회원의 추천 요청
- `GET /api/recommendations/history`: 회원별 최근 20건의 추천 이력

출처: [RecommendationController](../backend/AgentCart/src/main/java/com/agentcart/recommendation/controller/RecommendationController.java), [RecommendationAgentService](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/RecommendationAgentService.java), [RecommendationStreamService](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/RecommendationStreamService.java).

## 처리 순서

1. 인증과 빈 질의를 SSE 연결 전에 검증하고 회원 ID를 결정합니다.
2. `RecommendationRequestContextFactory`가 원본 질의에서 명시 가격·카테고리를 결정적으로 해석합니다.
3. 단일 Agent가 `searchCatalog` 인자를 만들고, 검색 시작 때 `status`를 전송합니다.
4. 도구는 MySQL 허용 상품을 먼저 제한한 뒤 FULLTEXT와 pgvector 결과를 결합하고 최신 상품 정책을 재검증합니다.
5. Agent가 후보를 선택해 통합 이유를 생성하거나, 관련성이 부족하면 다른 인자로 한 번 재검색합니다.
6. 검증된 상품마다 `result`, 정상 outcome은 결과가 0개여도 `done`, 처리 실패는 `error`로 한 번 종료합니다.
7. 성공적으로 전송한 `result`에 대해서만 Kafka 추천 이력 이벤트를 발행합니다.

## 질의 해석과 캐시

[RecommendationRequestContextFactory](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/RecommendationRequestContextFactory.java)는 모델 호출 전에 지원되는 원화 가격 표현과 13개 카테고리·승인 별칭을 결정적으로 해석합니다. 값, 원문 근거, `EXPLICIT` 출처와 원본 질의·회원 ID를 [RecommendationRequestContext](../backend/AgentCart/src/main/java/com/agentcart/recommendation/dto/RecommendationRequestContext.java)에 보존합니다. 역전된 가격 조건은 삭제하지 않고 `CLARIFICATION_REQUIRED`로 분류하며, 현행 리스트 응답에서는 검색과 모델 호출 없이 빈 결과로 종료합니다.

[QueryEnrichmentService](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/QueryEnrichmentService.java)는 비교 기준으로 유지된 기존 `RecommendationService` 경로에서 설정으로 선택한 `ChatModel`을 사용합니다. 현재 stream endpoint의 Agent 경로에서는 호출하지 않습니다.

- 출력은 [EnrichedQuery](../backend/AgentCart/src/main/java/com/agentcart/recommendation/dto/EnrichedQuery.java)의 `enrichedQuery`, `bm25Keywords`, `categories`, `minPrice`, `maxPrice`입니다.
- 응답 문자열의 첫 `{`부터 마지막 `}`까지를 JSON으로 파싱합니다. 추론 카테고리는 서버의 폐쇄형 분류로 정규화하고, 음수·역전 가격 범위는 사용하지 않습니다.
- 모델의 추론 조건은 `INFERRED`로 표시하며 같은 종류의 명시 조건을 덮어쓰지 못합니다.
- Redis `rec:query:` 캐시는 5분입니다. LLM·JSON 실패 시 원본 질의, 빈 카테고리, 가격 `null`로 fallback합니다.
- 캐시 쓰기 실패는 처리하지만 캐시 읽기에는 같은 예외 처리가 없습니다. 모든 Redis 장애를 격리한다고 설명하지 않습니다.

## 검색 방식과 점수

출처: [HybridSearchService](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/HybridSearchService.java), [ProductRepository](../backend/AgentCart/src/main/java/com/agentcart/product/repository/ProductRepository.java), [RecommendationVectorRepository](../backend/AgentCart/src/main/java/com/agentcart/recommendation/repository/RecommendationVectorRepository.java).

| 항목 | 현재 값 / 동작 |
|---|---|
| 키워드 검색 | MySQL `MATCH ... AGAINST (... IN BOOLEAN MODE)`; 메서드명은 `bm25Search` |
| 키워드 정리 | 중복 토큰과 `세트`, `선물`, `묶음`, `패키지` 제거; 전부 제거되면 원본 유지 |
| 벡터 검색 | pgvector cosine similarity, 최소 0.4 |
| 후보 제한 | 각 검색과 최종 융합 결과 최대 50개 |
| 결합 상수 | `RRF_K = 60` |
| 결합 점수 하한 | 정규화 전 `RRF_SCORE_THRESHOLD = 0.01` |

```text
rankScore(rank) = 검색 결과에 있으면 1 / (60 + rank), 없으면 0
rawScore = rankScore(keywordRank) + rankScore(vectorRank) * vectorSimilarity
score = rawScore / 남은 검색 후보의 최대 rawScore
```

코드의 순위 결합은 벡터 유사도로 가중한 RRF 방식입니다. `0.4 * BM25 + 0.6 * Vector` 공식을 사용하지 않습니다.

정규화 전 하한도 적용되므로 벡터 유사도가 0.4 이상이라고 항상 최종 후보에 남는 것은 아닙니다. 정규화된 검색 최상위 점수는 1.0이므로 확률·신뢰도로 해석하지 않습니다.

기존 고정형 `RecommendationService`는 가격·카테고리·주문 정책을 검색 후보 제한 이후에 적용합니다. 현재 Agent 경로의 `searchCatalog`는 가능한 명시 정책을 후보 제한 전에 적용하고 반환 전에 다시 검증합니다.

### 구현된 `searchCatalog` 계약

[SearchCatalogService](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/SearchCatalogService.java)와 Spring AI `ToolCallback` bean은 읽기 전용 검색 경계를 제공합니다. 현재 HTTP 경로의 `RecommendationAgentService`가 이 도구를 호출하며, 비교 기준으로 남은 `RecommendationService`는 호출하지 않습니다.

- 모델 입력은 BM25 키워드, 의미 검색어, 선택적 추론 카테고리뿐입니다. 회원 ID, 명시 조건, 요청 ID, 검색 횟수와 deadline은 별도 서버 `ToolContext`로 전달합니다.
- MySQL에서 `ACTIVE`, 양수 재고, 명시 가격·카테고리, 최근 7일 주문 제외를 적용해 허용 상품 ID를 먼저 계산합니다. 빈 집합이면 BM25·pgvector를 호출하지 않습니다.
- 같은 허용 ID 집합을 BM25와 pgvector 양쪽에 적용하고 기존 가중 RRF를 유지합니다.
- 첫 의미 검색은 모델 입력과 관계없이 원본 질의를 사용합니다. 추론 카테고리는 사전 허용 ID를 줄이지 않고 evaluator의 완화 가능한 조건으로만 사용합니다.
- 검색 결과는 최신 MySQL 상품 조회와 evaluator 정책을 다시 통과하며 최대 10개입니다. 상품 근거 ID는 `product:{id}` 형식입니다.
- 정상 결과, 결과 없음 사유, deadline·embedding·저장소 오류를 구조화된 상태로 구분합니다. 존재하지 않는 상품 참조는 저장소 장애와 다른 빈 결과 사유입니다.

### 구현된 단일 에이전트 실행

[RecommendationAgentService](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/RecommendationAgentService.java)는 transport와 분리된 도메인 결과를 반환합니다.

- 정상 흐름은 LLM 2회·검색 1회, 재검색 흐름은 LLM 3회·검색 2회로 코드에서 제한합니다. 마지막 LLM 호출에는 도구를 노출하지 않습니다.
- 회원 ID·명시 조건·request ID·검색 횟수·30초 deadline은 모델 입력이 아닌 서버 `ToolContext`로 전달합니다.
- 정규화된 동일 검색 인자는 다시 실행하지 않으며, 취소 또는 deadline 이후 새 호출을 시작하지 않습니다.
- 최종 결과는 검색 후보 ID와 `product:{id}` 근거를 검증하고 상품명·가격·카테고리·브랜드·점수는 서버 후보 데이터로 구성합니다. 최대 5개이며 0개 종료도 허용합니다.
- 최종 후보를 고를 때 사용자 원문의 대상·용도·필수 성능을 후보 상품명·설명과 대조하도록 지시합니다. 방수·방풍, 무게·휴대성, 규격, 대상 동물 호환성처럼 확인 가능한 속성은 후보 근거에 직접 있을 때만 선택과 이유에 사용하며, 카테고리 불일치만으로 근거 있는 대안을 제외하지 않습니다.
- 모델 또는 구조화 응답 실패 시 남은 검색 상한 안에서 원본 질의 일반 검색으로 fallback합니다. 후보별 추천 이유 LLM 호출은 이 에이전트 경로에서 사용하지 않습니다.

## 검증과 fallback

출처: [EvaluatorChain](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/EvaluatorChain.java).

1. 상품 존재, `ACTIVE` 상태와 `stock > 0`
2. `CategoryValidator`: 카테고리 목록이 있으면 일치 여부 확인
3. `RuleFilterValidator`: `SOLD_OUT`, 최근 7일 주문 상품 제외
4. `PriceConstraintValidator`: 추출된 가격 범위 검증

통과 결과가 없을 때 카테고리 완화는 `INFERRED` 조건에만 적용합니다. `EXPLICIT` 카테고리는 결과가 0개여도 유지합니다. 가격 규칙은 명시 조건을 우선하며, 명시 가격이 없을 때만 유효한 모델 추론값을 사용합니다.

## 실행 시간과 유지된 비교 기준

출처: [LlmReasoningService](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/LlmReasoningService.java), [ProductEmbeddingService](../backend/AgentCart/src/main/java/com/agentcart/product/service/ProductEmbeddingService.java), [application.yaml](../backend/AgentCart/src/main/resources/application.yaml).

| 항목 | 현재 값 / 적용 범위 |
|---|---|
| Agent 추천 개수 | 최대 5개 |
| Agent 전체 처리 제한 | 30초 |
| Agent LLM / 검색 | 최대 3회 / 최대 2회 |
| 기존 기준선 이유 생성 | 후보마다 LLM 호출, semaphore 동시 실행 3개 |
| 기존 기준선 이유 timeout | 후보별 Future 대기 10초 |
| 상품 임베딩 생성 timeout | `app.embedding.timeout-seconds`, 기본 5초 |
| 추천 질의 임베딩 / 질의 확장 | 각 서비스에 별도의 명시적 timeout wrapper 없음 |
| SSE emitter | 60초; completion·timeout·error를 Agent 취소에 전달 |

기존 기준선의 `LlmReasoningService`는 `REASON:`과 `CONDITIONS:` 텍스트를 파싱합니다. 현재 Agent 경로는 후보별 이유 호출을 사용하지 않고 최종 모델 응답의 후보 ID·근거를 검증한 뒤 서버 상품 사실과 결합합니다. 외부 SDK가 이미 실행 중인 호출의 interrupt를 즉시 준수한다고 보장하지는 않지만, 취소 이후 새 LLM·검색은 시작하지 않습니다.

## 현재 SSE 계약

일반 REST의 `ApiResponse`와 달리 SSE는 `{ "type": "...", "data": {...} }` 판별 유니온을 전송합니다.

| type | data | terminal |
|---|---|---|
| `status` | 검색 단계, 검색 시도 번호, 사용자 메시지 | 아니요 |
| `result` | 상품 ID·이름·가격·이유·조건·점수 | 아니요 |
| `done` | request ID, 정상 outcome, action code, 메시지, 결과 수 | 예 |
| `error` | request ID, 안정적 오류 code, 메시지, 재시도 가능 여부 | 예 |

- 살아 있는 연결은 `done` 또는 `error` 중 하나만 전송합니다. terminal 뒤에는 추가 상태·상품·Kafka 이벤트가 없습니다.
- 추천 성공, 결과 없음, 카탈로그 밖, 입력 구체화와 일반 검색 fallback은 모두 `done.outcome`으로 구분합니다.
- 처리 실패는 `error`이며 `done`이 뒤따르지 않습니다. EventSource transport 오류는 Frontend에서 서버 `error`와 별도 상태로 처리합니다.
- emitter completion·timeout·error와 전송 실패는 취소 토큰에 반영합니다. 연결이 이미 끊어졌다면 terminal 전송보다 추가 실행 중단을 우선합니다.

브라우저 구독은 `EventSource`와 토큰 query parameter를 사용합니다. [AUTH](AUTH.md)의 실제 인증 계약을 따릅니다.

## 이력과 데이터

전송한 상품별로 `recommendation.served` 이벤트를 발행합니다. Consumer는 Redis `rec:event:` 키를 24시간 보관해 같은 eventId의 재처리를 억제한 뒤 MySQL 이력을 저장합니다. Redis 처리와 DB 저장은 하나의 원자적 트랜잭션이 아니므로 exactly-once 저장을 보장한다고 설명하지 않습니다.

상품 임베딩의 현행 기준은 `bge-m3`, 1024차원이며 상품명·설명·카테고리·브랜드를 사용합니다. 생성 실패 시 적재가 누락될 수 있고 삭제된 상품의 벡터 정리도 현재 보장되지 않습니다. 재생성 경로와 이전 스크립트 차이는 [LOCAL_SETUP](LOCAL_SETUP.md)을 참조합니다.

## 후속 작업으로 남은 항목

고정 평가셋의 Claude 실제 실행은 호출 상한을 지켰고, v2 재평가에서 허용 Hit@5 1.0과 명시 정책 위반 0건을 기록했습니다. S07·S10의 기대 카테고리 불일치 2건은 사용자가 명시한 카테고리 제약이 아니므로 정책 위반과 분리합니다. 원본 실행과 v1 판정은 보존하며, assistant 판정·미판정 상품과 모델 간 단회 비교의 한계는 [추천 평가 README](../evaluation/recommendation/README.md)에 기록합니다.

상품 선택과 추천 이유의 직접 근거 기준은 [grounding-v1](../evaluation/recommendation/grounding-v1.json)에 세 문제 사례와 두 대조 사례로 고정했습니다. #198의 실제 모델 전후 비교에서 허용 Hit@5 17/17과 정책 위반 0건을 유지했고, S10·R01·R03 문제는 전체 실행과 focused 반복을 합쳐 변경 전 3/3에서 변경 후 0/3으로 줄었습니다. 이는 assistant rubric과 실행에 포함된 조합에 한정된 결과이며 새 상품·질의 조합은 미판정일 수 있습니다. 상세 실행·source hash·판정 한계는 [grounding 평가 보고서](../evaluation/recommendation/GROUNDING_EVALUATION_2026-09-18.md)에 기록합니다.
