package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ATM Transaction Event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtmTransactionEvent {
    private String transactionId;
    private String customerId;
    private String accountId;
    private String atmId;
    private String transactionType;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime transactionTime;
    private String cardNumber;
    private Boolean isPinVerified;
    private Boolean isReceiptRequested;
    private String eventId;
    private String atmLocation;
}
