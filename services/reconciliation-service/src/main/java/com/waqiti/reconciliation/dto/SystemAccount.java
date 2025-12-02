package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemAccount {

    private UUID accountId;
    
    private String accountCode;
    
    private String accountName;
    
    private SystemAccountType accountType;
    
    private String accountCategory;
    
    private BigDecimal balance;
    
    private String currency;
    
    private AccountStatus status;
    
    private boolean isControlAccount;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime lastActivity;
    
    private String ownerSystem;
    
    private String glCode;

    public enum SystemAccountType {
        NOSTRO,
        VOSTRO,
        SUSPENSE,
        CLEARING,
        FEE_INCOME,
        INTEREST_ACCRUAL,
        GL_CONTROL,
        OPERATIONAL,
        REGULATORY
    }

    public enum AccountStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        CLOSED
    }

    public boolean isActive() {
        return AccountStatus.ACTIVE.equals(status);
    }

    public boolean isControlAccount() {
        return isControlAccount;
    }

    public boolean hasBalance() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) != 0;
    }

    public boolean isNostroAccount() {
        return SystemAccountType.NOSTRO.equals(accountType);
    }

    public boolean isSuspenseAccount() {
        return SystemAccountType.SUSPENSE.equals(accountType);
    }
}