package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted when a customer is offboarded from the platform.
 *
 * This event is published when:
 * - Customer requests account closure
 * - Administrative account termination
 * - Regulatory compliance closure
 * - GDPR right-to-erasure request
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomerOffboardingEvent extends FinancialEvent {

    private UUID customerId;
    private String offboardingReason;
    private String offboardingType;  // VOLUNTARY, ADMINISTRATIVE, REGULATORY, GDPR
    private Instant offboardingStartedAt;
    private Instant offboardingCompletedAt;
    private UUID initiatedBy;
    private boolean dataRetentionRequired;
    private Integer retentionPeriodDays;
    private boolean accountsClosed;
    private boolean dataExported;
    private boolean notificationsSent;
    private Map<String, String> offboardingMetadata;
    private String finalStatus;

    /**
     * Factory method to create customer offboarding event
     */
    public static CustomerOffboardingEvent create(UUID customerId, UUID userId,
                                                   String reason, String offboardingType) {
        return CustomerOffboardingEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("CUSTOMER_OFFBOARDING")
            .eventCategory("CUSTOMER")
            .customerId(customerId)
            .userId(userId)
            .offboardingReason(reason)
            .offboardingType(offboardingType)
            .offboardingStartedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateType("Customer")
            .aggregateId(customerId)
            .build();
    }

    /**
     * Check if offboarding is voluntary
     */
    public boolean isVoluntary() {
        return "VOLUNTARY".equalsIgnoreCase(offboardingType);
    }

    /**
     * Check if offboarding is GDPR-related
     */
    public boolean isGdprOffboarding() {
        return "GDPR".equalsIgnoreCase(offboardingType);
    }

    /**
     * Check if data retention is required
     */
    public boolean requiresDataRetention() {
        return dataRetentionRequired;
    }

    /**
     * Check if offboarding is complete
     */
    public boolean isComplete() {
        return offboardingCompletedAt != null &&
               accountsClosed &&
               notificationsSent;
    }
}
