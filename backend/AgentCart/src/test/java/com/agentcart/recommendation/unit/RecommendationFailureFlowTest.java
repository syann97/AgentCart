package com.agentcart.recommendation.unit;

import com.agentcart.recommendation.config.SearchCatalogToolConfiguration;
import com.agentcart.recommendation.dto.ConditionSource;
import com.agentcart.recommendation.dto.RecommendationAgentActionCode;
import com.agentcart.recommendation.dto.RecommendationAgentOutcome;
import com.agentcart.recommendation.dto.RecommendationAgentResult;
import com.agentcart.recommendation.dto.RecommendationStreamDone;
import com.agentcart.recommendation.dto.RecommendationStreamError;
import com.agentcart.recommendation.dto.RecommendationStreamEvent;
import com.agentcart.recommendation.dto.SearchCatalogCandidate;
import com.agentcart.recommendation.dto.SearchCatalogEmptyReason;
import com.agentcart.recommendation.dto.SearchCatalogErrorCode;
import com.agentcart.recommendation.dto.SearchCatalogExecutionContext;
import com.agentcart.recommendation.dto.SearchCatalogRequest;
import com.agentcart.recommendation.dto.SearchCatalogResponse;
import com.agentcart.recommendation.service.RecommendationAgentService;
import com.agentcart.recommendation.service.RecommendationCancellationToken;
import com.agentcart.recommendation.service.RecommendationEventProducer;
import com.agentcart.recommendation.service.RecommendationRequestContextFactory;
import com.agentcart.recommendation.service.RecommendationStreamService;
import com.agentcart.recommendation.service.SearchCatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RecommendationFailureFlowTest {
    private static final String QUERY = "5만원 이하 캠핑용품 추천";
    private final ChatModel model = mock(ChatModel.class);
    private final SearchCatalogService catalog = mock(SearchCatalogService.class);
    private final RecommendationEventProducer producer = mock(RecommendationEventProducer.class);
    private final RecommendationCancellationToken cancellation = new RecommendationCancellationToken();
    private final MutableClock clock = new MutableClock();
    private RecommendationAgentService agent = agent(model);

    private RecommendationAgentService agent(ChatModel chatModel) {
        return new RecommendationAgentService(new RecommendationRequestContextFactory(),
                new SearchCatalogToolConfiguration().searchCatalogToolCallback(catalog),
                new ObjectMapper(), chatModel, clock, Duration.ofSeconds(30),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    @AfterEach
    void tearDown() {
        agent.shutdownExecutor();
    }

    @ParameterizedTest
    @CsvSource({"EMBEDDING_FAILED,1", "SEARCH_REPOSITORY_FAILURE,1",
            "EMBEDDING_FAILED,2", "SEARCH_REPOSITORY_FAILURE,2"})
    void stream_searchError_stopsBeforeModelCanReturnNoResults(SearchCatalogErrorCode code, int attempt) {
        given(model.call(any(Prompt.class))).willReturn(search("first"), search("second"), text(noResults()));
        if (attempt == 1) {
            given(catalog.search(any(), any())).willReturn(SearchCatalogResponse.error(code));
        } else {
            given(catalog.search(any(), any())).willReturn(success(), SearchCatalogResponse.error(code));
        }

        assertError(stream(), RecommendationAgentActionCode.valueOf(code.name()), true, attempt);
        verify(model, times(attempt)).call(any(Prompt.class));
        verify(catalog, times(attempt)).search(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"absent,EMBEDDING_FAILED", "absent,SEARCH_REPOSITORY_FAILURE",
            "malformed,EMBEDDING_FAILED", "malformed,SEARCH_REPOSITORY_FAILURE",
            "exception,EMBEDDING_FAILED", "exception,SEARCH_REPOSITORY_FAILURE"})
    void stream_fallbackSearchError_isNotEmptyFallback(String trigger, SearchCatalogErrorCode code) {
        if (trigger.equals("absent")) {
            agent.shutdownExecutor();
            agent = agent(null);
        } else if (trigger.equals("malformed")) {
            given(model.call(any(Prompt.class))).willReturn(text("not-json"));
        } else {
            given(model.call(any(Prompt.class))).willThrow(new IllegalStateException("provider unavailable"));
        }
        given(catalog.search(any(), any())).willReturn(SearchCatalogResponse.error(code));

        assertError(stream(), RecommendationAgentActionCode.valueOf(code.name()), true, 1);
        verify(model, times(trigger.equals("absent") ? 0 : 1)).call(any(Prompt.class));
        verify(catalog).search(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"0,false", "0,true", "1,false", "1,true", "2,false", "2,true"})
    void recommend_modelException_fallsBackWithoutRetryAndPreservesPolicy(int searches, boolean empty) {
        var stubbing = given(model.call(any(Prompt.class)));
        for (int i = 0; i < searches; i++) stubbing = stubbing.willReturn(search("search-" + i));
        stubbing.willThrow(new IllegalStateException("provider unavailable"));
        given(catalog.search(any(), any())).willReturn(empty
                ? SearchCatalogResponse.empty(SearchCatalogEmptyReason.NO_SEARCH_MATCHES) : success());

        RecommendationAgentResult result = agent.recommend(QUERY, 7L, cancellation);

        assertThat(result.outcome()).isEqualTo(RecommendationAgentOutcome.FALLBACK);
        assertThat(result.actionCode()).isEqualTo(RecommendationAgentActionCode.FALLBACK_SEARCH);
        assertThat(result.recommendations()).hasSize(empty ? 0 : 1);
        assertThat(result.searchCount()).isEqualTo(Math.min(searches + 1, 2));
        assertThat(result.llmCallCount()).isEqualTo(searches + 1);
        var requests = ArgumentCaptor.forClass(SearchCatalogRequest.class);
        var executions = ArgumentCaptor.forClass(SearchCatalogExecutionContext.class);
        verify(catalog, times(result.searchCount())).search(requests.capture(), executions.capture());
        for (var execution : executions.getAllValues()) {
            assertThat(execution.requestContext().memberId()).isEqualTo(7L);
            assertThat(execution.requestContext().originalQuery()).isEqualTo(QUERY);
            assertThat(execution.requestContext().priceRange().maxPrice()).isEqualTo(50_000L);
            assertThat(execution.requestContext().categoryConstraint().source()).isEqualTo(ConditionSource.EXPLICIT);
            assertThat(execution.requestContext().categoryConstraint().categories()).contains("캠핑·아웃도어");
        }
        if (searches < 2) {
            assertThat(requests.getValue()).isEqualTo(new SearchCatalogRequest(QUERY, QUERY, null));
        }
        verify(model, times(searches + 1)).call(any(Prompt.class));
    }

    @Test
    void recommend_failedModelAfterOriginalSearch_reusesValidatedCandidatesWithoutDuplicateSearch() {
        given(model.call(any(Prompt.class))).willReturn(search(QUERY))
                .willThrow(new IllegalStateException("provider unavailable"));
        given(catalog.search(any(), any())).willReturn(success());

        RecommendationAgentResult result = agent.recommend(QUERY, 7L);

        assertThat(result.outcome()).isEqualTo(RecommendationAgentOutcome.FALLBACK);
        assertThat(result.recommendations()).hasSize(1);
        assertThat(result.searchCount()).isEqualTo(1);
        verify(catalog).search(any(), any());
        verify(model, times(2)).call(any(Prompt.class));
    }

    @ParameterizedTest
    @EnumSource(value = SearchCatalogErrorCode.class, names = {"EMBEDDING_FAILED", "SEARCH_REPOSITORY_FAILURE"})
    void stream_fallbackErrorAfterSuccessfulSearch_discardsEarlierCandidates(SearchCatalogErrorCode code) {
        given(model.call(any(Prompt.class))).willReturn(search("first"), text("not-json"));
        given(catalog.search(any(), any())).willReturn(success(), SearchCatalogResponse.error(code));

        assertError(stream(), RecommendationAgentActionCode.valueOf(code.name()), true, 2);
        verify(model, times(2)).call(any(Prompt.class));
        verify(catalog, times(2)).search(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stream_modelFailureAndHealthySearch_sendsFallbackDoneAndOnlyServedHistory(boolean empty) {
        given(model.call(any(Prompt.class))).willThrow(new IllegalStateException("provider unavailable"));
        given(catalog.search(any(), any())).willReturn(empty
                ? SearchCatalogResponse.empty(SearchCatalogEmptyReason.NO_SEARCH_MATCHES) : success());

        List<RecommendationStreamEvent<?>> events = stream();

        assertThat(events).extracting(RecommendationStreamEvent::type).containsExactlyElementsOf(empty
                ? List.of("status", "done") : List.of("status", "result", "done"));
        RecommendationStreamDone done = (RecommendationStreamDone) events.getLast().data();
        assertThat(done.outcome()).isEqualTo(RecommendationAgentOutcome.FALLBACK);
        assertThat(done.actionCode()).isEqualTo(RecommendationAgentActionCode.FALLBACK_SEARCH);
        assertThat(done.resultCount()).isEqualTo(empty ? 0 : 1);
        verify(producer, times(empty ? 0 : 1)).publish(any());
        verify(model).call(any(Prompt.class));
        verify(catalog).search(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = SearchCatalogErrorCode.class,
            names = {"INVALID_TOOL_INPUT", "INVALID_EXECUTION_CONTEXT", "CLARIFICATION_REQUIRED", "DEADLINE_EXCEEDED"})
    void stream_otherToolErrors_failWithoutAdditionalCalls(SearchCatalogErrorCode code) {
        given(model.call(any(Prompt.class))).willReturn(search("first"), text(noResults()));
        given(catalog.search(any(), any())).willReturn(SearchCatalogResponse.error(code));

        RecommendationAgentActionCode expected = code == SearchCatalogErrorCode.DEADLINE_EXCEEDED
                ? RecommendationAgentActionCode.DEADLINE_EXCEEDED : RecommendationAgentActionCode.PROCESSING_FAILED;
        assertError(stream(), expected, true, 1);
        verify(model).call(any(Prompt.class));
        verify(catalog).search(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "null", "{}",
            "{\"status\":\"SUCCESS\",\"errorCode\":\"EMBEDDING_FAILED\"}"})
    void stream_invalidToolResponse_failsWithoutFallbackSearch(String response) {
        ToolCallback callback = mock(ToolCallback.class);
        agent.shutdownExecutor();
        agent = new RecommendationAgentService(new RecommendationRequestContextFactory(), callback,
                new ObjectMapper(), model, clock, Duration.ofSeconds(30),
                Executors.newVirtualThreadPerTaskExecutor());
        given(model.call(any(Prompt.class))).willReturn(search("first"), text(noResults()));
        given(callback.call(any(String.class), any())).willReturn(response);

        assertError(stream(), RecommendationAgentActionCode.PROCESSING_FAILED, true, 1);
        verify(model).call(any(Prompt.class));
        verify(callback).call(any(String.class), any());
        verifyNoInteractions(catalog);
    }

    @ParameterizedTest
    @CsvSource({"model,cancel", "model,deadline", "search,cancel", "search,deadline"})
    void recommend_stoppedDuringBoundary_doesNotFallbackOrStartAnotherCall(String boundary, String stop) {
        if (boundary.equals("model")) {
            given(model.call(any(Prompt.class))).willAnswer(invocation -> {
                stop(stop);
                throw new IllegalStateException("provider unavailable");
            });
        } else {
            given(model.call(any(Prompt.class))).willReturn(search("first"), text(noResults()));
            given(catalog.search(any(), any())).willAnswer(invocation -> {
                stop(stop);
                return SearchCatalogResponse.error(SearchCatalogErrorCode.SEARCH_REPOSITORY_FAILURE);
            });
        }

        RecommendationAgentResult result = agent.recommend(QUERY, 7L, cancellation);

        assertThat(result.outcome()).isEqualTo(RecommendationAgentOutcome.FAILED);
        assertThat(result.actionCode()).isEqualTo(stop.equals("cancel")
                ? RecommendationAgentActionCode.CANCELLED : RecommendationAgentActionCode.DEADLINE_EXCEEDED);
        assertThat(result.recommendations()).isEmpty();
        verify(model).call(any(Prompt.class));
        verify(catalog, times(boundary.equals("model") ? 0 : 1)).search(any(), any());
    }

    private void stop(String stop) {
        if (stop.equals("cancel")) cancellation.cancel();
        else clock.now = clock.now.plusSeconds(31);
    }

    private List<RecommendationStreamEvent<?>> stream() {
        List<RecommendationStreamEvent<?>> events = new ArrayList<>();
        new RecommendationStreamService(agent, producer).stream(QUERY, 7L, cancellation, events::add);
        return events;
    }

    private void assertError(List<RecommendationStreamEvent<?>> events, RecommendationAgentActionCode code,
                             boolean retryable, int statuses) {
        assertThat(events).hasSize(statuses + 1);
        assertThat(events.subList(0, statuses)).extracting(RecommendationStreamEvent::type)
                .containsOnly("status");
        assertThat(events.getLast().type()).isEqualTo("error");
        RecommendationStreamError error = (RecommendationStreamError) events.getLast().data();
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.retryable()).isEqualTo(retryable);
        assertThat(error.requestId()).isNotBlank();
        verifyNoInteractions(producer);
    }

    private SearchCatalogResponse success() {
        return SearchCatalogResponse.success(List.of(new SearchCatalogCandidate("product:1", 1L,
                "캠핑 의자", "접이식 의자", BigDecimal.valueOf(30_000), "캠핑·아웃도어", "브랜드",
                10, 1, 1, 0.9, 1.0)));
    }

    private ChatResponse search(String query) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + query, "function", "searchCatalog",
                        "{\"keywordQuery\":\"" + query + "\",\"semanticQuery\":\"" + query + "\"}")))
                .build())));
    }

    private ChatResponse text(String value) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(value))));
    }

    private String noResults() {
        return "{\"outcome\":\"NO_RESULTS\",\"recommendations\":[]}";
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now = Instant.now();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
