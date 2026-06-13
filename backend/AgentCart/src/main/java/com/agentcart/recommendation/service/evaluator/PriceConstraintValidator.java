package com.agentcart.recommendation.service.evaluator;

import com.agentcart.product.domain.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PriceConstraintValidator {

    public boolean validate(Product product, Long minPrice, Long maxPrice) {
        BigDecimal price = product.getPrice();
        if (minPrice != null && price.compareTo(BigDecimal.valueOf(minPrice)) < 0) return false;
        if (maxPrice != null && price.compareTo(BigDecimal.valueOf(maxPrice)) > 0) return false;
        return true;
    }
}
