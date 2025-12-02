package com.waqiti.common.fraud.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Location data model for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationData {
    private String userId;
    private String transactionId;
    private double latitude;
    private double longitude;
    private String countryCode;
    private String city;
    private String region;
    private String postalCode;
    private String ipAddress;
    private LocationService.ConnectionType connectionType;
    private String deviceId;
    private String userAgent;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
    
    /**
     * Get location as string
     */
    public String getLocationString() {
        return String.format("%s, %s, %s", city, region, countryCode);
    }
    
    /**
     * Check if location is valid
     */
    public boolean isValid() {
        return latitude >= -90 && latitude <= 90 &&
               longitude >= -180 && longitude <= 180;
    }
}