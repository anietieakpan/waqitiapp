package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a scheduled periodic review is triggered.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScheduledReviewEvent extends FinancialEvent {

    private UUID scheduleId;
    private UUID entityId;
    private String entityType;  // ACCOUNT, CUSTOMER, TRANSACTION
    private String reviewType;  // PERIODIC_KYC, RISK_ASSESSMENT, COMPLIANCE_AUDIT
    private String frequency;  // MONTHLY, QUARTERLY, ANNUALLY
    private Instant scheduledFor;
    private Instant triggeredAt;
    private UUID createdCaseId;  // ID of the review case created

    public static ScheduledReviewEvent create(UUID entityId, String entityType,
                                               String reviewType, Instant scheduledFor) {
        return ScheduledReviewEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("SCHEDULED_REVIEW")
            .eventCategory("REVIEW")
            .scheduleId(UUID.randomUUID())
            .entityId(entityId)
            .entityType(entityType)
            .reviewType(reviewType)
            .scheduledFor(scheduledFor)
            .triggeredAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateId(entityId)
            .build();
    }
}
