package com.agentcart.recommendation.unit;

import com.agentcart.order.repository.OrderItemRepository;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.dto.ValidatedCandidate;
import com.agentcart.recommendation.dto.CategoryConstraint;
import com.agentcart.recommendation.dto.ConditionSource;
import com.agentcart.recommendation.dto.InterpretationStatus;
import com.agentcart.recommendation.dto.PriceRange;
import com.agentcart.recommendation.dto.RecommendationRequestContext;
import com.agentcart.recommendation.service.EvaluatorChain;
import com.agentcart.recommendation.service.evaluator.CategoryValidator;
import com.agentcart.recommendation.service.evaluator.PriceConstraintValidator;
import com.agentcart.recommendation.service.evaluator.RuleFilterValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EvaluatorChainTest {

    @Mock private ProductRepository productRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CategoryValidator categoryValidator;
    @Mock private RuleFilterValidator ruleFilterValidator;
    @Mock private PriceConstraintValidator priceConstraintValidator;

    @InjectMocks
    private EvaluatorChain evaluatorChain;

    @Test
    @DisplayName("모든 단계 통과 — 결과에 포함")
    void filter_allPassValidators_included() {
        SearchCandidate c1 = candidate(1L), c2 = candidate(2L);
        Product p1 = product(1L), p2 = product(2L);

        given(productRepository.findAllById(any())).willReturn(List.of(p1, p2));
        given(orderItemRepository.findProductIdsOrderedByMemberSince(anyLong(), any())).willReturn(List.of());
        given(categoryValidator.validate(any(), any())).willReturn(true);
        given(ruleFilterValidator.validate(any(), any(), any())).willReturn(true);
        given(priceConstraintValidator.validate(any(), any(), any())).willReturn(true);

        List<ValidatedCandidate> result = evaluatorChain.filter(List.of(c1, c2), context());

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("ACTIVE 아닌 상품 — 결과에서 제외")
    void filter_inactiveProduct_excluded() {
        SearchCandidate c1 = candidate(1L);
        Product p1 = product(1L);
        ReflectionTestUtils.setField(p1, "status", ProductStatus.INACTIVE);

        given(productRepository.findAllById(any())).willReturn(List.of(p1));
        given(orderItemRepository.findProductIdsOrderedByMemberSince(anyLong(), any())).willReturn(List.of());

        List<ValidatedCandidate> result = evaluatorChain.filter(List.of(c1), context());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("일부만 카테고리 일치 — 불일치 후보만 제외(fallback 미발동)")
    void filter_partialCategoryMatch_excludesMismatchOnly() {
        SearchCandidate c1 = candidate(1L), c2 = candidate(2L);
        Product p1 = product(1L), p2 = product(2L);

        given(productRepository.findAllById(any())).willReturn(List.of(p1, p2));
        given(orderItemRepository.findProductIdsOrderedByMemberSince(anyLong(), any())).willReturn(List.of());
        given(categoryValidator.validate(eq(p1), any())).willReturn(true);
        given(categoryValidator.validate(eq(p2), any())).willReturn(false);
        given(ruleFilterValidator.validate(any(), any(), any(Set.class))).willReturn(true);
        given(priceConstraintValidator.validate(any(), any(), any())).willReturn(true);

        List<ValidatedCandidate> result = evaluatorChain.filter(
                List.of(c1, c2), context(ConditionSource.INFERRED, "패션·의류"));

        assertThat(result).extracting(vc -> vc.candidate().productId()).containsExactly(1L);
    }

    @Test
    @DisplayName("카테고리 필터로 전부 제외 — 카테고리 없이 재시도하여 회수(graceful fallback) (#167)")
    void filter_categoryEmptiesResult_fallsBackWithoutCategory() {
        SearchCandidate c1 = candidate(1L);
        Product p1 = product(1L);

        given(productRepository.findAllById(any())).willReturn(List.of(p1));
        given(orderItemRepository.findProductIdsOrderedByMemberSince(anyLong(), any())).willReturn(List.of());
        // 1차(카테고리 지정): 전부 reject → 2차(빈 카테고리 재시도): 통과
        given(categoryValidator.validate(any(), eq(List.of("패션·의류")))).willReturn(false);
        given(categoryValidator.validate(any(), eq(List.of()))).willReturn(true);
        given(ruleFilterValidator.validate(any(), any(), any(Set.class))).willReturn(true);
        given(priceConstraintValidator.validate(any(), any(), any())).willReturn(true);

        List<ValidatedCandidate> result = evaluatorChain.filter(
                List.of(c1), context(ConditionSource.INFERRED, "패션·의류"));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("명시 카테고리로 전부 제외되면 카테고리를 완화하지 않는다")
    void filter_explicitCategoryEmptiesResult_doesNotFallback() {
        SearchCandidate candidate = candidate(1L);
        Product product = product(1L);
        given(productRepository.findAllById(any())).willReturn(List.of(product));
        given(orderItemRepository.findProductIdsOrderedByMemberSince(anyLong(), any())).willReturn(List.of());
        given(categoryValidator.validate(any(), eq(List.of("패션·의류")))).willReturn(false);
        RecommendationRequestContext context = new RecommendationRequestContext("패션·의류 장갑", 1L, null,
                new CategoryConstraint(List.of("패션·의류"), "패션·의류", ConditionSource.EXPLICIT),
                InterpretationStatus.READY);

        List<ValidatedCandidate> result = evaluatorChain.filter(List.of(candidate), context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ACTIVE여도 재고가 0이면 결과에서 제외한다")
    void filter_activeProductWithZeroStock_excluded() {
        SearchCandidate candidate = candidate(1L);
        Product product = product(1L);
        ReflectionTestUtils.setField(product, "stock", 0);
        given(productRepository.findAllById(any())).willReturn(List.of(product));
        given(orderItemRepository.findProductIdsOrderedByMemberSince(anyLong(), any())).willReturn(List.of());

        List<ValidatedCandidate> result = evaluatorChain.filter(List.of(candidate), context());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("RuleFilterValidator 실패 — 결과에서 제외")
    void filter_ruleRejected_excluded() {
        SearchCandidate c1 = candidate(1L);
        Product p1 = product(1L);

        given(productRepository.findAllById(any())).willReturn(List.of(p1));
        given(orderItemRepository.findProductIdsOrderedByMemberSince(anyLong(), any())).willReturn(List.of());
        given(categoryValidator.validate(any(), any())).willReturn(true);
        given(ruleFilterValidator.validate(any(), any(), any(Set.class))).willReturn(false);

        List<ValidatedCandidate> result = evaluatorChain.filter(List.of(c1), context());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("PriceConstraintValidator 실패 — 결과에서 제외")
    void filter_priceRejected_excluded() {
        SearchCandidate c1 = candidate(1L);
        Product p1 = product(1L);

        given(productRepository.findAllById(any())).willReturn(List.of(p1));
        given(orderItemRepository.findProductIdsOrderedByMemberSince(anyLong(), any())).willReturn(List.of());
        given(categoryValidator.validate(any(), any())).willReturn(true);
        given(ruleFilterValidator.validate(any(), any(), any(Set.class))).willReturn(true);
        given(priceConstraintValidator.validate(any(), any(), any())).willReturn(false);

        RecommendationRequestContext context = new RecommendationRequestContext("", 1L,
                new PriceRange(null, 5000L, null, ConditionSource.INFERRED), null, InterpretationStatus.READY);

        List<ValidatedCandidate> result = evaluatorChain.filter(List.of(c1), context);

        assertThat(result).isEmpty();
    }

    private SearchCandidate candidate(long productId) {
        return new SearchCandidate(productId, 1, 1, 0.0, 0.5);
    }

    private RecommendationRequestContext context() {
        return new RecommendationRequestContext("", 1L, null, null, InterpretationStatus.READY);
    }

    private RecommendationRequestContext context(ConditionSource source, String category) {
        return new RecommendationRequestContext("", 1L, null,
                new CategoryConstraint(List.of(category), null, source), InterpretationStatus.READY);
    }

    private Product product(long id) {
        Product p = Product.builder()
                .name("상품" + id).category("카테고리").price(BigDecimal.valueOf(10000)).stock(10).build();
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "status", ProductStatus.ACTIVE);
        return p;
    }
}
