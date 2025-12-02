package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.CardNetwork;
import com.waqiti.virtualcard.domain.CardStatus;
import com.waqiti.virtualcard.domain.CardType;
import com.waqiti.virtualcard.domain.CardTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detailed DTO for virtual card information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualCardDetails {
    
    private String cardId;
    
    private String cardNumber;
    
    private String fullCardNumber;
    
    private String cvv;
    
    private int expiryMonth;
    
    private int expiryYear;
    
    private String cardholderName;
    
    private CardType cardType;
    
    private String cardPurpose;
    
    private CardStatus status;
    
    private boolean isLocked;
    
    private LocalDateTime lockedAt;
    
    private String lockReason;
    
    private CardNetwork cardNetwork;
    
    private String nickname;
    
    private String cardColor;
    
    private String cardDesign;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime activatedAt;
    
    private LocalDateTime lastUsedAt;
    
    private LocalDateTime terminatedAt;
    
    private String terminationReason;
    
    private Long usageCount;
    
    private BigDecimal totalSpent;
    
    private SpendingLimits limits;
    
    private CardControls controls;
    
    private MerchantRestrictions merchantRestrictions;
    
    private List<CardTransaction> recentTransactions;
    
    private boolean is3dsEnabled;
    
    private boolean isPinEnabled;
    
    private LocalDateTime cvvRotatedAt;
    
    private String billingAddress;
}