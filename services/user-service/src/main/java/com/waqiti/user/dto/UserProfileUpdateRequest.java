package com.waqiti.user.dto;

import com.waqiti.user.validation.SafeString;
import com.waqiti.user.validation.ValidPhoneNumber;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

/**
 * User Profile Update Request DTO
 *
 * Contains user profile update information with validation rules.
 *
 * COMPLIANCE RELEVANCE:
 * - GDPR: Personal data processing and consent
 * - KYC: Identity verification data updates
 * - SOC 2: Data integrity and audit trail
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateRequest {

    /**
     * First name
     */
    @Size(min = 1, max = 100)
    @SafeString(maxLength = 100)
    private String firstName;

    /**
     * Last name
     */
    @Size(min = 1, max = 100)
    @SafeString(maxLength = 100)
    private String lastName;

    /**
     * Middle name
     */
    @Size(max = 100)
    @SafeString(maxLength = 100)
    private String middleName;

    /**
     * Email address
     */
    @Email
    @SafeString(maxLength = 255)
    private String email;

    /**
     * Phone number
     */
    @ValidPhoneNumber
    private String phoneNumber;

    /**
     * Date of birth
     */
    private LocalDate dateOfBirth;

    /**
     * Gender
     * Values: MALE, FEMALE, NON_BINARY, PREFER_NOT_TO_SAY, OTHER
     */
    @SafeString(maxLength = 50)
    private String gender;

    /**
     * Nationality
     */
    @SafeString(maxLength = 100)
    private String nationality;

    /**
     * Country of residence
     */
    @SafeString(maxLength = 100)
    private String countryOfResidence;

    /**
     * Address line 1
     */
    @Size(max = 255)
    @SafeString(maxLength = 255)
    private String addressLine1;

    /**
     * Address line 2
     */
    @Size(max = 255)
    @SafeString(maxLength = 255)
    private String addressLine2;

    /**
     * City
     */
    @Size(max = 100)
    @SafeString(maxLength = 100)
    private String city;

    /**
     * State/Province
     */
    @Size(max = 100)
    @SafeString(maxLength = 100)
    private String state;

    /**
     * Postal code
     */
    @Size(max = 20)
    @SafeString(maxLength = 20)
    private String postalCode;

    /**
     * Country
     */
    @Size(max = 100)
    @SafeString(maxLength = 100)
    private String country;

    /**
     * Preferred language
     */
    @SafeString(maxLength = 50)
    private String preferredLanguage;

    /**
     * Timezone
     */
    @SafeString(maxLength = 100)
    private String timezone;

    /**
     * Currency preference
     */
    @SafeString(maxLength = 10)
    private String currencyPreference;

    /**
     * Profile picture URL
     */
    @SafeString(maxLength = 2048)
    private String profilePictureUrl;

    /**
     * Bio/About
     */
    @Size(max = 500)
    @SafeString(allowHtml = true, maxLength = 500)
    private String bio;

    /**
     * Occupation
     */
    @Size(max = 100)
    @SafeString(maxLength = 100)
    private String occupation;

    /**
     * Company name
     */
    @Size(max = 100)
    @SafeString(maxLength = 100)
    private String companyName;

    /**
     * Tax ID/SSN (encrypted)
     */
    @SafeString(maxLength = 255)
    private String taxId;

    /**
     * Identification number
     */
    @SafeString(maxLength = 100)
    private String identificationNumber;

    /**
     * Identification type
     * Values: PASSPORT, DRIVERS_LICENSE, NATIONAL_ID, SSN
     */
    @SafeString(maxLength = 50)
    private String identificationType;

    /**
     * Identification expiry date
     */
    private LocalDate identificationExpiryDate;

    /**
     * Employment status
     * Values: EMPLOYED, SELF_EMPLOYED, UNEMPLOYED, RETIRED, STUDENT
     */
    @SafeString(maxLength = 50)
    private String employmentStatus;

    /**
     * Annual income range
     */
    @SafeString(maxLength = 100)
    private String annualIncomeRange;

    /**
     * Source of funds
     */
    @SafeString(maxLength = 255)
    private String sourceOfFunds;

    /**
     * Politically exposed person flag
     */
    private Boolean politicallyExposedPerson;

    /**
     * Marketing emails enabled
     */
    private Boolean marketingEmailsEnabled;

    /**
     * Notification preferences
     */
    private Map<String, Boolean> notificationPreferences;

    /**
     * Privacy settings
     */
    private Map<String, String> privacySettings;

    /**
     * Two-factor authentication enabled
     */
    private Boolean twoFactorAuthEnabled;

    /**
     * Security question 1
     */
    @SafeString(maxLength = 255)
    private String securityQuestion1;

    /**
     * Security answer 1 (hashed)
     */
    @SafeString(maxLength = 255)
    private String securityAnswer1;

    /**
     * Security question 2
     */
    @SafeString(maxLength = 255)
    private String securityQuestion2;

    /**
     * Security answer 2 (hashed)
     */
    @SafeString(maxLength = 255)
    private String securityAnswer2;

    /**
     * Custom fields
     */
    private Map<String, String> customFields;

    /**
     * Update reason
     */
    @SafeString(maxLength = 500)
    private String updateReason;

    /**
     * Verification documents attached
     */
    private java.util.List<String> verificationDocuments;
}
