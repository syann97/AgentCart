package com.agentcart.recommendation.unit;

import com.agentcart.recommendation.config.SearchCatalogToolConfiguration;
import com.agentcart.recommendation.dto.InterpretationStatus;
import com.agentcart.recommendation.dto.RecommendationRequestContext;
import com.agentcart.recommendation.dto.SearchCatalogExecutionContext;
import com.agentcart.recommendation.dto.SearchCatalogEmptyReason;
import com.agentcart.recommendation.dto.SearchCatalogRequest;
import com.agentcart.recommendation.dto.SearchCatalogResponse;
import com.agentcart.recommendation.service.SearchCatalogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SearchCatalogToolConfigurationTest {

    @Test
    @DisplayName("Tool schema - 모델 입력에 회원·명시 조건·실행 문맥을 노출하지 않음")
    void toolCallback_schemaOnlyContainsModelInputs() {
        SearchCatalogService service = mock(SearchCatalogService.class);
        ToolCallback callback = new SearchCatalogToolConfiguration().searchCatalogToolCallback(service);
        String schema = callback.getToolDefinition().inputSchema();

        assertThat(callback.getToolDefinition().name()).isEqualTo("searchCatalog");
        assertThat(schema).contains("keywordQuery", "semanticQuery", "inferredCategory");
        assertThat(schema).contains("\"required\": [\"keywordQuery\", \"semanticQuery\"]");
        assertThat(schema).doesNotContain("memberId", "priceRange", "requestId", "deadline", "searchAttempt");
    }

    @Test
    @DisplayName("Tool 호출 - 서버 실행 문맥을 JSON 입력과 분리해 서비스에 전달")
    void toolCallback_call_passesServerContextSeparately() {
        SearchCatalogService service = mock(SearchCatalogService.class);
        SearchCatalogResponse expected = SearchCatalogResponse.empty(SearchCatalogEmptyReason.NO_SEARCH_MATCHES);
        given(service.search(any(), any())).willReturn(expected);
        ToolCallback callback = new SearchCatalogToolConfiguration().searchCatalogToolCallback(service);
        RecommendationRequestContext requestContext = new RecommendationRequestContext(
                "노트 추천", 7L, null, null, InterpretationStatus.READY);
        SearchCatalogExecutionContext execution = new SearchCatalogExecutionContext(
                requestContext, "request-1", 1, Instant.now().plusSeconds(30));

        callback.call("{\"keywordQuery\":\"노트\",\"semanticQuery\":\"업무용 노트\"}",
                new ToolContext(Map.of(SearchCatalogToolConfiguration.EXECUTION_CONTEXT_KEY, execution)));

        verify(service).search(new SearchCatalogRequest("노트", "업무용 노트", null), execution);
    }
}
