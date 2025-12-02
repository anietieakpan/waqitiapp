package com.waqiti.common.query;

/**
 * Exception thrown when query execution fails
 */
public class QueryExecutionException extends RuntimeException {
    
    public QueryExecutionException(String message) {
        super(message);
    }
    
    public QueryExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}