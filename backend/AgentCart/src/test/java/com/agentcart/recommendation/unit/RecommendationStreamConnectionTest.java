package com.agentcart.recommendation.unit;

import com.agentcart.recommendation.service.RecommendationStreamConnection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationStreamConnectionTest {

    @Test
    void onCompletion_marksAgentTokenCancelled() {
        RecommendationStreamConnection connection = new RecommendationStreamConnection();

        connection.onCompletion();

        assertThat(connection.cancellationToken().isCancelled()).isTrue();
    }

    @Test
    void onTimeout_marksAgentTokenCancelled() {
        RecommendationStreamConnection connection = new RecommendationStreamConnection();

        connection.onTimeout();

        assertThat(connection.cancellationToken().isCancelled()).isTrue();
    }

    @Test
    void onError_marksAgentTokenCancelled() {
        RecommendationStreamConnection connection = new RecommendationStreamConnection();

        connection.onError(new IllegalStateException("disconnected"));

        assertThat(connection.cancellationToken().isCancelled()).isTrue();
    }
}
