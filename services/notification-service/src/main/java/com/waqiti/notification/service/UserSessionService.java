package com.waqiti.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking user sessions and online status
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSessionService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // In-memory cache for active sessions
    private final Map<String, UserSession> activeSessions = new ConcurrentHashMap<>();
    
    private static final String SESSION_KEY_PREFIX = "user:session:";
    private static final String ONLINE_USERS_KEY = "users:online";
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);
    
    public boolean isUserOnline(String userId) {
        if (userId == null) return false;
        
        // Check in-memory cache first
        UserSession session = activeSessions.get(userId);
        if (session != null && !session.isExpired()) {
            return true;
        }
        
        // Check Redis for session
        try {
            String sessionKey = SESSION_KEY_PREFIX + userId;
            Object sessionData = redisTemplate.opsForValue().get(sessionKey);
            
            if (sessionData != null) {
                // Update local cache
                session = new UserSession(userId);
                activeSessions.put(userId, session);
                return true;
            }
        } catch (Exception e) {
            log.error("Error checking user online status in Redis: {}", e.getMessage());
        }
        
        return false;
    }
    
    public void markUserOnline(String userId, String sessionId, String deviceInfo) {
        try {
            log.debug("Marking user {} as online with session {}", userId, sessionId);
            
            UserSession session = new UserSession(userId, sessionId, deviceInfo);
            activeSessions.put(userId, session);
            
            // Store in Redis with expiration
            String sessionKey = SESSION_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(sessionKey, session, SESSION_TIMEOUT);
            
            // Add to online users set
            redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId);
            redisTemplate.expire(ONLINE_USERS_KEY, SESSION_TIMEOUT);
            
            log.debug("User {} marked as online", userId);
            
        } catch (Exception e) {
            log.error("Error marking user {} as online: {}", userId, e.getMessage());
        }
    }
    
    public void markUserOffline(String userId) {
        try {
            log.debug("Marking user {} as offline", userId);
            
            // Remove from local cache
            activeSessions.remove(userId);
            
            // Remove from Redis
            String sessionKey = SESSION_KEY_PREFIX + userId;
            redisTemplate.delete(sessionKey);
            
            // Remove from online users set
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId);
            
            log.debug("User {} marked as offline", userId);
            
        } catch (Exception e) {
            log.error("Error marking user {} as offline: {}", userId, e.getMessage());
        }
    }
    
    public void updateUserActivity(String userId) {
        UserSession session = activeSessions.get(userId);
        if (session != null) {
            session.updateActivity();
            
            // Extend session in Redis
            try {
                String sessionKey = SESSION_KEY_PREFIX + userId;
                redisTemplate.expire(sessionKey, SESSION_TIMEOUT);
                redisTemplate.expire(ONLINE_USERS_KEY, SESSION_TIMEOUT);
            } catch (Exception e) {
                log.error("Error updating user activity in Redis: {}", e.getMessage());
            }
        }
    }
    
    public Set<String> getActiveUserSessions() {
        try {
            // Get from Redis for most up-to-date data
            Set<Object> onlineUsers = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
            
            if (onlineUsers != null) {
                Set<String> activeUsers = new HashSet<>();
                for (Object user : onlineUsers) {
                    activeUsers.add((String) user);
                }
                return activeUsers;
            }
        } catch (Exception e) {
            log.error("Error getting active user sessions from Redis: {}", e.getMessage());
        }
        
        // Fallback to local cache
        Set<String> activeUsers = new HashSet<>();
        for (Map.Entry<String, UserSession> entry : activeSessions.entrySet()) {
            if (!entry.getValue().isExpired()) {
                activeUsers.add(entry.getKey());
            }
        }
        
        return activeUsers;
    }
    
    public int getOnlineUserCount() {
        return getActiveUserSessions().size();
    }
    
    public UserSession getUserSession(String userId) {
        return activeSessions.get(userId);
    }
    
    public List<UserSession> getAllActiveSessions() {
        return activeSessions.values().stream()
            .filter(session -> !session.isExpired())
            .toList();
    }
    
    public void cleanupExpiredSessions() {
        try {
            log.debug("Cleaning up expired user sessions");
            
            int removedCount = 0;
            Iterator<Map.Entry<String, UserSession>> iterator = activeSessions.entrySet().iterator();
            
            while (iterator.hasNext()) {
                Map.Entry<String, UserSession> entry = iterator.next();
                UserSession session = entry.getValue();
                
                if (session.isExpired()) {
                    iterator.remove();
                    
                    // Also remove from Redis
                    String sessionKey = SESSION_KEY_PREFIX + entry.getKey();
                    redisTemplate.delete(sessionKey);
                    redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, entry.getKey());
                    
                    removedCount++;
                }
            }
            
            if (removedCount > 0) {
                log.info("Cleaned up {} expired user sessions", removedCount);
            }
            
        } catch (Exception e) {
            log.error("Error cleaning up expired sessions: {}", e.getMessage());
        }
    }
    
    public SessionStats getSessionStats() {
        int totalSessions = activeSessions.size();
        int activeSessions = getActiveUserSessions().size();
        int expiredSessions = totalSessions - activeSessions;
        
        return SessionStats.builder()
            .totalSessions(totalSessions)
            .activeSessions(activeSessions)
            .expiredSessions(expiredSessions)
            .onlineUsers(getOnlineUserCount())
            .build();
    }
    
    // Inner classes
    
    public static class UserSession {
        private final String userId;
        private final String sessionId;
        private final String deviceInfo;
        private final LocalDateTime createdAt;
        private LocalDateTime lastActivity;
        
        public UserSession(String userId) {
            this(userId, UUID.randomUUID().toString(), "Unknown Device");
        }
        
        public UserSession(String userId, String sessionId, String deviceInfo) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.deviceInfo = deviceInfo;
            this.createdAt = LocalDateTime.now();
            this.lastActivity = LocalDateTime.now();
        }
        
        public void updateActivity() {
            this.lastActivity = LocalDateTime.now();
        }
        
        public boolean isExpired() {
            return lastActivity.isBefore(LocalDateTime.now().minus(SESSION_TIMEOUT));
        }
        
        public Duration getSessionDuration() {
            return Duration.between(createdAt, LocalDateTime.now());
        }
        
        public Duration getIdleTime() {
            return Duration.between(lastActivity, LocalDateTime.now());
        }
        
        // Getters
        public String getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        public String getDeviceInfo() { return deviceInfo; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getLastActivity() { return lastActivity; }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class SessionStats {
        private int totalSessions;
        private int activeSessions;
        private int expiredSessions;
        private int onlineUsers;
        
        public double getActiveSessionRate() {
            return totalSessions > 0 ? (double) activeSessions / totalSessions : 0.0;
        }
    }
}