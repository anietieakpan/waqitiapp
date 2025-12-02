package com.waqiti.notification.dto;

import com.waqiti.notification.domain.DeviceToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for device token information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenResponse {
    
    private String id;
    private String deviceId;
    private String platform;
    private String deviceName;
    private String deviceModel;
    private String osVersion;
    private String appVersion;
    private String manufacturer;
    private String timezone;
    private String language;
    private boolean active;
    private LocalDateTime lastUsed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String maskedToken;
    
    /**
     * Factory method to create response from DeviceToken entity
     */
    public static DeviceTokenResponse fromEntity(DeviceToken deviceToken) {
        return DeviceTokenResponse.builder()
            .id(deviceToken.getId())
            .deviceId(deviceToken.getDeviceId())
            .platform(deviceToken.getPlatform().getCode())
            .deviceName(deviceToken.getDeviceName())
            .deviceModel(deviceToken.getDeviceModel())
            .osVersion(deviceToken.getOsVersion())
            .appVersion(deviceToken.getAppVersion())
            .manufacturer(deviceToken.getManufacturer())
            .timezone(deviceToken.getTimezone())
            .language(deviceToken.getLanguage())
            .active(deviceToken.isActive())
            .lastUsed(deviceToken.getLastUsed())
            .createdAt(deviceToken.getCreatedAt())
            .updatedAt(deviceToken.getUpdatedAt())
            .maskedToken(deviceToken.getMaskedToken())
            .build();
    }
    
    /**
     * Get display name for platform
     */
    public String getPlatformDisplayName() {
        try {
            return DeviceToken.Platform.fromCode(platform).getDisplayName();
        } catch (IllegalArgumentException e) {
            return platform;
        }
    }
    
    /**
     * Check if device is expired
     */
    public boolean isExpired() {
        if (lastUsed == null) {
            return createdAt.isBefore(LocalDateTime.now().minusDays(30));
        }
        return lastUsed.isBefore(LocalDateTime.now().minusDays(30));
    }
    
    /**
     * Check if device is stale
     */
    public boolean isStale() {
        if (lastUsed == null) {
            return createdAt.isBefore(LocalDateTime.now().minusDays(7));
        }
        return lastUsed.isBefore(LocalDateTime.now().minusDays(7));
    }
}