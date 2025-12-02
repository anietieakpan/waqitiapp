package com.waqiti.payment.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive Transaction Authorized Event
 * 
 * Published when a payment transaction receives authorization from payment processors.
 * This event triggers downstream processing in fraud detection, risk assessment, 
 * ledger recording, and notification systems.
 * 
 * Consumed by:
 * - fraud-service: Real-time fraud scoring and analysis
 * - risk-service: Risk model updates and scoring 
 * - ledger-service: Transaction recording and reconciliation
 * - notification-service: Customer and merchant notifications
 * - analytics-service: Payment flow analytics
 * 
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAuthorizedEvent {

    /**
     * Unique event identifier for deduplication and tracing
     */
    @NotNull
    @JsonProperty("eventId")
    private String eventId;

    /**
     * Correlation ID for end-to-end transaction tracing
     */
    @NotNull
    @JsonProperty("correlationId")
    private String correlationId;

    /**
     * Event timestamp in UTC
     */
    @NotNull
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    /**
     * Event version for schema evolution
     */
    @NotNull
    @JsonProperty("eventVersion")
    @Builder.Default
    private String eventVersion = "1.0";

    /**
     * Source service that published the event
     */
    @NotNull
    @JsonProperty("source")
    @Builder.Default
    private String source = "payment-service";

    // === Transaction Core Details ===

    /**
     * Unique transaction identifier
     */
    @NotNull
    @JsonProperty("transactionId")
    private String transactionId;

    /**
     * Payment type classification
     */
    @NotNull
    @JsonProperty("paymentType")
    private String paymentType; // NFC_PAYMENT, P2P_TRANSFER, BILL_PAYMENT, etc.

    /**
     * Transaction status at authorization
     */
    @NotNull
    @JsonProperty("status")
    private String status; // AUTHORIZED

    /**
     * Primary amount being authorized
     */
    @NotNull
    @JsonProperty("amount")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    /**
     * ISO currency code
     */
    @NotNull
    @JsonProperty("currency")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    /**
     * Processing fee charged for the transaction
     */
    @JsonProperty("processingFee")
    @Builder.Default
    private BigDecimal processingFee = BigDecimal.ZERO;

    // === Authorization Details ===

    /**
     * Authorization code from payment processor
     */
    @NotNull
    @JsonProperty("authorizationCode")
    private String authorizationCode;

    /**
     * Payment processor response code
     */
    @JsonProperty("processorResponseCode")
    private String processorResponseCode;

    /**
     * Timestamp when authorization was granted
     */
    @NotNull
    @JsonProperty("authorizedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant authorizedAt;

    /**
     * Name of the payment processor/provider
     */
    @JsonProperty("providerName")
    private String providerName;

    /**
     * Provider-specific transaction identifier
     */
    @JsonProperty("providerTransactionId")
    private String providerTransactionId;

    // === Party Information ===

    /**
     * Payer (customer) identifier
     */
    @NotNull
    @JsonProperty("payerId")
    private UUID payerId;

    /**
     * Payee (merchant/recipient) identifier
     */
    @NotNull
    @JsonProperty("payeeId")
    private UUID payeeId;

    /**
     * Payment method used
     */
    @JsonProperty("paymentMethodId")
    private UUID paymentMethodId;

    // === Risk and Fraud Information ===

    /**
     * Calculated fraud score (0.0 to 1.0)
     */
    @JsonProperty("fraudScore")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private BigDecimal fraudScore;

    /**
     * Risk level classification
     */
    @JsonProperty("riskLevel")
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    /**
     * Identified risk factors during authorization
     */
    @JsonProperty("riskFactors")
    private List<String> riskFactors;

    /**
     * Compliance flags and violations
     */
    @JsonProperty("complianceFlags")
    private String complianceFlags;

    /**
     * AML (Anti-Money Laundering) flags
     */
    @JsonProperty("amlFlags")
    private List<String> amlFlags;

    // === Balance and Reservation ===

    /**
     * Balance reservation identifier
     */
    @JsonProperty("reservationId")
    private String reservationId;

    /**
     * Timestamp when balance was reserved
     */
    @JsonProperty("reservedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant reservedAt;

    // === Transaction Context ===

    /**
     * Transaction description
     */
    @JsonProperty("description")
    private String description;

    /**
     * Transaction reference number
     */
    @JsonProperty("reference")
    private String reference;

    /**
     * Merchant-provided reference
     */
    @JsonProperty("merchantReference")
    private String merchantReference;

    // === Device and Location ===

    /**
     * Device identifier used for transaction
     */
    @JsonProperty("deviceId")
    private String deviceId;

    /**
     * IP address of transaction originator
     */
    @JsonProperty("ipAddress")
    private String ipAddress;

    /**
     * User agent string
     */
    @JsonProperty("userAgent")
    private String userAgent;

    /**
     * Geographic latitude
     */
    @JsonProperty("latitude")
    private Double latitude;

    /**
     * Geographic longitude
     */
    @JsonProperty("longitude")
    private Double longitude;

    /**
     * Human-readable location name
     */
    @JsonProperty("locationName")
    private String locationName;

    // === P2P Transfer Specific ===

    /**
     * Sender identifier for P2P transfers
     */
    @JsonProperty("senderId")
    private String senderId;

    /**
     * Recipient identifier for P2P transfers
     */
    @JsonProperty("recipientId")
    private String recipientId;

    /**
     * Transfer message for P2P transactions
     */
    @JsonProperty("transferMessage")
    private String transferMessage;

    /**
     * Transfer reference for P2P transactions
     */
    @JsonProperty("transferReference")
    private String transferReference;

    // === Processing Metrics ===

    /**
     * Authorization processing time in milliseconds
     */
    @JsonProperty("authorizationTimeMs")
    private Long authorizationTimeMs;

    /**
     * Total processing time up to authorization in milliseconds
     */
    @JsonProperty("processingTimeMs")
    private Long processingTimeMs;

    // === Additional Metadata ===

    /**
     * Additional metadata as key-value pairs
     */
    @JsonProperty("metadata")
    private Map<String, String> metadata;

    /**
     * Transaction tags for categorization
     */
    @JsonProperty("tags")
    private String tags;

    /**
     * Processing notes
     */
    @JsonProperty("notes")
    private String notes;

    // === Utility Methods ===

    /**
     * Get total amount including processing fees
     */
    public BigDecimal getTotalAmount() {
        return amount.add(processingFee != null ? processingFee : BigDecimal.ZERO);
    }

    /**
     * Check if transaction is high risk
     */
    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel) ||
               (fraudScore != null && fraudScore.compareTo(new BigDecimal("0.7")) >= 0);
    }

    /**
     * Check if transaction requires AML review
     */
    public boolean requiresAMLReview() {
        return amlFlags != null && !amlFlags.isEmpty();
    }

    /**
     * Check if transaction has compliance issues
     */
    public boolean hasComplianceIssues() {
        return complianceFlags != null && !complianceFlags.trim().isEmpty();
    }

    /**
     * Get processing latency category for analytics
     */
    public String getLatencyCategory() {
        if (processingTimeMs == null) return "UNKNOWN";
        if (processingTimeMs < 100) return "FAST";
        if (processingTimeMs < 500) return "NORMAL";
        if (processingTimeMs < 2000) return "SLOW";
        return "VERY_SLOW";
    }
}