package com.waqiti.wallet.service;

import com.waqiti.wallet.dto.FraudAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Alert deduplication service to prevent alert storms.
 *
 * <p>Uses Redis to track recently sent alerts and prevents
 * sending duplicate alerts within a configurable time window.
 *
 * <p>Key Features:
 * <ul>
 *   <li>Configurable deduplication window (default: 5 minutes)</li>
 *   <li>Redis-based distributed deduplication</li>
 *   <li>Automatic expiration of deduplication keys</li>
 *   <li>Support for different alert types</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-22
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertDeduplicationService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String DEDUP_KEY_PREFIX = "wallet:alert:dedup:";

    /**
     * Check if an alert is a duplicate within the specified window.
     *
     * @param fraudAlert the alert to check
     * @param windowSeconds deduplication window in seconds
     * @return true if this is a duplicate alert
     */
    public boolean isDuplicate(FraudAlert fraudAlert, int windowSeconds) {
        String dedupKey = buildDeduplicationKey(fraudAlert);

        try {
            // Try to set the key with NX (only if not exists)
            Boolean wasSet = redisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, "1", Duration.ofSeconds(windowSeconds));

            if (wasSet != null && wasSet) {
                // Key was successfully set, this is NOT a duplicate
                log.debug("Alert dedupe key set: {} (window: {}s)", dedupKey, windowSeconds);
                return false;
            } else {
                // Key already exists, this IS a duplicate
                log.info("Duplicate alert detected: {} (window: {}s)", dedupKey, windowSeconds);
                return true;
            }
        } catch (Exception e) {
            log.error("Error checking alert deduplication, allowing alert through", e);
            // On error, don't block the alert
            return false;
        }
    }

    /**
     * Check if a generic alert is a duplicate.
     *
     * @param alertType type of alert
     * @param alertKey unique identifier for the alert
     * @param windowSeconds deduplication window in seconds
     * @return true if this is a duplicate alert
     */
    public boolean isDuplicate(String alertType, String alertKey, int windowSeconds) {
        String dedupKey = DEDUP_KEY_PREFIX + alertType + ":" + alertKey;

        try {
            Boolean wasSet = redisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, "1", Duration.ofSeconds(windowSeconds));

            if (wasSet != null && wasSet) {
                log.debug("Alert dedupe key set: {} (window: {}s)", dedupKey, windowSeconds);
                return false;
            } else {
                log.info("Duplicate alert detected: {} (window: {}s)", dedupKey, windowSeconds);
                return true;
            }
        } catch (Exception e) {
            log.error("Error checking alert deduplication for type: {}, key: {}", alertType, alertKey, e);
            return false;
        }
    }

    /**
     * Manually clear a deduplication key (for testing or manual intervention).
     *
     * @param fraudAlert the alert to clear
     */
    public void clearDeduplication(FraudAlert fraudAlert) {
        String dedupKey = buildDeduplicationKey(fraudAlert);
        try {
            redisTemplate.delete(dedupKey);
            log.info("Cleared deduplication key: {}", dedupKey);
        } catch (Exception e) {
            log.error("Error clearing deduplication key: {}", dedupKey, e);
        }
    }

    /**
     * Clear deduplication for a specific alert type and key.
     *
     * @param alertType type of alert
     * @param alertKey unique identifier
     */
    public void clearDeduplication(String alertType, String alertKey) {
        String dedupKey = DEDUP_KEY_PREFIX + alertType + ":" + alertKey;
        try {
            redisTemplate.delete(dedupKey);
            log.info("Cleared deduplication key: {}", dedupKey);
        } catch (Exception e) {
            log.error("Error clearing deduplication key: {}", dedupKey, e);
        }
    }

    /**
     * Build a deduplication key from fraud alert details.
     */
    private String buildDeduplicationKey(FraudAlert fraudAlert) {
        // Key format: wallet:alert:dedup:FRAUD:{fraudType}:{walletId}:{userId}
        return String.format("%sFRAUD:%s:%s:%s",
                DEDUP_KEY_PREFIX,
                fraudAlert.getFraudType(),
                fraudAlert.getWalletId(),
                fraudAlert.getUserId());
    }

    /**
     * Get TTL for a deduplication key (for monitoring/debugging).
     *
     * @param alertType type of alert
     * @param alertKey unique identifier
     * @return remaining TTL in seconds, or -1 if key doesn't exist
     */
    public long getDeduplicationTTL(String alertType, String alertKey) {
        String dedupKey = DEDUP_KEY_PREFIX + alertType + ":" + alertKey;
        try {
            Long ttl = redisTemplate.getExpire(dedupKey, TimeUnit.SECONDS);
            return ttl != null ? ttl : -1;
        } catch (Exception e) {
            log.error("Error getting TTL for deduplication key: {}", dedupKey, e);
            return -1;
        }
    }
}
