# Validation Skill

## Rules

- Validate at the boundary first
- Reject invalid input early
- Keep validation deterministic
- Fail fast on invalid state

---

## Request Validation

Use Bean Validation for:
- required fields
- format validation
- size limits

Do not place complex validation in controllers.

---

## Service Validation

Service layer validates:
- business rules
- ownership
- workflow state

Do not mix validation with persistence logic.

---

## Authentication Validation

Validate:
- token expiration
- token ownership
- authorization

Never trust client-provided identity data.

---

## Error Rules

Validation failures must:
- return deterministic responses
- avoid leaking internal details

Use explicit exceptions.

---

## Verification

Verify:
- invalid input is rejected
- business invariants remain consistent
- authorization rules are enforced