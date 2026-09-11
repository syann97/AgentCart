# Frontend API client 작업 가이드

이 파일은 도구 중립적인 저장소 참고 문서입니다. REST는 현재 [중앙 Axios client](../../../frontend/src/lib/axios.ts), SSE는 [useSse](../../../frontend/src/hooks/use-sse.ts)를 사용합니다. 두 경로의 인증·오류 처리를 같은 것으로 가정하지 않습니다.

## API Rules

- Use centralized API client
- Handle 401 responses consistently
- Normalize API error handling
- Keep Backend DTOs and Frontend types aligned

## Retry Rules

- Retry only idempotent requests
- Avoid infinite retry loops
- If a non-idempotent request is replayed after refresh, verify duplicate-execution behavior
- Mark or cap per-request refresh retries and queue concurrent refresh attempts

## Error Rules

- Surface user-friendly messages
- Avoid leaking internal server errors

현재 Axios는 동시 refresh queue가 있지만 request별 재시도 표시는 없고, refresh 실패 시 Zustand 상태와 redirect를 처리하지 않습니다. 변경 전 [AUTH](../../AUTH.md)의 현재 계약을 확인합니다.
