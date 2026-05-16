package com.agentcart.cart.unit;

import com.agentcart.cart.domain.Cart;
import com.agentcart.cart.domain.CartItem;
import com.agentcart.cart.repository.CartItemRepository;
import com.agentcart.cart.repository.CartRepository;
import com.agentcart.cart.service.CartService;
import com.agentcart.exception.CartException;
import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.ProductException;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private MemberRepository memberRepository;

    @InjectMocks
    private CartService cartService;

    private static final Long MEMBER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long CART_ID = 100L;
    private static final Long CART_ITEM_ID = 200L;

    private Member member;
    private Cart cart;
    private Product activeProduct;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .email("test@test.com").password("pass").name("Test").nickname("nick").role(Role.MEMBER)
                .build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);

        cart = new Cart(member);
        ReflectionTestUtils.setField(cart, "id", CART_ID);

        activeProduct = buildProduct(ProductStatus.ACTIVE, 10);
    }

    // ── getOrCreateCart ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrCreateCart - returns existing cart when found")
    void getOrCreateCart_existing_returnsCart() {
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));

        Cart result = cartService.getOrCreateCart(MEMBER_ID);

        assertThat(result.getId()).isEqualTo(CART_ID);
        then(cartRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("getOrCreateCart - creates and saves new cart when not found")
    void getOrCreateCart_notFound_createsNewCart() {
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(cartRepository.save(any(Cart.class))).willAnswer(inv -> inv.getArgument(0));

        Cart result = cartService.getOrCreateCart(MEMBER_ID);

        assertThat(result.getMember()).isEqualTo(member);
        then(cartRepository).should().save(any(Cart.class));
    }

    // ── addItem ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addItem - saves new CartItem when product not already in cart")
    void addItem_newProduct_savesCartItem() {
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(activeProduct));
        given(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).willReturn(Optional.empty());
        given(cartItemRepository.save(any(CartItem.class))).willAnswer(inv -> inv.getArgument(0));

        CartItem result = cartService.addItem(MEMBER_ID, PRODUCT_ID, 2);

        assertThat(result.getQuantity()).isEqualTo(2);
        then(cartItemRepository).should().save(any(CartItem.class));
    }

    @Test
    @DisplayName("addItem - accumulates quantity when product already exists in cart")
    void addItem_existingProduct_accumulatesQuantity() {
        CartItem existing = new CartItem(cart, activeProduct, 3);
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(activeProduct));
        given(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).willReturn(Optional.of(existing));

        CartItem result = cartService.addItem(MEMBER_ID, PRODUCT_ID, 2);

        assertThat(result.getQuantity()).isEqualTo(5);
        then(cartItemRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("addItem - throws PRODUCT_NOT_FOUND when product does not exist")
    void addItem_productNotFound_throwsException() {
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, PRODUCT_ID, 1))
                .isInstanceOf(ProductException.class)
                .satisfies(e -> assertThat(((ProductException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("addItem - throws PRODUCT_NOT_AVAILABLE for SOLD_OUT product")
    void addItem_soldOutProduct_throwsException() {
        Product soldOut = buildProduct(ProductStatus.SOLD_OUT, 0);
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(soldOut));

        assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, PRODUCT_ID, 1))
                .isInstanceOf(CartException.class)
                .satisfies(e -> assertThat(((CartException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PRODUCT_NOT_AVAILABLE));
    }

    @Test
    @DisplayName("addItem - throws PRODUCT_NOT_AVAILABLE for INACTIVE product")
    void addItem_inactiveProduct_throwsException() {
        Product inactive = buildProduct(ProductStatus.INACTIVE, 5);
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, PRODUCT_ID, 1))
                .isInstanceOf(CartException.class)
                .satisfies(e -> assertThat(((CartException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PRODUCT_NOT_AVAILABLE));
    }

    @Test
    @DisplayName("addItem - throws EXCEEDS_STOCK when quantity exceeds stock (new item)")
    void addItem_exceedsStock_throwsException() {
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(activeProduct));
        given(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, PRODUCT_ID, 11))
                .isInstanceOf(CartException.class)
                .satisfies(e -> assertThat(((CartException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EXCEEDS_STOCK));
    }

    @Test
    @DisplayName("addItem - throws EXCEEDS_STOCK when accumulated quantity exceeds stock")
    void addItem_accumulatedExceedsStock_throwsException() {
        CartItem existing = new CartItem(cart, activeProduct, 8);
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(activeProduct));
        given(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, PRODUCT_ID, 3))
                .isInstanceOf(CartException.class)
                .satisfies(e -> assertThat(((CartException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EXCEEDS_STOCK));
    }

    // ── updateItemQuantity ─────────────────────────────────────────────────────

    @Test
    @DisplayName("updateItemQuantity - updates quantity when item belongs to member's cart")
    void updateItemQuantity_validRequest_updatesQuantity() {
        CartItem item = buildCartItem(cart, activeProduct, 2);
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.of(item));

        CartItem result = cartService.updateItemQuantity(MEMBER_ID, CART_ITEM_ID, 5);

        assertThat(result.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("updateItemQuantity - throws CART_ITEM_NOT_FOUND when item does not exist")
    void updateItemQuantity_itemNotFound_throwsException() {
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItemQuantity(MEMBER_ID, CART_ITEM_ID, 3))
                .isInstanceOf(CartException.class)
                .satisfies(e -> assertThat(((CartException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    @Test
    @DisplayName("updateItemQuantity - throws CART_ITEM_NOT_FOUND when item belongs to another member's cart")
    void updateItemQuantity_itemOwnedByOther_throwsException() {
        Cart otherCart = new Cart(member);
        ReflectionTestUtils.setField(otherCart, "id", 999L);
        CartItem item = buildCartItem(otherCart, activeProduct, 2);

        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.updateItemQuantity(MEMBER_ID, CART_ITEM_ID, 3))
                .isInstanceOf(CartException.class)
                .satisfies(e -> assertThat(((CartException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    @Test
    @DisplayName("updateItemQuantity - throws EXCEEDS_STOCK when quantity exceeds stock")
    void updateItemQuantity_exceedsStock_throwsException() {
        CartItem item = buildCartItem(cart, activeProduct, 2);
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.updateItemQuantity(MEMBER_ID, CART_ITEM_ID, 11))
                .isInstanceOf(CartException.class)
                .satisfies(e -> assertThat(((CartException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EXCEEDS_STOCK));
    }

    // ── removeItem ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeItem - removes item from cart when item belongs to member's cart")
    void removeItem_validRequest_removesItem() {
        CartItem item = buildCartItem(cart, activeProduct, 2);
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.of(item));

        cartService.removeItem(MEMBER_ID, CART_ITEM_ID);

        assertThat(cart.getItems()).doesNotContain(item);
    }

    @Test
    @DisplayName("removeItem - throws CART_ITEM_NOT_FOUND when item does not exist")
    void removeItem_itemNotFound_throwsException() {
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(MEMBER_ID, CART_ITEM_ID))
                .isInstanceOf(CartException.class)
                .satisfies(e -> assertThat(((CartException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    @Test
    @DisplayName("removeItem - throws CART_ITEM_NOT_FOUND when item belongs to another member's cart")
    void removeItem_itemOwnedByOther_throwsException() {
        Cart otherCart = new Cart(member);
        ReflectionTestUtils.setField(otherCart, "id", 999L);
        CartItem item = buildCartItem(otherCart, activeProduct, 2);

        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.removeItem(MEMBER_ID, CART_ITEM_ID))
                .isInstanceOf(CartException.class)
                .satisfies(e -> assertThat(((CartException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    // ── clearCart ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clearCart - clears all items from cart")
    void clearCart_validRequest_clearsItems() {
        CartItem item1 = buildCartItem(cart, activeProduct, 1);
        cart.getItems().add(item1);
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));

        cartService.clearCart(MEMBER_ID);

        assertThat(cart.getItems()).isEmpty();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Product buildProduct(ProductStatus status, int stock) {
        Product product = Product.builder()
                .name("Product")
                .description("Desc")
                .price(BigDecimal.valueOf(10000))
                .category("electronics")
                .brand("Brand")
                .stock(stock)
                .status(status)
                .build();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        return product;
    }

    private CartItem buildCartItem(Cart cart, Product product, int quantity) {
        CartItem item = new CartItem(cart, product, quantity);
        ReflectionTestUtils.setField(item, "id", CART_ITEM_ID);
        return item;
    }
}