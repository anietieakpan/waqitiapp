package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationRequest {
    
    @NotBlank(message = "Device ID is required")
    private String deviceId;
    
    @NotBlank(message = "Device token is required")
    private String token;
    
    @NotBlank(message = "Platform is required")
    @Pattern(regexp = "iOS|Android|Web", message = "Platform must be iOS, Android, or Web")
    private String platform;
    
    private String appVersion;
    
    private String osVersion;
    
    private String model;
    
    private String manufacturer;
    
    private String timezone;
    
    private String language;
}