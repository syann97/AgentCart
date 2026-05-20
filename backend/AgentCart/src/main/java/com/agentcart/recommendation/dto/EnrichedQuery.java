package com.agentcart.recommendation.dto;

import java.util.List;

public record EnrichedQuery(String enrichedQuery, List<String> categories) {}