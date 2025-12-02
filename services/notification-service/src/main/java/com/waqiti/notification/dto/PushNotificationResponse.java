package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushNotificationResponse {
    private String notificationId;
    private String status;
    private int sentCount;
    private int failedCount;
    private String messageId;
    private String error;
}