package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import jakarta.validation.constraints.Size;

@Data
@Builder
public class Location {
    private double latitude;
    private double longitude;

    private String country; // PRODUCTION FIX: Country name for fraud context
    @Size(min = 2, max = 2, message = "Country code must be 2 characters")
    private String countryCode;
    private String city;
    private String region;
    private String ipAddress;
}