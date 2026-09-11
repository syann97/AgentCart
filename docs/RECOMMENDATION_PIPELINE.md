# 현재 추천 파이프라인

상태: **현재 소스에 구현된 고정형 RAG 흐름**. 다음 단계의 도구 호출 구조와 실행 상한은 [목표 설계](AGENTIC_RAG_PLAN.md)에 있으며 아직 적용되지 않았습니다.

## 진입점

- `GET /api/recommendations/stream?query=...`: 인증된 회원의 추천 요청
- `GET /api/recommendations/history`: 회원별 최근 20건의 추천 이력

출처: [RecommendationController](../backend/AgentCart/src/main/java/com/agentcart/recommendation/controller/RecommendationController.java), [RecommendationService](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/RecommendationService.java).

## 처리 순서

1. `QueryEnrichmentService`가 LLM으로 한국어 확장 키워드, 카테고리, 가격 범위를 추출합니다.
2. `RecommendationService`가 **원본 자연어 질의**를 Ollama 임베딩 모델에 전달합니다.
3. `HybridSearchService`가 확장 키워드의 MySQL FULLTEXT 결과와 pgvector 결과를 결합합니다.
4. `EvaluatorChain`이 상품을 조회하고 정책을 적용합니다. 통과 후보 중 최대 5개를 선택합니다.
5. `LlmReasoningService`가 후보마다 추천 이유와 `conditions`를 생성합니다.
6. 전체 결과가 준비된 뒤 Controller가 상품별 SSE 메시지를 전송하고 Kafka 이벤트를 발행합니다.

모델이 검색 도구를 호출하거나 검색 결과를 보고 새 검색어를 결정하는 반복은 현재 없습니다. 카테고리 fallback은 같은 후보에 필터를 다시 적용하는 동작이며 재검색이 아닙니다.

## 질의 해석과 캐시

[QueryEnrichmentService](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/QueryEnrichmentService.java)는 `openAiChatModel`을 사용합니다. 로컬 모델 설정은 [Backend Context](BACKEND_CONTEXT.md)에 설명합니다.

- 출력은 [EnrichedQuery](../backend/AgentCart/src/main/java/com/agentcart/recommendation/dto/EnrichedQuery.java)의 `enrichedQuery`, `bm25Keywords`, `categories`, `minPrice`, `maxPrice`입니다.
- 응답 문자열의 첫 `{`부터 마지막 `}`까지를 JSON으로 파싱합니다. 카테고리·가격 값의 의미 검증은 별도로 구현되어 있지 않습니다.
- 프롬프트는 [13개 카테고리](RECOMMENDATION_SCENARIOS.md)를 요구하지만 서버의 enum 검증으로 강제하는 상태는 아닙니다.
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

현재 가격·카테고리·주문 정책 검증은 검색 후보 제한 **이후**에 실행됩니다. 조건에 맞는 상품이 카탈로그에 있어도 상위 후보 밖이면 누락될 수 있습니다.

## 검증과 fallback

출처: [EvaluatorChain](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/EvaluatorChain.java).

1. 상품 존재와 `ACTIVE` 상태
2. `CategoryValidator`: 카테고리 목록이 있으면 일치 여부 확인
3. `RuleFilterValidator`: `SOLD_OUT`, 최근 7일 주문 상품 제외
4. `PriceConstraintValidator`: 추출된 가격 범위 검증

통과 결과가 없고 카테고리가 지정되어 있으면, 같은 후보를 카테고리 없이 다시 검증합니다. 현재는 명시 조건과 추론 조건을 구분하지 않습니다. 가격 규칙도 추출된 값이 있을 때만 적용됩니다.

`ACTIVE` 상태 검증과 별도로 `stock > 0`을 직접 검사하는 로직은 현재 evaluator에 없습니다. [목표 정책](AGENTIC_RAG_PLAN.md)은 이를 포함하며 현재 보장으로 간주하지 않습니다.

## 추천 이유와 실행 시간

