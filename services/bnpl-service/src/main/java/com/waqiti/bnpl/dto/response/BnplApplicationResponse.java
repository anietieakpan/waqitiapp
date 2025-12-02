/**
 * BNPL Application Response DTO
 */
package com.waqiti.bnpl.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnplApplicationResponse {
    
    private UUID applicationId;
    private String applicationNumber;
    private String status;
    private String decision;
    private String decisionReason;
    
    // Financial details
    private BigDecimal purchaseAmount;
    private BigDecimal downPayment;
    private BigDecimal financedAmount;
    private Integer installmentCount;
    private BigDecimal installmentAmount;
    private BigDecimal interestRate;
    private BigDecimal totalAmount;
    
    // Dates
    private LocalDateTime applicationDate;
    private LocalDateTime approvalDate;
    private LocalDate firstPaymentDate;
    private LocalDate finalPaymentDate;
    
    // Merchant info
    private UUID merchantId;
    private String merchantName;
    private String orderId;
    
    // Risk assessment
    private String riskTier;
    private Integer creditScore;
}