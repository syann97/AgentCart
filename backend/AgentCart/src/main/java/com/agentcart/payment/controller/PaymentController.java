package com.agentcart.payment.controller;

import com.agentcart.common.ApiResponse;
import com.agentcart.member.service.MemberService;
import com.agentcart.payment.dto.PaymentRequest;
import com.agentcart.payment.dto.PaymentResponse;
import com.agentcart.payment.service.PaymentFacadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacadeService paymentFacadeService;
    private final MemberService memberService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(
            Principal principal, @Valid @RequestBody PaymentRequest request) {
        Long memberId = getMemberId(principal);
        PaymentResponse response = PaymentResponse.from(
                paymentFacadeService.pay(memberId, request.orderId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            Principal principal, @PathVariable Long id) {
        Long memberId = getMemberId(principal);
        PaymentResponse response = PaymentResponse.from(
                paymentFacadeService.getPayment(memberId, id));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(
            Principal principal, @PathVariable Long orderId) {
        Long memberId = getMemberId(principal);
        PaymentResponse response = PaymentResponse.from(
                paymentFacadeService.getPaymentByOrder(memberId, orderId));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private Long getMemberId(Principal principal) {
        return memberService.findByEmail(principal.getName()).getId();
    }
}