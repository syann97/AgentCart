package com.agentcart.recommendation.service.evaluator;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.recommendation.dto.SearchCandidate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RuleFilterValidator {

    public boolean validate(SearchCandidate candidate, Product product, Set<Long> recentlyOrdered) {
        if (product.getStatus() == ProductStatus.SOLD_OUT) return false;
        return !recentlyOrdered.contains(candidate.productId());
    }
}