package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Request DTO for searching cash deposit locations based on various criteria.
 * Supports geographic, network, and feature-based filtering.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationRequest {

    /**
     * User's current location for distance-based search.
     */
    @NotNull(message = "User location cannot be null")
    @JsonProperty("user_location")
    private UserLocation userLocation;

    /**
     * Maximum distance from user location in miles.
     */
    @DecimalMin(value = "0.1", message = "Maximum distance must be at least 0.1 miles")
    @DecimalMax(value = "100.0", message = "Maximum distance cannot exceed 100 miles")
    @JsonProperty("max_distance_miles")
    @Builder.Default
    private BigDecimal maxDistanceMiles = new BigDecimal("25.0");

    /**
     * Preferred network providers (filter by specific networks).
     */
    @JsonProperty("preferred_networks")
    private java.util.List<String> preferredNetworks;

    /**
     * Minimum deposit amount the location should support.
     */
    @JsonProperty("min_deposit_amount")
    private BigDecimal minDepositAmount;

    /**
     * Maximum deposit amount the location should support.
     */
    @JsonProperty("max_deposit_amount")
    private BigDecimal maxDepositAmount;

    /**
     * Required features for the location.
     */
    @JsonProperty("required_features")
    private RequiredFeatures requiredFeatures;

    /**
     * Search filters and preferences.
     */
    @JsonProperty("search_filters")
    private SearchFilters searchFilters;

    /**
     * Pagination settings.
     */
    private Pagination pagination;

    /**
     * Sorting preferences.
     */
    @JsonProperty("sort_options")
    private SortOptions sortOptions;

    /**
     * User's current location.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserLocation {
        
        @NotNull(message = "Latitude cannot be null")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        private BigDecimal latitude;
        
        @NotNull(message = "Longitude cannot be null")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        private BigDecimal longitude;
        
        /**
         * Accuracy of the user's location in meters.
         */
        private BigDecimal accuracy;
        
        /**
         * Address components for fallback search.
         */
        @JsonProperty("address_components")
        private AddressComponents addressComponents;
    }

    /**
     * Address components for location context.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressComponents {
        
        private String city;
        
        @Pattern(regexp = "^[A-Z]{2}$", message = "State must be a valid 2-letter state code")
        private String state;
        
        @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "ZIP code must be in format 12345 or 12345-6789")
        @JsonProperty("zip_code")
        private String zipCode;
        
        @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a valid 2-letter country code")
        private String country;
    }

    /**
     * Required features that locations must have.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequiredFeatures {
        
        /**
         * Must accept cash deposits.
         */
        @JsonProperty("accepts_cash_deposits")
        @Builder.Default
        private Boolean acceptsCashDeposits = true;
        
        /**
         * Must support QR code scanning.
         */
        @JsonProperty("qr_code_scanning")
        private Boolean qrCodeScanning;
        
        /**
         * Must support barcode scanning.
         */
        @JsonProperty("barcode_scanning")
        private Boolean barcodeScanning;
        
        /**
         * Must provide instant confirmation.
         */
        @JsonProperty("instant_confirmation")
        private Boolean instantConfirmation;
        
        /**
         * Must be currently open.
         */
        @JsonProperty("currently_open")
        private Boolean currentlyOpen;
        
        /**
         * Must support specific languages.
         */
        @JsonProperty("supported_languages")
        private java.util.List<String> supportedLanguages;
        
        /**
         * Must accept specific currencies.
         */
        @JsonProperty("accepted_currencies")
        private java.util.List<String> acceptedCurrencies;
    }

    /**
     * Additional search filters.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchFilters {
        
        /**
         * Include only locations with specific status.
         */
        @JsonProperty("location_status")
        private java.util.List<String> locationStatus;
        
        /**
         * Search by location name or address.
         */
        @JsonProperty("text_search")
        private String textSearch;
        
        /**
         * Include locations open during specific hours.
         */
        @JsonProperty("open_during")
        private TimeRange openDuring;
        
        /**
         * Include locations with ratings above threshold.
         */
        @JsonProperty("min_rating")
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "5.0")
        private BigDecimal minRating;
        
        /**
         * Include only partner locations.
         */
        @JsonProperty("partner_locations_only")
        private Boolean partnerLocationsOnly;
    }

    /**
     * Time range filter.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeRange {
        
        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Start time must be in HH:mm format")
        @JsonProperty("start_time")
        private String startTime;
        
        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "End time must be in HH:mm format")
        @JsonProperty("end_time")
        private String endTime;
        
        @JsonProperty("days_of_week")
        private java.util.List<String> daysOfWeek;
    }

    /**
     * Pagination settings for search results.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pagination {
        
        @Min(value = 1, message = "Page number must be at least 1")
        @Builder.Default
        private Integer page = 1;
        
        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size cannot exceed 100")
        @JsonProperty("page_size")
        @Builder.Default
        private Integer pageSize = 20;
        
        /**
         * Whether to include total count in response.
         */
        @JsonProperty("include_total_count")
        @Builder.Default
        private Boolean includeTotalCount = false;
    }

    /**
     * Sorting options for search results.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SortOptions {
        
        /**
         * Primary sort field.
         */
        @JsonProperty("sort_by")
        @Builder.Default
        private SortField sortBy = SortField.DISTANCE;
        
        /**
         * Sort direction.
         */
        @JsonProperty("sort_direction")
        @Builder.Default
        private SortDirection sortDirection = SortDirection.ASC;
        
        /**
         * Secondary sort field.
         */
        @JsonProperty("secondary_sort")
        private SortField secondarySort;
    }

    /**
     * Available sort fields.
     */
    public enum SortField {
        DISTANCE("distance"),
        RATING("rating"),
        NAME("name"),
        NETWORK("network"),
        STATUS("status");

        private final String value;

        SortField(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Sort directions.
     */
    public enum SortDirection {
        ASC("asc"),
        DESC("desc");

        private final String value;

        SortDirection(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Validates the search request for logical consistency.
     *
     * @return true if valid
     * @throws IllegalArgumentException if validation fails
     */
    public boolean validateRequest() {
        if (userLocation == null) {
            throw new IllegalArgumentException("User location is required for location search");
        }
        
        if (maxDistanceMiles != null && maxDistanceMiles.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Maximum distance must be positive");
        }
        
        if (minDepositAmount != null && maxDepositAmount != null) {
            if (minDepositAmount.compareTo(maxDepositAmount) > 0) {
                throw new IllegalArgumentException("Minimum deposit amount cannot exceed maximum deposit amount");
            }
        }
        
        return true;
    }

    /**
     * Gets the effective page size with bounds checking.
     *
     * @return effective page size
     */
    public int getEffectivePageSize() {
        if (pagination == null || pagination.pageSize == null) {
            return 20;
        }
        return Math.max(1, Math.min(100, pagination.pageSize));
    }

    /**
     * Gets the effective page number with bounds checking.
     *
     * @return effective page number
     */
    public int getEffectivePage() {
        if (pagination == null || pagination.page == null) {
            return 1;
        }
        return Math.max(1, pagination.page);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocationRequest that = (LocationRequest) o;
        return Objects.equals(userLocation, that.userLocation) &&
               Objects.equals(maxDistanceMiles, that.maxDistanceMiles) &&
               Objects.equals(preferredNetworks, that.preferredNetworks) &&
               Objects.equals(requiredFeatures, that.requiredFeatures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userLocation, maxDistanceMiles, preferredNetworks, requiredFeatures);
    }

    @Override
    public String toString() {
        return "LocationRequest{" +
               "userLocation=" + userLocation +
               ", maxDistanceMiles=" + maxDistanceMiles +
               ", preferredNetworks=" + preferredNetworks +
               ", pageSize=" + (pagination != null ? pagination.pageSize : null) +
               '}';
    }
}