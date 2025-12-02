/**
 * File: src/main/java/com/waqiti/notification/event/WalletTransactionEvent.java
 */
package com.waqiti.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event for wallet transactions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class WalletTransactionEvent extends NotificationEvent {
    private UUID userId;
    private UUID walletId;
    private String transactionType; // DEPOSIT, WITHDRAWAL, TRANSFER
    private BigDecimal amount;
    private String currency;
    private UUID sourceWalletId;
    private UUID targetWalletId;
    private UUID counterpartyUserId;
    private String counterpartyName;
    private BigDecimal newBalance;
    private UUID transactionId;

    // Use setter method instead of direct field access
    public void initializeEventType() {
        this.setEventType("WALLET_TRANSACTION");
    }
}