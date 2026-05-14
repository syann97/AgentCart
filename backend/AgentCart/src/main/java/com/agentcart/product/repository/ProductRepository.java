package com.agentcart.product.repository;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategory(String category);
    List<Product> findByStatus(ProductStatus status);
}