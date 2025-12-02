package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceUpdateEvent {

    private String eventId;
    private String accountId;
    private String userId;
    private String transactionId;
    private String originalTransactionId;
    private UpdateType updateType;
    private BigDecimal amount;
    private BigDecimal previousBalance;
    private BigDecimal newBalance;
    private BigDecimal availableBalance;
    private String description;
    private String adjustmentReason;
    private String holdReason;
    private Instant holdExpiryTime;
    private String originalHoldId;
    private String reversalReason;
    private BigDecimal interestRate;
    private String interestPeriod;
    private String feeType;
    private Instant timestamp;
    private String correlationId;

    public enum UpdateType {
        CREDIT,
        DEBIT,
        ADJUSTMENT,
        HOLD,
        RELEASE_HOLD,
        REVERSAL,
        INTEREST_ACCRUAL,
        FEE_DEDUCTION
    }
}
