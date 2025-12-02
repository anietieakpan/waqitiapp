package com.waqiti.websocket.pool;

import lombok.Getter;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket connection wrapper with metadata and statistics
 */
@Getter
public class WebSocketConnection {
    
    private final String connectionId;
    private final String userId;
    private final WebSocketSession session;
    private final LocalDateTime createdAt;
    
    // Connection state
    private final AtomicBoolean active = new AtomicBoolean(true);
    private volatile LocalDateTime lastActivity;
    private volatile LocalDateTime lastPing;
    private volatile LocalDateTime lastPong;
    
    // Metrics
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong bytesSent = new AtomicLong(0);
    private final AtomicLong bytesReceived = new AtomicLong(0);
    
    // Custom attributes
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    
    public WebSocketConnection(String connectionId, String userId, WebSocketSession session) {
        this.connectionId = connectionId;
        this.userId = userId;
        this.session = session;
        this.createdAt = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
    }
    
    /**
     * Check if connection is active
     */
    public boolean isActive() {
        return active.get() && session.isOpen();
    }
    
    /**
     * Mark connection as active
     */
    public void markActive() {
        active.set(true);
        updateLastActivity();
    }
    
    /**
     * Mark connection as idle
     */
    public void markIdle() {
        active.set(false);
    }
    
    /**
     * Update last activity timestamp
     */
    public void updateLastActivity() {
        this.lastActivity = LocalDateTime.now();
    }
    
    /**
     * Update ping timestamp
     */
    public void updateLastPing() {
        this.lastPing = LocalDateTime.now();
    }
    
    /**
     * Update pong timestamp
     */
    public void updateLastPong() {
        this.lastPong = LocalDateTime.now();
    }
    
    /**
     * Record sent message
     */
    public void recordMessageSent(int bytes) {
        messagesSent.incrementAndGet();
        bytesSent.addAndGet(bytes);
        updateLastActivity();
    }
    
    /**
     * Record received message
     */
    public void recordMessageReceived(int bytes) {
        messagesReceived.incrementAndGet();
        bytesReceived.addAndGet(bytes);
        updateLastActivity();
    }
    
    /**
     * Get connection duration in seconds
     */
    public long getConnectionDurationSeconds() {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).getSeconds();
    }
    
    /**
     * Get idle duration in seconds
     */
    public long getIdleDurationSeconds() {
        return java.time.Duration.between(lastActivity, LocalDateTime.now()).getSeconds();
    }
    
    /**
     * Check if connection is healthy based on ping/pong
     */
    public boolean isHealthy() {
        if (!session.isOpen()) {
            return false;
        }
        
        // If we've sent a ping but haven't received pong in 30 seconds, consider unhealthy
        if (lastPing != null && lastPong != null && lastPing.isAfter(lastPong)) {
            long secondsSinceLastPing = java.time.Duration.between(lastPing, LocalDateTime.now()).getSeconds();
            return secondsSinceLastPing < 30;
        }
        
        return true;
    }
    
    /**
     * Set custom attribute
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    /**
     * Get custom attribute
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        return type.isInstance(value) ? (T) value : null;
    }
    
    /**
     * Remove custom attribute
     */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }
    
    /**
     * Get connection info for monitoring
     */
    public ConnectionInfo getConnectionInfo() {
        return ConnectionInfo.builder()
            .connectionId(connectionId)
            .userId(userId)
            .active(isActive())
            .healthy(isHealthy())
            .createdAt(createdAt)
            .lastActivity(lastActivity)
            .durationSeconds(getConnectionDurationSeconds())
            .idleSeconds(getIdleDurationSeconds())
            .messagesSent(messagesSent.get())
            .messagesReceived(messagesReceived.get())
            .bytesSent(bytesSent.get())
            .bytesReceived(bytesReceived.get())
            .remoteAddress(session.getRemoteAddress() != null ? 
                session.getRemoteAddress().toString() : "unknown")
            .build();
    }
    
    /**
     * Connection information DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class ConnectionInfo {
        private String connectionId;
        private String userId;
        private boolean active;
        private boolean healthy;
        private LocalDateTime createdAt;
        private LocalDateTime lastActivity;
        private long durationSeconds;
        private long idleSeconds;
        private long messagesSent;
        private long messagesReceived;
        private long bytesSent;
        private long bytesReceived;
        private String remoteAddress;
    }
}