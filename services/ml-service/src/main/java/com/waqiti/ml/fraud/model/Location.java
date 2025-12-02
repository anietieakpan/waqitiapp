package com.waqiti.ml.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Geographic location data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    private Double latitude;
    private Double longitude;
    private String country;
    private String countryCode;
    private String region;
    private String city;
    private String postalCode;
    private String timezone;

    public Location(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Calculate distance to another location in kilometers using Haversine formula
     */
    public double distanceTo(Location other) {
        if (this.latitude == null || this.longitude == null ||
            other.latitude == null || other.longitude == null) {
            return 0.0;
        }

        final int R = 6371; // Radius of the earth in km

        double latDistance = Math.toRadians(other.latitude - this.latitude);
        double lonDistance = Math.toRadians(other.longitude - this.longitude);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(other.latitude))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Check if location is valid
     */
    public boolean isValid() {
        return latitude != null && longitude != null &&
               latitude >= -90 && latitude <= 90 &&
               longitude >= -180 && longitude <= 180;
    }
}
