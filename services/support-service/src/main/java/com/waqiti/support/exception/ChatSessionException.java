package com.waqiti.support.exception;

public class ChatSessionException extends RuntimeException {
    
    public ChatSessionException(String message) {
        super(message);
    }
    
    public ChatSessionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static ChatSessionException sessionNotFound(String sessionId) {
        return new ChatSessionException("Chat session not found: " + sessionId);
    }
    
    public static ChatSessionException sessionExpired(String sessionId) {
        return new ChatSessionException("Chat session expired: " + sessionId);
    }
    
    public static ChatSessionException agentNotAvailable() {
        return new ChatSessionException("No agents available at this time");
    }
}