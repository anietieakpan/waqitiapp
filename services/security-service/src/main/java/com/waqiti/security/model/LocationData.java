package com.waqiti.security.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Location Data Embeddable
 * Represents geographic location information
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationData {

    private Double latitude;
    private Double longitude;
    private String country;
    private String region;
    private String city;
    private String postalCode;
    private String isp;
}
