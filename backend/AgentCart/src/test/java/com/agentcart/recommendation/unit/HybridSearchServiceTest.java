package com.agentcart.recommendation.unit;

import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.repository.RecommendationVectorRepository;
import com.agentcart.recommendation.service.HybridSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

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
    @DisplayName("BM25에만 매칭된 상품 - vectorRank=0, rrfScore=1/(60+bm25Rank)")
    void fuse_bm25OnlyMatch_vectorRankIsZero() {
        // productId=1: BM25 rank 1, Vector 없음
        given(productRepository.bm25Search(anyString(), anyInt()))
                .willReturn(bm25Results(new Object[]{1L, 0.8}));
        given(vectorRepository.findTopBySimilarity(any(), anyInt()))
                .willReturn(List.<Long>of());

        List<SearchCandidate> results = hybridSearchService.search("laptop", new float[]{0.1f});

        assertThat(results).hasSize(1);
        SearchCandidate candidate = results.get(0);
        assertThat(candidate.productId()).isEqualTo(1L);
        assertThat(candidate.bm25Rank()).isEqualTo(1);
        assertThat(candidate.vectorRank()).isEqualTo(0);
        assertThat(candidate.rrfScore()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("Vector에만 매칭된 상품 - bm25Rank=0, rrfScore=1/(60+vectorRank)")
    void fuse_vectorOnlyMatch_bm25RankIsZero() {
        // productId=2: BM25 없음, Vector rank 1
        given(productRepository.bm25Search(anyString(), anyInt()))
                .willReturn(List.<Object[]>of());
        given(vectorRepository.findTopBySimilarity(any(), anyInt()))
                .willReturn(List.of(2L));

        List<SearchCandidate> results = hybridSearchService.search("laptop", new float[]{0.1f});

        assertThat(results).hasSize(1);
        SearchCandidate candidate = results.get(0);
        assertThat(candidate.productId()).isEqualTo(2L);
        assertThat(candidate.bm25Rank()).isEqualTo(0);
        assertThat(candidate.vectorRank()).isEqualTo(1);
        assertThat(candidate.rrfScore()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("BM25 + Vector 둘 다 매칭된 상품 - rrfScore가 단일 매칭보다 높음")
    void fuse_bothMatch_higherScoreThanSingleMatch() {
        // productId=1: BM25 rank 1 only
        // productId=2: Vector rank 1 only
        // productId=3: BM25 rank 2, Vector rank 2 (both)
        given(productRepository.bm25Search(anyString(), anyInt()))
                .willReturn(bm25Results(new Object[]{1L, 0.9}, new Object[]{3L, 0.6}));
        given(vectorRepository.findTopBySimilarity(any(), anyInt()))
                .willReturn(List.of(2L, 3L));

        List<SearchCandidate> results = hybridSearchService.search("laptop", new float[]{0.1f});

        assertThat(results).hasSize(3);

        SearchCandidate both = results.stream().filter(c -> c.productId() == 3L).findFirst().orElseThrow();
        SearchCandidate bm25Only = results.stream().filter(c -> c.productId() == 1L).findFirst().orElseThrow();
        SearchCandidate vectorOnly = results.stream().filter(c -> c.productId() == 2L).findFirst().orElseThrow();

        // 정규화 후: both(2/62이 max) → 1.0, single-source → (1/61)/(2/62) = 31/61
        assertThat(both.rrfScore()).isCloseTo(1.0, within(1e-6));
        assertThat(bm25Only.rrfScore()).isCloseTo(31.0 / 61, within(1e-6));
        assertThat(vectorOnly.rrfScore()).isCloseTo(31.0 / 61, within(1e-6));

        // 둘 다 매칭이 가장 높은 점수
        assertThat(both.rrfScore()).isGreaterThan(bm25Only.rrfScore());
        assertThat(results.get(0).productId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("rrfScore 내림차순 정렬 검증")
    void fuse_resultsSortedByRrfScoreDesc() {
        // rank 1이 rank 2보다 높은 점수를 가져야 함
        given(productRepository.bm25Search(anyString(), anyInt()))
                .willReturn(bm25Results(new Object[]{1L, 0.9}, new Object[]{2L, 0.5}));
        given(vectorRepository.findTopBySimilarity(any(), anyInt()))
                .willReturn(List.<Long>of());

        List<SearchCandidate> results = hybridSearchService.search("laptop", new float[]{0.1f});

        assertThat(results.get(0).productId()).isEqualTo(1L);
        assertThat(results.get(1).productId()).isEqualTo(2L);
        assertThat(results.get(0).rrfScore()).isGreaterThan(results.get(1).rrfScore());
    }

    @Test
    @DisplayName("raw RRF score 0.01 미만 상품 - 정규화 전 제외 (rank 41: 1/101 < 0.01)")
    void fuse_belowRawThreshold_excluded() {
        // BM25 rank 1~50, no vector → rank 41: 1/(60+41)=1/101≈0.0099 < 0.01 → 제외
        List<Object[]> bm25 = new ArrayList<>();
        for (long i = 1; i <= 50; i++) bm25.add(new Object[]{i, 0.1});
        given(productRepository.bm25Search(anyString(), anyInt())).willReturn(bm25);
        given(vectorRepository.findTopBySimilarity(any(), anyInt())).willReturn(List.<Long>of());

        List<SearchCandidate> results = hybridSearchService.search("query", new float[]{0.1f});

        // rank 1~40: 1/(60+N) >= 0.01 → 포함, rank 41~50: < 0.01 → 제외
        assertThat(results).hasSize(40);
        assertThat(results).extracting(SearchCandidate::productId)
                .doesNotContain(41L, 42L, 43L, 44L, 45L, 46L, 47L, 48L, 49L, 50L);
    }

    private static float[] any() {
        return org.mockito.ArgumentMatchers.any();
    }

    @SafeVarargs
    private static List<Object[]> bm25Results(Object[]... rows) {
        return List.of(rows);
    }
}