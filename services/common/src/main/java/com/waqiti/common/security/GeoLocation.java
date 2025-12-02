package com.waqiti.common.security;

/**
 * Geolocation Data Classes
 */
@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
public class GeoLocation implements java.io.Serializable {
    private String countryCode;
    private String countryName;
    private String city;
    private String region;
    private double latitude;
    private double longitude;
    
    public String getCountryCode() {
        return countryCode;
    }
    
    public String getCity() {
        return city;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
}
