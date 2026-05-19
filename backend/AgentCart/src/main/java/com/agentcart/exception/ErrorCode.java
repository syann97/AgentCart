package com.agentcart.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid refresh token"),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token expired"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Member not found"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Email already in use"),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "Nickname already in use"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product not found"),
    DUPLICATE_PRODUCT(HttpStatus.CONFLICT, "Product with same name and brand already exists"),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "Insufficient stock"),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Cart item not found"),
    PRODUCT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "Product is not available for purchase"),
    EXCEEDS_STOCK(HttpStatus.BAD_REQUEST, "Requested quantity exceeds available stock"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found"),
    ORDER_NOT_CANCELLABLE(HttpStatus.BAD_REQUEST, "Order cannot be cancelled in its current status"),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access to this order is denied"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment not found"),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "Payment has already been completed"),
    PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access to this payment is denied"),
    PAYMENT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Payment processing failed"),
    LOCK_ACQUISITION_FAILED(HttpStatus.CONFLICT, "Failed to acquire inventory lock, please retry");

    private final HttpStatus status;
    private final String message;
}