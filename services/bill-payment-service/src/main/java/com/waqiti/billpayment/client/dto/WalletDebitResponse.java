package com.waqiti.billpayment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response from wallet debit operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDebitResponse {
    private UUID transactionId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String status;
    private LocalDateTime timestamp;
}
