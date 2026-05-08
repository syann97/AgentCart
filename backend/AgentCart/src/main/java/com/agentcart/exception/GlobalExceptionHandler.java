package com.agentcart.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException e) {
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(Map.of("error", e.getMessage()));
    }
    // Note: BadCredentialsException from the login flow never reaches here.
    // Spring Security intercepts it at the filter level and delegates to LoginFailureHandler.
}