package com.agentcart.recommendation.unit;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.service.evaluator.DbValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DbValidatorTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private DbValidator dbValidator;

    @Test
    @DisplayName("ACTIVE 상품 - 통과")
    void validate_activeProduct_returnsProduct() {
        Product product = activeProduct();
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        Optional<Product> result = dbValidator.validate(candidate(1L));

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(product);
    }

    @Test
    @DisplayName("존재하지 않는 상품 - 제거")
    void validate_productNotFound_returnsEmpty() {
        given(productRepository.findById(1L)).willReturn(Optional.empty());

        assertThat(dbValidator.validate(candidate(1L))).isEmpty();
    }

    @Test
    @DisplayName("INACTIVE 상품 - 제거")
    void validate_inactiveProduct_returnsEmpty() {
        Product product = productWithStatus(ProductStatus.INACTIVE);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        assertThat(dbValidator.validate(candidate(1L))).isEmpty();
    }

    @Test
    @DisplayName("SOLD_OUT 상품 - 제거")
    void validate_soldOutProduct_returnsEmpty() {
        Product product = productWithStatus(ProductStatus.SOLD_OUT);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        assertThat(dbValidator.validate(candidate(1L))).isEmpty();
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