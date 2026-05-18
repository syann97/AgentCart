package com.agentcart.payment.domain;

import com.agentcart.common.BaseTimeEntity;
import com.agentcart.order.domain.Order;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 100)
    private String paymentKey;

    @Column
    private LocalDateTime paidAt;

    public Payment(Order order, BigDecimal amount) {
        this.order = order;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public void complete(String paymentKey) {
        this.status = PaymentStatus.COMPLETED;
        this.paymentKey = paymentKey;
        this.paidAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }
}