package com.agentcart.recommendation.unit;

import com.agentcart.product.domain.Product;
import com.agentcart.recommendation.service.evaluator.CategoryValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryValidatorTest {

    private final CategoryValidator validator = new CategoryValidator();

    @Test
    @DisplayName("카테고리 목록에 포함 - 통과")
    void validate_categoryInList_returnsTrue() {
        assertThat(validator.validate(product("패션·의류"), List.of("패션·의류"))).isTrue();
    }

    @Test
    @DisplayName("카테고리 목록에 미포함 - 제외")
    void validate_categoryNotInList_returnsFalse() {
        assertThat(validator.validate(product("유아동"), List.of("패션·의류"))).isFalse();
    }

    @Test
    @DisplayName("여러 카테고리 중 하나 일치 - 통과")
    void validate_oneOfMultipleMatches_returnsTrue() {
        assertThat(validator.validate(product("주방용품"), List.of("패션·의류", "주방용품"))).isTrue();
    }

    @Test
    @DisplayName("categories null - 통과(fallback)")
    void validate_nullCategories_returnsTrue() {
        assertThat(validator.validate(product("유아동"), null)).isTrue();
    }

    @Test
    @DisplayName("categories 빈 배열 - 통과(fallback)")
    void validate_emptyCategories_returnsTrue() {
        assertThat(validator.validate(product("유아동"), List.of())).isTrue();
    }

    private Product product(String category) {
        return Product.builder()
                .name("테스트 상품").category(category).price(BigDecimal.valueOf(10000)).stock(10).build();
    }
}
