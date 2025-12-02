package com.waqiti.billingorchestrator.entity;

import com.waqiti.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Account Credit Entity
 *
 * Tracks credits issued to customer accounts for refunds, goodwill, promotions.
 *
 * BUSINESS USE CASES:
 * - Refunds (service issues, billing errors)
 * - Goodwill credits (customer retention)
 * - Promotional credits (marketing campaigns)
 * - Referral bonuses
 * - Dispute resolutions
 *
 * AUTO-APPLICATION:
 * - Credits auto-apply to next invoice if autoApply = true
 * - Oldest credits applied first (FIFO)
 * - Partial credit application supported
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Entity
@Table(name = "account_credits", indexes = {
    @Index(name = "idx_credit_account", columnList = "account_id"),
    @Index(name = "idx_credit_status", columnList = "status"),
    @Index(name = "idx_credit_expiry", columnList = "expiry_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountCredit extends Auditable {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    // Credit amount
    @Column(name = "original_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal originalAmount;

    @Column(name = "remaining_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal remainingBalance;

    @Column(name = "currency", length = 3)
    private String currency;

    // Credit details
    @Column(name = "credit_type", length = 50)
    @Enumerated(EnumType.STRING)
    private CreditType creditType;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(name = "reference_id")
    private UUID referenceId;  // Invoice, dispute, or promotion ID

    // Status
    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private CreditStatus status;

    // Timing
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    // Application settings
    @Column(name = "auto_apply")
    private Boolean autoApply;

    @Column(name = "applied_to_invoice_id")
    private UUID appliedToInvoiceId;

    @Column(name = "issued_by")
    private UUID issuedBy;  // User ID who issued credit

    @Version
    private Long version;

    public enum CreditType {
        REFUND,                 // Service refund
        GOODWILL,               // Customer retention credit
        PROMOTIONAL,            // Marketing campaign
        REFERRAL_BONUS,         // Referral program
        DISPUTE_RESOLUTION,     // Billing dispute settlement
        SERVICE_CREDIT,         // Service downtime compensation
        MIGRATION_CREDIT,       // Migration incentive
        CHARGEBACK_REFUND      // Chargeback lost
    }

    public enum CreditStatus {
        ISSUED,                 // Credit issued, not yet applied
        PARTIALLY_APPLIED,      // Partially used
        FULLY_APPLIED,          // Completely used
        EXPIRED,                // Expired before use
        REFUNDED,               // Converted to cash refund
        CANCELLED               // Cancelled by admin
    }

    /**
     * Applies credit to invoice
     */
    public BigDecimal applyToInvoice(BigDecimal invoiceAmount, UUID invoiceId) {
        if (remainingBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal creditApplied = remainingBalance.min(invoiceAmount);
        remainingBalance = remainingBalance.subtract(creditApplied);

        if (remainingBalance.compareTo(BigDecimal.ZERO) == 0) {
            status = CreditStatus.FULLY_APPLIED;
            appliedAt = LocalDateTime.now();
        } else {
            status = CreditStatus.PARTIALLY_APPLIED;
        }

        appliedToInvoiceId = invoiceId;

        return creditApplied;
    }

    /**
     * Checks if credit is expired
     */
    public boolean isExpired() {
        return expiryDate != null && LocalDateTime.now().isAfter(expiryDate);
    }

    /**
     * Marks credit as expired
     */
    public void markExpired() {
        status = CreditStatus.EXPIRED;
        expiredAt = LocalDateTime.now();
    }
}
