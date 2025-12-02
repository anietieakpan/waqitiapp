package com.waqiti.card.entity;

import com.waqiti.card.enums.AuthorizationStatus;
import com.waqiti.card.enums.DeclineReason;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CardAuthorization entity - Authorization record
 * Represents a card authorization request and response
 *
 * Authorizations are created before transactions are completed.
 * They reserve funds and perform fraud/risk checks.
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card_authorization", indexes = {
    @Index(name = "idx_authorization_id", columnList = "authorization_id"),
    @Index(name = "idx_authorization_card", columnList = "card_id"),
    @Index(name = "idx_authorization_transaction", columnList = "transaction_id"),
    @Index(name = "idx_authorization_status", columnList = "authorization_status"),
    @Index(name = "idx_authorization_date", columnList = "authorization_date"),
    @Index(name = "idx_authorization_expiry", columnList = "expiry_date"),
    @Index(name = "idx_authorization_code", columnList = "authorization_code"),
    @Index(name = "idx_authorization_idempotency", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_authorization_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CardAuthorization extends BaseAuditEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ========================================================================
    // AUTHORIZATION IDENTIFICATION
    // ========================================================================

    @Column(name = "authorization_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Authorization ID is required")
    private String authorizationId;

    @Column(name = "authorization_code", length = 6)
    @Size(max = 6)
    private String authorizationCode;

    @Column(name = "external_authorization_id", length = 100)
    @Size(max = 100)
    private String externalAuthorizationId;

    @Column(name = "idempotency_key", unique = true, length = 64)
    @Size(max = 64)
    private String idempotencyKey;

    // ========================================================================
    // REFERENCES
    // ========================================================================

    @Column(name = "transaction_id")
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", insertable = false, updatable = false)
    private CardTransaction transaction;

    @Column(name = "card_id", nullable = false)
    @NotNull(message = "Card ID is required")
    private UUID cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", insertable = false, updatable = false)
    private Card card;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;

    // ========================================================================
    // AUTHORIZATION DETAILS
    // ========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_status", nullable = false, length = 30)
    @NotNull(message = "Authorization status is required")
    @Builder.Default
    private AuthorizationStatus authorizationStatus = AuthorizationStatus.PENDING;

    @Column(name = "authorization_date", nullable = false)
    @NotNull
    @Builder.Default
    private LocalDateTime authorizationDate = LocalDateTime.now();

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(name = "is_expired")
    @Builder.Default
    private Boolean isExpired = false;

    // ========================================================================
    // FINANCIAL DETAILS
    // ========================================================================

    @Column(name = "authorization_amount", precision = 18, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal authorizationAmount;

    @Column(name = "currency_code", length = 3, nullable = false)
    @NotBlank
    @Size(min = 3, max = 3)
    private String currencyCode;

    @Column(name = "approved_amount", precision = 18, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "captured_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal capturedAmount = BigDecimal.ZERO;

    @Column(name = "remaining_amount", precision = 18, scale = 2)
    private BigDecimal remainingAmount;

    // ========================================================================
    // BALANCE SNAPSHOT
    // ========================================================================

    @Column(name = "available_balance_before", precision = 18, scale = 2)
    private BigDecimal availableBalanceBefore;

    @Column(name = "available_balance_after", precision = 18, scale = 2)
    private BigDecimal availableBalanceAfter;

    @Column(name = "credit_limit", precision = 18, scale = 2)
    private BigDecimal creditLimit;

    // ========================================================================
    // RISK & FRAUD ASSESSMENT
    // ========================================================================

    @Column(name = "risk_score", precision = 5, scale = 2)
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal riskScore;

    @Column(name = "risk_level", length = 20)
    @Size(max = 20)
    private String riskLevel;

    @Column(name = "fraud_check_result", length = 20)
    @Size(max = 20)
    private String fraudCheckResult;

    @Column(name = "fraud_check_passed")
    private Boolean fraudCheckPassed;

    @Column(name = "velocity_check_result", length = 20)
    @Size(max = 20)
    private String velocityCheckResult;

    @Column(name = "velocity_check_passed")
    private Boolean velocityCheckPassed;

    @Column(name = "limit_check_passed")
    private Boolean limitCheckPassed;

    @Column(name = "three_ds_check_passed")
    private Boolean threeDsCheckPassed;

    @Column(name = "cvv_check_result", length = 1)
    @Size(max = 1)
    private String cvvCheckResult;

    @Column(name = "avs_check_result", length = 1)
    @Size(max = 1)
    private String avsCheckResult;

    // ========================================================================
    // DECLINE DETAILS
    // ========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "decline_reason", length = 50)
    private DeclineReason declineReason;

    @Column(name = "decline_message", length = 255)
    @Size(max = 255)
    private String declineMessage;

    @Column(name = "decline_code", length = 10)
    @Size(max = 10)
    private String declineCode;

    // ========================================================================
    // PROCESSOR & NETWORK RESPONSES
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "processor_response", columnDefinition = "jsonb")
    private Map<String, Object> processorResponse;

    @Column(name = "processor_response_code", length = 10)
    @Size(max = 10)
    private String processorResponseCode;

    @Column(name = "processor_response_message", length = 255)
    @Size(max = 255)
    private String processorResponseMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "network_response", columnDefinition = "jsonb")
    private Map<String, Object> networkResponse;

    @Column(name = "network_response_code", length = 10)
    @Size(max = 10)
    private String networkResponseCode;

    @Column(name = "network_transaction_id", length = 100)
    @Size(max = 100)
    private String networkTransactionId;

    // ========================================================================
    // MERCHANT DETAILS
    // ========================================================================

    @Column(name = "merchant_id", length = 100)
    @Size(max = 100)
    private String merchantId;

    @Column(name = "merchant_name", length = 255)
    @Size(max = 255)
    private String merchantName;

    @Column(name = "merchant_category_code", length = 4)
    @Size(min = 4, max = 4)
    private String merchantCategoryCode;

    @Column(name = "merchant_country", length = 3)
    @Size(min = 2, max = 3)
    private String merchantCountry;

    // ========================================================================
    // POINT OF SALE DATA
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pos_data", columnDefinition = "jsonb")
    private Map<String, Object> posData;

    @Column(name = "pos_entry_mode", length = 3)
    @Size(max = 3)
    private String posEntryMode;

    @Column(name = "terminal_id", length = 50)
    @Size(max = 50)
    private String terminalId;

    @Column(name = "is_contactless")
    private Boolean isContactless;

    @Column(name = "is_online")
    private Boolean isOnline;

    @Column(name = "is_international")
    private Boolean isInternational;

    @Column(name = "is_card_present")
    private Boolean isCardPresent;

    // ========================================================================
    // CAPTURE & SETTLEMENT
    // ========================================================================

    @Column(name = "is_captured")
    @Builder.Default
    private Boolean isCaptured = false;

    @Column(name = "capture_date")
    private LocalDateTime captureDate;

    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;

    @Column(name = "is_partial_capture")
    @Builder.Default
    private Boolean isPartialCapture = false;

    // ========================================================================
    // REVERSAL
    // ========================================================================

    @Column(name = "is_reversed")
    @Builder.Default
    private Boolean isReversed = false;

    @Column(name = "reversal_date")
    private LocalDateTime reversalDate;

    @Column(name = "reversal_reason", length = 255)
    @Size(max = 255)
    private String reversalReason;

    @Column(name = "reversed_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal reversedAmount = BigDecimal.ZERO;

    // ========================================================================
    // METADATA
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if authorization is approved
     */
    @Transient
    public boolean isApproved() {
        return authorizationStatus == AuthorizationStatus.APPROVED ||
               authorizationStatus == AuthorizationStatus.PARTIAL_APPROVAL;
    }

    /**
     * Check if authorization is declined
     */
    @Transient
    public boolean isDeclined() {
        return authorizationStatus == AuthorizationStatus.DECLINED ||
               authorizationStatus == AuthorizationStatus.FRAUD_BLOCKED;
    }

    /**
     * Check if authorization is active (approved and not captured/expired)
     */
    @Transient
    public boolean isActive() {
        return isApproved() &&
               !isCaptured &&
               !isReversed &&
               !isAuthorizationExpired() &&
               deletedAt == null;
    }

    /**
     * Check if authorization has expired
     */
    @Transient
    public boolean isAuthorizationExpired() {
        if (expiryDate == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expiryDate);
    }

    /**
     * Check if partial approval
     */
    @Transient
    public boolean isPartialApproval() {
        return authorizationStatus == AuthorizationStatus.PARTIAL_APPROVAL ||
               (approvedAmount != null && authorizationAmount != null &&
                approvedAmount.compareTo(authorizationAmount) < 0);
    }

    /**
     * Get available capture amount
     */
    @Transient
    public BigDecimal getAvailableCaptureAmount() {
        if (approvedAmount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal captured = capturedAmount != null ? capturedAmount : BigDecimal.ZERO;
        BigDecimal reversed = reversedAmount != null ? reversedAmount : BigDecimal.ZERO;
        return approvedAmount.subtract(captured).subtract(reversed);
    }

    /**
     * Approve authorization
     */
    public void approve(BigDecimal amount) {
        this.authorizationStatus = AuthorizationStatus.APPROVED;
        this.approvedAmount = amount;
        this.remainingAmount = amount;
        // Set expiry to 7 days from now (typical authorization hold period)
        this.expiryDate = LocalDateTime.now().plusDays(7);
    }

    /**
     * Partially approve authorization
     */
    public void partialApprove(BigDecimal amount) {
        this.authorizationStatus = AuthorizationStatus.PARTIAL_APPROVAL;
        this.approvedAmount = amount;
        this.remainingAmount = amount;
        this.expiryDate = LocalDateTime.now().plusDays(7);
    }

    /**
     * Decline authorization
     */
    public void decline(DeclineReason reason, String message) {
        this.authorizationStatus = AuthorizationStatus.DECLINED;
        this.declineReason = reason;
        this.declineMessage = message;
        if (reason != null) {
            this.declineCode = reason.getResponseCode();
        }
    }

    /**
     * Capture authorization (convert to transaction)
     */
    public void capture(BigDecimal amount) {
        if (!isApproved()) {
            throw new IllegalStateException("Cannot capture non-approved authorization");
        }
        if (amount.compareTo(getAvailableCaptureAmount()) > 0) {
            throw new IllegalArgumentException("Capture amount exceeds available amount");
        }

        this.capturedAmount = this.capturedAmount.add(amount);
        this.remainingAmount = this.remainingAmount.subtract(amount);
        this.captureDate = LocalDateTime.now();

        if (this.remainingAmount.compareTo(BigDecimal.ZERO) == 0) {
            this.isCaptured = true;
            this.authorizationStatus = AuthorizationStatus.CAPTURED;
        } else {
            this.isPartialCapture = true;
        }
    }

    /**
     * Reverse authorization (release hold)
     */
    public void reverse(String reason) {
        this.isReversed = true;
        this.reversalDate = LocalDateTime.now();
        this.reversalReason = reason;
        this.authorizationStatus = AuthorizationStatus.REVERSED;

        BigDecimal amountToReverse = getAvailableCaptureAmount();
        this.reversedAmount = this.reversedAmount.add(amountToReverse);
        this.remainingAmount = BigDecimal.ZERO;
    }

    /**
     * Mark as expired
     */
    public void markExpired() {
        this.isExpired = true;
        this.authorizationStatus = AuthorizationStatus.EXPIRED;
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (authorizationStatus == null) {
            authorizationStatus = AuthorizationStatus.PENDING;
        }
        if (authorizationDate == null) {
            authorizationDate = LocalDateTime.now();
        }
        if (isCaptured == null) {
            isCaptured = false;
        }
        if (isReversed == null) {
            isReversed = false;
        }
        if (isExpired == null) {
            isExpired = false;
        }
        if (isPartialCapture == null) {
            isPartialCapture = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
        // Check if authorization has expired
        if (!isExpired && isAuthorizationExpired()) {
            markExpired();
        }
    }
}
