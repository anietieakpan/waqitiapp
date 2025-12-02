package com.waqiti.bankintegration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

/**
 * Billing details for payment processing
 *
 * Contains customer billing information
 *
 * SECURITY FIX: Replaced @Data with @Getter/@Setter to prevent PII exposure in toString()
 * GDPR Article 32: Personal data (email, phone) must be protected
 */
@lombok.Getter
@lombok.Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingDetails {
    private String name;

    @Email(message = "Invalid email format")
    private String email; // GDPR: PII - must mask in logs

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number")
    private String phone; // GDPR: PII - must mask in logs

    private Address address;

    private String taxId;
    private String companyName;

    /**
     * Custom toString() that masks PII (email, phone) per GDPR requirements
     */
    @Override
    public String toString() {
        return "BillingDetails{" +
            "name='" + name + '\'' +
            ", email='[REDACTED-GDPR]'" +
            ", phone='[REDACTED-GDPR]'" +
            ", address=" + address +
            ", taxId='" + taxId + '\'' +
            ", companyName='" + companyName + '\'' +
            '}';
    }
}