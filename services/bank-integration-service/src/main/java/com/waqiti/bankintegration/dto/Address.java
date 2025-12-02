package com.waqiti.bankintegration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Address information for billing and shipping
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    @NotBlank(message = "Address line 1 is required")
    @Size(max = 100, message = "Address line 1 cannot exceed 100 characters")
    private String line1;
    
    @Size(max = 100, message = "Address line 2 cannot exceed 100 characters")
    private String line2;
    
    @NotBlank(message = "City is required")
    @Size(max = 50, message = "City cannot exceed 50 characters")
    private String city;
    
    @Size(max = 50, message = "State cannot exceed 50 characters")
    private String state;
    
    @Pattern(regexp = "^[A-Za-z0-9\\s-]+$", message = "Invalid postal code format")
    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    private String postalCode;
    
    @NotBlank(message = "Country is required")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a 2-letter ISO code")
    private String country;
}