package com.agentcart.recommendation.dto;

public record SearchCandidate(Long productId, int bm25Rank, int vectorRank, double rrfScore) {}