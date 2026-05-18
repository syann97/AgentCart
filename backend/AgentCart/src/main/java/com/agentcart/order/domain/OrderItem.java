package com.agentcart.order.domain;

import com.agentcart.common.BaseTimeEntity;
import com.agentcart.product.domain.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor
public class OrderItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtOrder;

    @Column(nullable = false)
    private int quantity;

    public OrderItem(Order order, Product product, int quantity) {
        this.order = order;
        this.product = product;
        this.productName = product.getName();
        this.priceAtOrder = product.getPrice();
        this.quantity = quantity;
    }
}