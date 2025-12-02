package com.waqiti.common.messaging.recovery;


import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualReviewCase {
    private String caseId;
    private String eventId;
    private String serviceName;
    private String eventType;
    private String payload;
    private String errorMessage;
    private Integer retryCount;
    private String priority;
    private String status;
    private LocalDateTime createdAt;
    private String assignedTo;
}