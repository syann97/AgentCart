package com.agentcart.recommendation.service;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RecommendationCancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
