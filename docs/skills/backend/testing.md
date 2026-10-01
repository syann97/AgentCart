# Backend 테스트 작업 가이드

이 파일은 도구 중립적인 저장소 참고 문서입니다. [공통 검증 기준](../../DEVELOPMENT.md)과 변경 영역의 실제 테스트 구성을 먼저 확인합니다.

## Layer Structure

```
unit/         — @ExtendWith(MockitoExtension.class), no Spring context
repository/   — @DataJpaTest slice, real JPA, Flyway disabled
integration/  — @SpringBootTest + @AutoConfigureMockMvc + Testcontainers
migration/    — @SpringBootTest(webEnvironment=NONE), JDBC schema assertions
```

Repository slice 일부는 H2를 사용합니다. MySQL FULLTEXT, pgvector, Redis, migration처럼 DB 고유 동작은 H2 결과로 대체하지 않고 필요한 Testcontainers를 사용합니다. 현재 통합 테스트의 image 선언은 각 테스트 class가 기준입니다.

## Naming Convention

`methodName_condition_expectedOutcome()`

Annotation and assertion examples: [testing examples](references/testing-examples.md). Read them only when the existing tests do not establish the pattern.

## Rules

- Unit test: business logic only, no Spring context
- Repository test: query behavior only, no service layer
- DB·Redis 계약을 포함한 integration test: 필요한 Testcontainers로 실제 경계를 검증
- Assert observable output only — not internal state or invocation counts unless necessary
- `@DisplayName` required on integration test methods
- Reuse private helper methods for repeated flows (`performLogin()`, `buildRequest()`)
- Constants for repeated test data values (`private static final String EMAIL = "..."`)
- Each test must be independent — no hidden dependency on execution order
- When a test breaks, it must fail for the correct reason (not a setup side-effect)

## Anti-Patterns

- `@SpringBootTest` for unit tests
- H2 in integration tests (use Testcontainers)
- Asserting mock internals when the return value already proves the behavior
- `@BeforeEach` that does more than entity/data setup

## 실행

Backend 루트인 `backend/AgentCart`에서 범위에 맞는 test를 선택합니다.

```powershell
.\gradlew.bat test --tests "com.agentcart.recommendation.unit.*"
```

전체 `test`는 Docker가 필요한 Testcontainers를 포함합니다. 외부 LLM은 일반 회귀 테스트에서 mock하고 실제 모델 평가는 [추천 평가 계획](../../RECOMMENDATION_SCENARIOS.md)과 분리합니다.
