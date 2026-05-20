package com.agentcart.recommendation.unit;

import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.service.evaluator.ConsistencyValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsistencyValidatorTest {

    private final ConsistencyValidator validator = new ConsistencyValidator();

    @Test
    @DisplayName("rrfScore > 0.01 - 통과")
    void validate_scoreAboveThreshold_returnsTrue() {
        assertThat(validator.validate(candidate(0.02))).isTrue();
    }

    @Test
    @DisplayName("rrfScore = 0.01 (경계값) - 제거")
    void validate_scoreAtThreshold_returnsFalse() {
        assertThat(validator.validate(candidate(ConsistencyValidator.MIN_RRF_SCORE))).isFalse();
    }

    @Test
    @DisplayName("rrfScore < 0.01 - 제거")
    void validate_scoreBelowThreshold_returnsFalse() {
        assertThat(validator.validate(candidate(0.005))).isFalse();
    }

    @Test
    @DisplayName("rrfScore = 0 - 제거")
    void validate_scoreZero_returnsFalse() {
        assertThat(validator.validate(candidate(0.0))).isFalse();
    }

    private SearchCandidate candidate(double rrfScore) {
        return new SearchCandidate(1L, 1, 1, rrfScore);
    }
}