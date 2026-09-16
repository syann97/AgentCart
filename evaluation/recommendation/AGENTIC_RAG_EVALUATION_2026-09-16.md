# Agentic RAG 평가 실행 기록 — 2026-09-16

## 판정

**기본 경로 전환을 승인할 수 없습니다.** 평가 코드와 snapshot은 검증됐지만 실제 OpenAI 계정의 잔여 credit이 없어 모델 의존 질의 19개가 HTTP 429로 종료됐습니다. A01은 모델 호출 전에 결정적 가격 검증으로 `CLARIFICATION_REQUIRED`를 반환했습니다.

이 결과의 0건 추천과 0 token은 Agent 품질 측정값이 아니라 provider가 요청을 처리하지 않은 결과입니다. credit을 준비한 뒤 같은 commit과 snapshot으로 다시 실행해야 합니다.

## 실행 식별자

| 항목 | 값 |
|---|---|
| 평가 harness / Agent commit | `c795264c544d25b15077558209fb0fcbc80c039f` |
| 기존 기준선 commit | `79627c080491b4d9e3d8400d4f81e8dcc40768b6` |
| snapshot | `recommendation-catalog-2026-09-15` |
| MySQL | 상품 500건, ID 195–694, manifest ID digest 일치 |
| pgvector | 500건 / 고유 상품 500건 |
| Chat 설정 | `gpt-4o-mini`; 성공 응답이 없어 provider 응답 모델 ID는 미수집 |
| Embedding | Ollama `bge-m3`, 1024차원 |
| 실행 artifact | [`runs/agentic-rag-c795264.json`](runs/agentic-rag-c795264.json) |

## 기준선 비교

| 지표 | 고정형 RAG 기준선 | Agent 실행 | 판정 |
|---|---:|---:|---|
| 완료된 모델 의존 질의 | 19 | 0 | 비교 불가 |
| 엄격 Hit@5 | 15/17 (0.882353) | 측정 불가 | 비교 불가 |
| 허용 Hit@5 | 16/17 (0.941176) | 측정 불가 | 비교 불가 |
| 가격·카테고리·재고·최근 주문 위반 | 24 | 관찰 0 | Agent 결과가 없어 안전 통과 근거로 사용 불가 |
| N01·N02 무관 상품 | 5개 | 관찰 0개 | 모델 호출 실패이므로 품질 판정 제외 |
| A01 구체화 | 실패 | 성공 | 통과 |
| LLM 호출 시도 | 105 | 19 | 각 질의 첫 호출에서 provider 거부 |
| 검색 실행 | 기준선 미기록 | 0 | 모델이 도구 인자를 만들기 전 실패 |
| token usage | 미기록 | 0 | provider가 요청을 처리하지 않아 사용량 없음 |
| 평균 / p95 지연 | 12,585.75ms / 13,572ms | 4,121.75ms / 4,965ms | Agent 값은 429 실패 응답 지연 |

## 확인된 계약

- snapshot 파일 hash, MySQL 상품 ID digest, MySQL·pgvector 500건이 기준선과 일치했습니다.
- A01은 LLM·검색 호출 없이 입력 구체화로 종료했습니다.
- 실패 실행에서도 검색 2회, LLM 3회 상한 위반과 중복 검색 실행은 없었습니다.
- mock 회귀 테스트는 timeout·취소·malformed 출력·후보 밖 ID·근거 ID·중복 ID·호출 상한을 계속 검증합니다.

## 발견한 런타임 결함

첫 실행에서 Spring AI 범용 `DefaultToolCallingChatOptions`를 OpenAI 모델이 `OpenAiChatOptions`로 캐스팅하며 모든 질의가 실패했습니다. Agent 호출 옵션을 provider 호환 타입으로 변경하고 단위 테스트를 통과시킨 뒤 실제 실행을 다시 수행했습니다. 두 번째 실행은 이 타입 오류 없이 OpenAI까지 도달했고 잔여 credit 부족 응답을 받았습니다.

## 재실행 조건

1. 현재 OpenAI API key가 `gpt-4o-mini` 호출에 사용할 credit을 보유해야 합니다.
2. MySQL과 pgvector snapshot이 manifest의 500건 및 ID digest와 일치해야 합니다.
3. 아래 명령으로 artifact를 다시 생성한 뒤 validator가 저장 지표와 원본 결과를 재계산해 일치해야 합니다.
4. 필수 안전 기준과 N01·N02·A01을 모두 통과하고 Hit@5·지연·token 비교를 검토한 뒤에만 기본 경로 전환 여부를 다시 판정합니다.
