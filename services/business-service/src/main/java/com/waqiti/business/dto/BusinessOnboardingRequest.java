package com.waqiti.business.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BusinessOnboardingRequest {
    
    @NotBlank(message = "Business name is required")
    @Size(max = 100, message = "Business name must not exceed 100 characters")
    private String businessName;
    
    @NotBlank(message = "Business type is required")
    private String businessType;
    
    @NotBlank(message = "Industry is required")
    private String industry;
    
    @Size(max = 50, message = "Registration number must not exceed 50 characters")
    private String registrationNumber;
    
    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    private String taxId;
    
    @NotBlank(message = "Address is required")
    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;
    
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phoneNumber;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @Size(max = 255, message = "Website URL must not exceed 255 characters")
    private String website;
}