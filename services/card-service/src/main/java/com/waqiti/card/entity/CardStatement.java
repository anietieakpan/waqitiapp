package com.waqiti.card.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CardStatement entity - Billing statement record
 * Represents a periodic billing statement for a card
 *
 * Statements include:
 * - Previous balance
 * - New transactions
 * - Payments received
 * - Fees and interest
 * - Minimum payment due
 * - Payment due date
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card_statement", indexes = {
    @Index(name = "idx_statement_id", columnList = "statement_id"),
    @Index(name = "idx_statement_card", columnList = "card_id"),
    @Index(name = "idx_statement_user", columnList = "user_id"),
    @Index(name = "idx_statement_period_start", columnList = "period_start_date"),
    @Index(name = "idx_statement_period_end", columnList = "period_end_date"),
    @Index(name = "idx_statement_due_date", columnList = "payment_due_date"),
    @Index(name = "idx_statement_status", columnList = "statement_status"),
    @Index(name = "idx_statement_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CardStatement extends BaseAuditEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ========================================================================
    // STATEMENT IDENTIFICATION
    // ========================================================================

    @Column(name = "statement_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Statement ID is required")
    private String statementId;

    @Column(name = "statement_number", length = 50)
    @Size(max = 50)
    private String statementNumber;

    @Column(name = "statement_year")
    @Min(2000)
    @Max(2100)
    private Integer statementYear;

    @Column(name = "statement_month")
    @Min(1)
    @Max(12)
    private Integer statementMonth;

    // ========================================================================
    // REFERENCES
    // ========================================================================

    @Column(name = "card_id", nullable = false)
    @NotNull(message = "Card ID is required")
    private UUID cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", insertable = false, updatable = false)
    private Card card;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;

    @Column(name = "account_id")
    private UUID accountId;

    // ========================================================================
    // STATEMENT PERIOD
    // ========================================================================

    @Column(name = "period_start_date", nullable = false)
    @NotNull
    private LocalDate periodStartDate;

    @Column(name = "period_end_date", nullable = false)
    @NotNull
    private LocalDate periodEndDate;

    @Column(name = "statement_date", nullable = false)
    @NotNull
    @Builder.Default
    private LocalDate statementDate = LocalDate.now();

    @Column(name = "days_in_period")
    @Min(1)
    @Max(366)
    private Integer daysInPeriod;

    // ========================================================================
    // STATEMENT STATUS
    // ========================================================================

    @Column(name = "statement_status", nullable = false, length = 30)
    @NotBlank(message = "Statement status is required")
    @Builder.Default
    private String statementStatus = "GENERATED";

    @Column(name = "is_current_statement")
    @Builder.Default
    private Boolean isCurrentStatement = false;

    @Column(name = "is_finalized")
    @Builder.Default
    private Boolean isFinalized = false;

    @Column(name = "finalized_date")
    private LocalDateTime finalizedDate;

    // ========================================================================
    // BALANCES
    // ========================================================================

    @Column(name = "previous_balance", precision = 18, scale = 2, nullable = false)
    @NotNull
    @Builder.Default
    private BigDecimal previousBalance = BigDecimal.ZERO;

    @Column(name = "new_charges", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal newCharges = BigDecimal.ZERO;

    @Column(name = "payments_received", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal paymentsReceived = BigDecimal.ZERO;

    @Column(name = "credits_applied", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal creditsApplied = BigDecimal.ZERO;

    @Column(name = "fees_charged", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal feesCharged = BigDecimal.ZERO;

    @Column(name = "interest_charged", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal interestCharged = BigDecimal.ZERO;

    @Column(name = "closing_balance", precision = 18, scale = 2, nullable = false)
    @NotNull
    @Builder.Default
    private BigDecimal closingBalance = BigDecimal.ZERO;

    @Column(name = "currency_code", length = 3, nullable = false)
    @NotBlank
    @Size(min = 3, max = 3)
    private String currencyCode;

    // ========================================================================
    // TRANSACTION SUMMARY
    // ========================================================================

    @Column(name = "total_purchases", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalPurchases = BigDecimal.ZERO;

    @Column(name = "total_cash_advances", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalCashAdvances = BigDecimal.ZERO;

    @Column(name = "total_balance_transfers", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalBalanceTransfers = BigDecimal.ZERO;

    @Column(name = "total_refunds", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalRefunds = BigDecimal.ZERO;

    @Column(name = "transaction_count")
    @Builder.Default
    private Integer transactionCount = 0;

    @Column(name = "purchase_count")
    @Builder.Default
    private Integer purchaseCount = 0;

    @Column(name = "payment_count")
    @Builder.Default
    private Integer paymentCount = 0;

    // ========================================================================
    // PAYMENT DETAILS
    // ========================================================================

    @Column(name = "payment_due_date", nullable = false)
    @NotNull
    private LocalDate paymentDueDate;

    @Column(name = "minimum_payment_due", precision = 18, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal minimumPaymentDue = BigDecimal.ZERO;

    @Column(name = "total_amount_due", precision = 18, scale = 2)
    private BigDecimal totalAmountDue;

    @Column(name = "past_due_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal pastDueAmount = BigDecimal.ZERO;

    @Column(name = "is_paid")
    @Builder.Default
    private Boolean isPaid = false;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "payment_amount", precision = 18, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "is_overdue")
    @Builder.Default
    private Boolean isOverdue = false;

    @Column(name = "days_overdue")
    @Min(0)
    private Integer daysOverdue;

    // ========================================================================
    // CREDIT DETAILS
    // ========================================================================

    @Column(name = "credit_limit", precision = 18, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "available_credit", precision = 18, scale = 2)
    private BigDecimal availableCredit;

    @Column(name = "credit_utilization_rate", precision = 5, scale = 4)
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal creditUtilizationRate;

    // ========================================================================
    // INTEREST & FEES DETAILS
    // ========================================================================

    @Column(name = "purchase_interest", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal purchaseInterest = BigDecimal.ZERO;

    @Column(name = "cash_advance_interest", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal cashAdvanceInterest = BigDecimal.ZERO;

    @Column(name = "balance_transfer_interest", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal balanceTransferInterest = BigDecimal.ZERO;

    @Column(name = "late_payment_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal latePaymentFee = BigDecimal.ZERO;

    @Column(name = "overlimit_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal overlimitFee = BigDecimal.ZERO;

    @Column(name = "annual_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal annualFee = BigDecimal.ZERO;

    @Column(name = "other_fees", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal otherFees = BigDecimal.ZERO;

    // ========================================================================
    // APR DETAILS
    // ========================================================================

    @Column(name = "purchase_apr", precision = 5, scale = 4)
    private BigDecimal purchaseApr;

    @Column(name = "cash_advance_apr", precision = 5, scale = 4)
    private BigDecimal cashAdvanceApr;

    @Column(name = "balance_transfer_apr", precision = 5, scale = 4)
    private BigDecimal balanceTransferApr;

    @Column(name = "penalty_apr", precision = 5, scale = 4)
    private BigDecimal penaltyApr;

    // ========================================================================
    // REWARDS
    // ========================================================================

    @Column(name = "rewards_earned", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal rewardsEarned = BigDecimal.ZERO;

    @Column(name = "rewards_redeemed", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal rewardsRedeemed = BigDecimal.ZERO;

    @Column(name = "rewards_balance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal rewardsBalance = BigDecimal.ZERO;

    // ========================================================================
    // DELIVERY
    // ========================================================================

    @Column(name = "delivery_method", length = 30)
    @Size(max = 30)
    private String deliveryMethod;

    @Column(name = "is_emailed")
    @Builder.Default
    private Boolean isEmailed = false;

    @Column(name = "email_sent_date")
    private LocalDateTime emailSentDate;

    @Column(name = "is_mailed")
    @Builder.Default
    private Boolean isMailed = false;

    @Column(name = "mail_sent_date")
    private LocalDateTime mailSentDate;

    @Column(name = "statement_file_url", length = 500)
    @Size(max = 500)
    private String statementFileUrl;

    @Column(name = "statement_file_path", length = 500)
    @Size(max = 500)
    private String statementFilePath;

    // ========================================================================
    // METADATA
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transaction_summary", columnDefinition = "jsonb")
    private Map<String, Object> transactionSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Calculate closing balance
     */
    public void calculateClosingBalance() {
        this.closingBalance = previousBalance
            .add(newCharges)
            .subtract(paymentsReceived)
            .subtract(creditsApplied)
            .add(feesCharged)
            .add(interestCharged);
    }

    /**
     * Calculate total interest
     */
    @Transient
    public BigDecimal getTotalInterest() {
        BigDecimal total = BigDecimal.ZERO;
        if (purchaseInterest != null) {
            total = total.add(purchaseInterest);
        }
        if (cashAdvanceInterest != null) {
            total = total.add(cashAdvanceInterest);
        }
        if (balanceTransferInterest != null) {
            total = total.add(balanceTransferInterest);
        }
        return total;
    }

    /**
     * Calculate total fees
     */
    @Transient
    public BigDecimal getTotalFees() {
        BigDecimal total = BigDecimal.ZERO;
        if (latePaymentFee != null) {
            total = total.add(latePaymentFee);
        }
        if (overlimitFee != null) {
            total = total.add(overlimitFee);
        }
        if (annualFee != null) {
            total = total.add(annualFee);
        }
        if (otherFees != null) {
            total = total.add(otherFees);
        }
        return total;
    }

    /**
     * Calculate credit utilization
     */
    public void calculateCreditUtilization() {
        if (creditLimit == null || creditLimit.compareTo(BigDecimal.ZERO) == 0) {
            this.creditUtilizationRate = BigDecimal.ZERO;
            return;
        }

        this.creditUtilizationRate = closingBalance
            .divide(creditLimit, 4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Check if statement is overdue
     */
    @Transient
    public boolean isStatementOverdue() {
        if (isPaid) {
            return false;
        }

        LocalDate now = LocalDate.now();
        return paymentDueDate != null && now.isAfter(paymentDueDate);
    }

    /**
     * Calculate days overdue
     */
    public void calculateDaysOverdue() {
        if (isStatementOverdue()) {
            LocalDate now = LocalDate.now();
            this.daysOverdue = (int) java.time.temporal.ChronoUnit.DAYS.between(paymentDueDate, now);
            this.isOverdue = true;
        } else {
            this.daysOverdue = 0;
            this.isOverdue = false;
        }
    }

    /**
     * Mark as paid
     */
    public void markAsPaid(BigDecimal amount, LocalDate date) {
        this.isPaid = true;
        this.paymentAmount = amount;
        this.paidDate = date;
        this.isOverdue = false;
        this.daysOverdue = 0;
    }

    /**
     * Finalize statement
     */
    public void finalizeStatement() {
        this.isFinalized = true;
        this.finalizedDate = LocalDateTime.now();
        this.statementStatus = "FINALIZED";

        calculateClosingBalance();
        calculateCreditUtilization();
        calculateDaysOverdue();
    }

    /**
     * Send email
     */
    public void sendEmail() {
        this.isEmailed = true;
        this.emailSentDate = LocalDateTime.now();
    }

    /**
     * Send mail
     */
    public void sendMail() {
        this.isMailed = true;
        this.mailSentDate = LocalDateTime.now();
    }

    /**
     * Check if minimum payment is met
     */
    @Transient
    public boolean isMinimumPaymentMet() {
        if (paymentAmount == null || minimumPaymentDue == null) {
            return false;
        }
        return paymentAmount.compareTo(minimumPaymentDue) >= 0;
    }

    /**
     * Check if balance is paid in full
     */
    @Transient
    public boolean isPaidInFull() {
        if (paymentAmount == null || closingBalance == null) {
            return false;
        }
        return paymentAmount.compareTo(closingBalance) >= 0;
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (statementStatus == null) {
            statementStatus = "GENERATED";
        }
        if (statementDate == null) {
            statementDate = LocalDate.now();
        }
        if (isCurrentStatement == null) {
            isCurrentStatement = false;
        }
        if (isFinalized == null) {
            isFinalized = false;
        }
        if (previousBalance == null) {
            previousBalance = BigDecimal.ZERO;
        }
        if (newCharges == null) {
            newCharges = BigDecimal.ZERO;
        }
        if (paymentsReceived == null) {
            paymentsReceived = BigDecimal.ZERO;
        }
        if (creditsApplied == null) {
            creditsApplied = BigDecimal.ZERO;
        }
        if (feesCharged == null) {
            feesCharged = BigDecimal.ZERO;
        }
        if (interestCharged == null) {
            interestCharged = BigDecimal.ZERO;
        }
        if (closingBalance == null) {
            closingBalance = BigDecimal.ZERO;
        }
        if (minimumPaymentDue == null) {
            minimumPaymentDue = BigDecimal.ZERO;
        }
        if (isPaid == null) {
            isPaid = false;
        }
        if (isOverdue == null) {
            isOverdue = false;
        }
        if (isEmailed == null) {
            isEmailed = false;
        }
        if (isMailed == null) {
            isMailed = false;
        }

        // Calculate period length
        if (periodStartDate != null && periodEndDate != null) {
            this.daysInPeriod = (int) java.time.temporal.ChronoUnit.DAYS.between(periodStartDate, periodEndDate) + 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
        // Recalculate overdue status
        calculateDaysOverdue();
    }
}
