package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a dormant or suspended customer is reactivated.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomerReactivationEvent extends FinancialEvent {

    private UUID customerId;
    private String previousStatus;
    private String reactivationReason;
    private Instant reactivatedAt;
    private UUID reactivatedBy;
    private String reactivationChannel;

    public static CustomerReactivationEvent create(UUID customerId, UUID userId, String reason) {
        return CustomerReactivationEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("CUSTOMER_REACTIVATION")
            .eventCategory("CUSTOMER")
            .customerId(customerId)
            .userId(userId)
            .reactivationReason(reason)
            .reactivatedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateId(customerId)
            .build();
    }
}
