package com.waqiti.ml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IP Geolocation data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpGeolocation {
    
    private String ipAddress;
    private String country;
    private String countryCode;
    private String region;
    private String city;
    private double latitude;
    private double longitude;
    private String timezone;
    private String isp;
    private String asn;
}