package com.agentcart.product.repository;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("findByCategory - returns page of products in given category")
    void findByCategory_withPageable_returnsPage() {
        productRepository.save(buildProduct("Laptop", "electronics", ProductStatus.ACTIVE, "Samsung"));
        productRepository.save(buildProduct("Phone", "electronics", ProductStatus.ACTIVE, "Apple"));
        productRepository.save(buildProduct("Shirt", "clothing", ProductStatus.ACTIVE, "Nike"));

        Page<Product> result = productRepository.findByCategory("electronics", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting("name").containsExactlyInAnyOrder("Laptop", "Phone");
    }

    @Test
    @DisplayName("findByCategory - returns empty page when no products in category")
    void findByCategory_noMatch_returnsEmptyPage() {
        productRepository.save(buildProduct("Laptop", "electronics", ProductStatus.ACTIVE, "Samsung"));

        Page<Product> result = productRepository.findByCategory("furniture", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("findByStatus - returns page of products with given status")
    void findByStatus_withPageable_returnsPage() {
        productRepository.save(buildProduct("Laptop", "electronics", ProductStatus.ACTIVE, "Samsung"));
        productRepository.save(buildProduct("Old Phone", "electronics", ProductStatus.INACTIVE, "Nokia"));
        productRepository.save(buildProduct("Watch", "accessories", ProductStatus.SOLD_OUT, "Casio"));

        Page<Product> result = productRepository.findByStatus(ProductStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("existsByNameAndBrand - returns true when combination exists")
    void existsByNameAndBrand_existingCombination_returnsTrue() {
        productRepository.save(buildProduct("Laptop", "electronics", ProductStatus.ACTIVE, "Samsung"));

        assertThat(productRepository.existsByNameAndBrand("Laptop", "Samsung")).isTrue();
    }

    @Test
    @DisplayName("existsByNameAndBrand - returns false for different name or brand")
    void existsByNameAndBrand_unknownCombination_returnsFalse() {
        productRepository.save(buildProduct("Laptop", "electronics", ProductStatus.ACTIVE, "Samsung"));

        assertThat(productRepository.existsByNameAndBrand("Laptop", "Apple")).isFalse();
        assertThat(productRepository.existsByNameAndBrand("Phone", "Samsung")).isFalse();
    }

    @Test
    @DisplayName("Save - createdAt and updatedAt are populated automatically")
    void save_timestampsAreSet() {
        Product saved = productRepository.save(buildProduct("Laptop", "electronics", ProductStatus.ACTIVE, "Samsung"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    private Product buildProduct(String name, String category, ProductStatus status, String brand) {
        return Product.builder()
                .name(name)
                .description("Description")
                .price(BigDecimal.valueOf(99.99))
                .category(category)
                .brand(brand)
                .stock(5)
                .status(status)
                .build();
    }
}