package com.agentcart.recommendation.dto;

public record RecommendationRequestContext(
        String originalQuery,
        Long memberId,
        PriceRange priceRange,
        CategoryConstraint categoryConstraint,
        InterpretationStatus status
) {
    public boolean requiresClarification() {
        return status == InterpretationStatus.CLARIFICATION_REQUIRED;
    }

    public RecommendationRequestContext mergeInferred(EnrichedQuery enriched) {
        PriceRange mergedPrice = priceRange;
        if (mergedPrice == null && validRange(enriched.minPrice(), enriched.maxPrice())) {
            mergedPrice = inferredPrice(enriched.minPrice(), enriched.maxPrice());
        }

        CategoryConstraint mergedCategory = categoryConstraint;
        if (mergedCategory == null && enriched.categories() != null && !enriched.categories().isEmpty()) {
            var normalized = CategoryTaxonomy.normalize(enriched.categories());
            if (!normalized.isEmpty()) {
                mergedCategory = new CategoryConstraint(normalized, null, ConditionSource.INFERRED);
            }
        }
        return new RecommendationRequestContext(originalQuery, memberId, mergedPrice, mergedCategory, status);
    }

    private static boolean validRange(Long minPrice, Long maxPrice) {
        if (minPrice == null && maxPrice == null) return false;
        if (minPrice != null && minPrice < 0) return false;
        if (maxPrice != null && maxPrice < 0) return false;
        return minPrice == null || maxPrice == null || minPrice <= maxPrice;
    }

    private static PriceRange inferredPrice(Long minPrice, Long maxPrice) {
        return new PriceRange(minPrice, maxPrice, null, ConditionSource.INFERRED);
    }
}
