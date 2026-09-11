# 버그 수정 작업 가이드

이 파일은 도구 중립적인 저장소 참고 문서입니다. 범위·검증·사용자 변경 보존은 [공통 개발 규칙](../../DEVELOPMENT.md)을 따릅니다.

## Goal

Fix bugs with minimal and targeted changes.

## When To Use

- Fixing defects
- Handling regressions
- Stabilizing existing behavior

## Rules

- Reproduce the bug first.
- Change only what is necessary.
- Avoid unrelated refactoring.
- Preserve existing architecture and style.
- Prefer root-cause fixes over symptom masking.

## Workflow

1. Reproduce the bug.
2. Identify the root cause.
3. 회귀 가능성이 있거나 경계 동작이 바뀌면 실패를 재현하는 의미 있는 테스트를 추가합니다.
4. Apply the minimal fix.
5. Verify the fix.
6. Run regression verification.

## Verification

- Bug reproduction no longer occurs.
- Existing behavior remains stable.
- 실행한 관련 검증이 통과합니다.
- No unrelated code changed.

## Anti-Patterns

- Refactoring during bug fixes
- Broad architectural changes
- Fixing symptoms only
- Silent exception swallowing
