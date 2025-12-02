package com.waqiti.payment.dto;

import com.waqiti.payment.entity.CheckDepositStatus;
import com.waqiti.payment.entity.CheckHoldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for check deposit status inquiries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckDepositStatusResponse {
    
    private UUID depositId;
    private CheckDepositStatus status;
    private BigDecimal amount;
    
    // Current status details
    private String statusDescription;
    private LocalDateTime lastUpdated;
    private String currentStep;
    private Integer progressPercentage;
    
    // Hold and availability
    private CheckHoldType holdType;
    private LocalDate fundsAvailableDate;
    private BigDecimal availableAmount;
    private BigDecimal pendingAmount;
    
    // Processing timeline
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime depositedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime returnedAt;
    
    // Additional information
    private String rejectionReason;
    private String returnReason;
    private Map<String, String> metadata;
}