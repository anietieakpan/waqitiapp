package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a review case is created or updated.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReviewCaseEvent extends FinancialEvent {

    private UUID caseId;
    private UUID entityId;  // Account, Transaction, or Customer being reviewed
    private String entityType;  // ACCOUNT, TRANSACTION, CUSTOMER
    private String caseType;  // FRAUD_REVIEW, COMPLIANCE_REVIEW, RISK_REVIEW, CLOSURE_REVIEW
    private String caseStatus;  // OPEN, IN_PROGRESS, RESOLVED, ESCALATED
    private String priority;  // LOW, MEDIUM, HIGH, CRITICAL
    private UUID assignedTo;
    private Instant createdAt;
    private Instant dueDate;
    private String caseNotes;

    public Integer getPriority() {
        if (priority == null) return 3; // Medium
        switch (priority.toUpperCase()) {
            case "LOW": return 4;
            case "MEDIUM": return 3;
            case "HIGH": return 2;
            case "CRITICAL": return 1;
            default: return 3;
        }
    }

    public static ReviewCaseEvent create(UUID entityId, String entityType, String caseType, String priority) {
        return ReviewCaseEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("REVIEW_CASE")
            .eventCategory("REVIEW")
            .caseId(UUID.randomUUID())
            .entityId(entityId)
            .entityType(entityType)
            .caseType(caseType)
            .caseStatus("OPEN")
            .priority(priority)
            .createdAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateId(entityId)
            .build();
    }
}
