package com.agentcart.order.unit;

import com.agentcart.cart.domain.Cart;
import com.agentcart.cart.domain.CartItem;
import com.agentcart.cart.repository.CartItemRepository;
import com.agentcart.exception.AuthException;
import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.OrderException;
import com.agentcart.exception.ProductException;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
import com.agentcart.order.domain.Order;
import com.agentcart.order.domain.OrderItem;
import com.agentcart.order.domain.OrderStatus;
import com.agentcart.order.repository.OrderRepository;
import com.agentcart.order.service.InventoryLockService;
import com.agentcart.order.service.OrderService;
import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import com.agentcart.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private InventoryLockService inventoryLockService;

    @InjectMocks
    private OrderService orderService;

    private static final Long MEMBER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long ORDER_ID = 100L;
    private static final Long CART_ITEM_ID = 200L;

    private static final String RECIPIENT = "홍길동";
    private static final String PHONE = "010-1234-5678";
    private static final String ADDRESS = "서울시 강남구";
    private static final String ADDRESS_DETAIL = "101호";

    private Member member;
    private Product product;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .email("test@test.com").password("pass").name("Test").nickname("nick").role(Role.MEMBER)
                .build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);

        product = Product.builder()
                .name("상품A").description("설명").price(BigDecimal.valueOf(10000))
                .category("electronics").brand("BrandA").stock(10).status(ProductStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);

        lenient().when(inventoryLockService.withLocks(anyList(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
    }

    // ── createDirect ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("createDirect - creates order and decreases stock")
    void createDirect_validRequest_createsOrder() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(productRepository.findByIdForUpdate(PRODUCT_ID)).willReturn(Optional.of(product));
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        Order result = orderService.createDirect(MEMBER_ID, PRODUCT_ID, 3, RECIPIENT, PHONE, ADDRESS, ADDRESS_DETAIL);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("createDirect - throws MEMBER_NOT_FOUND when member does not exist")
    void createDirect_memberNotFound_throwsException() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createDirect(MEMBER_ID, PRODUCT_ID, 1, RECIPIENT, PHONE, ADDRESS, null))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    @DisplayName("createDirect - throws PRODUCT_NOT_FOUND when product does not exist")
    void createDirect_productNotFound_throwsException() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(productRepository.findByIdForUpdate(PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createDirect(MEMBER_ID, PRODUCT_ID, 1, RECIPIENT, PHONE, ADDRESS, null))
                .isInstanceOf(ProductException.class)
                .satisfies(e -> assertThat(((ProductException) e).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("createDirect - throws INSUFFICIENT_STOCK when quantity exceeds stock")
    void createDirect_insufficientStock_throwsException() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(productRepository.findByIdForUpdate(PRODUCT_ID)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.createDirect(MEMBER_ID, PRODUCT_ID, 11, RECIPIENT, PHONE, ADDRESS, null))
                .isInstanceOf(ProductException.class)
                .satisfies(e -> assertThat(((ProductException) e).getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
    }

    // ── createFromCart ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("createFromCart - creates order from cart items and removes them")
    void createFromCart_validRequest_createsOrderAndRemovesCartItems() {
        Cart cart = new Cart(member);
        ReflectionTestUtils.setField(cart, "id", 50L);
        CartItem cartItem = new CartItem(cart, product, 2);
        ReflectionTestUtils.setField(cartItem, "id", CART_ITEM_ID);

        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(cartItemRepository.findAllById(List.of(CART_ITEM_ID))).willReturn(List.of(cartItem));
        given(productRepository.findByIdForUpdate(PRODUCT_ID)).willReturn(Optional.of(product));
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        Order result = orderService.createFromCart(MEMBER_ID, List.of(CART_ITEM_ID), RECIPIENT, PHONE, ADDRESS, ADDRESS_DETAIL);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(product.getStock()).isEqualTo(8);
        then(cartItemRepository).should().deleteAll(List.of(cartItem));
    }

    @Test
    @DisplayName("createFromCart - throws ORDER_NOT_FOUND when some cart items are missing")
    void createFromCart_missingCartItems_throwsException() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(cartItemRepository.findAllById(List.of(CART_ITEM_ID, 999L))).willReturn(List.of());

        assertThatThrownBy(() -> orderService.createFromCart(MEMBER_ID, List.of(CART_ITEM_ID, 999L), RECIPIENT, PHONE, ADDRESS, null))
                .isInstanceOf(OrderException.class)
                .satisfies(e -> assertThat(((OrderException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    // ── cancel ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancel - cancels order and restores stock")
    void cancel_pendingOrder_cancelsAndRestoresStock() {
        Order order = buildOrder(OrderStatus.PENDING);
        OrderItem item = buildOrderItem(order, product, 3);
        order.addItem(item);

        given(orderRepository.findByIdWithItems(ORDER_ID)).willReturn(Optional.of(order));
        given(productRepository.findByIdForUpdate(PRODUCT_ID)).willReturn(Optional.of(product));

        orderService.cancel(MEMBER_ID, ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStock()).isEqualTo(13);
    }

    @Test
    @DisplayName("cancel - throws ORDER_NOT_CANCELLABLE when order is SHIPPED")
    void cancel_shippedOrder_throwsException() {
        Order order = buildOrder(OrderStatus.SHIPPED);
        given(orderRepository.findByIdWithItems(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(MEMBER_ID, ORDER_ID))
                .isInstanceOf(OrderException.class)
                .satisfies(e -> assertThat(((OrderException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_CANCELLABLE));
    }

    @Test
    @DisplayName("cancel - throws ORDER_ACCESS_DENIED when order belongs to another member")
    void cancel_otherMembersOrder_throwsException() {
        Order order = buildOrder(OrderStatus.PENDING);
        given(orderRepository.findByIdWithItems(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(999L, ORDER_ID))
                .isInstanceOf(OrderException.class)
                .satisfies(e -> assertThat(((OrderException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
    }

    @Test
    @DisplayName("cancel - throws ORDER_NOT_FOUND when order does not exist")
    void cancel_orderNotFound_throwsException() {
        given(orderRepository.findByIdWithItems(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancel(MEMBER_ID, ORDER_ID))
                .isInstanceOf(OrderException.class)
                .satisfies(e -> assertThat(((OrderException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    // ── getOrders ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrders - returns paged orders for member")
    void getOrders_validMember_returnsPage() {
        Order order = buildOrder(OrderStatus.PENDING);
        PageRequest pageable = PageRequest.of(0, 10);
        given(orderRepository.findByMemberId(MEMBER_ID, pageable))
                .willReturn(new PageImpl<>(List.of(order)));

        Page<Order> result = orderService.getOrders(MEMBER_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    // ── getOrder ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrder - returns order when it belongs to member")
    void getOrder_ownOrder_returnsOrder() {
        Order order = buildOrder(OrderStatus.PENDING);
        given(orderRepository.findByIdWithItems(ORDER_ID)).willReturn(Optional.of(order));

        Order result = orderService.getOrder(MEMBER_ID, ORDER_ID);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("getOrder - throws ORDER_ACCESS_DENIED when order belongs to another member")
    void getOrder_otherMembersOrder_throwsException() {
        Order order = buildOrder(OrderStatus.PENDING);
        given(orderRepository.findByIdWithItems(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(999L, ORDER_ID))
                .isInstanceOf(OrderException.class)
                .satisfies(e -> assertThat(((OrderException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Order buildOrder(OrderStatus status) {
        Order order = new Order(member, BigDecimal.valueOf(10000), RECIPIENT, PHONE, ADDRESS, ADDRESS_DETAIL);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "status", status);
        return order;
    }

    private OrderItem buildOrderItem(Order order, Product product, int quantity) {
        OrderItem item = new OrderItem(order, product, quantity);
        ReflectionTestUtils.setField(item, "id", 300L);
        return item;
    }
}