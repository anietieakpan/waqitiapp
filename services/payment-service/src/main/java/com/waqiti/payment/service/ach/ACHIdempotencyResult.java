package com.waqiti.payment.service.ach;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ACH Idempotency Result
 *
 * Wrapper for idempotency check results in ACH processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ACHIdempotencyResult<T> {

    private boolean isNewOperation;
    private T result;
    private String idempotencyKey;

    public static <T> ACHIdempotencyResult<T> newOperation(String key) {
        return ACHIdempotencyResult.<T>builder()
            .isNewOperation(true)
            .idempotencyKey(key)
            .build();
    }

    public static <T> ACHIdempotencyResult<T> duplicate(T cachedResult, String key) {
        return ACHIdempotencyResult.<T>builder()
            .isNewOperation(false)
            .result(cachedResult)
            .idempotencyKey(key)
            .build();
    }
}
