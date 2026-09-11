# Backend 인증 작업 가이드

이 파일은 도구 중립적인 저장소 참고 문서입니다. 실제 token 저장·cookie·SSE 인증 동작은 [AUTH](../../AUTH.md), 공통 범위와 검증 원칙은 [DEVELOPMENT](../../DEVELOPMENT.md)가 기준입니다.

## Authentication Rules

- Use JWT authentication
- Access token is short-lived
- Refresh token uses HttpOnly cookie
- Refresh token rotation is required

---

## Security Rules

- Never trust client-provided identity data
- Validate token expiration before business logic
- Validate token ownership
- Reject invalid or revoked refresh tokens

---

## Refresh Token Rules

- Rotate refresh token on refresh
- Invalidate previous refresh token
- 로그아웃에서 Redis token 상태와 browser cookie 정리 여부를 각각 결정
- Refresh failure must return 401

---

## Authorization Rules

Validate:
- authentication
- resource ownership
- role permissions

Protected APIs must require authentication explicitly.

---

## Cookie Rules

Refresh token cookie must:
- use HttpOnly
- use Secure in production
- use appropriate SameSite policy

Do not expose refresh tokens in response body.

현재 SSE는 Access token을 query parameter로 전달합니다. 변경할 때 URL·proxy log 노출, CORS, Frontend `EventSource`, Backend filter를 함께 검토합니다.

---

## Redis Rules

- Store refresh token state in Redis
- TTL must match token expiration
- Remove token state on logout

---

## Error Rules

Authentication failures must:
- return deterministic responses
- avoid leaking internal details

Use:
- 401 for unauthenticated requests
- 403 for forbidden access

---

## Verification

Verify:
- login issues access token correctly
- refresh rotates tokens correctly
- logout invalidates refresh token
- cookie 속성과 logout 후 browser/Redis 상태가 계약과 일치
- unauthorized requests return 401
- forbidden requests return 403

현재 구현의 한계를 새 규칙이 이미 충족된 것처럼 기록하지 않습니다. 예를 들어 현재 logout은 Redis key를 지우지만 cookie 만료 header는 보내지 않습니다.
