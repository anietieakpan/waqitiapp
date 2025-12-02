package com.waqiti.lending.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Lending domain event model for loan applications, credit decisions, loan servicing,
 * defaults, collateral valuation, modifications, foreclosure, and student loans
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LendingEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String loanId;
    private String applicationId;
    private String borrowerId;
    private String userId;
    private String lenderId;
    private String loanType;
    private BigDecimal amount;
    private BigDecimal previousAmount;
    private String currency;
    private BigDecimal interestRate;
    private String term;
    private String status;
    private String creditScore;
    private String riskRating;
    private String collateralId;
    private String collateralType;
    private BigDecimal collateralValue;
    private String appraisalId;
    private String decisionReason;
    private String servicingType;
    private BigDecimal paymentAmount;
    private Instant paymentDueDate;
    private Instant nextPaymentDate;
    private String defaultReason;
    private String defaultStage;
    private BigDecimal outstandingBalance;
    private String modificationType;
    private String modificationReason;
    private String foreclosureStage;
    private String studentLoanType;
    private String repaymentPlan;
    private String defermentType;
    private String forbearanceType;
    private Instant maturityDate;
    private Instant disbursementDate;
    private String originationChannel;
    private Instant timestamp;
    private String correlationId;
    private String causationId;
    private String version;
    private String description;
    private Long sequenceNumber;
    private Integer retryCount;
    private Map<String, Object> metadata;
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
    
    /**
     * Check if this is a loan application event
     */
    public boolean isLoanApplicationEvent() {
        return "LOAN_APPLICATION".equals(eventType);
    }
    
    /**
     * Check if this is a default event
     */
    public boolean isDefaultEvent() {
        return "LOAN_DEFAULT".equals(eventType);
    }
    
    /**
     * Check if this is a high-priority event
     */
    public boolean isHighPriorityEvent() {
        return "LOAN_DEFAULT".equals(eventType) || 
               "FORECLOSURE".equals(eventType);
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        if (timestamp == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
}