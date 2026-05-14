package com.agentcart.product.dto;

import com.agentcart.product.domain.Product;

import java.math.BigDecimal;

public record ProductSummaryResponse(
        Long id,
        String name,
        BigDecimal price,
        String category,
        String brand,
        String status
) {
    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCategory(),
                product.getBrand(),
                product.getStatus().name()
        );
    }
}