package com.waqiti.compliance.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Configuration properties for financial institution information used in regulatory filings
 * Validates all required fields for SAR, CTR, and other FinCEN filings
 *
 * CRITICAL: These values must be configured correctly in production
 * Incorrect values will cause filing rejections and regulatory violations
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "waqiti.compliance.institution")
@Validated
public class InstitutionProperties {

    /**
     * Legal name of the financial institution
     * Example: "Waqiti Financial Services Inc."
     */
    @NotBlank(message = "Institution name must be configured for regulatory filings")
    private String name;

    /**
     * Employer Identification Number (EIN) - Format: XX-XXXXXXX
     * CRITICAL: This must be your actual EIN, not a placeholder
     * FinCEN will reject filings with invalid EINs
     */
    @NotBlank(message = "Institution EIN must be configured for regulatory filings")
    @Pattern(
        regexp = "\\d{2}-\\d{7}",
        message = "Institution EIN must be in format XX-XXXXXXX (e.g., 12-3456789)"
    )
    private String ein;

    /**
     * Physical address of the institution
     */
    private Address address = new Address();

    /**
     * Contact information for compliance officer
     */
    private Contact contact = new Contact();

    /**
     * Regulatory identifiers and information
     */
    private Regulatory regulatory = new Regulatory();

    @Data
    public static class Address {
        @NotBlank(message = "Institution street address required for regulatory filings")
        private String street;

        @NotBlank(message = "Institution city required for regulatory filings")
        private String city;

        @NotBlank(message = "Institution state required for regulatory filings")
        @Pattern(regexp = "[A-Z]{2}", message = "State must be 2-letter code (e.g., NY)")
        private String state;

        @NotBlank(message = "Institution ZIP code required for regulatory filings")
        @Pattern(regexp = "\\d{5}(-\\d{4})?", message = "ZIP must be 5 or 9 digits (e.g., 10001 or 10001-1234)")
        private String zip;

        private String country = "United States";

        /**
         * Formats address for regulatory forms
         */
        public String getFormattedAddress() {
            return String.format("%s, %s, %s %s, %s", street, city, state, zip, country);
        }
    }

    @Data
    public static class Contact {
        @NotBlank(message = "Compliance officer name required for regulatory filings")
        private String complianceOfficerName;

        @NotBlank(message = "Compliance officer title required for regulatory filings")
        private String complianceOfficerTitle;

        @NotBlank(message = "Compliance officer phone required for regulatory filings")
        @Pattern(
            regexp = "\\+?1?-?\\(?\\d{3}\\)?-?\\d{3}-?\\d{4}",
            message = "Phone must be valid US format (e.g., +1-800-555-0100)"
        )
        private String complianceOfficerPhone;

        @NotBlank(message = "Compliance officer email required for regulatory filings")
        @Email(message = "Compliance officer email must be valid")
        private String complianceOfficerEmail;
    }

    @Data
    public static class Regulatory {
        /**
         * Filing institution code assigned by FinCEN
         */
        @NotBlank(message = "Filing institution code required for FinCEN submissions")
        private String filingInstitutionCode;

        /**
         * Research, Statistics, Supervision, Discount & Credit ID
         * Assigned by Federal Reserve for banking institutions
         * Optional for MSBs
         */
        private String rssdId;

        /**
         * Primary regulatory authority
         * Examples: FinCEN, OCC, FDIC, Federal Reserve, State Banking Department
         */
        @NotBlank(message = "Primary regulator must be specified")
        private String primaryRegulator;

        /**
         * Type of charter
         * Examples: Money Services Business, National Bank, State Bank, Credit Union
         */
        @NotBlank(message = "Charter type must be specified")
        private String charterType;
    }

    /**
     * Validates that the EIN is not a placeholder value
     * @return true if EIN appears to be configured properly
     */
    public boolean isEinConfigured() {
        return ein != null && !ein.equals("XX-XXXXXXX") && !ein.contains("X");
    }

    /**
     * Validates that all required fields are configured for production
     * @return true if all critical fields are present and valid
     */
    public boolean isFullyConfigured() {
        return isEinConfigured()
            && name != null && !name.isEmpty()
            && address != null && address.street != null
            && contact != null && contact.complianceOfficerEmail != null
            && regulatory != null && regulatory.filingInstitutionCode != null;
    }
}
