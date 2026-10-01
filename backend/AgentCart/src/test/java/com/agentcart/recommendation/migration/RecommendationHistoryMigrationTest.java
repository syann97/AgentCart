package com.agentcart.recommendation.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RecommendationHistoryMigrationTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Test
    @DisplayName("V9에서 V10으로 올려도 기존 이력·인덱스를 보존하고 nullable eventId unique 제약을 추가한다")
    void migrate_existingHistory_preservesDataAndAddsEventUniqueness() {
        flyway("9").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
        jdbc.update("""
                INSERT INTO recommendation_history
                (member_id, query, product_id, product_name, reason, score, recommended_at)
                VALUES (1, '기존 질의', 10, '기존 상품', '기존 이유', 0.8, '2026-01-01 12:00:00')
                """);
        var before = jdbc.queryForMap("SELECT * FROM recommendation_history");

        var result = flyway("10").migrate();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        var after = jdbc.queryForMap("SELECT * FROM recommendation_history");
        assertThat(after).containsAllEntriesOf(before).containsEntry("event_id", null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'recommendation_history' AND index_name = 'idx_member_at'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT non_unique FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'recommendation_history' AND index_name = 'uk_recommendation_history_event_id'", Integer.class)).isZero();

        jdbc.update("INSERT INTO recommendation_history (member_id, product_id, product_name, score, recommended_at) VALUES (1, 11, '직접 저장', 0.7, NOW())");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_history WHERE event_id IS NULL", Integer.class)).isEqualTo(2);
        String insert = "INSERT INTO recommendation_history (event_id, member_id, product_id, product_name, score, recommended_at) VALUES (?, 1, 12, '이벤트 상품', 0.9, NOW())";
        jdbc.update(insert, "evt-1");
        assertThatThrownBy(() -> jdbc.update(insert, "evt-1")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_history", Integer.class)).isEqualTo(3);
        assertThat(flyway("10").migrate().migrationsExecuted).isZero();
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration").target(target).load();
    }
}
