package com.waqiti.billingorchestrator.entity;

import com.waqiti.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Billing Cycle Entity
 * Represents a complete billing period for a customer
 *
 * CRITICAL FINANCIAL ENTITY - All monetary fields use DECIMAL(19,4) precision
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Entity
@Table(name = "billing_cycles", indexes = {
    @Index(name = "idx_billing_cycle_customer", columnList = "customer_id"),
    @Index(name = "idx_billing_cycle_account", columnList = "account_id"),
    @Index(name = "idx_billing_cycle_status", columnList = "status"),
    @Index(name = "idx_billing_cycle_dates", columnList = "cycle_start_date, cycle_end_date"),
    @Index(name = "idx_billing_cycle_due_date", columnList = "due_date"),
    @Index(name = "idx_billing_cycle_invoice", columnList = "invoice_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BillingCycle extends Auditable {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false)
    private CustomerType customerType;

    @Column(name = "cycle_start_date", nullable = false)
    private LocalDate cycleStartDate;

    @Column(name = "cycle_end_date", nullable = false)
    private LocalDate cycleEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CycleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_frequency", nullable = false)
    private BillingFrequency billingFrequency;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    // CRITICAL FINANCIAL AMOUNTS - DECIMAL(19,4) precision
    @Column(name = "subscription_charges", precision = 19, scale = 4)
    private BigDecimal subscriptionCharges;

    @Column(name = "usage_charges", precision = 19, scale = 4)
    private BigDecimal usageCharges;

    @Column(name = "transaction_fees", precision = 19, scale = 4)
    private BigDecimal transactionFees;

    @Column(name = "adjustments", precision = 19, scale = 4)
    private BigDecimal adjustments;

    @Column(name = "credits", precision = 19, scale = 4)
    private BigDecimal credits;

    @Column(name = "tax_amount", precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", precision = 19, scale = 4)
    private BigDecimal paidAmount;

    @Column(name = "balance_due", precision = 19, scale = 4)
    private BigDecimal balanceDue;

    // Dates
    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "grace_period_end_date")
    private LocalDate gracePeriodEndDate;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // Invoice information
    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "invoice_number", unique = true)
    private String invoiceNumber;

    @Column(name = "invoice_generated")
    private Boolean invoiceGenerated = false;

    @Column(name = "invoice_sent")
    private Boolean invoiceSent = false;

    @Column(name = "invoice_sent_at")
    private LocalDateTime invoiceSentAt;

    // Payment information
    @Column(name = "auto_pay_enabled")
    private Boolean autoPayEnabled = false;

    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    @Column(name = "payment_attempts")
    private Integer paymentAttempts = 0;

    @Column(name = "last_payment_attempt_at")
    private LocalDateTime lastPaymentAttemptAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // Dunning
    @Column(name = "dunning_level")
    private Integer dunningLevel = 0;

    @Column(name = "last_dunning_action_at")
    private LocalDateTime lastDunningActionAt;

    // Metadata
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ElementCollection
    @CollectionTable(name = "billing_cycle_metadata", joinColumns = @JoinColumn(name = "cycle_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> metadata = new HashMap<>();

    // Relationships
    @OneToMany(mappedBy = "billingCycle", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<LineItem> lineItems = new HashSet<>();

    @OneToMany(mappedBy = "billingCycle", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<BillingEvent> events = new HashSet<>();

    @OneToMany(mappedBy = "billingCycle", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Payment> payments = new HashSet<>();

    // Optimistic locking
    @Version
    private Long version;

    /**
     * Customer types
     */
    public enum CustomerType {
        PERSONAL,
        BUSINESS,
        MERCHANT,
        ENTERPRISE
    }

    /**
     * Billing cycle status
     */
    public enum CycleStatus {
        DRAFT,          // Cycle created but not finalized
        OPEN,           // Active billing cycle
        CLOSED,         // Cycle closed, invoice pending
        INVOICED,       // Invoice sent
        PARTIALLY_PAID, // Partial payment received
        PAID,           // Fully paid
        OVERDUE,        // Past due date
        DUNNING,        // In dunning process
        WRITTEN_OFF,    // Bad debt
        DISPUTED        // Under dispute
    }

    /**
     * Billing frequency
     */
    public enum BillingFrequency {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        QUARTERLY,
        SEMI_ANNUAL,
        ANNUAL,
        ON_DEMAND
    }

    // BUSINESS METHODS

    /**
     * Calculate total amount from all charge components
     * CRITICAL: Must be called before finalizing cycle
     */
    public void calculateTotalAmount() {
        BigDecimal total = BigDecimal.ZERO;

        if (subscriptionCharges != null) total = total.add(subscriptionCharges);
        if (usageCharges != null) total = total.add(usageCharges);
        if (transactionFees != null) total = total.add(transactionFees);
        if (adjustments != null) total = total.add(adjustments);
        if (credits != null) total = total.subtract(credits);
        if (taxAmount != null) total = total.add(taxAmount);

        this.totalAmount = total;
        updateBalanceDue();
    }

    /**
     * Update balance due after payment received
     */
    public void updateBalanceDue() {
        if (totalAmount != null && paidAmount != null) {
            this.balanceDue = totalAmount.subtract(paidAmount);
        } else if (totalAmount != null) {
            this.balanceDue = totalAmount;
        }
    }

    /**
     * Check if cycle is past due
     */
    public boolean isPastDue() {
        return dueDate != null && LocalDate.now().isAfter(dueDate) &&
               balanceDue != null && balanceDue.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if cycle is in grace period
     */
    public boolean isInGracePeriod() {
        return gracePeriodEndDate != null &&
               LocalDate.now().isAfter(dueDate) &&
               LocalDate.now().isBefore(gracePeriodEndDate.plusDays(1));
    }

    /**
     * Check if cycle is fully paid
     */
    public boolean isFullyPaid() {
        return balanceDue != null && balanceDue.compareTo(BigDecimal.ZERO) == 0 &&
               status == CycleStatus.PAID;
    }

    /**
     * Check if auto-pay should be attempted
     */
    public boolean shouldAttemptAutoPay() {
        return autoPayEnabled &&
               paymentMethodId != null &&
               balanceDue != null &&
               balanceDue.compareTo(BigDecimal.ZERO) > 0 &&
               (status == CycleStatus.INVOICED || status == CycleStatus.PARTIALLY_PAID);
    }

    /**
     * Increment dunning level
     */
    public void incrementDunningLevel() {
        this.dunningLevel = (dunningLevel != null ? dunningLevel : 0) + 1;
        this.lastDunningActionAt = LocalDateTime.now();
        if (this.status != CycleStatus.WRITTEN_OFF) {
            this.status = CycleStatus.DUNNING;
        }
    }

    /**
     * Reset dunning when payment received
     */
    public void resetDunning() {
        this.dunningLevel = 0;
        this.lastDunningActionAt = null;
    }
}
