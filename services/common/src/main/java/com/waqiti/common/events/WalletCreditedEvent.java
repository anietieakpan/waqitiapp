package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Event published when funds are credited to a wallet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletCreditedEvent {
    private UUID eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private UUID walletId;
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private UUID transactionId;
    private String description;
    private BigDecimal balanceAfter;
    private Map<String, Object> metadata;
}
