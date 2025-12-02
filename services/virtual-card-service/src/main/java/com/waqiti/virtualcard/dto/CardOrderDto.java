package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.CardDesign;
import com.waqiti.virtualcard.domain.enums.CardBrand;
import com.waqiti.virtualcard.domain.enums.CardType;
import com.waqiti.virtualcard.domain.enums.OrderStatus;
import com.waqiti.virtualcard.domain.enums.ShippingMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for card order information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardOrderDto {
    
    private String id;
    private String cardId;
    private CardType type;
    private CardBrand brand;
    private CardDesign design;
    private OrderStatus status;
    private ShippingMethod shippingMethod;
    
    private BigDecimal orderFee;
    private BigDecimal shippingFee;
    private BigDecimal totalFee;
    
    private Instant orderedAt;
    private Instant estimatedDelivery;
    private Instant completedAt;
    
    private boolean isReplacement;
    private String notes;
    
    // Computed fields
    private String statusDescription;
    private int estimatedDaysRemaining;
    private boolean canCancel;
    private boolean isOverdue;
    
    public String getStatusDescription() {
        return status != null ? status.getDescription() : null;
    }
    
    public int getEstimatedDaysRemaining() {
        if (estimatedDelivery == null) {
            return -1;
        }
        
        long daysRemaining = java.time.Duration.between(Instant.now(), estimatedDelivery).toDays();
        return Math.max(0, (int) daysRemaining);
    }
    
    public boolean canCancel() {
        return status != null && status.isCancellable();
    }
    
    public boolean isOverdue() {
        return estimatedDelivery != null && 
               Instant.now().isAfter(estimatedDelivery) && 
               status != null && status.isActive();
    }
    
    public boolean isCompleted() {
        return status != null && status.isCompleted();
    }
    
    public boolean isInProgress() {
        return status != null && status.isInProgress();
    }
}