package com.agentcart.recommendation.unit;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.service.evaluator.RuleFilterValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RuleFilterValidatorTest {

    private final RuleFilterValidator validator = new RuleFilterValidator();

    @Test
    @DisplayName("SOLD_OUT 상품 - 제거")
    void validate_soldOutProduct_returnsFalse() {
        assertThat(validator.validate(candidate(1L), productWithStatus(ProductStatus.SOLD_OUT), Set.of())).isFalse();
    }

    @Test
    @DisplayName("최근 7일 이내 주문한 상품 - 제거")
    void validate_recentlyOrderedProduct_returnsFalse() {
        assertThat(validator.validate(candidate(1L), activeProduct(), Set.of(1L, 2L, 3L))).isFalse();
    }

    @Test
    @DisplayName("최근 7일 이내 주문 없음 - 통과")
    void validate_notRecentlyOrdered_returnsTrue() {
        assertThat(validator.validate(candidate(1L), activeProduct(), Set.of(99L))).isTrue();
    }

    @Test
    @DisplayName("주문 이력 없음 - 통과")
    void validate_noOrderHistory_returnsTrue() {
        assertThat(validator.validate(candidate(1L), activeProduct(), Set.of())).isTrue();
    }

    private SearchCandidate candidate(long productId) {
        return new SearchCandidate(productId, 1, 1, 0.0, 0.05);
    }

    private Product activeProduct() {
        return productWithStatus(ProductStatus.ACTIVE);
    }

    private Product productWithStatus(ProductStatus status) {
        Product p = Product.builder()
                .name("테스트 상품").category("전자제품").price(BigDecimal.valueOf(10000)).stock(10).build();
        ReflectionTestUtils.setField(p, "status", status);
        return p;
    }
}