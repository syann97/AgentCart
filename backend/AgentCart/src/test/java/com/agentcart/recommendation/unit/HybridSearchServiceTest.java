package com.agentcart.recommendation.unit;

import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.dto.VectorSearchResult;
import com.agentcart.recommendation.repository.RecommendationVectorRepository;
import com.agentcart.recommendation.service.HybridSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RecommendationVectorRepository vectorRepository;

    @InjectMocks
    private HybridSearchService hybridSearchService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(hybridSearchService, "vectorRepository", vectorRepository);
    }

    @Test
    @DisplayName("BM25에만 매칭된 상품 - vectorRank=0, vectorSimilarity=0.0, rrfScore=1.0(정규화)")
    void fuse_bm25OnlyMatch_vectorRankIsZero() {
        given(productRepository.bm25Search(anyString(), anyInt()))
                .willReturn(bm25Results(new Object[]{1L, 0.8}));
        given(vectorRepository.findTopBySimilarity(any(), anyInt(), anyDouble()))
                .willReturn(List.of());

        List<SearchCandidate> results = hybridSearchService.search("laptop", new float[]{0.1f});

        assertThat(results).hasSize(1);
        SearchCandidate candidate = results.get(0);
        assertThat(candidate.productId()).isEqualTo(1L);
        assertThat(candidate.bm25Rank()).isEqualTo(1);
        assertThat(candidate.vectorRank()).isEqualTo(0);
        assertThat(candidate.vectorSimilarity()).isCloseTo(0.0, within(1e-6));
        assertThat(candidate.rrfScore()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("Vector에만 매칭된 상품 - bm25Rank=0, vectorSimilarity 반영")
    void fuse_vectorOnlyMatch_bm25RankIsZero() {
        given(productRepository.bm25Search(anyString(), anyInt()))
                .willReturn(List.of());
        given(vectorRepository.findTopBySimilarity(any(), anyInt(), anyDouble()))
                .willReturn(List.of(new VectorSearchResult(2L, 1.0)));

        List<SearchCandidate> results = hybridSearchService.search("laptop", new float[]{0.1f});

        assertThat(results).hasSize(1);
        SearchCandidate candidate = results.get(0);
        assertThat(candidate.productId()).isEqualTo(2L);
        assertThat(candidate.bm25Rank()).isEqualTo(0);
        assertThat(candidate.vectorRank()).isEqualTo(1);
        assertThat(candidate.vectorSimilarity()).isCloseTo(1.0, within(1e-6));
        assertThat(candidate.rrfScore()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("BM25 + Vector 둘 다 매칭된 상품 - rrfScore가 단일 매칭보다 높음")
    void fuse_bothMatch_higherScoreThanSingleMatch() {
        // product 1: BM25 rank 1 only
        // product 2: Vector rank 1 only, sim=1.0
        // product 3: BM25 rank 2, Vector rank 2, sim=1.0 (both)
        given(productRepository.bm25Search(anyString(), anyInt()))
                .willReturn(bm25Results(new Object[]{1L, 0.9}, new Object[]{3L, 0.6}));
        given(vectorRepository.findTopBySimilarity(any(), anyInt(), anyDouble()))
                .willReturn(List.of(new VectorSearchResult(2L, 1.0), new VectorSearchResult(3L, 1.0)));

        List<SearchCandidate> results = hybridSearchService.search("laptop", new float[]{0.1f});

        assertThat(results).hasSize(3);

        SearchCandidate both = results.stream().filter(c -> c.productId() == 3L).findFirst().orElseThrow();
        SearchCandidate bm25Only = results.stream().filter(c -> c.productId() == 1L).findFirst().orElseThrow();
        SearchCandidate vectorOnly = results.stream().filter(c -> c.productId() == 2L).findFirst().orElseThrow();

        // sim=1.0이므로 기존 RRF와 동일: both(2/62) > single(1/61)
        // 정규화: both=1.0, single=(1/61)/(2/62)=31/61
        assertThat(both.rrfScore()).isCloseTo(1.0, within(1e-6));
        assertThat(bm25Only.rrfScore()).isCloseTo(31.0 / 61, within(1e-6));
        assertThat(vectorOnly.rrfScore()).isCloseTo(31.0 / 61, within(1e-6));
        assertThat(both.rrfScore()).isGreaterThan(bm25Only.rrfScore());
        assertThat(results.get(0).productId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("rrfScore 내림차순 정렬 검증")
    void fuse_resultsSortedByRrfScoreDesc() {
        given(productRepository.bm25Search(anyString(), anyInt()))
                .willReturn(bm25Results(new Object[]{1L, 0.9}, new Object[]{2L, 0.5}));
        given(vectorRepository.findTopBySimilarity(any(), anyInt(), anyDouble()))
                .willReturn(List.of());

        List<SearchCandidate> results = hybridSearchService.search("laptop", new float[]{0.1f});

        assertThat(results.get(0).productId()).isEqualTo(1L);
        assertThat(results.get(1).productId()).isEqualTo(2L);
        assertThat(results.get(0).rrfScore()).isGreaterThan(results.get(1).rrfScore());
    }

    @Test
    @DisplayName("raw RRF score 0.01 미만 상품 - 정규화 전 제외 (rank 41: 1/101 < 0.01)")
    void fuse_belowRawThreshold_excluded() {
        List<Object[]> bm25 = new ArrayList<>();
        for (long i = 1; i <= 50; i++) bm25.add(new Object[]{i, 0.1});
        given(productRepository.bm25Search(anyString(), anyInt())).willReturn(bm25);
        given(vectorRepository.findTopBySimilarity(any(), anyInt(), anyDouble())).willReturn(List.of());

        List<SearchCandidate> results = hybridSearchService.search("query", new float[]{0.1f});

        assertThat(results).hasSize(40);
        assertThat(results).extracting(SearchCandidate::productId)
                .doesNotContain(41L, 42L, 43L, 44L, 45L, 46L, 47L, 48L, 49L, 50L);
    }

    @Test
    @DisplayName("벡터 유사도가 RRF 점수에 가중치로 반영됨 - 낮은 유사도는 점수 할인")
    void fuse_vectorSimilarityWeightsRrfScore() {
        // product 1: BM25 rank 1 → raw = 1/61
        // product 2: Vector rank 1, sim=0.8 → raw = 0.8/61 (>= threshold 0.01)
        given(productRepository.bm25Search(anyString(), anyInt()))
                .willReturn(bm25Results(new Object[]{1L, 0.9}));
        given(vectorRepository.findTopBySimilarity(any(), anyInt(), anyDouble()))
                .willReturn(List.of(new VectorSearchResult(2L, 0.8)));

        List<SearchCandidate> results = hybridSearchService.search("query", new float[]{0.1f});

        SearchCandidate bm25Only = results.stream().filter(c -> c.productId() == 1L).findFirst().orElseThrow();
        SearchCandidate vectorOnly = results.stream().filter(c -> c.productId() == 2L).findFirst().orElseThrow();

        // 정규화: max=1/61(bm25Only)=1.0, vectorOnly=0.8/61 / 1/61 = 0.8
        assertThat(bm25Only.rrfScore()).isCloseTo(1.0, within(1e-6));
        assertThat(vectorOnly.rrfScore()).isCloseTo(0.8, within(1e-6));
        assertThat(vectorOnly.vectorSimilarity()).isCloseTo(0.8, within(1e-6));
        assertThat(bm25Only.rrfScore()).isGreaterThan(vectorOnly.rrfScore());
    }

    @Test
    @DisplayName("BM25 조회 - 중복 토큰/범용 불용어(세트·선물) 제거된 키워드로 검색")
    void search_bm25Keyword_dedupedAndStopwordsRemoved() {
        given(productRepository.bm25Search(anyString(), anyInt())).willReturn(List.of());
        given(vectorRepository.findTopBySimilarity(any(), anyInt(), anyDouble())).willReturn(List.of());

        hybridSearchService.search("여자 귀걸이 여자 목걸이 세트 선물", new float[]{0.1f});

        ArgumentCaptor<String> kw = ArgumentCaptor.forClass(String.class);
        verify(productRepository).bm25Search(kw.capture(), anyInt());
        assertThat(kw.getValue()).isEqualTo("여자 귀걸이 목걸이");
    }

    @Test
    @DisplayName("BM25 조회 - 불용어 없는 질의는 그대로(회귀 없음)")
    void search_bm25Keyword_noStopwords_unchanged() {
        given(productRepository.bm25Search(anyString(), anyInt())).willReturn(List.of());
        given(vectorRepository.findTopBySimilarity(any(), anyInt(), anyDouble())).willReturn(List.of());

        hybridSearchService.search("겨울 방한 장갑", new float[]{0.1f});

        ArgumentCaptor<String> kw = ArgumentCaptor.forClass(String.class);
        verify(productRepository).bm25Search(kw.capture(), anyInt());
        assertThat(kw.getValue()).isEqualTo("겨울 방한 장갑");
    }

    @Test
    @DisplayName("BM25 조회 - 전부 불용어면 원본 유지(fallback)")
    void search_bm25Keyword_allStopwords_keepsOriginal() {
        given(productRepository.bm25Search(anyString(), anyInt())).willReturn(List.of());
        given(vectorRepository.findTopBySimilarity(any(), anyInt(), anyDouble())).willReturn(List.of());

        hybridSearchService.search("세트 선물", new float[]{0.1f});

        ArgumentCaptor<String> kw = ArgumentCaptor.forClass(String.class);
        verify(productRepository).bm25Search(kw.capture(), anyInt());
        assertThat(kw.getValue()).isEqualTo("세트 선물");
    }

    private static float[] any() {
        return org.mockito.ArgumentMatchers.any();
    }

    @SafeVarargs
    private static List<Object[]> bm25Results(Object[]... rows) {
        return List.of(rows);
    }
}