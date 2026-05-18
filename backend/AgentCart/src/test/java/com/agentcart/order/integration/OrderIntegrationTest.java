package com.agentcart.order.integration;

import com.agentcart.auth.repository.RefreshTokenRepository;
import com.agentcart.cart.repository.CartRepository;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
import com.agentcart.order.domain.OrderStatus;
import com.agentcart.order.repository.OrderRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OrderIntegrationTest {

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
    @Autowired OrderRepository orderRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String MEMBER_EMAIL = "member@example.com";
    private static final String MEMBER2_EMAIL = "member2@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String PASSWORD = "password123";
    private static final BigDecimal PRICE = BigDecimal.valueOf(10000);

    @BeforeEach
    void setUp() {
        memberRepository.save(Member.builder()
                .email(MEMBER_EMAIL).password(passwordEncoder.encode(PASSWORD))
                .name("Member").nickname("member").role(Role.MEMBER).build());
        memberRepository.save(Member.builder()
                .email(MEMBER2_EMAIL).password(passwordEncoder.encode(PASSWORD))
                .name("Member2").nickname("member2").role(Role.MEMBER).build());
        memberRepository.save(Member.builder()
                .email(ADMIN_EMAIL).password(passwordEncoder.encode(PASSWORD))
                .name("Admin").nickname("admin").role(Role.ADMIN).build());
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAll();
        productRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    // ── POST /api/orders - 장바구니 선택 주문 ──────────────────────────────────

    @Test
    @DisplayName("POST /api/orders - 장바구니 주문: 정상 생성, 재고 감소, CartItem 제거")
    void createOrder_fromCart_returns201AndDecreasesStockAndRemovesCartItem() throws Exception {
        Product product = saveProduct("Laptop", 10, ProductStatus.ACTIVE);
        String token = memberToken();
        Long cartItemId = addToCartAndGetItemId(token, product.getId(), 3);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartOrderBody(cartItemId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalPrice").value(30000))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productName").value("Laptop"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3));

        assertThat(productRepository.findById(product.getId()).get().getStock()).isEqualTo(7);

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("POST /api/orders - 장바구니 주문: 존재하지 않는 cartItemId → 404")
    void createOrder_fromCart_invalidCartItemId_returns404() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartOrderBody(99999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/orders - 장바구니 주문: 재고 부족 → 400")
    void createOrder_fromCart_insufficientStock_returns400() throws Exception {
        Product product = saveProduct("Laptop", 5, ProductStatus.ACTIVE);
        String token = memberToken();
        Long cartItemId = addToCartAndGetItemId(token, product.getId(), 5);

        // 다른 경로로 재고 소진
        Product fresh = productRepository.findById(product.getId()).get();
        fresh.decreaseStock(5);
        productRepository.saveAndFlush(fresh);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartOrderBody(cartItemId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
    }

    // ── POST /api/orders - 바로구매 ────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/orders - 바로구매: 정상 생성, 재고 감소")
    void createOrder_direct_returns201AndDecreasesStock() throws Exception {
        Product product = saveProduct("Phone", 10, ProductStatus.ACTIVE);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directOrderBody(product.getId(), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalPrice").value(20000))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));

        assertThat(productRepository.findById(product.getId()).get().getStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("POST /api/orders - 바로구매: SOLD_OUT 상품 → 400")
    void createOrder_direct_soldOutProduct_returns400() throws Exception {
        Product product = saveProduct("SoldOut", 0, ProductStatus.SOLD_OUT);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directOrderBody(product.getId(), 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("POST /api/orders - 바로구매: INACTIVE 상품 → 400")
    void createOrder_direct_inactiveProduct_returns400() throws Exception {
        Product product = saveProduct("Inactive", 5, ProductStatus.INACTIVE);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directOrderBody(product.getId(), 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("POST /api/orders - 바로구매: 재고 초과 수량 → 400")
    void createOrder_direct_exceedsStock_returns400() throws Exception {
        Product product = saveProduct("Watch", 3, ProductStatus.ACTIVE);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directOrderBody(product.getId(), 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
    }

    @Test
    @DisplayName("POST /api/orders - cartItemIds와 productId 동시 전달 → 400")
    void createOrder_bothInputs_returns400() throws Exception {
        Product product = saveProduct("Laptop", 10, ProductStatus.ACTIVE);
        String token = memberToken();
        Long cartItemId = addToCartAndGetItemId(token, product.getId(), 1);

        String body = """
                {"cartItemIds":[%d],"productId":%d,"quantity":1,
                "recipientName":"홍길동","phone":"010-1234-5678","address":"서울"}
                """.formatted(cartItemId, product.getId());

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/orders - 주문 타입 미전달 → 400")
    void createOrder_noInput_returns400() throws Exception {
        String body = """
                {"recipientName":"홍길동","phone":"010-1234-5678","address":"서울"}
                """;

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ── GET /api/orders ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders - 내 주문 목록 페이징 반환")
    void getOrders_returnsPaged() throws Exception {
        Product product = saveProduct("Laptop", 10, ProductStatus.ACTIVE);
        String token = memberToken();
        createDirectOrderViaApi(token, product.getId(), 1);
        createDirectOrderViaApi(token, product.getId(), 1);

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    // ── GET /api/orders/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders/{id} - 내 주문 상세 조회 → 200")
    void getOrder_ownOrder_returns200() throws Exception {
        Product product = saveProduct("Laptop", 10, ProductStatus.ACTIVE);
        String token = memberToken();
        Long orderId = createDirectOrderAndGetId(token, product.getId(), 1);

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} - 다른 회원의 주문 접근 → 403")
    void getOrder_anotherMembersOrder_returns403() throws Exception {
        Product product = saveProduct("Laptop", 10, ProductStatus.ACTIVE);
        Long orderId = createDirectOrderAndGetId(memberToken(), product.getId(), 1);

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + member2Token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER_ACCESS_DENIED"));
    }

    // ── DELETE /api/orders/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/orders/{id} - PENDING 주문 취소, 재고 복원 → 200")
    void cancelOrder_pending_returns200AndRestoresStock() throws Exception {
        Product product = saveProduct("Laptop", 10, ProductStatus.ACTIVE);
        String token = memberToken();
        Long orderId = createDirectOrderAndGetId(token, product.getId(), 3);

        assertThat(productRepository.findById(product.getId()).get().getStock()).isEqualTo(7);

        mockMvc.perform(delete("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(productRepository.findById(product.getId()).get().getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("DELETE /api/orders/{id} - SHIPPED 이후 취소 불가 → 400")
    void cancelOrder_shipped_returns400() throws Exception {
        Product product = saveProduct("Laptop", 10, ProductStatus.ACTIVE);
        String memberToken = memberToken();
        String adminToken = adminToken();
        Long orderId = createDirectOrderAndGetId(memberToken, product.getId(), 1);

        patchStatus(adminToken, orderId, OrderStatus.SHIPPED);

        mockMvc.perform(delete("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_CANCELLABLE"));
    }

    // ── PATCH /api/orders/{id}/status ─────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/orders/{id}/status - ADMIN: CONFIRMED → SHIPPED → DELIVERED")
    void updateStatus_adminChangesStatus_returns200() throws Exception {
        Product product = saveProduct("Laptop", 10, ProductStatus.ACTIVE);
        Long orderId = createDirectOrderAndGetId(memberToken(), product.getId(), 1);
        String adminToken = adminToken();

        patchStatus(adminToken, orderId, OrderStatus.CONFIRMED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        patchStatus(adminToken, orderId, OrderStatus.SHIPPED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));

        patchStatus(adminToken, orderId, OrderStatus.DELIVERED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("PATCH /api/orders/{id}/status - MEMBER 접근 → 403")
    void updateStatus_memberAccess_returns403() throws Exception {
        Product product = saveProduct("Laptop", 10, ProductStatus.ACTIVE);
        Long orderId = createDirectOrderAndGetId(memberToken(), product.getId(), 1);

        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String memberToken() throws Exception {
        return loginAndGetToken(MEMBER_EMAIL);
    }

    private String member2Token() throws Exception {
        return loginAndGetToken(MEMBER2_EMAIL);
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

    private Long addToCartAndGetItemId(String token, Long productId, int qty) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":%d}".formatted(productId, qty)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("id").asLong();
    }

    private Long createDirectOrderAndGetId(String token, Long productId, int qty) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directOrderBody(productId, qty)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("id").asLong();
    }

    private void createDirectOrderViaApi(String token, Long productId, int qty) throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directOrderBody(productId, qty)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions patchStatus(
            String token, Long orderId, OrderStatus status) throws Exception {
        return mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\"}".formatted(status.name())));
    }

    private Product saveProduct(String name, int stock, ProductStatus status) {
        return productRepository.save(Product.builder()
                .name(name).description("Description")
                .price(PRICE).category("electronics")
                .brand("Brand").stock(stock).status(status)
                .build());
    }

    private String cartOrderBody(Long cartItemId) {
        return """
                {"cartItemIds":[%d],"recipientName":"홍길동","phone":"010-1234-5678","address":"서울시 강남구"}
                """.formatted(cartItemId);
    }

    private String directOrderBody(Long productId, int qty) {
        return """
                {"productId":%d,"quantity":%d,"recipientName":"홍길동","phone":"010-1234-5678","address":"서울시 강남구"}
                """.formatted(productId, qty);
    }
}