package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a review case is assigned or reassigned to a reviewer.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReviewAssignmentEvent extends FinancialEvent {

    private UUID assignmentId;
    private UUID caseId;
    private UUID previousAssignee;
    private UUID newAssignee;
    private String assignmentReason;
    private UUID assignedBy;
    private Instant assignedAt;
    private Instant dueDate;

    public static ReviewAssignmentEvent create(UUID caseId, UUID newAssignee, UUID assignedBy) {
        return ReviewAssignmentEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("REVIEW_ASSIGNMENT")
            .eventCategory("REVIEW")
            .assignmentId(UUID.randomUUID())
            .caseId(caseId)
            .newAssignee(newAssignee)
            .assignedBy(assignedBy)
            .assignedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateId(caseId)
            .build();
    }
}
