package com.waqiti.payment.dto;

import com.waqiti.payment.domain.PaymentLink.PaymentLinkType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request DTO for creating payment links
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentLinkRequest {
    
    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    private String title;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @Digits(integer = 17, fraction = 2, message = "Invalid amount format")
    private BigDecimal amount; // null for flexible amount
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter code")
    @Builder.Default
    private String currency = "USD";
    
    @DecimalMin(value = "0.01", message = "Minimum amount must be positive")
    @Digits(integer = 17, fraction = 2, message = "Invalid minimum amount format")
    private BigDecimal minAmount;
    
    @DecimalMin(value = "0.01", message = "Maximum amount must be positive")
    @Digits(integer = 17, fraction = 2, message = "Invalid maximum amount format")
    private BigDecimal maxAmount;
    
    @NotNull(message = "Link type is required")
    private PaymentLinkType linkType;
    
    @Future(message = "Expiration date must be in the future")
    private LocalDateTime expiresAt;
    
    @Min(value = 1, message = "Max uses must be at least 1")
    @Max(value = 10000, message = "Max uses cannot exceed 10,000")
    private Integer maxUses;
    
    @Builder.Default
    private Boolean requiresNote = false;
    
    @Size(max = 1000, message = "Custom message cannot exceed 1000 characters")
    private String customMessage;
    
    private Map<String, String> metadata;
    
    // Custom link ID (optional, will be generated if not provided)
    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,20}$", 
             message = "Custom link ID must be 3-20 characters (alphanumeric, underscore, hyphen only)")
    private String customLinkId;
    
    // Validation methods
    public boolean hasValidAmountRange() {
        if (minAmount != null && maxAmount != null) {
            return minAmount.compareTo(maxAmount) <= 0;
        }
        return true;
    }
    
    public boolean hasValidFixedAmount() {
        if (amount != null) {
            return minAmount == null && maxAmount == null;
        }
        return true;
    }
    
    @AssertTrue(message = "Amount range is invalid: minimum cannot be greater than maximum")
    private boolean isValidAmountRange() {
        return hasValidAmountRange();
    }
    
    @AssertTrue(message = "Cannot specify both fixed amount and amount range")
    private boolean isValidAmountConfiguration() {
        return hasValidFixedAmount();
    }
    
    @AssertTrue(message = "Flexible amount links must have minimum amount specified")
    private boolean isValidFlexibleAmount() {
        if (amount == null && linkType != PaymentLinkType.DONATION) {
            return minAmount != null;
        }
        return true;
    }
}