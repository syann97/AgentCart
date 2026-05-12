# Frontend Auth Skill

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