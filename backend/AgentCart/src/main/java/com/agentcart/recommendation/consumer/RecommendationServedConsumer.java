package com.agentcart.recommendation.consumer;

import com.agentcart.recommendation.domain.RecommendationHistory;
import com.agentcart.recommendation.dto.RecommendationServedEvent;
import com.agentcart.recommendation.repository.RecommendationHistoryRepository;
import com.agentcart.recommendation.service.RecommendationEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnBean(KafkaTemplate.class)
@RequiredArgsConstructor
public class RecommendationServedConsumer {

    private static final String IDEMPOTENCY_PREFIX = "rec:event:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    private final RecommendationHistoryRepository historyRepository;
    private final StringRedisTemplate redisTemplate;
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

        String idempotencyKey = IDEMPOTENCY_PREFIX + event.eventId();
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_TTL_HOURS, TimeUnit.HOURS);

        if (!Boolean.TRUE.equals(isNew)) {
            log.debug("Duplicate event skipped: eventId={}", event.eventId());
            return;
        }

        historyRepository.save(RecommendationHistory.of(
                event.memberId(), event.query(), event.productId(),
                event.productName(), event.reason(), event.score()));

        log.debug("RecommendationHistory saved via Kafka: productId={} memberId={}", event.productId(), event.memberId());
    }
}