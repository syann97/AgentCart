# 공통 개발 규칙

Claude Code, Codex, 직접 개발 모두에 적용하는 작업 가이드입니다. 도메인 사실의 위치는 [문서 인덱스](README.md)를 참조합니다.

## 범위와 변경

- 사용자 요청과 승인된 설계에 맞춰 필요한 변경을 완료합니다. 이미 승인된 범위에서 같은 확인을 반복하지 않습니다.
- 현재 코드, 목표 설계, 실제 측정 결과를 구분합니다. 가정은 드러내고 확인할 수 있는 사항은 소스·테스트로 확인합니다.
- 기존 서비스·Repository·DTO를 우선 활용합니다. 단일 용도를 위한 불필요한 계층·범용 프레임워크를 추가하지 않습니다.
- 관련 없는 리팩터링을 섞지 않고, 사용자의 기존 변경과 개인 파일을 보존합니다.
- 기능 변경은 문제·원인·관찰 가능한 결과·검증 기준으로 설명합니다.
- 문서 정비 범위에서는 잘못된 동작을 현재의 한계로 기록하고 후속 코드 작업과 구분합니다.

## 문서와 코딩 도구

- Codex 진입점은 [AGENTS.md](../AGENTS.md), Claude Code 진입점은 [CLAUDE.md](../CLAUDE.md)입니다. 두 파일이 같은 공통 규칙을 참조합니다.
- [docs/skills](skills/README.md)의 11개 가이드는 일반 Markdown 참조 문서입니다. 필요한 영역만 읽습니다.
- [공유 프롬프트](prompts/README.md)는 작업 입력 템플릿입니다. 런타임 LLM 프롬프트는 현재 Java 서비스 안에 있습니다.
- 문서의 경로는 해당 문서를 기준으로 상대 경로를 작성합니다. 이동한 문서는 참조를 갱신하거나 안내 경로를 남깁니다.
- 기능·정책 변경 시 현행 문서와 목표 설계의 구현 상태를 같은 변경에서 갱신합니다.

## 검증

| 변경 | 적절한 검증 |
|---|---|
| 문서만 변경 | Markdown 링크·경로, 코드/설정 대조, 현재/목표 구분, `git diff --check` |
| Backend 규칙 | 관련 Mockito 단위 테스트, 정책 경계·실패 사례 |
| SQL·스키마·HTTP | 필요한 DB의 Testcontainers 테스트와 해당 API 계약 |
| Frontend 타입·훅·UI | 관련 Vitest 테스트, 필요 시 lint·build |
| 추천 모델·프롬프트 | 기존 회귀 테스트와 [별도 평가 계획](RECOMMENDATION_SCENARIOS.md) |

기존 테스트의 유효한 회귀 검증은 유지합니다. 정책 변경과 충돌하는 기대값은 새 명세에 맞춰 갱신하며, 잘못된 동작을 보존하기 위해 테스트를 고정하지 않습니다. 사소한 문서 변경에 구현을 그대로 복제하는 테스트를 추가하지 않습니다.

Backend 명령은 `backend/AgentCart`에서 실행합니다.

```powershell
.\gradlew.bat test --tests "com.agentcart.recommendation.unit.*"
```

macOS/Linux에서는 같은 위치에서 `./gradlew test --tests "com.agentcart.recommendation.unit.*"`를 사용합니다. 전체 `test`에는 Docker가 필요한 테스트가 포함됩니다.

Frontend 명령은 `frontend`에서 실행합니다.

```sh
npm test
npm run lint
npm run build
```

검증 결과에는 실제 실행한 명령, 통과·실패·미실행 범위를 구분합니다. `UP-TO-DATE`나 캐시된 결과를 새 실측 결과로 설명하지 않습니다. 검증을 통과했고 새 변경이 없으면 같은 검사를 불필요하게 반복하지 않습니다.

## 환경과 데이터

로컬 설정·인증 정보는 [LOCAL_SETUP](LOCAL_SETUP.md)의 관리 기준을 따릅니다. API 키·토큰·개인 프롬프트를 공유 문서나 예시에 넣지 않습니다. 실제 LLM 호출과 데이터 적재·임베딩 재생성은 단순 문서 검사와 구분합니다.
