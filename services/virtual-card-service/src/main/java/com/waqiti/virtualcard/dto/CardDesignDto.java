package com.waqiti.virtualcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for card design information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardDesignDto {
    
    private String id;
    private String designCode;
    private String name;
    private String description;
    private String category;
    
    private String imageUrl;
    private String thumbnailUrl;
    private String previewUrl;
    
    private boolean isPremium;
    private BigDecimal fee;
    private boolean active;
    private boolean isDefault;
    
    private int sortOrder;
    private boolean available;
    private Integer maxOrders;
    private Integer remainingOrders;
    
    // Computed fields
    private String formattedFee;
    private String availabilityStatus;
    private boolean isLimitedEdition;
    
    public String getFormattedFee() {
        if (fee == null || fee.compareTo(BigDecimal.ZERO) == 0) {
            return "Free";
        }
        return "$" + fee.toString();
    }
    
    public String getAvailabilityStatus() {
        if (!active) {
            return "Inactive";
        }
        
        if (!available) {
            return "Unavailable";
        }
        
        if (maxOrders != null && remainingOrders != null) {
            if (remainingOrders <= 0) {
                return "Sold Out";
            } else if (remainingOrders <= 10) {
                return "Limited Availability";
            }
        }
        
        return "Available";
    }
    
    public boolean isLimitedEdition() {
        return maxOrders != null;
    }
    
    public boolean isFree() {
        return fee == null || fee.compareTo(BigDecimal.ZERO) == 0;
    }
    
    public boolean isAvailableForOrder() {
        return active && available && (maxOrders == null || (remainingOrders != null && remainingOrders > 0));
    }
    
    public Integer getRemainingOrders() {
        if (maxOrders == null) {
            return null;
        }
        
        return remainingOrders;
    }
    
    /**
     * Gets the display priority for sorting
     */
    public int getDisplayPriority() {
        int priority = sortOrder;
        
        // Boost priority for default design
        if (isDefault) {
            priority -= 1000;
        }
        
        // Boost priority for free designs
        if (isFree()) {
            priority -= 100;
        }
        
        return priority;
    }
    
    /**
     * Gets the design type description
     */
    public String getTypeDescription() {
        if (isPremium) {
            return isLimitedEdition() ? "Premium Limited Edition" : "Premium Design";
        } else {
            return isDefault ? "Standard Design" : "Free Design";
        }
    }
}