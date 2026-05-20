package com.agentcart.recommendation.unit;

import com.agentcart.recommendation.consumer.RecommendationServedConsumer;
import com.agentcart.recommendation.domain.RecommendationHistory;
import com.agentcart.recommendation.dto.RecommendationServedEvent;
import com.agentcart.recommendation.repository.RecommendationHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RecommendationServedConsumerTest {

    @Mock RecommendationHistoryRepository historyRepository;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ObjectMapper objectMapper;
    @Mock ValueOperations<String, String> valueOps;
    @InjectMocks RecommendationServedConsumer consumer;

    private static final RecommendationServedEvent EVENT =
            new RecommendationServedEvent("evt-1", 1L, "돌잔치", 10L, "상품A", "이유", 0.8);

    @Test
    @DisplayName("신규 eventId - setIfAbsent 성공 → 히스토리 저장")
    void consume_newEvent_savesHistory() throws Exception {
        given(objectMapper.readValue(anyString(), eq(RecommendationServedEvent.class))).willReturn(EVENT);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent("rec:event:evt-1", "1", 24L, TimeUnit.HOURS)).willReturn(true);

        consumer.consume("{\"eventId\":\"evt-1\"}");

        then(historyRepository).should().save(any(RecommendationHistory.class));
    }

    @Test
    @DisplayName("중복 eventId - setIfAbsent 실패 → 저장 생략 (멱등성)")
    void consume_duplicateEvent_skipsHistory() throws Exception {
        given(objectMapper.readValue(anyString(), eq(RecommendationServedEvent.class))).willReturn(EVENT);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent("rec:event:evt-1", "1", 24L, TimeUnit.HOURS)).willReturn(false);

        consumer.consume("{\"eventId\":\"evt-1\"}");

        then(historyRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("잘못된 JSON - 로그 후 무시, 저장 없음")
    void consume_invalidJson_skipsWithoutException() throws Exception {
        given(objectMapper.readValue(anyString(), eq(RecommendationServedEvent.class)))
                .willThrow(new RuntimeException("parse error"));

        consumer.consume("invalid-json");

        then(historyRepository).should(never()).save(any());
    }
}