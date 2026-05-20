package com.agentcart.product.service;

import com.agentcart.product.domain.Product;
import com.agentcart.product.repository.ProductEmbeddingRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ProductEmbeddingService {

    private static final String MODEL = "bge-m3";

    @Value("${app.embedding.timeout-seconds:5}")
    private int timeoutSeconds;

    private final EmbeddingModel embeddingModel;
    private final ProductEmbeddingRepository embeddingRepository;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ProductEmbeddingService(
            @Autowired(required = false) @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel,
            @Autowired(required = false) ProductEmbeddingRepository embeddingRepository) {
        this.embeddingModel = embeddingModel;
        this.embeddingRepository = embeddingRepository;
    }

    public void createOrUpdate(Long productId, Product product) {
        if (embeddingModel == null || embeddingRepository == null) {
            log.debug("Embedding skipped for product {} — model={}, repo={}", productId, embeddingModel != null, embeddingRepository != null);
            return;
        }
        try {
            String text = buildText(product);
            float[] embedding = CompletableFuture
                    .supplyAsync(() -> embeddingModel.embed(text), executor)
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .join();
            embeddingRepository.upsertEmbedding(productId, embedding, MODEL);
        } catch (Exception e) {
            log.warn("Embedding generation failed for product {} — {}", productId, e.getMessage());
        }
    }

    private String buildText(Product product) {
        return String.format("카테고리: %s 브랜드: %s 상품명: %s",
                product.getCategory(),
                product.getBrand() != null ? product.getBrand() : "",
                product.getName());
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdown();
    }
}