package com.agentcart.product.controller;

import com.agentcart.common.ApiResponse;
import com.agentcart.common.PageResponse;
import com.agentcart.product.dto.ProductCreateRequest;
import com.agentcart.product.dto.ProductResponse;
import com.agentcart.product.dto.ProductSummaryResponse;
import com.agentcart.product.dto.ProductUpdateRequest;
import com.agentcart.product.dto.StockAdjustRequest;
import com.agentcart.product.dto.StockAdjustResponse;
import com.agentcart.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> getProducts(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductSummaryResponse> page = search != null && !search.isBlank()
                ? productService.search(search, pageable).map(ProductSummaryResponse::from)
                : productService.findAll(pageable).map(ProductSummaryResponse::from);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(ProductResponse.from(productService.findById(id))));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ProductResponse.from(productService.register(request))));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(ProductResponse.from(productService.update(id, request))));
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StockAdjustResponse>> adjustStock(
            @PathVariable Long id, @Valid @RequestBody StockAdjustRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                StockAdjustResponse.from(productService.adjustStock(id, request.delta()))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}