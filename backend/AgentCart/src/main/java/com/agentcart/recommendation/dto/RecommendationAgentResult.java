package com.agentcart.recommendation.dto;

import java.util.List;

public record RecommendationAgentResult(
        String requestId,
        RecommendationAgentOutcome outcome,
        RecommendationAgentActionCode actionCode,
        String message,
        List<AgentRecommendation> recommendations,
        int searchCount,
        int llmCallCount,
        long elapsedMillis
) {
    public RecommendationAgentResult {
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }
}
