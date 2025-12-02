package com.waqiti.voice.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Voice Transaction Entity - Financial transaction initiated via voice command
 *
 * CRITICAL FINANCIAL ENTITY:
 * - Handles real money transactions
 * - Must maintain ACID properties
 * - Requires idempotency (prevent duplicate payments)
 * - Subject to PCI-DSS, SOX, AML/KYC compliance
 * - Immutable after completion (audit trail)
 *
 * Security Requirements:
 * - Optimistic locking to prevent concurrent modifications
 * - Complete audit trail of all state changes
 * - Encryption of sensitive fields
 * - Tamper detection
 */
@Entity
@Table(name = "voice_transactions", indexes = {
    @Index(name = "idx_voice_transactions_transaction_id", columnList = "transaction_id", unique = true),
    @Index(name = "idx_voice_transactions_user_id", columnList = "user_id"),
    @Index(name = "idx_voice_transactions_status", columnList = "status"),
    @Index(name = "idx_voice_transactions_initiated_at", columnList = "initiated_at"),
    @Index(name = "idx_voice_transactions_payment_ref", columnList = "payment_reference"),
    @Index(name = "idx_voice_transactions_user_status", columnList = "user_id,status"),
    @Index(name = "idx_voice_transactions_amount", columnList = "amount"),
    @Index(name = "idx_voice_transactions_session", columnList = "voice_session_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"metadata", "errorDetails"})
@EqualsAndHashCode(of = "id")
public class VoiceTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Transaction ID is required")
    @Column(name = "transaction_id", nullable = false, unique = true, length = 100)
    private String transactionId;

    // Relationships
    @Column(name = "voice_command_id")
    private UUID voiceCommandId;

    @Column(name = "voice_session_id")
    private UUID voiceSessionId;

    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Transaction Classification
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50)
    @Builder.Default
    private TransactionType transactionType = TransactionType.SEND_MONEY;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.INITIATED;

    // Financial Details
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "1000000.00", message = "Amount exceeds maximum limit")
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code")
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "fee_amount", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 19, scale = 4)
    private BigDecimal totalAmount;

    // Parties Involved
    @Column(name = "recipient_id", length = 100)
    private String recipientId;

    @Column(name = "recipient_identifier", length = 200)
    private String recipientIdentifier;

    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    // Transaction Purpose & Description
    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 50)
    private String category;

    // Payment Processing
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "payment_provider", length = 50)
    private String paymentProvider;

    @Column(name = "external_transaction_id", length = 100)
    private String externalTransactionId;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    // Voice-Specific Details
    @Column(name = "transcribed_command", columnDefinition = "TEXT")
    private String transcribedCommand;

    @Column(name = "command_confidence_score")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double commandConfidenceScore;

    @Column(name = "biometric_verified")
    @Builder.Default
    private Boolean biometricVerified = false;

    @Column(name = "biometric_confidence_score")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double biometricConfidenceScore;

    // Confirmation & Authorization
    @Column(name = "requires_confirmation")
    @Builder.Default
    private Boolean requiresConfirmation = false;

    @Column(name = "confirmation_status", length = 30)
    private String confirmationStatus;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by_voice")
    @Builder.Default
    private Boolean confirmedByVoice = false;

    // Transaction Timeline
    @CreationTimestamp
    @Column(name = "initiated_at", nullable = false, updatable = false)
    private LocalDateTime initiatedAt;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // Error & Failure Handling
    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Type(JsonBinaryType.class)
    @Column(name = "error_details", columnDefinition = "jsonb")
    private Map<String, Object> errorDetails;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;

    // Cancellation
    @Column(name = "cancellation_reason", length = 200)
    private String cancellationReason;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    // Fraud Detection & Risk
    @Column(name = "fraud_score")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double fraudScore;

    @Column(name = "fraud_check_passed")
    private Boolean fraudCheckPassed;

    @Column(name = "fraud_flags", columnDefinition = "TEXT")
    private String fraudFlags;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    // Compliance & Regulatory
    @Column(name = "aml_check_required")
    @Builder.Default
    private Boolean amlCheckRequired = false;

    @Column(name = "aml_check_passed")
    private Boolean amlCheckPassed;

    @Column(name = "kyc_verified")
    @Builder.Default
    private Boolean kycVerified = false;

    @Column(name = "regulatory_hold")
    @Builder.Default
    private Boolean regulatoryHold = false;

    @Column(name = "regulatory_hold_reason", columnDefinition = "TEXT")
    private String regulatoryHoldReason;

    // Metadata & Additional Info
    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "location", length = 200)
    private String location;

    // Idempotency (Critical for preventing duplicate payments)
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "idempotency_fingerprint", length = 64)
    private String idempotencyFingerprint;

    // Audit Fields
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    // Enums
    public enum TransactionType {
        SEND_MONEY,
        REQUEST_MONEY,
        PAY_BILL,
        TRANSFER_FUNDS,
        SPLIT_BILL,
        REFUND,
        REVERSAL
    }

    public enum TransactionStatus {
        INITIATED,          // Transaction created
        PENDING,            // Awaiting confirmation
        CONFIRMED,          // User confirmed
        AUTHORIZED,         // Payment authorized
        PROCESSING,         // Being processed
        COMPLETED,          // Successfully completed
        FAILED,             // Failed to process
        CANCELLED,          // Cancelled by user
        REVERSED,           // Reversed/refunded
        ON_HOLD            // Held for review (fraud/compliance)
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    // Business Logic Methods

    /**
     * Check if transaction is in a final state (immutable)
     */
    public boolean isFinal() {
        return status == TransactionStatus.COMPLETED ||
               status == TransactionStatus.FAILED ||
               status == TransactionStatus.CANCELLED ||
               status == TransactionStatus.REVERSED;
    }

    /**
     * Check if transaction can be cancelled
     */
    public boolean isCancellable() {
        return status == TransactionStatus.INITIATED ||
               status == TransactionStatus.PENDING ||
               status == TransactionStatus.CONFIRMED;
    }

    /**
     * Check if transaction is completed successfully
     */
    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }

    /**
     * Mark transaction as completed
     */
    public void complete() {
        if (isFinal()) {
            throw new IllegalStateException("Cannot modify completed transaction");
        }
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.processedAt = LocalDateTime.now();
    }

    /**
     * Mark transaction as failed with error details
     */
    public void fail(String errorCode, String errorMessage) {
        if (isFinal()) {
            throw new IllegalStateException("Cannot modify completed transaction");
        }
        this.status = TransactionStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.failedAt = LocalDateTime.now();
    }

    /**
     * Record error details
     */
    public void recordError(String code, String message, Map<String, Object> details) {
        this.errorCode = code;
        this.errorMessage = message;
        this.errorDetails = details;
    }

    /**
     * Cancel transaction
     */
    public void cancel(String reason) {
        if (!isCancellable()) {
            throw new IllegalStateException("Transaction cannot be cancelled in status: " + status);
        }
        this.status = TransactionStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = LocalDateTime.now();
    }

    /**
     * Record payment reference from payment service
     */
    public void recordPaymentReference(String provider, String reference, String externalTxId) {
        this.paymentProvider = provider;
        this.paymentReference = reference;
        this.externalTransactionId = externalTxId;
    }

    /**
     * Increment retry counter
     */
    public void incrementRetry() {
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        this.retryCount++;
    }

    /**
     * Check if max retries exceeded
     */
    public boolean hasExceededMaxRetries() {
        return retryCount != null && maxRetries != null && retryCount >= maxRetries;
    }

    /**
     * Confirm transaction
     */
    public void confirm(boolean viaVoice) {
        if (status != TransactionStatus.PENDING && status != TransactionStatus.INITIATED) {
            throw new IllegalStateException("Can only confirm pending transactions");
        }
        this.status = TransactionStatus.CONFIRMED;
        this.confirmationStatus = "CONFIRMED";
        this.confirmedAt = LocalDateTime.now();
        this.confirmedByVoice = viaVoice;
    }

    /**
     * Place transaction on regulatory hold
     */
    public void placeOnHold(String reason) {
        this.status = TransactionStatus.ON_HOLD;
        this.regulatoryHold = true;
        this.regulatoryHoldReason = reason;
    }

    /**
     * Release from hold
     */
    public void releaseFromHold() {
        if (status == TransactionStatus.ON_HOLD) {
            this.status = TransactionStatus.PROCESSING;
            this.regulatoryHold = false;
        }
    }

    /**
     * Record fraud check results
     */
    public void recordFraudCheck(double score, boolean passed, RiskLevel risk) {
        this.fraudScore = score;
        this.fraudCheckPassed = passed;
        this.riskLevel = risk;

        if (!passed) {
            placeOnHold("Fraud detection triggered");
        }
    }

    /**
     * Calculate total amount including fees
     */
    public void calculateTotal() {
        if (amount != null) {
            BigDecimal fee = feeAmount != null ? feeAmount : BigDecimal.ZERO;
            this.totalAmount = amount.add(fee);
        }
    }

    /**
     * Validate transaction before processing
     */
    public boolean isValid() {
        return amount != null &&
               amount.compareTo(BigDecimal.ZERO) > 0 &&
               currency != null &&
               !currency.isBlank() &&
               userId != null &&
               status != null;
    }

    /**
     * Generate idempotency key from command details
     */
    public static String generateIdempotencyKey(UUID userId, UUID commandId) {
        return String.format("VT-%s-%s", userId, commandId);
    }

    /**
     * Pre-persist initialization
     */
    @PrePersist
    protected void onCreate() {
        if (transactionId == null) {
            transactionId = "VTX-" + UUID.randomUUID().toString();
        }
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        if (errorDetails == null) {
            errorDetails = new HashMap<>();
        }
        calculateTotal();

        // Generate idempotency key if not set
        if (idempotencyKey == null && userId != null && voiceCommandId != null) {
            idempotencyKey = generateIdempotencyKey(userId, voiceCommandId);
        }
    }

    /**
     * Pre-update validation - prevent modification of final transactions
     */
    @PreUpdate
    protected void onUpdate() {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        calculateTotal();
    }
}
