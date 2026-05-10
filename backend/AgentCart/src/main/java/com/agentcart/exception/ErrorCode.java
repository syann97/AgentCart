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
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed");

    private final HttpStatus status;
    private final String message;
}