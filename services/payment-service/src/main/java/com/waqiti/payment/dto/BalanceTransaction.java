package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO representing a balance transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceTransaction {
    
    private String transactionId;
    private String customerId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String type; // DEBIT, CREDIT
    private String status;
    private String reference;
    private String description;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Instant createdAt;
}