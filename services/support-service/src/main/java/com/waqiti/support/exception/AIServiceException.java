package com.waqiti.support.exception;

public class AIServiceException extends RuntimeException {
    
    public AIServiceException(String message) {
        super(message);
    }
    
    public AIServiceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static AIServiceException serviceUnavailable() {
        return new AIServiceException("AI service is currently unavailable");
    }
    
    public static AIServiceException rateLimitExceeded() {
        return new AIServiceException("AI service rate limit exceeded. Please try again later.");
    }
    
    public static AIServiceException invalidResponse() {
        return new AIServiceException("Invalid response from AI service");
    }
}