# Backend 검증 작업 가이드

이 파일은 도구 중립적인 저장소 참고 문서입니다. [Backend 변경 규칙](../../BACKEND_RULES.md)과 [공통 개발 규칙](../../DEVELOPMENT.md)을 함께 적용합니다.

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

## LLM과 도구 출력

- JSON parsing 성공만으로 의미가 유효하다고 보지 않기
- 허용 enum·가격 범위·후보 ID·근거 ID를 server data로 검증
- 모델이 사용자 명시 조건이나 회원 ID를 덮어쓰지 못하게 하기
- 잘못된 출력의 fallback과 종료 상태를 계약으로 테스트

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
- 잘못된 LLM·tool 출력이 후보와 정책 경계를 넘지 못함
