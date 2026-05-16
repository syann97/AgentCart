package com.agentcart.cart.controller;

import com.agentcart.cart.domain.Cart;
import com.agentcart.cart.domain.CartItem;
import com.agentcart.cart.dto.CartItemAddRequest;
import com.agentcart.cart.dto.CartItemResponse;
import com.agentcart.cart.dto.CartItemUpdateRequest;
import com.agentcart.cart.dto.CartResponse;
import com.agentcart.cart.service.CartService;
import com.agentcart.common.ApiResponse;
import com.agentcart.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final MemberService memberService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(Principal principal) {
        Cart cart = cartService.getCartWithItems(getMemberId(principal));
        return ResponseEntity.ok(ApiResponse.ok(CartResponse.from(cart)));
    }

    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartItemResponse>> addItem(
            Principal principal, @Valid @RequestBody CartItemAddRequest request) {
        CartItem item = cartService.addItem(getMemberId(principal), request.getProductId(), request.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(CartItemResponse.from(item)));
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartItemResponse>> updateItem(
            Principal principal, @PathVariable Long itemId,
            @Valid @RequestBody CartItemUpdateRequest request) {
        CartItem item = cartService.updateItemQuantity(getMemberId(principal), itemId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.ok(CartItemResponse.from(item)));
    }

    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> removeItem(Principal principal, @PathVariable Long itemId) {
        cartService.removeItem(getMemberId(principal), itemId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> clearCart(Principal principal) {
        cartService.clearCart(getMemberId(principal));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private Long getMemberId(Principal principal) {
        return memberService.findByEmail(principal.getName()).getId();
    }
}