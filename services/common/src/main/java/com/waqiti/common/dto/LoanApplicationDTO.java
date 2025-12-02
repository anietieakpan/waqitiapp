/**
 * CRITICAL SECURITY FIX - LoanApplicationDTO
 * Secure Data Transfer Object for Loan Applications
 * Prevents direct exposure of sensitive database entity fields
 */
package com.waqiti.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanApplicationDTO extends BaseDTO {
    
    private String loanNumber;
    private String loanType;
    private String status;
    
    // Financial amounts - safely exposed
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private BigDecimal disbursedAmount;
    private BigDecimal outstandingBalance;
    private String currency;
    
    // Interest and terms
    private BigDecimal interestRate;
    private String interestType;
    private Integer loanTermMonths;
    private String repaymentFrequency;
    private BigDecimal monthlyPayment;
    private BigDecimal totalInterest;
    private BigDecimal totalRepayment;
    
    // Application dates
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime applicationDate;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime approvalDate;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime disbursementDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate firstPaymentDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate maturityDate;
    
    // SENSITIVE DATA - MASKED OR FILTERED OUT
    // These fields are intentionally NOT included to protect sensitive data:
    // - creditScore (highly sensitive)
    // - debtToIncomeRatio (personal financial data)
    // - annualIncome (personal financial data)
    // - employmentStatus (personal data)
    // - employmentDurationMonths (personal data)
    
    // Business fields
    private String purpose;
    private String riskGrade;
    private String decision;
    private String decisionReason;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime decisionDate;
    
    // Administrative fields
    private UUID loanOfficerId;
    private UUID branchId;
    private UUID productId;
    
    // Masked sensitive fields for authorized users only
    private String maskedCreditScore;
    private String maskedAnnualIncome;
    private String maskedDebtRatio;
    
    // Helper methods for security context
    public boolean isSecured() {
        // Cannot expose collateral details in API
        return "SECURED".equals(riskGrade);
    }
    
    public String getDisplayAmount() {
        if (requestedAmount == null) return null;
        return getSafeAmount(requestedAmount).toString();
    }
    
    public int getDaysToMaturity() {
        if (maturityDate == null) return 0;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), maturityDate);
    }
    
    // Status helper methods
    public boolean isPending() {
        return "PENDING".equals(status);
    }
    
    public boolean isApproved() {
        return "APPROVED".equals(status);
    }
    
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
    
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
    
    public boolean isDefaulted() {
        return "DEFAULTED".equals(status);
    }
    
    /**
     * Get safe credit score display for authorized users
     */
    public String getSafeCreditScore(boolean isAuthorized) {
        if (!isAuthorized) {
            return "***";
        }
        return maskedCreditScore;
    }
    
    /**
     * Get safe income display for authorized users
     */
    public String getSafeIncome(boolean isAuthorized) {
        if (!isAuthorized) {
            return "***.**";
        }
        return maskedAnnualIncome;
    }
}