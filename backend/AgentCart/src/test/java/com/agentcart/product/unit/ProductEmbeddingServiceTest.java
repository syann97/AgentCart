package com.agentcart.product.unit;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductEmbeddingRepository;
import com.agentcart.product.service.ProductEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProductEmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private ProductEmbeddingRepository embeddingRepository;

    private ProductEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new ProductEmbeddingService(embeddingModel, embeddingRepository);
        ReflectionTestUtils.setField(embeddingService, "timeoutSeconds", 5);
    }

    @Test
    @DisplayName("createOrUpdate - saves embedding when model returns successfully")
    void createOrUpdate_success_savesEmbedding() {
        float[] vector = {0.1f, 0.2f, 0.3f};
        given(embeddingModel.embed(anyString())).willReturn(vector);

        embeddingService.createOrUpdate(1L, buildProduct());

        then(embeddingRepository).should().upsertEmbedding(eq(1L), same(vector), eq("bge-m3"));
    }

    @Test
    @DisplayName("createOrUpdate - does not throw when embedding times out")
    void createOrUpdate_timeout_doesNotThrow() throws InterruptedException {
        CountDownLatch block = new CountDownLatch(1);
        given(embeddingModel.embed(anyString())).willAnswer(inv -> {
            block.await(10, TimeUnit.SECONDS);
            return new float[0];
        });
        ReflectionTestUtils.setField(embeddingService, "timeoutSeconds", 1);

        assertThatCode(() -> embeddingService.createOrUpdate(1L, buildProduct()))
                .doesNotThrowAnyException();

        block.countDown();
        then(embeddingRepository).should(never()).upsertEmbedding(any(), any(), any());
    }

    @Test
    @DisplayName("createOrUpdate - does not throw when model throws exception")
    void createOrUpdate_modelThrows_doesNotThrow() {
        given(embeddingModel.embed(anyString())).willThrow(new RuntimeException("API error"));

        assertThatCode(() -> embeddingService.createOrUpdate(1L, buildProduct()))
                .doesNotThrowAnyException();

        then(embeddingRepository).should(never()).upsertEmbedding(any(), any(), any());
    }

    @Test
    @DisplayName("createOrUpdate - skips model call when repository is null")
    void createOrUpdate_repositoryNull_skipsEmbeddingAndModelCall() {
        embeddingService = new ProductEmbeddingService(embeddingModel, null);
        ReflectionTestUtils.setField(embeddingService, "timeoutSeconds", 5);

        embeddingService.createOrUpdate(1L, buildProduct());

        then(embeddingModel).should(never()).embed(anyString());
    }

    private Product buildProduct() {
        return Product.builder()
                .name("Laptop").description("A laptop")
                .price(BigDecimal.valueOf(99.99)).category("electronics")
                .brand("Samsung").stock(10).status(ProductStatus.ACTIVE)
                .build();
    }
}
