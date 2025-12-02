package com.waqiti.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Complaint Entity
 *
 * Represents customer complaints including type, severity, status,
 * and resolution tracking for compliance and quality management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_complaint", indexes = {
    @Index(name = "idx_customer_complaint_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_complaint_status", columnList = "status"),
    @Index(name = "idx_customer_complaint_severity", columnList = "severity"),
    @Index(name = "idx_customer_complaint_type", columnList = "complaint_type"),
    @Index(name = "idx_customer_complaint_sla", columnList = "sla_due_date"),
    @Index(name = "idx_customer_complaint_cfpb", columnList = "cfpb_submitted")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "complaintId")
public class CustomerComplaint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "complaint_id", unique = true, nullable = false, length = 100)
    private String complaintId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "complaint_type", nullable = false, length = 50)
    private ComplaintType complaintType;

    @Enumerated(EnumType.STRING)
    @Column(name = "complaint_category", length = 50)
    private ComplaintCategory complaintCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private Severity severity = Severity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.OPEN;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "sla_due_date")
    private LocalDateTime slaDueDate;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "cfpb_submitted")
    @Builder.Default
    private Boolean cfpbSubmitted = false;

    @Column(name = "cfpb_submission_date")
    private LocalDate cfpbSubmissionDate;

    @Column(name = "sentiment_score", precision = 3, scale = 2)
    private BigDecimal sentimentScore;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Enums
    public enum ComplaintType {
        PRODUCT_SERVICE,
        BILLING,
        FRAUD,
        ACCOUNT_ACCESS,
        TRANSACTION_DISPUTE,
        FEES_CHARGES,
        CUSTOMER_SERVICE,
        TECHNICAL_ISSUE,
        COMPLIANCE,
        PRIVACY,
        DISCRIMINATION,
        UNAUTHORIZED_TRANSACTION,
        CREDIT_REPORTING,
        COLLECTION_PRACTICES,
        OTHER
    }

    public enum ComplaintCategory {
        ACCOUNT_MANAGEMENT,
        PAYMENT_PROCESSING,
        CARD_SERVICES,
        LOAN_SERVICES,
        DEPOSIT_SERVICES,
        WIRE_TRANSFER,
        CUSTOMER_SUPPORT,
        MOBILE_BANKING,
        ONLINE_BANKING,
        ATM_SERVICES,
        REGULATORY,
        OTHER
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum Status {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        CLOSED,
        ESCALATED
    }

    /**
     * Check if complaint is open
     *
     * @return true if status is OPEN
     */
    public boolean isOpen() {
        return status == Status.OPEN;
    }

    /**
     * Check if complaint is resolved
     *
     * @return true if status is RESOLVED or CLOSED
     */
    public boolean isResolved() {
        return status == Status.RESOLVED || status == Status.CLOSED;
    }

    /**
     * Check if complaint is escalated
     *
     * @return true if status is ESCALATED
     */
    public boolean isEscalated() {
        return status == Status.ESCALATED;
    }

    /**
     * Check if complaint is overdue
     *
     * @return true if SLA due date has passed
     */
    public boolean isOverdue() {
        if (slaDueDate == null || isResolved()) {
            return false;
        }
        return LocalDateTime.now().isAfter(slaDueDate);
    }

    /**
     * Check if complaint is critical
     *
     * @return true if severity is CRITICAL
     */
    public boolean isCritical() {
        return severity == Severity.CRITICAL;
    }

    /**
     * Check if complaint was submitted to CFPB
     *
     * @return true if submitted to CFPB
     */
    public boolean isCfpbSubmitted() {
        return cfpbSubmitted != null && cfpbSubmitted;
    }

    /**
     * Get days until SLA deadline
     *
     * @return days until SLA, negative if overdue, null if no SLA
     */
    public Long getDaysUntilSla() {
        if (slaDueDate == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), slaDueDate);
    }

    /**
     * Get hours until SLA deadline
     *
     * @return hours until SLA, negative if overdue, null if no SLA
     */
    public Long getHoursUntilSla() {
        if (slaDueDate == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.HOURS.between(LocalDateTime.now(), slaDueDate);
    }

    /**
     * Get resolution time in hours
     *
     * @return resolution time in hours, null if not resolved
     */
    public Long getResolutionTimeHours() {
        if (resolvedAt == null || createdAt == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.HOURS.between(createdAt, resolvedAt);
    }

    /**
     * Assign complaint to user
     *
     * @param assignee the user to assign to
     */
    public void assignTo(String assignee) {
        this.assignedTo = assignee;
        if (this.status == Status.OPEN) {
            this.status = Status.IN_PROGRESS;
        }
    }

    /**
     * Escalate complaint
     */
    public void escalate() {
        this.status = Status.ESCALATED;
        if (this.severity == Severity.LOW) {
            this.severity = Severity.MEDIUM;
        } else if (this.severity == Severity.MEDIUM) {
            this.severity = Severity.HIGH;
        } else if (this.severity == Severity.HIGH) {
            this.severity = Severity.CRITICAL;
        }
    }

    /**
     * Resolve complaint
     *
     * @param resolutionText the resolution details
     */
    public void resolve(String resolutionText) {
        this.status = Status.RESOLVED;
        this.resolution = resolutionText;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * Close complaint
     */
    public void close() {
        this.status = Status.CLOSED;
        if (this.resolvedAt == null) {
            this.resolvedAt = LocalDateTime.now();
        }
    }

    /**
     * Submit to CFPB
     */
    public void submitToCfpb() {
        this.cfpbSubmitted = true;
        this.cfpbSubmissionDate = LocalDate.now();
    }
}
