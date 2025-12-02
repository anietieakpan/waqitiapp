package com.waqiti.discovery.domain;

import java.time.Duration;

/**
 * Timeframe for metrics aggregation
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
public enum MetricsTimeframe {
    /**
     * Last hour
     */
    LAST_HOUR(Duration.ofHours(1)),

    /**
     * Last 24 hours
     */
    LAST_24_HOURS(Duration.ofDays(1)),

    /**
     * Last 7 days
     */
    LAST_7_DAYS(Duration.ofDays(7)),

    /**
     * Last 30 days
     */
    LAST_30_DAYS(Duration.ofDays(30)),

    /**
     * Last 90 days
     */
    LAST_90_DAYS(Duration.ofDays(90)),

    /**
     * Real-time (last 5 minutes)
     */
    REALTIME(Duration.ofMinutes(5));

    private final Duration duration;

    MetricsTimeframe(Duration duration) {
        this.duration = duration;
    }

    /**
     * Get the duration of this timeframe
     *
     * @return duration
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * Get duration in seconds
     *
     * @return seconds
     */
    public long getSeconds() {
        return duration.getSeconds();
    }
}
