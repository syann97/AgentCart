package com.agentcart.order.service;

import com.agentcart.cart.domain.CartItem;
import com.agentcart.cart.repository.CartItemRepository;
import com.agentcart.exception.AuthException;
import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.OrderException;
import com.agentcart.exception.ProductException;
import com.agentcart.member.domain.Member;
import com.agentcart.member.repository.MemberRepository;
import com.agentcart.order.domain.Order;
import com.agentcart.order.domain.OrderItem;
import com.agentcart.order.domain.OrderStatus;
import com.agentcart.order.repository.OrderRepository;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Order createFromCart(Long memberId, List<Long> cartItemIds,
                                String recipientName, String phone,
                                String address, String addressDetail) {
        Member member = findMember(memberId);
        List<CartItem> cartItems = cartItemRepository.findAllById(cartItemIds);

        if (cartItems.size() != cartItemIds.size()) {
            throw new OrderException(ErrorCode.ORDER_NOT_FOUND);
        }

        BigDecimal totalPrice = BigDecimal.ZERO;
        Order order = new Order(member, BigDecimal.ZERO, recipientName, phone, address, addressDetail);
        orderRepository.save(order);

        for (CartItem cartItem : cartItems) {
            Product product = productRepository.findByIdForUpdate(cartItem.getProduct().getId())
                    .orElseThrow(() -> new ProductException(ErrorCode.PRODUCT_NOT_FOUND));
            product.decreaseStock(cartItem.getQuantity());
            OrderItem item = new OrderItem(order, product, cartItem.getQuantity());
            order.addItem(item);
            totalPrice = totalPrice.add(item.getPriceAtOrder().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        order.updateTotalPrice(totalPrice);
        cartItemRepository.deleteAll(cartItems);
        return order;
    }

    @Transactional
    public Order createDirect(Long memberId, Long productId, int quantity,
                              String recipientName, String phone,
                              String address, String addressDetail) {
        Member member = findMember(memberId);
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ProductException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }

        product.decreaseStock(quantity);

        BigDecimal totalPrice = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        Order order = new Order(member, totalPrice, recipientName, phone, address, addressDetail);
        orderRepository.save(order);

        OrderItem item = new OrderItem(order, product, quantity);
        order.addItem(item);
        return order;
    }

    @Transactional
    public void cancel(Long memberId, Long orderId) {
        Order order = findOrder(orderId);
        checkAccess(memberId, order);

        if (!order.getStatus().isCancellable()) {
            throw new OrderException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }

        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ProductException(ErrorCode.PRODUCT_NOT_FOUND));
            product.restoreStock(item.getQuantity());
        }
        order.cancel();
    }

    @Transactional(readOnly = true)
    public Page<Order> getOrders(Long memberId, Pageable pageable) {
        Page<Order> page = orderRepository.findByMemberId(memberId, pageable);
        page.getContent().forEach(o -> o.getItems().size());
        return page;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long memberId, Long orderId) {
        Order order = findOrder(orderId);
        checkAccess(memberId, order);
        return order;
    }

    @Transactional
    public Order updateStatus(Long orderId, OrderStatus status) {
        Order order = findOrder(orderId);
        order.changeStatus(status);
        return order;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
    }

    private void checkAccess(Long memberId, Order order) {
        if (!order.getMember().getId().equals(memberId)) {
            throw new OrderException(ErrorCode.ORDER_ACCESS_DENIED);
        }
    }
}