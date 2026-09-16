package com.agentcart.recommendation.dto;

public record RecommendationStreamStatus(
        RecommendationStreamStatusPhase phase,
        int searchAttempt,
        String message
) {}
