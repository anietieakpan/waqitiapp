package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event emitted when a customer is assigned to segments for marketing/analytics.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomerSegmentationEvent extends FinancialEvent {

    private UUID customerId;
    private List<String> segments;
    private List<String> previousSegments;
    private String segmentationStrategy;
    private Instant segmentedAt;

    public static CustomerSegmentationEvent create(UUID customerId, UUID userId, List<String> segments) {
        return CustomerSegmentationEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("CUSTOMER_SEGMENTATION")
            .eventCategory("CUSTOMER")
            .customerId(customerId)
            .userId(userId)
            .segments(segments)
            .segmentedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateId(customerId)
            .build();
    }
}
