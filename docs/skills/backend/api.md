# Backend API 작업 가이드

이 파일은 도구 중립적인 저장소 참고 문서입니다. [공통 개발 규칙](../../DEVELOPMENT.md), [Backend Context](../../BACKEND_CONTEXT.md), [Backend 변경 규칙](../../BACKEND_RULES.md)을 먼저 적용합니다.

## Goal

Implement backend APIs with minimal and consistent changes.

## Controller Rules

- Controllers only handle HTTP concerns
- No business logic in controller
- Use request/response DTOs
- Return unified response structure

## Service Rules

- Business logic belongs in service
- Explicit transaction boundaries
- Avoid unnecessary abstraction
- Keep methods cohesive

## Entity Rules

- Entities manage domain state
- Avoid infrastructure dependencies
- Use explicit state transition methods

## Validation Rules

- Validate request DTOs
- Use Bean Validation first
- Validate business rules in service layer

## Test Rules

- 계약·SQL·보안 경계가 바뀌면 필요한 통합 테스트를 추가
- 버그 수정이면 가능한 범위에서 먼저 재현
- Service 단위 테스트는 협력 객체를 mock하고, DB·HTTP 계약은 적절한 slice 또는 Testcontainers로 검증

## Workflow

1. Analyze existing endpoint structure
2. Add DTOs
3. Implement service logic
4. Add validation
5. Add repository logic if needed
6. Write tests
7. Verify transaction behavior

## Verification

- API works as expected
- Validation catches invalid input
- Tests pass
- No unnecessary code added
- API 문서·Frontend type·client가 실제 응답과 일치

## Anti-Patterns

- Fat controllers
- Interface/Impl split without need
- Business logic in DTOs
- Generic abstraction for single use
