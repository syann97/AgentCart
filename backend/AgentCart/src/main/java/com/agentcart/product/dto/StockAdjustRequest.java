package com.agentcart.product.dto;

import jakarta.validation.constraints.NotBlank;

public record StockAdjustRequest(
        int delta,
        @NotBlank String reason
) {}