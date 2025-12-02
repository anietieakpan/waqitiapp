package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * DTO representing a cash deposit location with coordinates, address, network info, and operating hours.
 * Used for location search and display functionality.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDepositLocation {

    /**
     * Unique location identifier.
     */
    @NotBlank(message = "Location ID cannot be blank")
    @JsonProperty("location_id")
    private String locationId;

    /**
     * Display name of the location.
     */
    @NotBlank(message = "Location name cannot be blank")
    @Size(max = 100, message = "Location name cannot exceed 100 characters")
    @JsonProperty("location_name")
    private String locationName;

    /**
     * Geographic coordinates.
     */
    @NotNull(message = "Coordinates cannot be null")
    private Coordinates coordinates;

    /**
     * Physical address of the location.
     */
    @NotNull(message = "Address cannot be null")
    private Address address;

    /**
     * Network provider operating at this location.
     */
    @NotBlank(message = "Network provider cannot be blank")
    @Pattern(regexp = "^(MONEYGRAM|WESTERN_UNION|INGO_MONEY|GREEN_DOT)$", 
             message = "Network provider must be one of: MONEYGRAM, WESTERN_UNION, INGO_MONEY, GREEN_DOT")
    @JsonProperty("network_provider")
    private String networkProvider;

    /**
     * Operating hours for cash deposit services.
     */
    @JsonProperty("operating_hours")
    private OperatingHours operatingHours;

    /**
     * Location capabilities and features.
     */
    private LocationFeatures features;

    /**
     * Contact information.
     */
    @JsonProperty("contact_info")
    private ContactInfo contactInfo;

    /**
     * Distance from user's location (populated during search).
     */
    @JsonProperty("distance_miles")
    private BigDecimal distanceMiles;

    /**
     * Current operational status.
     */
    @Builder.Default
    private LocationStatus status = LocationStatus.ACTIVE;

    /**
     * Additional location metadata.
     */
    @Builder.Default
    private java.util.Map<String, Object> metadata = new java.util.HashMap<>();

    /**
     * Geographic coordinates.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinates {
        
        @NotNull(message = "Latitude cannot be null")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        private BigDecimal latitude;
        
        @NotNull(message = "Longitude cannot be null")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        private BigDecimal longitude;
        
        /**
         * Accuracy of coordinates in meters.
         */
        private BigDecimal accuracy;
    }

    /**
     * Physical address information.
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
        
        @Size(max = 50, message = "Address line 2 cannot exceed 50 characters")
        @JsonProperty("address_line_2")
        private String addressLine2;
        
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
        
        /**
         * Gets formatted address string.
         */
        public String getFormattedAddress() {
            StringBuilder sb = new StringBuilder();
            sb.append(streetAddress);
            
            if (addressLine2 != null && !addressLine2.trim().isEmpty()) {
                sb.append(", ").append(addressLine2);
            }
            
            sb.append(", ").append(city)
              .append(", ").append(state)
              .append(" ").append(zipCode);
            
            return sb.toString();
        }
    }

    /**
     * Operating hours information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperatingHours {
        
        @JsonProperty("monday")
        private DayHours monday;
        
        @JsonProperty("tuesday")
        private DayHours tuesday;
        
        @JsonProperty("wednesday")
        private DayHours wednesday;
        
        @JsonProperty("thursday")
        private DayHours thursday;
        
        @JsonProperty("friday")
        private DayHours friday;
        
        @JsonProperty("saturday")
        private DayHours saturday;
        
        @JsonProperty("sunday")
        private DayHours sunday;
        
        /**
         * Special holiday hours or closures.
         */
        @JsonProperty("special_hours")
        @Builder.Default
        private java.util.List<SpecialHours> specialHours = new java.util.ArrayList<>();
        
        /**
         * Timezone for the operating hours.
         */
        @Builder.Default
        private String timezone = "America/New_York";
    }

    /**
     * Hours for a specific day.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayHours {
        
        /**
         * Whether the location is open on this day.
         */
        @Builder.Default
        private Boolean open = true;
        
        /**
         * Opening time in HH:mm format (24-hour).
         */
        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Opening time must be in HH:mm format")
        @JsonProperty("open_time")
        private String openTime;
        
        /**
         * Closing time in HH:mm format (24-hour).
         */
        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Closing time must be in HH:mm format")
        @JsonProperty("close_time")
        private String closeTime;
        
        /**
         * Lunch break start time (optional).
         */
        @JsonProperty("lunch_start")
        private String lunchStart;
        
        /**
         * Lunch break end time (optional).
         */
        @JsonProperty("lunch_end")
        private String lunchEnd;
    }

    /**
     * Special hours for holidays or events.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecialHours {
        
        @NotBlank(message = "Date cannot be blank")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in YYYY-MM-DD format")
        private String date;
        
        private String description;
        
        @Builder.Default
        private Boolean open = false;
        
        @JsonProperty("open_time")
        private String openTime;
        
        @JsonProperty("close_time")
        private String closeTime;
    }

    /**
     * Location features and capabilities.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationFeatures {
        
        /**
         * Whether location accepts cash deposits.
         */
        @JsonProperty("accepts_cash_deposits")
        @Builder.Default
        private Boolean acceptsCashDeposits = true;
        
        /**
         * Whether location provides QR code scanning.
         */
        @JsonProperty("qr_code_scanning")
        @Builder.Default
        private Boolean qrCodeScanning = true;
        
        /**
         * Whether location provides barcode scanning.
         */
        @JsonProperty("barcode_scanning")
        @Builder.Default
        private Boolean barcodeScanning = true;
        
        /**
         * Whether location provides instant confirmation.
         */
        @JsonProperty("instant_confirmation")
        @Builder.Default
        private Boolean instantConfirmation = true;
        
        /**
         * Maximum deposit amount at this location.
         */
        @JsonProperty("max_deposit_amount")
        private BigDecimal maxDepositAmount;
        
        /**
         * Minimum deposit amount at this location.
         */
        @JsonProperty("min_deposit_amount")
        private BigDecimal minDepositAmount;
        
        /**
         * Accepted currencies.
         */
        @JsonProperty("accepted_currencies")
        @Builder.Default
        private java.util.List<String> acceptedCurrencies = java.util.List.of("USD");
        
        /**
         * Available languages for customer service.
         */
        @JsonProperty("available_languages")
        @Builder.Default
        private java.util.List<String> availableLanguages = java.util.List.of("en");
    }

    /**
     * Contact information for the location.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactInfo {
        
        @Pattern(regexp = "^[+]?[1-9]\\d{1,14}$", message = "Invalid phone number format")
        @JsonProperty("phone_number")
        private String phoneNumber;
        
        @jakarta.validation.constraints.Email(message = "Invalid email format")
        private String email;
        
        /**
         * Website URL for the location.
         */
        private String website;
        
        /**
         * Customer service phone number.
         */
        @JsonProperty("customer_service_phone")
        private String customerServicePhone;
    }

    /**
     * Location operational status.
     */
    public enum LocationStatus {
        ACTIVE("Location is active and accepting deposits"),
        INACTIVE("Location is temporarily inactive"),
        CLOSED("Location is permanently closed"),
        MAINTENANCE("Location is under maintenance"),
        LIMITED_SERVICE("Location has limited service available");

        private final String description;

        LocationStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Calculates whether the location is currently open.
     *
     * @return true if location is currently open
     */
    public boolean isCurrentlyOpen() {
        if (operatingHours == null || status != LocationStatus.ACTIVE) {
            return false;
        }
        
        java.time.LocalTime now = java.time.LocalTime.now();
        java.time.DayOfWeek today = java.time.LocalDate.now().getDayOfWeek();
        
        DayHours todayHours = getTodayHours(today);
        
        if (todayHours == null || !Boolean.TRUE.equals(todayHours.open)) {
            return false;
        }
        
        try {
            java.time.LocalTime openTime = java.time.LocalTime.parse(todayHours.openTime);
            java.time.LocalTime closeTime = java.time.LocalTime.parse(todayHours.closeTime);
            
            return !now.isBefore(openTime) && now.isBefore(closeTime);
        } catch (Exception e) {
            return false;
        }
    }

    private DayHours getTodayHours(java.time.DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY: return operatingHours.monday;
            case TUESDAY: return operatingHours.tuesday;
            case WEDNESDAY: return operatingHours.wednesday;
            case THURSDAY: return operatingHours.thursday;
            case FRIDAY: return operatingHours.friday;
            case SATURDAY: return operatingHours.saturday;
            case SUNDAY: return operatingHours.sunday;
            default: {
                log.warn("CRITICAL: Invalid day of week provided for operating hours: {}", dayOfWeek);
                throw new IllegalArgumentException("Invalid day of week: " + dayOfWeek);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CashDepositLocation that = (CashDepositLocation) o;
        return Objects.equals(locationId, that.locationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId);
    }

    @Override
    public String toString() {
        return "CashDepositLocationDto{" +
               "locationId='" + locationId + '\'' +
               ", locationName='" + locationName + '\'' +
               ", networkProvider='" + networkProvider + '\'' +
               ", status=" + status +
               ", distanceMiles=" + distanceMiles +
               '}';
    }
}