package com.waqiti.common.feign;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Standard Fallback Response Builders
 *
 * CRITICAL: Use these builders to create consistent fallback responses
 *
 * PATTERNS:
 * 1. Empty Response → Return empty list/map when service unavailable
 * 2. Cached Response → Return last known good value
 * 3. Default Response → Return safe default value
 * 4. Degraded Response → Return partial data with warning
 *
 * USAGE:
 * <pre>
 * // Empty response
 * return DefaultFallbackResponse.emptyList();
 *
 * // Default response
 * return DefaultFallbackResponse.defaultResponse("Service unavailable");
 *
 * // Cached response
 * return cache.get(key).orElse(DefaultFallbackResponse.empty());
 * </pre>
 *
 * @author Waqiti Engineering Team
 * @version 3.0.0
 */
public class DefaultFallbackResponse {

    /**
     * Empty list response
     */
    public static <T> List<T> emptyList() {
        return Collections.emptyList();
    }

    /**
     * Empty map response
     */
    public static <K, V> Map<K, V> emptyMap() {
        return Collections.emptyMap();
    }

    /**
     * Empty optional response
     */
    public static <T> java.util.Optional<T> empty() {
        return java.util.Optional.empty();
    }

    /**
     * Default error response
     */
    public static ErrorResponse error(String message) {
        return ErrorResponse.builder()
            .success(false)
            .error(message)
            .fallback(true)
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Default success response with fallback flag
     */
    public static SuccessResponse success(String message) {
        return SuccessResponse.builder()
            .success(true)
            .message(message)
            .fallback(true)
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Degraded response (partial data)
     */
    public static <T> DegradedResponse<T> degraded(T data, String warning) {
        return DegradedResponse.<T>builder()
            .data(data)
            .warning(warning)
            .degraded(true)
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Error response DTO
     */
    @Data
    @Builder
    public static class ErrorResponse {
        private boolean success;
        private String error;
        private boolean fallback;
        private Instant timestamp;
    }

    /**
     * Success response DTO
     */
    @Data
    @Builder
    public static class SuccessResponse {
        private boolean success;
        private String message;
        private boolean fallback;
        private Instant timestamp;
    }

    /**
     * Degraded response DTO (partial data)
     */
    @Data
    @Builder
    public static class DegradedResponse<T> {
        private T data;
        private String warning;
        private boolean degraded;
        private Instant timestamp;
    }
}
