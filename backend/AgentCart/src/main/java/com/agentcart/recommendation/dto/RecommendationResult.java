package com.agentcart.recommendation.dto;

import java.util.List;

public record RecommendationResult(
        Long productId,
        String productName,
        String reason,
        List<String> conditions,
        double score
) {}