package com.waqiti.atm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction Summary DTO for mini-statements
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummary {

    private String transactionId;
    private LocalDateTime date;
    private String type;
    private String debitCredit; // "DR" or "CR"
    private BigDecimal amount;
    private BigDecimal balance;
    private String description;
}
