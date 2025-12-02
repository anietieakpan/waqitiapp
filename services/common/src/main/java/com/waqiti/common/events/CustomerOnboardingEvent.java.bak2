package com.waqiti.common.events;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.Instant;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomerOnboardingEvent extends FinancialEvent {
    private UUID customerId;
    private String onboardingStatus;
    private String onboardingChannel;
    private String kycStatus;
    private Instant startedAt;
    private Instant completedAt;

    public static CustomerOnboardingEvent create(UUID customerId, UUID userId, String status) {
        return CustomerOnboardingEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("CUSTOMER_ONBOARDING")
            .eventCategory("CUSTOMER")
            .customerId(customerId)
            .userId(userId)
            .onboardingStatus(status)
            .timestamp(Instant.now())
            .aggregateId(customerId)
            .build();
    }
}
