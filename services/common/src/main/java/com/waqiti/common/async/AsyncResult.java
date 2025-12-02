package com.waqiti.common.async;

import lombok.Data;

/**
 * Result wrapper for async operations
 */
@Data
public class AsyncResult<T> {
    private final T result;
    private final boolean successful;
    private final String errorMessage;
    
    public AsyncResult(T result) {
        this.result = result;
        this.successful = true;
        this.errorMessage = null;
    }
    
    public AsyncResult(String errorMessage) {
        this.result = null;
        this.successful = false;
        this.errorMessage = errorMessage;
    }
    
    public static <T> AsyncResult<T> success(T result) {
        return new AsyncResult<>(result);
    }
    
    public static <T> AsyncResult<T> failure(String errorMessage) {
        return new AsyncResult<>(errorMessage);
    }
}