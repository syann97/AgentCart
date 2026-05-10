package com.agentcart.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String errorCode;
    private final String message;
    private final Map<String, String> fields;
    private final LocalDateTime timestamp;

    private ApiResponse(boolean success, T data, String errorCode, String message, Map<String, String> fields) {
        this.success = success;
        this.data = data;
        this.errorCode = errorCode;
        this.message = message;
        this.fields = fields;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, null);
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(false, null, errorCode, message, null);
    }

    public static ApiResponse<Void> validationError(Map<String, String> fields) {
        String firstMessage = fields.values().stream().findFirst().orElse("입력 값을 확인해주세요");
        return new ApiResponse<>(false, null, "VALIDATION_ERROR", firstMessage, fields);
    }
}
