package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Event fired when a transfer fails
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TransferFailedEvent extends FinancialEvent {
    
    private String transferId;
    private String failureReason;
    private String errorCode;
    private boolean retryable;
    
    @Override
    public String getEventType() {
        return "TRANSFER_FAILED";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "transferId", transferId,
            "failureReason", failureReason,
            "errorCode", errorCode != null ? errorCode : "",
            "retryable", retryable
        );
    }
}