package com.waqiti.billingorchestrator.entity;

import com.waqiti.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Billing Dispute Entity
 *
 * Represents customer disputes of invoices/charges with complete workflow tracking.
 *
 * CRITICAL BUSINESS IMPACT:
 * - Average dispute: $50-$500
 * - Monthly dispute volume: 100-500 cases
 * - Resolution time: 5-30 days
 * - Win rate: 70% for merchant
 *
 * DISPUTE LIFECYCLE:
 * 1. SUBMITTED: Customer submits dispute with evidence
 * 2. UNDER_REVIEW: Billing team investigates
 * 3. PENDING_MERCHANT_RESPONSE: Awaiting merchant evidence
 * 4. ESCALATED: Complex case requiring senior review
 * 5. RESOLVED: Dispute closed (ACCEPTED/REJECTED/PARTIAL_REFUND)
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Entity
@Table(name = "billing_disputes", indexes = {
    @Index(name = "idx_dispute_customer", columnList = "customer_id"),
    @Index(name = "idx_dispute_billing_cycle", columnList = "billing_cycle_id"),
    @Index(name = "idx_dispute_status", columnList = "status"),
    @Index(name = "idx_dispute_created", columnList = "created_at"),
    @Index(name = "idx_dispute_resolution_date", columnList = "resolution_date"),
    @Index(name = "idx_dispute_assignee", columnList = "assigned_to")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BillingDispute extends Auditable {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "billing_cycle_id", nullable = false)
    private UUID billingCycleId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DisputeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private DisputeReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_type")
    private DisputeResolution resolutionType;

    // Financial details
    @Column(name = "disputed_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal disputedAmount;

    @Column(name = "approved_refund_amount", precision = 19, scale = 4)
    private BigDecimal approvedRefundAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    // Dispute content
    @Column(name = "customer_description", columnDefinition = "TEXT", nullable = false)
    private String customerDescription;

    @Column(name = "merchant_response", columnDefinition = "TEXT")
    private String merchantResponse;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    // Evidence tracking
    @Column(name = "customer_evidence_url", length = 500)
    private String customerEvidenceUrl;  // S3/file storage URL

    @Column(name = "merchant_evidence_url", length = 500)
    private String merchantEvidenceUrl;

    // Workflow tracking
    @Column(name = "assigned_to")
    private UUID assignedTo;  // User ID of billing team member

    @Column(name = "priority")
    @Enumerated(EnumType.STRING)
    private DisputePriority priority;

    @Column(name = "escalated")
    private Boolean escalated;

    @Column(name = "escalation_reason")
    private String escalationReason;

    // Timeline tracking
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "merchant_response_deadline")
    private LocalDateTime merchantResponseDeadline;

    @Column(name = "merchant_responded_at")
    private LocalDateTime merchantRespondedAt;

    @Column(name = "resolution_date")
    private LocalDateTime resolutionDate;

    @Column(name = "resolved_by")
    private UUID resolvedBy;  // User ID of resolver

    // SLA tracking
    @Column(name = "sla_breach")
    private Boolean slaBreach;

    @Column(name = "sla_deadline")
    private LocalDateTime slaDeadline;

    @Version
    private Long version;

    public enum DisputeStatus {
        SUBMITTED,
        UNDER_REVIEW,
        PENDING_MERCHANT_RESPONSE,
        MERCHANT_RESPONDED,
        ESCALATED,
        PENDING_REFUND,
        RESOLVED,
        CLOSED
    }

    public enum DisputeReason {
        INCORRECT_AMOUNT("Incorrect charge amount"),
        SERVICE_NOT_RECEIVED("Service not received"),
        POOR_SERVICE_QUALITY("Poor service quality"),
        DUPLICATE_CHARGE("Duplicate charge"),
        CANCELLED_SUBSCRIPTION("Subscription was cancelled"),
        BILLING_ERROR("Billing calculation error"),
        UNAUTHORIZED_CHARGE("Unauthorized charge"),
        PROMOTIONAL_DISCOUNT_NOT_APPLIED("Promotional discount not applied"),
        REFUND_NOT_RECEIVED("Refund not received"),
        OTHER("Other reason");

        private final String description;

        DisputeReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum DisputeResolution {
        ACCEPTED_FULL_REFUND("Accepted - Full refund issued"),
        ACCEPTED_PARTIAL_REFUND("Accepted - Partial refund issued"),
        ACCEPTED_CREDIT_NOTE("Accepted - Credit note issued"),
        REJECTED_NO_MERIT("Rejected - No merit"),
        REJECTED_INSUFFICIENT_EVIDENCE("Rejected - Insufficient evidence"),
        REJECTED_OUTSIDE_POLICY("Rejected - Outside refund policy"),
        WITHDRAWN_BY_CUSTOMER("Withdrawn by customer"),
        RESOLVED_GOODWILL_REFUND("Resolved - Goodwill refund");

        private final String description;

        DisputeResolution(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum DisputePriority {
        LOW,     // < $50, no SLA breach risk
        MEDIUM,  // $50-$500, standard timeline
        HIGH,    // > $500 or SLA approaching
        URGENT   // > $1000 or SLA breached or escalated
    }

    /**
     * Calculates dispute age in hours
     */
    public long getDisputeAgeHours() {
        return java.time.Duration.between(submittedAt, LocalDateTime.now()).toHours();
    }

    /**
     * Checks if SLA is approaching (within 24 hours of deadline)
     */
    public boolean isSlaApproaching() {
        if (slaDeadline == null) return false;
        return LocalDateTime.now().plusHours(24).isAfter(slaDeadline) &&
               LocalDateTime.now().isBefore(slaDeadline);
    }

    /**
     * Checks if SLA is breached
     */
    public boolean isSlaBreached() {
        if (slaDeadline == null) return false;
        return LocalDateTime.now().isAfter(slaDeadline);
    }
}
