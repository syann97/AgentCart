# Integration Test Skill

## Goal

Create reliable integration tests that validate real application behavior.

## When To Use

- API testing
- Transaction testing
- Security flow testing
- Bug reproduction testing

## Rules

- Test real application flows whenever possible.
- Prefer integration tests over excessive mocking.
- Mock only external dependencies.
- Keep tests deterministic and isolated.
- Use meaningful test names.

## Workflow

1. Identify the behavior to verify.
2. Reproduce the expected success/failure flow.
3. Prepare minimal test data.
4. Execute the real application flow.
5. Assert observable behavior only.
6. Verify rollback and exception handling if relevant.

## Verification

- Test fails for the correct reason when broken.
- Test passes consistently.
- Test validates real behavior.
- No hidden dependency on execution order exists.

## Anti-Patterns

- Testing implementation details
- Excessive mocking
- Shared mutable test state
- Fragile assertions
- Giant test setup blocks