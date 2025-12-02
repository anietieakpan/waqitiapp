package com.waqiti.notification.dto;

import com.waqiti.notification.domain.DeviceToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for device token registration and updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenRequest {
    
    @NotBlank(message = "Token is required")
    @Size(max = 1000, message = "Token is too long")
    private String token;
    
    @NotBlank(message = "Platform is required")
    private String platform;
    
    @NotBlank(message = "Device ID is required")
    @Size(max = 255, message = "Device ID is too long")
    private String deviceId;
    
    @Size(max = 255, message = "Device name is too long")
    private String deviceName;
    
    @Size(max = 255, message = "Device model is too long")
    private String deviceModel;
    
    @Size(max = 50, message = "OS version is too long")
    private String osVersion;
    
    @Size(max = 50, message = "App version is too long")
    private String appVersion;
    
    @Size(max = 255, message = "Manufacturer is too long")
    private String manufacturer;
    
    @Size(max = 50, message = "Timezone is too long")
    private String timezone;
    
    @Size(max = 10, message = "Language is too long")
    private String language;
    
    /**
     * Convert to DeviceToken.Platform enum
     */
    public DeviceToken.Platform getPlatformEnum() {
        return DeviceToken.Platform.fromCode(platform.toLowerCase());
    }
    
    /**
     * Validate and normalize the request
     */
    public void normalize() {
        if (platform != null) {
            platform = platform.toLowerCase().trim();
        }
        if (deviceId != null) {
            deviceId = deviceId.trim();
        }
        if (token != null) {
            token = token.trim();
        }
    }
}