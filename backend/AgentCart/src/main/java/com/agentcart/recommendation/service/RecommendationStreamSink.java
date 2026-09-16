package com.agentcart.recommendation.service;

import com.agentcart.recommendation.dto.RecommendationStreamEvent;

import java.io.IOException;

@FunctionalInterface
public interface RecommendationStreamSink {

    void send(RecommendationStreamEvent<?> event) throws IOException;
}
