package com.waqiti.ledger.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reconciliation Discrepancy Entity
 * 
 * Tracks discrepancies found during reconciliation processes
 * and their resolution status.
 */
@Entity
@Table(name = "reconciliation_discrepancies", indexes = {
    @Index(name = "idx_discrepancy_account", columnList = "accountId"),
    @Index(name = "idx_discrepancy_status", columnList = "status"),
    @Index(name = "idx_discrepancy_type", columnList = "discrepancyType"),
    @Index(name = "idx_discrepancy_date", columnList = "discoveredDate"),
    @Index(name = "idx_discrepancy_amount", columnList = "amount")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationDiscrepancy {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "discrepancy_id")
    private UUID id;

    @Column(name = "discrepancy_number", nullable = false, unique = true, length = 50)
    private String discrepancyNumber;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false)
    private DiscrepancyType discrepancyType;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DiscrepancyStatus status = DiscrepancyStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @Column(name = "discovered_date", nullable = false)
    private LocalDate discoveredDate;

    @Column(name = "discovered_by", length = 100)
    private String discoveredBy;

    @Column(name = "source_system", length = 50)
    private String sourceSystem;

    @Column(name = "source_reference", length = 100)
    private String sourceReference;

    @Column(name = "bank_statement_entry_id")
    private UUID bankStatementEntryId;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "reconciliation_id")
    private UUID reconciliationId;

    @Column(name = "aging_days")
    private Integer agingDays;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "resolution_type", length = 50)
    private String resolutionType;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    @Column(name = "resolution_amount", precision = 19, scale = 4)
    private BigDecimal resolutionAmount;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approval_notes", length = 1000)
    private String approvalNotes;

    @Column(name = "related_journal_entry_id")
    private UUID relatedJournalEntryId;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "escalated_to", length = 100)
    private String escalatedTo;

    @Column(name = "escalation_reason", length = 500)
    private String escalationReason;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Discrepancy Types
     */
    public enum DiscrepancyType {
        // Balance Discrepancies
        BALANCE_VARIANCE,           // General balance variance
        BANK_BALANCE_MISMATCH,      // Bank vs ledger balance mismatch
        OPENING_BALANCE_ERROR,      // Opening balance discrepancy
        CLOSING_BALANCE_ERROR,      // Closing balance discrepancy
        
        // Transaction Discrepancies
        UNMATCHED_BANK_ITEM,        // Bank transaction without ledger match
        UNMATCHED_LEDGER_ITEM,      // Ledger entry without bank match
        AMOUNT_MISMATCH,            // Transaction amounts don't match
        DATE_MISMATCH,              // Transaction dates don't match
        DUPLICATE_TRANSACTION,      // Duplicate transactions detected
        MISSING_TRANSACTION,        // Expected transaction not found
        
        // Reconciliation Issues
        TIMING_DIFFERENCE,          // Timing differences between systems
        CUTOFF_ERROR,              // Period cutoff errors
        EXCHANGE_RATE_VARIANCE,     // Foreign exchange rate differences
        ROUNDING_DIFFERENCE,        // Rounding differences
        
        // System Issues
        SYSTEM_ERROR,              // System processing errors
        DATA_CORRUPTION,           // Data integrity issues
        INTERFACE_ERROR,           // System interface errors
        POSTING_ERROR,             // Transaction posting errors
        
        // Manual Adjustments
        MANUAL_ADJUSTMENT_NEEDED,   // Requires manual adjustment
        CORRECTION_REQUIRED,        // Error correction needed
        WRITE_OFF_REQUIRED,         // Write-off required
        RECLASSIFICATION_NEEDED,    // Account reclassification needed
        
        // Audit Findings
        AUDIT_ADJUSTMENT,          // Audit-identified discrepancy
        COMPLIANCE_ISSUE,          // Regulatory compliance issue
        CONTROL_WEAKNESS,          // Internal control weakness
        
        // Other
        UNKNOWN,                   // Unknown discrepancy type
        INVESTIGATIVE             // Under investigation
    }

    /**
     * Discrepancy Status
     */
    public enum DiscrepancyStatus {
        OPEN,                      // Newly identified, not yet assigned
        ASSIGNED,                  // Assigned to someone for resolution
        IN_PROGRESS,               // Being worked on
        PENDING_APPROVAL,          // Resolution pending approval
        RESOLVED,                  // Successfully resolved
        CLOSED,                    // Closed without resolution
        ESCALATED,                 // Escalated to higher authority
        ON_HOLD,                   // On hold pending additional information
        CANCELLED,                 // Cancelled (invalid discrepancy)
        REOPENED                   // Reopened after being closed
    }

    /**
     * Priority Levels
     */
    public enum Priority {
        CRITICAL,                  // Requires immediate attention
        HIGH,                      // High priority
        MEDIUM,                    // Normal priority
        LOW                        // Low priority
    }

    /**
     * Business logic methods
     */
    
    public boolean isOpen() {
        return status == DiscrepancyStatus.OPEN || status == DiscrepancyStatus.ASSIGNED ||
               status == DiscrepancyStatus.IN_PROGRESS || status == DiscrepancyStatus.PENDING_APPROVAL;
    }

    public boolean isClosed() {
        return status == DiscrepancyStatus.RESOLVED || status == DiscrepancyStatus.CLOSED ||
               status == DiscrepancyStatus.CANCELLED;
    }

    public boolean canBeResolved() {
        return isOpen() && status != DiscrepancyStatus.ON_HOLD;
    }

    public boolean requiresApproval() {
        return amount.abs().compareTo(BigDecimal.valueOf(1000)) > 0 || // Amounts over $1000
               discrepancyType == DiscrepancyType.WRITE_OFF_REQUIRED ||
               priority == Priority.CRITICAL;
    }

    public boolean isOverdue() {
        return dueDate != null && LocalDate.now().isAfter(dueDate) && isOpen();
    }

    public boolean shouldBeEscalated() {
        return isOverdue() && 
               java.time.temporal.ChronoUnit.DAYS.between(discoveredDate, LocalDate.now()) > 30;
    }

    public void calculateAgingDays() {
        this.agingDays = (int) java.time.temporal.ChronoUnit.DAYS.between(discoveredDate, LocalDate.now());
    }

    public void assign(String assignedTo) {
        this.status = DiscrepancyStatus.ASSIGNED;
        this.assignedTo = assignedTo;
        this.assignedAt = LocalDateTime.now();
        this.dueDate = calculateDueDate();
    }

    public void startWork() {
        this.status = DiscrepancyStatus.IN_PROGRESS;
    }

    public void resolve(String resolvedBy, String resolutionType, String notes, BigDecimal resolutionAmount) {
        if (requiresApproval()) {
            this.status = DiscrepancyStatus.PENDING_APPROVAL;
        } else {
            this.status = DiscrepancyStatus.RESOLVED;
            this.resolvedAt = LocalDateTime.now();
        }
        this.resolvedBy = resolvedBy;
        this.resolutionType = resolutionType;
        this.resolutionNotes = notes;
        this.resolutionAmount = resolutionAmount;
    }

    public void approve(String approvedBy, String notes) {
        this.status = DiscrepancyStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.approvedAt = LocalDateTime.now();
        this.approvedBy = approvedBy;
        this.approvalNotes = notes;
    }

    public void escalate(String escalatedTo, String reason) {
        this.status = DiscrepancyStatus.ESCALATED;
        this.escalatedAt = LocalDateTime.now();
        this.escalatedTo = escalatedTo;
        this.escalationReason = reason;
        this.priority = Priority.HIGH; // Escalated items become high priority
    }

    public void putOnHold(String reason) {
        this.status = DiscrepancyStatus.ON_HOLD;
        this.resolutionNotes = reason;
    }

    public void reopen(String reason) {
        this.status = DiscrepancyStatus.REOPENED;
        this.resolutionNotes = reason;
        this.resolvedAt = null;
        this.approvedAt = null;
    }

    private LocalDate calculateDueDate() {
        LocalDate baseDate = LocalDate.now();
        switch (priority) {
            case CRITICAL:
                return baseDate.plusDays(1);
            case HIGH:
                return baseDate.plusDays(3);
            case MEDIUM:
                return baseDate.plusDays(7);
            case LOW:
                return baseDate.plusDays(14);
            default:
                return baseDate.plusDays(7);
        }
    }
}