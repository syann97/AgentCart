package com.agentcart.recommendation.service;

public final class RecommendationStreamConnection {

    private final RecommendationCancellationToken cancellationToken = new RecommendationCancellationToken();

    public RecommendationCancellationToken cancellationToken() {
        return cancellationToken;
    }

    public void onCompletion() {
        cancellationToken.cancel();
    }

    public void onTimeout() {
        cancellationToken.cancel();
    }

    public void onError(Throwable error) {
        cancellationToken.cancel();
    }

    public void cancel() {
        cancellationToken.cancel();
    }
}
