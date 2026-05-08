package com.agentcart.auth.integration;

import com.agentcart.auth.repository.RefreshTokenRepository;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration test for the JWT authentication flow.
 *
 * Infrastructure: MySQL (primary DB) + Redis (Redisson) via TestContainers.
 * Flyway runs on startup and creates the schema.
 *
 * Note: if 'spring.autoconfigure.exclude' entries in application-test.yaml reference
 * wrong class names for your Spring AI version, remove them and add the correct ones.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthIntegrationTest {

    // ── Test-only controller (exposes a protected endpoint for filter tests) ───

    @TestConfiguration
    static class TestControllerConfig {
        @RestController
        @RequestMapping("/api/test")
        static class ProtectedController {
            @GetMapping("/protected")
            ResponseEntity<String> protect() {
                return ResponseEntity.ok("authenticated");
            }
        }
    }

    // ── TestContainers ─────────────────────────────────────────────────────────

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // ── Spring beans ───────────────────────────────────────────────────────────

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Test member credentials ────────────────────────────────────────────────

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "password123";

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        Member member = Member.builder()
                .email(EMAIL)
                .password(passwordEncoder.encode(PASSWORD))
                .name("Integration Tester")
                .role(Role.MEMBER)
                .build();
        memberRepository.save(member);
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login - valid credentials return access token and set refresh cookie")
    void login_validCredentials_returnsAccessTokenAndSetsRefreshCookie() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800000))
                .andReturn();

        // Then: refresh token cookie is set with correct attributes
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("refresh_token=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("Path=/api/auth/refresh");

        // Then: refresh token persisted in DB
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/auth/login - wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login - unknown email returns 401")
    void login_unknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // ── JwtVerificationFilter: protected endpoint ──────────────────────────────

    @Test
    @DisplayName("GET /api/test/protected - valid access token returns 200")
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        // Given: obtain access token via login
        String accessToken = loginAndGetAccessToken();

        // When / Then
        mockMvc.perform(get("/api/test/protected")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/test/protected - missing token returns 401")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/test/protected - tampered token returns 401")
    void protectedEndpoint_withTamperedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/test/protected")
                        .header("Authorization", "Bearer tampered.token.value"))
                .andExpect(status().isUnauthorized());
    }

    // ── Refresh token rotation ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/refresh - valid cookie returns new access token and rotates refresh token")
    void refresh_validCookie_returnsNewAccessTokenAndRotatesRefreshToken() throws Exception {
        // Given: login to get the initial refresh token
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        String oldRefreshToken = extractRefreshTokenCookie(loginResult);
        assertThat(refreshTokenRepository.findByToken(oldRefreshToken)).isPresent();

        // When: call refresh endpoint with the cookie
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new MockCookie("refresh_token",oldRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        // Then: old token is deleted (rotation)
        assertThat(refreshTokenRepository.findByToken(oldRefreshToken)).isEmpty();

        // Then: new refresh token is stored in DB
        String newRefreshToken = extractRefreshTokenCookie(refreshResult);
        assertThat(refreshTokenRepository.findByToken(newRefreshToken)).isPresent();

        // Then: new access token differs from the perspective of being a valid JWT
        String newAccessToken = extractJsonField(refreshResult, "accessToken");
        assertThat(newAccessToken).isNotBlank();
    }

    @Test
    @DisplayName("POST /api/auth/refresh - missing cookie returns 401")
    void refresh_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/refresh - invalid (non-existent) token returns 401")
    void refresh_invalidToken_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new MockCookie("refresh_token","this-token-does-not-exist")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/refresh - replay (using old rotated token) returns 401")
    void refresh_replayOfRotatedToken_returns401() throws Exception {
        // Given: login and refresh once
        MvcResult loginResult = performLogin();
        String firstToken = extractRefreshTokenCookie(loginResult);

        MvcResult firstRefresh = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new MockCookie("refresh_token",firstToken)))
                .andExpect(status().isOk())
                .andReturn();

        // The first token was rotated — using it again must fail
        assertThat(extractRefreshTokenCookie(firstRefresh)).isNotEqualTo(firstToken);

        // When: replay the original (now rotated) token
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new MockCookie("refresh_token",firstToken)))
                .andExpect(status().isUnauthorized());
    }

    // ── DB state verification ──────────────────────────────────────────────────

    @Test
    @DisplayName("Login followed by second login - only one refresh token exists per member (rotation)")
    void login_twice_onlyOneRefreshTokenStoredPerMember() throws Exception {
        // When: login twice
        performLogin();
        performLogin();

        // Then: only the latest refresh token persisted
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private MvcResult performLogin() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String loginAndGetAccessToken() throws Exception {
        MvcResult result = performLogin();
        return extractJsonField(result, "accessToken");
    }

    private String extractJsonField(MvcResult result, String field) throws Exception {
        String json = result.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.get(field).asString();
    }

    private String extractRefreshTokenCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).as("Set-Cookie header must be present").isNotNull();
        // Format: refresh_token=<value>; ...
        String tokenPart = setCookie.split(";")[0];
        return tokenPart.substring("refresh_token=".length());
    }

    private String loginBody(String email, String password) {
        return String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
    }
}