package com.agentcart.recommendation.consumer;

import com.agentcart.recommendation.domain.RecommendationHistory;
import com.agentcart.recommendation.dto.RecommendationServedEvent;
import com.agentcart.recommendation.repository.RecommendationHistoryRepository;
import com.agentcart.recommendation.service.RecommendationEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@ConditionalOnBean(KafkaTemplate.class)
@RequiredArgsConstructor
public class RecommendationServedConsumer {

    private final RecommendationHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = RecommendationEventProducer.TOPIC,
                   groupId = "${spring.kafka.consumer.group-id:recommendation-consumer}")
    public void consume(String eventJson) {
        RecommendationServedEvent event;
        try {
            event = objectMapper.readValue(eventJson, RecommendationServedEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize RecommendationServedEvent: {}", e.getMessage());
            return;
        }

        // The repository proxy commits (or rolls back) before returning. Keep this
        // listener outside a transaction so the duplicate check sees committed data
        // after a failed insert, rather than a rollback-only persistence context.
        try {
            historyRepository.saveAndFlush(RecommendationHistory.ofEvent(
                    event.eventId(), event.memberId(), event.query(), event.productId(),
                    event.productName(), event.reason(), event.score()));
        } catch (DataIntegrityViolationException failure) {
            if (historyRepository.existsByEventId(event.eventId())) {
                log.debug("Duplicate event skipped: eventId={}", event.eventId());
                return;
            }
            throw failure;
        }

        log.debug("RecommendationHistory saved via Kafka: productId={} memberId={}", event.productId(), event.memberId());
    }
}
