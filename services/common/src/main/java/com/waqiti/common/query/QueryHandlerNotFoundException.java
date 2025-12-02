package com.waqiti.common.query;

/**
 * Exception thrown when no handler is found for a query
 */
public class QueryHandlerNotFoundException extends RuntimeException {
    
    public QueryHandlerNotFoundException(String message) {
        super(message);
    }
    
    public QueryHandlerNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}