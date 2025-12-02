package com.waqiti.virtualcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO from card provider for order submission
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardProviderOrderResponse {
    
    private boolean success;
    private String providerOrderId;
    private String providerCardId;
    private String status;
    private String message;
    
    private String lastFourDigits;
    private Integer expiryMonth;
    private Integer expiryYear;
    
    private Integer estimatedProductionDays;
    private String trackingUrl;
    
    // Error details
    private String errorCode;
    private String errorMessage;
    private String errorDetails;
}