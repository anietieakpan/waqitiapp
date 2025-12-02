package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.CardDesign;
import com.waqiti.virtualcard.domain.CardPersonalization;
import com.waqiti.virtualcard.domain.ShippingAddress;
import com.waqiti.virtualcard.domain.enums.CardBrand;
import com.waqiti.virtualcard.domain.enums.ShippingMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for ordering a physical card
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCardRequest {
    
    @NotNull(message = "Shipping address is required")
    @Valid
    private ShippingAddress shippingAddress;
    
    @NotNull(message = "Shipping method is required")
    private ShippingMethod shippingMethod;
    
    private CardBrand brand;
    
    private CardDesign design;
    
    @Valid
    private CardPersonalization personalization;
    
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter code")
    @Builder.Default
    private String currency = "USD";
    
    @Builder.Default
    private boolean rushDelivery = false;
    
    @Builder.Default
    private boolean giftCard = false;
    
    private String specialInstructions;
    
    private String referenceNumber;
    
    /**
     * Validates the order request
     */
    public boolean isValid() {
        if (shippingAddress == null || !shippingAddress.isValid()) {
            return false;
        }
        
        if (shippingMethod == null) {
            return false;
        }
        
        if (personalization != null && !personalization.isValid()) {
            return false;
        }
        
        if (currency == null || !currency.matches("^[A-Z]{3}$")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Returns estimated delivery time in business days based on shipping method
     */
    public int getEstimatedBusinessDays() {
        if (rushDelivery) {
            return Math.max(1, shippingMethod.getEstimatedBusinessDays() - 2);
        }
        return shippingMethod.getEstimatedBusinessDays();
    }
    
    /**
     * Checks if international shipping is required
     */
    public boolean isInternationalShipping() {
        return shippingAddress != null && !"US".equals(shippingAddress.getCountry());
    }
    
    /**
     * Gets the effective card brand (default to VISA if not specified)
     */
    public CardBrand getEffectiveBrand() {
        return brand != null ? brand : CardBrand.VISA;
    }
}