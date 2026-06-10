package com.agentcart.recommendation.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecommendationResult(
        Long productId,
        String productName,
        BigDecimal price,
        String reason,
        List<String> conditions,
        double score
) {}