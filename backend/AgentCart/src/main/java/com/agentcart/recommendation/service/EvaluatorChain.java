package com.agentcart.recommendation.service;

import com.agentcart.order.repository.OrderItemRepository;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.dto.ValidatedCandidate;
import com.agentcart.recommendation.service.evaluator.CategoryValidator;
import com.agentcart.recommendation.service.evaluator.PriceConstraintValidator;
import com.agentcart.recommendation.service.evaluator.RuleFilterValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluatorChain {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final CategoryValidator categoryValidator;
    private final RuleFilterValidator ruleFilterValidator;
    private final PriceConstraintValidator priceConstraintValidator;

    public List<ValidatedCandidate> filter(List<SearchCandidate> candidates, Long memberId,
                                           Long minPrice, Long maxPrice, List<String> categories) {
        Set<Long> ids = candidates.stream().map(SearchCandidate::productId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        Set<Long> recentlyOrdered = new HashSet<>(
                orderItemRepository.findProductIdsOrderedByMemberSince(memberId, LocalDateTime.now().minusDays(7)));

        List<ValidatedCandidate> result = new ArrayList<>();
        for (SearchCandidate candidate : candidates) {
            Product product = productMap.get(candidate.productId());
            if (product == null) {
                log.debug("REJECTED reason=PRODUCT_NOT_FOUND productId={}", candidate.productId());
                continue;
            }
            if (product.getStatus() != ProductStatus.ACTIVE) {
                log.debug("REJECTED reason=INACTIVE productId={} productName={}", product.getId(), product.getName());
                continue;
            }

            if (!categoryValidator.validate(product, categories)) {
                log.debug("REJECTED reason=CATEGORY_MISMATCH productId={} productName={} category={} wanted={}",
                        product.getId(), product.getName(), product.getCategory(), categories);
                continue;
            }
            if (!ruleFilterValidator.validate(candidate, product, recentlyOrdered)) {
                log.debug("REJECTED reason=RECENTLY_ORDERED productId={} productName={}", product.getId(), product.getName());
                continue;
            }
            if (!priceConstraintValidator.validate(product, minPrice, maxPrice)) {
                log.debug("REJECTED reason=PRICE_OUT_OF_RANGE productId={} productName={} price={} min={} max={}",
                        product.getId(), product.getName(), product.getPrice(), minPrice, maxPrice);
                continue;
            }

            result.add(new ValidatedCandidate(candidate, product));
        }

        log.debug("filter result: input={} passed={}", candidates.size(), result.size());
        return result;
    }
}
