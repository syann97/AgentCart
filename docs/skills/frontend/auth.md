# Frontend 인증 작업 가이드

이 파일은 도구 중립적인 저장소 참고 문서입니다. 실제 저장 위치·refresh·route guard·SSE 인증은 [AUTH](../../AUTH.md)가 기준입니다.

## Token Rules

- Access token is short-lived
- Refresh token uses HttpOnly cookie

## Refresh Rules

- Retry original request after refresh
- Prevent duplicate refresh requests
- Clear auth state on refresh failure

## Route Rules

- Redirect unauthenticated users to /login
- Preserve original destination when redirecting

## Security Rules

- Do not store refresh token in localStorage
- Do not expose tokens in logs

위 항목은 변경 시 지킬 목표 규칙이며 모두 현재 구현됐다는 목록이 아닙니다. 현재 store는 page load에서 복원되지 않고 refresh 실패 시 token만 지우며, guard는 원래 목적지를 보존하지 않습니다.

SSE는 Axios interceptor를 통하지 않습니다. token 전달 방식을 바꾸면 `useSse`, Backend filter, CORS와 연결 종료 테스트를 같은 변경에서 맞춥니다.
