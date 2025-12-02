package com.waqiti.common.alerting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Production-grade Alert Statistics
 *
 * Contains aggregated statistics for alerting system
 * Used for monitoring and reporting
 *
 * @author Waqiti Engineering
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertStatistics {

    /**
     * Total alerts sent
     */
    @Builder.Default
    private long totalAlertsSent = 0;

    /**
     * Total alerts deduplicated
     */
    @Builder.Default
    private long totalAlertsDeduplicated = 0;

    /**
     * Deduplicated alerts (alias for totalAlertsDeduplicated)
     */
    @Builder.Default
    private long deduplicatedAlerts = 0;

    /**
     * Suppressed alerts
     */
    @Builder.Default
    private long suppressedAlerts = 0;

    /**
     * Failed deliveries
     */
    @Builder.Default
    private long failedDeliveries = 0;

    /**
     * Maintenance mode active
     */
    @Builder.Default
    private boolean maintenanceModeActive = false;

    /**
     * │
     * │ 60 + * Suppressed alert types │
     * │ 61 +
     */
    @Builder.Default
    private java.util.Set<String> suppressedAlertTypes = new java.util.HashSet<>();

    /**
     * Critical alerts sent
     */
    @Builder.Default
    private long criticalAlerts = 0;

    /**
     * Error alerts sent (alias for high severity alerts)
     */
    @Builder.Default
    private long errorAlerts = 0;

    /**
     * Warning alerts sent
     */
    @Builder.Default
    private long warningAlerts = 0;

    /**
     * Info alerts sent
     */
    @Builder.Default
    private long infoAlerts = 0;

    /**
     * Total alerts failed
     */
    @Builder.Default
    private long totalAlertsFailed = 0;

    /**
     * Alerts sent by severity
     */
    private java.util.Map<AlertSeverity, Long> alertsBySeverity;

    /**
     * Alerts sent by source
     */
    private java.util.Map<String, Long> alertsBySource;

    /**
     * Alerts sent by channel
     */
    private java.util.Map<String, Long> alertsByChannel;

    /**
     * Current alerts in deduplication cache
     */
    @Builder.Default
    private int currentCachedAlerts = 0;

    /**
     * Maintenance mode enabled
     */
    @Builder.Default
    private boolean maintenanceModeEnabled = false;
}
