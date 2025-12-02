package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Account entity representing a payment account
 * CRITICAL: Added @Version for optimistic locking to prevent balance corruption
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "accounts")
public class Account {
    @Id
    private String id;

    @Version
    private Long version;

    private String userId;
    private String accountNumber;
    private String accountType;
    private String currency;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal frozenAmount;
    private AccountStatus status;
    private boolean isActive;
    private boolean isFrozen;
    private boolean isRestricted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivityAt;
    private String kycLevel;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal transactionLimit;
    private String accountCategory;
    private String accountSubType;
    private boolean allowInternationalTransactions;
    private boolean allowCashTransactions;
}