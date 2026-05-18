package com.agentcart.order.dto;

import com.agentcart.order.domain.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal priceAtOrder,
        int quantity,
        BigDecimal subtotal
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProductName(),
                item.getPriceAtOrder(),
                item.getQuantity(),
                item.getPriceAtOrder().multiply(BigDecimal.valueOf(item.getQuantity()))
        );
    }
}
