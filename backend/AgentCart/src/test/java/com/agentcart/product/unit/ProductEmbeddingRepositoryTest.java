package com.agentcart.product.unit;

import com.agentcart.product.domain.ProductEmbedding;
import com.agentcart.product.repository.ProductEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProductEmbeddingRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private ProductEmbeddingRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ProductEmbeddingRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("findByProductId - returns embedding when found")
    void findByProductId_existingProduct_returnsEmbedding() {
        ProductEmbedding embedding = new ProductEmbedding(1L, 42L, new float[]{0.1f, 0.2f, 0.3f},
                "text-embedding-3-small", OffsetDateTime.now());
        given(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
                .willReturn(List.of(embedding));

        Optional<ProductEmbedding> result = repository.findByProductId(42L);

        assertThat(result).isPresent();
        assertThat(result.get().productId()).isEqualTo(42L);
        assertThat(result.get().model()).isEqualTo("text-embedding-3-small");
    }

    @Test
    @DisplayName("findByProductId - returns empty when not found")
    void findByProductId_notFound_returnsEmpty() {
        given(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
                .willReturn(List.of());

        Optional<ProductEmbedding> result = repository.findByProductId(99L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("upsertEmbedding - executes upsert query with vector string")
    void upsertEmbedding_callsUpdateWithCorrectParams() {
        float[] embedding = {0.1f, 0.2f, 0.3f};

        repository.upsertEmbedding(1L, embedding, "text-embedding-3-small");

        then(jdbcTemplate).should().update(anyString(), argThat((Map<String, ?> params) ->
                params.get("productId").equals(1L)
                && params.get("model").equals("text-embedding-3-small")
                && params.get("embedding").toString().startsWith("[")
        ));
    }
}