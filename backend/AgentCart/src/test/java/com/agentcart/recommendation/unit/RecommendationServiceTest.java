package com.agentcart.recommendation.unit;

import com.agentcart.member.service.MemberService;
import com.agentcart.product.domain.Product;
import com.agentcart.recommendation.dto.EnrichedQuery;
import com.agentcart.recommendation.dto.LlmReasonResult;
import com.agentcart.recommendation.dto.RecommendationResult;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.dto.ValidatedCandidate;
import com.agentcart.recommendation.repository.RecommendationHistoryRepository;
import com.agentcart.recommendation.service.EvaluatorChain;
import com.agentcart.recommendation.service.HybridSearchService;
import com.agentcart.recommendation.service.LlmReasoningService;
import com.agentcart.recommendation.service.QueryEnrichmentService;
import com.agentcart.recommendation.service.RecommendationService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final String QUERY = "선물 추천";
    private static final Long MEMBER_ID = 1L;

    @Mock private RecommendationHistoryRepository historyRepository;
    @Mock private MemberService memberService;
    @Mock private QueryEnrichmentService queryEnrichmentService;
    @Mock private HybridSearchService hybridSearchService;
    @Mock private EvaluatorChain evaluatorChain;
    @Mock private LlmReasoningService llmReasoningService;
    @Mock private EmbeddingModel embeddingModel;

    @InjectMocks
    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(recommendationService, "embeddingModel", embeddingModel);
    }

    @Test
    @DisplayName("bm25Keywords null - enrichedQuery로 폴백하여 BM25 검색")
    void recommend_bm25KeywordsNull_fallsBackToEnrichedQuery() {
        given(queryEnrichmentService.enrich(QUERY))
                .willReturn(new EnrichedQuery("확장된 쿼리", null, List.of(), null, null));

        recommendationService.recommend(QUERY, MEMBER_ID);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(hybridSearchService).search(keyword.capture(), any());
        assertThat(keyword.getValue()).isEqualTo("확장된 쿼리");
    }

    @Test
    @DisplayName("bm25Keywords 존재 - bm25Keywords로 BM25 검색")
    void recommend_bm25KeywordsPresent_usesBm25Keywords() {
        given(queryEnrichmentService.enrich(QUERY))
                .willReturn(new EnrichedQuery("확장된 쿼리", "bm25 키워드", List.of(), null, null));

        recommendationService.recommend(QUERY, MEMBER_ID);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(hybridSearchService).search(keyword.capture(), any());
        assertThat(keyword.getValue()).isEqualTo("bm25 키워드");
    }

    @Test
    @DisplayName("가격 제약(min/max) - evaluatorChain.filter로 그대로 전달")
    void recommend_priceConstraints_passedToEvaluatorChain() {
        given(queryEnrichmentService.enrich(QUERY))
                .willReturn(new EnrichedQuery("쿼리", "키워드", List.of(), 50000L, 150000L));

        recommendationService.recommend(QUERY, MEMBER_ID);

        verify(evaluatorChain).filter(any(), eq(MEMBER_ID), eq(50000L), eq(150000L), any());
    }

    @Test
    @DisplayName("categories - evaluatorChain.filter로 그대로 전달")
    void recommend_categories_passedToEvaluatorChain() {
        given(queryEnrichmentService.enrich(QUERY))
                .willReturn(new EnrichedQuery("쿼리", "키워드", List.of("패션·의류"), null, null));

        recommendationService.recommend(QUERY, MEMBER_ID);

        verify(evaluatorChain).filter(any(), eq(MEMBER_ID), any(), any(), eq(List.of("패션·의류")));
    }

    @Test
    @DisplayName("벡터 arm - enrichedQuery가 아닌 원본 질의를 임베딩 (#162)")
    void recommend_vectorArm_embedsOriginalQuery() {
        given(queryEnrichmentService.enrich(QUERY))
                .willReturn(new EnrichedQuery("확장된 쿼리", "bm25 키워드", List.of(), null, null));

        recommendationService.recommend(QUERY, MEMBER_ID);

        verify(embeddingModel).embed(QUERY);
    }

    @Test
    @DisplayName("임베딩 실패 - null 임베딩으로 검색 진행")
    void recommend_embeddingFails_searchesWithNullEmbedding() {
        given(queryEnrichmentService.enrich(QUERY))
                .willReturn(new EnrichedQuery("쿼리", "키워드", List.of(), null, null));
        given(embeddingModel.embed(anyString())).willThrow(new RuntimeException("embed fail"));

        recommendationService.recommend(QUERY, MEMBER_ID);

        ArgumentCaptor<float[]> embedding = ArgumentCaptor.forClass(float[].class);
        verify(hybridSearchService).search(eq("키워드"), embedding.capture());
        assertThat(embedding.getValue()).isNull();
    }

    @Test
    @DisplayName("validated 비어있음 - 빈 결과 반환")
    void recommend_noValidatedCandidates_returnsEmpty() {
        given(queryEnrichmentService.enrich(QUERY))
                .willReturn(new EnrichedQuery("쿼리", "키워드", List.of(), null, null));
        given(evaluatorChain.filter(any(), any(), any(), any(), any())).willReturn(List.of());

        List<RecommendationResult> results = recommendationService.recommend(QUERY, MEMBER_ID);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("validated가 TOP_N 초과 - 상위 5개로 제한")
    void recommend_moreThanTopN_limitsToFive() {
        given(queryEnrichmentService.enrich(QUERY))
                .willReturn(new EnrichedQuery("쿼리", "키워드", List.of(), null, null));
        List<ValidatedCandidate> seven = new ArrayList<>();
        for (long i = 1; i <= 7; i++) seven.add(validated(i, "상품" + i, "전자제품", 10000, 0.9));
        given(evaluatorChain.filter(any(), any(), any(), any(), any())).willReturn(seven);

        List<RecommendationResult> results = recommendationService.recommend(QUERY, MEMBER_ID);

        assertThat(results).hasSize(5);
    }

    @Test
    @DisplayName("LLM 이유 누락 - 카테고리 기반 fallback 이유 생성")
    void recommend_missingLlmReason_usesFallbackReason() {
        given(queryEnrichmentService.enrich(QUERY))
                .willReturn(new EnrichedQuery("쿼리", "키워드", List.of(), null, null));
        given(evaluatorChain.filter(any(), any(), any(), any(), any()))
                .willReturn(List.of(validated(1L, "무선 이어폰", "전자제품", 89000, 0.95)));
        given(llmReasoningService.generateReasons(any(), any())).willReturn(Map.of());

        List<RecommendationResult> results = recommendationService.recommend(QUERY, MEMBER_ID);

        assertThat(results).hasSize(1);
        RecommendationResult r = results.get(0);
        assertThat(r.productId()).isEqualTo(1L);
        assertThat(r.productName()).isEqualTo("무선 이어폰");
        assertThat(r.price()).isEqualByComparingTo(BigDecimal.valueOf(89000));
        assertThat(r.reason()).isEqualTo("전자제품 카테고리에서 검색된 상품입니다.");
        assertThat(r.conditions()).isEmpty();
        assertThat(r.score()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("LLM 이유 존재 - 해당 이유와 조건을 결과에 반영")
    void recommend_withLlmReason_usesGeneratedReason() {
        given(queryEnrichmentService.enrich(QUERY))
                .willReturn(new EnrichedQuery("쿼리", "키워드", List.of(), null, null));
        given(evaluatorChain.filter(any(), any(), any(), any(), any()))
                .willReturn(List.of(validated(1L, "무선 이어폰", "전자제품", 89000, 0.95)));
        given(llmReasoningService.generateReasons(any(), any()))
                .willReturn(Map.of(1L, new LlmReasonResult("출퇴근에 적합합니다", List.of("노이즈캔슬링", "장시간 배터리"))));

        List<RecommendationResult> results = recommendationService.recommend(QUERY, MEMBER_ID);

        assertThat(results).hasSize(1);
        RecommendationResult r = results.get(0);
        assertThat(r.reason()).isEqualTo("출퇴근에 적합합니다");
        assertThat(r.conditions()).containsExactly("노이즈캔슬링", "장시간 배터리");
    }

    private SearchCandidate candidate(long id, double rrfScore) {
        return new SearchCandidate(id, 1, 1, 0.0, rrfScore);
    }

    private Product product(String name, String category, long price) {
        return Product.builder()
                .name(name).category(category).price(BigDecimal.valueOf(price)).stock(10).build();
    }

    private ValidatedCandidate validated(long id, String name, String category, long price, double rrfScore) {
        return new ValidatedCandidate(candidate(id, rrfScore), product(name, category, price));
    }
}
