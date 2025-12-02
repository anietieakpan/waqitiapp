package com.waqiti.common.kyc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Customer address model for KYC verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddress {
    
    @NotBlank
    private String flatNumber;
    private String buildingNumber;
    private String buildingName;
    @NotBlank
    private String street;
    private String subStreet;
    @NotBlank
    private String town;
    private String state;
    @NotBlank
    private String postcode;
    @NotBlank
    private String country;
    private String line1;
    private String line2;
    private String line3;
    
    /**
     * Get formatted address as single string
     */
    public String getFormattedAddress() {
        StringBuilder address = new StringBuilder();
        
        if (flatNumber != null) {
            address.append("Flat ").append(flatNumber).append(", ");
        }
        if (buildingNumber != null) {
            address.append(buildingNumber).append(" ");
        }
        if (buildingName != null) {
            address.append(buildingName).append(", ");
        }
        if (street != null) {
            address.append(street).append(", ");
        }
        if (town != null) {
            address.append(town).append(", ");
        }
        if (state != null) {
            address.append(state).append(" ");
        }
        if (postcode != null) {
            address.append(postcode).append(", ");
        }
        if (country != null) {
            address.append(country);
        }
        
        return address.toString().replaceAll(", $", "");
    }
    
    /**
     * Check if address is complete
     */
    public boolean isComplete() {
        return street != null && !street.trim().isEmpty() &&
               town != null && !town.trim().isEmpty() &&
               postcode != null && !postcode.trim().isEmpty() &&
               country != null && !country.trim().isEmpty();
    }
    
    /**
     * Alias for town - used for compatibility
     */
    public String getCity() {
        return town;
    }
    
    /**
     * Alias for postcode - used for compatibility
     */
    public String getPostalCode() {
        return postcode;
    }
    
    /**
     * Alias for country - used for compatibility
     */
    public String getCountryCode() {
        return country;
    }
}