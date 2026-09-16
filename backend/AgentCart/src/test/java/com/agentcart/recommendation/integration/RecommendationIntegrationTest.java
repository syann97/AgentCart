package com.agentcart.recommendation.integration;

import com.agentcart.auth.repository.RefreshTokenRepository;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
import com.agentcart.order.domain.Order;
import com.agentcart.order.domain.OrderItem;
import com.agentcart.order.repository.OrderRepository;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.domain.RecommendationHistory;
import com.agentcart.recommendation.repository.RecommendationHistoryRepository;
import com.agentcart.recommendation.repository.RecommendationVectorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired RecommendationVectorRepository vectorRepository;
    @Autowired @Qualifier("pgVectorJdbcTemplate") NamedParameterJdbcTemplate pgVectorJdbcTemplate;
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
        orderRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        pgVectorJdbcTemplate.update("DELETE FROM product_embeddings", Map.of());
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

    @Test
    @DisplayName("GET /api/recommendations/stream - 미인증 요청은 SSE 연결 전에 401 반환")
    void stream_unauthenticated_returns401BeforeSseConnection() throws Exception {
        mockMvc.perform(get("/api/recommendations/stream").param("query", "캠핑 의자"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/recommendations/stream - 빈 질의는 SSE 연결 전에 400 반환")
    void stream_blankQuery_returns400BeforeSseConnection() throws Exception {
        mockMvc.perform(get("/api/recommendations/stream")
                        .param("query", " ")
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("searchCatalog 사전 필터 - 가격·카테고리·ACTIVE·재고·최근 주문을 MySQL에서 적용")
    void searchCatalogEligibility_explicitPolicies_returnsOnlyAllowedIds() {
        Product eligible = saveProduct("업무 노트 허용", 30_000, "문구·오피스", 10, ProductStatus.ACTIVE);
        saveProduct("업무 노트 고가", 80_000, "문구·오피스", 10, ProductStatus.ACTIVE);
        saveProduct("업무 노트 타카테고리", 30_000, "디지털·IT기기", 10, ProductStatus.ACTIVE);
        saveProduct("업무 노트 품절", 30_000, "문구·오피스", 0, ProductStatus.ACTIVE);
        saveProduct("업무 노트 비활성", 30_000, "문구·오피스", 10, ProductStatus.INACTIVE);
        Product ordered = saveProduct("업무 노트 최근주문", 30_000, "문구·오피스", 10, ProductStatus.ACTIVE);
        Order order = new Order(member, ordered.getPrice(), "Member", "01012345678", "Seoul", null);
        order.addItem(new OrderItem(order, ordered, 1));
        orderRepository.saveAndFlush(order);

        List<Long> allowedIds = productRepository.findEligibleProductIdsByCategories(
                member.getId(), LocalDateTime.now().minusDays(7), ProductStatus.ACTIVE,
                BigDecimal.valueOf(20_000), BigDecimal.valueOf(50_000), List.of("문구·오피스"));

        assertThat(allowedIds).containsExactly(eligible.getId());
        assertThat(productRepository.bm25SearchWithinIds("업무 노트", allowedIds, 50))
                .extracting(row -> ((Number) row[0]).longValue())
                .containsExactly(eligible.getId());
    }

    @Test
    @DisplayName("searchCatalog Vector 조회 - PostgreSQL 검색 전에 허용 상품 ID 적용")
    void searchCatalogVector_allowedIds_filtersBeforeSimilarityLimit() {
        float[] query = vector(1.0f, 0.0f);
        pgVectorJdbcTemplate.update(
                "INSERT INTO product_embeddings(product_id, embedding, model) " +
                        "VALUES (:productId, CAST(:embedding AS vector), 'bge-m3')",
                Map.of("productId", 101L, "embedding", vectorText(vector(1.0f, 0.0f))));
        pgVectorJdbcTemplate.update(
                "INSERT INTO product_embeddings(product_id, embedding, model) " +
                        "VALUES (:productId, CAST(:embedding AS vector), 'bge-m3')",
                Map.of("productId", 202L, "embedding", vectorText(vector(0.9f, 0.1f))));

        var results = vectorRepository.findTopBySimilarityWithinIds(query, List.of(202L), 50, 0.4);

        assertThat(results).extracting(result -> result.productId()).containsExactly(202L);
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

    private Product saveProduct(String name, long price, String category, int stock, ProductStatus status) {
        return productRepository.saveAndFlush(Product.builder()
                .name(name).description("업무용 필기 상품").price(BigDecimal.valueOf(price))
                .category(category).brand("AgentCart").stock(stock).status(status).build());
    }

    private float[] vector(float first, float second) {
        float[] vector = new float[1024];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }

    private String vectorText(float[] vector) {
        StringBuilder text = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) text.append(',');
            text.append(vector[index]);
        }
        return text.append(']').toString();
    }
}
