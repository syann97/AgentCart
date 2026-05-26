package com.agentcart.recommendation.dto;

import java.util.List;

public record EnrichedQuery(String enrichedQuery, String bm25Keywords, List<String> categories) {}