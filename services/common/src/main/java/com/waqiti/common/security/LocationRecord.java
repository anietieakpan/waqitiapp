package com.waqiti.common.security;

import java.time.Instant;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
public class LocationRecord implements java.io.Serializable {
    private double latitude;
    private double longitude;
    private String countryCode;
    private String city;
    private Instant timestamp;
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
}
