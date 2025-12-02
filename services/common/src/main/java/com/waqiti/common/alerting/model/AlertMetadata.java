package com.waqiti.common.alerting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Production-grade Alert Metadata
 *
 * Contains metadata about alert delivery and deduplication
 * Used internally by AlertingService for tracking
 *
 * @author Waqiti Engineering
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertMetadata {

    /**
     * Alert ID
     */
    private String alertId;

    /**
     * Deduplication key (same as alertId for builder compatibility)
     */
    private String deduplicationKey;

    /**
     * Alert creation timestamp (first occurrence)
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Last sent timestamp
     */
    private Instant lastSent;

    /**
     * Number of occurrences of this alert
     */
    @Builder.Default
    private int occurrences = 1;

    /**
     * Number of times alert was sent
     */
    @Builder.Default
    private int sendCount = 0;

    /**
     * Number of times alert was deduplicated (suppressed)
     */
    @Builder.Default
    private int deduplicatedCount = 0;

    /**
     * Channels alert was sent to
     */
    private java.util.List<String> channels;

    /**
     * Additional metadata
     */
    private Map<String, Object> additionalMetadata;

    /**
     * Increment occurrence count for deduplication
     */
    public void incrementOccurrences() {
        this.occurrences++;
        this.timestamp = Instant.now();  // Update timestamp to latest occurrence
    }

    /**
     * Static factory method to get current timestamp
     * Used by AlertingService for deduplication window checks
     */
    public static Instant getTimestamp() {
        return Instant.now();
    }
}
