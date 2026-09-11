# 보안 리뷰 작업 가이드

이 파일은 도구 중립적인 저장소 참고 문서입니다. 인증 변경은 [AUTH](../../AUTH.md), 공통 범위와 증거 기준은 [DEVELOPMENT](../../DEVELOPMENT.md)를 함께 확인합니다.

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
- Trace secrets and tokens through storage, headers, cookies, URLs, logs, and error messages.

## Workflow

1. Identify security-sensitive flows.
2. Verify authentication logic.
3. Verify authorization logic.
4. Verify token/cookie handling.
5. Verify logging safety.
6. Verify failure behavior.
7. Check SSR/client boundaries, CORS, query parameters, and external callbacks when relevant.

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
- Secrets or access tokens in query/access logs
