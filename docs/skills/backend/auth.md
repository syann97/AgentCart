# Backend Auth Skill

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
- Remove refresh token on logout
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
- unauthorized requests return 401
- forbidden requests return 403