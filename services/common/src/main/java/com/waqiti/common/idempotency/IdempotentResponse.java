package com.waqiti.common.idempotency;

import lombok.Builder;
import lombok.Data;

/**
 * Response wrapper for idempotent operations
 *
 * @param <T> Response data type
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
public class IdempotentResponse<T> {
    /**
     * The actual response data
     */
    private T result;

    /**
     * Whether this is a retry of a previous request
     */
    private boolean isRetry;

    /**
     * Number of times this request was retried
     */
    private int retryCount;

    /**
     * Idempotency key used
     */
    private String idempotencyKey;
}
