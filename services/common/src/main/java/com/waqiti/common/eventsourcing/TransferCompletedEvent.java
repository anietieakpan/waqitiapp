package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Event fired when a transfer is completed
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TransferCompletedEvent extends FinancialEvent {
    
    private String transferId;
    private BigDecimal finalAmount;
    private BigDecimal exchangeRate;
    private String confirmationId;
    private Instant completedAt;
    
    @Override
    public String getEventType() {
        return "TRANSFER_COMPLETED";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "transferId", transferId,
            "finalAmount", finalAmount,
            "exchangeRate", exchangeRate != null ? exchangeRate : BigDecimal.ONE,
            "confirmationId", confirmationId,
            "completedAt", completedAt
        );
    }
}