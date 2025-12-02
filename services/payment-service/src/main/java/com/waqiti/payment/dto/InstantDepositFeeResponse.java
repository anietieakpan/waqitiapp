package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for instant deposit fee calculations
 * Contains fee breakdown and eligibility information for instant deposits
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstantDepositFeeResponse {
    
    private UUID achTransferId;
    
    private BigDecimal originalAmount;
    
    private BigDecimal feeAmount;
    
    private BigDecimal feePercentage;
    
    private BigDecimal netAmount;
    
    private LocalDateTime estimatedCompletionTime;
    
    private boolean eligible;
}