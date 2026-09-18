# 추천 평가 snapshot과 버전별 재평가

이 디렉터리는 500개 상품 snapshot, 20개 평가 질의, relevance label, C04 최근 주문 fixture와 실제 모델 실행 결과를 함께 고정합니다. 원본 v1 입력·실행·보고서는 변경하지 않으며, 정책 해석을 고친 평가는 `v2/`에 별도 기록합니다. v2 label은 assistant 검토 결과이고 사람 검증 ground truth가 아닙니다.

## 파일

- `snapshot-manifest.json`: JSON 8개 파일의 항목 수와 SHA-256, 실행 당시 MySQL·pgvector 수, 임베딩 규격
- `queries.json`: S01-S10, C01-C04, R01-R03, N01-N02, A01 질의와 명시 제약
- `labels.json`: `name|brand` 안정 키로 작성한 relevant, acceptable alternative, irrelevant 판정
- `fixtures.json`: C04 평가 중 최근 7일 주문으로 삽입할 상품
- `baselines/fixed-rag-79627c0.json`: commit `79627c0`의 실제 `gpt-4o-mini`/`bge-m3` 실행 결과와 재계산 가능한 지표
- `runs/agentic-rag-*.json`: 실제 Agent 실행 결과, 검색·LLM 호출 수, token·지연과 재계산 지표
- `AGENTIC_RAG_EVALUATION_2026-09-16.md`: OpenAI 차단 실행과 #191 Claude 완료 실행의 비교·판정
- `v2/definition.json`: 명시 정책, query intent, soft 기대 카테고리와 비교 대상 실행을 분리한 평가 정의
- `v2/labels.json`: 두 완료 실행의 반환 상품 합집합을 검토한 relevance label과 provenance
- `v2/reassessment.json`: 원본 실행을 수정하지 않고 v2 scorer로 산출한 결정적 재평가 결과
- `grounding-v1.json`: S10·R01·R03의 선택 및 이유 문제와 S07·S10 대조 사례를 상품명·설명 근거로 판정한 assistant rubric
- `grounding-comparison.json`: 변경 전후 전체 실행과 focused 반복의 source hash·핵심 지표·사례별 재발 횟수
- `GROUNDING_EVALUATION_2026-09-18.md`: 상품 선택과 추천 이유 근거 개선의 전후 실제 모델 평가 보고서
- `executions/raw/<run-id>.json`: 신규 실제 모델 실행의 수정하지 않는 schema v2 원시 결과
- `executions/assessments/<run-id>.json`: 원시 결과와 분리해 생성한 v2.1 오프라인 채점 결과

상품 DB ID는 적재 순서에 따라 달라질 수 있어 label의 식별자로 쓰지 않습니다. `name|brand`가 중복되면 validator가 snapshot을 거부합니다. 기준선의 `productId`는 실행 당시 MySQL snapshot을 추적하기 위한 값입니다.

## 검증

저장소 루트에서 다음 명령을 실행합니다.

```bash
python scripts/evaluation/validate_recommendation_evaluation.py
python scripts/evaluation/validate_recommendation_evaluation.py --print-metrics
python -m unittest discover -s scripts/evaluation -p "test_*.py"
```

validator는 파일 hash와 8/500 합계, 안정 키 유일성, 질의·label·fixture 참조, 기준선 결과의 상품 사실과 ID 중복, 로컬 절대 경로·secret 형태 문자열을 검사합니다. 기존 v1 metrics를 원래 공식으로 확인한 뒤 원본 파일 hash, v2 판정 pool의 완전성, provenance와 v2 재평가 결과도 검증합니다. 이 검증은 모델이나 외부 서비스를 호출하지 않으므로 CI와 로컬에서 결정적입니다. 재평가 산출물을 의도적으로 갱신할 때만 `--write-reassessment`를 사용하며, 과거 실행 artifact를 쓰던 옵션은 거부됩니다.

## 실제 모델 실행과 mock 회귀 테스트

기준선은 2026-09-15에 별도 worktree의 commit `79627c080491b4d9e3d8400d4f81e8dcc40768b6`에서 실행했습니다. MySQL 상품 500건, `bge-m3` 1024차원 vector 500건, `gpt-4o-mini`를 사용했습니다. 각 질의 전에 `rec:query:*` 캐시를 비웠으며, LLM 호출 수는 애플리케이션 호출 시도 기준으로 enrichment 1회와 반환 상품별 reason 생성 1회를 합산했습니다. 고정 pipeline이 provider token usage를 노출하지 않아 token 수와 금액은 기록하지 못했고 artifact에 이 한계를 명시했습니다. C04 fixture는 실행 직전에 삽입하고 `finally`에서 제거했습니다.

실제 모델 결과는 비결정적이고 비용이 발생하므로 일반 테스트에서 재실행하지 않습니다. backend의 mock 기반 단위·통합 테스트는 실행 상한, 후보 제한, 예외 경로 같은 결정적 계약을 검증하고, 이 디렉터리의 validator는 고정된 실제 실행 artifact를 검증합니다.

v2 엄격 Hit@5는 `relevant`만, 허용 Hit@5는 `relevant`와 `acceptable`을 합쳐 계산합니다. S/C/R 17개 질의만 Hit@5 분모에 포함합니다. 명시 가격·카테고리·재고·최근 주문·상태 정책은 relevance와 별도로 계산합니다. 판정이 없거나 상품 설명만으로 핵심 속성을 확정할 수 없는 결과는 `unjudged`로 남기고 Hit@5와 관련 상품 비율에 하한·상한을 함께 기록합니다. N 질의는 결과 없음, A01은 구체화 응답 여부를 별도 지표로 계산합니다.

