package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.CardStatus;
import com.waqiti.virtualcard.domain.CardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Summary DTO for virtual card listings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualCardSummary {
    
    private String cardId;
    
    private String maskedCardNumber;
    
    private CardType cardType;
    
    private CardStatus status;
    
    private String nickname;
    
    private int expiryMonth;
    
    private int expiryYear;
    
    private String cardColor;
    
    private String cardDesign;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime lastUsedAt;
    
    private Long usageCount;
    
    private BigDecimal totalSpent;
    
    private boolean isLocked;
    
    private boolean isExpired;
    
    private BigDecimal dailyLimit;
    
    private BigDecimal monthlyLimit;
    
    private BigDecimal availableBalance;
    
    private int daysUntilExpiry;
    
    /**
     * Check if card is active and usable
     */
    public boolean isActive() {
        return status == CardStatus.ACTIVE && !isLocked && !isExpired;
    }
    
    /**
     * Get card display name
     */
    public String getDisplayName() {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname;
        }
        return cardType.name().replace("_", " ") + " Card";
    }
}