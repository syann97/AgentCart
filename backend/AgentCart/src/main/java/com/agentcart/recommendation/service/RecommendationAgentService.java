package com.agentcart.recommendation.service;

import com.agentcart.recommendation.config.RecommendationChatOptionsFactory;
import com.agentcart.recommendation.config.SearchCatalogToolConfiguration;
import com.agentcart.recommendation.dto.AgentRecommendation;
import com.agentcart.recommendation.dto.CategoryConstraint;
import com.agentcart.recommendation.dto.PriceRange;
import com.agentcart.recommendation.dto.RecommendationAgentActionCode;
import com.agentcart.recommendation.dto.RecommendationAgentModelResponse;
import com.agentcart.recommendation.dto.RecommendationAgentOutcome;
import com.agentcart.recommendation.dto.RecommendationAgentResult;
import com.agentcart.recommendation.dto.RecommendationRequestContext;
import com.agentcart.recommendation.dto.SearchCatalogCandidate;
import com.agentcart.recommendation.dto.SearchCatalogExecutionContext;
import com.agentcart.recommendation.dto.SearchCatalogRequest;
import com.agentcart.recommendation.dto.SearchCatalogResponse;
import com.agentcart.recommendation.dto.SearchCatalogStatus;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class RecommendationAgentService {

    static final int MAX_LLM_CALLS = 3;
    static final int MAX_SEARCHES = 2;
    static final int MAX_RECOMMENDATIONS = 5;
    static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(30);

    private static final String SYSTEM_PROMPT = """
            당신은 AgentCart의 단일 상품 추천 에이전트입니다.
            사용 가능한 도구는 searchCatalog 하나뿐입니다. 검색은 최대 두 번이며 두 번째 검색은 첫 결과가 관련성이 부족할 때만 서로 다른 인자로 요청하세요.
            후보 수가 5개보다 적다는 이유만으로 재검색하지 마세요. 카탈로그 근거가 없으면 결과 없음 또는 지원 범위 밖으로 종료하세요.
            최종 후보를 고르기 전에 사용자 원문의 대상, 용도, 필수 성능을 각 후보의 상품명과 설명에 대조하세요. 카테고리나 검색 점수만으로 관련성을 판단하지 마세요.
            방수·방풍, 무게·휴대성, 크기·규격, 동물 종·대상 호환성, 성능처럼 확인 가능한 속성은 후보 상품명이나 설명에 직접 근거가 있을 때만 사용하세요. 일반 상식으로 속성이나 호환성을 만들어내지 마세요.
            핵심 요구를 뒷받침하는 근거가 없는 후보는 제외하세요. 근거 있는 후보가 적으면 적은 수로 끝내고, 모두 부족하면 NO_RESULTS 또는 필요한 경우 CLARIFICATION_REQUIRED로 종료하세요.
            추천 이유에는 후보 상품명이나 설명이 뒷받침하는 사실만 쓰세요. 카테고리가 달라도 원문의 용도와 필수 속성이 설명에 직접 나타나는 유효한 대안은 허용하세요.
            최종 답변은 JSON만 출력하세요. 상품명, 가격, 카테고리, 브랜드는 만들지 말고 후보 productId와 추천 이유, 후보의 evidenceId만 사용하세요.
            형식: {"outcome":"SUCCESS|NO_RESULTS|OUT_OF_SCOPE|CLARIFICATION_REQUIRED","message":"안내 문구","recommendations":[{"productId":1,"reason":"추천 이유","evidenceIds":["product:1"]}]}
            SUCCESS 결과는 최대 5개입니다. NO_RESULTS, OUT_OF_SCOPE, CLARIFICATION_REQUIRED의 recommendations는 빈 배열이어야 합니다.
            """;

    private final RecommendationRequestContextFactory requestContextFactory;
    private final ToolCallback searchCatalogToolCallback;
    private final ObjectMapper objectMapper;
    private final ChatModel chatModel;
    private final RecommendationChatOptionsFactory chatOptionsFactory;
    private final Clock clock;
    private final Duration deadlineDuration;
    private final ExecutorService executor;

    @Autowired
    public RecommendationAgentService(
            RecommendationRequestContextFactory requestContextFactory,
            @Qualifier("searchCatalogToolCallback") ToolCallback searchCatalogToolCallback,
            ObjectMapper objectMapper,
            ObjectProvider<ChatModel> chatModelProvider,
            RecommendationChatOptionsFactory chatOptionsFactory) {
        this(requestContextFactory, searchCatalogToolCallback, objectMapper, chatModelProvider.getIfAvailable(),
                Clock.systemUTC(), DEFAULT_DEADLINE, Executors.newVirtualThreadPerTaskExecutor(), chatOptionsFactory);
    }

    public RecommendationAgentService(
            RecommendationRequestContextFactory requestContextFactory,
            ToolCallback searchCatalogToolCallback,
            ObjectMapper objectMapper,
            ChatModel chatModel,
            Clock clock,
            Duration deadlineDuration,
            ExecutorService executor) {
        this(requestContextFactory, searchCatalogToolCallback, objectMapper, chatModel, clock,
                deadlineDuration, executor, new RecommendationChatOptionsFactory("openai"));
    }

    public RecommendationAgentService(
            RecommendationRequestContextFactory requestContextFactory,
            ToolCallback searchCatalogToolCallback,
            ObjectMapper objectMapper,
            ChatModel chatModel,
            Clock clock,
            Duration deadlineDuration,
            ExecutorService executor,
            RecommendationChatOptionsFactory chatOptionsFactory) {
        this.requestContextFactory = requestContextFactory;
        this.searchCatalogToolCallback = searchCatalogToolCallback;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.chatOptionsFactory = chatOptionsFactory;
        this.clock = clock;
        this.deadlineDuration = deadlineDuration;
        this.executor = executor;
    }

    public RecommendationAgentResult recommend(String query, Long memberId) {
        return recommend(query, memberId, new RecommendationCancellationToken());
    }

    public RecommendationAgentResult recommend(
            String query, Long memberId, RecommendationCancellationToken cancellationToken) {
        return recommend(query, memberId, cancellationToken, RecommendationAgentProgressListener.NO_OP);
    }

    public RecommendationAgentResult recommend(
            String query, Long memberId, RecommendationCancellationToken cancellationToken,
            RecommendationAgentProgressListener progressListener) {
        Instant startedAt = clock.instant();
        String requestId = UUID.randomUUID().toString();
        RecommendationRequestContext requestContext = requestContextFactory.create(query, memberId);
        ExecutionState state = new ExecutionState(
                requestId, requestContext, startedAt, startedAt.plus(deadlineDuration), cancellationToken);

        if (requestContext.requiresClarification()) {
            return finish(state, RecommendationAgentOutcome.CLARIFICATION_REQUIRED,
                    RecommendationAgentActionCode.CLARIFICATION_REQUIRED,
                    "가격 범위가 서로 충돌합니다. 조건을 확인해 주세요.", List.of());
        }
        if (chatModel == null) {
            return fallback(state, new LinkedHashMap<>(), progressListener);
        }

        List<Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage(SYSTEM_PROMPT));
        conversation.add(new UserMessage(buildRequestPrompt(requestContext)));
        Map<Long, SearchCatalogCandidate> candidates = new LinkedHashMap<>();

        try {
            for (int turn = 1; turn <= MAX_LLM_CALLS; turn++) {
                checkCanStart(state, true);
                boolean toolsAvailable = turn < MAX_LLM_CALLS && state.searchCount < MAX_SEARCHES;
                ChatResponse response = callModel(state, conversation, toolsAvailable);
                AssistantMessage assistant = response == null || response.getResult() == null
                        ? null : response.getResult().getOutput();
                if (assistant == null) {
                    return fallback(state, candidates, progressListener);
                }

                if (assistant.hasToolCalls()) {
                    if (!toolsAvailable) {
                        return finish(state, RecommendationAgentOutcome.FAILED,
                                RecommendationAgentActionCode.SEARCH_LIMIT_REACHED,
                                "마지막 모델 호출에서는 추가 검색을 실행할 수 없습니다.", List.of());
                    }
                    if (assistant.getToolCalls().size() != 1
                            || !"searchCatalog".equals(assistant.getToolCalls().getFirst().name())) {
                        return fallback(state, candidates, progressListener);
                    }
                    AssistantMessage.ToolCall toolCall = assistant.getToolCalls().getFirst();
                    SearchCatalogRequest request = parseToolRequest(toolCall.arguments());
                    String fingerprint = fingerprint(request);
                    if (!state.searchFingerprints.add(fingerprint)) {
                        return finish(state, RecommendationAgentOutcome.NO_RESULTS,
                                RecommendationAgentActionCode.DUPLICATE_SEARCH_BLOCKED,
                                "같은 검색 조건이 반복되어 실행을 종료했습니다.", List.of());
                    }
                    SearchCatalogResponse toolResponse = executeSearch(state, request, progressListener, false);
                    toolResponse.candidates().forEach(candidate -> candidates.put(candidate.productId(), candidate));
                    conversation.add(assistant);
                    conversation.add(toolResponseMessage(toolCall, toolResponse));
                    continue;
                }

                RecommendationAgentResult terminal = parseAndValidateTerminal(state, assistant.getText(), candidates);
                if (terminal != null) {
                    return terminal;
                }
                return fallback(state, candidates, progressListener);
            }
            return finish(state, RecommendationAgentOutcome.FAILED,
                    RecommendationAgentActionCode.LLM_LIMIT_REACHED,
                    "모델 호출 상한에 도달했습니다.", List.of());
        } catch (CancelledException e) {
            return finish(state, RecommendationAgentOutcome.FAILED,
                    RecommendationAgentActionCode.CANCELLED, "요청이 취소되었습니다.", List.of());
        } catch (DeadlineExceededException e) {
            return finish(state, RecommendationAgentOutcome.FAILED,
                    RecommendationAgentActionCode.DEADLINE_EXCEEDED, "추천 처리 시간이 초과되었습니다.", List.of());
        } catch (InvalidModelResponseException e) {
            return fallback(state, candidates, progressListener);
        } catch (RuntimeException e) {
            log.warn("recommendation agent failed: requestId={} error={}", requestId, e.getMessage());
            return finish(state, RecommendationAgentOutcome.FAILED,
                    RecommendationAgentActionCode.PROCESSING_FAILED, "추천 처리에 실패했습니다.", List.of());
        }
    }

    private ChatResponse callModel(ExecutionState state, List<Message> conversation, boolean toolsAvailable) {
        if (state.llmCallCount >= MAX_LLM_CALLS) {
            throw new InvalidModelResponseException();
        }
        checkCanStart(state, true);
        List<ToolCallback> toolCallbacks;
        Map<String, Object> toolContext;
        if (toolsAvailable) {
            SearchCatalogExecutionContext executionContext = new SearchCatalogExecutionContext(
                    state.requestContext, state.requestId, state.searchCount + 1, state.deadline);
            toolCallbacks = List.of(searchCatalogToolCallback);
            toolContext = Map.of(SearchCatalogToolConfiguration.EXECUTION_CONTEXT_KEY, executionContext);
        } else {
            toolCallbacks = List.of();
            toolContext = Map.of();
        }
        state.llmCallCount++;
        ChatResponse response = withinDeadline(
                state, () -> chatModel.call(new Prompt(List.copyOf(conversation),
                        chatOptionsFactory.create(0.0, toolCallbacks, toolContext))));
        recordUsage(state, response);
        return response;
    }

    private void recordUsage(ExecutionState state, ChatResponse response) {
        if (response == null || response.getMetadata() == null) return;
        if (response.getMetadata().getModel() != null && !response.getMetadata().getModel().isBlank()) {
            state.chatModels.add(response.getMetadata().getModel());
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) return;
        state.promptTokens += valueOrZero(usage.getPromptTokens());
        state.completionTokens += valueOrZero(usage.getCompletionTokens());
        state.totalTokens += valueOrZero(usage.getTotalTokens());
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private SearchCatalogResponse executeSearch(
            ExecutionState state, SearchCatalogRequest request,
            RecommendationAgentProgressListener progressListener, boolean fallback) {
        if (state.searchCount >= MAX_SEARCHES) {
            throw new InvalidModelResponseException();
        }
        checkCanStart(state, false);
        progressListener.onSearchStarted(state.searchCount + 1, fallback);
        checkCanStart(state, false);
        state.searchCount++;
        SearchCatalogExecutionContext executionContext = new SearchCatalogExecutionContext(
                state.requestContext, state.requestId, state.searchCount, state.deadline);
        String json = withinDeadline(state, () -> searchCatalogToolCallback.call(
                objectMapper.writeValueAsString(request),
                new ToolContext(Map.of(SearchCatalogToolConfiguration.EXECUTION_CONTEXT_KEY, executionContext))));
        try {
            return objectMapper.readValue(json, SearchCatalogResponse.class);
        } catch (Exception e) {
            throw new InvalidModelResponseException();
        }
    }

    private RecommendationAgentResult fallback(
            ExecutionState state, Map<Long, SearchCatalogCandidate> existingCandidates,
            RecommendationAgentProgressListener progressListener) {
        try {
            checkCanStart(state, false);
            SearchCatalogRequest request = new SearchCatalogRequest(
                    state.requestContext.originalQuery(), state.requestContext.originalQuery(), null);
            String fingerprint = fingerprint(request);
            Map<Long, SearchCatalogCandidate> fallbackCandidates = new LinkedHashMap<>(existingCandidates);
            if (!state.searchFingerprints.contains(fingerprint) && state.searchCount < MAX_SEARCHES) {
                state.searchFingerprints.add(fingerprint);
                SearchCatalogResponse response = executeSearch(
                        state, request, progressListener, true);
                response.candidates().forEach(candidate -> fallbackCandidates.put(candidate.productId(), candidate));
            }
            List<AgentRecommendation> results = fallbackCandidates.values().stream()
                    .limit(MAX_RECOMMENDATIONS)
                    .map(candidate -> fromCandidate(candidate,
                            "검색 조건과 서버 정책을 통과한 상품입니다.", List.of(candidate.evidenceId())))
                    .toList();
            return finish(state, RecommendationAgentOutcome.FALLBACK,
                    RecommendationAgentActionCode.FALLBACK_SEARCH,
                    results.isEmpty() ? "일반 검색에서도 근거 있는 상품을 찾지 못했습니다." : "일반 검색 결과입니다.",
                    results);
        } catch (CancelledException e) {
            return finish(state, RecommendationAgentOutcome.FAILED,
                    RecommendationAgentActionCode.CANCELLED, "요청이 취소되었습니다.", List.of());
        } catch (DeadlineExceededException e) {
            return finish(state, RecommendationAgentOutcome.FAILED,
                    RecommendationAgentActionCode.DEADLINE_EXCEEDED, "추천 처리 시간이 초과되었습니다.", List.of());
        } catch (RuntimeException e) {
            return finish(state, RecommendationAgentOutcome.FAILED,
                    RecommendationAgentActionCode.PROCESSING_FAILED, "추천 처리에 실패했습니다.", List.of());
        }
    }

    private RecommendationAgentResult parseAndValidateTerminal(
            ExecutionState state, String content, Map<Long, SearchCatalogCandidate> candidates) {
        RecommendationAgentModelResponse response = parseModelResponse(content);
        if (response == null || response.outcome() == null) return null;
        if (response.outcome() == RecommendationAgentOutcome.SUCCESS) {
            if (response.recommendations().isEmpty()
                    || response.recommendations().size() > MAX_RECOMMENDATIONS) return null;
            Set<Long> seen = new LinkedHashSet<>();
            List<AgentRecommendation> validated = new ArrayList<>();
            for (RecommendationAgentModelResponse.ModelRecommendation recommendation : response.recommendations()) {
                SearchCatalogCandidate candidate = candidates.get(recommendation.productId());
                if (candidate == null || seen.contains(recommendation.productId())
                        || recommendation.reason() == null || recommendation.reason().isBlank()
                        || recommendation.evidenceIds().isEmpty()
                        || recommendation.evidenceIds().stream().anyMatch(id -> !candidate.evidenceId().equals(id))) {
                    continue;
                }
                seen.add(recommendation.productId());
                validated.add(fromCandidate(candidate, recommendation.reason(), recommendation.evidenceIds()));
            }
            if (validated.isEmpty()) return null;
            return finish(state, RecommendationAgentOutcome.SUCCESS,
                    RecommendationAgentActionCode.COMPLETED, response.message(), validated);
        }
        if (!response.recommendations().isEmpty()) return null;
        return switch (response.outcome()) {
            case NO_RESULTS -> finish(state, response.outcome(), RecommendationAgentActionCode.NO_RESULTS,
                    response.message(), List.of());
            case OUT_OF_SCOPE -> finish(state, response.outcome(), RecommendationAgentActionCode.OUT_OF_SCOPE,
                    response.message(), List.of());
            case CLARIFICATION_REQUIRED -> finish(state, response.outcome(),
                    RecommendationAgentActionCode.CLARIFICATION_REQUIRED, response.message(), List.of());
            default -> null;
        };
    }

    private RecommendationAgentModelResponse parseModelResponse(String content) {
        if (content == null || content.isBlank()) return null;
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < start) return null;
        try {
            return objectMapper.readValue(content.substring(start, end + 1), RecommendationAgentModelResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    private SearchCatalogRequest parseToolRequest(String arguments) {
        try {
            SearchCatalogRequest request = objectMapper.readValue(arguments, SearchCatalogRequest.class);
            if (request.keywordQuery() == null || request.keywordQuery().isBlank()
                    || request.semanticQuery() == null || request.semanticQuery().isBlank()) {
                throw new InvalidModelResponseException();
            }
            return request;
        } catch (InvalidModelResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidModelResponseException();
        }
    }

    private ToolResponseMessage toolResponseMessage(
            AssistantMessage.ToolCall toolCall, SearchCatalogResponse response) {
        try {
            return ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            toolCall.id(), toolCall.name(), objectMapper.writeValueAsString(response))))
                    .build();
        } catch (Exception e) {
            throw new InvalidModelResponseException();
        }
    }

    private AgentRecommendation fromCandidate(
            SearchCatalogCandidate candidate, String reason, List<String> evidenceIds) {
        return new AgentRecommendation(candidate.productId(), candidate.productName(), candidate.price(),
                candidate.category(), candidate.brand(), reason, evidenceIds, candidate.rrfScore());
    }

    private String buildRequestPrompt(RecommendationRequestContext context) {
        PriceRange price = context.priceRange();
        CategoryConstraint category = context.categoryConstraint();
        return "사용자 원문: " + context.originalQuery()
                + "\n서버가 확정한 가격 조건: " + (price == null ? "없음"
                : "최소=" + price.minPrice() + ", 최대=" + price.maxPrice() + ", 근거=" + price.evidence())
                + "\n서버가 확정한 카테고리 조건: " + (category == null ? "없음"
                : String.join(",", category.categories()) + ", 근거=" + category.evidence());
    }

    private String fingerprint(SearchCatalogRequest request) {
        return normalize(request.keywordQuery()) + "|" + normalize(request.semanticQuery())
                + "|" + normalize(request.inferredCategory());
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private void checkCanStart(ExecutionState state, boolean llm) {
        if (state.cancellationToken != null && state.cancellationToken.isCancelled()) {
            throw new CancelledException();
        }
        if (!clock.instant().isBefore(state.deadline)) {
            throw new DeadlineExceededException();
        }
        if (llm && state.llmCallCount >= MAX_LLM_CALLS) {
            throw new InvalidModelResponseException();
        }
    }

    private <T> T withinDeadline(ExecutionState state, Callable<T> task) {
        checkCanStart(state, false);
        long remainingMillis = Duration.between(clock.instant(), state.deadline).toMillis();
        if (remainingMillis <= 0) throw new DeadlineExceededException();
        Future<T> future = executor.submit(task);
        try {
            return future.get(remainingMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new DeadlineExceededException();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CancelledException();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException(cause);
        }
    }

    private RecommendationAgentResult finish(
            ExecutionState state, RecommendationAgentOutcome outcome,
            RecommendationAgentActionCode actionCode, String message,
            List<AgentRecommendation> recommendations) {
        long elapsedMillis = Math.max(0, Duration.between(state.startedAt, clock.instant()).toMillis());
        log.info("recommendation agent completed: requestId={} action={} searches={} llmCalls={} results={} elapsedMs={}",
                state.requestId, actionCode, state.searchCount, state.llmCallCount,
                recommendations.size(), elapsedMillis);
        return new RecommendationAgentResult(state.requestId, outcome, actionCode, message,
                recommendations, state.searchCount, state.llmCallCount,
                state.promptTokens, state.completionTokens, state.totalTokens,
                List.copyOf(state.chatModels), elapsedMillis);
    }

    @PreDestroy
    public void shutdownExecutor() {
        executor.shutdownNow();
    }

    private static final class ExecutionState {
        private final String requestId;
        private final RecommendationRequestContext requestContext;
        private final Instant startedAt;
        private final Instant deadline;
        private final RecommendationCancellationToken cancellationToken;
        private final Set<String> searchFingerprints = new LinkedHashSet<>();
        private final Set<String> chatModels = new LinkedHashSet<>();
        private int searchCount;
        private int llmCallCount;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;

        private ExecutionState(String requestId, RecommendationRequestContext requestContext,
                               Instant startedAt, Instant deadline,
                               RecommendationCancellationToken cancellationToken) {
            this.requestId = requestId;
            this.requestContext = requestContext;
            this.startedAt = startedAt;
            this.deadline = deadline;
            this.cancellationToken = cancellationToken;
        }
    }

    private static final class InvalidModelResponseException extends RuntimeException {}
    private static final class DeadlineExceededException extends RuntimeException {}
    private static final class CancelledException extends RuntimeException {}
}
