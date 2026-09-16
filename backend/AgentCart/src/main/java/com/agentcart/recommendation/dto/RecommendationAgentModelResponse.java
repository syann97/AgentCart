package com.agentcart.recommendation.dto;

import java.util.List;

public record RecommendationAgentModelResponse(
        RecommendationAgentOutcome outcome,
        String message,
        List<ModelRecommendation> recommendations
) {
    public RecommendationAgentModelResponse {
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }

    public record ModelRecommendation(Long productId, String reason, List<String> evidenceIds) {
        public ModelRecommendation {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }
}
