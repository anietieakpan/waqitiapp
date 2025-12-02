package com.waqiti.frauddetection.events.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event representing fraud detection result.
 * This event is published when ML models identify suspicious activity.
 *
 * CRITICAL: This event triggers wallet freezes, compliance alerts, and user account locks.
 * Must be processed idempotently to prevent duplicate actions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudDetectedEvent {

    /**
     * Unique identifier for this fraud assessment
     */
    private UUID fraudId;

    /**
     * Unique identifier for the assessed transaction/operation
     */
    private UUID transactionId;

    /**
     * Wallet ID involved in the suspicious activity
     */
    private UUID walletId;

    /**
     * User ID associated with the suspicious activity
     */
    private UUID userId;

    /**
     * Transaction amount that triggered fraud detection
     */
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217)
     */
    private String currency;

    /**
     * Risk level: LOW, MEDIUM, HIGH, CRITICAL
     */
    private String riskLevel;

    /**
     * Risk score from ML models (0.0 to 1.0)
     */
    private Double riskScore;

    /**
     * Specific fraud patterns detected
     */
    private String[] fraudPatterns;

    /**
     * Recommended actions: ALLOW, REVIEW, BLOCK, FREEZE_WALLET
     */
    private String[] recommendedActions;

    /**
     * Detailed reason for fraud detection
     */
    private String reason;

    /**
     * Model confidence score
     */
    private Double confidence;

    /**
     * Device information
     */
    private String deviceId;

    /**
     * IP address
     */
    private String ipAddress;

    /**
     * Geographic location
     */
    private String location;

    /**
     * Additional context for fraud analysis
     */
    private Map<String, Object> additionalData;

    /**
     * Timestamp when fraud was detected
     */
    private Instant detectedAt;

    /**
     * Event correlation ID for distributed tracing
     */
    private String correlationId;

    /**
     * Idempotency key for deduplication
     * Format: fraud:{fraudId}:{walletId}:{transactionId}
     */
    private String idempotencyKey;

    /**
     * Event version for schema evolution
     */
    private Integer eventVersion = 1;

    /**
     * Generate idempotency key for this event
     */
    public static String generateIdempotencyKey(UUID fraudId, UUID walletId, UUID transactionId) {
        return String.format("fraud:%s:%s:%s", fraudId, walletId, transactionId);
    }

    /**
     * Check if this fraud event requires immediate wallet freeze
     */
    public boolean requiresWalletFreeze() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }

    /**
     * Check if this fraud event requires compliance reporting
     */
    public boolean requiresComplianceReporting() {
        return riskScore != null && riskScore >= 0.8;
    }

    /**
     * Check if this fraud event requires user notification
     */
    public boolean requiresUserNotification() {
        return riskScore != null && riskScore >= 0.5;
    }
}
