# Backend API Skill

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

- Add integration tests for APIs
- Reproduce bugs before fixing
- Mock only external systems

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

## Anti-Patterns

- Fat controllers
- Interface/Impl split without need
- Business logic in DTOs
- Generic abstraction for single use