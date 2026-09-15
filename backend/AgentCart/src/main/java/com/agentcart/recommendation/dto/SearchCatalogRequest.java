package com.agentcart.recommendation.dto;

public record SearchCatalogRequest(
        String keywordQuery,
        String semanticQuery,
        String inferredCategory
) {}
