package com.waqiti.common.ratelimit.exception;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * CRITICAL SECURITY EXCEPTION - Rate Limit Exceeded
 * 
 * Thrown when API rate limits are exceeded to protect against:
 * - DDoS attacks and API abuse
 * - Brute force authentication attempts
 * - High-frequency trading abuse
 * - Resource exhaustion attacks
 * 
 * This exception triggers automatic logging, monitoring alerts,
 * and potential IP blocking for security purposes.
 */
@Getter
public class RateLimitExceededException extends RuntimeException {
    
    private final String rateLimitKey;
    private final String keyType;
    private final long requestsAllowed;
    private final long requestsRemaining;
    private final long windowSeconds;
    private final LocalDateTime resetTime;
    private final long retryAfterSeconds;
    private final String clientIp;
    private final String userId;
    private final String endpoint;
    private final boolean blocked;
    private final String blockReason;
    
    public RateLimitExceededException(String message, 
                                   String rateLimitKey,
                                   String keyType,
                                   long requestsAllowed,
                                   long requestsRemaining,
                                   long windowSeconds,
                                   LocalDateTime resetTime,
                                   long retryAfterSeconds,
                                   String clientIp,
                                   String userId,
                                   String endpoint) {
        super(message);
        this.rateLimitKey = rateLimitKey;
        this.keyType = keyType;
        this.requestsAllowed = requestsAllowed;
        this.requestsRemaining = requestsRemaining;
        this.windowSeconds = windowSeconds;
        this.resetTime = resetTime;
        this.retryAfterSeconds = retryAfterSeconds;
        this.clientIp = clientIp;
        this.userId = userId;
        this.endpoint = endpoint;
        this.blocked = false;
        this.blockReason = null;
    }
    
    public RateLimitExceededException(String message, 
                                   String rateLimitKey,
                                   String keyType,
                                   long requestsAllowed,
                                   long requestsRemaining,
                                   long windowSeconds,
                                   LocalDateTime resetTime,
                                   long retryAfterSeconds,
                                   String clientIp,
                                   String userId,
                                   String endpoint,
                                   boolean blocked,
                                   String blockReason) {
        super(message);
        this.rateLimitKey = rateLimitKey;
        this.keyType = keyType;
        this.requestsAllowed = requestsAllowed;
        this.requestsRemaining = requestsRemaining;
        this.windowSeconds = windowSeconds;
        this.resetTime = resetTime;
        this.retryAfterSeconds = retryAfterSeconds;
        this.clientIp = clientIp;
        this.userId = userId;
        this.endpoint = endpoint;
        this.blocked = blocked;
        this.blockReason = blockReason;
    }
    
    /**
     * Create exception for basic rate limit exceeded
     */
    public static RateLimitExceededException basicExceeded(String message, 
                                                         String rateLimitKey,
                                                         long requestsAllowed,
                                                         long retryAfterSeconds) {
        return new RateLimitExceededException(
            message, rateLimitKey, "UNKNOWN", requestsAllowed, 0, 60,
            LocalDateTime.now().plusSeconds(retryAfterSeconds), retryAfterSeconds,
            null, null, null
        );
    }
    
    /**
     * Create exception for IP-based rate limit exceeded
     */
    public static RateLimitExceededException ipExceeded(String message,
                                                       String clientIp,
                                                       String endpoint,
                                                       long requestsAllowed,
                                                       long retryAfterSeconds) {
        return new RateLimitExceededException(
            message, "ip:" + clientIp, "IP", requestsAllowed, 0, 60,
            LocalDateTime.now().plusSeconds(retryAfterSeconds), retryAfterSeconds,
            clientIp, null, endpoint
        );
    }
    
    /**
     * Create exception for user-based rate limit exceeded
     */
    public static RateLimitExceededException userExceeded(String message,
                                                         String userId,
                                                         String endpoint,
                                                         long requestsAllowed,
                                                         long retryAfterSeconds) {
        return new RateLimitExceededException(
            message, "user:" + userId, "USER", requestsAllowed, 0, 60,
            LocalDateTime.now().plusSeconds(retryAfterSeconds), retryAfterSeconds,
            null, userId, endpoint
        );
    }
    
    /**
     * Create exception for blocked IP/user
     */
    public static RateLimitExceededException blocked(String message,
                                                    String identifier,
                                                    String keyType,
                                                    String blockReason,
                                                    long blockDurationSeconds) {
        return new RateLimitExceededException(
            message, keyType.toLowerCase() + ":" + identifier, keyType, 0, 0, blockDurationSeconds,
            LocalDateTime.now().plusSeconds(blockDurationSeconds), blockDurationSeconds,
            keyType.equals("IP") ? identifier : null,
            keyType.equals("USER") ? identifier : null,
            null, true, blockReason
        );
    }
    
    /**
     * Get formatted error message for API response
     */
    public String getFormattedMessage() {
        if (blocked) {
            return String.format("%s (Blocked: %s, Duration: %d seconds)", 
                getMessage(), blockReason, retryAfterSeconds);
        }
        
        return String.format("%s (Limit: %d requests, Reset in: %d seconds)", 
            getMessage(), requestsAllowed, retryAfterSeconds);
    }
    
    /**
     * Get rate limit info for response headers
     */
    public RateLimitInfo getRateLimitInfo() {
        return RateLimitInfo.builder()
            .limit(requestsAllowed)
            .remaining(Math.max(0, requestsRemaining))
            .resetTime(resetTime)
            .retryAfterSeconds(retryAfterSeconds)
            .blocked(blocked)
            .blockReason(blockReason)
            .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RateLimitInfo {
        private long limit;
        private long remaining;
        private LocalDateTime resetTime;
        private long retryAfterSeconds;
        private boolean blocked;
        private String blockReason;
    }
}