package com.agentcart.cart.service;

import com.agentcart.cart.domain.Cart;
import com.agentcart.cart.domain.CartItem;
import com.agentcart.cart.repository.CartItemRepository;
import com.agentcart.cart.repository.CartRepository;
import com.agentcart.exception.AuthException;
import com.agentcart.exception.CartException;
import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.ProductException;
import com.agentcart.member.domain.Member;
import com.agentcart.member.repository.MemberRepository;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Cart getCartWithItems(Long memberId) {
        return cartRepository.findByMemberIdWithItems(memberId)
                .orElseGet(() -> getOrCreateCart(memberId));
    }

    @Transactional
    public Cart getOrCreateCart(Long memberId) {
        return cartRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    Member member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new AuthException(ErrorCode.MEMBER_NOT_FOUND));
                    return cartRepository.save(new Cart(member));
                });
    }

    @Transactional
    public CartItem addItem(Long memberId, Long productId, int quantity) {
        Cart cart = getOrCreateCart(memberId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new CartException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }

        Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQty = item.getQuantity() + quantity;
            if (newQty > product.getStock()) {
                throw new CartException(ErrorCode.EXCEEDS_STOCK);
            }
            item.updateQuantity(newQty);
            return item;
        }

        if (quantity > product.getStock()) {
            throw new CartException(ErrorCode.EXCEEDS_STOCK);
        }
        return cartItemRepository.save(new CartItem(cart, product, quantity));
    }

    @Transactional
    public CartItem updateItemQuantity(Long memberId, Long cartItemId, int quantity) {
        Cart cart = getOrCreateCart(memberId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new CartException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        if (quantity > item.getProduct().getStock()) {
            throw new CartException(ErrorCode.EXCEEDS_STOCK);
        }

        item.updateQuantity(quantity);
        return item;
    }

    @Transactional
    public void removeItem(Long memberId, Long cartItemId) {
        Cart cart = getOrCreateCart(memberId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new CartException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        cart.removeItem(cartItemId);
    }

    @Transactional
    public void clearCart(Long memberId) {
        Cart cart = getOrCreateCart(memberId);
        cart.clear();
    }
}