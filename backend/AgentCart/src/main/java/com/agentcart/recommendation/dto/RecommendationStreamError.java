package com.agentcart.recommendation.dto;

public record RecommendationStreamError(
        String requestId,
        RecommendationAgentActionCode code,
        String message,
        boolean retryable
) {}
