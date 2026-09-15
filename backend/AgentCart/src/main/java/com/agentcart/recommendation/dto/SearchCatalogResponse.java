package com.agentcart.recommendation.dto;

import java.util.List;

public record SearchCatalogResponse(
        SearchCatalogStatus status,
        List<SearchCatalogCandidate> candidates,
        SearchCatalogEmptyReason emptyReason,
        SearchCatalogErrorCode errorCode
) {
    public SearchCatalogResponse {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static SearchCatalogResponse success(List<SearchCatalogCandidate> candidates) {
        return new SearchCatalogResponse(SearchCatalogStatus.SUCCESS, candidates, null, null);
    }

    public static SearchCatalogResponse empty(SearchCatalogEmptyReason reason) {
        return new SearchCatalogResponse(SearchCatalogStatus.EMPTY, List.of(), reason, null);
    }

    public static SearchCatalogResponse error(SearchCatalogErrorCode code) {
        return new SearchCatalogResponse(SearchCatalogStatus.ERROR, List.of(), null, code);
    }
}
