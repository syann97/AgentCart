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
- `executions/assessments/<run-id>.json`: 보존하는 v2.1 오프라인 채점 결과
- `executions/assessments/v2.2/<run-id>.json`: 같은 raw run에서 상세·집계 불일치를 보정한 v2.2 채점 결과
- `v2.2/manifest.json`: 보존한 역사 파일의 hash, 기준 commit, 이전 scorer hash와 보정 결과의 source/output/supersedes 연결

상품 DB ID는 적재 순서에 따라 달라질 수 있어 label의 식별자로 쓰지 않습니다. `name|brand`가 중복되면 validator가 snapshot을 거부합니다. 기준선의 `productId`는 실행 당시 MySQL snapshot을 추적하기 위한 값입니다.

## 검증

저장소 루트에서 다음 명령을 실행합니다.

```bash
python scripts/evaluation/validate_recommendation_evaluation.py
python scripts/evaluation/validate_recommendation_evaluation.py --print-metrics
python -m unittest discover -s scripts/evaluation -p "test_*.py"
```

validator는 파일 hash와 8/500 합계, 안정 키 유일성, 질의·label·fixture 참조, 기준선 결과의 상품 사실과 ID 중복, 로컬 절대 경로·secret 형태 문자열을 검사합니다. v1은 원래 공식, v2는 기존 scorer로 재계산하고, v2.1은 기록한 역사 hash로 보존을 검증합니다. v2.2는 상품→질의→전체 불변식과 최신 scorer의 결정적 재계산을 모두 확인합니다. 새 scorer로 과거 v2.1을 다시 계산해 같은 결과라고 주장하지 않습니다. 일반 텍스트 hash는 기존 `content_sha256`의 LF 정규화 규칙을 사용하며 `.gitattributes`의 `-text` 실행 기록은 grounding comparison의 바이트 hash 검사도 유지합니다. 이 검증은 외부 서비스를 호출하지 않습니다. `--write-reassessment`는 기존 v2 전용이며 v2.1·v2.2 파일을 덮어쓰지 않습니다.

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
  --output evaluation/recommendation/executions/assessments/v2.2/<run-id>.json
