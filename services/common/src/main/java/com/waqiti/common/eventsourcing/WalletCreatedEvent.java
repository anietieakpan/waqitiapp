package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Event fired when a wallet is created
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class WalletCreatedEvent extends FinancialEvent {
    
    private String walletId;
    private String walletType;
    private String currency;
    private BigDecimal initialBalance;
    private String ownerId;
    
    @Override
    public String getEventType() {
        return "WALLET_CREATED";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "walletId", walletId,
            "walletType", walletType,
            "currency", currency,
            "initialBalance", initialBalance,
            "ownerId", ownerId
        );
    }
}