package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemPushStatsDto {
    private long totalDevices;
    private long activeDevices;
    private Map<String, Long> devicesByPlatform;
    private long notificationsSentLast7Days;
    private long notificationsSentLast30Days;
    private double successRate;
    private double avgDeliveryTimeMs;
    private Map<String, Long> notificationsByType;
    private Map<String, Double> successRateByPlatform;
}