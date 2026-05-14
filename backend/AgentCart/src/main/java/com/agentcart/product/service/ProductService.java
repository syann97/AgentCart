package com.agentcart.product.service;

import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.ProductException;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.dto.ProductCreateRequest;
import com.agentcart.product.dto.ProductUpdateRequest;
import com.agentcart.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Autowired(required = false)
    private ProductEmbeddingService embeddingService;

    @Transactional
    public Product register(ProductCreateRequest request) {
        if (productRepository.existsByNameAndBrand(request.getName(), request.getBrand())) {
            throw new ProductException(ErrorCode.DUPLICATE_PRODUCT);
        }
        Product saved = productRepository.save(Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .brand(request.getBrand())
                .stock(request.getStock())
                .status(ProductStatus.ACTIVE)
                .build());
        if (embeddingService != null) {
            embeddingService.createOrUpdate(saved.getId(), saved);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> findByCategory(String category, Pageable pageable) {
        return productRepository.findByCategory(category, pageable);
    }

    @Transactional
    public Product update(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductException(ErrorCode.PRODUCT_NOT_FOUND));
        product.update(request.getName(), request.getDescription(), request.getPrice(),
                request.getCategory(), request.getBrand(), request.getStock());
        if (embeddingService != null) {
            embeddingService.createOrUpdate(product.getId(), product);
        }
        return product;
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductException(ErrorCode.PRODUCT_NOT_FOUND));
        productRepository.delete(product);
    }
}