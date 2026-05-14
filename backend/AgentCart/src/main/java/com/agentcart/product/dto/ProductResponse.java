package com.agentcart.product.dto;

import com.agentcart.product.domain.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String category,
        String brand,
        int stock,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.getBrand(),
                product.getStock(),
                product.getStatus().name(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
