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
 * Event published when funds are transferred between wallets
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransferEvent {
    private UUID eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private UUID fromWalletId;
    private UUID fromUserId;
    private UUID toWalletId;
    private UUID toUserId;
    private BigDecimal amount;
    private String currency;
    private UUID transactionId;
    private String description;
    private BigDecimal fromBalanceAfter;
    private BigDecimal toBalanceAfter;
    private Map<String, Object> metadata;
}
