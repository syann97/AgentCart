package com.agentcart.recommendation.unit;

import com.agentcart.recommendation.dto.AgentRecommendation;
import com.agentcart.recommendation.dto.RecommendationAgentActionCode;
import com.agentcart.recommendation.dto.RecommendationAgentOutcome;
import com.agentcart.recommendation.dto.RecommendationAgentResult;
import com.agentcart.recommendation.dto.RecommendationStreamEvent;
import com.agentcart.recommendation.service.RecommendationAgentProgressListener;
import com.agentcart.recommendation.service.RecommendationAgentService;
import com.agentcart.recommendation.service.RecommendationCancellationToken;
import com.agentcart.recommendation.service.RecommendationEventProducer;
import com.agentcart.recommendation.service.RecommendationStreamService;
import com.agentcart.recommendation.service.RecommendationStreamSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationStreamServiceTest {

    @Mock RecommendationAgentService agentService;
    @Mock RecommendationEventProducer eventProducer;

    @Test
    void stream_success_sendsStatusResultsAndDoneInOrder() {
        RecommendationStreamService service = new RecommendationStreamService(agentService, eventProducer);
        given(agentService.recommend(eq("캠핑 의자"), eq(7L), any(), any()))
                .willAnswer(invocation -> {
                    RecommendationAgentProgressListener listener = invocation.getArgument(3);
                    listener.onSearchStarted(1, false);
                    return successResult(List.of(recommendation(1L), recommendation(2L)));
                });
        List<RecommendationStreamEvent<?>> events = new ArrayList<>();

        service.stream("캠핑 의자", 7L, new RecommendationCancellationToken(), events::add);

        assertThat(events).extracting(RecommendationStreamEvent::type)
                .containsExactly("status", "result", "result", "done");
        verify(eventProducer, times(2)).publish(any());
    }

    @Test
    void stream_noResults_sendsDoneWithoutResultOrHistory() {
        RecommendationStreamService service = new RecommendationStreamService(agentService, eventProducer);
        given(agentService.recommend(eq("없는 상품"), eq(7L), any(), any()))
                .willReturn(new RecommendationAgentResult("req-1", RecommendationAgentOutcome.NO_RESULTS,
                        RecommendationAgentActionCode.NO_RESULTS, "결과 없음", List.of(), 1, 2,
                        10, 5, 15, List.of("gpt-4o-mini"), 10));
        List<RecommendationStreamEvent<?>> events = new ArrayList<>();

        service.stream("없는 상품", 7L, new RecommendationCancellationToken(), events::add);

        assertThat(events).extracting(RecommendationStreamEvent::type).containsExactly("done");
        verify(eventProducer, never()).publish(any());
    }

    @Test
    void stream_failedAgent_sendsSingleErrorWithoutDone() {
        RecommendationStreamService service = new RecommendationStreamService(agentService, eventProducer);
        given(agentService.recommend(eq("오류"), eq(7L), any(), any()))
                .willReturn(new RecommendationAgentResult("req-1", RecommendationAgentOutcome.FAILED,
                        RecommendationAgentActionCode.PROCESSING_FAILED, "처리 실패", List.of(), 0, 1,
                        10, 5, 15, List.of("gpt-4o-mini"), 10));
        List<RecommendationStreamEvent<?>> events = new ArrayList<>();

        service.stream("오류", 7L, new RecommendationCancellationToken(), events::add);

        assertThat(events).extracting(RecommendationStreamEvent::type).containsExactly("error");
        verify(eventProducer, never()).publish(any());
    }

    @Test
    void stream_resultSendFails_cancelsAndDoesNotPublishOrSendTerminal() {
        RecommendationStreamService service = new RecommendationStreamService(agentService, eventProducer);
        given(agentService.recommend(eq("캠핑 의자"), eq(7L), any(), any()))
                .willReturn(successResult(List.of(recommendation(1L))));
        RecommendationCancellationToken token = new RecommendationCancellationToken();
        List<String> sentTypes = new ArrayList<>();
        RecommendationStreamSink sink = event -> {
            sentTypes.add(event.type());
            if (event.type().equals("result")) throw new IOException("disconnected");
        };

        service.stream("캠핑 의자", 7L, token, sink);

        assertThat(sentTypes).containsExactly("result");
        assertThat(token.isCancelled()).isTrue();
        verify(eventProducer, never()).publish(any());
    }

    @Test
    void stream_agentThrows_sendsSingleError() {
        RecommendationStreamService service = new RecommendationStreamService(agentService, eventProducer);
        given(agentService.recommend(eq("오류"), eq(7L), any(), any()))
                .willThrow(new IllegalStateException("boom"));
        List<RecommendationStreamEvent<?>> events = new ArrayList<>();

        service.stream("오류", 7L, new RecommendationCancellationToken(), events::add);

        assertThat(events).extracting(RecommendationStreamEvent::type).containsExactly("error");
    }

    private RecommendationAgentResult successResult(List<AgentRecommendation> recommendations) {
        return new RecommendationAgentResult("req-1", RecommendationAgentOutcome.SUCCESS,
                RecommendationAgentActionCode.COMPLETED, "완료", recommendations, 1, 2,
                10, 5, 15, List.of("gpt-4o-mini"), 10);
    }

    private AgentRecommendation recommendation(long id) {
        return new AgentRecommendation(id, "상품" + id, BigDecimal.valueOf(30_000),
                "캠핑·아웃도어", "브랜드", "추천 이유", List.of("product:" + id), 0.9);
    }
}
