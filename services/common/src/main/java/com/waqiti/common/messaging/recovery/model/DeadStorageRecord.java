package com.waqiti.common.messaging.recovery.model;

import lombok.*;
import java.time.LocalDateTime;
import java.util.*;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadStorageRecord {
    private String eventId;
    private String serviceName;
    private String eventType;
    private Map<String, Object> payload;
    private String originalTopic;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime firstFailedAt;
    private LocalDateTime archivedAt;
    private String reason;
}

