package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Event fired when a transfer is initiated
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TransferInitiatedEvent extends FinancialEvent {
    
    private String transferId;
    private String fromWalletId;
    private String toWalletId;
    private BigDecimal amount;
    private String currency;
    private String transferType;
    private String reason;
    
    @Override
    public String getEventType() {
        return "TRANSFER_INITIATED";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "transferId", transferId,
            "fromWalletId", fromWalletId,
            "toWalletId", toWalletId,
            "amount", amount,
            "currency", currency,
            "transferType", transferType,
            "reason", reason != null ? reason : ""
        );
    }
}