package com.waqiti.common.messaging.recovery.model;

import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualReviewCase {
    private String caseId;
    private String eventId;
    private String serviceName;
    private String eventType;
    private Map<String, Object> payload;
    private String errorMessage;
    private Integer retryCount;
    private String priority;
    private String status;
    private LocalDateTime createdAt;
    private UUID assignedTo;
}