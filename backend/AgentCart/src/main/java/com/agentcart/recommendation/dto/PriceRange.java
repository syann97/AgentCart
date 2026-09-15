package com.agentcart.recommendation.dto;

public record PriceRange(Long minPrice, Long maxPrice, String evidence, ConditionSource source) {

    public PriceRange {
        if (minPrice == null && maxPrice == null) {
            throw new IllegalArgumentException("At least one price boundary is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("Price condition source is required");
        }
    }
}
