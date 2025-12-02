package com.waqiti.common.exception;

/**
 * Exception thrown when database connection pool is exhausted.
 * Indicates all connections are in use and no new connections available.
 *
 * This is typically a transient error that should trigger:
 * - Circuit breaker activation
 * - Retry with backoff
 * - Alert operations team
 * - Auto-scaling database connections
 *
 * @author Waqiti Platform
 */
public class ConnectionPoolExhaustedException extends RuntimeException {

    private final int maxPoolSize;
    private final int activeConnections;
    private final long waitTimeMs;

    /**
     * Creates exception with message
     */
    public ConnectionPoolExhaustedException(String message) {
        super(message);
        this.maxPoolSize = -1;
        this.activeConnections = -1;
        this.waitTimeMs = -1;
    }

    /**
     * Creates exception with message and cause
     */
    public ConnectionPoolExhaustedException(String message, Throwable cause) {
        super(message, cause);
        this.maxPoolSize = -1;
        this.activeConnections = -1;
        this.waitTimeMs = -1;
    }

    /**
     * Creates exception with detailed pool information
     */
    public ConnectionPoolExhaustedException(String message, int maxPoolSize, int activeConnections, long waitTimeMs) {
        super(String.format("%s (maxPoolSize=%d, active=%d, waitTime=%dms)",
            message, maxPoolSize, activeConnections, waitTimeMs));
        this.maxPoolSize = maxPoolSize;
        this.activeConnections = activeConnections;
        this.waitTimeMs = waitTimeMs;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public int getActiveConnections() {
        return activeConnections;
    }

    public long getWaitTimeMs() {
        return waitTimeMs;
    }
}
