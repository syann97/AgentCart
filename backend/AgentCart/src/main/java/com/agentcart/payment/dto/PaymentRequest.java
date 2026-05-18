package com.agentcart.payment.dto;

import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotNull(message = "주문 ID는 필수입니다") Long orderId
) {}