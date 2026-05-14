package com.agentcart.product.domain;

import java.time.OffsetDateTime;

public record ProductEmbedding(Long id, Long productId, float[] embedding, String model, OffsetDateTime createdAt) {}