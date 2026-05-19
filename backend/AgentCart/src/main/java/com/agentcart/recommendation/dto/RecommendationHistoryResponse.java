package com.agentcart.recommendation.dto;

import com.agentcart.recommendation.domain.RecommendationHistory;

import java.time.LocalDateTime;

public record RecommendationHistoryResponse(
        Long productId,
        String productName,
        String reason,
        double score,
        LocalDateTime recommendedAt
) {
    public static RecommendationHistoryResponse from(RecommendationHistory h) {
        return new RecommendationHistoryResponse(
                h.getProductId(), h.getProductName(), h.getReason(), h.getScore(), h.getRecommendedAt()
        );
    }
}