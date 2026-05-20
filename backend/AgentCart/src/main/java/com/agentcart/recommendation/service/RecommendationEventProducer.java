package com.agentcart.recommendation.service;

import com.agentcart.recommendation.dto.RecommendationServedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class RecommendationEventProducer {

    public static final String TOPIC = "recommendation.served";

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper;

    public RecommendationEventProducer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void publish(RecommendationServedEvent event) {
        if (kafkaTemplate == null) return;
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.eventId(), json);
        } catch (Exception e) {
            log.warn("Failed to publish RecommendationServedEvent: eventId={} error={}", event.eventId(), e.getMessage());
        }
    }
}