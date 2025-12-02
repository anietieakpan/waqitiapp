package com.waqiti.common.exception;

/**
 * Exception thrown when database query execution exceeds timeout.
 * Indicates slow query performance or database overload.
 *
 * This should trigger:
 * - Query optimization analysis
 * - Database performance monitoring
 * - Potential circuit breaker activation
 * - Alert to database team
 *
 * @author Waqiti Platform
 */
public class QueryTimeoutException extends RuntimeException {

    private final String query;
    private final long timeoutMs;
    private final long actualTimeMs;

    /**
     * Creates exception with message
     */
    public QueryTimeoutException(String message) {
        super(message);
        this.query = null;
        this.timeoutMs = -1;
        this.actualTimeMs = -1;
    }

    /**
     * Creates exception with message and cause
     */
    public QueryTimeoutException(String message, Throwable cause) {
        super(message, cause);
        this.query = null;
        this.timeoutMs = -1;
        this.actualTimeMs = -1;
    }

    /**
     * Creates exception with detailed query information
     */
    public QueryTimeoutException(String message, String query, long timeoutMs, long actualTimeMs) {
        super(String.format("%s (timeout=%dms, actual=%dms)", message, timeoutMs, actualTimeMs));
        this.query = query;
        this.timeoutMs = timeoutMs;
        this.actualTimeMs = actualTimeMs;
    }

    public String getQuery() {
        return query;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public long getActualTimeMs() {
        return actualTimeMs;
    }
}
