package com.waqiti.common.fraud.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * User location profile for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLocationProfile {
    private String userId;
    private LocationData lastKnownLocation;
    private List<LocationData> locationHistory;
    private List<LocationData> commonLocations;
    private GeoPoint centerPoint;
    private Map<String, Integer> timeBasedPatterns;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private Map<String, Object> metadata;
}

