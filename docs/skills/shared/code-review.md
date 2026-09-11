# 코드 리뷰 작업 가이드

이 파일은 도구 중립적인 저장소 참고 문서입니다. 요구사항과 [공통 개발 규칙](../../DEVELOPMENT.md), 변경 영역의 context·rules를 기준으로 실제 diff를 검토합니다.

## Goal

Review code for correctness, simplicity, and maintainability.

## When To Use

- Reviewing pull requests
- Reviewing AI-generated code
- Reviewing bug fixes
- Reviewing refactors

## Rules

- Focus on correctness first.
- Prefer simplicity over cleverness.
- Verify changes match requirements.
- Review only relevant changes.

## Workflow

1. Understand the requested change.
2. Verify implementation correctness.
3. Check architectural consistency.
4. Check test coverage.
5. Check unnecessary complexity.
6. Verify regression risks.
7. Verify documentation describes current and planned behavior separately.

## Verification

- Changes match requirements.
- Complexity is justified.
- Tests cover important behavior.
- No unnecessary changes exist.
- Report unrun tests and unresolved assumptions explicitly.

## Anti-Patterns

- Premature abstraction
- Overengineering
- Large unrelated diffs
- Missing validation
- Missing tests
