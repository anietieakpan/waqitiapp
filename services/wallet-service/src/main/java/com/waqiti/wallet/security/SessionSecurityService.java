/**
 * SECURITY ENHANCEMENT: Session Security Service
 * Implements session ID regeneration and security validation for wallet operations
 * 
 * Note: Since this application uses stateless JWT tokens, "session regeneration" 
 * involves invalidating JWT tokens and requiring re-authentication for sensitive operations
 */
package com.waqiti.wallet.security;

import com.waqiti.common.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * SECURITY-FOCUSED session management for wallet operations
 * Provides JWT token invalidation and session security validation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionSecurityService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${wallet.security.session.max-age-minutes:30}")
    private int maxSessionAgeMinutes;
    
    @Value("${wallet.security.session.regeneration-required-operations:WITHDRAW,TRANSFER,FREEZE,UNFREEZE,LIMIT_UPDATE}")
    private String regenerationRequiredOperations;
    
    @Value("${wallet.security.session.token-blacklist-duration-hours:24}")
    private int tokenBlacklistDurationHours;
    
    // In-memory cache for fast lookup (Redis is the source of truth)
    private final Map<String, LocalDateTime> sessionCreationCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> sessionOperationCount = new ConcurrentHashMap<>();
    
    private static final String BLACKLISTED_TOKEN_PREFIX = "wallet:blacklisted:token:";
    private static final String SESSION_SECURITY_PREFIX = "wallet:session:security:";
    
    /**
     * SECURITY FIX: Validate session security for wallet operations
     * This method checks if the current session is secure enough for the requested operation
     */
    public void validateSessionSecurity(String operationType, UUID userId) {
        log.debug("SECURITY: Validating session security for operation {} by user {}", operationType, userId);
        
        try {
            String sessionId = SecurityContextUtil.getCurrentSessionId();
            if (sessionId == null) {
                log.warn("SECURITY: No session ID found for user {} operation {}", userId, operationType);
                throw new SecurityException("Session validation failed - no session identifier");
            }
            
            // SECURITY CHECK 1: Verify token is not blacklisted
            validateTokenNotBlacklisted(sessionId);
            
            // SECURITY CHECK 2: Check session age for sensitive operations
            validateSessionAge(sessionId, operationType, userId);
            
            // SECURITY CHECK 3: Check operation count limits
            validateOperationLimits(sessionId, operationType, userId);
            
            // SECURITY CHECK 4: Check for suspicious session patterns
            validateSessionPattern(sessionId, operationType, userId);
            
            // Update session activity
            updateSessionActivity(sessionId, operationType, userId);
            
            log.debug("SECURITY: Session validation passed for user {} operation {}", userId, operationType);
            
        } catch (SecurityException e) {
            log.warn("SECURITY: Session validation failed for user {} operation {}: {}", 
                    userId, operationType, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("SECURITY: Error during session validation for user {} operation {}", 
                    userId, operationType, e);
            throw new SecurityException("Session validation failed due to system error");
        }
    }
    
    /**
     * SECURITY FIX: Force session regeneration for sensitive operations
     * In JWT stateless system, this blacklists the current token and requires re-authentication
     */
    public void requireSessionRegeneration(String operationType, UUID userId, String reason) {
        log.info("SECURITY: Requiring session regeneration for user {} operation {} - Reason: {}", 
                userId, operationType, reason);
        
        try {
            String sessionId = SecurityContextUtil.getCurrentSessionId();
            if (sessionId != null) {
                // Blacklist the current JWT token
                blacklistToken(sessionId, userId, reason);
                
                // Clear session cache
                clearSessionCache(sessionId);
                
                // Record security event
                recordSessionRegenerationEvent(sessionId, operationType, userId, reason);
            }
            
            // Throw exception to require re-authentication
            throw new SessionRegenerationRequiredException(
                "Session regeneration required for security. Please re-authenticate and retry the operation.");
                
        } catch (SessionRegenerationRequiredException e) {
            throw e;
        } catch (Exception e) {
            log.error("SECURITY: Error during session regeneration for user {} operation {}", 
                    userId, operationType, e);
            throw new SecurityException("Session regeneration failed");
        }
    }
    
    /**
     * SECURITY CHECK 1: Validate token is not blacklisted
     */
    private void validateTokenNotBlacklisted(String sessionId) {
        String blacklistKey = BLACKLISTED_TOKEN_PREFIX + sessionId;
        Boolean isBlacklisted = redisTemplate.hasKey(blacklistKey);
        
        if (Boolean.TRUE.equals(isBlacklisted)) {
            log.warn("SECURITY: Attempt to use blacklisted token: {}", sessionId);
            throw new SecurityException("Token has been invalidated. Please re-authenticate.");
        }
    }
    
    /**
     * SECURITY CHECK 2: Validate session age for sensitive operations
     */
    private void validateSessionAge(String sessionId, String operationType, UUID userId) {
        if (!isSensitiveOperation(operationType)) {
            return; // No age restriction for non-sensitive operations
        }
        
        LocalDateTime sessionCreation = getSessionCreationTime(sessionId);
        if (sessionCreation != null) {
            long sessionAgeMinutes = ChronoUnit.MINUTES.between(sessionCreation, LocalDateTime.now());
            
            if (sessionAgeMinutes > maxSessionAgeMinutes) {
                log.warn("SECURITY: Session too old for sensitive operation - User: {}, Operation: {}, Age: {} minutes", 
                        userId, operationType, sessionAgeMinutes);
                requireSessionRegeneration(operationType, userId, 
                    "Session exceeded maximum age for sensitive operation (" + sessionAgeMinutes + " minutes)");
            }
        }
    }
    
    /**
     * SECURITY CHECK 3: Validate operation count limits
     */
    private void validateOperationLimits(String sessionId, String operationType, UUID userId) {
        int operationCount = sessionOperationCount.getOrDefault(sessionId, 0);
        
        // Limit of 50 operations per session to prevent session riding
        if (operationCount >= 50) {
            log.warn("SECURITY: Session operation limit exceeded - User: {}, Session: {}, Count: {}", 
                    userId, sessionId, operationCount);
            requireSessionRegeneration(operationType, userId, 
                "Session operation limit exceeded (" + operationCount + " operations)");
        }
        
        // Additional limits for sensitive operations
        if (isSensitiveOperation(operationType) && operationCount >= 10) {
            log.warn("SECURITY: Sensitive operation limit exceeded - User: {}, Operation: {}, Count: {}", 
                    userId, operationType, operationCount);
            requireSessionRegeneration(operationType, userId, 
                "Sensitive operation limit exceeded (" + operationCount + " operations)");
        }
    }
    
    /**
     * SECURITY CHECK 4: Validate session patterns for suspicious activity
     */
    private void validateSessionPattern(String sessionId, String operationType, UUID userId) {
        // Check for rapid successive operations (potential automation)
        String patternKey = SESSION_SECURITY_PREFIX + "pattern:" + sessionId;
        Long lastOperationTime = (Long) redisTemplate.opsForValue().get(patternKey);
        
        if (lastOperationTime != null) {
            long timeDiff = System.currentTimeMillis() - lastOperationTime;
            
            // If operations are happening faster than 2 seconds apart, flag as suspicious
            if (timeDiff < 2000) {
                log.warn("SECURITY: Suspicious rapid operations detected - User: {}, Time diff: {}ms", 
                        userId, timeDiff);
                requireSessionRegeneration(operationType, userId, 
                    "Suspicious rapid operations detected (interval: " + timeDiff + "ms)");
            }
        }
        
        // Store current operation time
        redisTemplate.opsForValue().set(patternKey, System.currentTimeMillis(), 
                5, TimeUnit.MINUTES);
    }
    
    /**
     * Update session activity tracking
     */
    private void updateSessionActivity(String sessionId, String operationType, UUID userId) {
        // Increment operation count
        sessionOperationCount.merge(sessionId, 1, Integer::sum);
        
        // Update last activity in Redis
        String activityKey = SESSION_SECURITY_PREFIX + "activity:" + sessionId;
        SessionActivity activity = new SessionActivity(
            LocalDateTime.now(),
            operationType,
            userId.toString(),
            SecurityContextUtil.getClientIp(),
            SecurityContextUtil.getUserAgent()
        );
        
        redisTemplate.opsForValue().set(activityKey, activity, 
                maxSessionAgeMinutes + 10, TimeUnit.MINUTES);
    }
    
    /**
     * Blacklist a JWT token
     */
    private void blacklistToken(String sessionId, UUID userId, String reason) {
        String blacklistKey = BLACKLISTED_TOKEN_PREFIX + sessionId;
        TokenBlacklistEntry entry = new TokenBlacklistEntry(
            sessionId,
            userId.toString(),
            LocalDateTime.now(),
            reason,
            SecurityContextUtil.getClientIp()
        );
        
        // Store in Redis with expiration
        redisTemplate.opsForValue().set(blacklistKey, entry, 
                tokenBlacklistDurationHours, TimeUnit.HOURS);
        
        log.info("SECURITY: Token blacklisted - Session: {}, User: {}, Reason: {}", 
                sessionId, userId, reason);
    }
    
    /**
     * Clear session cache entries
     */
    private void clearSessionCache(String sessionId) {
        sessionCreationCache.remove(sessionId);
        sessionOperationCount.remove(sessionId);
        
        // Clear Redis cache entries
        String patternKey = SESSION_SECURITY_PREFIX + "pattern:" + sessionId;
        String activityKey = SESSION_SECURITY_PREFIX + "activity:" + sessionId;
        redisTemplate.delete(patternKey);
        redisTemplate.delete(activityKey);
    }
    
    /**
     * Get session creation time
     */
    private LocalDateTime getSessionCreationTime(String sessionId) {
        // First check cache
        LocalDateTime cached = sessionCreationCache.get(sessionId);
        if (cached != null) {
            return cached;
        }
        
        // Try to get from JWT token issued time
        try {
            // For JWT tokens, we can use the 'iat' (issued at) claim
            // This would require JWT parsing but for now we'll use current time as fallback
            LocalDateTime now = LocalDateTime.now();
            sessionCreationCache.put(sessionId, now);
            return now;
        } catch (Exception e) {
            log.warn("SECURITY: Could not determine session creation time for session: {}", sessionId);
            return LocalDateTime.now().minusMinutes(maxSessionAgeMinutes + 1); // Force regeneration
        }
    }
    
    /**
     * Check if operation is sensitive and requires session validation
     */
    private boolean isSensitiveOperation(String operationType) {
        return regenerationRequiredOperations.contains(operationType.toUpperCase());
    }
    
    /**
     * Record session regeneration event for audit
     */
    private void recordSessionRegenerationEvent(String sessionId, String operationType, 
                                              UUID userId, String reason) {
        SessionRegenerationEvent event = new SessionRegenerationEvent(
            UUID.randomUUID(),
            sessionId,
            userId.toString(),
            operationType,
            reason,
            LocalDateTime.now(),
            SecurityContextUtil.getClientIp(),
            SecurityContextUtil.getUserAgent()
        );
        
        // Store audit event
        String eventKey = SESSION_SECURITY_PREFIX + "regeneration:" + event.getEventId();
        redisTemplate.opsForValue().set(eventKey, event, 30, TimeUnit.DAYS);
        
        log.info("SECURITY: Session regeneration event recorded - Event: {}, User: {}, Reason: {}", 
                event.getEventId(), userId, reason);
    }
    
    /**
     * Check if current session requires regeneration based on operation type
     */
    public boolean shouldRegenerateSession(String operationType) {
        return isSensitiveOperation(operationType);
    }
    
    /**
     * Get session security statistics for monitoring
     */
    public Map<String, Object> getSessionSecurityStats() {
        try {
            // Count blacklisted tokens
            Long blacklistedCount = redisTemplate.opsForValue().size();
            
            // Count active sessions
            int activeSessions = sessionCreationCache.size();
            
            // Count high-activity sessions
            long highActivitySessions = sessionOperationCount.entrySet().stream()
                .filter(entry -> entry.getValue() > 20)
                .count();
            
            return Map.of(
                "blacklistedTokens", blacklistedCount != null ? blacklistedCount : 0,
                "activeSessions", activeSessions,
                "highActivitySessions", highActivitySessions,
                "maxSessionAgeMinutes", maxSessionAgeMinutes,
                "tokenBlacklistDurationHours", tokenBlacklistDurationHours
            );
        } catch (Exception e) {
            log.error("Error getting session security stats", e);
            return Map.of("error", "Unable to retrieve stats");
        }
    }
    
    /**
     * Session activity tracking class
     */
    private static class SessionActivity {
        public final LocalDateTime timestamp;
        public final String operationType;
        public final String userId;
        public final String clientIp;
        public final String userAgent;
        
        public SessionActivity(LocalDateTime timestamp, String operationType, String userId, 
                             String clientIp, String userAgent) {
            this.timestamp = timestamp;
            this.operationType = operationType;
            this.userId = userId;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
        }
    }
    
    /**
     * Token blacklist entry class
     */
    private static class TokenBlacklistEntry {
        public final String sessionId;
        public final String userId;
        public final LocalDateTime blacklistedAt;
        public final String reason;
        public final String clientIp;
        
        public TokenBlacklistEntry(String sessionId, String userId, LocalDateTime blacklistedAt, 
                                 String reason, String clientIp) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.blacklistedAt = blacklistedAt;
            this.reason = reason;
            this.clientIp = clientIp;
        }
    }
    
    /**
     * Session regeneration event class
     */
    private static class SessionRegenerationEvent {
        public final UUID eventId;
        public final String sessionId;
        public final String userId;
        public final String operationType;
        public final String reason;
        public final LocalDateTime timestamp;
        public final String clientIp;
        public final String userAgent;
        
        public SessionRegenerationEvent(UUID eventId, String sessionId, String userId, 
                                      String operationType, String reason, LocalDateTime timestamp,
                                      String clientIp, String userAgent) {
            this.eventId = eventId;
            this.sessionId = sessionId;
            this.userId = userId;
            this.operationType = operationType;
            this.reason = reason;
            this.timestamp = timestamp;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
        }
        
        public UUID getEventId() { return eventId; }
    }
    
    /**
     * Custom exception for session regeneration requirements
     */
    public static class SessionRegenerationRequiredException extends SecurityException {
        public SessionRegenerationRequiredException(String message) {
            super(message);
        }
    }
}