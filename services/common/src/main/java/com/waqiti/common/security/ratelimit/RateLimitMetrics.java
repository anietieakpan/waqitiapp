package com.waqiti.common.security.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rate Limit Metrics for Monitoring and Alerting
 *
 * Provides:
 * - Active rate limit violations count
 * - Locked out clients count
 * - Rate limit efficiency metrics
 * - Historical violation trends
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitMetrics {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String RATE_LIMIT_KEY_PATTERN = "ratelimit:auth:*";
    private static final String VIOLATION_KEY_PATTERN = "ratelimit:violations:*";

    // Metrics cache
    private volatile int activeRateLimits = 0;
    private volatile int lockedOutClients = 0;
    private volatile int totalViolations = 0;

    /**
     * Update metrics every 60 seconds
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void updateMetrics() {
        try {
            // Count active rate limits
            Set<String> rateLimitKeys = redisTemplate.keys(RATE_LIMIT_KEY_PATTERN);
            activeRateLimits = rateLimitKeys != null ? rateLimitKeys.size() : 0;

            // Count locked out clients
            Set<String> violationKeys = redisTemplate.keys(VIOLATION_KEY_PATTERN);
            if (violationKeys != null) {
                lockedOutClients = (int) violationKeys.stream()
                        .filter(key -> {
                            String count = redisTemplate.opsForValue().get(key);
                            return count != null && Integer.parseInt(count) >= 3;
                        })
                        .count();

                totalViolations = violationKeys.size();
            }

            log.debug("Rate Limit Metrics - Active: {}, Locked Out: {}, Total Violations: {}",
                    activeRateLimits, lockedOutClients, totalViolations);

            // Alert if high number of lockouts
            if (lockedOutClients > 10) {
                log.warn("SECURITY ALERT: High number of locked out clients: {}", lockedOutClients);
            }

        } catch (Exception e) {
            log.error("Error updating rate limit metrics", e);
        }
    }

    /**
     * Get current metrics
     */
    public Map<String, Object> getCurrentMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("activeRateLimits", activeRateLimits);
        metrics.put("lockedOutClients", lockedOutClients);
        metrics.put("totalViolations", totalViolations);
        metrics.put("timestamp", System.currentTimeMillis());
        return metrics;
    }

    /**
     * Get top violators (for security monitoring)
     */
    public List<ViolatorInfo> getTopViolators(int limit) {
        try {
            Set<String> violationKeys = redisTemplate.keys(VIOLATION_KEY_PATTERN);
            if (violationKeys == null || violationKeys.isEmpty()) {
                return Collections.emptyList();
            }

            return violationKeys.stream()
                    .map(key -> {
                        String clientId = key.replace("ratelimit:violations:", "");
                        String countStr = redisTemplate.opsForValue().get(key);
                        int count = countStr != null ? Integer.parseInt(countStr) : 0;
                        return new ViolatorInfo(clientId, count);
                    })
                    .sorted((a, b) -> Integer.compare(b.violationCount, a.violationCount))
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting top violators", e);
            return Collections.emptyList();
        }
    }

    /**
     * Clear rate limit for a specific client (admin function)
     */
    public boolean clearRateLimit(String clientIdentifier, String endpoint) {
        try {
            String rateLimitKey = "ratelimit:auth:" + endpoint + ":" + clientIdentifier;
            Boolean deleted = redisTemplate.delete(rateLimitKey);

            log.info("SECURITY: Rate limit cleared for client: {} on endpoint: {}", clientIdentifier, endpoint);

            return deleted != null && deleted;

        } catch (Exception e) {
            log.error("Error clearing rate limit for client: {}", clientIdentifier, e);
            return false;
        }
    }

    /**
     * Clear lockout for a specific client (admin function)
     */
    public boolean clearLockout(String clientIdentifier) {
        try {
            String violationKey = "ratelimit:violations:" + clientIdentifier;
            Boolean deleted = redisTemplate.delete(violationKey);

            log.info("SECURITY: Lockout cleared for client: {}", clientIdentifier);

            return deleted != null && deleted;

        } catch (Exception e) {
            log.error("Error clearing lockout for client: {}", clientIdentifier, e);
            return false;
        }
    }

    /**
     * Violator information
     */
    public static class ViolatorInfo {
        public final String clientIdentifier;
        public final int violationCount;

        public ViolatorInfo(String clientIdentifier, int violationCount) {
            this.clientIdentifier = clientIdentifier;
            this.violationCount = violationCount;
        }
    }
}
