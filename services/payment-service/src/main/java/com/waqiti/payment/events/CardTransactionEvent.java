package com.waqiti.payment.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Card Transaction Event
 * 
 * Represents card transaction events from card processing networks and issuers.
 * These events are consumed by payment-service for payment processing integration,
 * fraud detection, rewards calculation, and transaction reconciliation.
 * 
 * Event Sources:
 * - Card processors (Visa, Mastercard, Discover, Amex)
 * - Card issuing partners
 * - ATM networks
 * - Point-of-sale systems
 * 
 * Consumer Services:
 * - payment-service: Payment processing and reconciliation
 * - fraud-service: Real-time fraud detection and analysis
 * - rewards-service: Points calculation and rewards processing
 * - analytics-service: Transaction analytics and reporting
 * 
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardTransactionEvent {

    /**
     * Unique event identifier for deduplication
     */
    @NotNull
    @JsonProperty("eventId")
    private String eventId;

    /**
     * Correlation ID for transaction tracing
     */
    @NotNull
    @JsonProperty("correlationId")
    private String correlationId;

    /**
     * Event timestamp
     */
    @NotNull
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * Event version for schema evolution
     */
    @NotNull
    @JsonProperty("eventVersion")
    @Builder.Default
    private String eventVersion = "1.0";

    /**
     * Source system that published the event
     */
    @NotNull
    @JsonProperty("source")
    private String source;

    // === Core Transaction Details ===

    /**
     * Unique transaction identifier
     */
    @NotNull
    @JsonProperty("transactionId")
    private UUID transactionId;

    /**
     * User/cardholder identifier
     */
    @NotNull
    @JsonProperty("userId")
    private UUID userId;

    /**
     * Card identifier
     */
    @NotNull
    @JsonProperty("cardId")
    private UUID cardId;

    /**
     * Masked card number (last 4 digits)
     */
    @JsonProperty("cardNumber")
    private String cardNumber;

    /**
     * Transaction status
     */
    @NotNull
    @JsonProperty("transactionStatus")
    private String transactionStatus; // AUTHORIZED, DECLINED, PENDING, COMPLETED, REVERSED

    /**
     * Type of card transaction
     */
    @NotNull
    @JsonProperty("transactionType")
    private String transactionType; // PURCHASE, WITHDRAWAL, REFUND, AUTHORIZATION, SETTLEMENT

    /**
     * Transaction amount
     */
    @NotNull
    @JsonProperty("amount")
    @DecimalMin(value = "0.00", message = "Amount must be non-negative")
    private BigDecimal amount;

    /**
     * ISO currency code
     */
    @NotNull
    @JsonProperty("currency")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    /**
     * Original transaction amount in merchant currency
     */
    @JsonProperty("originalAmount")
    private BigDecimal originalAmount;

    /**
     * Original currency code
     */
    @JsonProperty("originalCurrency")
    private String originalCurrency;

    /**
     * Exchange rate applied
     */
    @JsonProperty("exchangeRate")
    private BigDecimal exchangeRate;

    // === Transaction Timestamp ===

    /**
     * When the transaction occurred
     */
    @NotNull
    @JsonProperty("transactionTimestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime transactionTimestamp;

    /**
     * Authorization timestamp
     */
    @JsonProperty("authorizationTimestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime authorizationTimestamp;

    /**
     * Settlement timestamp
     */
    @JsonProperty("settlementTimestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime settlementTimestamp;

    // === Authorization Details ===

    /**
     * Authorization code from card network
     */
    @JsonProperty("authorizationCode")
    private String authorizationCode;

    /**
     * Response code from authorization
     */
    @JsonProperty("responseCode")
    private String responseCode;

    /**
     * Decline reason if transaction was declined
     */
    @JsonProperty("declineReason")
    private String declineReason;

    /**
     * Authorization response code
     */
    @JsonProperty("authorizationResponseCode")
    private String authorizationResponseCode;

    // === Merchant Information ===

    /**
     * Merchant identifier
     */
    @JsonProperty("merchantId")
    private String merchantId;

    /**
     * Merchant name
     */
    @JsonProperty("merchantName")
    private String merchantName;

    /**
     * Merchant category code (MCC)
     */
    @JsonProperty("merchantCategory")
    private String merchantCategory;

    /**
     * Merchant country
     */
    @JsonProperty("merchantCountry")
    private String merchantCountry;

    /**
     * Merchant city
     */
    @JsonProperty("merchantCity")
    private String merchantCity;

    /**
     * Merchant state/province
     */
    @JsonProperty("merchantState")
    private String merchantState;

    /**
     * Merchant postal code
     */
    @JsonProperty("merchantPostalCode")
    private String merchantPostalCode;

    // === Transaction Context ===

    /**
     * Whether transaction is international
     */
    @JsonProperty("isInternational")
    @Builder.Default
    private Boolean isInternational = false;

    /**
     * Whether transaction used contactless technology
     */
    @JsonProperty("isContactless")
    @Builder.Default
    private Boolean isContactless = false;

    /**
     * Whether transaction was online/card-not-present
     */
    @JsonProperty("isOnline")
    @Builder.Default
    private Boolean isOnline = false;

    /**
     * Whether transaction was recurring
     */
    @JsonProperty("isRecurring")
    @Builder.Default
    private Boolean isRecurring = false;

    /**
     * Point of sale entry mode
     */
    @JsonProperty("posEntryMode")
    private String posEntryMode; // CHIP, SWIPE, CONTACTLESS, ONLINE, MANUAL

    /**
     * Card verification method
     */
    @JsonProperty("cardVerificationMethod")
    private String cardVerificationMethod; // PIN, SIGNATURE, BIOMETRIC, NONE

    // === Risk and Fraud ===

    /**
     * Risk score (0-100)
     */
    @JsonProperty("riskScore")
    @Min(0)
    @Max(100)
    private Integer riskScore;

    /**
     * Risk level assessment
     */
    @JsonProperty("riskLevel")
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    /**
     * Fraud indicators
     */
    @JsonProperty("fraudIndicators")
    private String fraudIndicators;

    /**
     * Whether transaction triggered fraud alerts
     */
    @JsonProperty("fraudAlert")
    @Builder.Default
    private Boolean fraudAlert = false;

    /**
     * 3DS authentication status
     */
    @JsonProperty("threeDSStatus")
    private String threeDSStatus; // SUCCESS, FAILED, BYPASSED, NOT_APPLICABLE

    // === Network and Processor Details ===

    /**
     * Card network (Visa, Mastercard, etc.)
     */
    @JsonProperty("cardNetwork")
    private String cardNetwork;

    /**
     * Processing institution ID
     */
    @JsonProperty("processingInstitutionId")
    private String processingInstitutionId;

    /**
     * Acquiring bank ID
     */
    @JsonProperty("acquiringBankId")
    private String acquiringBankId;

    /**
     * Issuing bank ID
     */
    @JsonProperty("issuingBankId")
    private String issuingBankId;

    /**
     * Network reference number
     */
    @JsonProperty("networkReferenceNumber")
    private String networkReferenceNumber;

    /**
     * Retrieval reference number
     */
    @JsonProperty("retrievalReferenceNumber")
    private String retrievalReferenceNumber;

    // === Balance Impact ===

    /**
     * Available balance after transaction
     */
    @JsonProperty("availableBalance")
    private BigDecimal availableBalance;

    /**
     * Account balance after transaction
     */
    @JsonProperty("accountBalance")
    private BigDecimal accountBalance;

    /**
     * Credit limit if applicable
     */
    @JsonProperty("creditLimit")
    private BigDecimal creditLimit;

    /**
     * Credit available after transaction
     */
    @JsonProperty("creditAvailable")
    private BigDecimal creditAvailable;

    // === Fees and Charges ===

    /**
     * Transaction fee charged
     */
    @JsonProperty("transactionFee")
    @Builder.Default
    private BigDecimal transactionFee = BigDecimal.ZERO;

    /**
     * Foreign transaction fee
     */
    @JsonProperty("foreignTransactionFee")
    @Builder.Default
    private BigDecimal foreignTransactionFee = BigDecimal.ZERO;

    /**
     * ATM fee if applicable
     */
    @JsonProperty("atmFee")
    @Builder.Default
    private BigDecimal atmFee = BigDecimal.ZERO;

    /**
     * Other fees
     */
    @JsonProperty("otherFees")
    @Builder.Default
    private BigDecimal otherFees = BigDecimal.ZERO;

    // === ATM Specific Fields ===

    /**
     * ATM ID for ATM transactions
     */
    @JsonProperty("atmId")
    private String atmId;

    /**
     * ATM location
     */
    @JsonProperty("atmLocation")
    private String atmLocation;

    /**
     * ATM network
     */
    @JsonProperty("atmNetwork")
    private String atmNetwork;

    // === Metadata ===

    /**
     * Additional transaction metadata
     */
    @JsonProperty("metadata")
    private Map<String, String> metadata;

    /**
     * Transaction description/memo
     */
    @JsonProperty("description")
    private String description;

    /**
     * Transaction reference number
     */
    @JsonProperty("referenceNumber")
    private String referenceNumber;

    // === Utility Methods ===

    /**
     * Check if transaction was approved
     */
    public boolean isApproved() {
        return "AUTHORIZED".equals(transactionStatus) || "COMPLETED".equals(transactionStatus);
    }

    /**
     * Check if transaction was declined
     */
    public boolean isDeclined() {
        return "DECLINED".equals(transactionStatus);
    }

    /**
     * Check if transaction is high risk
     */
    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel) || 
               (riskScore != null && riskScore >= 70);
    }

    /**
     * Check if transaction is international
     */
    public boolean isInternationalTransaction() {
        return Boolean.TRUE.equals(isInternational);
    }

    /**
     * Check if transaction requires 3DS authentication
     */
    public boolean requires3DS() {
        return Boolean.TRUE.equals(isOnline) && 
               ("SUCCESS".equals(threeDSStatus) || "FAILED".equals(threeDSStatus));
    }

    /**
     * Get total fees
     */
    public BigDecimal getTotalFees() {
        BigDecimal total = transactionFee != null ? transactionFee : BigDecimal.ZERO;
        total = total.add(foreignTransactionFee != null ? foreignTransactionFee : BigDecimal.ZERO);
        total = total.add(atmFee != null ? atmFee : BigDecimal.ZERO);
        total = total.add(otherFees != null ? otherFees : BigDecimal.ZERO);
        return total;
    }

    /**
     * Get net transaction amount (amount minus fees)
     */
    public BigDecimal getNetAmount() {
        return amount.subtract(getTotalFees());
    }
}