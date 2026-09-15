package com.agentcart.recommendation.unit;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.CategoryConstraint;
import com.agentcart.recommendation.dto.ConditionSource;
import com.agentcart.recommendation.dto.InterpretationStatus;
import com.agentcart.recommendation.dto.PriceRange;
import com.agentcart.recommendation.dto.RecommendationRequestContext;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.dto.SearchCatalogEmptyReason;
import com.agentcart.recommendation.dto.SearchCatalogErrorCode;
import com.agentcart.recommendation.dto.SearchCatalogExecutionContext;
import com.agentcart.recommendation.dto.SearchCatalogRequest;
import com.agentcart.recommendation.dto.SearchCatalogResponse;
import com.agentcart.recommendation.dto.SearchCatalogStatus;
import com.agentcart.recommendation.dto.ValidatedCandidate;
import com.agentcart.recommendation.service.EvaluatorChain;
import com.agentcart.recommendation.service.HybridSearchService;
import com.agentcart.recommendation.service.SearchCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SearchCatalogServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final String ORIGINAL_QUERY = "5만원 이하 사무용품 추천";

    @Mock private ProductRepository productRepository;
    @Mock private HybridSearchService hybridSearchService;
    @Mock private EvaluatorChain evaluatorChain;
    @Mock private EmbeddingModel embeddingModel;

    @InjectMocks private SearchCatalogService searchCatalogService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(searchCatalogService, "embeddingModel", embeddingModel);
    }

    @Test
    @DisplayName("명시 조건 - 허용 ID 검색과 최종 평가에 보존하고 결과를 10개로 제한")
    void search_explicitConstraints_preservesPolicyAndLimitsResults() {
        RecommendationRequestContext context = explicitContext();
        List<Long> allowedIds = ids(12);
        List<SearchCandidate> candidates = candidates(12);
        List<Product> products = products(12, "문구·오피스");
        given(productRepository.findEligibleProductIdsByCategories(
                eq(MEMBER_ID), any(), eq(ProductStatus.ACTIVE), eq(BigDecimal.ZERO),
                eq(BigDecimal.valueOf(50_000)), eq(List.of("문구·오피스"))))
                .willReturn(allowedIds);
        given(embeddingModel.embed(ORIGINAL_QUERY)).willReturn(new float[]{0.1f});
        given(hybridSearchService.search("노트 선물", new float[]{0.1f}, allowedIds)).willReturn(candidates);
        given(productRepository.findAllById(anyList())).willReturn(products);
        given(evaluatorChain.filter(anyList(), any())).willReturn(validated(candidates, products));

        SearchCatalogResponse response = searchCatalogService.search(
                new SearchCatalogRequest("노트 선물", "모델이 바꾼 의미 검색어", "디지털·IT기기"),
                execution(context, 1));

        assertThat(response.status()).isEqualTo(SearchCatalogStatus.SUCCESS);
        assertThat(response.candidates()).hasSize(10);
        assertThat(response.candidates()).extracting(candidate -> candidate.productId())
                .containsExactlyElementsOf(ids(10));
        assertThat(response.candidates().get(0).evidenceId()).isEqualTo("product:1");
        assertThat(response.candidates().get(0).price()).isEqualByComparingTo("30000");
        assertThat(response.candidates().get(0).category()).isEqualTo("문구·오피스");
        assertThat(response.candidates().get(0).stock()).isEqualTo(10);
        verify(embeddingModel).embed(ORIGINAL_QUERY);
        ArgumentCaptor<RecommendationRequestContext> policy = ArgumentCaptor.forClass(RecommendationRequestContext.class);
        verify(evaluatorChain).filter(anyList(), policy.capture());
        assertThat(policy.getValue().priceRange().source()).isEqualTo(ConditionSource.EXPLICIT);
        assertThat(policy.getValue().categoryConstraint().categories()).containsExactly("문구·오피스");
    }

    @Test
    @DisplayName("허용 ID 없음 - BM25·Vector·embedding 검색을 실행하지 않음")
    void search_noEligibleProducts_skipsSearch() {
        given(productRepository.findEligibleProductIdsByCategories(any(), any(), any(), any(), any(), anyList()))
                .willReturn(List.of());

        SearchCatalogResponse response = searchCatalogService.search(
                new SearchCatalogRequest("노트", "노트", null), execution(explicitContext(), 1));

        assertThat(response.status()).isEqualTo(SearchCatalogStatus.EMPTY);
        assertThat(response.emptyReason()).isEqualTo(SearchCatalogEmptyReason.NO_ELIGIBLE_PRODUCTS);
        verifyNoInteractions(embeddingModel, hybridSearchService, evaluatorChain);
    }

    @Test
    @DisplayName("추론 카테고리 - 허용 ID를 줄이지 않고 최종 soft constraint로만 전달")
    void search_inferredCategory_isSoftConstraint() {
        RecommendationRequestContext context = new RecommendationRequestContext(
                "운동 선물", MEMBER_ID, null, null, InterpretationStatus.READY);
        given(productRepository.findEligibleProductIds(any(), any(), any(), any(), any()))
                .willReturn(List.of(1L));
        given(embeddingModel.embed("운동 선물")).willReturn(new float[]{0.1f});
        given(hybridSearchService.search(any(), any(), any())).willReturn(List.of(candidate(1L)));
        Product product = product(1L, "스포츠·피트니스");
        given(productRepository.findAllById(anyList())).willReturn(List.of(product));
        given(evaluatorChain.filter(anyList(), any())).willReturn(List.of(new ValidatedCandidate(candidate(1L), product)));

        searchCatalogService.search(new SearchCatalogRequest("운동", "운동용품", "운동용품"), execution(context, 1));

        verify(productRepository).findEligibleProductIds(eq(MEMBER_ID), any(), eq(ProductStatus.ACTIVE),
                eq(null), eq(null));
        verify(productRepository, never()).findEligibleProductIdsByCategories(any(), any(), any(), any(), any(), anyList());
        ArgumentCaptor<RecommendationRequestContext> policy = ArgumentCaptor.forClass(RecommendationRequestContext.class);
        verify(evaluatorChain).filter(anyList(), policy.capture());
        assertThat(policy.getValue().categoryConstraint().source()).isEqualTo(ConditionSource.INFERRED);
        assertThat(policy.getValue().categoryConstraint().categories()).containsExactly("스포츠·피트니스");
    }

    @Test
    @DisplayName("두 번째 검색 - 모델이 제공한 의미 검색어를 임베딩")
    void search_secondAttempt_embedsModelSemanticQuery() {
        RecommendationRequestContext context = noConstraintContext();
        given(productRepository.findEligibleProductIds(any(), any(), any(), any(), any()))
                .willReturn(List.of(1L));
        given(embeddingModel.embed("업무 기록용 종이 제품")).willReturn(new float[]{0.1f});
        given(hybridSearchService.search(any(), any(), any())).willReturn(List.of());

        searchCatalogService.search(
                new SearchCatalogRequest("업무 노트", "업무 기록용 종이 제품", null), execution(context, 2));

        verify(embeddingModel).embed("업무 기록용 종이 제품");
    }

    @Test
    @DisplayName("검색 후 상품 상태 변경 - 최종 재검증에서 제외")
    void search_productChangedAfterSearch_returnsNoValidCandidates() {
        stubSingleSearch();
        Product product = product(1L, "문구·오피스");
        given(productRepository.findAllById(anyList())).willReturn(List.of(product));
        given(evaluatorChain.filter(anyList(), any())).willReturn(List.of());

        SearchCatalogResponse response = searchCatalogService.search(
                new SearchCatalogRequest("노트", "노트", null), execution(noConstraintContext(), 1));

        assertThat(response.status()).isEqualTo(SearchCatalogStatus.EMPTY);
        assertThat(response.emptyReason()).isEqualTo(SearchCatalogEmptyReason.NO_VALID_CANDIDATES);
    }

    @Test
    @DisplayName("삭제된 상품 ID - 저장소 오류가 아닌 stale reference 빈 결과")
    void search_missingProductReference_returnsDistinctEmptyReason() {
        stubSingleSearch();
        given(productRepository.findAllById(anyList())).willReturn(List.of());

        SearchCatalogResponse response = searchCatalogService.search(
                new SearchCatalogRequest("노트", "노트", null), execution(noConstraintContext(), 1));

        assertThat(response.status()).isEqualTo(SearchCatalogStatus.EMPTY);
        assertThat(response.emptyReason()).isEqualTo(SearchCatalogEmptyReason.STALE_PRODUCT_REFERENCES);
        verifyNoInteractions(evaluatorChain);
    }

    @Test
    @DisplayName("검색 저장소 장애 - 정상 0건과 구분되는 오류 반환")
    void search_repositoryFailure_returnsError() {
        given(productRepository.findEligibleProductIds(any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("database unavailable"));

        SearchCatalogResponse response = searchCatalogService.search(
                new SearchCatalogRequest("노트", "노트", null), execution(noConstraintContext(), 1));

        assertThat(response.status()).isEqualTo(SearchCatalogStatus.ERROR);
        assertThat(response.errorCode()).isEqualTo(SearchCatalogErrorCode.SEARCH_REPOSITORY_FAILURE);
    }

    @Test
    @DisplayName("기한 만료 - 저장소나 모델을 호출하지 않고 종료")
    void search_expiredDeadline_skipsWork() {
        SearchCatalogExecutionContext expired = new SearchCatalogExecutionContext(
                noConstraintContext(), "request-1", 1, Instant.now().minusSeconds(1));

        SearchCatalogResponse response = searchCatalogService.search(
                new SearchCatalogRequest("노트", "노트", null), expired);

        assertThat(response.errorCode()).isEqualTo(SearchCatalogErrorCode.DEADLINE_EXCEEDED);
        verifyNoInteractions(productRepository, embeddingModel, hybridSearchService, evaluatorChain);
    }

    private void stubSingleSearch() {
        given(productRepository.findEligibleProductIds(any(), any(), any(), any(), any()))
                .willReturn(List.of(1L));
        given(embeddingModel.embed(anyString())).willReturn(new float[]{0.1f});
        given(hybridSearchService.search(any(), any(), any())).willReturn(List.of(candidate(1L)));
    }

    private RecommendationRequestContext explicitContext() {
        return new RecommendationRequestContext(ORIGINAL_QUERY, MEMBER_ID,
                new PriceRange(0L, 50_000L, "5만원 이하", ConditionSource.EXPLICIT),
                new CategoryConstraint(List.of("문구·오피스"), "사무용품", ConditionSource.EXPLICIT),
                InterpretationStatus.READY);
    }

    private RecommendationRequestContext noConstraintContext() {
        return new RecommendationRequestContext("노트 추천", MEMBER_ID, null, null, InterpretationStatus.READY);
    }

    private SearchCatalogExecutionContext execution(RecommendationRequestContext context, int attempt) {
        return new SearchCatalogExecutionContext(context, "request-1", attempt, Instant.now().plusSeconds(30));
    }

    private List<Long> ids(int count) {
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= count; id++) ids.add(id);
        return ids;
    }

    private List<SearchCandidate> candidates(int count) {
        return ids(count).stream().map(this::candidate).toList();
    }

    private SearchCandidate candidate(long id) {
        return new SearchCandidate(id, (int) id, (int) id, 0.8, 1.0 / id);
    }

    private List<Product> products(int count, String category) {
        return ids(count).stream().map(id -> product(id, category)).toList();
    }

    private Product product(long id, String category) {
        Product product = Product.builder()
                .name("상품" + id).description("설명" + id).price(BigDecimal.valueOf(30_000))
                .category(category).brand("브랜드").stock(10).status(ProductStatus.ACTIVE).build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private List<ValidatedCandidate> validated(List<SearchCandidate> candidates, List<Product> products) {
        return candidates.stream()
                .map(candidate -> new ValidatedCandidate(candidate, products.get(candidate.productId().intValue() - 1)))
                .toList();
    }
}
