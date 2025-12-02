package com.waqiti.support.service;

import com.waqiti.support.domain.ChatSession;
import com.waqiti.support.dto.ChatMessage;

import java.util.List;
import java.util.Optional;

public interface ChatSessionService {
    
    /**
     * Get existing session or create new one
     */
    ChatSession getOrCreateSession(String sessionId, String userId);
    
    /**
     * Get session by ID
     */
    Optional<ChatSession> getSession(String sessionId);
    
    /**
     * Add message to session
     */
    void addMessage(String sessionId, ChatMessage message);
    
    /**
     * Get session messages
     */
    List<ChatMessage> getSessionMessages(String sessionId);
    
    /**
     * Get recent messages from session
     */
    List<ChatMessage> getRecentMessages(String sessionId, int count);
    
    /**
     * End chat session
     */
    void endSession(String sessionId);
    
    /**
     * Transfer session to human agent
     */
    void transferToAgent(String sessionId, String agentId);
    
    /**
     * Update session metadata
     */
    void updateSessionMetadata(String sessionId, String key, Object value);
    
    /**
     * Check if session is active
     */
    boolean isSessionActive(String sessionId);
    
    /**
     * Get active sessions for user
     */
    List<ChatSession> getActiveSessionsForUser(String userId);
    
    /**
     * Clean up expired sessions
     */
    void cleanupExpiredSessions();
}