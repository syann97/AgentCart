package com.agentcart.recommendation.dto;

public record RecommendationStreamDone(
        String requestId,
        RecommendationAgentOutcome outcome,
        RecommendationAgentActionCode actionCode,
        String message,
        int resultCount
) {}
