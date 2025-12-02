package com.waqiti.business.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Business Profile Validation Request DTO
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessProfileValidationRequest {
    private String businessName;
    private String ein;
    private String businessAddress;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String principalOwnerName;
    private String industryCode;
    private String businessType;
}
