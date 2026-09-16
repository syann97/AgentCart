# 추천 평가 snapshot과 고정형 RAG 기준선

이 디렉터리는 500개 상품 snapshot, 20개 평가 질의, 사람이 검토한 relevance label, C04 최근 주문 fixture와 고정형 RAG의 실제 모델 실행 결과를 함께 고정합니다. 목표 agent 구현의 결과와 비교할 기준이며, 이 기준선 자체가 목표 동작을 만족한다는 의미는 아닙니다.

## 파일

- `snapshot-manifest.json`: JSON 8개 파일의 항목 수와 SHA-256, 실행 당시 MySQL·pgvector 수, 임베딩 규격
- `queries.json`: S01-S10, C01-C04, R01-R03, N01-N02, A01 질의와 명시 제약
- `labels.json`: `name|brand` 안정 키로 작성한 relevant, acceptable alternative, irrelevant 판정
- `fixtures.json`: C04 평가 중 최근 7일 주문으로 삽입할 상품
- `baselines/fixed-rag-79627c0.json`: commit `79627c0`의 실제 `gpt-4o-mini`/`bge-m3` 실행 결과와 재계산 가능한 지표
- `runs/agentic-rag-*.json`: 실제 Agent 실행 결과, 검색·LLM 호출 수, token·지연과 재계산 지표
- `AGENTIC_RAG_EVALUATION_2026-09-16.md`: #181 실행 판정과 재실행 조건

상품 DB ID는 적재 순서에 따라 달라질 수 있어 label의 식별자로 쓰지 않습니다. `name|brand`가 중복되면 validator가 snapshot을 거부합니다. 기준선의 `productId`는 실행 당시 MySQL snapshot을 추적하기 위한 값입니다.

## 검증

저장소 루트에서 다음 명령을 실행합니다.

```bash
python scripts/evaluation/validate_recommendation_evaluation.py
python scripts/evaluation/validate_recommendation_evaluation.py --print-metrics
```

validator는 파일 hash와 8/500 합계, 안정 키 유일성, 질의·label·fixture 참조, 기준선 결과의 상품 사실과 ID 중복, 로컬 절대 경로·secret 형태 문자열을 검사합니다. 이어 Hit@5, 조건 위반, 범위 밖 오탐, 구체화 실패, 결과·LLM 호출 시도 수, 지연시간을 다시 계산하여 저장된 metrics와 정확히 비교합니다. 이 검증은 모델이나 외부 서비스를 호출하지 않으므로 CI와 로컬에서 결정적입니다.

## 실제 모델 실행과 mock 회귀 테스트

기준선은 2026-09-15에 별도 worktree의 commit `79627c080491b4d9e3d8400d4f81e8dcc40768b6`에서 실행했습니다. MySQL 상품 500건, `bge-m3` 1024차원 vector 500건, `gpt-4o-mini`를 사용했습니다. 각 질의 전에 `rec:query:*` 캐시를 비웠으며, LLM 호출 수는 애플리케이션 호출 시도 기준으로 enrichment 1회와 반환 상품별 reason 생성 1회를 합산했습니다. 고정 pipeline이 provider token usage를 노출하지 않아 token 수와 금액은 기록하지 못했고 artifact에 이 한계를 명시했습니다. C04 fixture는 실행 직전에 삽입하고 `finally`에서 제거했습니다.

실제 모델 결과는 비결정적이고 비용이 발생하므로 일반 테스트에서 재실행하지 않습니다. backend의 mock 기반 단위·통합 테스트는 실행 상한, 후보 제한, 예외 경로 같은 결정적 계약을 검증하고, 이 디렉터리의 validator는 고정된 실제 실행 artifact를 검증합니다.

엄격 Hit@5는 `relevant`만, 허용 Hit@5는 `relevant`와 `acceptableAlternatives`를 합쳐 계산합니다. S/C/R 17개 질의만 Hit@5 분모에 포함합니다. N 질의는 결과 없음, A01은 구체화 응답 여부를 별도 지표로 계산합니다.

## Agent 실제 실행

`AgenticRagEvaluationTest`는 일반 테스트에서 비활성화되며 `AGENTCART_EVALUATION_ENABLED=true`일 때만 local profile의 실제 MySQL, pgvector, Ollama와 `CHAT_PROVIDER`로 선택한 채팅 공급자를 호출합니다. 실행 전에 root, output, 평가 대상 commit 환경 변수를 명시해야 합니다. C04 최근 주문 fixture와 평가 회원은 실행 중 생성하고 `finally`에서 제거합니다.

2026-09-16 실행은 snapshot 검증과 A01 결정적 구체화에는 성공했지만 OpenAI 잔여 credit 부족으로 모델 의존 19개 질의가 완료되지 않았습니다. 이 artifact는 실패 실행의 재현 기록이며 Agent 품질 기준선으로 사용하지 않습니다. 자세한 판정은 [실행 기록](AGENTIC_RAG_EVALUATION_2026-09-16.md)을 참조합니다.
