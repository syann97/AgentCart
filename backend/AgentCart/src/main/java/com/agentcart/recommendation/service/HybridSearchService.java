package com.agentcart.recommendation.service;

import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.repository.RecommendationVectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private static final int RRF_K = 60;
    static final int SEARCH_LIMIT = 50;

    private final ProductRepository productRepository;

    @Autowired(required = false)
    private RecommendationVectorRepository vectorRepository;

    public List<SearchCandidate> search(String keyword, float[] queryEmbedding) {
        List<Long> bm25Ids = fetchBm25Ids(keyword);
        List<Long> vectorIds = fetchVectorIds(queryEmbedding);
        return fuse(bm25Ids, vectorIds);
    }

    private List<Long> fetchBm25Ids(String keyword) {
        return productRepository.bm25Search(keyword, SEARCH_LIMIT)
                .stream()
                .map(row -> ((Number) row[0]).longValue())
                .toList();
    }

    private List<Long> fetchVectorIds(float[] queryEmbedding) {
        if (vectorRepository == null || queryEmbedding == null) {
            return List.of();
        }
        return vectorRepository.findTopBySimilarity(queryEmbedding, SEARCH_LIMIT);
    }

    List<SearchCandidate> fuse(List<Long> bm25Ids, List<Long> vectorIds) {
        Map<Long, Integer> bm25Ranks = rankMap(bm25Ids);
        Map<Long, Integer> vectorRanks = rankMap(vectorIds);

        Set<Long> all = new LinkedHashSet<>();
        all.addAll(bm25Ids);
        all.addAll(vectorIds);

        return all.stream()
                .map(id -> {
                    int bm25Rank = bm25Ranks.getOrDefault(id, 0);
                    int vectorRank = vectorRanks.getOrDefault(id, 0);
                    double score = rrfScore(bm25Rank) + rrfScore(vectorRank);
                    return new SearchCandidate(id, bm25Rank, vectorRank, score);
                })
                .sorted(Comparator.comparingDouble(SearchCandidate::rrfScore).reversed())
                .limit(SEARCH_LIMIT)
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