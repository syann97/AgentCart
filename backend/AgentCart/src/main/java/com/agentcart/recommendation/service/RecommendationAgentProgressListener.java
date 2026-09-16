package com.agentcart.recommendation.service;

@FunctionalInterface
public interface RecommendationAgentProgressListener {

    RecommendationAgentProgressListener NO_OP = (attempt, fallback) -> {};

    void onSearchStarted(int attempt, boolean fallback);
}
