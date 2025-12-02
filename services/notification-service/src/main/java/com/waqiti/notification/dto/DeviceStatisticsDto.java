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
public class DeviceStatisticsDto {
    private long totalDevices;
    private long activeDevices;
    private Map<String, Long> devicesByPlatform;
    private Map<String, Long> devicesByAppVersion;
    private LocalDateTime lastActivity;
    private double activeDevicePercentage;
}