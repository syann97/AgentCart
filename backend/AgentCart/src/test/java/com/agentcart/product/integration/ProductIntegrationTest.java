package com.agentcart.product.integration;

import com.agentcart.auth.repository.RefreshTokenRepository;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ProductIntegrationTest {

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
    @Autowired ProductRepository productRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String MEMBER_EMAIL = "member@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String PASSWORD = "password123";

    @BeforeEach
    void setUp() {
        memberRepository.save(Member.builder()
                .email(MEMBER_EMAIL).password(passwordEncoder.encode(PASSWORD))
                .name("Member").nickname("member").role(Role.MEMBER).build());
        memberRepository.save(Member.builder()
                .email(ADMIN_EMAIL).password(passwordEncoder.encode(PASSWORD))
                .name("Admin").nickname("admin").role(Role.ADMIN).build());
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAll();
        productRepository.deleteAll();
        memberRepository.deleteAll();
    }

    // ── GET /api/products ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products - authenticated member returns 200 with product list")
    void getProducts_withValidToken_returns200() throws Exception {
        saveProduct("Laptop", "electronics");
        saveProduct("Phone", "electronics");

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/products - unauthenticated request returns 401")
    void getProducts_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/products?search= - returns products matching name, brand, or category")
    void getProducts_withSearch_returnsMatchedList() throws Exception {
        saveProduct("Laptop Pro", "electronics");
        saveProduct("Phone Max", "electronics");
        saveProduct("Cotton Shirt", "clothing");

        mockMvc.perform(get("/api/products").param("search", "laptop")
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Laptop Pro"));
    }

    // ── GET /api/products/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/{id} - existing product returns 200 with full detail")
    void getProduct_existingId_returns200() throws Exception {
        Product saved = saveProduct("Laptop", "electronics");

        mockMvc.perform(get("/api/products/{id}", saved.getId())
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(saved.getId()))
                .andExpect(jsonPath("$.data.name").value("Laptop"))
                .andExpect(jsonPath("$.data.stock").exists())
                .andExpect(jsonPath("$.data.description").exists());
    }

    @Test
    @DisplayName("GET /api/products/{id} - non-existent product returns 404")
    void getProduct_notExistingId_returns404() throws Exception {
        mockMvc.perform(get("/api/products/{id}", 99999L)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    // ── POST /api/products ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/products - admin creates product and returns 201")
    void createProduct_asAdmin_returns201() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Laptop", "electronics", "99.99")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Laptop"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(productRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/products - member role returns 403")
    void createProduct_asMember_returns403() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Laptop", "electronics", "99.99")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /api/products - unauthenticated request returns 401")
    void createProduct_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Laptop", "electronics", "99.99")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/products - invalid body returns 400 with field errors")
    void createProduct_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":\"-1\",\"category\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ── PUT /api/products/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/products/{id} - admin updates product and returns 200")
    void updateProduct_asAdmin_returns200() throws Exception {
        Product saved = saveProduct("Old Name", "electronics");

        mockMvc.perform(put("/api/products/{id}", saved.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("New Name", "clothing")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"))
                .andExpect(jsonPath("$.data.category").value("clothing"));
    }

    @Test
    @DisplayName("PUT /api/products/{id} - member role returns 403")
    void updateProduct_asMember_returns403() throws Exception {
        Product saved = saveProduct("Laptop", "electronics");

        mockMvc.perform(put("/api/products/{id}", saved.getId())
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("New Name", "clothing")))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/products/{id} ─────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/products/{id} - admin deletes product and returns 200")
    void deleteProduct_asAdmin_returns200() throws Exception {
        Product saved = saveProduct("Laptop", "electronics");

        mockMvc.perform(delete("/api/products/{id}", saved.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(productRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/products/{id} - member role returns 403")
    void deleteProduct_asMember_returns403() throws Exception {
        Product saved = saveProduct("Laptop", "electronics");

        mockMvc.perform(delete("/api/products/{id}", saved.getId())
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/products (embedding) ───────────────────────────────────────

    @Test
    @DisplayName("POST /api/products - embedding failure does not affect product save")
    void createProduct_embeddingFails_returns201() throws Exception {
        given(embeddingModel.embed(anyString())).willThrow(new RuntimeException("model unavailable"));

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Laptop", "electronics", "99.99")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Laptop"));

        assertThat(productRepository.count()).isEqualTo(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String memberToken() throws Exception {
        return loginAndGetToken(MEMBER_EMAIL);
    }

    private String adminToken() throws Exception {
        return loginAndGetToken(ADMIN_EMAIL);
    }

    private String loginAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("accessToken").asString();
    }

    private Product saveProduct(String name, String category) {
        return productRepository.save(Product.builder()
                .name(name).description("Description")
                .price(BigDecimal.valueOf(99.99)).category(category)
                .brand("Brand").stock(10).status(ProductStatus.ACTIVE)
                .build());
    }

    private String createBody(String name, String category, String price) {
        return """
                {"name":"%s","description":"Description","price":%s,"category":"%s","brand":"Brand","stock":10}
                """.formatted(name, price, category);
    }

    private String updateBody(String name, String category) {
        return """
                {"name":"%s","description":"Updated","price":"149.99","category":"%s","brand":"Brand","stock":5}
                """.formatted(name, category);
    }
}