`grounding-v1.json`은 relevance와 추천 이유의 사실 근거를 분리합니다. 이유는 상품명·설명에 핵심 주장이 직접 있으면 `supported`, 없는 성능·호환성·용도를 단정하면 `unsupported`, 근거만으로 확정할 수 없으면 `unjudged`입니다. 기존 Claude 실행의 실제 이유 문구와 v2 relevance label을 참조하므로 validator는 과거 기록을 바꾸지 않고 기준의 출처가 유지되는지 검사합니다. 이는 assistant 검토 기준이며 사람 검증 결과가 아닙니다.

## Agent 실제 실행

`AgenticRagEvaluationTest`는 일반 테스트에서 비활성화되며 `AGENTCART_EVALUATION_ENABLED=true`일 때만 local profile의 실제 MySQL, pgvector, Ollama와 `CHAT_PROVIDER`로 선택한 채팅 공급자를 호출합니다. 실행 전에 root, output, 평가 대상 commit과 run ID 환경 변수를 명시해야 합니다. run ID 형식은 `agentic-rag-<commit>-<UTC YYYYMMDDTHHMMSSZ>-r<반복번호>`이며, 같은 commit의 반복 실행도 별도 파일로 남깁니다. 출력 파일이 이미 있으면 실행 전에 실패합니다. C04 최근 주문 fixture와 평가 회원은 실행 중 생성하고 `finally`에서 제거합니다.

일부 질의만 반복할 때는 쉼표로 구분한 `AGENTCART_EVALUATION_QUERY_IDS`를 지정합니다. 이 결과는 `real-model-agentic-rag-focused-run`으로 기록하며 전체 20개 품질 assessment에 사용하지 않습니다. #198은 `S10,R01,R03` focused 실행을 전후 두 번씩 추가해 전체 실행과 합쳐 사례별 세 번을 비교했습니다.

PowerShell에서 다음과 같이 원시 실행을 수집합니다. `<run-id>`와 commit은 실제 실행 대상으로 바꾸고, output은 저장소 내부의 새 경로를 사용합니다.

```powershell
$env:AGENTCART_EVALUATION_ENABLED = "true"
$env:AGENTCART_EVALUATION_ROOT = (Resolve-Path .).Path
$env:AGENTCART_EVALUATION_AGENT_COMMIT = "<40자리 commit>"
$env:AGENTCART_EVALUATION_RUN_ID = "<run-id>"
$env:AGENTCART_EVALUATION_OUTPUT = Join-Path (Resolve-Path .).Path "evaluation/recommendation/executions/raw/<run-id>.json"
Push-Location backend/AgentCart
.\gradlew.bat test --tests "com.agentcart.recommendation.evaluation.AgenticRagEvaluationTest"
Pop-Location
```

수집된 raw run은 수정하지 않고 별도 assessment로 채점합니다.

```powershell
python -B scripts/evaluation/score_recommendation_run.py `
  --run evaluation/recommendation/executions/raw/<run-id>.json `
  --definition evaluation/recommendation/v2/definition.json `
  --labels evaluation/recommendation/v2/labels.json `
  --manifest evaluation/recommendation/snapshot-manifest.json `
  --fixtures evaluation/recommendation/fixtures.json `
  --output evaluation/recommendation/executions/assessments/<run-id>.json
```

scorer는 raw run, snapshot, 질의·상품 사실, 중복 결과와 실행 상한을 검증한 후 `metricVersion=2.1-observed-policy`로 채점합니다. 요청 직후 DB에서 관측한 status·stock만 정책 근거로 사용하며 삭제·조회 누락은 unknown으로 집계합니다. label에 없는 상품-질의 조합도 `unjudged`로 남습니다. `FAILED` 질의가 하나라도 있는 실행은 품질 비교에서 제외하고 metrics를 만들지 않습니다. 기존 raw run이나 assessment 경로가 이미 있으면 덮어쓰지 않습니다.

2026-09-16 OpenAI 실행은 잔여 credit 부족으로 모델 의존 19개 질의를 완료하지 못했으므로 v2 품질 비교에서도 제외합니다. #191의 `claude-haiku-4-5` 완료 실행은 v2에서 엄격 Hit@5 0.941176, 허용 Hit@5 1.0, 관찰된 정책 준수 허용 Hit@5 1.0과 명시 정책 위반 0건을 기록했습니다. 기존 보고서의 카테고리 위반 2건은 S07·S10의 soft 기대 카테고리 불일치이며 사용자 명시 조건 위반이 아닙니다. Agent 결과 80개 중 2개는 `unjudged`이고 상품 status는 원시 snapshot에 없어 ACTIVE 준수 여부 80건이 unknown입니다.

이 수치는 서로 다른 chat 모델의 단회 실행을 비교한 결과이므로 pipeline 변경만의 인과 효과로 해석하지 않습니다. v2 검토도 카탈로그 전체의 완전한 정답 집합이나 독립적인 사람 검증이 아닙니다. 당시 판단을 담은 [v1 실행 기록](AGENTIC_RAG_EVALUATION_2026-09-16.md)은 이력을 위해 그대로 보존합니다.
