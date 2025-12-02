package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Geo Location Data
 * Contains detailed geographic information from IP lookup
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoLocationData {

    private String ipAddress;
    private String country;
    private String countryCode;
    private String region;
    private String regionCode;
    private String city;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private String timezone;
    private String isp;
    private String organization;
    private String asn;
    private Boolean isProxy;
    private Boolean isVpn;
    private Boolean isTor;
    private String threatLevel;
}
