package com.agentcart.recommendation.service.evaluator;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.SearchCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DbValidator {

    private final ProductRepository productRepository;

    public Optional<Product> validate(SearchCandidate candidate) {
        return productRepository.findById(candidate.productId())
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE);
    }
}