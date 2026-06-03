package com.agentcart.recommendation.unit;

import com.agentcart.order.repository.OrderItemRepository;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.dto.ValidatedCandidate;
import com.agentcart.recommendation.service.EvaluatorChain;
import com.agentcart.recommendation.service.evaluator.ConsistencyValidator;
import com.agentcart.recommendation.service.evaluator.LlmCrossValidator;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EvaluatorChainTest {

    @Mock private ProductRepository productRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ConsistencyValidator consistencyValidator;
    @Mock private RuleFilterValidator ruleFilterValidator;
    @Mock private LlmCrossValidator llmCrossValidator;

    @InjectMocks
    private EvaluatorChain evaluatorChain;

    @Test
    @DisplayName("LLM 거절 상품 - 결과에서 제외")
    void filter_llmRejected_excludedFromResult() {
        SearchCandidate c1 = candidate(1L), c2 = candidate(2L);
        Product p1 = product(1L), p2 = product(2L);

        given(productRepository.findAllById(any())).willReturn(List.of(p1, p2));
        given(orderItemRepository.findProductIdsOrderedByMemberSince(anyLong(), any())).willReturn(List.of());
        given(consistencyValidator.validate(any())).willReturn(true);
        given(ruleFilterValidator.validate(any(), any(), any())).willReturn(true);
        given(llmCrossValidator.validate(c1, p1, "query")).willReturn(false);
        given(llmCrossValidator.validate(c2, p2, "query")).willReturn(true);

        List<ValidatedCandidate> result = evaluatorChain.filter(List.of(c1, c2), "query", 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).candidate().productId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("LLM 전체 거절 - 빈 결과 반환")
    void filter_allLlmRejected_emptyResult() {
        SearchCandidate c1 = candidate(1L);
        Product p1 = product(1L);

        given(productRepository.findAllById(any())).willReturn(List.of(p1));
        given(orderItemRepository.findProductIdsOrderedByMemberSince(anyLong(), any())).willReturn(List.of());
        given(consistencyValidator.validate(c1)).willReturn(true);
        given(ruleFilterValidator.validate(any(), any(), any())).willReturn(true);
        given(llmCrossValidator.validate(c1, p1, "query")).willReturn(false);

        List<ValidatedCandidate> result = evaluatorChain.filter(List.of(c1), "query", 1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("LLM 전체 승인 - 모든 상품 포함")
    void filter_allLlmAccepted_allIncluded() {
        SearchCandidate c1 = candidate(1L), c2 = candidate(2L);
        Product p1 = product(1L), p2 = product(2L);

        given(productRepository.findAllById(any())).willReturn(List.of(p1, p2));
        given(orderItemRepository.findProductIdsOrderedByMemberSince(anyLong(), any())).willReturn(List.of());
        given(consistencyValidator.validate(any())).willReturn(true);
        given(ruleFilterValidator.validate(any(), any(), any())).willReturn(true);
        given(llmCrossValidator.validate(any(), any(), anyString())).willReturn(true);

        List<ValidatedCandidate> result = evaluatorChain.filter(List.of(c1, c2), "query", 1L);

        assertThat(result).hasSize(2);
    }

    private SearchCandidate candidate(long productId) {
        return new SearchCandidate(productId, 1, 1, 0.0, 0.5);
    }

    private Product product(long id) {
        Product p = Product.builder()
                .name("상품" + id).category("카테고리").price(BigDecimal.valueOf(10000)).stock(10).build();
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "status", ProductStatus.ACTIVE);
        return p;
    }
}