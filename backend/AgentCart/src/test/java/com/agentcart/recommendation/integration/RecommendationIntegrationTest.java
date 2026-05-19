package com.agentcart.recommendation.integration;

import com.agentcart.auth.repository.RefreshTokenRepository;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
import com.agentcart.recommendation.domain.RecommendationHistory;
import com.agentcart.recommendation.repository.RecommendationHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class RecommendationIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> pgvector = new PostgreSQLContainer<>("pgvector/pgvector:0.8.2-pg17")
            .withDatabaseName("agentcart")
            .withUsername("postgres")
            .withPassword("postgres");

    @MockitoBean
    EmbeddingModel embeddingModel;

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @DynamicPropertySource
    static void configurePgvector(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.pgvector.url", pgvector::getJdbcUrl);
        registry.add("spring.datasource.pgvector.username", pgvector::getUsername);
        registry.add("spring.datasource.pgvector.password", pgvector::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MemberRepository memberRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired RecommendationHistoryRepository historyRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String MEMBER_EMAIL = "member@example.com";
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String PASSWORD = "password123";

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
                .email(MEMBER_EMAIL).password(passwordEncoder.encode(PASSWORD))
                .name("Member").nickname("member").role(Role.MEMBER).build());
        memberRepository.save(Member.builder()
                .email(OTHER_EMAIL).password(passwordEncoder.encode(PASSWORD))
                .name("Other").nickname("other").role(Role.MEMBER).build());
    }

    @AfterEach
    void tearDown() {
        historyRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAllInBatch();
    }

    // ── GET /api/recommendations/history ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/recommendations/history - 이력 없을 때 빈 리스트 반환")
    void getHistory_noHistory_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/recommendations/history")
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/recommendations/history - 이력 있을 때 최신순 반환")
    void getHistory_withHistory_returnsOrderedList() throws Exception {
        historyRepository.save(RecommendationHistory.of(member.getId(), "노트북", 1L, "MacBook", "고성능 노트북", 0.85));
        historyRepository.save(RecommendationHistory.of(member.getId(), "의류", 2L, "청바지", "캐주얼 의류", 0.72));

        mockMvc.perform(get("/api/recommendations/history")
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].productName").value("청바지"))
                .andExpect(jsonPath("$.data[0].score").value(0.72))
                .andExpect(jsonPath("$.data[1].productName").value("MacBook"));
    }

    @Test
    @DisplayName("GET /api/recommendations/history - 다른 멤버의 이력은 포함되지 않음")
    void getHistory_onlyReturnsOwnHistory() throws Exception {
        Member other = memberRepository.findByEmail(OTHER_EMAIL).orElseThrow();
        historyRepository.save(RecommendationHistory.of(member.getId(), "노트북", 1L, "MacBook", "이유", 0.85));
        historyRepository.save(RecommendationHistory.of(other.getId(), "의류", 2L, "청바지", "이유", 0.72));

        mockMvc.perform(get("/api/recommendations/history")
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("MacBook"));
    }

    @Test
    @DisplayName("GET /api/recommendations/history - 미인증 요청 → 401")
    void getHistory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/recommendations/history"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String memberToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + MEMBER_EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("accessToken").asString();
    }
}
