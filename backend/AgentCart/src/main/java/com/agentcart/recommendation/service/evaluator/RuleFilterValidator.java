package com.agentcart.recommendation.service.evaluator;

import com.agentcart.order.repository.OrderItemRepository;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.recommendation.dto.SearchCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RuleFilterValidator {

    private final OrderItemRepository orderItemRepository;

    public boolean validate(SearchCandidate candidate, Product product, Long memberId) {
        if (product.getStatus() == ProductStatus.SOLD_OUT) {
            return false;
        }
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<Long> recentlyOrdered = orderItemRepository.findProductIdsOrderedByMemberSince(memberId, since);
        return !recentlyOrdered.contains(candidate.productId());
    }
}