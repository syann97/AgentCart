package com.agentcart.recommendation.service;

import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.dto.VectorSearchResult;
import com.agentcart.recommendation.repository.RecommendationVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private static final int RRF_K = 60;
    static final int SEARCH_LIMIT = 50;
    private static final double RRF_SCORE_THRESHOLD = 0.01;
    // bge-m3 코사인 분포상 0.5는 과도하게 높아 리콜이 붕괴함 → 0.4로 완화 (#162). 노이즈는 카테고리 필터/RRF로 제어.
    private static final double MIN_VECTOR_SIMILARITY = 0.4;

    // 범용 포장/선물 토큰은 ngram bigram으로 대량 매칭되어 BM25 과매칭을 유발 → 불용어 처리 (#163).
    private static final Set<String> BM25_STOPWORDS = Set.of("세트", "선물", "묶음", "패키지");

    private final ProductRepository productRepository;

    @Autowired(required = false)
    private RecommendationVectorRepository vectorRepository;

    public List<SearchCandidate> search(String keyword, float[] queryEmbedding) {
        List<Long> bm25Ids = fetchBm25Ids(keyword);
        List<VectorSearchResult> vectorResults = fetchVectorResults(queryEmbedding);
        return fuse(bm25Ids, vectorResults);
    }

    private List<Long> fetchBm25Ids(String keyword) {
        String cleaned = sanitizeBm25Keyword(keyword);
        List<Long> ids = productRepository.bm25Search(cleaned, SEARCH_LIMIT)
                .stream()
                .map(row -> ((Number) row[0]).longValue())
                .toList();
        log.info("HybridSearch BM25: keyword='{}' cleaned='{}' hits={} ids={}", keyword, cleaned, ids.size(), ids);
        return ids;
    }

    // 중복 토큰 제거 + 범용 불용어 제거로 BM25 과매칭 완화 (#163). 토큰이 전부 제거되면 원본 유지(fallback).
    private String sanitizeBm25Keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return keyword;
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : keyword.trim().split("\\s+")) {
            if (!token.isBlank() && !BM25_STOPWORDS.contains(token)) tokens.add(token);
        }
        String cleaned = String.join(" ", tokens);
        return cleaned.isBlank() ? keyword : cleaned;
    }

    private List<VectorSearchResult> fetchVectorResults(float[] queryEmbedding) {
        if (vectorRepository == null || queryEmbedding == null) {
            log.info("HybridSearch Vector: skipped (vectorRepository={}, embedding={})",
                    vectorRepository != null ? "ok" : "null",
                    queryEmbedding != null ? "ok" : "null");
            return List.of();
        }
        List<VectorSearchResult> results = vectorRepository.findTopBySimilarity(queryEmbedding, SEARCH_LIMIT, MIN_VECTOR_SIMILARITY);
        if (log.isInfoEnabled()) {
            Set<Long> ids = results.stream().map(VectorSearchResult::productId).collect(Collectors.toSet());
            Map<Long, String> nameMap = productRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(p -> p.getId(), p -> p.getName()));
            log.info("HybridSearch Vector: hits={} (minSimilarity={}) results={}",
                    results.size(), MIN_VECTOR_SIMILARITY,
                    results.stream()
                            .map(r -> r.productId() + " '" + nameMap.getOrDefault(r.productId(), "?") + "'(" + String.format("%.2f", r.similarity()) + ")")
                            .toList());
        }
        return results;
    }

    List<SearchCandidate> fuse(List<Long> bm25Ids, List<VectorSearchResult> vectorResults) {
        Map<Long, Integer> bm25Ranks = rankMap(bm25Ids);
        List<Long> vectorIds = vectorResults.stream().map(VectorSearchResult::productId).toList();
        Map<Long, Integer> vectorRanks = rankMap(vectorIds);
        Map<Long, Double> vectorSimilarities = vectorResults.stream()
                .collect(Collectors.toMap(VectorSearchResult::productId, VectorSearchResult::similarity));

        Set<Long> all = new LinkedHashSet<>();
        all.addAll(bm25Ids);
        all.addAll(vectorIds);

        List<SearchCandidate> raw = all.stream()
                .map(id -> {
                    int bm25Rank = bm25Ranks.getOrDefault(id, 0);
                    int vectorRank = vectorRanks.getOrDefault(id, 0);
                    double similarity = vectorSimilarities.getOrDefault(id, 0.0);
                    double score = rrfScore(bm25Rank) + rrfScore(vectorRank) * similarity;
                    return new SearchCandidate(id, bm25Rank, vectorRank, similarity, score);
                })
                .filter(c -> c.rrfScore() >= RRF_SCORE_THRESHOLD)
                .sorted(Comparator.comparingDouble(SearchCandidate::rrfScore).reversed())
                .limit(SEARCH_LIMIT)
                .toList();

        if (raw.isEmpty()) return raw;
        double maxScore = raw.get(0).rrfScore();
        if (maxScore <= 0) return raw;

        return raw.stream()
                .map(c -> new SearchCandidate(c.productId(), c.bm25Rank(), c.vectorRank(),
                        c.vectorSimilarity(), c.rrfScore() / maxScore))
                .toList();
    }

    private Map<Long, Integer> rankMap(List<Long> ids) {
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            map.put(ids.get(i), i + 1);
        }
        return map;
    }

    private double rrfScore(int rank) {
        if (rank == 0) return 0.0;
        return 1.0 / (RRF_K + rank);
    }
}