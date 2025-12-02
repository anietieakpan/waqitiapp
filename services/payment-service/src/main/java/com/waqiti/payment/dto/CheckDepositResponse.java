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
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for check deposit operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckDepositResponse {
    
    private UUID depositId;
    private UUID userId;
    private UUID walletId;
    private BigDecimal amount;
    private CheckDepositStatus status;
    private String message;
    
    // Hold information
    private CheckHoldType holdType;
    private LocalDate holdReleaseDate;
    private LocalDate fundsAvailableDate;
    private BigDecimal immediatelyAvailableAmount;
    private BigDecimal heldAmount;
    
    // Check details
    private String checkNumber;
    private String payorName;
    private String payeeName;
    private LocalDate checkDate;
    
    // Risk assessment
    private BigDecimal riskScore;
    private boolean manualReviewRequired;
    private List<String> verificationSteps;
    
    // Processing information
    private String externalReferenceId;
    private LocalDateTime submittedAt;
    private LocalDateTime estimatedCompletionTime;
    
    // Error information (if applicable)
    private String errorCode;
    private String errorMessage;
    private List<String> validationErrors;
}