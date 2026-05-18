package com.agentcart.payment.integration;

import com.agentcart.auth.repository.RefreshTokenRepository;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
import com.agentcart.order.domain.Order;
import com.agentcart.order.repository.OrderRepository;
import com.agentcart.payment.domain.Payment;
import com.agentcart.payment.repository.PaymentRepository;
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
class PaymentIntegrationTest {

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
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String MEMBER_EMAIL = "member@example.com";
    private static final String MEMBER2_EMAIL = "member2@example.com";
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
    }

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAll();
        productRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    // ── POST /api/payments ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/payments - 결제 성공 → 201, COMPLETED, Order CONFIRMED")
    void pay_success_returns201() throws Exception {
        Product product = saveProduct("Laptop", 5);
        String token = memberToken();
        Long orderId = createOrderAndGetId(token, product.getId(), 1);

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d}".formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.paymentKey").isNotEmpty());

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("POST /api/payments - 미인증 요청 → 401")
    void pay_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/payments - 없는 주문 → 404 ORDER_NOT_FOUND")
    void pay_orderNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":99999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/payments - 타인 주문 결제 시도 → 403 PAYMENT_ACCESS_DENIED")
    void pay_anotherMembersOrder_returns403() throws Exception {
        Product product = saveProduct("Phone", 5);
        Long orderId = createOrderAndGetId(memberToken(), product.getId(), 1);

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + member2Token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d}".formatted(orderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /api/payments - PENDING이 아닌 주문 결제 → 400 PAYMENT_ALREADY_COMPLETED")
    void pay_notPendingOrder_returns400() throws Exception {
        Product product = saveProduct("Watch", 5);
        String token = memberToken();
        Long orderId = createOrderAndGetId(token, product.getId(), 1);

        // 첫 번째 결제 성공
        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d}".formatted(orderId)))
                .andExpect(status().isCreated());

        // 두 번째 결제 시도 → CONFIRMED 상태라 거부
        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d}".formatted(orderId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_ALREADY_COMPLETED"));
    }

    // ── GET /api/payments/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/payments/{id} - 본인 결제 조회 → 200")
    void getPayment_success_returns200() throws Exception {
        Product product = saveProduct("Laptop", 5);
        String token = memberToken();
        Long orderId = createOrderAndGetId(token, product.getId(), 1);
        Long paymentId = payAndGetId(token, orderId);

        mockMvc.perform(get("/api/payments/{id}", paymentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(paymentId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/payments/{id} - 타인 결제 조회 → 403 PAYMENT_ACCESS_DENIED")
    void getPayment_anotherMember_returns403() throws Exception {
        Product product = saveProduct("Laptop", 5);
        String token = memberToken();
        Long orderId = createOrderAndGetId(token, product.getId(), 1);
        Long paymentId = payAndGetId(token, orderId);

        mockMvc.perform(get("/api/payments/{id}", paymentId)
                        .header("Authorization", "Bearer " + member2Token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_ACCESS_DENIED"));
    }

    // ── GET /api/payments/order/{orderId} ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/payments/order/{orderId} - 주문 ID로 결제 조회 → 200")
    void getPaymentByOrder_success_returns200() throws Exception {
        Product product = saveProduct("Laptop", 5);
        String token = memberToken();
        Long orderId = createOrderAndGetId(token, product.getId(), 1);
        payAndGetId(token, orderId);

        mockMvc.perform(get("/api/payments/order/{orderId}", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/payments/order/{orderId} - 결제 없는 주문 조회 → 404 PAYMENT_NOT_FOUND")
    void getPaymentByOrder_paymentNotFound_returns404() throws Exception {
        Product product = saveProduct("Laptop", 5);
        String token = memberToken();
        Long orderId = createOrderAndGetId(token, product.getId(), 1);

        mockMvc.perform(get("/api/payments/order/{orderId}", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"));
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

    private Long createOrderAndGetId(String token, Long productId, int qty) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"quantity":%d,"recipientName":"홍길동",
                                "phone":"010-1234-5678","address":"서울시 강남구"}
                                """.formatted(productId, qty)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("id").asLong();
    }

    private Long payAndGetId(String token, Long orderId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d}".formatted(orderId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("id").asLong();
    }

    private Product saveProduct(String name, int stock) {
        return productRepository.save(Product.builder()
                .name(name).description("Description")
                .price(PRICE).category("electronics")
                .brand("Brand").stock(stock).status(ProductStatus.ACTIVE)
                .build());
    }
}