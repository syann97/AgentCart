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
- 모델 호출 예외·모델 부재·구조화 응답 실패 시 취소와 deadline을 확인한 뒤 남은 검색 상한 안에서 같은 요청 컨텍스트의 원본 질의 일반 검색으로 fallback합니다. 추가 모델 재시도는 하지 않습니다. 후보별 추천 이유 LLM 호출은 이 에이전트 경로에서 사용하지 않습니다.
- 첫 검색·재검색·fallback 검색의 `SUCCESS/EMPTY/ERROR`는 서버가 분기합니다. `ERROR`는 모델에 전달하지 않고 즉시 `FAILED`로 종료합니다. embedding 오류는 `EMBEDDING_FAILED`, 저장소 오류는 `SEARCH_REPOSITORY_FAILURE`, 검색 deadline은 `DEADLINE_EXCEEDED`, 기타 도구 오류·파싱 실패는 `PROCESSING_FAILED`입니다. 이전 후보가 있어도 새 검색 오류를 정상 결과로 숨기지 않습니다.
- fallback 원본 검색이 이미 실행됐거나 검색 2회 예산이 소진됐으면 해당 요청의 성공한 검색에서 정책 검증을 통과한 후보만 재사용합니다. 후보가 없으면 빈 `FALLBACK`입니다. 추가 fallback 검색이 정상 `EMPTY`이면 이전 검증 후보는 유지하고, 이전 후보도 없을 때만 빈 `FALLBACK`으로 종료합니다. 검색 장애와 구분하며 가격·명시 카테고리·회원 문맥과 요청 deadline은 재사용·추가 검색 모두 유지합니다.

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
- 검색 인프라 장애의 `error.code`는 `EMBEDDING_FAILED` 또는 `SEARCH_REPOSITORY_FAILURE`이며 `retryable=true`입니다. 이는 사용자 재시도 가능 안내이고 자동 모델·검색 재시도가 아닙니다. Frontend의 문자열 code 타입은 이 값을 그대로 보존합니다.
- emitter completion·timeout·error와 전송 실패는 취소 토큰에 반영합니다. 연결이 이미 끊어졌다면 terminal 전송보다 추가 실행 중단을 우선합니다.

브라우저 구독은 `EventSource`와 토큰 query parameter를 사용합니다. [AUTH](AUTH.md)의 실제 인증 계약을 따릅니다.

## 이력과 데이터

성공적으로 전송한 상품별로 `recommendation.served` 이벤트를 발행합니다. Consumer는 이벤트의 `eventId`를 MySQL 이력과 함께 저장하며, `event_id` unique 제약으로 같은 ID의 재전달·동시 저장에서 최대 한 건만 커밋합니다. Redis `rec:event:` 키는 사용하지 않으므로 키 만료·소실·Redis 장애가 이력 저장의 중복 판단에 영향을 주지 않습니다.

저장은 repository의 `saveAndFlush` 트랜잭션에서 완료합니다. Consumer 자체는 트랜잭션으로 감싸지 않으며, 무결성 오류가 나면 저장 트랜잭션이 rollback된 뒤 별도 조회로 같은 `eventId`의 커밋 이력이 있는지 확인합니다. 이력이 있으면 중복 처리로 종료하고, 없거나 DB 저장·조회가 실패하면 예외를 전달해 Kafka 재전달이 가능하게 합니다. 재전달은 실제 재시도·offset·보관 정책에 의존하므로 모든 실패 이벤트의 최종 저장을 보장하지 않습니다. 잘못된 JSON은 기존처럼 로그 후 무시하고, 이벤트 ID 누락·공백은 저장 실패로 처리합니다.

[V10 migration](../backend/AgentCart/src/main/resources/db/migration/V10__add_recommendation_history_event_id.sql)은 기존 이력을 보존하고 nullable `event_id`를 추가합니다. 과거 이력과 기존 직접 저장 경로의 ID는 NULL이며 여러 건을 허용합니다. 이 경로에는 이벤트 멱등성 보장이 없고, 회원별 최근 20건 조회는 두 종류의 이력을 함께 최신순으로 반환합니다. 이 보장은 Consumer의 MySQL 저장 경계에 한정되며 Producer 전달 보장이나 Kafka·Redis·MySQL 전체의 exactly-once를 의미하지 않습니다.

상품 임베딩의 현행 기준은 `bge-m3`, 1024차원이며 상품명·설명·카테고리·브랜드를 사용합니다. 생성 실패 시 적재가 누락될 수 있고 삭제된 상품의 벡터 정리도 현재 보장되지 않습니다. 재생성 경로와 이전 스크립트 차이는 [LOCAL_SETUP](LOCAL_SETUP.md)을 참조합니다.

## 후속 작업으로 남은 항목

고정 평가셋의 Claude 실제 실행은 호출 상한을 지켰고, v2 재평가에서 허용 Hit@5 1.0과 명시 정책 위반 0건을 기록했습니다. S07·S10의 기대 카테고리 불일치 2건은 사용자가 명시한 카테고리 제약이 아니므로 정책 위반과 분리합니다. 원본 실행과 v1 판정은 보존하며, assistant 판정·미판정 상품과 모델 간 단회 비교의 한계는 [추천 평가 README](../evaluation/recommendation/README.md)에 기록합니다.

상품 선택과 추천 이유의 직접 근거 기준은 [grounding-v1](../evaluation/recommendation/grounding-v1.json)에 세 문제 사례와 두 대조 사례로 고정했습니다. #198의 실제 모델 전후 비교에서 허용 Hit@5 17/17과 정책 위반 0건을 유지했고, S10·R01·R03 문제는 전체 실행과 focused 반복을 합쳐 변경 전 3/3에서 변경 후 0/3으로 줄었습니다. 이는 assistant rubric과 실행에 포함된 조합에 한정된 결과이며 새 상품·질의 조합은 미판정일 수 있습니다. 상세 실행·source hash·판정 한계는 [grounding 평가 보고서](../evaluation/recommendation/GROUNDING_EVALUATION_2026-09-18.md)에 기록합니다.

#210의 [확대 검토](../evaluation/recommendation/EXPANDED_REVIEW_2026-10-01.md)는 보관된 전체 2회·집중 4회에서 상품 조합 92개와 이유 197건을 검토했습니다. v3 label + #209 scorer 재채점에서 전체 실행의 허용 Hit@5는 전후 17/17, 정책 위반·status/stock unknown은 0입니다. 관련성 미판정은 전후 2/80·0/72이고 허용 상품 비율 하한–상한은 96.25–98.75%·100–100%입니다. 그러나 이유는 전후 supported/unsupported/unjudged 75/5/0·65/2/5, 전체 이유 supported 하한–상한 93.75–93.75%·90.28–97.22%입니다. 방수에서 방풍을 단정한 이유와 상품 용도를 설명하지 않는 정책 fallback이 남으므로 모든 이유의 정확성을 주장하지 않습니다. 이는 새 모델 실행이나 런타임 개선이 아닌 assistant 사후 검토이며, 기존 token·지연 증가와 단회 비교 한계를 유지합니다.
