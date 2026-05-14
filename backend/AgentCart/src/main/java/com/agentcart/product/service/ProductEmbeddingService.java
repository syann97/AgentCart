package com.agentcart.product.service;

import com.agentcart.product.domain.Product;
import com.agentcart.product.repository.ProductEmbeddingRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnBean(EmbeddingModel.class)
public class ProductEmbeddingService {

    private static final String MODEL = "text-embedding-3-small";

    @Value("${app.embedding.timeout-seconds:5}")
    private int timeoutSeconds;

    private final EmbeddingModel embeddingModel;
    private final ProductEmbeddingRepository embeddingRepository;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ProductEmbeddingService(EmbeddingModel embeddingModel,
                                   @Autowired(required = false) ProductEmbeddingRepository embeddingRepository) {
        this.embeddingModel = embeddingModel;
        this.embeddingRepository = embeddingRepository;
    }

    public void createOrUpdate(Long productId, Product product) {
        if (embeddingRepository == null) {
            log.debug("ProductEmbeddingRepository not available — skipping embedding for product {}", productId);
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
        return String.join(" ",
                product.getName(),
                product.getDescription() != null ? product.getDescription() : "",
                product.getCategory(),
                product.getBrand() != null ? product.getBrand() : ""
        ).trim();
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdown();
    }
}