package com.waqiti.common.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * API Response wrapper
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private T data;
    private boolean success;
    private String message;
    private String errorCode;
    private Object error;
    private Instant timestamp;
    private ApiMetadata metadata;
    
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .data(data)
            .success(true)
            .timestamp(Instant.now())
            .build();
    }
    
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .errorCode(errorCode)
            .timestamp(Instant.now())
            .build();
    }
}