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
public class DormantAccount {

    private UUID accountId;
    
    private String accountNumber;
    
    private String accountName;
    
    private String accountType;
    
    private BigDecimal balance;
    
    private String currency;
    
    private LocalDateTime lastActivity;
    
    private int daysSinceLastActivity;
    
    private UUID customerId;
    
    private String customerName;
    
    private String contactInfo;
    
    private DormancyReason dormancyReason;
    
    private boolean hasEscheatmentRisk;
    
    private LocalDateTime expectedEscheatmentDate;
    
    private String recommendedAction;

    public enum DormancyReason {
        NO_ACTIVITY,
        CUSTOMER_INACTIVE,
        ZERO_BALANCE,
        MINIMAL_BALANCE,
        ACCOUNT_ABANDONED
    }

    public boolean isDormant() {
        return daysSinceLastActivity >= 365; // 1 year standard
    }

    public boolean isEscheatmentCandidate() {
        return hasEscheatmentRisk && daysSinceLastActivity >= 1095; // 3 years
    }

    public boolean hasBalance() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) != 0;
    }

    public boolean requiresAction() {
        return hasEscheatmentRisk || daysSinceLastActivity > 730; // 2 years
    }

    public int getYearsSinceActivity() {
        return daysSinceLastActivity / 365;
    }
}