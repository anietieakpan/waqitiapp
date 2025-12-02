package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Balance Reconciliation Event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceReconciliationEvent {

    private UUID reconciliationId;
    private UUID walletId;
    private BigDecimal expectedBalance;
    private String currency;
    private LocalDateTime asOfTime;
    private LocalDateTime timestamp;
    private String correlationId;
    private String initiatedBy;

    public String name() {
        return "BalanceReconciliationEvent";
    }
}
