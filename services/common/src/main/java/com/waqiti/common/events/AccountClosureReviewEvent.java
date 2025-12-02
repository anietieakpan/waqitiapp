package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an account closure request is submitted for review.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountClosureReviewEvent extends FinancialEvent {

    private UUID reviewId;
    private UUID accountId;
    private String closureReason;
    private String reviewStatus;  // PENDING, IN_REVIEW, APPROVED, REJECTED
    private UUID reviewedBy;
    private Instant submittedAt;
    private Instant reviewedAt;
    private String reviewNotes;

    public static AccountClosureReviewEvent create(UUID accountId, UUID userId, String reason) {
        return AccountClosureReviewEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("ACCOUNT_CLOSURE_REVIEW")
            .eventCategory("REVIEW")
            .reviewId(UUID.randomUUID())
            .accountId(accountId)
            .userId(userId)
            .closureReason(reason)
            .reviewStatus("PENDING")
            .submittedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateId(accountId)
            .build();
    }
}
