# 작업 가이드 인덱스

`docs/skills`의 문서는 특정 코딩 도구에 자동 등록되는 skill이 아니라, 사람과 도구가 함께 참고하는 일반 Markdown 가이드입니다. 공통 범위·변경·검증 원칙은 [DEVELOPMENT](../DEVELOPMENT.md)가 기준이며, 현재 구현 사실은 각 context 문서와 소스에서 확인합니다.

작업에 직접 필요한 가이드만 읽습니다. 여러 가이드가 같은 내용을 반복하면 공통 규칙과 담당 context를 우선하고, 이 문서는 해당 작업에 특화된 점검 항목만 보충합니다.

## Backend

| 가이드 | 사용할 때 | 함께 볼 문서 |
|---|---|---|
| [API](backend/api.md) | Controller·DTO·Service·HTTP 계약 변경 | [Backend Context](../BACKEND_CONTEXT.md), [Backend Rules](../BACKEND_RULES.md) |
| [인증](backend/auth.md) | JWT·쿠키·인가·Redis token 변경 | [AUTH](../AUTH.md) |
| [테스트](backend/testing.md) | 단위·Repository·통합·migration 테스트 | [개발 규칙](../DEVELOPMENT.md) |
| [검증](backend/validation.md) | 요청·도메인·LLM 출력 경계 변경 | [Backend Rules](../BACKEND_RULES.md) |

## Frontend

| 가이드 | 사용할 때 | 함께 볼 문서 |
|---|---|---|
| [API client](frontend/api-client.md) | Axios·에러·재시도 변경 | [Frontend Rules](../FRONTEND_RULES.md) |
| [인증](frontend/auth.md) | token 저장·refresh·route guard 변경 | [AUTH](../AUTH.md) |
| [테스트](frontend/testing.md) | Vitest·Testing Library·SSE 테스트 | [Frontend Context](../FRONTEND_CONTEXT.md) |
| [UI](frontend/ui.md) | component·상태·접근성·스타일 변경 | [Frontend Rules](../FRONTEND_RULES.md) |

## Shared

| 가이드 | 사용할 때 |
|---|---|
| [버그 수정](shared/bug-fix.md) | 기존 동작의 결함이나 회귀 수정 |
| [코드 리뷰](shared/code-review.md) | 변경 diff의 정확성·범위·검증 검토 |
| [보안 리뷰](shared/security-review.md) | 인증·인가·secret·외부 입력 경계 검토 |

추천 작업은 위 가이드보다 먼저 [현재 추천 파이프라인](../RECOMMENDATION_PIPELINE.md)과 [승인된 목표 설계](../AGENTIC_RAG_PLAN.md)를 구분합니다. 현재 존재하지 않는 agent loop나 SSE 종료 이벤트를 구현된 전제로 삼지 않습니다.
