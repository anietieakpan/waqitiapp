package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted for Net Promoter Score (NPS) survey interactions.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NPSSurveyEvent extends FinancialEvent {

    private UUID surveyId;
    private UUID customerId;
    private Integer npsScore;  // 0-10
    private String feedback;
    private String surveyTrigger;  // POST_TRANSACTION, PERIODIC, SUPPORT_INTERACTION
    private Instant surveyedAt;
    private Instant respondedAt;
    private String channel;  // EMAIL, IN_APP, SMS

    public static NPSSurveyEvent create(UUID customerId, UUID userId, String surveyTrigger) {
        return NPSSurveyEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("NPS_SURVEY")
            .eventCategory("CUSTOMER_FEEDBACK")
            .surveyId(UUID.randomUUID())
            .customerId(customerId)
            .userId(userId)
            .surveyTrigger(surveyTrigger)
            .surveyedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateId(customerId)
            .build();
    }

    public boolean isPromoter() {
        return npsScore != null && npsScore >= 9;
    }

    public boolean isDetractor() {
        return npsScore != null && npsScore <= 6;
    }

    public boolean isPassive() {
        return npsScore != null && npsScore >= 7 && npsScore <= 8;
    }
}
