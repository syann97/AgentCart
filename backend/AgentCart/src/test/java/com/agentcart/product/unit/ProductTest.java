package com.agentcart.product.unit;

import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.ProductException;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class ProductTest {

    @Test
    void activate_inactiveProduct_changesStatusToActive() {
        Product product = buildProduct(ProductStatus.INACTIVE, 10);
        product.activate();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void deactivate_activeProduct_changesStatusToInactive() {
        Product product = buildProduct(ProductStatus.ACTIVE, 10);
        product.deactivate();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void decreaseStock_sufficientStock_reducesQuantity() {
        Product product = buildProduct(ProductStatus.ACTIVE, 10);
        product.decreaseStock(3);
        assertThat(product.getStock()).isEqualTo(7);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void decreaseStock_stockReachesZero_changesStatusToSoldOut() {
        Product product = buildProduct(ProductStatus.ACTIVE, 5);
        product.decreaseStock(5);
        assertThat(product.getStock()).isEqualTo(0);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    void decreaseStock_insufficientStock_throwsException() {
        Product product = buildProduct(ProductStatus.ACTIVE, 2);

        assertThatThrownBy(() -> product.decreaseStock(5))
                .isInstanceOf(ProductException.class)
                .satisfies(ex -> assertThat(((ProductException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
    }

    private Product buildProduct(ProductStatus status, int stock) {
        return Product.builder()
                .name("Test Product")
                .description("Description")
                .price(BigDecimal.valueOf(99.99))
                .category("electronics")
                .brand("Brand")
                .stock(stock)
                .status(status)
                .build();
    }
}