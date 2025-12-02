package com.waqiti.analytics.dto;

import com.waqiti.analytics.domain.AlertStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Alert Resolution Recovery Result DTO
 *
 * Comprehensive result object for alert resolution attempts in DLQ recovery scenarios.
 * Captures all information needed for proper alert lifecycle management and metrics.
 *
 * Used when recovering failed alert resolution events from Dead Letter Queue.
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertResolutionRecoveryResult {

    /**
     * Alert ID that was processed
     */
    @NotNull(message = "Alert ID cannot be null")
    private UUID alertId;

    /**
     * Type of alert (e.g., FRAUD_DETECTION, VELOCITY_LIMIT, UNUSUAL_PATTERN)
     */
    @NotBlank(message = "Alert type cannot be blank")
    private String alertType;

    /**
     * Current resolution status after recovery attempt
     */
    @NotNull(message = "Resolution status cannot be null")
    private AlertStatus resolutionStatus;

    /**
     * Method used to resolve the alert
     * (AUTOMATED, MANUAL, ESCALATED, AI_ASSISTED)
     */
    private String resolutionMethod;

    /**
     * Detailed explanation of the resolution
     */
    private String resolutionDetails;

    /**
     * User or system that resolved the alert
     */
    private String resolvedBy;

    /**
     * Timestamp when resolution was attempted
     */
    @NotNull(message = "Resolution timestamp cannot be null")
    private Instant resolutionTimestamp;

    /**
     * Was the resolution successful?
     */
    private boolean resolved;

    /**
     * Was the alert escalated to higher tier?
     */
    private boolean escalated;

    /**
     * Escalation tier (1, 2, 3)
     */
    private Integer escalationTier;

    /**
     * Escalation reason if escalated
     */
    private String escalationReason;

    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;

    /**
     * Number of retry attempts before success/failure
     */
    private Integer retryCount;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;

    /**
     * Recovery strategy used
     * (IMMEDIATE, DELAYED, MANUAL_INTERVENTION, IGNORE)
     */
    private String recoveryStrategy;

    /**
     * Confidence score of the resolution (0.0 - 1.0)
     */
    private Double confidenceScore;

    /**
     * Whether manual review is required
     */
    private boolean requiresManualReview;

    /**
     * Root cause identified (if any)
     */
    private String rootCause;

    /**
     * Preventive actions taken
     */
    private String preventiveActions;

    // Helper Methods

    /**
     * Check if resolution was successful
     */
    public boolean isSuccessful() {
        return resolved && resolutionStatus == AlertStatus.RESOLVED;
    }

    /**
     * Check if alert should be escalated
     */
    public boolean shouldEscalate() {
        return escalated || resolutionStatus == AlertStatus.ESCALATED;
    }

    /**
     * Check if result indicates failure
     */
    public boolean isFailed() {
        return !resolved && !escalated && resolutionStatus != AlertStatus.PENDING_REVIEW;
    }

    /**
     * Get human-readable summary
     */
    public String getSummary() {
        return String.format("Alert %s: %s - %s by %s",
                alertId,
                alertType,
                resolutionStatus.getDisplayName(),
                resolvedBy != null ? resolvedBy : "system");
    }
}
