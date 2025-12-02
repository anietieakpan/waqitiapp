package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkNotificationResponse {
    private int totalUsers;
    private int successCount;
    private int failureCount;
    private List<String> failedUsers;
    private long processingTimeMs;
    private String status;
}