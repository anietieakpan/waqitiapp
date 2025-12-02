package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Account-related event for event sourcing
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AccountEvent extends FinancialEvent {

    private UUID accountId;
    private String accountType;
    private String accountStatus;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private String ipAddress;
    private String userAgent;

    /**
     * Account event types
     */
    public enum EventType {
        ACCOUNT_CREATED,
        ACCOUNT_ACTIVATED,
        ACCOUNT_SUSPENDED,
        ACCOUNT_CLOSED,
        ACCOUNT_UPDATED,
        BALANCE_UPDATED,
        LIMIT_CHANGED,
        STATUS_CHANGED,
        ACCOUNT_VERIFIED,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED
    }
    
    /**
     * Create account created event
     */
    public static AccountEvent accountCreated(UUID accountId, UUID userId, String accountType, String currency) {
        return AccountEvent.builder()
            .eventId(UUID.randomUUID())
            .accountId(accountId)
            .userId(userId)
            .eventType(EventType.ACCOUNT_CREATED.name())
            .accountType(accountType)
            .accountStatus("PENDING")
            .currency(currency)
            .balance(BigDecimal.ZERO)
            .availableBalance(BigDecimal.ZERO)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create account activated event
     */
    public static AccountEvent accountActivated(UUID accountId, UUID userId) {
        return AccountEvent.builder()
            .eventId(UUID.randomUUID())
            .accountId(accountId)
            .userId(userId)
            .eventType(EventType.ACCOUNT_ACTIVATED.name())
            .accountStatus("ACTIVE")
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create account suspended event
     */
    public static AccountEvent accountSuspended(UUID accountId, UUID userId, String reason) {
        return AccountEvent.builder()
            .eventId(UUID.randomUUID())
            .accountId(accountId)
            .userId(userId)
            .eventType(EventType.ACCOUNT_SUSPENDED.name())
            .accountStatus("SUSPENDED")
            .description(reason)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create balance updated event
     */
    public static AccountEvent balanceUpdated(UUID accountId, UUID userId, BigDecimal newBalance, BigDecimal availableBalance) {
        return AccountEvent.builder()
            .eventId(UUID.randomUUID())
            .accountId(accountId)
            .userId(userId)
            .eventType(EventType.BALANCE_UPDATED.name())
            .balance(newBalance)
            .availableBalance(availableBalance)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Check if account is active
     */
    public boolean isActive() {
        return "ACTIVE".equals(accountStatus);
    }
    
    /**
     * Check if account is suspended
     */
    public boolean isSuspended() {
        return "SUSPENDED".equals(accountStatus);
    }
    
    /**
     * Check if account is closed
     */
    public boolean isClosed() {
        return "CLOSED".equals(accountStatus);
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        if (getTimestamp() == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - getTimestamp().getEpochSecond();
    }
}