package com.agentcart.cart.integration;

import com.agentcart.auth.repository.RefreshTokenRepository;
import com.agentcart.cart.repository.CartRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CartIntegrationTest {

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
    @Autowired CartRepository cartRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String MEMBER_EMAIL = "member@example.com";
    private static final String MEMBER2_EMAIL = "member2@example.com";
    private static final String PASSWORD = "password123";

    @BeforeEach
    void setUp() {
        memberRepository.save(Member.builder()
                .email(MEMBER_EMAIL).password(passwordEncoder.encode(PASSWORD))
                .name("Member").nickname("member").role(Role.MEMBER).build());
        memberRepository.save(Member.builder()
                .email(MEMBER2_EMAIL).password(passwordEncoder.encode(PASSWORD))
                .name("Member2").nickname("member2").role(Role.MEMBER).build());
    }

    @AfterEach
    void tearDown() {
        cartRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAll();
        productRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    // ── GET /api/cart ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/cart - empty cart returns 200 with empty items and zero totalPrice")
    void getCart_empty_returns200WithEmptyItems() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalPrice").value(0));
    }

    @Test
    @DisplayName("GET /api/cart - unauthenticated request returns 401")
    void getCart_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/cart/items ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/cart/items - valid product returns 201 with CartItemResponse")
    void addItem_validProduct_returns201() throws Exception {
        Product product = saveActiveProduct("Laptop", 10);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(product.getId(), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(product.getId()))
                .andExpect(jsonPath("$.data.productName").value("Laptop"))
                .andExpect(jsonPath("$.data.productStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.subtotal").exists());
    }

    @Test
    @DisplayName("POST /api/cart/items - same product added twice accumulates quantity")
    void addItem_sameProduct_accumulatesQuantity() throws Exception {
        Product product = saveActiveProduct("Laptop", 10);
        String token = memberToken();

        addItemViaApi(token, product.getId(), 3);
        addItemViaApi(token, product.getId(), 2);

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(5));
    }

    @Test
    @DisplayName("POST /api/cart/items - SOLD_OUT product returns 400 with PRODUCT_NOT_AVAILABLE")
    void addItem_soldOutProduct_returns400() throws Exception {
        Product product = saveProduct("Sold Out Item", 0, ProductStatus.SOLD_OUT);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(product.getId(), 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("POST /api/cart/items - quantity exceeds stock returns 400 with EXCEEDS_STOCK")
    void addItem_exceedsStock_returns400() throws Exception {
        Product product = saveActiveProduct("Laptop", 5);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(product.getId(), 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("EXCEEDS_STOCK"));
    }

    @Test
    @DisplayName("POST /api/cart/items - unauthenticated request returns 401")
    void addItem_withoutToken_returns401() throws Exception {
        Product product = saveActiveProduct("Laptop", 10);

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(product.getId(), 1)))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/cart/items/{itemId} ──────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/cart/items/{itemId} - valid update returns 200 with updated quantity")
    void updateItem_validRequest_returns200() throws Exception {
        Product product = saveActiveProduct("Laptop", 10);
        String token = memberToken();
        Long itemId = addItemAndGetId(token, product.getId(), 2);

        mockMvc.perform(put("/api/cart/items/{itemId}", itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.quantity").value(7));
    }

    @Test
    @DisplayName("PUT /api/cart/items/{itemId} - another member's item returns 404")
    void updateItem_anotherMembersItem_returns404() throws Exception {
        Product product = saveActiveProduct("Laptop", 10);
        Long itemId = addItemAndGetId(memberToken(), product.getId(), 2);

        mockMvc.perform(put("/api/cart/items/{itemId}", itemId)
                        .header("Authorization", "Bearer " + member2Token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CART_ITEM_NOT_FOUND"));
    }

    // ── DELETE /api/cart/items/{itemId} ──────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/cart/items/{itemId} - removes item and returns 200")
    void removeItem_validRequest_returns200() throws Exception {
        Product product = saveActiveProduct("Laptop", 10);
        String token = memberToken();
        Long itemId = addItemAndGetId(token, product.getId(), 2);

        mockMvc.perform(delete("/api/cart/items/{itemId}", itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("DELETE /api/cart/items/{itemId} - another member's item returns 404")
    void removeItem_anotherMembersItem_returns404() throws Exception {
        Product product = saveActiveProduct("Laptop", 10);
        Long itemId = addItemAndGetId(memberToken(), product.getId(), 2);

        mockMvc.perform(delete("/api/cart/items/{itemId}", itemId)
                        .header("Authorization", "Bearer " + member2Token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CART_ITEM_NOT_FOUND"));
    }

    // ── DELETE /api/cart ──────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/cart - clears all items and returns 200 with empty cart")
    void clearCart_returns200WithEmptyCart() throws Exception {
        Product p1 = saveActiveProduct("Laptop", 10);
        Product p2 = saveActiveProduct("Phone", 10);
        String token = memberToken();
        addItemViaApi(token, p1.getId(), 1);
        addItemViaApi(token, p2.getId(), 2);

        mockMvc.perform(delete("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalPrice").value(0));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String memberToken() throws Exception {
        return loginAndGetToken(MEMBER_EMAIL);
    }

    private String member2Token() throws Exception {
        return loginAndGetToken(MEMBER2_EMAIL);
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

    private Long addItemAndGetId(String token, Long productId, int qty) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(productId, qty)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("id").asLong();
    }

    private void addItemViaApi(String token, Long productId, int qty) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(productId, qty)))
                .andExpect(status().isCreated());
    }

    private Product saveActiveProduct(String name, int stock) {
        return saveProduct(name, stock, ProductStatus.ACTIVE);
    }

    private Product saveProduct(String name, int stock, ProductStatus status) {
        return productRepository.save(Product.builder()
                .name(name).description("Description")
                .price(BigDecimal.valueOf(99.99)).category("electronics")
                .brand("Brand").stock(stock).status(status)
                .build());
    }

    private String addItemBody(Long productId, int quantity) {
        return """
                {"productId":%d,"quantity":%d}
                """.formatted(productId, quantity);
    }
}