package com.agentcart.recommendation.unit;

import com.agentcart.recommendation.consumer.RecommendationServedConsumer;
import com.agentcart.recommendation.domain.RecommendationHistory;
import com.agentcart.recommendation.dto.RecommendationServedEvent;
import com.agentcart.recommendation.repository.RecommendationHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RecommendationServedConsumerTest {
    @Mock RecommendationHistoryRepository historyRepository;
    @Mock ObjectMapper objectMapper;
    @InjectMocks RecommendationServedConsumer consumer;

    private static final RecommendationServedEvent EVENT =
            new RecommendationServedEvent("evt-1", 1L, "돌잔치", 10L, "상품A", "이유", 0.8);

    private void parsedEvent() {
        given(objectMapper.readValue(anyString(), eq(RecommendationServedEvent.class))).willReturn(EVENT);
    }

    @Test
    @DisplayName("신규 이벤트는 eventId와 실제 전송 데이터를 함께 저장한다")
    void consume_newEvent_savesHistory() {
        parsedEvent();
        consumer.consume("json");
        var captured = ArgumentCaptor.forClass(RecommendationHistory.class);
        then(historyRepository).should().saveAndFlush(captured.capture());
        var history = captured.getValue();
        assertThat(history.getEventId()).isEqualTo(EVENT.eventId());
        assertThat(history.getMemberId()).isEqualTo(EVENT.memberId());
        assertThat(history.getQuery()).isEqualTo(EVENT.query());
        assertThat(history.getProductId()).isEqualTo(EVENT.productId());
        assertThat(history.getProductName()).isEqualTo(EVENT.productName());
        assertThat(history.getReason()).isEqualTo(EVENT.reason());
        assertThat(history.getScore()).isEqualTo(EVENT.score());
    }

    @Test
    @DisplayName("DB 저장 실패는 전파되고 동일 이벤트 재전달은 다시 저장한다")
    void consume_databaseFailure_redeliverySaves() {
        parsedEvent();
        var failure = new DataAccessResourceFailureException("DB unavailable");
        given(historyRepository.saveAndFlush(any())).willThrow(failure).willAnswer(i -> i.getArgument(0));
        assertThatThrownBy(() -> consumer.consume("json")).isSameAs(failure);
        assertThatCode(() -> consumer.consume("json")).doesNotThrowAnyException();
        then(historyRepository).should(times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName("unique 충돌 뒤 같은 eventId의 커밋 이력이 있으면 중복 처리로 종료한다")
    void consume_duplicateEvent_completes() {
        parsedEvent();
        given(historyRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("duplicate"));
        given(historyRepository.existsByEventId(EVENT.eventId())).willReturn(true);
        assertThatCode(() -> consumer.consume("json")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 무결성 오류는 저장 성공으로 숨기지 않는다")
    void consume_otherIntegrityFailure_propagates() {
        parsedEvent();
        var failure = new DataIntegrityViolationException("invalid data");
        given(historyRepository.saveAndFlush(any())).willThrow(failure);
        given(historyRepository.existsByEventId(EVENT.eventId())).willReturn(false);
        assertThatThrownBy(() -> consumer.consume("json")).isSameAs(failure);
    }

    @Test
    @DisplayName("충돌 확인 조회 실패도 재처리를 위해 전파한다")
    void consume_duplicateCheckFailure_propagates() {
        parsedEvent();
        given(historyRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("duplicate"));
        var failure = new DataAccessResourceFailureException("read unavailable");
        given(historyRepository.existsByEventId(EVENT.eventId())).willThrow(failure);
        assertThatThrownBy(() -> consumer.consume("json")).isSameAs(failure);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("이벤트 ID 누락·공백은 nullable 과거 이력 경로로 저장하지 않는다")
    void consume_missingEventId_rejects(String eventId) {
        given(objectMapper.readValue(anyString(), eq(RecommendationServedEvent.class))).willReturn(
                new RecommendationServedEvent(eventId, 1L, "query", 10L, "product", "reason", 0.8));
        assertThatThrownBy(() -> consumer.consume("json")).isInstanceOf(IllegalArgumentException.class);
        then(historyRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("잘못된 JSON은 기존 계약대로 무시한다")
    void consume_invalidJson_skipsWithoutException() {
        given(objectMapper.readValue(anyString(), eq(RecommendationServedEvent.class)))
                .willThrow(new RuntimeException("parse error"));
        consumer.consume("invalid-json");
        then(historyRepository).shouldHaveNoInteractions();
    }
}
