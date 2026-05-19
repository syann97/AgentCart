package com.agentcart.order.integration;

import com.agentcart.auth.repository.RefreshTokenRepository;
import com.agentcart.cart.repository.CartRepository;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
import com.agentcart.order.repository.OrderRepository;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OrderConcurrencyTest {

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

    private static final String PASSWORD = "password123";
    private static final BigDecimal PRICE = BigDecimal.valueOf(10000);

    @AfterEach
    void tearDown() {
        orderRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAll();
        productRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    // ── 시나리오 1: 바로구매 동시성 ────────────────────────────────────────────

    @Test
    @DisplayName("재고 5개 상품에 10명 동시 바로구매 → 성공 5건, 최종 재고 0, SOLD_OUT")
    void concurrentDirectOrders_respectsStock() throws Exception {
        int stock = 5;
        int threadCount = 10;
        Product product = saveProduct("상품A", stock);
        String token = createMemberAndLogin("buyer@test.com");

        int successCount = runConcurrently(threadCount, () ->
                mockMvc.perform(post("/api/orders")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(directOrderBody(product.getId(), 1)))
                        .andReturn()
        );

        assertThat(successCount).isEqualTo(stock);
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(0);
        assertThat(updated.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    // ── 시나리오 2: 장바구니 주문 동시성 ──────────────────────────────────────

    @Test
    @DisplayName("재고 3개 상품에 5명 동시 장바구니 주문 → 성공 3건, 최종 재고 0, SOLD_OUT")
    void concurrentCartOrders_respectsStock() throws Exception {
        int stock = 3;
        int threadCount = 5;
        Product product = saveProduct("상품B", stock);

        List<String> tokens = new ArrayList<>();
        List<Long> cartItemIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            String token = createMemberAndLogin("buyer" + i + "@test.com");
            tokens.add(token);
            cartItemIds.add(addToCartAndGetItemId(token, product.getId(), 1));
        }

        AtomicInteger successCount = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String token = tokens.get(i);
            final Long cartItemId = cartItemIds.get(i);
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    MvcResult result = mockMvc.perform(post("/api/orders")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(cartOrderBody(cartItemId)))
                            .andReturn();
                    if (result.getResponse().getStatus() == 201) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) { }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(stock);
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(0);
        assertThat(updated.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface MvcAction {
        MvcResult run() throws Exception;
    }

    private int runConcurrently(int threadCount, MvcAction action) throws InterruptedException {
        AtomicInteger successCount = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    MvcResult result = action.run();
                    if (result.getResponse().getStatus() == 201) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) { }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        return successCount.get();
    }

    private Product saveProduct(String name, int stock) {
        return productRepository.save(Product.builder()
                .name(name).description("desc").price(PRICE)
                .category("test").brand("brand").stock(stock).status(ProductStatus.ACTIVE)
                .build());
    }

    private String createMemberAndLogin(String email) throws Exception {
        memberRepository.save(Member.builder()
                .email(email).password(passwordEncoder.encode(PASSWORD))
                .name("User").nickname(email.split("@")[0]).role(Role.MEMBER)
                .build());
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

    private String directOrderBody(Long productId, int qty) {
        return """
                {"productId":%d,"quantity":%d,"recipientName":"홍길동","phone":"010-1234-5678","address":"서울시 강남구"}
                """.formatted(productId, qty);
    }

    private String cartOrderBody(Long cartItemId) {
        return """
                {"cartItemIds":[%d],"recipientName":"홍길동","phone":"010-1234-5678","address":"서울시 강남구"}
                """.formatted(cartItemId);
    }
}