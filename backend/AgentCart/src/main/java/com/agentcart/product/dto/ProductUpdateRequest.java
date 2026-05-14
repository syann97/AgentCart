package com.agentcart.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class ProductUpdateRequest {

    @NotBlank(message = "상품명을 입력해주세요")
    private String name;

    private String description;

    @NotNull(message = "가격을 입력해주세요")
    @DecimalMin(value = "0.01", message = "가격은 0보다 커야 합니다")
    private BigDecimal price;

    @NotBlank(message = "카테고리를 입력해주세요")
    private String category;

    private String brand;

    @Min(value = 0, message = "재고는 0 이상이어야 합니다")
    private int stock;
}