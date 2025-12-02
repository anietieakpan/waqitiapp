package com.waqiti.payment.reversal.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment Reversal Result
 */
@Data
@Builder
public class ReversalResult {
    private boolean success;
    private String reversalId;
    private String originalTransactionId;
    private BigDecimal amountReversed;
    private String currency;
    private ReversalStatus status;
    private LocalDateTime reversalDate;
    private String errorCode;
    private String errorMessage;
}
