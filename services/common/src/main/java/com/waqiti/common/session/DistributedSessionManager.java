package com.waqiti.common.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Distributed session manager with Redis cluster support
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DistributedSessionManager {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String SESSION_PREFIX = "session:";
    private static final String USER_SESSIONS_PREFIX = "user-sessions:";
    private static final String ACTIVE_SESSIONS_SET = "active-sessions";
    private static final Duration DEFAULT_SESSION_TIMEOUT = Duration.ofMinutes(30);
    
    /**
     * Create a new session
     */
    public DistributedSession createSession(String userId, Map<String, Object> attributes) {
        String sessionId = generateSessionId();
        DistributedSession session = new DistributedSession(
            sessionId, userId, attributes, LocalDateTime.now());
        
        // Store session data
        String sessionKey = SESSION_PREFIX + sessionId;
        redisTemplate.opsForValue().set(sessionKey, session, DEFAULT_SESSION_TIMEOUT);
        
        // Track user sessions
        String userSessionsKey = USER_SESSIONS_PREFIX + userId;
        redisTemplate.opsForSet().add(userSessionsKey, sessionId);
        redisTemplate.expire(userSessionsKey, DEFAULT_SESSION_TIMEOUT);
        
        // Add to active sessions
        redisTemplate.opsForZSet().add(ACTIVE_SESSIONS_SET, sessionId, 
            System.currentTimeMillis());
        
        log.debug("Created session {} for user {}", sessionId, userId);
        return session;
    }
    
    /**
     * Get session by ID
     */
    public Optional<DistributedSession> getSession(String sessionId) {
        String sessionKey = SESSION_PREFIX + sessionId;
        DistributedSession session = (DistributedSession) redisTemplate.opsForValue().get(sessionKey);
        
        if (session != null) {
            // Update last accessed time
            session.setLastAccessedAt(LocalDateTime.now());
            redisTemplate.opsForValue().set(sessionKey, session, DEFAULT_SESSION_TIMEOUT);
            
            // Update active sessions score
            redisTemplate.opsForZSet().add(ACTIVE_SESSIONS_SET, sessionId, 
                System.currentTimeMillis());
        }
        
        return Optional.ofNullable(session);
    }
    
    /**
     * Update session attributes
     */
    public void updateSession(String sessionId, Map<String, Object> attributes) {
        Optional<DistributedSession> sessionOpt = getSession(sessionId);
        if (sessionOpt.isPresent()) {
            DistributedSession session = sessionOpt.get();
            session.getAttributes().putAll(attributes);
            session.setLastAccessedAt(LocalDateTime.now());
            
            String sessionKey = SESSION_PREFIX + sessionId;
            redisTemplate.opsForValue().set(sessionKey, session, DEFAULT_SESSION_TIMEOUT);
            
            log.debug("Updated session {}", sessionId);
        }
    }
    
    /**
     * Set session attribute
     */
    public void setAttribute(String sessionId, String key, Object value) {
        Optional<DistributedSession> sessionOpt = getSession(sessionId);
        if (sessionOpt.isPresent()) {
            DistributedSession session = sessionOpt.get();
            session.getAttributes().put(key, value);
            session.setLastAccessedAt(LocalDateTime.now());
            
            String sessionKey = SESSION_PREFIX + sessionId;
            redisTemplate.opsForValue().set(sessionKey, session, DEFAULT_SESSION_TIMEOUT);
        }
    }
    
    /**
     * Get session attribute
     */
    public Optional<Object> getAttribute(String sessionId, String key) {
        return getSession(sessionId)
            .map(session -> session.getAttributes().get(key));
    }
    
    /**
     * Invalidate session
     */
    public void invalidateSession(String sessionId) {
        Optional<DistributedSession> sessionOpt = getSession(sessionId);
        if (sessionOpt.isPresent()) {
            DistributedSession session = sessionOpt.get();
            String userId = session.getUserId();
            
            // Remove session data
            String sessionKey = SESSION_PREFIX + sessionId;
            redisTemplate.delete(sessionKey);
            
            // Remove from user sessions
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            redisTemplate.opsForSet().remove(userSessionsKey, sessionId);
            
            // Remove from active sessions
            redisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_SET, sessionId);
            
            log.debug("Invalidated session {} for user {}", sessionId, userId);
        }
    }
    
    /**
     * Invalidate all sessions for a user
     */
    public void invalidateUserSessions(String userId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + userId;
        Set<Object> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);
        
        if (sessionIds != null && !sessionIds.isEmpty()) {
            for (Object sessionId : sessionIds) {
                invalidateSession((String) sessionId);
            }
            log.info("Invalidated {} sessions for user {}", sessionIds.size(), userId);
        }
    }
    
    /**
     * Get all sessions for a user
     */
    public List<DistributedSession> getUserSessions(String userId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + userId;
        Set<Object> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);
        
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<DistributedSession> sessions = new ArrayList<>();
        for (Object sessionId : sessionIds) {
            getSession((String) sessionId).ifPresent(sessions::add);
        }
        
        return sessions;
    }
    
    /**
     * Check if session exists and is valid
     */
    public boolean isSessionValid(String sessionId) {
        return getSession(sessionId).isPresent();
    }
    
    /**
     * Extend session timeout
     */
    public void extendSession(String sessionId, Duration additionalTime) {
        String sessionKey = SESSION_PREFIX + sessionId;
        if (redisTemplate.hasKey(sessionKey)) {
            redisTemplate.expire(sessionKey, additionalTime);
            log.debug("Extended session {} by {}", sessionId, additionalTime);
        }
    }
    
    /**
     * Clean up expired sessions
     */
    public void cleanupExpiredSessions() {
        long cutoffTime = System.currentTimeMillis() - DEFAULT_SESSION_TIMEOUT.toMillis();
        Set<Object> expiredSessionIds = redisTemplate.opsForZSet()
            .rangeByScore(ACTIVE_SESSIONS_SET, 0, cutoffTime);
        
        if (expiredSessionIds != null && !expiredSessionIds.isEmpty()) {
            for (Object sessionId : expiredSessionIds) {
                invalidateSession((String) sessionId);
            }
            log.info("Cleaned up {} expired sessions", expiredSessionIds.size());
        }
    }
    
    /**
     * Get session statistics
     */
    public SessionStatistics getSessionStatistics() {
        Long totalActiveSessions = redisTemplate.opsForZSet().count(ACTIVE_SESSIONS_SET, 
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        
        long currentTime = System.currentTimeMillis();
        long recentCutoff = currentTime - Duration.ofMinutes(5).toMillis();
        
        Long recentActiveSessions = redisTemplate.opsForZSet().count(ACTIVE_SESSIONS_SET, 
            recentCutoff, currentTime);
        
        return new SessionStatistics(
            totalActiveSessions != null ? totalActiveSessions : 0,
            recentActiveSessions != null ? recentActiveSessions : 0
        );
    }
    
    /**
     * Replicate session to backup Redis instance
     */
    public void replicateSession(String sessionId, DistributedSession session) {
        try {
            String backupKey = "backup:" + SESSION_PREFIX + sessionId;
            redisTemplate.opsForValue().set(backupKey, session, DEFAULT_SESSION_TIMEOUT);
            log.debug("Replicated session {} to backup", sessionId);
        } catch (Exception e) {
            log.warn("Failed to replicate session {} to backup", sessionId, e);
        }
    }
    
    /**
     * Restore session from backup
     */
    public Optional<DistributedSession> restoreSessionFromBackup(String sessionId) {
        try {
            String backupKey = "backup:" + SESSION_PREFIX + sessionId;
            DistributedSession session = (DistributedSession) redisTemplate.opsForValue().get(backupKey);
            
            if (session != null) {
                // Restore to primary location
                String sessionKey = SESSION_PREFIX + sessionId;
                redisTemplate.opsForValue().set(sessionKey, session, DEFAULT_SESSION_TIMEOUT);
                log.info("Restored session {} from backup", sessionId);
                return Optional.of(session);
            }
        } catch (Exception e) {
            log.error("Failed to restore session {} from backup", sessionId, e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Generate unique session ID
     */
    private String generateSessionId() {
        return "sess_" + UUID.randomUUID().toString().replace("-", "") + 
               "_" + System.currentTimeMillis();
    }
    
    /**
     * Distributed session data class
     */
    public static class DistributedSession {
        private String sessionId;
        private String userId;
        private Map<String, Object> attributes;
        private LocalDateTime createdAt;
        private LocalDateTime lastAccessedAt;
        
        public DistributedSession() {}
        
        public DistributedSession(String sessionId, String userId, 
                                Map<String, Object> attributes, LocalDateTime createdAt) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
            this.createdAt = createdAt;
            this.lastAccessedAt = createdAt;
        }
        
        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public Map<String, Object> getAttributes() { return attributes; }
        public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public LocalDateTime getLastAccessedAt() { return lastAccessedAt; }
        public void setLastAccessedAt(LocalDateTime lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
        
        public boolean isExpired(Duration timeout) {
            return lastAccessedAt.isBefore(LocalDateTime.now().minus(timeout));
        }
    }
    
    /**
     * Session statistics
     */
    public static class SessionStatistics {
        private final long totalActiveSessions;
        private final long recentActiveSessions;
        
        public SessionStatistics(long totalActiveSessions, long recentActiveSessions) {
            this.totalActiveSessions = totalActiveSessions;
            this.recentActiveSessions = recentActiveSessions;
        }
        
        public long getTotalActiveSessions() { return totalActiveSessions; }
        public long getRecentActiveSessions() { return recentActiveSessions; }
    }
}