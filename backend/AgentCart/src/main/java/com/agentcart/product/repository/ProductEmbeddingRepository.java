package com.agentcart.product.repository;

import com.agentcart.product.domain.ProductEmbedding;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

@Repository
@ConditionalOnBean(name = "pgVectorJdbcTemplate")
public class ProductEmbeddingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProductEmbeddingRepository(@Qualifier("pgVectorJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ProductEmbedding> findByProductId(Long productId) {
        List<ProductEmbedding> results = jdbcTemplate.query(
                "SELECT id, product_id, embedding::text, model, created_at " +
                "FROM product_embeddings WHERE product_id = :productId",
                Map.of("productId", productId),
                this::mapRow
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void upsertEmbedding(Long productId, float[] embedding, String model) {
        jdbcTemplate.update(
                """
                INSERT INTO product_embeddings (product_id, embedding, model, created_at)
                VALUES (:productId, CAST(:embedding AS vector), :model, NOW())
                ON CONFLICT (product_id) DO UPDATE
                SET embedding = EXCLUDED.embedding, model = EXCLUDED.model
                """,
                Map.of("productId", productId, "embedding", toVectorString(embedding), "model", model)
        );
    }

    private ProductEmbedding mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ProductEmbedding(
                rs.getLong("id"),
                rs.getLong("product_id"),
                parseVector(rs.getString("embedding")),
                rs.getString("model"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private String toVectorString(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float f : embedding) {
            joiner.add(String.valueOf(f));
        }
        return joiner.toString();
    }

    private float[] parseVector(String vectorStr) {
        String[] parts = vectorStr.substring(1, vectorStr.length() - 1).split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}