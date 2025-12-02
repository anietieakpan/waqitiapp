package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.ShippingAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for address validation results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressValidationResult {
    
    private boolean valid;
    private String errorMessage;
    private String errorCode;
    
    private Double validationScore; // 0.0 to 1.0
    private Double latitude;
    private Double longitude;
    
    private ShippingAddress normalizedAddress;
    
    // Additional validation details
    private boolean deliverable;
    private boolean residential;
    private boolean business;
    private String addressType;
    
    // Suggestions for correction
    private java.util.List<ShippingAddress> suggestedAddresses;
    
    public boolean isValid() {
        return valid && errorMessage == null;
    }
}