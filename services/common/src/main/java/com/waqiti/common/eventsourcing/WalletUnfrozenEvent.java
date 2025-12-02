package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Event fired when a wallet is unfrozen
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class WalletUnfrozenEvent extends FinancialEvent {
    
    private String walletId;
    private String unfreezeReason;
    private String unfrozenBy;
    
    @Override
    public String getEventType() {
        return "WALLET_UNFROZEN";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "walletId", walletId,
            "unfreezeReason", unfreezeReason,
            "unfrozenBy", unfrozenBy
        );
    }
}