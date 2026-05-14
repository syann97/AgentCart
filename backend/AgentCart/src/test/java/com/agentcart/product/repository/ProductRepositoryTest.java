package com.agentcart.product.repository;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

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
    @DisplayName("findByCategory - returns only products in the given category")
    void findByCategory_returnsMatchingProducts() {
        productRepository.save(buildProduct("Laptop", "electronics", ProductStatus.ACTIVE));
        productRepository.save(buildProduct("Phone", "electronics", ProductStatus.ACTIVE));
        productRepository.save(buildProduct("Shirt", "clothing", ProductStatus.ACTIVE));

        List<Product> result = productRepository.findByCategory("electronics");

        assertThat(result).hasSize(2);
        assertThat(result).extracting("name").containsExactlyInAnyOrder("Laptop", "Phone");
    }

    @Test
    @DisplayName("findByCategory - returns empty list when no products in category")
    void findByCategory_noMatch_returnsEmptyList() {
        productRepository.save(buildProduct("Laptop", "electronics", ProductStatus.ACTIVE));

        List<Product> result = productRepository.findByCategory("furniture");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByStatus - returns only products with the given status")
    void findByStatus_returnsMatchingProducts() {
        productRepository.save(buildProduct("Laptop", "electronics", ProductStatus.ACTIVE));
        productRepository.save(buildProduct("Old Phone", "electronics", ProductStatus.INACTIVE));
        productRepository.save(buildProduct("Watch", "accessories", ProductStatus.SOLD_OUT));

        List<Product> result = productRepository.findByStatus(ProductStatus.ACTIVE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("Save - createdAt and updatedAt are populated automatically")
    void save_timestampsAreSet() {
        Product saved = productRepository.save(buildProduct("Laptop", "electronics", ProductStatus.ACTIVE));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    private Product buildProduct(String name, String category, ProductStatus status) {
        return Product.builder()
                .name(name)
                .description("Description")
                .price(BigDecimal.valueOf(99.99))
                .category(category)
                .brand("Brand")
                .stock(5)
                .status(status)
                .build();
    }
}