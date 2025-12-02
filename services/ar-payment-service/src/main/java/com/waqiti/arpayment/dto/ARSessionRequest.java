package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARSessionRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "Session type is required")
    private String sessionType;
    
    @NotBlank(message = "Device ID is required")
    private String deviceId;
    
    @NotBlank(message = "Device type is required")
    private String deviceType; // ARKit, ARCore, HoloLens, Magic Leap
    
    @NotBlank(message = "AR platform is required")
    private String arPlatform;
    
    private String arPlatformVersion;
    
    @NotNull(message = "Device capabilities are required")
    private Map<String, Object> deviceCapabilities;
    
    private Double currentLocationLat;
    private Double currentLocationLng;
    private Double locationAccuracy;
    private String indoorLocation;
    
    private Map<String, Object> sessionMetadata;
}