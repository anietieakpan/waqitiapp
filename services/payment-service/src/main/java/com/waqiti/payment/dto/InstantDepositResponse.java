package com.waqiti.payment.dto;

import com.waqiti.payment.entity.InstantDepositStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for instant deposit operations
 * Contains the result and details of an instant deposit transaction
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstantDepositResponse {
    
    private UUID instantDepositId;
    
    private UUID achTransferId;
    
    private InstantDepositStatus status;
    
    private BigDecimal originalAmount;
    
    private BigDecimal feeAmount;
    
    private BigDecimal netAmount;
    
    private LocalDateTime completedAt;
    
    private String message;
}