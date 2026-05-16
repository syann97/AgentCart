package com.agentcart.cart.domain;

import com.agentcart.common.BaseTimeEntity;
import com.agentcart.member.domain.Member;
import com.agentcart.product.domain.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carts")
@Getter
@NoArgsConstructor
public class Cart extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItem> items = new ArrayList<>();

    public Cart(Member member) {
        this.member = member;
    }

    public CartItem addItem(Product product, int qty) {
        CartItem newItem = new CartItem(this, product, qty);
        items.add(newItem);
        return newItem;
    }

    public void removeItem(Long cartItemId) {
        items.removeIf(item -> item.getId().equals(cartItemId));
    }

    public void clear() {
        items.clear();
    }
}