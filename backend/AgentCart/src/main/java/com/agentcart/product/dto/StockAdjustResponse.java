package com.agentcart.product.dto;

import com.agentcart.product.domain.Product;

public record StockAdjustResponse(Long id, int stock, String status) {
    public static StockAdjustResponse from(Product product) {
        return new StockAdjustResponse(product.getId(), product.getStock(), product.getStatus().name());
    }
}