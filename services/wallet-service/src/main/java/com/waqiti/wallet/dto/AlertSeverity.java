package com.waqiti.wallet.dto;

/**
 * Alert severity levels for fraud detection and wallet events.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-22
 */
public enum AlertSeverity {
    /**
     * Critical - requires immediate action (PagerDuty alert).
     * Examples: High-value fraud, account takeover, money laundering.
     */
    CRITICAL,

    /**
     * High - requires urgent attention within 1 hour.
     * Examples: Medium fraud, suspicious patterns, compliance violations.
     */
    HIGH,

    /**
     * Medium - requires attention within 4 hours.
     * Examples: Low-value fraud, velocity warnings, unusual behavior.
     */
    MEDIUM,

    /**
     * Low - informational, review within 24 hours.
     * Examples: Pattern anomalies, minor violations, educational alerts.
     */
    LOW,

    /**
     * Info - for logging and analytics only.
     */
    INFO
}
