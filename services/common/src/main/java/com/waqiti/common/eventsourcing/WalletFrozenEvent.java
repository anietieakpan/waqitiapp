package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Event fired when a wallet is frozen
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class WalletFrozenEvent extends FinancialEvent {
    
    private String walletId;
    private String freezeReason;
    private String frozenBy;
    private boolean temporaryFreeze;
    
    @Override
    public String getEventType() {
        return "WALLET_FROZEN";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "walletId", walletId,
            "freezeReason", freezeReason,
            "frozenBy", frozenBy,
            "temporaryFreeze", temporaryFreeze
        );
    }
}