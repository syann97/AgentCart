package com.agentcart.recommendation.dto;

public record RecommendationStreamEvent<T>(String type, T data) {

    public static RecommendationStreamEvent<RecommendationStreamStatus> status(
            RecommendationStreamStatus data) {
        return new RecommendationStreamEvent<>("status", data);
    }

    public static RecommendationStreamEvent<RecommendationResult> result(RecommendationResult data) {
        return new RecommendationStreamEvent<>("result", data);
    }

    public static RecommendationStreamEvent<RecommendationStreamDone> done(RecommendationStreamDone data) {
        return new RecommendationStreamEvent<>("done", data);
    }

    public static RecommendationStreamEvent<RecommendationStreamError> error(RecommendationStreamError data) {
        return new RecommendationStreamEvent<>("error", data);
    }
}
