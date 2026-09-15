package com.agentcart.recommendation.dto;

import java.math.BigDecimal;

public record SearchCatalogCandidate(
        String evidenceId,
        Long productId,
        String productName,
        String description,
        BigDecimal price,
        String category,
        String brand,
        int stock,
        int bm25Rank,
        int vectorRank,
        double vectorSimilarity,
        double rrfScore
) {}
