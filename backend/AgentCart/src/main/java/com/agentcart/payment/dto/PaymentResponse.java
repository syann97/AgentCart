package com.agentcart.payment.dto;

import com.agentcart.payment.domain.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        String status,
        BigDecimal amount,
        String paymentKey,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getPaymentKey(),
                payment.getPaidAt(),
                payment.getCreatedAt()
        );
    }
}