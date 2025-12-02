package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Request DTO for generating cash deposit reference numbers.
 * Contains all necessary information to create a cash deposit reference.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateReferenceRequest {

    /**
     * Amount to be deposited.
     */
    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be positive")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "10000.00", message = "Amount cannot exceed 10,000")
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217).
     */
    @NotBlank(message = "Currency cannot be blank")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
    private String currency;

    /**
     * Preferred network provider (e.g., MoneyGram, Western Union).
     */
    @JsonProperty("network_provider")
    @Pattern(regexp = "^(MONEYGRAM|WESTERN_UNION|INGO_MONEY|GREEN_DOT)$", 
             message = "Network provider must be one of: MONEYGRAM, WESTERN_UNION, INGO_MONEY, GREEN_DOT")
    private String networkProvider;

    /**
     * User details for the deposit.
     */
    @NotNull(message = "User details cannot be null")
    @Valid
    @JsonProperty("user_details")
    private UserDetails userDetails;

    /**
     * Deposit preferences.
     */
    @Valid
    @JsonProperty("deposit_preferences")
    private DepositPreferences depositPreferences;

    /**
     * Device information for fraud prevention.
     */
    @Valid
    @JsonProperty("device_info")
    private DeviceInfo deviceInfo;

    /**
     * User details for the cash deposit.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDetails {
        
        @NotBlank(message = "User ID cannot be blank")
        @JsonProperty("user_id")
        private String userId;
        
        @NotBlank(message = "First name cannot be blank")
        @Size(max = 50, message = "First name cannot exceed 50 characters")
        @JsonProperty("first_name")
        private String firstName;
        
        @NotBlank(message = "Last name cannot be blank")
        @Size(max = 50, message = "Last name cannot exceed 50 characters")
        @JsonProperty("last_name")
        private String lastName;
        
        @Pattern(regexp = "^[+]?[1-9]\\d{1,14}$", message = "Invalid phone number format")
        @JsonProperty("phone_number")
        private String phoneNumber;
        
        @jakarta.validation.constraints.Email(message = "Invalid email format")
        private String email;
        
        /**
         * Date of birth in YYYY-MM-DD format.
         */
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date of birth must be in YYYY-MM-DD format")
        @JsonProperty("date_of_birth")
        private String dateOfBirth;
        
        /**
         * Address for verification purposes.
         */
        @Valid
        private Address address;
    }

    /**
     * Address information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        
        @NotBlank(message = "Street address cannot be blank")
        @Size(max = 100, message = "Street address cannot exceed 100 characters")
        @JsonProperty("street_address")
        private String streetAddress;
        
        @NotBlank(message = "City cannot be blank")
        @Size(max = 50, message = "City cannot exceed 50 characters")
        private String city;
        
        @NotBlank(message = "State cannot be blank")
        @Pattern(regexp = "^[A-Z]{2}$", message = "State must be a valid 2-letter state code")
        private String state;
        
        @NotBlank(message = "ZIP code cannot be blank")
        @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "ZIP code must be in format 12345 or 12345-6789")
        @JsonProperty("zip_code")
        private String zipCode;
        
        @NotBlank(message = "Country cannot be blank")
        @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a valid 2-letter country code")
        private String country;
    }

    /**
     * Deposit preferences and options.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepositPreferences {
        
        /**
         * Preferred location ID (if any).
         */
        @JsonProperty("preferred_location_id")
        private String preferredLocationId;
        
        /**
         * Maximum distance from user location (in miles).
         */
        @DecimalMin(value = "0.1", message = "Maximum distance must be at least 0.1 miles")
        @DecimalMax(value = "50.0", message = "Maximum distance cannot exceed 50 miles")
        @JsonProperty("max_distance_miles")
        private BigDecimal maxDistanceMiles;
        
        /**
         * Whether to include QR code in response.
         */
        @JsonProperty("include_qr_code")
        @Builder.Default
        private Boolean includeQrCode = true;
        
        /**
         * Whether to include barcode in response.
         */
        @JsonProperty("include_barcode")
        @Builder.Default
        private Boolean includeBarcode = true;
        
        /**
         * Preferred language for instructions.
         */
        @Pattern(regexp = "^[a-z]{2}$", message = "Language must be a valid 2-letter language code")
        @Builder.Default
        private String language = "en";
        
        /**
         * Whether to send SMS notification.
         */
        @JsonProperty("send_sms_notification")
        @Builder.Default
        private Boolean sendSmsNotification = false;
        
        /**
         * Whether to send email notification.
         */
        @JsonProperty("send_email_notification")
        @Builder.Default
        private Boolean sendEmailNotification = false;
    }

    /**
     * Device information for fraud prevention and analytics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        
        /**
         * Device fingerprint or unique identifier.
         */
        @JsonProperty("device_fingerprint")
        private String deviceFingerprint;
        
        /**
         * IP address of the requesting device.
         */
        @JsonProperty("ip_address")
        private String ipAddress;
        
        /**
         * User agent string.
         */
        @JsonProperty("user_agent")
        private String userAgent;
        
        /**
         * Geographic location (latitude, longitude).
         */
        private Location location;
        
        /**
         * Device platform (iOS, Android, Web).
         */
        @Pattern(regexp = "^(iOS|Android|Web)$", message = "Platform must be one of: iOS, Android, Web")
        private String platform;
        
        /**
         * App version.
         */
        @JsonProperty("app_version")
        private String appVersion;
    }

    /**
     * Geographic location information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        private BigDecimal latitude;
        
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        private BigDecimal longitude;
        
        /**
         * Accuracy of the location in meters.
         */
        @Positive(message = "Accuracy must be positive")
        private BigDecimal accuracy;
        
        /**
         * Timestamp when location was captured.
         */
        private Long timestamp;
    }

    /**
     * Validates the request for business logic constraints.
     *
     * @return true if valid
     * @throws IllegalArgumentException if validation fails
     */
    public boolean validateBusinessRules() {
        // Check if amount is within network provider limits
        if (networkProvider != null) {
            switch (networkProvider) {
                case "MONEYGRAM":
                    if (amount.compareTo(new BigDecimal("2999.99")) > 0) {
                        throw new IllegalArgumentException("MoneyGram deposits cannot exceed $2,999.99");
                    }
                    break;
                case "WESTERN_UNION":
                    if (amount.compareTo(new BigDecimal("7500.00")) > 0) {
                        throw new IllegalArgumentException("Western Union deposits cannot exceed $7,500.00");
                    }
                    break;
                case "INGO_MONEY":
                    if (amount.compareTo(new BigDecimal("5000.00")) > 0) {
                        throw new IllegalArgumentException("Ingo Money deposits cannot exceed $5,000.00");
                    }
                    break;
                case "GREEN_DOT":
                    if (amount.compareTo(new BigDecimal("2500.00")) > 0) {
                        throw new IllegalArgumentException("Green Dot deposits cannot exceed $2,500.00");
                    }
                    break;
            }
        }
        
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GenerateReferenceRequest that = (GenerateReferenceRequest) o;
        return Objects.equals(amount, that.amount) &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(networkProvider, that.networkProvider) &&
               Objects.equals(userDetails, that.userDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency, networkProvider, userDetails);
    }

    @Override
    public String toString() {
        return "GenerateReferenceRequest{" +
               "amount=" + amount +
               ", currency='" + currency + '\'' +
               ", networkProvider='" + networkProvider + '\'' +
               ", userId='" + (userDetails != null ? userDetails.userId : null) + '\'' +
               '}';
    }
}