```

scorer는 raw run, snapshot, 질의·상품 사실, 중복 결과와 실행 상한을 검증한 후 `metricVersion=2.2-observed-policy`로 채점합니다. 요청 직후 DB에서 관측한 status·stock만 해당 정책 근거로 사용하며 삭제·조회 누락은 unknown으로 집계합니다. snapshot의 재고·상태를 대체 근거로 쓰지 않습니다. label에 없는 상품-질의 조합도 `unjudged`로 남습니다. `FAILED` 질의가 하나라도 있는 실행은 품질 비교에서 제외하고 metrics를 만들지 않습니다. 기존 raw run이나 assessment 경로가 이미 있으면 덮어쓰지 않습니다.

## v2.2 관측 정책 보정과 재현

#209 이전 v2.1은 status·stock 위반과 전체 집계를 관측값으로 바꾸면서 상품의 `activeStatusUnknown`과 질의의 `observedPolicyCompliantAcceptedHitAt5`를 v2 계산값으로 남겼습니다. 전부 ACTIVE·양수 재고인 변경 전/후 실행에서 상품 상세 unknown은 80/72인데 전체 unknown은 0이었습니다. S01의 상위 5개를 모두 INACTIVE·재고 0으로 바꾼 메모리 fixture에서는 질의 Hit@5가 true인데 전체는 miss로 집계됐습니다.

v2.2는 각 상품의 `activeStatusUnknown`, `stockUnknown`, `policyEvidenceUnknown`과 위반을 관측값으로 확정하고, 질의 unknown 수·위반·Hit@5를 계산한 뒤 그 질의값으로 전체 집계를 만듭니다. 상태·재고의 부분 누락, observation 객체 누락 또는 null도 unknown입니다. `fullyObservedProducts`는 정책 **근거의 완전성**이며 정책 준수 상품 수가 아닙니다.

| 질의별 관측 정책 Hit@5 | 의미 |
|---|---|
| `true` | 상위 5개에 relevant/acceptable이고 알려진 위반이 없으며 상태·재고 근거가 완전한 상품 존재 |
| `null` | 입증된 hit는 없지만 알려진 위반이 없는 후보의 relevance 또는 상태·재고가 미판정이어서 hit 가능성이 남음 |
| `false` | hit 가능 후보 없음; 빈 결과·전부 알려진 정책 위반·전부 irrelevant 포함 |

알려진 위반이 있는 상품은 다른 근거가 누락돼도 hit 가능 후보가 아닙니다. irrelevant 상품의 정책 unknown도 Hit@5를 null로 바꾸지 않습니다. 입증된 hit가 있으면 다른 unknown보다 우선합니다. 상품의 unknown 플래그는 이 Hit@5 판정과 별도로 유지합니다. S/C/R 17개만 전체 `hits + misses + unjudged = eligibleQueries` 분모에 포함하며, null도 분모에서 제외하지 않습니다. N/A 질의의 null은 비대상 표시이고 unjudged 수에 넣지 않습니다.

두 고정 raw run의 보정 결과는 새 경로에 저장했습니다. relevance label·raw run·v1/v2/v2.1 assessment·grounding comparison과 보고서는 변경하지 않았습니다.

| raw run | 반환 상품 | 상품 상세 status unknown 전→후 | 전체 status unknown | 관측 정책 Hit@5 |
|---|---:|---:|---:|---|
| `agentic-rag-fe2766f-20260918T004833Z-r1` | 80 | 80→0 | 0→0 | 17/17 유지 |
| `agentic-rag-770d062-20260918T005338Z-r1` | 72 | 72→0 | 0→0 | 17/17 유지 |

새 평가는 scorer와 기반 v2 scorer의 hash도 기록합니다. 현재 scorer는 v2.2만 생성하며, 과거 scorer는 manifest의 기준 commit과 `previousScorerSha256`로 추적합니다. 역사 hash 목록은 LF 정규화 내용 hash이며 보호된 실행 기록은 기존 byte hash 검증도 통과해야 합니다.

재현은 루트에서 아래 명령으로 수행합니다. validator가 두 보정 결과를 메모리에서 재계산해 저장 결과와 비교하며 파일을 쓰지 않습니다.

```bash
python -B scripts/evaluation/validate_recommendation_evaluation.py
python -B -m unittest discover -s scripts/evaluation -p "test_*.py"
```

CLI로 별도 결과를 만들려면 위 채점 명령의 output을 **존재하지 않는 새 경로**로 지정합니다. 같은 raw 입력의 반복 채점은 동일한 결과를 만들며 기존 경로는 거부합니다. v2.2 결과를 저장소에 추가할 때는 manifest의 assessments에 source/output을 등록하고 원시 source hash를 preservedHistory에 추가합니다. v2.1 보정이면 supersedes도 지정하며 기존 역사 항목과 두 보정 연결을 유지합니다. validator는 v2.2 폴더의 미등록·누락 결과를 거부합니다.

이 작업은 오프라인 계산 수정이며 실제 LLM 재실행이나 추천 품질 개선 실험이 아닙니다. 알려진 평가 한계와 assistant label provenance는 유지합니다.

2026-09-16 OpenAI 실행은 잔여 credit 부족으로 모델 의존 19개 질의를 완료하지 못했으므로 v2 품질 비교에서도 제외합니다. #191의 `claude-haiku-4-5` 완료 실행은 v2에서 엄격 Hit@5 0.941176, 허용 Hit@5 1.0, 관찰된 정책 준수 허용 Hit@5 1.0과 명시 정책 위반 0건을 기록했습니다. 기존 보고서의 카테고리 위반 2건은 S07·S10의 soft 기대 카테고리 불일치이며 사용자 명시 조건 위반이 아닙니다. Agent 결과 80개 중 2개는 `unjudged`이고 상품 status는 원시 snapshot에 없어 ACTIVE 준수 여부 80건이 unknown입니다.

이 수치는 서로 다른 chat 모델의 단회 실행을 비교한 결과이므로 pipeline 변경만의 인과 효과로 해석하지 않습니다. v2 검토도 카탈로그 전체의 완전한 정답 집합이나 독립적인 사람 검증이 아닙니다. 당시 판단을 담은 [v1 실행 기록](AGENTIC_RAG_EVALUATION_2026-09-16.md)은 이력을 위해 그대로 보존합니다.
