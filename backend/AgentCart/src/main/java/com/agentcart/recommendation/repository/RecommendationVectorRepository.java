package com.agentcart.recommendation.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.agentcart.recommendation.dto.VectorSearchResult;

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

    public List<VectorSearchResult> findTopBySimilarity(float[] queryVector, int limit, double minSimilarity) {
        return jdbcTemplate.query(
                "SELECT product_id, similarity FROM (" +
                "  SELECT product_id, 1 - (embedding <=> CAST(:queryVector AS vector)) AS similarity" +
                "  FROM product_embeddings" +
                ") sub " +
                "WHERE similarity >= :minSimilarity " +
                "ORDER BY similarity DESC LIMIT :limit",
                Map.of("queryVector", toVectorString(queryVector), "limit", limit, "minSimilarity", minSimilarity),
                (rs, rowNum) -> new VectorSearchResult(rs.getLong("product_id"), rs.getDouble("similarity"))
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