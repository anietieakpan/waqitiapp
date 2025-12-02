package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.CardType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for creating a virtual card
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVirtualCardRequest {
    
    @NotNull(message = "User ID is required")
    @NotBlank(message = "User ID cannot be blank")
    private String userId;
    
    @NotNull(message = "Card type is required")
    private CardType cardType;
    
    @Size(max = 200, message = "Card purpose cannot exceed 200 characters")
    private String cardPurpose;
    
    @NotNull(message = "Cardholder name is required")
    @NotBlank(message = "Cardholder name cannot be blank")
    @Size(max = 100, message = "Cardholder name cannot exceed 100 characters")
    private String cardholderName;
    
    @Size(max = 1000, message = "Billing address cannot exceed 1000 characters")
    private String billingAddress;
    
    @Valid
    private SpendingLimits spendingLimits;
    
    @Valid
    private CardControls cardControls;
    
    @Valid
    private MerchantRestrictions merchantRestrictions;
    
    @Min(value = 1, message = "Validity months must be at least 1")
    @Max(value = 60, message = "Validity months cannot exceed 60")
    private Integer validityMonths;
    
    private boolean isPinEnabled = false;
    
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Card color must be a valid hex color")
    private String cardColor;
    
    @Size(max = 50, message = "Card design cannot exceed 50 characters")
    private String cardDesign;
    
    @Size(max = 50, message = "Nickname cannot exceed 50 characters")
    private String nickname;
    
    private boolean showCvv = false;
    
    private Map<String, String> metadata;
}