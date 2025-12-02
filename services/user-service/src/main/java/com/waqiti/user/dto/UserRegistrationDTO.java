package com.waqiti.user.dto;

import com.waqiti.common.validation.ValidationConstraints.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * User registration DTO with comprehensive validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationDTO {

    @NotBlank(message = "Username is required")
    @ValidUsername(minLength = 3, maxLength = 30, 
                  reservedUsernames = {"admin", "root", "system", "api", "support", "waqiti"})
    @NoSQLInjection
    @NoXSS
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @ValidEmail(checkDNS = true, allowDisposable = false, 
               blockedDomains = {"example.com", "test.com"})
    private String email;

    @NotBlank(message = "Password is required")
    @StrongPassword(minLength = 12, requireUppercase = true, requireLowercase = true,
                   requireDigit = true, requireSpecialChar = true, 
                   preventCommonPasswords = true, preventUserInfoInPassword = true)
    private String password;

    @NotBlank(message = "Password confirmation is required")
    private String passwordConfirmation;

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z\\s\\-\\']+$", message = "First name contains invalid characters")
    @NoXSS
    @NoSQLInjection
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z\\s\\-\\']+$", message = "Last name contains invalid characters")
    @NoXSS
    @NoSQLInjection
    private String lastName;

    @Size(max = 50)
    @Pattern(regexp = "^[a-zA-Z\\s\\-\\']*$", message = "Middle name contains invalid characters")
    @NoXSS
    private String middleName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    @ValidAge(minAge = 18, maxAge = 120)
    private LocalDate dateOfBirth;

    @NotBlank(message = "Phone number is required")
    @ValidPhoneNumber(requireInternationalFormat = true)
    private String phoneNumber;

    @ValidPhoneNumber(requireInternationalFormat = false)
    private String alternatePhoneNumber;

    @NotBlank(message = "Country is required")
    @ValidCountryCode
    private String country;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 100, message = "Address line 1 cannot exceed 100 characters")
    @NoXSS
    @NoSQLInjection
    private String addressLine1;

    @Size(max = 100, message = "Address line 2 cannot exceed 100 characters")
    @NoXSS
    @NoSQLInjection
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 50, message = "City cannot exceed 50 characters")
    @Pattern(regexp = "^[a-zA-Z\\s\\-]+$", message = "City contains invalid characters")
    private String city;

    @NotBlank(message = "State/Province is required")
    @Size(max = 50, message = "State/Province cannot exceed 50 characters")
    @Pattern(regexp = "^[a-zA-Z\\s\\-]+$", message = "State/Province contains invalid characters")
    private String stateProvince;

    @NotBlank(message = "Postal code is required")
    @ValidPostalCode
    private String postalCode;

    @ValidTaxId(type = TaxIdType.AUTO)
    private String taxIdentificationNumber;

    @ValidSSN(masked = false)
    private String socialSecurityNumber;

    @Pattern(regexp = "^(PASSPORT|DRIVERS_LICENSE|NATIONAL_ID|VOTER_ID)$",
             message = "Invalid ID type")
    private String idType;

    @Size(max = 50)
    @Pattern(regexp = "^[A-Z0-9\\-]+$", message = "Invalid ID number format")
    private String idNumber;

    @Future(message = "ID expiry date must be in the future")
    private LocalDate idExpiryDate;

    @ValidCountryCode
    private String idIssuingCountry;

    @Pattern(regexp = "^(PERSONAL|BUSINESS)$", message = "Invalid account type")
    @NotBlank(message = "Account type is required")
    private String accountType;

    @ValidBusinessNumber(type = BusinessNumberType.AUTO)
    private String businessRegistrationNumber;

    @Size(max = 100)
    @NoXSS
    private String companyName;

    @ValidURL(requireSSL = true)
    private String companyWebsite;

    @Pattern(regexp = "^(MALE|FEMALE|OTHER|PREFER_NOT_TO_SAY)$", 
             message = "Invalid gender value")
    private String gender;

    @Pattern(regexp = "^(SINGLE|MARRIED|DIVORCED|WIDOWED|OTHER)$",
             message = "Invalid marital status")
    private String maritalStatus;

    @Pattern(regexp = "^(EMPLOYED|SELF_EMPLOYED|UNEMPLOYED|STUDENT|RETIRED|OTHER)$",
             message = "Invalid employment status")
    private String employmentStatus;

    @Size(max = 100)
    @NoXSS
    private String occupation;

    @Size(max = 100)
    @NoXSS
    private String employer;

    @DecimalMin(value = "0", message = "Annual income cannot be negative")
    @DecimalMax(value = "999999999", message = "Annual income value too large")
    private Double annualIncome;

    @Pattern(regexp = "^(USD|EUR|GBP|JPY|CNY|INR|AUD|CAD|CHF|NZD)$",
             message = "Invalid income currency")
    private String incomeCurrency;

    @ValidIPAddress(allowPrivate = false, allowLoopback = false)
    private String registrationIpAddress;

    @Pattern(regexp = "^[A-Za-z0-9+/=]+$", message = "Invalid device fingerprint")
    @Size(max = 500)
    private String deviceFingerprint;

    @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$", message = "Invalid locale format")
    private String preferredLocale;

    @Pattern(regexp = "^[A-Za-z]+/[A-Za-z_]+$", message = "Invalid timezone format")
    private String timezone;

    @NotNull(message = "Terms acceptance is required")
    @AssertTrue(message = "You must accept the terms and conditions")
    private Boolean acceptedTerms;

    @NotNull(message = "Privacy policy acceptance is required")
    @AssertTrue(message = "You must accept the privacy policy")
    private Boolean acceptedPrivacyPolicy;

    @NotNull(message = "Marketing consent preference is required")
    private Boolean marketingConsent;

    @NotNull(message = "Data sharing consent preference is required")
    private Boolean dataProcessingConsent;

    @Size(max = 50)
    @Pattern(regexp = "^[A-Za-z0-9\\-_]+$", message = "Invalid referral code")
    private String referralCode;

    @Size(max = 10)
    private List<@NotBlank @Pattern(regexp = "^[A-Z]{2}$") String> preferredLanguages;

    @Size(max = 5)
    private List<@NotBlank @ValidCurrency String> preferredCurrencies;

    @ValidJSON(maxDepth = 3, maxKeys = 10)
    private String additionalInfo;

    @AssertTrue(message = "Passwords do not match")
    private boolean isPasswordConfirmed() {
        return password != null && password.equals(passwordConfirmation);
    }

    @AssertTrue(message = "Business details required for BUSINESS account type")
    private boolean isBusinessDetailsValid() {
        if ("BUSINESS".equals(accountType)) {
            return businessRegistrationNumber != null && companyName != null;
        }
        return true;
    }

    @AssertTrue(message = "You must be at least 18 years old to register")
    private boolean isAdult() {
        if (dateOfBirth != null) {
            return !dateOfBirth.isAfter(LocalDate.now().minusYears(18));
        }
        return false;
    }

    @AssertTrue(message = "SSN required for US residents")
    private boolean isSSNProvidedForUS() {
        if ("US".equals(country)) {
            return socialSecurityNumber != null && !socialSecurityNumber.isEmpty();
        }
        return true;
    }
}