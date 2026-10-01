package com.agentcart.recommendation.integration;

import com.agentcart.recommendation.consumer.RecommendationServedConsumer;
import com.agentcart.recommendation.domain.RecommendationHistory;
import com.agentcart.recommendation.dto.RecommendationServedEvent;
import com.agentcart.recommendation.repository.RecommendationHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Only the Consumer -> repository -> MySQL boundary is under test; no Kafka,
// Redis or model server is needed. Do not wrap deliveries in a test transaction.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import(RecommendationServedConsumerIntegrationTest.ConsumerConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RecommendationServedConsumerIntegrationTest {
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired RecommendationHistoryRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired RecommendationServedConsumer consumer;

    @TestConfiguration(proxyBeanMethods = false)
    static class ConsumerConfiguration {
        @Bean
        RecommendationServedConsumer consumer(RecommendationHistoryRepository repository) {
            // Spring manages this bean so transactional annotations cannot be
            // accidentally bypassed by constructing the listener in each test.
            return new RecommendationServedConsumer(repository, new ObjectMapper());
        }
    }

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
    }

    @Test
    @DisplayName("실제 MySQL INSERT 실패 뒤 재전달하면 이력이 한 건 저장된다")
    void consume_databaseInsertFailure_redeliveryStoresOneHistory() {
        String json = eventJson("evt-retry");
        // A temporary CHECK fails the real insert using the normal migration user.
        // Triggers would require SUPER with MySQL's default binary logging.
        jdbc.execute("ALTER TABLE recommendation_history ADD CONSTRAINT fail_history_insert CHECK (product_id <> 10)");
        try {
            assertThatThrownBy(() -> consumer.consume(json)).isInstanceOf(DataAccessException.class);
            assertThat(repository.count()).isZero();
            assertThat(repository.existsByEventId("evt-retry")).isFalse();
        } finally {
            jdbc.execute("ALTER TABLE recommendation_history DROP CHECK fail_history_insert");
        }
        consumer.consume(json);
        consumer.consume(json);
        assertSingleEvent("evt-retry");
    }

    @Test
    @DisplayName("INSERT 후 트랜잭션 rollback된 eventId도 다시 저장할 수 있다")
    void consume_rolledBackInsert_redeliveryStoresOneHistory() {
        var failure = new IllegalStateException("fail before commit");
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            repository.saveAndFlush(RecommendationHistory.ofEvent("evt-rollback", 1L, "query", 10L, "product", "reason", 0.8));
            throw failure;
        })).isSameAs(failure);
        assertThat(repository.count()).isZero();
        consumer.consume(eventJson("evt-rollback"));
        assertSingleEvent("evt-rollback");
    }

    @Test
    @DisplayName("Redis 없이 성공한 이벤트를 반복 재전달해도 한 건만 유지한다")
    void consume_repeatedDeliveryWithoutRedis_keepsOneHistory() {
        String json = eventJson("evt-repeat");
        for (int index = 0; index < 3; index++) {
            assertThatCode(() -> consumer.consume(json)).doesNotThrowAnyException();
        }
        assertSingleEvent("evt-repeat");
    }

    @Test
    @DisplayName("동일 이벤트의 동시 재전달은 모두 종료되고 DB에는 한 건만 남는다")
    void consume_concurrentDelivery_keepsOneCommittedHistory() throws Exception {
        String json = eventJson("evt-concurrent");
        int workers = 8;
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(workers)) {
            var futures = new ArrayList<Future<?>>();
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    consumer.consume(json);
                    return null;
                }));
            }
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
        }
        assertSingleEvent("evt-concurrent");
    }

    private String eventJson(String id) {
        return mapper.writeValueAsString(new RecommendationServedEvent(id, 1L, "query", 10L, "product", "reason", 0.8));
    }

    private void assertSingleEvent(String id) {
        assertThat(repository.findAll()).singleElement().satisfies(history -> {
            assertThat(history.getEventId()).isEqualTo(id);
            assertThat(history.getMemberId()).isEqualTo(1L);
            assertThat(history.getProductId()).isEqualTo(10L);
            assertThat(history.getQuery()).isEqualTo("query");
            assertThat(history.getReason()).isEqualTo("reason");
        });
    }
}
