package com.agentcart.recommendation.unit;

import com.agentcart.product.domain.Product;
import com.agentcart.recommendation.service.evaluator.PriceConstraintValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceConstraintValidatorTest {

    private final PriceConstraintValidator validator = new PriceConstraintValidator();

    @Test
    @DisplayName("제약 없음(min/max null) - 통과")
    void validate_noConstraint_returnsTrue() {
        assertThat(validator.validate(product(100000), null, null)).isTrue();
    }

    @Test
    @DisplayName("maxPrice 이하 - 통과")
    void validate_withinMax_returnsTrue() {
        assertThat(validator.validate(product(90000), null, 100000L)).isTrue();
    }

    @Test
    @DisplayName("maxPrice 초과 - 제외")
    void validate_aboveMax_returnsFalse() {
        assertThat(validator.validate(product(160000), null, 100000L)).isFalse();
    }

    @Test
    @DisplayName("minPrice 미만 - 제외")
    void validate_belowMin_returnsFalse() {
        assertThat(validator.validate(product(30000), 50000L, null)).isFalse();
    }

    @Test
    @DisplayName("min/max 범위 내 - 통과")
    void validate_withinRange_returnsTrue() {
        assertThat(validator.validate(product(100000), 50000L, 150000L)).isTrue();
    }

    @Test
    @DisplayName("경계값(가격 == maxPrice) - 통과")
    void validate_equalsMaxBoundary_returnsTrue() {
        assertThat(validator.validate(product(100000), null, 100000L)).isTrue();
    }

    @Test
    @DisplayName("경계값(가격 == minPrice) - 통과")
    void validate_equalsMinBoundary_returnsTrue() {
        assertThat(validator.validate(product(50000), 50000L, null)).isTrue();
    }

    private Product product(long price) {
        return Product.builder()
                .name("테스트 상품").category("전자제품").price(BigDecimal.valueOf(price)).stock(10).build();
    }
}
