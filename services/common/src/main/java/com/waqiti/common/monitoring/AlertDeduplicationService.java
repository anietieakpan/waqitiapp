package com.waqiti.common.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Alert Deduplication Service
 *
 * Prevents alert fatigue by suppressing duplicate alerts within a time window.
 *
 * FEATURES:
 * - Redis-based deduplication cache
 * - Configurable deduplication windows
 * - Severity-aware deduplication
 * - Alert counting and statistics
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @since 2025-11-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDeduplicationService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String DEDUP_KEY_PREFIX = "alert:dedup:";

    // Deduplication windows by severity
    private static final int CRITICAL_DEDUP_MINUTES = 5;  // Allow critical alerts every 5 min
    private static final int ERROR_DEDUP_MINUTES = 15;     // Allow error alerts every 15 min
    private static final int WARNING_DEDUP_MINUTES = 60;   // Allow warning alerts every hour
    private static final int INFO_DEDUP_MINUTES = 120;     // Allow info alerts every 2 hours

    /**
     * Check if alert is duplicate within deduplication window
     *
     * @param alertType Alert type identifier
     * @param message Alert message
     * @param severity Alert severity
     * @return true if duplicate (should suppress), false if new (should send)
     */
    public boolean isDuplicate(String alertType, String message, AlertSeverity severity) {
        String dedupKey = buildDedupKey(alertType, message);

        try {
            // Check if key exists
            Boolean exists = redisTemplate.hasKey(dedupKey);

            if (Boolean.TRUE.equals(exists)) {
                // Duplicate found - increment counter
                redisTemplate.opsForValue().increment(dedupKey + ":count");

                log.debug("ALERT DEDUP: Duplicate alert suppressed - type={}, severity={}",
                        alertType, severity);
                return true;
            }

            // Not duplicate - set key with TTL based on severity
            int ttlMinutes = getDedupWindow(severity);
            redisTemplate.opsForValue().set(dedupKey, "1", Duration.ofMinutes(ttlMinutes));
            redisTemplate.opsForValue().set(dedupKey + ":count", "1", Duration.ofMinutes(ttlMinutes));

            return false;

        } catch (Exception e) {
            log.error("ALERT DEDUP: Error checking deduplication - allowing alert", e);
            // On error, allow alert through (fail open)
            return false;
        }
    }

    /**
     * Get deduplication window based on severity
     */
    private int getDedupWindow(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> CRITICAL_DEDUP_MINUTES;
            case ERROR -> ERROR_DEDUP_MINUTES;
            case WARNING -> WARNING_DEDUP_MINUTES;
            case INFO -> INFO_DEDUP_MINUTES;
        };
    }

    /**
     * Build deduplication key
     */
    private String buildDedupKey(String alertType, String message) {
        // Use hash of message to handle long messages
        int messageHash = message.hashCode();
        return DEDUP_KEY_PREFIX + alertType + ":" + messageHash;
    }

    /**
     * Get alert count within deduplication window
     */
    public long getAlertCount(String alertType, String message) {
        String dedupKey = buildDedupKey(alertType, message) + ":count";

        try {
            String count = redisTemplate.opsForValue().get(dedupKey);
            return count != null ? Long.parseLong(count) : 0;
        } catch (Exception e) {
            log.error("Error getting alert count", e);
            return 0;
        }
    }
}
