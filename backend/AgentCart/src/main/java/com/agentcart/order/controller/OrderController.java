package com.agentcart.order.controller;

import com.agentcart.common.ApiResponse;
import com.agentcart.common.PageResponse;
import com.agentcart.member.service.MemberService;
import com.agentcart.order.domain.Order;
import com.agentcart.order.dto.OrderCreateRequest;
import com.agentcart.order.dto.OrderResponse;
import com.agentcart.order.dto.OrderStatusUpdateRequest;
import com.agentcart.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final MemberService memberService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            Principal principal, @Valid @RequestBody OrderCreateRequest request) {
        Long memberId = getMemberId(principal);
        Order order;
        if (request.isCartOrder()) {
            order = orderService.createFromCart(memberId, request.getCartItemIds(),
                    request.getRecipientName(), request.getPhone(),
                    request.getAddress(), request.getAddressDetail());
        } else {
            order = orderService.createDirect(memberId, request.getProductId(), request.getQuantity(),
                    request.getRecipientName(), request.getPhone(),
                    request.getAddress(), request.getAddressDetail());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(OrderResponse.from(order)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrders(
            Principal principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Order> page = orderService.getOrders(getMemberId(principal), pageable);
        PageResponse<OrderResponse> response = PageResponse.from(page.map(OrderResponse::from));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(Principal principal, @PathVariable Long id) {
        Order order = orderService.getOrder(getMemberId(principal), id);
        return ResponseEntity.ok(ApiResponse.ok(OrderResponse.from(order)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(Principal principal, @PathVariable Long id) {
        orderService.cancel(getMemberId(principal), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody OrderStatusUpdateRequest request) {
        Order order = orderService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.ok(OrderResponse.from(order)));
    }

    private Long getMemberId(Principal principal) {
        return memberService.findByEmail(principal.getName()).getId();
    }
}