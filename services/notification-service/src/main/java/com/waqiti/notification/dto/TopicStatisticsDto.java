package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicStatisticsDto {
    private String topicName;
    private String displayName;
    private String category;
    private long subscriberCount;
    private long notificationsSentLast7Days;
    private long notificationsSentLast30Days;
    private double deliveryRate;
    private Map<String, Long> subscribersByPlatform;
    private LocalDateTime lastNotificationSent;
    private boolean active;
}