출처: [LlmReasoningService](../backend/AgentCart/src/main/java/com/agentcart/recommendation/service/LlmReasoningService.java), [ProductEmbeddingService](../backend/AgentCart/src/main/java/com/agentcart/product/service/ProductEmbeddingService.java), [application.yaml](../backend/AgentCart/src/main/resources/application.yaml).

| 항목 | 현재 값 / 적용 범위 |
|---|---|
| 추천 개수 | 최대 5개 |
| 이유 생성 | 후보마다 LLM 호출, semaphore 동시 실행 3개 |
| 이유 생성 timeout | 후보별 Future 대기 10초 |
| 상품 임베딩 생성 timeout | `app.embedding.timeout-seconds`, 기본 5초 |
| 추천 질의 임베딩 / 질의 확장 | 각 서비스에 별도의 명시적 timeout wrapper 없음 |
| SSE emitter | 60초 |
| 요청 전체 실행 상한 | 서비스 차원의 통합 deadline 없음 |

이유는 `REASON:`과 `CONDITIONS:` 텍스트를 파싱합니다. 실제 가격 대신 가격대 레이블을 모델에 제공하고, conditions의 가격 형태 문자열을 제거합니다. 생성 문장이 상품 사실에 부합하는지를 검증하는 기능은 아직 없습니다.

Future의 timeout이나 SSE 연결 종료가 실행 중인 외부 호출을 모두 취소한다는 보장은 없습니다.

## 현재 SSE 계약

일반 REST의 `ApiResponse`와 달리 SSE는 아래 JSON을 상품마다 `data:` 메시지로 전송합니다.

```json
{
  "type": "complete",
  "data": {
    "productId": 1,
    "productName": "예시 상품",
    "price": 45000,
    "reason": "추천 이유",
    "conditions": [],
    "score": 1.0
  }
}
```

이 예시는 스키마 설명이며 실제 추천 결과가 아닙니다.

- `complete`는 현재 **상품 한 개의 완성된 결과**를 뜻합니다. 전체 요청 종료 이벤트가 아닙니다.
- 전체 리스트 계산이 끝난 후 메시지를 전송하며, 단계별 진행 이벤트와 `done` 메시지는 없습니다.
- 결과가 0개이면 결과 메시지 없이 연결을 종료합니다. 예외는 `completeWithError`로 처리합니다.
- [프런트 타입](../frontend/src/features/recommendation/types/recommendation.types.ts)은 `partial | complete | error`를 선언하지만 Backend가 세 타입을 모두 발행하는 것은 아닙니다.
- [구독 훅](../frontend/src/features/recommendation/hooks/use-recommendation-stream.ts)은 `complete`를 누적하고 오류 콜백으로 종료를 처리하여 정상 종료·오류 구분이 충분하지 않습니다.

브라우저 구독은 `EventSource`와 토큰 query parameter를 사용합니다. [AUTH](AUTH.md)의 실제 인증 계약을 따릅니다.

## 이력과 데이터

전송한 상품별로 `recommendation.served` 이벤트를 발행합니다. Consumer는 Redis `rec:event:` 키를 24시간 보관해 같은 eventId의 재처리를 억제한 뒤 MySQL 이력을 저장합니다. Redis 처리와 DB 저장은 하나의 원자적 트랜잭션이 아니므로 exactly-once 저장을 보장한다고 설명하지 않습니다.

상품 임베딩의 현행 기준은 `bge-m3`, 1024차원이며 상품명·설명·카테고리·브랜드를 사용합니다. 생성 실패 시 적재가 누락될 수 있고 삭제된 상품의 벡터 정리도 현재 보장되지 않습니다. 재생성 경로와 이전 스크립트 차이는 [LOCAL_SETUP](LOCAL_SETUP.md)을 참조합니다.

## 후속 작업으로 남은 항목

명시 조건 유실, 무조건 카테고리 완화, 검색 전 조건 적용, 근거 검증, 실행 취소, SSE 종료 계약, 모델 호출 루프는 [승인된 계획](AGENTIC_RAG_PLAN.md)에 따라 코드 변경과 회귀 검증이 필요합니다. 이 문서 정비만으로 해결된 항목이 아닙니다.
