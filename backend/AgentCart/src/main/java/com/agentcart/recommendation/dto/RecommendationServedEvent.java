package com.agentcart.recommendation.dto;

public record RecommendationServedEvent(
        String eventId,
        Long memberId,
        String query,
        Long productId,
        String productName,
        String reason,
        double score
) {}