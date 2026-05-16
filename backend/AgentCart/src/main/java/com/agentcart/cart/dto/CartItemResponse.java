package com.agentcart.cart.dto;

import com.agentcart.cart.domain.CartItem;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal productPrice,
        String productStatus,
        int quantity,
        BigDecimal subtotal
) {
    public static CartItemResponse from(CartItem item) {
        BigDecimal subtotal = item.getProduct().getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getProduct().getPrice(),
                item.getProduct().getStatus().name(),
                item.getQuantity(),
                subtotal
        );
    }
}