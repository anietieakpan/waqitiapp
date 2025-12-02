package com.waqiti.grouppayment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.waqiti.grouppayment.domain.SplitMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for split bill calculation
 * Supports all major splitting methods with comprehensive options
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for calculating split bill among participants")
public class SplitBillCalculationRequest {
    
    @NotNull(message = "Organizer ID is required")
    @Schema(description = "ID of the user organizing the split", required = true, example = "usr_123456")
    private String organizerId;
    
    @Size(max = 100, message = "Group name cannot exceed 100 characters")
    @Schema(description = "Name for the group or event", example = "Dinner at Luigi's")
    private String groupName;
    
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @Schema(description = "Description of the bill or expense", example = "Team dinner after project completion")
    private String description;
    
    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Total amount must be greater than 0")
    @DecimalMax(value = "100000.00", message = "Total amount exceeds maximum limit")
    @Digits(integer = 10, fraction = 4, message = "Invalid amount format")
    @Schema(description = "Total bill amount before tax and tip", required = true, example = "150.00")
    private BigDecimal totalAmount;
    
    @NotNull(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
    @Schema(description = "Currency code in ISO 4217 format", required = true, example = "USD")
    private String currency;
    
    @NotNull(message = "Split method is required")
    @Schema(description = "Method to use for splitting the bill", required = true)
    private SplitMethod splitMethod;
    
    @NotNull(message = "Participants list is required")
    @NotEmpty(message = "At least one participant is required")
    @Size(max = 50, message = "Maximum 50 participants allowed")
    @Valid
    @Schema(description = "List of participants in the split", required = true)
    private List<SplitParticipant> participants;
    
    @Valid
    @Schema(description = "List of individual items (required for item-based split)")
    private List<BillItem> items;
    
    @DecimalMin(value = "0.00", message = "Tax amount cannot be negative")
    @Digits(integer = 8, fraction = 4, message = "Invalid tax amount format")
    @Schema(description = "Total tax amount to be split", example = "12.50")
    private BigDecimal taxAmount;
    
    @DecimalMin(value = "0.00", message = "Tip amount cannot be negative")
    @Digits(integer = 8, fraction = 4, message = "Invalid tip amount format")
    @Schema(description = "Total tip amount to be split", example = "30.00")
    private BigDecimal tipAmount;
    
    @DecimalMin(value = "0.00", message = "Discount amount cannot be negative")
    @Digits(integer = 8, fraction = 4, message = "Invalid discount amount format")
    @Schema(description = "Total discount amount to be deducted", example = "15.00")
    private BigDecimal discountAmount;
    
    @DecimalMin(value = "0.0", message = "Tax rate cannot be negative")
    @DecimalMax(value = "100.0", message = "Tax rate cannot exceed 100%")
    @Schema(description = "Tax rate as percentage (alternative to fixed tax amount)", example = "8.5")
    private BigDecimal taxRate;
    
    @DecimalMin(value = "0.0", message = "Tip rate cannot be negative")
    @DecimalMax(value = "100.0", message = "Tip rate cannot exceed 100%")
    @Schema(description = "Tip rate as percentage (alternative to fixed tip amount)", example = "18.0")
    private BigDecimal tipRate;
    
    @Schema(description = "How to handle tax distribution")
    @Builder.Default
    private TaxTipDistribution taxDistribution = TaxTipDistribution.PROPORTIONAL;
    
    @Schema(description = "How to handle tip distribution")
    @Builder.Default
    private TaxTipDistribution tipDistribution = TaxTipDistribution.PROPORTIONAL;
    
    @Schema(description = "Rounding strategy for final amounts")
    @Builder.Default
    private RoundingStrategy roundingStrategy = RoundingStrategy.ROUND_TO_NEAREST_CENT;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Payment deadline for participants")
    private LocalDateTime paymentDeadline;
    
    @Schema(description = "Whether to send reminder notifications", example = "true")
    @Builder.Default
    private Boolean sendReminders = true;
    
    @Min(value = 1, message = "Reminder hours must be at least 1")
    @Max(value = 168, message = "Reminder hours cannot exceed 1 week")
    @Schema(description = "Hours before deadline to send reminder", example = "24")
    @Builder.Default
    private Integer reminderHours = 24;
    
    @Schema(description = "Allow participants to add tips individually", example = "true")
    @Builder.Default
    private Boolean allowIndividualTips = false;
    
    @Schema(description = "Allow participants to add their own items", example = "false")
    @Builder.Default
    private Boolean allowParticipantItems = false;
    
    @Schema(description = "Require all participants to pay exact amounts", example = "true")
    @Builder.Default
    private Boolean enforceExactAmounts = true;
    
    @DecimalMin(value = "0.01", message = "Tolerance must be positive")
    @Schema(description = "Payment tolerance amount for approximate payments", example = "0.50")
    private BigDecimal paymentTolerance;
    
    @Size(max = 50)
    @Schema(description = "Category for expense tracking", example = "dining")
    private String category;
    
    @Size(max = 100)
    @Schema(description = "Merchant or venue name", example = "Luigi's Italian Restaurant")
    private String merchantName;
    
    @Schema(description = "Location information for the expense")
    private LocationInfo location;
    
    @Schema(description = "Receipt or bill images")
    private List<String> receiptImages;
    
    @Schema(description = "Additional metadata for the split")
    private Map<String, String> metadata;
    
    @Schema(description = "Notes or comments about the split")
    private String notes;
    
    @Schema(description = "Tags for categorization and search")
    private List<String> tags;
    
    /**
     * How tax and tip should be distributed
     */
    public enum TaxTipDistribution {
        PROPORTIONAL,   // Based on each person's subtotal
        EQUAL,          // Split equally among all participants
        BY_ITEM,        // Applied only to items consumed by each person
        EXEMPT          // Not applied to specific participants
    }
    
    /**
     * Rounding strategy for final amounts
     */
    public enum RoundingStrategy {
        ROUND_TO_NEAREST_CENT,    // Standard currency rounding
        ROUND_UP,                 // Always round up to next cent
        ROUND_DOWN,               // Always round down
        NO_ROUNDING               // Keep exact calculated amounts
    }
    
    /**
     * Location information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Location information for the expense")
    public static class LocationInfo {
        
        @Schema(description = "Latitude", example = "37.7749")
        private Double latitude;
        
        @Schema(description = "Longitude", example = "-122.4194")
        private Double longitude;
        
        @Schema(description = "Address", example = "123 Main St, San Francisco, CA")
        private String address;
        
        @Schema(description = "City", example = "San Francisco")
        private String city;
        
        @Schema(description = "State or province", example = "CA")
        private String state;
        
        @Schema(description = "Country code", example = "US")
        private String countryCode;
        
        @Schema(description = "Postal code", example = "94102")
        private String postalCode;
    }
}