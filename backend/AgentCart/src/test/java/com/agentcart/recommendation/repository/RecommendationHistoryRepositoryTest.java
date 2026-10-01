package com.agentcart.recommendation.repository;

import com.agentcart.recommendation.domain.RecommendationHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RecommendationHistoryRepositoryTest {
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired RecommendationHistoryRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearHistory() {
        repository.deleteAllInBatch();
    }

    @Test
    @DisplayName("동일 eventId는 MySQL unique 제약으로 거부되고 별도 조회가 가능하다")
    void save_duplicateEventId_rollsBackAndKeepsCommittedHistory() {
        repository.saveAndFlush(event("evt-1"));
        assertThatThrownBy(() -> repository.saveAndFlush(event("evt-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.existsByEventId("evt-1")).isTrue();
        assertThat(repository.count()).isEqualTo(1);
        repository.saveAndFlush(event("evt-2"));
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("eventId는 대소문자를 구분하고 직접 저장 이력의 NULL은 여러 건 허용한다")
    void save_distinctIdsAndLegacyHistory_preservesAllRows() {
        repository.saveAndFlush(event("evt-A"));
        repository.saveAndFlush(event("evt-a"));
        repository.saveAndFlush(RecommendationHistory.of(1L, "query", 10L, "product", "reason", 0.8));
        repository.saveAndFlush(RecommendationHistory.of(1L, "query", 10L, "product", "reason", 0.8));
        assertThat(repository.count()).isEqualTo(4);
        assertThat(repository.findAll()).filteredOn(h -> h.getEventId() == null).hasSize(2);
    }

    @Test
    @DisplayName("회원별 최근 20건 조회는 event 이력과 직접 저장 이력을 함께 최신순으로 반환한다")
    void findTop20_mixedHistory_returnsOnlyMembersLatestTwenty() {
        for (int index = 0; index < 25; index++) {
            var history = index % 2 == 0
                    ? event("evt-" + index)
                    : RecommendationHistory.of(1L, "query", 10L, "product", "reason", 0.8);
            repository.saveAndFlush(history);
            jdbc.update("UPDATE recommendation_history SET recommended_at = TIMESTAMPADD(SECOND, ?, '2026-01-01 00:00:00') WHERE id = ?",
                    index, history.getId());
        }
        repository.saveAndFlush(RecommendationHistory.of(2L, "other", 20L, "other", "reason", 0.8));

        var history = repository.findTop20ByMemberIdOrderByRecommendedAtDesc(1L);
        assertThat(history).hasSize(20).allMatch(h -> h.getMemberId().equals(1L));
        assertThat(history).extracting(RecommendationHistory::getRecommendedAt).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(history.getFirst().getEventId()).isEqualTo("evt-24");
        assertThat(history.getLast().getRecommendedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 1, 0, 0, 5));
    }

    private RecommendationHistory event(String id) {
        return RecommendationHistory.ofEvent(id, 1L, "query", 10L, "product", "reason", 0.8);
    }
}
