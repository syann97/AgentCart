# Backend test examples

Optional examples. Match annotations and container versions to existing tests; these snippets are not current application contracts. Test layer selection and rules: [testing guide](../testing.md).

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
