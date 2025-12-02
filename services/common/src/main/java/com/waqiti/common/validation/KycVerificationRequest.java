package com.waqiti.common.validation;

import lombok.Builder;
import lombok.Data;

/**
 * KYC verification request for validation
 */
@Data
@Builder
public class KycVerificationRequest {
    private String documentType;
    private String documentNumber;
    private String dateOfBirth;
    private String address;
    private String nationality;
    private String occupation;
    private String sourceOfFunds;
}