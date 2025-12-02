package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.enums.CardStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for card production status from provider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardProductionStatus {
    
    private String providerCardId;
    private CardStatus status;
    private String statusMessage;
    private Instant estimatedShipDate;
    private String trackingNumber;
    private String carrier;
    
    @Builder.Default
    private boolean hasError = false;
    
    private String errorMessage;
    private Instant lastUpdated;
}