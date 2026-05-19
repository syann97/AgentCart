package com.agentcart.recommendation.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Repository
@ConditionalOnBean(name = "pgVectorJdbcTemplate")
public class RecommendationVectorRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RecommendationVectorRepository(@Qualifier("pgVectorJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> findTopBySimilarity(float[] queryVector, int limit) {
        return jdbcTemplate.query(
                "SELECT product_id FROM product_embeddings " +
                "ORDER BY embedding <=> CAST(:queryVector AS vector) ASC LIMIT :limit",
                Map.of("queryVector", toVectorString(queryVector), "limit", limit),
                (rs, rowNum) -> rs.getLong("product_id")
        );
    }

    private String toVectorString(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float f : embedding) {
            joiner.add(String.valueOf(f));
        }
        return joiner.toString();
    }
}