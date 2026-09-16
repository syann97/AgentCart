package com.agentcart.recommendation.unit;

import com.agentcart.recommendation.config.SearchCatalogToolConfiguration;
import com.agentcart.recommendation.dto.RecommendationAgentActionCode;
import com.agentcart.recommendation.dto.RecommendationAgentOutcome;
import com.agentcart.recommendation.dto.RecommendationAgentResult;
import com.agentcart.recommendation.dto.SearchCatalogCandidate;
import com.agentcart.recommendation.dto.SearchCatalogEmptyReason;
import com.agentcart.recommendation.dto.SearchCatalogResponse;
import com.agentcart.recommendation.service.RecommendationAgentService;
import com.agentcart.recommendation.service.RecommendationAgentProgressListener;
import com.agentcart.recommendation.service.RecommendationCancellationToken;
import com.agentcart.recommendation.service.RecommendationRequestContextFactory;
import com.agentcart.recommendation.service.SearchCatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RecommendationAgentServiceTest {

    private static final String QUERY = "5만원 이하 캠핑 의자 추천";
    private static final Long MEMBER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatModel chatModel = mock(ChatModel.class);
    private final SearchCatalogService catalogService = mock(SearchCatalogService.class);
    private final ToolCallback toolCallback = new SearchCatalogToolConfiguration()
            .searchCatalogToolCallback(catalogService);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private RecommendationAgentService service = service(Duration.ofSeconds(30));

    @AfterEach
    void tearDown() {
        service.shutdownExecutor();
        executor.shutdownNow();
    }

    @Test
    @DisplayName("첫 검색 결과 충분 - LLM 2회와 검색 1회로 서버 상품 사실을 반환")
    void recommend_firstSearchSufficient_finishesWithinTwoLlmCalls() {
        given(catalogService.search(any(), any())).willReturn(success(candidate(1L, "캠핑 의자")));
        given(chatModel.call(any(Prompt.class))).willReturn(
                toolCall("call-1", "캠핑 의자", "가족 캠핑 의자"),
                text(successJson(1L, "접이식이라 이동하기 좋습니다", "product:1")));

        RecommendationAgentResult result = service.recommend(QUERY, MEMBER_ID);

        assertThat(result.outcome()).isEqualTo(RecommendationAgentOutcome.SUCCESS);
        assertThat(result.searchCount()).isEqualTo(1);
        assertThat(result.llmCallCount()).isEqualTo(2);
        assertThat(result.recommendations()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("캠핑 의자");
            assertThat(item.price()).isEqualByComparingTo("30000");
            assertThat(item.evidenceIds()).containsExactly("product:1");
        });
    }

    @Test
    @DisplayName("관련성 부족 후 검색어 변경 - 검색 2회와 LLM 3회로 종료")
    void recommend_changedSearch_runsOneResearch() {
        RecommendationAgentProgressListener progressListener = mock(RecommendationAgentProgressListener.class);
        given(catalogService.search(any(), any())).willReturn(
                success(candidate(1L, "일반 의자")), success(candidate(2L, "캠핑 의자")));
        given(chatModel.call(any(Prompt.class))).willReturn(
                toolCall("call-1", "의자", "야외 의자"),
                toolCall("call-2", "캠핑 접이식 의자", "가족 캠핑용 접이식 의자"),
                text(successJson(2L, "가족 캠핑에 적합합니다", "product:2")));

        RecommendationAgentResult result = service.recommend(
                QUERY, MEMBER_ID, new RecommendationCancellationToken(), progressListener);

        assertThat(result.outcome()).isEqualTo(RecommendationAgentOutcome.SUCCESS);
        assertThat(result.searchCount()).isEqualTo(2);
        assertThat(result.llmCallCount()).isEqualTo(3);
        verify(progressListener).onSearchStarted(1, false);
        verify(progressListener).onSearchStarted(2, false);
        verify(catalogService, times(2)).search(any(), any());
    }

    @Test
    @DisplayName("같은 정규화 검색 인자 반복 - 두 번째 검색을 실행하지 않고 종료")
    void recommend_repeatedNormalizedSearch_blocksSecondExecution() {
        given(catalogService.search(any(), any())).willReturn(success(candidate(1L, "캠핑 의자")));
        given(chatModel.call(any(Prompt.class))).willReturn(
                toolCall("call-1", "캠핑   의자", "가족 캠핑 의자"),
                toolCall("call-2", " 캠핑 의자 ", "가족  캠핑 의자"));

        RecommendationAgentResult result = service.recommend(QUERY, MEMBER_ID);

        assertThat(result.actionCode()).isEqualTo(RecommendationAgentActionCode.DUPLICATE_SEARCH_BLOCKED);
        assertThat(result.searchCount()).isEqualTo(1);
        assertThat(result.llmCallCount()).isEqualTo(2);
        verify(catalogService).search(any(), any());
    }

    @Test
    @DisplayName("마지막 LLM 호출 - 도구를 노출하지 않고 도구 호출 응답도 실행하지 않음")
    void recommend_lastCall_hasNoToolAndCannotStartThirdSearch() {
        given(catalogService.search(any(), any())).willReturn(
                success(candidate(1L, "일반 의자")), success(candidate(2L, "캠핑 의자")));
        given(chatModel.call(any(Prompt.class))).willReturn(
                toolCall("call-1", "의자", "야외 의자"),
                toolCall("call-2", "캠핑 의자", "가족 캠핑 의자"),
                toolCall("call-3", "캠핑 체어", "접이식 체어"));

        RecommendationAgentResult result = service.recommend(QUERY, MEMBER_ID);

        assertThat(result.actionCode()).isEqualTo(RecommendationAgentActionCode.SEARCH_LIMIT_REACHED);
        assertThat(result.llmCallCount()).isEqualTo(3);
        assertThat(result.searchCount()).isEqualTo(2);
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(3)).call(prompts.capture());
        List<Prompt> values = prompts.getAllValues();
        assertThat(options(values.get(0)).getToolCallbacks()).hasSize(1);
        assertThat(options(values.get(1)).getToolCallbacks()).hasSize(1);
        assertThat(options(values.get(2)).getToolCallbacks()).isEmpty();
        verify(catalogService, times(2)).search(any(), any());
    }

    @Test
    @DisplayName("첫 검색 0건 - 정상 결과 없음으로 종료")
    void recommend_firstSearchEmpty_returnsNoResults() {
        given(catalogService.search(any(), any()))
                .willReturn(SearchCatalogResponse.empty(SearchCatalogEmptyReason.NO_SEARCH_MATCHES));
        given(chatModel.call(any(Prompt.class))).willReturn(
                toolCall("call-1", "없는 상품", "없는 상품"), text(noResultsJson()));

        RecommendationAgentResult result = service.recommend("없는 상품", MEMBER_ID);

        assertThat(result.outcome()).isEqualTo(RecommendationAgentOutcome.NO_RESULTS);
        assertThat(result.searchCount()).isEqualTo(1);
        assertThat(result.llmCallCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("재검색도 0건 - LLM과 검색 상한 안에서 결과 없음으로 종료")
    void recommend_researchEmpty_returnsNoResultsAtLimits() {
        given(catalogService.search(any(), any()))
                .willReturn(SearchCatalogResponse.empty(SearchCatalogEmptyReason.NO_SEARCH_MATCHES));
        given(chatModel.call(any(Prompt.class))).willReturn(
                toolCall("call-1", "없는 상품", "첫 표현"),
                toolCall("call-2", "다른 상품", "다른 표현"),
                text(noResultsJson()));

        RecommendationAgentResult result = service.recommend("없는 상품", MEMBER_ID);

        assertThat(result.outcome()).isEqualTo(RecommendationAgentOutcome.NO_RESULTS);
        assertThat(result.searchCount()).isEqualTo(2);
        assertThat(result.llmCallCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("후보 밖 ID·근거 ID·중복 ID - 유효한 후보만 최종 결과에 포함")
    void recommend_invalidCandidateEvidenceAndDuplicate_filtersInvalidEntries() {
        given(catalogService.search(any(), any())).willReturn(success(candidate(1L, "캠핑 의자")));
        given(chatModel.call(any(Prompt.class))).willReturn(
                toolCall("call-1", "캠핑 의자", "캠핑 의자"),
                text("""
                        {"outcome":"SUCCESS","message":"ok","recommendations":[
                          {"productId":999,"reason":"조작","evidenceIds":["product:999"]},
                          {"productId":1,"reason":"잘못된 근거","evidenceIds":["product:999"]},
                          {"productId":1,"reason":"검증된 이유","evidenceIds":["product:1"]},
                          {"productId":1,"reason":"중복","evidenceIds":["product:1"]}
                        ]}
                        """));

        RecommendationAgentResult result = service.recommend(QUERY, MEMBER_ID);

        assertThat(result.outcome()).isEqualTo(RecommendationAgentOutcome.SUCCESS);
        assertThat(result.recommendations()).singleElement()
                .satisfies(item -> assertThat(item.reason()).isEqualTo("검증된 이유"));
    }

    @Test
    @DisplayName("최종 추천 5개 초과 - 구조화 응답을 거부하고 일반 검색 fallback")
    void recommend_moreThanFiveRecommendations_rejectsResponseAndFallsBack() {
        List<SearchCatalogCandidate> candidates = new ArrayList<>();
        for (long id = 1; id <= 6; id++) candidates.add(candidate(id, "상품" + id));
        given(catalogService.search(any(), any())).willReturn(SearchCatalogResponse.success(candidates));
        given(chatModel.call(any(Prompt.class))).willReturn(
                toolCall("call-1", "상품", "추천 상품"), text(sixRecommendationsJson()));

        RecommendationAgentResult result = service.recommend("상품 추천", MEMBER_ID);

        assertThat(result.outcome()).isEqualTo(RecommendationAgentOutcome.FALLBACK);
        assertThat(result.recommendations()).hasSize(5);
        assertThat(result.searchCount()).isLessThanOrEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "{}"})
    @DisplayName("malformed·빈 모델 응답 - 추가 LLM 재시도 없이 일반 검색 fallback")
    void recommend_malformedModelResponse_usesGeneralSearchFallback(String response) {
        given(catalogService.search(any(), any())).willReturn(success(candidate(1L, "캠핑 의자")));
        given(chatModel.call(any(Prompt.class))).willReturn(text(response));

        RecommendationAgentResult result = service.recommend(QUERY, MEMBER_ID);

        assertThat(result.outcome()).isEqualTo(RecommendationAgentOutcome.FALLBACK);
        assertThat(result.searchCount()).isEqualTo(1);
        assertThat(result.llmCallCount()).isEqualTo(1);
        assertThat(result.recommendations()).hasSize(1);
        ArgumentCaptor<com.agentcart.recommendation.dto.SearchCatalogExecutionContext> execution =
                ArgumentCaptor.forClass(com.agentcart.recommendation.dto.SearchCatalogExecutionContext.class);
        verify(catalogService).search(any(), execution.capture());
        assertThat(execution.getValue().requestContext().priceRange().maxPrice()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("충돌 가격 조건 - LLM과 검색 없이 입력 구체화 반환")
    void recommend_conflictingPrice_returnsClarificationWithoutCalls() {
        RecommendationAgentResult result = service.recommend(
                "5만원 이상 3만원 이하 캠핑용품", MEMBER_ID);

        assertThat(result.outcome()).isEqualTo(RecommendationAgentOutcome.CLARIFICATION_REQUIRED);
        assertThat(result.searchCount()).isZero();
        assertThat(result.llmCallCount()).isZero();
        verify(chatModel, never()).call(any(Prompt.class));
        verify(catalogService, never()).search(any(), any());
    }

    @Test
    @DisplayName("카탈로그 지원 범위 밖 - 검색 없이 명시 outcome으로 종료")
    void recommend_outOfScope_returnsTypedOutcome() {
        given(chatModel.call(any(Prompt.class))).willReturn(text("""
                {"outcome":"OUT_OF_SCOPE","message":"지원하지 않는 상품군입니다.","recommendations":[]}
                """));

        RecommendationAgentResult result = service.recommend("자동차 세차용품", MEMBER_ID);

        assertThat(result.outcome()).isEqualTo(RecommendationAgentOutcome.OUT_OF_SCOPE);
        assertThat(result.searchCount()).isZero();
        assertThat(result.llmCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("취소 상태 - 이후 LLM과 검색을 시작하지 않음")
    void recommend_cancelledBeforeStart_doesNotCallExternalBoundaries() {
        RecommendationCancellationToken token = new RecommendationCancellationToken();
        token.cancel();

        RecommendationAgentResult result = service.recommend(QUERY, MEMBER_ID, token);

        assertThat(result.actionCode()).isEqualTo(RecommendationAgentActionCode.CANCELLED);
        assertThat(result.searchCount()).isZero();
        assertThat(result.llmCallCount()).isZero();
        verify(chatModel, never()).call(any(Prompt.class));
        verify(catalogService, never()).search(any(), any());
    }

    @Test
    @DisplayName("LLM 응답 뒤 취소 - 후속 검색을 시작하지 않음")
    void recommend_cancelledAfterModelResponse_doesNotStartSearch() {
        RecommendationCancellationToken token = new RecommendationCancellationToken();
        given(chatModel.call(any(Prompt.class))).willAnswer(invocation -> {
            token.cancel();
            return toolCall("call-1", "캠핑 의자", "가족 캠핑 의자");
        });

        RecommendationAgentResult result = service.recommend(QUERY, MEMBER_ID, token);

        assertThat(result.actionCode()).isEqualTo(RecommendationAgentActionCode.CANCELLED);
        assertThat(result.llmCallCount()).isEqualTo(1);
        assertThat(result.searchCount()).isZero();
        verify(catalogService, never()).search(any(), any());
    }

    @Test
    @DisplayName("전체 deadline 만료 - 실행 중 LLM을 취소하고 후속 검색을 시작하지 않음")
    void recommend_deadlineExpires_cancelsCallAndStops() {
        service.shutdownExecutor();
        executor.shutdownNow();
        ExecutorService shortExecutor = Executors.newVirtualThreadPerTaskExecutor();
        service = new RecommendationAgentService(new RecommendationRequestContextFactory(), toolCallback,
                objectMapper, chatModel, Clock.systemUTC(), Duration.ofMillis(20), shortExecutor);
        given(chatModel.call(any(Prompt.class))).willAnswer(invocation -> {
            Thread.sleep(200);
            return toolCall("call-1", "캠핑", "캠핑");
        });

        RecommendationAgentResult result = service.recommend(QUERY, MEMBER_ID);

        assertThat(result.actionCode()).isEqualTo(RecommendationAgentActionCode.DEADLINE_EXCEEDED);
        assertThat(result.llmCallCount()).isEqualTo(1);
        assertThat(result.searchCount()).isZero();
        verify(catalogService, never()).search(any(), any());
    }

    private RecommendationAgentService service(Duration deadline) {
        return new RecommendationAgentService(new RecommendationRequestContextFactory(), toolCallback,
                objectMapper, chatModel, Clock.systemUTC(), deadline, executor);
    }

    private ToolCallingChatOptions options(Prompt prompt) {
        return (ToolCallingChatOptions) prompt.getOptions();
    }

    private ChatResponse toolCall(String id, String keyword, String semantic) {
        String args = "{\"keywordQuery\":\"" + keyword + "\",\"semanticQuery\":\"" + semantic + "\"}";
        AssistantMessage message = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", "searchCatalog", args)))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private ChatResponse text(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private SearchCatalogResponse success(SearchCatalogCandidate... candidates) {
        return SearchCatalogResponse.success(List.of(candidates));
    }

    private SearchCatalogCandidate candidate(long id, String name) {
        return new SearchCatalogCandidate("product:" + id, id, name, "서버 설명",
                BigDecimal.valueOf(30_000), "캠핑·아웃도어", "브랜드", 10,
                1, 1, 0.9, 1.0 - id / 100.0);
    }

    private String successJson(long id, String reason, String evidenceId) {
        return "{\"outcome\":\"SUCCESS\",\"message\":\"추천\",\"recommendations\":["
                + "{\"productId\":" + id + ",\"reason\":\"" + reason
                + "\",\"evidenceIds\":[\"" + evidenceId + "\"]}]}";
    }

    private String noResultsJson() {
        return "{\"outcome\":\"NO_RESULTS\",\"message\":\"근거 없음\",\"recommendations\":[]}";
    }

    private String sixRecommendationsJson() {
        StringBuilder json = new StringBuilder("{\"outcome\":\"SUCCESS\",\"recommendations\":[");
        for (int id = 1; id <= 6; id++) {
            if (id > 1) json.append(',');
            json.append("{\"productId\":").append(id)
                    .append(",\"reason\":\"이유\",\"evidenceIds\":[\"product:")
                    .append(id).append("\"]}");
        }
        return json.append("]}").toString();
    }
}
