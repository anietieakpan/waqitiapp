package com.waqiti.common.alerting;

import com.waqiti.common.alerting.model.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Alert Deduplication Service
 *
 * Prevents alert storms by deduplicating identical alerts within time window
 * Uses in-memory cache with TTL for high-performance deduplication
 *
 * @author Waqiti Engineering
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertDeduplicationService {

    private final AlertingConfiguration config;

    // Deduplication cache: dedupKey -> last sent timestamp
    private final Map<String, Instant> deduplicationCache = new ConcurrentHashMap<>();

    /**
     * Check if alert should be deduplicated (suppressed)
     *
     * @param alert Alert to check
     * @return true if alert should be suppressed, false if should be sent
     */
    public boolean shouldDeduplicate(Alert alert) {
        String dedupKey = alert.getDedupKey() != null ? alert.getDedupKey() : alert.getId();

        Instant lastSent = deduplicationCache.get(dedupKey);
        if (lastSent == null) {
            // First occurrence, allow and cache
            deduplicationCache.put(dedupKey, Instant.now());
            return false;
        }

        Duration timeSinceLastSent = Duration.between(lastSent, Instant.now());
        Duration deduplicationWindow = Duration.ofSeconds(config.getDeduplicationWindowSeconds());

        if (timeSinceLastSent.compareTo(deduplicationWindow) < 0) {
            // Within deduplication window, suppress
            log.debug("Alert deduplicated: {} (last sent {} seconds ago)",
                dedupKey, timeSinceLastSent.getSeconds());
            return true;
        } else {
            // Outside deduplication window, allow and update cache
            deduplicationCache.put(dedupKey, Instant.now());
            return false;
        }
    }

    /**
     * Clear deduplication cache entry
     */
    public void clearDeduplication(String dedupKey) {
        deduplicationCache.remove(dedupKey);
    }

    /**
     * Clear all deduplication cache
     */
    public void clearAllDeduplication() {
        deduplicationCache.clear();
    }
}
