package com.agentcart.recommendation.unit;

import com.agentcart.recommendation.dto.CategoryTaxonomy;
import com.agentcart.recommendation.dto.ConditionSource;
import com.agentcart.recommendation.dto.EnrichedQuery;
import com.agentcart.recommendation.dto.InterpretationStatus;
import com.agentcart.recommendation.dto.RecommendationRequestContext;
import com.agentcart.recommendation.service.RecommendationRequestContextFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationRequestContextFactoryTest {

    private final RecommendationRequestContextFactory factory = new RecommendationRequestContextFactory();

    @Test
    @DisplayName("가격 상한·하한은 포함 경계와 원문 근거로 보존한다")
    void create_priceBounds_preservesInclusiveValuesAndEvidence() {
        RecommendationRequestContext context = factory.create("3만원 이상 50,000원 이하 캠핑용품", 7L);

        assertThat(context.priceRange().minPrice()).isEqualTo(30_000L);
        assertThat(context.priceRange().maxPrice()).isEqualTo(50_000L);
        assertThat(context.priceRange().evidence()).contains("3만원 이상", "50,000원 이하");
        assertThat(context.priceRange().source()).isEqualTo(ConditionSource.EXPLICIT);
        assertThat(context.status()).isEqualTo(InterpretationStatus.READY);
    }

    @Test
    @DisplayName("물결표 가격 범위는 양쪽 값을 만원 단위로 해석한다")
    void create_tildeRange_inheritsUnit() {
        RecommendationRequestContext context = factory.create("3만~5만원 선물", 1L);

        assertThat(context.priceRange().minPrice()).isEqualTo(30_000L);
        assertThat(context.priceRange().maxPrice()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("역전된 가격 범위는 조건을 유지하고 구체화 필요로 분류한다")
    void create_conflictingBounds_requiresClarification() {
        RecommendationRequestContext context = factory.create("5만원 이상 3만원 이하 캠핑용품", 1L);

        assertThat(context.status()).isEqualTo(InterpretationStatus.CLARIFICATION_REQUIRED);
        assertThat(context.priceRange().minPrice()).isEqualTo(50_000L);
        assertThat(context.priceRange().maxPrice()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("13개 정식 카테고리를 모두 명시 조건으로 판별한다")
    void create_allCanonicalCategories_recognizesAll() {
        for (String category : CategoryTaxonomy.names()) {
            RecommendationRequestContext context = factory.create(category + " 추천", 1L);
            assertThat(context.categoryConstraint().categories()).containsExactly(category);
            assertThat(context.categoryConstraint().source()).isEqualTo(ConditionSource.EXPLICIT);
        }
    }

    @Test
    @DisplayName("승인된 모든 별칭은 정식 카테고리로 정규화한다")
    void create_approvedAliases_normalizesCategories() {
        CategoryTaxonomy.aliases().forEach((category, aliases) -> aliases.forEach(alias -> {
            RecommendationRequestContext context = factory.create(alias + " 추천", 1L);
            assertThat(context.categoryConstraint().categories()).containsExactly(category);
            assertThat(context.categoryConstraint().evidence()).isEqualTo(alias);
        }));
    }

    @Test
    @DisplayName("알 수 없는 카테고리는 명시 조건으로 만들지 않는다")
    void create_unknownCategory_doesNotCreateConstraint() {
        assertThat(factory.create("자동차용품 추천", 1L).categoryConstraint()).isNull();
    }

    @Test
    @DisplayName("원 단위가 없는 일반 수치는 가격 조건으로 해석하지 않는다")
    void create_unitlessNumber_doesNotCreatePriceConstraint() {
        assertThat(factory.create("평점 3 이상 상품", 1L).priceRange()).isNull();
    }

    @Test
    @DisplayName("모델 조건은 명시 가격과 카테고리를 덮어쓰지 못한다")
    void mergeInferred_explicitConditionsWin() {
        RecommendationRequestContext explicit = factory.create("5만원 이하 사무용품", 1L);

        RecommendationRequestContext merged = explicit.mergeInferred(
                new EnrichedQuery("노트북", "노트북", List.of("디지털·IT기기"), 100_000L, 200_000L));

        assertThat(merged.priceRange().maxPrice()).isEqualTo(50_000L);
        assertThat(merged.categoryConstraint().categories()).containsExactly("문구·오피스");
        assertThat(merged.priceRange().source()).isEqualTo(ConditionSource.EXPLICIT);
        assertThat(merged.categoryConstraint().source()).isEqualTo(ConditionSource.EXPLICIT);
    }
}
