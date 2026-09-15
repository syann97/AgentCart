package com.agentcart.recommendation.dto;

import java.time.Instant;

public record SearchCatalogExecutionContext(
        RecommendationRequestContext requestContext,
        String requestId,
        int searchAttempt,
        Instant deadline
) {
    public boolean isValid() {
        return requestContext != null && requestId != null && !requestId.isBlank()
                && requestContext.originalQuery() != null && !requestContext.originalQuery().isBlank()
                && requestContext.memberId() != null && requestContext.status() != null
                && searchAttempt >= 1 && searchAttempt <= 2 && deadline != null;
    }
}
