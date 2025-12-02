package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a review case deadline is approaching or breached.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReviewDeadlineEvent extends FinancialEvent {

    private UUID caseId;
    private UUID assignedTo;
    private Instant originalDeadline;
    private Instant newDeadline;
    private String deadlineStatus;  // APPROACHING, BREACHED, EXTENDED
    private String escalationLevel;
    private Instant notifiedAt;

    public static ReviewDeadlineEvent create(UUID caseId, UUID assignedTo,
                                              Instant deadline, String deadlineStatus) {
        return ReviewDeadlineEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("REVIEW_DEADLINE")
            .eventCategory("REVIEW")
            .caseId(caseId)
            .assignedTo(assignedTo)
            .originalDeadline(deadline)
            .deadlineStatus(deadlineStatus)
            .notifiedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateId(caseId)
            .build();
    }
}
