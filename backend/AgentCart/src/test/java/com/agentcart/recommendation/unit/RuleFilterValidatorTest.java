package com.agentcart.recommendation.unit;

import com.agentcart.order.repository.OrderItemRepository;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.service.evaluator.RuleFilterValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RuleFilterValidatorTest {

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private RuleFilterValidator validator;

    private static final Long MEMBER_ID = 10L;

    @Test
    @DisplayName("SOLD_OUT 상품 - 제거")
    void validate_soldOutProduct_returnsFalse() {
        Product product = productWithStatus(ProductStatus.SOLD_OUT);

        assertThat(validator.validate(candidate(1L), product, MEMBER_ID)).isFalse();
    }

    @Test
    @DisplayName("최근 7일 이내 주문한 상품 - 제거")
    void validate_recentlyOrderedProduct_returnsFalse() {
        Product product = activeProduct();
        given(orderItemRepository.findProductIdsOrderedByMemberSince(eq(MEMBER_ID), any(LocalDateTime.class)))
                .willReturn(List.of(1L, 2L, 3L));

        assertThat(validator.validate(candidate(1L), product, MEMBER_ID)).isFalse();
    }

    @Test
    @DisplayName("최근 7일 이내 주문 없음 - 통과")
    void validate_notRecentlyOrdered_returnsTrue() {
        Product product = activeProduct();
        given(orderItemRepository.findProductIdsOrderedByMemberSince(eq(MEMBER_ID), any(LocalDateTime.class)))
                .willReturn(List.of(99L));

        assertThat(validator.validate(candidate(1L), product, MEMBER_ID)).isTrue();
    }

    @Test
    @DisplayName("주문 이력 없음 - 통과")
    void validate_noOrderHistory_returnsTrue() {
        Product product = activeProduct();
        given(orderItemRepository.findProductIdsOrderedByMemberSince(eq(MEMBER_ID), any(LocalDateTime.class)))
                .willReturn(List.of());

        assertThat(validator.validate(candidate(1L), product, MEMBER_ID)).isTrue();
    }

    private SearchCandidate candidate(long productId) {
        return new SearchCandidate(productId, 1, 1, 0.05);
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