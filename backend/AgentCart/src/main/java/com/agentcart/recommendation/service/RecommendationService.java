package com.agentcart.recommendation.service;

import com.agentcart.member.service.MemberService;
import com.agentcart.recommendation.dto.*;
import com.agentcart.recommendation.repository.RecommendationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int TOP_N = 10;

    private final RecommendationHistoryRepository historyRepository;
    private final MemberService memberService;
    private final QueryEnrichmentService queryEnrichmentService;
    private final HybridSearchService hybridSearchService;
    private final EvaluatorChain evaluatorChain;
    private final LlmReasoningService llmReasoningService;

    @Autowired(required = false)
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
        float[] embedding = embed(enriched.enrichedQuery());

        List<SearchCandidate> candidates = hybridSearchService.search(enriched.enrichedQuery(), embedding);

        List<ValidatedCandidate> validated = evaluatorChain
                .filter(candidates, enriched.enrichedQuery(), memberId)
                .stream().limit(TOP_N).toList();

        Map<Long, LlmReasonResult> reasons = llmReasoningService.generateReasons(validated, enriched.enrichedQuery());

        return buildResults(validated, reasons);
    }

    private List<RecommendationResult> buildResults(List<ValidatedCandidate> validated,
                                                    Map<Long, LlmReasonResult> reasons) {
        return validated.stream()
                .map(vc -> {
                    LlmReasonResult r = reasons.getOrDefault(vc.candidate().productId(),
                            new LlmReasonResult(vc.product().getCategory() + " 카테고리에서 검색된 상품입니다.", List.of()));
                    return new RecommendationResult(
                            vc.candidate().productId(),
                            vc.product().getName(),
                            r.reason(),
                            r.conditions(),
                            vc.candidate().rrfScore());
                })
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