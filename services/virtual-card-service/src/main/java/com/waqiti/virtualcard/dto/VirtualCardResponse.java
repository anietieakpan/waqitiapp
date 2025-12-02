package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.CardNetwork;
import com.waqiti.virtualcard.domain.CardStatus;
import com.waqiti.virtualcard.domain.CardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for virtual card operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualCardResponse {
    
    private String cardId;
    
    private String cardNumber;
    
    private String cvv;
    
    private int expiryMonth;
    
    private int expiryYear;
    
    private String cardholderName;
    
    private CardType cardType;
    
    private CardStatus status;
    
    private CardNetwork cardNetwork;
    
    private String nickname;
    
    private String cardColor;
    
    private String cardDesign;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime activatedAt;
    
    private SpendingLimits limits;
    
    private CardControls controls;
    
    private String message;
    
    private boolean success;
}