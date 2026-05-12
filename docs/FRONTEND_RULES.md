# Frontend Rules

## SSE Rules

- Stream recommendation results progressively
- Do not wait for full response
- Render partial results immediately
- Keep UI responsive during streaming

---

## Authentication Rules

- Store refresh token in HttpOnly cookie only
- Retry original request after successful token refresh
- Redirect unauthenticated users to /login
- Clear authentication state on refresh failure

---

## UX Rules

- Show recommendation reason clearly
- Emphasize matched conditions
- Display loading state during streaming
- Show meaningful error messages
- Avoid blocking full-page loading states

---

## API Rules

- Use centralized API client
- Handle 401 responses consistently
- Avoid duplicate refresh requests