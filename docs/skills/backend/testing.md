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

```java
register_duplicateEmail_throwsException()
refresh_validToken_returnsNewPair()
products_table_has_all_required_columns()
```

## Unit Test

```java
@ExtendWith(MockitoExtension.class)
class FooServiceTest {

    @Mock FooRepository fooRepository;
    @InjectMocks FooService fooService;

    @Test
    void doSomething_validInput_returnsResult() {
        given(fooRepository.findById(1L)).willReturn(Optional.of(foo));

        var result = fooService.doSomething(1L);

        assertThat(result).isEqualTo(expected);
        then(fooRepository).should().findById(1L);
    }
}
```

## Repository Test

```java
@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class FooRepositoryTest {
    @Autowired FooRepository fooRepository;
}
```

## Integration Test

```java
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class FooIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("human-readable description of what is being tested")
    void doSomething_validRequest_returns200() throws Exception {
        mockMvc.perform(post("/api/foo")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"field": "value"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.field").value("value"));
    }
}
```

## Assertions

```java
// value
assertThat(result).isEqualTo(expected);

// optional
assertThat(result).isPresent();

// exception
assertThatThrownBy(() -> service.method(arg))
    .isInstanceOf(FooException.class)
    .satisfies(e -> assertThat(((FooException) e).getCode()).isEqualTo(ErrorCode.FOO_ERROR));

// collection
assertThat(list).contains("a", "b");
assertThat(list).extracting("field").containsExactly("x", "y");
```

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
