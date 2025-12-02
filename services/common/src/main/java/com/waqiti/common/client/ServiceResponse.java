package com.waqiti.common.client;

import com.waqiti.common.api.StandardApiResponse;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Service Response wrapper for inter-service communication
 * Wraps StandardApiResponse for consistent client responses
 */
@Data
@Builder
public class ServiceResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private String errorCode;
    private LocalDateTime timestamp;
    
    public static <T> ServiceResponse<T> success(T data) {
        return ServiceResponse.<T>builder()
            .success(true)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> ServiceResponse<T> error(String message, String errorCode) {
        return ServiceResponse.<T>builder()
            .success(false)
            .message(message)
            .errorCode(errorCode)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> ServiceResponse<T> fromStandardResponse(StandardApiResponse<T> standardResponse) {
        return ServiceResponse.<T>builder()
            .success(standardResponse.isSuccess())
            .data(standardResponse.getData())
            .message(standardResponse.getMessage())
            .errorCode(standardResponse.getError() != null ? standardResponse.getError().getCode() : null)
            .timestamp(standardResponse.getTimestamp())
            .build();
    }
}