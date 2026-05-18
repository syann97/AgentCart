package com.agentcart.exception;

import com.agentcart.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }

    @ExceptionHandler(ProductException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductException(ProductException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }

    @ExceptionHandler(CartException.class)
    public ResponseEntity<ApiResponse<Void>> handleCartException(CartException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderException(OrderException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
                fields.putIfAbsent(fe.getField(), fe.getDefaultMessage())
        );
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.validationError(fields));
    }
}
