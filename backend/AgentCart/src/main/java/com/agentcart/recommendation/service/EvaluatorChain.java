package com.agentcart.recommendation.service;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.dto.ValidatedCandidate;
import com.agentcart.recommendation.service.evaluator.ConsistencyValidator;
import com.agentcart.recommendation.service.evaluator.LlmCrossValidator;
import com.agentcart.recommendation.service.evaluator.RuleFilterValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EvaluatorChain {

    private final ProductRepository productRepository;
    private final ConsistencyValidator consistencyValidator;
    private final RuleFilterValidator ruleFilterValidator;
    private final LlmCrossValidator llmCrossValidator;

    private static final int LLM_CROSS_VALIDATE_LIMIT = 10;

    public List<ValidatedCandidate> filter(List<SearchCandidate> candidates, String query, Long memberId) {
        Set<Long> ids = candidates.stream().map(SearchCandidate::productId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<ValidatedCandidate> result = new ArrayList<>();
        for (SearchCandidate candidate : candidates) {
            Product product = productMap.get(candidate.productId());
            if (product == null || product.getStatus() != ProductStatus.ACTIVE) continue;

            if (!consistencyValidator.validate(candidate)) continue;
            if (!ruleFilterValidator.validate(candidate, product, memberId)) continue;

            result.add(new ValidatedCandidate(candidate, product));
        }

        // Stage 4: LLM cross-validation — synchronous filter on top candidates
        return result.stream()
                .limit(LLM_CROSS_VALIDATE_LIMIT)
                .filter(vc -> llmCrossValidator.validate(vc.candidate(), vc.product(), query))
                .toList();
    }
}