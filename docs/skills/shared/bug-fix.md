# Bug Fix Skill

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
3. Add a failing test if possible.
4. Apply the minimal fix.
5. Verify the fix.
6. Run regression verification.

## Verification

- Bug reproduction no longer occurs.
- Existing behavior remains stable.
- Tests pass successfully.
- No unrelated code changed.

## Anti-Patterns

- Refactoring during bug fixes
- Broad architectural changes
- Fixing symptoms only
- Silent exception swallowing