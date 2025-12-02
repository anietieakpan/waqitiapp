package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Result DTO for balance transfer operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceTransferResult {
    
    private boolean success;
    private String transferId;
    private String fromCustomerId;
    private String toCustomerId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private Instant completedAt;
    private String errorMessage;
    private String reference;
}