package com.waqiti.common.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Event published when a loan is disbursed to a borrower
 * 
 * This event triggers:
 * - General ledger entry in accounting system
 * - Cash flow statement updates
 * - Regulatory capital allocation (Basel III)
 * - Credit risk exposure tracking
 * 
 * @author Waqiti Lending Platform
 * @version 1.0
 * @since 2025-09-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDisbursementEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique loan identifier
     */
    @JsonProperty("loan_id")
    private String loanId;
    
    /**
     * Borrower user ID
     */
    @JsonProperty("borrower_id")
    private String borrowerId;
    
    /**
     * Loan disbursement amount (principal)
     */
    @JsonProperty("disbursement_amount")
    private BigDecimal disbursementAmount;
    
    /**
     * Currency code (ISO 4217)
     */
    @JsonProperty("currency")
    private String currency;
    
    /**
     * Date of disbursement
     */
    @JsonProperty("disbursement_date")
    private LocalDate disbursementDate;
    
    /**
     * Loan type: PERSONAL, COMMERCIAL, MORTGAGE, AUTO, STUDENT, etc.
     */
    @JsonProperty("loan_type")
    private String loanType;
    
    /**
     * Annual interest rate (percentage)
     */
    @JsonProperty("interest_rate")
    private BigDecimal interestRate;
    
    /**
     * Loan term in months
     */
    @JsonProperty("term_months")
    private Integer termMonths;
    
    /**
     * Disbursement method: BANK_TRANSFER, CHECK, WIRE, ACH
     */
    @JsonProperty("disbursement_method")
    private String disbursementMethod;
    
    /**
     * Source account number (bank account funds come from)
     */
    @JsonProperty("source_account_number")
    private String sourceAccountNumber;
    
    /**
     * Destination account number (borrower's account)
     */
    @JsonProperty("destination_account_number")
    private String destinationAccountNumber;
    
    /**
     * Loan officer/approver ID
     */
    @JsonProperty("approved_by")
    private String approvedBy;
    
    /**
     * Loan approval timestamp
     */
    @JsonProperty("approved_at")
    private LocalDateTime approvedAt;
    
    /**
     * Origination fee (if any)
     */
    @JsonProperty("origination_fee")
    private BigDecimal originationFee;
    
    /**
     * Collateral description (if secured loan)
     */
    @JsonProperty("collateral")
    private String collateral;
    
    /**
     * Credit score of borrower at disbursement
     */
    @JsonProperty("credit_score")
    private Integer creditScore;
    
    /**
     * Debt-to-income ratio
     */
    @JsonProperty("debt_to_income_ratio")
    private BigDecimal debtToIncomeRatio;
    
    /**
     * Loan purpose/use of funds
     */
    @JsonProperty("loan_purpose")
    private String loanPurpose;
}