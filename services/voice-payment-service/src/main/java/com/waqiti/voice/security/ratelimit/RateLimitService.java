package com.waqiti.voice.security.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Rate Limiting Service
 *
 * CRITICAL SECURITY: Prevents abuse and DoS attacks
 *
 * Rate Limits:
 * - Voice command processing: 100 requests/hour per user
 * - Voice enrollment: 10 attempts/hour per user
 * - Payment transactions: 50 transactions/hour per user
 * - API calls: 1000 requests/hour per user
 *
 * Implementation:
 * - Redis-based sliding window counter
 * - Per-user rate limiting
 * - Configurable limits and windows
 * - Automatic expiration
 *
 * Benefits:
 * - Prevents brute force attacks on voice biometrics
 * - Prevents payment fraud (rapid-fire transactions)
 * - Protects infrastructure from abuse
 * - Fair resource allocation
 *
 * Compliance:
 * - PCI-DSS Requirement 8.1.6 (Limit repeated access attempts)
 * - OWASP Top 10 - API4:2023 Unrestricted Resource Consumption
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Voice command rate limits
    @Value("${voice-payment.rate-limit.voice-commands.max-requests:100}")
    private int voiceCommandMaxRequests;

    @Value("${voice-payment.rate-limit.voice-commands.window-seconds:3600}")
    private long voiceCommandWindowSeconds;

    // Voice enrollment rate limits
    @Value("${voice-payment.rate-limit.enrollment.max-attempts:10}")
    private int enrollmentMaxAttempts;

    @Value("${voice-payment.rate-limit.enrollment.window-seconds:3600}")
    private long enrollmentWindowSeconds;

    // Payment transaction rate limits
    @Value("${voice-payment.rate-limit.transactions.max-requests:50}")
    private int transactionMaxRequests;

    @Value("${voice-payment.rate-limit.transactions.window-seconds:3600}")
    private long transactionWindowSeconds;

    // General API rate limits
    @Value("${voice-payment.rate-limit.api.max-requests:1000}")
    private int apiMaxRequests;

    @Value("${voice-payment.rate-limit.api.window-seconds:3600}")
    private long apiWindowSeconds;

    // Biometric verification rate limits (prevent brute force)
    @Value("${voice-payment.rate-limit.biometric.max-attempts:5}")
    private int biometricMaxAttempts;

    @Value("${voice-payment.rate-limit.biometric.window-seconds:900}")
    private long biometricWindowSeconds;

    /**
     * Check rate limit for voice commands
     *
     * @param userId User ID
     * @return true if within limit, false if exceeded
     */
    public boolean checkVoiceCommandLimit(UUID userId) {
        return checkRateLimit(
                buildKey("voice-command", userId),
                voiceCommandMaxRequests,
                voiceCommandWindowSeconds
        );
    }

    /**
     * Check rate limit for voice enrollment
     *
     * @param userId User ID
     * @return true if within limit, false if exceeded
     */
    public boolean checkEnrollmentLimit(UUID userId) {
        return checkRateLimit(
                buildKey("enrollment", userId),
                enrollmentMaxAttempts,
                enrollmentWindowSeconds
        );
    }

    /**
     * Check rate limit for payment transactions
     *
     * @param userId User ID
     * @return true if within limit, false if exceeded
     */
    public boolean checkTransactionLimit(UUID userId) {
        return checkRateLimit(
                buildKey("transaction", userId),
                transactionMaxRequests,
                transactionWindowSeconds
        );
    }

    /**
     * Check rate limit for general API calls
     *
     * @param userId User ID
     * @return true if within limit, false if exceeded
     */
    public boolean checkApiLimit(UUID userId) {
        return checkRateLimit(
                buildKey("api", userId),
                apiMaxRequests,
                apiWindowSeconds
        );
    }

    /**
     * Check rate limit for biometric verification attempts
     *
     * CRITICAL: Prevents brute force attacks on voice biometrics
     *
     * @param userId User ID
     * @return true if within limit, false if exceeded
     */
    public boolean checkBiometricVerificationLimit(UUID userId) {
        return checkRateLimit(
                buildKey("biometric-verify", userId),
                biometricMaxAttempts,
                biometricWindowSeconds
        );
    }

    /**
     * Generic rate limit check with sliding window
     *
     * @param key Redis key
     * @param maxRequests Maximum requests allowed
     * @param windowSeconds Time window in seconds
     * @return true if within limit, false if exceeded
     */
    private boolean checkRateLimit(String key, int maxRequests, long windowSeconds) {
        try {
            // Get current count
            Long currentCount = (Long) redisTemplate.opsForValue().get(key);

            if (currentCount == null) {
                // First request - initialize counter
                redisTemplate.opsForValue().set(key, 1L, windowSeconds, TimeUnit.SECONDS);
                log.debug("Rate limit initialized: key={}, count=1/{}", key, maxRequests);
                return true;
            }

            if (currentCount >= maxRequests) {
                // Rate limit exceeded
                log.warn("RATE LIMIT EXCEEDED: key={}, current={}, max={}", key, currentCount, maxRequests);
                return false;
            }

            // Increment counter
            Long newCount = redisTemplate.opsForValue().increment(key);
            log.debug("Rate limit check: key={}, count={}/{}", key, newCount, maxRequests);

            return newCount <= maxRequests;

        } catch (Exception e) {
            log.error("Rate limit check failed: key={}", key, e);
            // Fail open: Allow request if Redis is down (prevent DoS on ourselves)
            return true;
        }
    }

    /**
     * Get remaining requests for rate limit
     *
     * @param userId User ID
     * @param limitType Limit type (voice-command, enrollment, transaction, etc.)
     * @return Remaining requests
     */
    public RateLimitInfo getRateLimitInfo(UUID userId, String limitType) {
        String key = buildKey(limitType, userId);

        int maxRequests;
        long windowSeconds;

        switch (limitType) {
            case "voice-command":
                maxRequests = voiceCommandMaxRequests;
                windowSeconds = voiceCommandWindowSeconds;
                break;
            case "enrollment":
                maxRequests = enrollmentMaxAttempts;
                windowSeconds = enrollmentWindowSeconds;
                break;
            case "transaction":
                maxRequests = transactionMaxRequests;
                windowSeconds = transactionWindowSeconds;
                break;
            case "biometric-verify":
                maxRequests = biometricMaxAttempts;
                windowSeconds = biometricWindowSeconds;
                break;
            case "api":
            default:
                maxRequests = apiMaxRequests;
                windowSeconds = apiWindowSeconds;
                break;
        }

        try {
            Long currentCount = (Long) redisTemplate.opsForValue().get(key);
            if (currentCount == null) {
                currentCount = 0L;
            }

            long remaining = Math.max(0, maxRequests - currentCount);
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

            return RateLimitInfo.builder()
                    .limitType(limitType)
                    .maxRequests(maxRequests)
                    .currentCount(currentCount.intValue())
                    .remaining((int) remaining)
                    .windowSeconds(windowSeconds)
                    .resetInSeconds(ttl != null ? ttl : windowSeconds)
                    .limited(currentCount >= maxRequests)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get rate limit info: key={}", key, e);
            return RateLimitInfo.error(limitType);
        }
    }

    /**
     * Reset rate limit for user (admin operation)
     *
     * @param userId User ID
     * @param limitType Limit type
     */
    public void resetRateLimit(UUID userId, String limitType) {
        String key = buildKey(limitType, userId);
        redisTemplate.delete(key);
        log.info("Rate limit reset: userId={}, limitType={}", userId, limitType);
    }

    /**
     * Build Redis key for rate limiting
     */
    private String buildKey(String limitType, UUID userId) {
        return String.format("rate-limit:%s:%s", limitType, userId);
    }

    /**
     * Rate limit exceeded exception
     */
    public static class RateLimitExceededException extends RuntimeException {
        private final String limitType;
        private final int maxRequests;
        private final long windowSeconds;
        private final long resetInSeconds;

        public RateLimitExceededException(String limitType, int maxRequests,
                                         long windowSeconds, long resetInSeconds) {
            super(String.format(
                    "Rate limit exceeded: %s (max: %d requests per %d seconds, resets in %d seconds)",
                    limitType, maxRequests, windowSeconds, resetInSeconds
            ));
            this.limitType = limitType;
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
            this.resetInSeconds = resetInSeconds;
        }

        public String getLimitType() {
            return limitType;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public long getResetInSeconds() {
            return resetInSeconds;
        }
    }

    /**
     * Rate limit information DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class RateLimitInfo {
        private String limitType;
        private int maxRequests;
        private int currentCount;
        private int remaining;
        private long windowSeconds;
        private long resetInSeconds;
        private boolean limited;
        private String errorMessage;

        public static RateLimitInfo error(String limitType) {
            return RateLimitInfo.builder()
                    .limitType(limitType)
                    .errorMessage("Failed to retrieve rate limit information")
                    .build();
        }
    }
}
