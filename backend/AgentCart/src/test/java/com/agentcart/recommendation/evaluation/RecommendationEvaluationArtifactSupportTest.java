package com.agentcart.recommendation.evaluation;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RecommendationEvaluationArtifactSupportTest {

    @Test
    void fingerprintIsIndependentOfDatabaseOrderAndPriceScale() throws Exception {
        Product first = product("A", "brand", "description", "1000.00", "생활");
        Product equivalent = product("A", "brand", "description", "1000", "생활");
        Product second = product("B", null, null, "9.90", "식품");

        assertThat(RecommendationEvaluationArtifactSupport.immutableFactsSha256(List.of(first, second)))
                .isEqualTo(RecommendationEvaluationArtifactSupport.immutableFactsSha256(
                        List.of(second, equivalent)));
    }

    @Test
    void runIdMustIdentifyCommitTimestampAndRepetition() {
        RecommendationEvaluationArtifactSupport.requireValidRunId(
                "agentic-rag-ba6747f-20260917T120000Z-r1",
                "ba6747f012345678901234567890123456789012");

        assertThatIllegalArgumentException().isThrownBy(() ->
                RecommendationEvaluationArtifactSupport.requireValidRunId(
                        "agentic-rag-other-latest", "ba6747f012345678901234567890123456789012"));
    }

    private Product product(String name, String brand, String description, String price, String category) {
        return Product.builder()
                .name(name).brand(brand).description(description).price(new BigDecimal(price))
                .category(category).stock(1).status(ProductStatus.ACTIVE).build();
    }
}
