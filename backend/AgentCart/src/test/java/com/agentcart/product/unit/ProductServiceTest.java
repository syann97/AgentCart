package com.agentcart.product.unit;

import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.ProductException;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import com.agentcart.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("findById - returns product when found")
    void findById_existingProduct_returnsProduct() {
        Product product = buildProduct("Laptop", "electronics", ProductStatus.ACTIVE);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        Product result = productService.findById(1L);

        assertThat(result.getName()).isEqualTo("Laptop");
        assertThat(result.getCategory()).isEqualTo("electronics");
        assertThat(result.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("findById - throws PRODUCT_NOT_FOUND when not found")
    void findById_notFound_throwsException() {
        given(productRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ProductException.class)
                .satisfies(ex -> assertThat(((ProductException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("findByCategory - returns products in the given category")
    void findByCategory_existingCategory_returnsProducts() {
        List<Product> products = List.of(
                buildProduct("Laptop", "electronics", ProductStatus.ACTIVE),
                buildProduct("Phone", "electronics", ProductStatus.ACTIVE)
        );
        given(productRepository.findByCategory("electronics")).willReturn(products);

        List<Product> result = productService.findByCategory("electronics");

        assertThat(result).hasSize(2);
        assertThat(result).extracting("name").containsExactly("Laptop", "Phone");
    }

    @Test
    @DisplayName("findByCategory - returns empty list when no products in category")
    void findByCategory_noProducts_returnsEmptyList() {
        given(productRepository.findByCategory("furniture")).willReturn(List.of());

        List<Product> result = productService.findByCategory("furniture");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findActive - delegates to repository with ACTIVE status")
    void findActive_returnsActiveProducts() {
        List<Product> activeProducts = List.of(
                buildProduct("Laptop", "electronics", ProductStatus.ACTIVE)
        );
        given(productRepository.findByStatus(ProductStatus.ACTIVE)).willReturn(activeProducts);

        List<Product> result = productService.findActive();

        assertThat(result).hasSize(1);
        then(productRepository).should().findByStatus(ProductStatus.ACTIVE);
    }

    private Product buildProduct(String name, String category, ProductStatus status) {
        return Product.builder()
                .name(name)
                .description("A product")
                .price(BigDecimal.valueOf(99.99))
                .category(category)
                .brand("TestBrand")
                .stock(10)
                .status(status)
                .build();
    }
}