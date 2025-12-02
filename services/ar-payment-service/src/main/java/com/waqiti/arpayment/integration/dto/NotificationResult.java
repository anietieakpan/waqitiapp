package com.waqiti.arpayment.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Notification result from notification service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResult {

    private boolean sent;
    private String notificationId;
    private Instant sentAt;
    private Integer recipientsCount;
    private Integer successCount;
    private Integer failureCount;
    private List<String> failedRecipients;
    private String errorMessage;
    private boolean queuedForRetry;
}
