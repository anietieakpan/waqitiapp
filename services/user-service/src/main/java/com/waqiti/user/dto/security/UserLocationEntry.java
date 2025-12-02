package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * User Location Entry DTO
 * 
 * Represents a single location entry in a user's location history
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLocationEntry {
    
    // Entry identification
    private String entryId;
    private String userId;
    private String sessionId;
    
    // Location data
    private LocationData locationData;
    
    // Timing information
    private LocalDateTime timestamp;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private Long durationMs;
    
    // Entry metadata
    private String entrySource; // LOGIN, TRANSACTION, API_CALL, PERIODIC
    private String detectionMethod; // IP_GEOLOCATION, GPS, CELL_TOWER, WIFI
    private Double accuracy; // Accuracy in meters
    private Double confidence; // 0.0 to 1.0
    
    // Activity context
    private String activityType;
    private String userAgent;
    private String deviceFingerprint;
    private String ipAddress;
    
    // Validation status
    private Boolean validated;
    private String validationStatus; // VALID, SUSPICIOUS, BLOCKED, PENDING
    private LocalDateTime validatedAt;
    private String validatedBy;
    
    // Risk assessment
    private Double riskScore; // 0.0 to 1.0
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private Boolean flagged;
    private String flagReason;
    
    // Location characteristics
    private Boolean newLocation;
    private Boolean trustedLocation;
    private Boolean homeLocation;
    private Boolean workLocation;
    private Boolean travelLocation;
    private Boolean publicLocation;
    
    // Security indicators
    private Boolean vpnDetected;
    private Boolean proxyDetected;
    private Boolean torDetected;
    private Boolean anomalousLocation;
    private Boolean impossibleTravel;
    
    // Frequency data
    private Integer visitCount;
    private LocalDateTime previousVisit;
    private LocalDateTime nextVisit;
    private String visitPattern; // FIRST_TIME, OCCASIONAL, FREQUENT, REGULAR
    
    // Additional metadata
    private Map<String, Object> metadata;
    private String notes;
    
    // Audit information
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    
    /**
     * Get location data (alias for getLocationData)
     */
    public LocationData getLocation() {
        return locationData;
    }
}