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
public class AccountSummary {

    private UUID accountId;
    
    private String accountNumber;
    
    private String accountName;
    
    private String accountType;
    
    private String status;
    
    private BigDecimal currentBalance;
    
    private BigDecimal availableBalance;
    
    private String currency;
    
    private LocalDateTime lastActivity;
    
    private UUID customerId;
    
    private String customerName;
    
    private String branchCode;
    
    private String productCode;
    
    private boolean hasRestrictions;
    
    private int transactionCountMTD;
    
    private BigDecimal transactionVolumeMTD;

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean hasBalance() {
        return currentBalance != null && currentBalance.compareTo(BigDecimal.ZERO) != 0;
    }

    public boolean hasRecentActivity() {
        return lastActivity != null && 
               lastActivity.isAfter(LocalDateTime.now().minusDays(30));
    }

    public boolean isDormant() {
        return !hasRecentActivity() && 
               lastActivity != null && 
               lastActivity.isBefore(LocalDateTime.now().minusDays(365));
    }

    public BigDecimal getAbsoluteBalance() {
        return currentBalance != null ? currentBalance.abs() : BigDecimal.ZERO;
    }
}