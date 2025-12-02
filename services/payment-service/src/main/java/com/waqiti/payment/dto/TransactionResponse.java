package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Transaction Response DTO
 *
 * Contains comprehensive transaction processing results including
 * status, amounts, fees, and audit information.
 *
 * COMPLIANCE RELEVANCE:
 * - PCI DSS: Transaction security and audit trail
 * - SOX: Financial transaction documentation
 * - AML: Transaction monitoring
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    /**
     * Unique transaction identifier
     */
    @NotNull
    private UUID transactionId;

    /**
     * User ID associated with the transaction
     */
    @NotNull
    private UUID userId;

    /**
     * Transaction status
     * Values: INITIATED, PENDING, PROCESSING, AUTHORIZED, COMPLETED, FAILED,
     *         DECLINED, CANCELLED, REVERSED, REFUNDED
     */
    @NotNull
    private String status;

    /**
     * Transaction type
     * Values: PAYMENT, REFUND, WITHDRAWAL, DEPOSIT, TRANSFER, REVERSAL, FEE
     */
    @NotNull
    private String transactionType;

    /**
     * Transaction amount
     */
    @NotNull
    @Positive
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217)
     */
    @NotNull
    private String currency;

    /**
     * Transaction fee
     */
    private BigDecimal fee;

    /**
     * Net amount (amount ± fee depending on context)
     */
    private BigDecimal netAmount;

    /**
     * Payment method used
     * Values: CARD, ACH, WIRE, WALLET, CASH, CHECK, CRYPTO
     */
    private String paymentMethod;

    /**
     * Payment method details (masked)
     */
    private String paymentMethodDetails;

    /**
     * From account/wallet ID
     */
    private UUID fromAccountId;

    /**
     * To account/wallet ID
     */
    private UUID toAccountId;

    /**
     * Merchant ID if applicable
     */
    private UUID merchantId;

    /**
     * Merchant name
     */
    private String merchantName;

    /**
     * Description or purpose
     */
    private String description;

    /**
     * Reference number
     */
    private String referenceNumber;

    /**
     * External reference (from external system)
     */
    private String externalReference;

    /**
     * Authorization code
     */
    private String authorizationCode;

    /**
     * Approval code
     */
    private String approvalCode;

    /**
     * Transaction timestamp
     */
    @NotNull
    private LocalDateTime transactionTimestamp;

    /**
     * Initiated timestamp
     */
    private LocalDateTime initiatedAt;

    /**
     * Authorized timestamp
     */
    private LocalDateTime authorizedAt;

    /**
     * Completed timestamp
     */
    private LocalDateTime completedAt;

    /**
     * Processing duration (ms)
     */
    private long processingDurationMs;

    /**
     * Batch ID if part of a batch
     */
    private UUID batchId;

    /**
     * Parent transaction ID (for refunds, reversals)
     */
    private UUID parentTransactionId;

    /**
     * Related transaction IDs
     */
    private java.util.List<UUID> relatedTransactionIds;

    /**
     * Risk score (0-100)
     */
    private int riskScore;

    /**
     * Risk level
     * Values: LOW, MEDIUM, HIGH, CRITICAL
     */
    private String riskLevel;

    /**
     * Fraud check performed
     */
    private boolean fraudCheckPerformed;

    /**
     * Fraud check result
     * Values: PASSED, FAILED, REVIEW_REQUIRED
     */
    private String fraudCheckResult;

    /**
     * Location information
     */
    private LocationInfo locationInfo;

    /**
     * Device information
     */
    private DeviceInfo deviceInfo;

    /**
     * IP address
     */
    private String ipAddress;

    /**
     * Geolocation
     */
    private String geolocation;

    /**
     * Error code if transaction failed
     */
    private String errorCode;

    /**
     * Error message if transaction failed
     */
    private String errorMessage;

    /**
     * Decline reason if declined
     */
    private String declineReason;

    /**
     * Retryable flag
     */
    private boolean retryable;

    /**
     * Retry count
     */
    private int retryCount;

    /**
     * Notification sent flag
     */
    private boolean notificationSent;

    /**
     * Receipt generated flag
     */
    private boolean receiptGenerated;

    /**
     * Receipt URL
     */
    private String receiptUrl;

    /**
     * Metadata additional fields
     */
    private Map<String, String> metadata;

    /**
     * Tags for categorization
     */
    private java.util.List<String> tags;

    /**
     * Audit trail reference
     */
    private UUID auditTrailId;

    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Location Info nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        private String city;
        private String state;
        private String country;
        private String postalCode;
        private Double latitude;
        private Double longitude;
    }

    /**
     * Device Info nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        private String deviceId;
        private String deviceType;
        private String deviceModel;
        private String operatingSystem;
        private String browser;
        private String userAgent;
    }
}
