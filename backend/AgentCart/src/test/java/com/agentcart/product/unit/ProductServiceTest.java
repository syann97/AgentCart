package com.agentcart.product.unit;

import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.ProductException;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.dto.ProductCreateRequest;
import com.agentcart.product.dto.ProductUpdateRequest;
import com.agentcart.product.repository.ProductRepository;
import com.agentcart.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register - saves and returns product when name+brand combination is unique")
    void register_validRequest_savesProduct() {
        ProductCreateRequest request = buildCreateRequest("Laptop", "Samsung");
        given(productRepository.existsByNameAndBrand("Laptop", "Samsung")).willReturn(false);
        given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        Product result = productService.register(request);

        assertThat(result.getName()).isEqualTo("Laptop");
        assertThat(result.getBrand()).isEqualTo("Samsung");
        assertThat(result.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("register - throws DUPLICATE_PRODUCT when name+brand already exists")
    void register_duplicateNameAndBrand_throwsException() {
        ProductCreateRequest request = buildCreateRequest("Laptop", "Samsung");
        given(productRepository.existsByNameAndBrand("Laptop", "Samsung")).willReturn(true);

        assertThatThrownBy(() -> productService.register(request))
                .isInstanceOf(ProductException.class)
                .satisfies(ex -> assertThat(((ProductException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_PRODUCT));

        then(productRepository).should(never()).save(any());
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById - returns product when found")
    void findById_existingProduct_returnsProduct() {
        Product product = buildProduct("Laptop", "electronics", ProductStatus.ACTIVE);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        Product result = productService.findById(1L);

        assertThat(result.getName()).isEqualTo("Laptop");
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

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll - delegates to repository with pageable")
    void findAll_returnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(
                buildProduct("Laptop", "electronics", ProductStatus.ACTIVE),
                buildProduct("Phone", "electronics", ProductStatus.ACTIVE)
        ));
        given(productRepository.findAll(pageable)).willReturn(page);

        Page<Product> result = productService.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        then(productRepository).should().findAll(pageable);
    }

    // ── findByCategory ────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByCategory - returns page of products in category")
    void findByCategory_existingCategory_returnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(buildProduct("Laptop", "electronics", ProductStatus.ACTIVE)));
        given(productRepository.findByCategory("electronics", pageable)).willReturn(page);

        Page<Product> result = productService.findByCategory("electronics", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCategory()).isEqualTo("electronics");
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update - updates and returns product when found")
    void update_existingProduct_updatesFields() {
        Product product = buildProduct("Old Name", "electronics", ProductStatus.ACTIVE);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        ProductUpdateRequest request = buildUpdateRequest("New Name", "clothing");
        Product result = productService.update(1L, request);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getCategory()).isEqualTo("clothing");
    }

    @Test
    @DisplayName("update - throws PRODUCT_NOT_FOUND when product does not exist")
    void update_notFound_throwsException() {
        given(productRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, buildUpdateRequest("Name", "category")))
                .isInstanceOf(ProductException.class)
                .satisfies(ex -> assertThat(((ProductException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete - deletes product when found")
    void delete_existingProduct_deletesProduct() {
        Product product = buildProduct("Laptop", "electronics", ProductStatus.ACTIVE);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        productService.delete(1L);

        then(productRepository).should().delete(product);
    }

    @Test
    @DisplayName("delete - throws PRODUCT_NOT_FOUND when product does not exist")
    void delete_notFound_throwsException() {
        given(productRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductException.class)
                .satisfies(ex -> assertThat(((ProductException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Product buildProduct(String name, String category, ProductStatus status) {
        return Product.builder()
                .name(name)
                .description("Description")
                .price(BigDecimal.valueOf(99.99))
                .category(category)
                .brand("Brand")
                .stock(10)
                .status(status)
                .build();
    }

    private ProductCreateRequest buildCreateRequest(String name, String brand) {
        ProductCreateRequest request = new ProductCreateRequest();
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "description", "A product");
        ReflectionTestUtils.setField(request, "price", BigDecimal.valueOf(99.99));
        ReflectionTestUtils.setField(request, "category", "electronics");
        ReflectionTestUtils.setField(request, "brand", brand);
        ReflectionTestUtils.setField(request, "stock", 10);
        return request;
    }

    private ProductUpdateRequest buildUpdateRequest(String name, String category) {
        ProductUpdateRequest request = new ProductUpdateRequest();
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "description", "Updated description");
        ReflectionTestUtils.setField(request, "price", BigDecimal.valueOf(149.99));
        ReflectionTestUtils.setField(request, "category", category);
        ReflectionTestUtils.setField(request, "brand", "Brand");
        ReflectionTestUtils.setField(request, "stock", 5);
        return request;
    }
}