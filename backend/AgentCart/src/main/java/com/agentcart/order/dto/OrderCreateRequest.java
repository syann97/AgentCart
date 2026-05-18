package com.agentcart.order.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OrderCreateRequest {

    private List<Long> cartItemIds;

    private Long productId;

    @Min(value = 1, message = "수량은 1 이상이어야 합니다")
    private int quantity;

    @NotBlank(message = "수령인 이름을 입력해주세요")
    private String recipientName;

    @NotBlank(message = "연락처를 입력해주세요")
    private String phone;

    @NotBlank(message = "주소를 입력해주세요")
    private String address;

    private String addressDetail;

    @AssertTrue(message = "장바구니 주문(cartItemIds) 또는 바로구매(productId + quantity) 중 하나만 입력해주세요")
    public boolean isValidOrderType() {
        boolean hasCartItems = cartItemIds != null && !cartItemIds.isEmpty();
        boolean hasDirect = productId != null && quantity >= 1;
        return hasCartItems ^ hasDirect;
    }

    public boolean isCartOrder() {
        return cartItemIds != null && !cartItemIds.isEmpty();
    }
}