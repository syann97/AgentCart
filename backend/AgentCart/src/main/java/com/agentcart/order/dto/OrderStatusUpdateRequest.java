package com.agentcart.order.dto;

import com.agentcart.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderStatusUpdateRequest {

    @NotNull(message = "변경할 상태를 입력해주세요")
    private OrderStatus status;
}