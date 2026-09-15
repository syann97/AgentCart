package com.agentcart.recommendation.service;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.CategoryConstraint;
import com.agentcart.recommendation.dto.CategoryTaxonomy;
import com.agentcart.recommendation.dto.ConditionSource;
import com.agentcart.recommendation.dto.InterpretationStatus;
import com.agentcart.recommendation.dto.PriceRange;
import com.agentcart.recommendation.dto.RecommendationRequestContext;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.dto.SearchCatalogCandidate;
import com.agentcart.recommendation.dto.SearchCatalogEmptyReason;
import com.agentcart.recommendation.dto.SearchCatalogErrorCode;
import com.agentcart.recommendation.dto.SearchCatalogExecutionContext;
import com.agentcart.recommendation.dto.SearchCatalogRequest;
import com.agentcart.recommendation.dto.SearchCatalogResponse;
import com.agentcart.recommendation.dto.ValidatedCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchCatalogService {

    private static final int RESULT_LIMIT = 10;

    private final ProductRepository productRepository;
    private final HybridSearchService hybridSearchService;
    private final EvaluatorChain evaluatorChain;

    @Autowired(required = false)
    @Qualifier("ollamaEmbeddingModel")
    private EmbeddingModel embeddingModel;

    public SearchCatalogResponse search(SearchCatalogRequest request, SearchCatalogExecutionContext execution) {
        if (request == null || request.keywordQuery() == null || request.keywordQuery().isBlank()) {
            return SearchCatalogResponse.error(SearchCatalogErrorCode.INVALID_TOOL_INPUT);
        }
        if (execution == null || !execution.isValid()) {
            return SearchCatalogResponse.error(SearchCatalogErrorCode.INVALID_EXECUTION_CONTEXT);
        }
        if (execution.requestContext().requiresClarification()) {
            return SearchCatalogResponse.error(SearchCatalogErrorCode.CLARIFICATION_REQUIRED);
        }
        if (expired(execution)) {
            return SearchCatalogResponse.error(SearchCatalogErrorCode.DEADLINE_EXCEEDED);
        }

        RecommendationRequestContext policyContext = policyContext(execution.requestContext(), request.inferredCategory());
        List<Long> allowedIds;
        try {
            allowedIds = findAllowedIds(policyContext);
        } catch (RuntimeException e) {
            log.warn("searchCatalog eligibility lookup failed: requestId={} error={}",
                    execution.requestId(), e.getMessage());
            return SearchCatalogResponse.error(SearchCatalogErrorCode.SEARCH_REPOSITORY_FAILURE);
        }
        if (allowedIds.isEmpty()) {
            return SearchCatalogResponse.empty(SearchCatalogEmptyReason.NO_ELIGIBLE_PRODUCTS);
        }

        float[] embedding;
        try {
            String semanticQuery = execution.searchAttempt() == 1
                    ? policyContext.originalQuery()
                    : nonBlankOrOriginal(request.semanticQuery(), policyContext.originalQuery());
            embedding = embeddingModel != null ? embeddingModel.embed(semanticQuery) : null;
        } catch (RuntimeException e) {
            log.warn("searchCatalog embedding failed: requestId={} error={}",
                    execution.requestId(), e.getMessage());
            return SearchCatalogResponse.error(SearchCatalogErrorCode.EMBEDDING_FAILED);
        }
        if (expired(execution)) {
            return SearchCatalogResponse.error(SearchCatalogErrorCode.DEADLINE_EXCEEDED);
        }

        try {
            List<SearchCandidate> candidates = hybridSearchService.search(
                    request.keywordQuery(), embedding, allowedIds);
            if (candidates.isEmpty()) {
                return SearchCatalogResponse.empty(SearchCatalogEmptyReason.NO_SEARCH_MATCHES);
            }

            Set<Long> existingIds = new HashSet<>(productRepository.findAllById(
                    candidates.stream().map(SearchCandidate::productId).toList()).stream()
                    .map(Product::getId)
                    .toList());
            List<SearchCandidate> existingCandidates = candidates.stream()
                    .filter(candidate -> existingIds.contains(candidate.productId()))
                    .toList();
            if (existingCandidates.isEmpty()) {
                return SearchCatalogResponse.empty(SearchCatalogEmptyReason.STALE_PRODUCT_REFERENCES);
            }

            List<ValidatedCandidate> validated = evaluatorChain.filter(existingCandidates, policyContext);
            if (validated.isEmpty()) {
                return SearchCatalogResponse.empty(SearchCatalogEmptyReason.NO_VALID_CANDIDATES);
            }
            if (expired(execution)) {
                return SearchCatalogResponse.error(SearchCatalogErrorCode.DEADLINE_EXCEEDED);
            }
            return SearchCatalogResponse.success(validated.stream()
                    .limit(RESULT_LIMIT)
                    .map(this::toCandidate)
                    .toList());
        } catch (RuntimeException e) {
            log.warn("searchCatalog repository search failed: requestId={} error={}",
                    execution.requestId(), e.getMessage());
            return SearchCatalogResponse.error(SearchCatalogErrorCode.SEARCH_REPOSITORY_FAILURE);
        }
    }

    private List<Long> findAllowedIds(RecommendationRequestContext context) {
        PriceRange price = context.priceRange();
        BigDecimal minPrice = price != null && price.minPrice() != null ? BigDecimal.valueOf(price.minPrice()) : null;
        BigDecimal maxPrice = price != null && price.maxPrice() != null ? BigDecimal.valueOf(price.maxPrice()) : null;
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        CategoryConstraint category = context.categoryConstraint();
        if (category == null || category.source() != ConditionSource.EXPLICIT) {
            return productRepository.findEligibleProductIds(
                    context.memberId(), since, ProductStatus.ACTIVE, minPrice, maxPrice);
        }
        return productRepository.findEligibleProductIdsByCategories(
                context.memberId(), since, ProductStatus.ACTIVE, minPrice, maxPrice, category.categories());
    }

    private RecommendationRequestContext policyContext(RecommendationRequestContext original, String inferredCategory) {
        PriceRange explicitPrice = original.priceRange() != null
                && original.priceRange().source() == ConditionSource.EXPLICIT ? original.priceRange() : null;
        CategoryConstraint explicitCategory = original.categoryConstraint() != null
                && original.categoryConstraint().source() == ConditionSource.EXPLICIT
                ? original.categoryConstraint() : null;
        CategoryConstraint category = explicitCategory;
        if (category == null) {
            category = CategoryTaxonomy.canonicalName(inferredCategory)
                    .map(value -> new CategoryConstraint(List.of(value), null, ConditionSource.INFERRED))
                    .orElse(null);
        }
        return new RecommendationRequestContext(original.originalQuery(), original.memberId(), explicitPrice, category,
                InterpretationStatus.READY);
    }

    private SearchCatalogCandidate toCandidate(ValidatedCandidate validated) {
        SearchCandidate search = validated.candidate();
        Product product = validated.product();
        return new SearchCatalogCandidate(
                "product:" + product.getId(), product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getCategory(), product.getBrand(), product.getStock(),
                search.bm25Rank(), search.vectorRank(), search.vectorSimilarity(), search.rrfScore());
    }

    private boolean expired(SearchCatalogExecutionContext execution) {
        return !Instant.now().isBefore(execution.deadline());
    }

    private String nonBlankOrOriginal(String value, String original) {
        return value == null || value.isBlank() ? original : value;
    }
}
