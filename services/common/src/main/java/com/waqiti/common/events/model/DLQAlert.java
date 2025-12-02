package com.waqiti.common.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Alert model for Dead Letter Queue events
 * Used for monitoring and alerting on DLQ entries
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DLQAlert {
    
    private UUID alertId;
    private UUID dlqEntryId;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    private String failureReason;
    private int retryCount;
    private LocalDateTime originalCreatedAt;
    private String alertLevel;
    private Instant createdAt;
    private String description;
    private String severity;
    private boolean acknowledged;
    private String acknowledgedBy;
    private Instant acknowledgedAt;
}