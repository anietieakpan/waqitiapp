package com.waqiti.common.saga;

import java.util.concurrent.ScheduledFuture;

/**
 * Handle for saga timeout operations
 */
public class SagaTimeoutHandle {
    
    private final String sagaId;
    private final ScheduledFuture<?> timeoutTask;
    private boolean cancelled = false;
    
    public SagaTimeoutHandle(String sagaId, ScheduledFuture<?> timeoutTask) {
        this.sagaId = sagaId;
        this.timeoutTask = timeoutTask;
    }
    
    /**
     * Cancel the timeout
     */
    public boolean cancel() {
        if (!cancelled && timeoutTask != null) {
            cancelled = timeoutTask.cancel(false);
            return cancelled;
        }
        return false;
    }
    
    /**
     * Check if timeout is cancelled
     */
    public boolean isCancelled() {
        return cancelled || (timeoutTask != null && timeoutTask.isCancelled());
    }
    
    /**
     * Check if timeout has completed
     */
    public boolean isDone() {
        return timeoutTask != null && timeoutTask.isDone();
    }
    
    /**
     * Get saga ID
     */
    public String getSagaId() {
        return sagaId;
    }
}