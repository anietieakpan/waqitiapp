package com.waqiti.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PII Data Response DTO
 *
 * Contains personally identifiable information with appropriate masking
 * and access tracking for GDPR compliance.
 *
 * COMPLIANCE RELEVANCE:
 * - GDPR: Personal data access and processing
 * - CCPA: California Consumer Privacy Act compliance
 * - SOC 2: Data privacy and security controls
 * - PCI DSS: Sensitive data protection
 *
 * SECURITY NOTE:
 * This response contains PII and must be handled with extreme care.
 * All access is logged for compliance auditing.
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PIIDataResponse {

    /**
     * User ID
     */
    @NotNull
    private UUID userId;

    /**
     * First name
     */
    private String firstName;

    /**
     * Last name
     */
    private String lastName;

    /**
     * Middle name
     */
    private String middleName;

    /**
     * Full name
     */
    private String fullName;

    /**
     * Email address
     */
    private String email;

    /**
     * Phone number
     */
    private String phoneNumber;

    /**
     * Date of birth
     */
    private LocalDate dateOfBirth;

    /**
     * Age
     */
    private Integer age;

    /**
     * Gender
     */
    private String gender;

    /**
     * Nationality
     */
    private String nationality;

    /**
     * Social Security Number (masked)
     */
    private String maskedSSN;

    /**
     * Tax ID (masked)
     */
    private String maskedTaxId;

    /**
     * Identification number (masked)
     */
    private String maskedIdentificationNumber;

    /**
     * Identification type
     */
    private String identificationType;

    /**
     * Passport number (masked)
     */
    private String maskedPassportNumber;

    /**
     * Driver's license number (masked)
     */
    private String maskedDriversLicense;

    /**
     * Full address
     */
    private Address fullAddress;

    /**
     * Previous addresses
     */
    private List<Address> previousAddresses;

    /**
     * Billing address
     */
    private Address billingAddress;

    /**
     * Shipping addresses
     */
    private List<Address> shippingAddresses;

    /**
     * Employment information
     */
    private EmploymentInfo employmentInfo;

    /**
     * Financial information
     */
    private FinancialInfo financialInfo;

    /**
     * Medical information (if applicable)
     */
    private Map<String, String> medicalInfo;

    /**
     * Biometric data flags (not actual data)
     */
    private BiometricDataFlags biometricFlags;

    /**
     * Family information
     */
    private FamilyInfo familyInfo;

    /**
     * Emergency contacts
     */
    private List<EmergencyContact> emergencyContacts;

    /**
     * Data fields accessed
     */
    @NotNull
    private List<String> accessedFields;

    /**
     * Access reason
     */
    @NotNull
    private String accessReason;

    /**
     * Legal basis for processing
     * Values: CONSENT, CONTRACT, LEGAL_OBLIGATION, VITAL_INTERESTS, PUBLIC_TASK, LEGITIMATE_INTERESTS
     */
    @NotNull
    private String legalBasis;

    /**
     * Consent verified flag
     */
    private boolean consentVerified;

    /**
     * Consent ID
     */
    private UUID consentId;

    /**
     * Consent timestamp
     */
    private LocalDateTime consentTimestamp;

    /**
     * Data retention period (days)
     */
    private Integer dataRetentionPeriodDays;

    /**
     * Data deletion scheduled
     */
    private boolean dataDeletionScheduled;

    /**
     * Scheduled deletion date
     */
    private LocalDate scheduledDeletionDate;

    /**
     * Data portability available
     */
    private boolean dataPortabilityAvailable;

    /**
     * Right to be forgotten available
     */
    private boolean rightToBeForgottenAvailable;

    /**
     * Data source
     * Values: USER_PROVIDED, THIRD_PARTY, INFERRED, DERIVED
     */
    private Map<String, String> dataSource;

    /**
     * Last updated timestamp
     */
    private LocalDateTime lastUpdated;

    /**
     * Accessed by (user ID who accessed this data)
     */
    @NotNull
    private UUID accessedBy;

    /**
     * Accessed at timestamp
     */
    @NotNull
    private LocalDateTime accessedAt;

    /**
     * Access logged flag
     */
    private boolean accessLogged;

    /**
     * Audit trail ID
     */
    private UUID auditTrailId;

    /**
     * Data classification
     * Values: PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED, SECRET
     */
    private String dataClassification;

    /**
     * Encryption status
     */
    private boolean encrypted;

    /**
     * Pseudonymized flag
     */
    private boolean pseudonymized;

    /**
     * Anonymized flag
     */
    private boolean anonymized;

    // Nested classes

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private String addressType;
        private LocalDate validFrom;
        private LocalDate validTo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmploymentInfo {
        private String employmentStatus;
        private String employerName;
        private String occupation;
        private String jobTitle;
        private String industry;
        private LocalDate employmentStartDate;
        private LocalDate employmentEndDate;
        private String annualIncomeRange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialInfo {
        private String annualIncomeRange;
        private String netWorthRange;
        private String sourceOfFunds;
        private String sourceOfWealth;
        private boolean politicallyExposedPerson;
        private String creditScoreRange;
        private String financialRiskProfile;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BiometricDataFlags {
        private boolean fingerprintStored;
        private boolean facialRecognitionStored;
        private boolean voicePrintStored;
        private boolean irisS canStored;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FamilyInfo {
        private String maritalStatus;
        private Integer numberOfDependents;
        private List<String> dependentAges;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmergencyContact {
        private String name;
        private String relationship;
        private String phoneNumber;
        private String email;
    }
}
