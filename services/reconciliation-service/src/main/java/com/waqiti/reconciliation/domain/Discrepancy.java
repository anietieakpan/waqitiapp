package com.waqiti.reconciliation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Discrepancy - Represents a mismatch between reconciliation sources
 *
 * Tracks discrepancies found during reconciliation process with full audit trail
 * and resolution workflow support.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Entity
@Table(name = "discrepancies", indexes = {
    @Index(name = "idx_discrepancy_status", columnList = "status"),
    @Index(name = "idx_discrepancy_type", columnList = "discrepancy_type"),
    @Index(name = "idx_discrepancy_severity", columnList = "severity"),
    @Index(name = "idx_discrepancy_created", columnList = "created_at"),
    @Index(name = "idx_discrepancy_assigned", columnList = "assigned_to"),
    @Index(name = "idx_discrepancy_batch", columnList = "reconciliation_batch_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Discrepancy {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "reconciliation_batch_id", nullable = false)
    private String reconciliationBatchId;

    @Column(name = "discrepancy_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private DiscrepancyType discrepancyType;

    @Column(name = "severity", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(name = "source_item_id")
    private String sourceItemId;

    @Column(name = "target_item_id")
    private String targetItemId;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "target_system", length = 100)
    private String targetSystem;

    @Column(name = "source_amount", precision = 19, scale = 4)
    private BigDecimal sourceAmount;

    @Column(name = "target_amount", precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "amount_difference", precision = 19, scale = 4)
    private BigDecimal amountDifference;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private DiscrepancyStatus status;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "resolution_action", length = 50)
    @Enumerated(EnumType.STRING)
    private ResolutionAction resolutionAction;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "sla_due_date")
    private LocalDateTime slaDueDate;

    @Column(name = "is_sla_breached", nullable = false)
    private boolean slaBreach = false;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = DiscrepancyStatus.OPEN;
        }
        if (slaDueDate == null) {
            calculateSlaDueDate();
        }
    }

    public enum DiscrepancyType {
        AMOUNT_MISMATCH,
        MISSING_TRANSACTION,
        DUPLICATE_TRANSACTION,
        TIMING_DIFFERENCE,
        CURRENCY_MISMATCH,
        COUNTERPARTY_MISMATCH,
        STATUS_MISMATCH,
        FEE_DISCREPANCY,
        SETTLEMENT_DISCREPANCY,
        ROUNDING_DIFFERENCE,
        UNKNOWN
    }

    public enum Severity {
        CRITICAL,    // > $10,000 or regulatory impact
        HIGH,        // $1,000 - $10,000
        MEDIUM,      // $100 - $1,000
        LOW,         // < $100
        INFORMATIONAL
    }

    public enum DiscrepancyStatus {
        OPEN,
        ASSIGNED,
        IN_PROGRESS,
        PENDING_APPROVAL,
        RESOLVED,
        CLOSED,
        ESCALATED,
        CANCELLED
    }

    public enum ResolutionAction {
        ADJUST_LEDGER,
        ADJUST_BANK_RECORD,
        ADJUST_PROVIDER_RECORD,
        CREATE_MISSING_ENTRY,
        REMOVE_DUPLICATE,
        WRITE_OFF,
        ESCALATE_TO_FINANCE,
        ESCALATE_TO_COMPLIANCE,
        NO_ACTION_REQUIRED,
        PENDING_INVESTIGATION
    }

    private void calculateSlaDueDate() {
        LocalDateTime now = LocalDateTime.now();
        switch (severity) {
            case CRITICAL:
                slaDueDate = now.plusHours(4);
                break;
            case HIGH:
                slaDueDate = now.plusHours(24);
                break;
            case MEDIUM:
                slaDueDate = now.plusDays(3);
                break;
            case LOW:
                slaDueDate = now.plusDays(7);
                break;
            default:
                slaDueDate = now.plusDays(14);
        }
    }

    public void assignTo(String userId) {
        this.assignedTo = userId;
        this.assignedAt = LocalDateTime.now();
        this.status = DiscrepancyStatus.ASSIGNED;
    }

    public void markInProgress() {
        this.status = DiscrepancyStatus.IN_PROGRESS;
    }

    public void resolve(ResolutionAction action, String notes, String resolvedBy) {
        this.resolutionAction = action;
        this.resolutionNotes = notes;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = LocalDateTime.now();
        this.status = DiscrepancyStatus.RESOLVED;
    }

    public boolean isSlaBreached() {
        if (slaDueDate == null || resolvedAt != null) {
            return false;
        }
        return LocalDateTime.now().isAfter(slaDueDate);
    }

    public void checkAndUpdateSlaBreach() {
        if (isSlaBreached() && !slaBreach) {
            this.slaBreach = true;
        }
    }
}
