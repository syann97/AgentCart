# Security Review Skill

## Goal

Identify security weaknesses and unsafe implementation patterns.

## When To Use

- Authentication changes
- Authorization logic updates
- Cookie/session changes
- External API integrations

## Rules

- Validate authentication boundaries explicitly.
- Enforce authorization consistently.
- Avoid leaking sensitive information.
- Use secure defaults whenever possible.

## Workflow

1. Identify security-sensitive flows.
2. Verify authentication logic.
3. Verify authorization logic.
4. Verify token/cookie handling.
5. Verify logging safety.
6. Verify failure behavior.

## Verification

- Unauthorized access is blocked.
- Sensitive data is protected.
- Tokens and cookies are handled safely.
- Security failures are explicit.

## Anti-Patterns

- Missing authorization checks
- Logging secrets
- Trusting client-provided state
- Silent authentication fallback
- Overly broad permissions