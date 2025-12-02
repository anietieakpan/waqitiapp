package com.waqiti.common.messaging.recovery;


import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadStorageRecord {
    private String eventId;
    private String serviceName;
    private String eventType;
    private String payload;
    private String originalTopic;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime firstFailedAt;
    private LocalDateTime archivedAt;
    private String reason;
}