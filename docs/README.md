# 문서 인덱스

문서는 **현재 구현**, **승인된 목표**, **개발 지침**을 구분합니다. 목표 설계의 수치를 현재 운영 값으로 소개하거나, 문서 변경만으로 기능이 구현되었다고 기록하지 않습니다.

## 작업별 읽기 경로

공통 시작점은 [DEVELOPMENT](DEVELOPMENT.md)의 `범위와 변경`, `환경과 데이터`입니다. 검증·문서·PR 절은 수행할 작업에 맞춰 추가합니다. 프로젝트 범위가 낯설 때만 [개요](PROJECT_CONTEXT.md)를 먼저 확인합니다. 아래 문서도 관련 절을 선택하며, 영향 범위가 불명확하면 전체를 읽습니다.

| 작업 | 추가로 읽을 문서 / 절 |
|---|---|
| 문서·Git 작업 | DEVELOPMENT의 문서·검증·PR 절; 수정 대상 문서와 그 사실의 출처 |
| Frontend UI | FRONTEND_CONTEXT, FRONTEND_RULES의 사용자 상태 절, [UI](skills/frontend/ui.md), [테스트](skills/frontend/testing.md) |
| Frontend API·SSE | FRONTEND_CONTEXT, FRONTEND_RULES의 해당 계약, [API client](skills/frontend/api-client.md), 테스트 가이드 |
| Backend API | BACKEND_CONTEXT, BACKEND_RULES의 관련 계층·입력 규칙, [API](skills/backend/api.md), [검증](skills/backend/validation.md), [테스트](skills/backend/testing.md) |
| 인증·보안 | 해당 영역 context·rules, AUTH의 변경할 계약, [가이드 인덱스](skills/README.md)의 해당 인증·보안 가이드 |
| 추천 검색·agent | RECOMMENDATION_PIPELINE의 질의·검색·검증·실행 상한; AGENTIC_RAG_PLAN의 도구·재검색·실행 상한·작업 상태; BACKEND_RULES의 추천 규칙 |
| 추천 SSE | RECOMMENDATION_PIPELINE의 `현재 SSE 계약`, AGENTIC_RAG_PLAN의 `SSE 계약 — 구현됨`과 작업 상태; 해당 Frontend/Backend 규칙 |
| 추천 평가 | RECOMMENDATION_SCENARIOS와 해당 평가 artifact; 파이프라인·목표 설계에서 평가할 동작과 완료 기준 |
| 환경·데이터 적재 | LOCAL_SETUP, INFRA_CONTEXT의 해당 서비스·설정; 데이터 변경은 담당 Backend 계약 |

추천 작업은 **영향받는 현재/목표 계약을 반드시 함께 확인**합니다. 관련 API·인증·캐시로 영향이 이어지면 읽기 범위를 넓힙니다. 가이드는 필요한 것만 선택하고 예제는 작성 방식이 불명확할 때 엽니다. 읽은 지침은 파일·영역 변경이나 기억이 불확실한 경우에 다시 확인합니다.

코딩 에이전트의 문서 효율과 행동 품질 평가는 [AGENT_GUIDANCE_EVALUATION](AGENT_GUIDANCE_EVALUATION.md)에 정의합니다. 이 평가 문서는 일반 작업의 필수 읽기 대상이 아닙니다.

## 문서별 책임

| 문서 | 역할 / 사실 기준 |
|---|---|
| [프로젝트 README](../README.md) | 기능 현황과 문서 진입점 |
| [PROJECT_CONTEXT](PROJECT_CONTEXT.md) | 개인 프로젝트의 목적과 범위 |
| [RECOMMENDATION_PIPELINE](RECOMMENDATION_PIPELINE.md) | 현재 코드의 검색·검증·SSE·실행 값과 알려진 한계 |
| [AGENTIC_RAG_PLAN](AGENTIC_RAG_PLAN.md) | 승인된 다음 구현의 도구·제약·상한·응답·평가 계약 |
| [RECOMMENDATION_SCENARIOS](RECOMMENDATION_SCENARIOS.md) | 현재 JSON 데이터 범위와 후속 평가셋 계획 |
| [BACKEND_CONTEXT](BACKEND_CONTEXT.md) / [BACKEND_RULES](BACKEND_RULES.md) | Backend 구조와 변경 규칙 |
| [FRONTEND_CONTEXT](FRONTEND_CONTEXT.md) / [FRONTEND_RULES](FRONTEND_RULES.md) | Frontend 구조와 변경 규칙 |
| [INFRA_CONTEXT](INFRA_CONTEXT.md) | Compose 서비스, 용도별 TTL, 인프라 설정 출처 |
| [AUTH](AUTH.md) | 실제 인증·쿠키·SSE 인증 동작 |
| [LOCAL_SETUP](LOCAL_SETUP.md) | 실행 전제, 설정 키, 데이터 적재·임베딩 경로 |
| [DEVELOPMENT](DEVELOPMENT.md) | 도구 중립적인 작업·검증·문서 동기화 규칙 |
| [skills](skills/README.md) / [prompts](prompts/README.md) | 필요할 때 읽는 작업 가이드와 공유 템플릿 |

[AGENT_CONTEXT.md](AGENT_CONTEXT.md)는 이전 경로 호환용 안내입니다. 새 설계나 정책을 그 파일에 추가하지 않습니다.

## 정합성 유지

- 실제 동작은 소스·설정·적절한 테스트로 확인합니다. 목표 동작은 승인된 설계와 변경 범위를 기준으로 구현합니다.
- 실행 값은 위 담당 문서에 출처와 함께 기록하고, 다른 문서는 링크로 참조합니다.
- 의존성의 간접 버전은 별도 목록으로 복제하지 않습니다.
- 기능을 구현한 변경에서 현행 문서와 목표 문서의 구현 상태를 함께 갱신합니다.
- 로컬 Markdown 링크는 작성한 파일을 기준으로 상대 경로를 사용합니다.
- 개인 설정·이전 세션 메모는 공유 가이드의 사실 기준으로 사용하지 않습니다.
