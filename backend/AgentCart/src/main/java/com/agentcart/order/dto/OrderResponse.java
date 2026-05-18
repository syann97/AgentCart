package com.agentcart.order.dto;

import com.agentcart.order.domain.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        BigDecimal totalPrice,
        List<OrderItemResponse> items,
        String recipientName,
        String phone,
        String address,
        String addressDetail,
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalPrice(),
                items,
                order.getRecipientName(),
                order.getPhone(),
                order.getAddress(),
                order.getAddressDetail(),
                order.getCreatedAt()
        );
    }
}