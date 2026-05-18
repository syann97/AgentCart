package com.agentcart.order.domain;

import com.agentcart.common.BaseTimeEntity;
import com.agentcart.member.domain.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false, length = 100)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column
    private String addressDetail;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    public Order(Member member, BigDecimal totalPrice,
                 String recipientName, String phone, String address, String addressDetail) {
        this.member = member;
        this.status = OrderStatus.PENDING;
        this.totalPrice = totalPrice;
        this.recipientName = recipientName;
        this.phone = phone;
        this.address = address;
        this.addressDetail = addressDetail;
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    public void updateTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }
}
