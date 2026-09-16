package com.agentcart.recommendation.dto;

import java.math.BigDecimal;
import java.util.List;

public record AgentRecommendation(
        Long productId,
        String productName,
        BigDecimal price,
        String category,
        String brand,
        String reason,
        List<String> evidenceIds,
        double score
) {
    public AgentRecommendation {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
