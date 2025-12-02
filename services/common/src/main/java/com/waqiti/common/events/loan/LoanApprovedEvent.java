package com.waqiti.common.events.loan;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApprovedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Event ID is required")
    @JsonProperty("event_id")
    private String eventId;

    @NotBlank(message = "Correlation ID is required")
    @JsonProperty("correlation_id")
    private String correlationId;

    @NotNull(message = "Timestamp is required")
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @JsonProperty("event_version")
    private String eventVersion = "1.0";

    @NotBlank(message = "Event source is required")
    @JsonProperty("source")
    private String source;

    @NotBlank(message = "Loan ID is required")
    @JsonProperty("loan_id")
    private String loanId;

    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "Loan type is required")
    @JsonProperty("loan_type")
    private String loanType;

    @NotNull(message = "Loan amount is required")
    @JsonProperty("loan_amount")
    private BigDecimal loanAmount;

    @NotBlank(message = "Currency is required")
    @JsonProperty("currency")
    private String currency;

    @NotNull(message = "Interest rate is required")
    @JsonProperty("interest_rate")
    private BigDecimal interestRate;

    @NotNull(message = "Term in months is required")
    @JsonProperty("term_months")
    private Integer termMonths;

    @NotBlank(message = "Approved by is required")
    @JsonProperty("approved_by")
    private String approvedBy;

    @NotNull(message = "Approved at timestamp is required")
    @JsonProperty("approved_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime approvedAt;

    @JsonProperty("first_payment_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate firstPaymentDate;

    @JsonProperty("monthly_payment_amount")
    private BigDecimal monthlyPaymentAmount;

    @JsonProperty("total_interest")
    private BigDecimal totalInterest;

    @JsonProperty("total_repayment_amount")
    private BigDecimal totalRepaymentAmount;
}