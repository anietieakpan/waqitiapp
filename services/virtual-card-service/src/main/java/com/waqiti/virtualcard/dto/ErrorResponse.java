package com.waqiti.virtualcard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response for all API errors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Unique error identifier for tracking
     */
    private String errorId;

    /**
     * Timestamp when error occurred
     */
    private Instant timestamp;

    /**
     * HTTP status code
     */
    private Integer status;

    /**
     * Error type/category
     */
    private String error;

    /**
     * User-friendly error message
     */
    private String message;

    /**
     * Request path where error occurred
     */
    private String path;

    /**
     * Field-level validation errors (for validation failures)
     */
    private Map<String, String> fieldErrors;

    /**
     * Additional error details (optional)
     */
    private Map<String, Object> details;
}
