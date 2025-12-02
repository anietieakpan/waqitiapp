package com.waqiti.reconciliation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing discrepancies found during reconciliation
 */
@Entity
@Table(name = "reconciliation_discrepancies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Discrepancy {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "transaction_id", length = 50)
    private String transactionId;

    @Column(name = "provider_transaction_id", length = 50)
    private String providerTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, length = 50)
    private DiscrepancyType discrepancyType;

    @Column(name = "expected_amount", precision = 19, scale = 4)
    private BigDecimal expectedAmount;

    @Column(name = "actual_amount", precision = 19, scale = 4)
    private BigDecimal actualAmount;

    @Column(name = "amount_difference", precision = 19, scale = 4)
    private BigDecimal amountDifference;

    @Column(name = "confidence", precision = 5, scale = 2)
    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DiscrepancyStatus status;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "assigned_to", length = 50)
    private String assignedTo;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 50)
    private String resolvedBy;

    @Column(name = "priority", length = 20)
    @Builder.Default
    private String priority = "MEDIUM";

    @Column(name = "escalated", nullable = false)
    @Builder.Default
    private Boolean escalated = false;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "escalated_to", length = 50)
    private String escalatedTo;

    @Column(name = "impact_level", length = 20)
    @Builder.Default
    private String impactLevel = "LOW";

    @Column(name = "root_cause", length = 500)
    private String rootCause;

    @Column(name = "corrective_action", columnDefinition = "TEXT")
    private String correctiveAction;

    @Column(name = "related_discrepancy_id", length = 50)
    private String relatedDiscrepancyId;

    public enum DiscrepancyType {
        AMOUNT_MISMATCH,
        STATUS_MISMATCH,
        CURRENCY_MISMATCH,
        TIMING_MISMATCH,
        ORPHANED_TRANSACTION,
        ORPHANED_PROVIDER_TRANSACTION,
        DUPLICATE_TRANSACTION,
        PARTIAL_MATCH,
        DATA_MISMATCH,
        BALANCE_MISMATCH,
        SETTLEMENT_MISMATCH,
        FEE_MISMATCH
    }

    public enum DiscrepancyStatus {
        PENDING_REVIEW,
        UNDER_INVESTIGATION,
        RESOLVED,
        ACCEPTED_RISK,
        ESCALATED,
        CANCELLED,
        AUTO_RESOLVED
    }

    // Utility methods
    public boolean isHighPriority() {
        return "HIGH".equalsIgnoreCase(priority) || "CRITICAL".equalsIgnoreCase(priority);
    }

    public boolean isHighValue() {
        return amountDifference != null && 
               amountDifference.abs().compareTo(new BigDecimal("1000")) > 0;
    }

    public boolean requiresEscalation() {
        if (escalated) {
            return false;
        }

        // Escalate if high value and pending for more than 24 hours
        if (isHighValue() && getAgeInHours() > 24) {
            return true;
        }

        // Escalate if critical priority and pending for more than 4 hours
        if ("CRITICAL".equalsIgnoreCase(priority) && getAgeInHours() > 4) {
            return true;
        }

        // Escalate if pending for more than 72 hours
        return getAgeInHours() > 72;
    }

    public long getAgeInHours() {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toHours();
    }

    public boolean isResolved() {
        return status == DiscrepancyStatus.RESOLVED || 
               status == DiscrepancyStatus.ACCEPTED_RISK ||
               status == DiscrepancyStatus.AUTO_RESOLVED;
    }

    public boolean isPending() {
        return status == DiscrepancyStatus.PENDING_REVIEW || 
               status == DiscrepancyStatus.UNDER_INVESTIGATION;
    }

    public String getSeverityLevel() {
        if (isHighValue()) {
            return "HIGH";
        }
        if (amountDifference != null && 
            amountDifference.abs().compareTo(new BigDecimal("100")) > 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    @PreUpdate
    public void preUpdate() {
        if (status == DiscrepancyStatus.RESOLVED && resolvedAt == null) {
            resolvedAt = LocalDateTime.now();
        }
    }
}