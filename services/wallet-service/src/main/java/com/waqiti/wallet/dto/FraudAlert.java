package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Fraud alert DTO containing all details for fraud detection notifications.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlert {

    /**
     * Unique alert identifier.
     */
    private UUID alertId;

    /**
     * Wallet ID where fraud was detected.
     */
    private UUID walletId;

    /**
     * User ID associated with the wallet.
     */
    private UUID userId;

    /**
     * Transaction amount involved.
     */
    private BigDecimal amount;

    /**
     * Currency of the transaction.
     */
    private String currency;

    /**
     * Type of fraud detected (e.g., "VELOCITY_ABUSE", "STOLEN_CARD", "MONEY_LAUNDERING").
     */
    private String fraudType;

    /**
     * Risk score (0-100, higher = more risky).
     */
    private Double riskScore;

    /**
     * Alert severity level.
     */
    private AlertSeverity severity;

    /**
     * List of fraud indicators detected.
     */
    private List<String> fraudIndicators;

    /**
     * Recommended action (e.g., "FREEZE_WALLET", "BLOCK_TRANSACTION", "MANUAL_REVIEW").
     */
    private String recommendedAction;

    /**
     * When the fraud was detected.
     */
    private LocalDateTime detectedAt;

    /**
     * Transaction ID if applicable.
     */
    private UUID transactionId;

    /**
     * Device ID if available.
     */
    private String deviceId;

    /**
     * IP address if available.
     */
    private String ipAddress;

    /**
     * Geographic location if available.
     */
    private String location;

    /**
     * Additional context or metadata.
     */
    private String additionalContext;

    /**
     * Whether the wallet has been automatically frozen.
     */
    private boolean walletFrozen;

    /**
     * Whether the transaction has been blocked.
     */
    private boolean transactionBlocked;
}
