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

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthIntegrationTest {

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

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "password123";
    private static final String NICKNAME = "tester";

    @BeforeEach
    void setUp() {
        memberRepository.save(Member.builder()
                .email(EMAIL)
                .password(passwordEncoder.encode(PASSWORD))
                .name("Integration Tester")
                .nickname(NICKNAME)
                .role(Role.MEMBER)
                .build());
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();
    }

    // ── Register ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register - valid request returns 201 with member info")
    void register_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("new@example.com", "newpassword", "New User", "newuser")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("new@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("newuser"));
    }

    @Test
    @DisplayName("POST /api/auth/register - duplicate email returns 409")
    void register_duplicateEmail_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(EMAIL, "somepassword", "Name", "othernick")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("POST /api/auth/register - duplicate nickname returns 409")
    void register_duplicateNickname_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("other@example.com", "somepassword", "Name", NICKNAME)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_NICKNAME"));
    }

    @Test
    @DisplayName("POST /api/auth/register - invalid body returns 400")
    void register_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"name\":\"N\",\"nickname\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ── Register — response contract ───────────────────────────────────────────
    //
    // These tests pin the exact JSON shape that the frontend reads:
    //   response.data.errorCode
    //   response.data.message
    //   response.data.fields[fieldName]
    //
    // Field keys must match the frontend form field names exactly.

    @Test
    @DisplayName("Contract: invalid email format → fields.email is set")
    void registerContract_invalidEmailFormat_fieldsEmailIsSet() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"password123\",\"name\":\"Test\",\"nickname\":\"testuser\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.email").value("올바른 이메일 형식이 아닙니다"));
    }

    @Test
    @DisplayName("Contract: short password → fields.password is set")
    void registerContract_shortPassword_fieldsPasswordIsSet() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"valid@example.com\",\"password\":\"short\",\"name\":\"Test\",\"nickname\":\"testuser\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.password").value("비밀번호는 8자 이상이어야 합니다"));
    }

    @Test
    @DisplayName("Contract: short nickname → fields.nickname is set")
    void registerContract_shortNickname_fieldsNicknameIsSet() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"valid@example.com\",\"password\":\"password123\",\"name\":\"Test\",\"nickname\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.nickname").value("닉네임은 2자 이상 20자 이하여야 합니다"));
    }

    @Test
    @DisplayName("Contract: empty body → fields contains all four blank-field messages")
    void registerContract_emptyBody_allFieldsHaveMessages() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.email").value("이메일을 입력해주세요"))
                .andExpect(jsonPath("$.fields.password").value("비밀번호를 입력해주세요"))
                .andExpect(jsonPath("$.fields.name").value("이름을 입력해주세요"))
                .andExpect(jsonPath("$.fields.nickname").value("닉네임을 입력해주세요"));
    }

    @Test
    @DisplayName("Contract: duplicate email → DUPLICATE_EMAIL, no fields key in response")
    void registerContract_duplicateEmail_noFieldsInResponse() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(EMAIL, "somepassword", "Name", "othernick")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.fields").doesNotExist());
    }

    @Test
    @DisplayName("Contract: duplicate nickname → DUPLICATE_NICKNAME, no fields key in response")
    void registerContract_duplicateNickname_noFieldsInResponse() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("other@example.com", "somepassword", "Name", NICKNAME)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_NICKNAME"))
                .andExpect(jsonPath("$.fields").doesNotExist());
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login - valid credentials return access token and member info")
    void login_validCredentials_returnsAccessTokenAndMemberInfo() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800000))
                .andExpect(jsonPath("$.data.member.email").value(EMAIL))
                .andExpect(jsonPath("$.data.member.nickname").value(NICKNAME))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("refresh_token=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("Path=/api/auth/refresh");

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/auth/login - wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /api/auth/login - unknown email returns 401")
    void login_unknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // ── Protected endpoint ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/test/protected - valid access token returns 200")
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        String accessToken = loginAndGetAccessToken();

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
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        String oldRefreshToken = extractRefreshTokenCookie(loginResult);
        assertThat(refreshTokenRepository.findByToken(oldRefreshToken)).isPresent();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new MockCookie("refresh_token", oldRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();

        assertThat(refreshTokenRepository.findByToken(oldRefreshToken)).isEmpty();

        String newRefreshToken = extractRefreshTokenCookie(refreshResult);
        assertThat(refreshTokenRepository.findByToken(newRefreshToken)).isPresent();

        String newAccessToken = extractDataField(refreshResult, "accessToken");
        assertThat(newAccessToken).isNotBlank();
    }

    @Test
    @DisplayName("POST /api/auth/refresh - missing cookie returns 401")
    void refresh_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/refresh - invalid token returns 401")
    void refresh_invalidToken_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new MockCookie("refresh_token", "this-token-does-not-exist")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/refresh - replay of rotated token returns 401")
    void refresh_replayOfRotatedToken_returns401() throws Exception {
        MvcResult loginResult = performLogin();
        String firstToken = extractRefreshTokenCookie(loginResult);

        MvcResult firstRefresh = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new MockCookie("refresh_token", firstToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(extractRefreshTokenCookie(firstRefresh)).isNotEqualTo(firstToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new MockCookie("refresh_token", firstToken)))
                .andExpect(status().isUnauthorized());
    }

    // ── Logout ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/logout - valid token removes refresh token from Redis")
    void logout_validToken_removesRefreshToken() throws Exception {
        performLogin();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);

        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(refreshTokenRepository.count()).isZero();
    }

    // ── Me endpoint ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/auth/me - returns current member info")
    void me_withValidToken_returnsMemberInfo() throws Exception {
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.nickname").value(NICKNAME));
    }

    // ── DB state ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Login twice - only one refresh token stored per member (rotation)")
    void login_twice_onlyOneRefreshTokenStoredPerMember() throws Exception {
        performLogin();
        performLogin();

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
        return extractDataField(result, "accessToken");
    }

    private String extractDataField(MvcResult result, String field) throws Exception {
        String json = result.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.get("data").get(field).asString();
    }

    private String extractRefreshTokenCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).as("Set-Cookie header must be present").isNotNull();
        String tokenPart = setCookie.split(";")[0];
        return tokenPart.substring("refresh_token=".length());
    }

    private String loginBody(String email, String password) {
        return String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
    }

    private String registerBody(String email, String password, String name, String nickname) {
        return String.format("{\"email\":\"%s\",\"password\":\"%s\",\"name\":\"%s\",\"nickname\":\"%s\"}",
                email, password, name, nickname);
    }
}