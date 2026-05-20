package com.agentcart.recommendation.dto;

import com.agentcart.product.domain.Product;

public record ValidatedCandidate(SearchCandidate candidate, Product product) {}