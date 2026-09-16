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
        int promptTokens,
        int completionTokens,
        int totalTokens,
        List<String> chatModels,
        long elapsedMillis
) {
    public RecommendationAgentResult {
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        chatModels = chatModels == null ? List.of() : List.copyOf(chatModels);
    }
}
