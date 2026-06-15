package com.agentcart.recommendation.service;

import com.agentcart.member.service.MemberService;
import com.agentcart.recommendation.dto.*;
import com.agentcart.recommendation.repository.RecommendationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int TOP_N = 5;

    private final RecommendationHistoryRepository historyRepository;
    private final MemberService memberService;
    private final QueryEnrichmentService queryEnrichmentService;
    private final HybridSearchService hybridSearchService;
    private final EvaluatorChain evaluatorChain;
    private final LlmReasoningService llmReasoningService;

    @Autowired(required = false)
    @Qualifier("ollamaEmbeddingModel")
    private EmbeddingModel embeddingModel;

    @Transactional(readOnly = true)
    public List<RecommendationHistoryResponse> getHistory(String email) {
        Long memberId = memberService.findByEmail(email).getId();
        return historyRepository.findTop20ByMemberIdOrderByRecommendedAtDesc(memberId)
                .stream()
                .map(RecommendationHistoryResponse::from)
                .toList();
    }

    public List<RecommendationResult> recommend(String query, Long memberId) {
        EnrichedQuery enriched = queryEnrichmentService.enrich(query);
        // 벡터 arm은 자연어 원본 질의를 임베딩(상품 임베딩이 자연어 라벨 기반이라 키워드 나열보다 정합이 높음, #162).
        // BM25 arm은 확장 키워드를 사용해 키워드 매칭을 유지.
        float[] embedding = embed(query);

        String bm25Query = enriched.bm25Keywords() != null ? enriched.bm25Keywords() : enriched.enrichedQuery();
        List<SearchCandidate> candidates = hybridSearchService.search(bm25Query, embedding);

        List<ValidatedCandidate> validated = evaluatorChain
                .filter(candidates, memberId, enriched.minPrice(), enriched.maxPrice(), enriched.categories())
                .stream().limit(TOP_N).toList();

        Map<Long, LlmReasonResult> reasons = llmReasoningService.generateReasons(validated, enriched.enrichedQuery());

        return buildResults(validated, reasons);
    }

    private List<RecommendationResult> buildResults(List<ValidatedCandidate> validated,
                                                    Map<Long, LlmReasonResult> reasons) {
        return validated.stream()
                .map(vc -> Map.entry(vc, reasons.getOrDefault(vc.candidate().productId(),
                        new LlmReasonResult(vc.product().getCategory() + " 카테고리에서 검색된 상품입니다.", List.of()))))
                .map(e -> new RecommendationResult(
                        e.getKey().candidate().productId(),
                        e.getKey().product().getName(),
                        e.getKey().product().getPrice(),
                        e.getValue().reason(),
                        e.getValue().conditions(),
                        e.getKey().candidate().rrfScore()))
                .toList();
    }

    private float[] embed(String text) {
        if (embeddingModel == null) return null;
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.warn("Embedding failed for query: {}", e.getMessage());
            return null;
        }
    }
}