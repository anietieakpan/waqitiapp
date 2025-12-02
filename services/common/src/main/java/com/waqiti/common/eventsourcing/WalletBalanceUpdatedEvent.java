package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Event fired when wallet balance is updated
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class WalletBalanceUpdatedEvent extends FinancialEvent {
    
    private String walletId;
    private BigDecimal previousBalance;
    private BigDecimal newBalance;
    private BigDecimal changeAmount;
    private String changeReason;
    private String referenceId;
    
    @Override
    public String getEventType() {
        return "WALLET_BALANCE_UPDATED";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "walletId", walletId,
            "previousBalance", previousBalance,
            "newBalance", newBalance,
            "changeAmount", changeAmount,
            "changeReason", changeReason,
            "referenceId", referenceId != null ? referenceId : ""
        );
    }
}