package com.agentcart.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CartItemAddRequest {

    @NotNull(message = "상품 ID를 입력해주세요")
    private Long productId;

    @Min(value = 1, message = "수량은 1 이상이어야 합니다")
    private int quantity;
}