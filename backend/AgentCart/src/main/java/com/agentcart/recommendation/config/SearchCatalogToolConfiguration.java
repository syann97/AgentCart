package com.agentcart.recommendation.config;

import com.agentcart.recommendation.dto.SearchCatalogErrorCode;
import com.agentcart.recommendation.dto.SearchCatalogExecutionContext;
import com.agentcart.recommendation.dto.SearchCatalogRequest;
import com.agentcart.recommendation.dto.SearchCatalogResponse;
import com.agentcart.recommendation.service.SearchCatalogService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SearchCatalogToolConfiguration {

    public static final String EXECUTION_CONTEXT_KEY = "searchCatalogExecutionContext";
    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "keywordQuery": {
                  "type": "string",
                  "description": "Keywords for the catalog full-text search"
                },
                "semanticQuery": {
                  "type": "string",
                  "description": "Natural-language query for semantic search"
                },
                "inferredCategory": {
                  "type": "string",
                  "description": "Optional category inferred from the user intent"
                }
              },
              "required": ["keywordQuery", "semanticQuery"],
              "additionalProperties": false
            }
            """;

    @Bean("searchCatalogToolCallback")
    public ToolCallback searchCatalogToolCallback(SearchCatalogService searchCatalogService) {
        return FunctionToolCallback.<SearchCatalogRequest, SearchCatalogResponse>builder(
                        "searchCatalog",
                        (request, toolContext) -> invoke(searchCatalogService, request, toolContext))
                .description("Search the AgentCart product catalog using keyword and semantic queries")
                .inputType(SearchCatalogRequest.class)
                .inputSchema(INPUT_SCHEMA)
                .build();
    }

    private SearchCatalogResponse invoke(SearchCatalogService service, SearchCatalogRequest request,
                                         ToolContext toolContext) {
        if (toolContext == null) {
            return SearchCatalogResponse.error(SearchCatalogErrorCode.INVALID_EXECUTION_CONTEXT);
        }
        Object execution = toolContext.getContext().get(EXECUTION_CONTEXT_KEY);
        if (!(execution instanceof SearchCatalogExecutionContext searchContext)) {
            return SearchCatalogResponse.error(SearchCatalogErrorCode.INVALID_EXECUTION_CONTEXT);
        }
        return service.search(request, searchContext);
    }
}
