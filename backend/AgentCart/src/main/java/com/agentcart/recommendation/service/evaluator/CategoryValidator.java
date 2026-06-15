package com.agentcart.recommendation.service.evaluator;

import com.agentcart.product.domain.Product;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CategoryValidator {

    public boolean validate(Product product, List<String> categories) {
        if (categories == null || categories.isEmpty()) return true;
        return categories.contains(product.getCategory());
    }
}
