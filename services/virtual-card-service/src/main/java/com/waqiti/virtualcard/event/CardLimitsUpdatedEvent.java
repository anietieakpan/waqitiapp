package com.waqiti.virtualcard.event;

import com.waqiti.virtualcard.dto.SpendingLimits;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when card limits are updated
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardLimitsUpdatedEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String cardId;
    private String userId;
    private SpendingLimits previousLimits;
    private SpendingLimits newLimits;
    private String updatedBy;
    private String updateReason;
    private LocalDateTime timestamp;
    private String eventId;
    private String eventType = "CARD_LIMITS_UPDATED";
    private String source = "virtual-card-service";
    private String correlationId;
    private Integer version = 1;
    
    public CardLimitsUpdatedEvent(String cardId, String userId, SpendingLimits newLimits, LocalDateTime timestamp) {
        this.cardId = cardId;
        this.userId = userId;
        this.newLimits = newLimits;
        this.timestamp = timestamp;
        this.eventId = UUID.randomUUID().toString();
        this.correlationId = UUID.randomUUID().toString();
    }
    
    /**
     * Create event with full context
     */
    public static CardLimitsUpdatedEvent create(String cardId, String userId, 
                                                SpendingLimits previousLimits, 
                                                SpendingLimits newLimits,
                                                String updatedBy,
                                                String updateReason) {
        return CardLimitsUpdatedEvent.builder()
            .cardId(cardId)
            .userId(userId)
            .previousLimits(previousLimits)
            .newLimits(newLimits)
            .updatedBy(updatedBy)
            .updateReason(updateReason)
            .timestamp(LocalDateTime.now())
            .eventId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }
    
    /**
     * Check if limits were increased
     */
    public boolean isLimitIncrease() {
        if (previousLimits == null || newLimits == null) {
            return false;
        }
        
        return (newLimits.getDailyLimit() != null && previousLimits.getDailyLimit() != null &&
                newLimits.getDailyLimit().compareTo(previousLimits.getDailyLimit()) > 0) ||
               (newLimits.getMonthlyLimit() != null && previousLimits.getMonthlyLimit() != null &&
                newLimits.getMonthlyLimit().compareTo(previousLimits.getMonthlyLimit()) > 0);
    }
    
    /**
     * Check if limits were decreased
     */
    public boolean isLimitDecrease() {
        if (previousLimits == null || newLimits == null) {
            return false;
        }
        
        return (newLimits.getDailyLimit() != null && previousLimits.getDailyLimit() != null &&
                newLimits.getDailyLimit().compareTo(previousLimits.getDailyLimit()) < 0) ||
               (newLimits.getMonthlyLimit() != null && previousLimits.getMonthlyLimit() != null &&
                newLimits.getMonthlyLimit().compareTo(previousLimits.getMonthlyLimit()) < 0);
    }
}