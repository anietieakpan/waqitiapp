// File: services/common/src/main/java/com/waqiti/common/exception/ValidationErrorResponse.java

package com.waqiti.common.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response structure for validation errors
 *
 * PRODUCTION FIX: Changed from ZonedDateTime to LocalDateTime for consistency
 * with ErrorResponse and to fix compilation errors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String correlationId;
    private Map<String, String> validationErrors;
}