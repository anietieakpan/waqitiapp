package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Device Registration Result DTO
 * 
 * Contains the result of device registration/recognition process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationResult {
    
    // Registration status
    private Boolean registered;
    private String registrationStatus; // NEW, EXISTING, UPDATED, BLOCKED
    private String deviceId;
    private String fingerprint;
    
    // Device information
    private String deviceName;
    private String deviceType;
    private Boolean newDevice;
    private Boolean recognizedDevice;
    private String deviceFingerprint; // Full device fingerprint
    private Boolean isNewDevice; // Alias for newDevice
    
    // Trust and security
    private String trustLevel; // TRUSTED, UNKNOWN, SUSPICIOUS, BLOCKED
    private Double trustScore; // 0.0 to 1.0
    private Double riskScore; // 0.0 to 1.0
    private Boolean requiresApproval;
    private Boolean requiresVerification;
    
    // Confidence metrics
    private Double fingerprintConfidence;
    private Double matchConfidence;
    private String matchStatus; // EXACT, SIMILAR, DIFFERENT, UNKNOWN
    
    // Behavioral analysis
    private Boolean behaviorConsistent;
    private Double behaviorScore;
    private List<String> behaviorAnomalies;
    
    // Location context
    private Boolean locationConsistent;
    private String locationStatus;
    private Double locationDistance; // km from previous locations
    
    // Fraud detection
    private Boolean fraudSuspected;
    private Double fraudScore;
    private List<String> fraudIndicators;
    
    // Device characteristics
    private Map<String, Object> deviceCharacteristics;
    private List<String> deviceCapabilities;
    private Boolean emulatorDetected;
    private Boolean rootedDetected;
    
    // Historical context
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private Integer usageCount;
    private List<String> previousLocations;
    
    // Security recommendations
    private List<String> securityActions;
    private List<String> userActions;
    private Boolean requiresUserVerification;
    private List<String> verificationMethods;
    
    // Registration metadata
    private String registrationId;
    private LocalDateTime registeredAt;
    private String registrationMethod;
    private Map<String, Object> metadata;
    
    // Notifications
    private Boolean notifyUser;
    private String notificationLevel; // INFO, WARNING, CRITICAL
    private String notificationMessage;
    
    // Audit information
    private String auditTrail;
    private LocalDateTime processedAt;
    private Long processingTimeMs;
}