package com.waqiti.dispute.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeTimelineEvent {

    private UUID eventId;
    private UUID disputeId;
    private String eventType;
    private String description;
    private String performedBy;
    private LocalDateTime occurredAt;
    private String metadata;
}
