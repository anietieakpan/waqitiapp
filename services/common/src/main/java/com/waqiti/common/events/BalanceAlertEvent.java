package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Balance Alert Event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceAlertEvent {

    private String eventId;
    private String accountId;
    private String userId;
    private AlertType alertType;
    private Double currentBalance;
    private Double threshold;
    private String severity;
    private LocalDateTime timestamp;
    private String correlationId;
    private Double requestedAmount;
    private Double expectedBalance;
    private Double previousBalance;
    private String freezeReason;

    public enum AlertType {
        LOW_BALANCE_WARNING,
        LOW_BALANCE_CRITICAL,
        NEGATIVE_BALANCE,
        BALANCE_THRESHOLD_EXCEEDED,
        INSUFFICIENT_FUNDS,
        BALANCE_RECONCILIATION_MISMATCH,
        UNUSUAL_BALANCE_CHANGE,
        BALANCE_FREEZE_REQUIRED,
        BALANCE_RESTORED
    }
}
