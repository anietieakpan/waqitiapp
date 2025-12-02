package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerServiceResponse {

    private String responseId;
    
    private ResponseStatus status;
    
    private String message;
    
    private LedgerEntry ledgerEntry;
    
    private List<LedgerEntry> ledgerEntries;
    
    private AccountBalance accountBalance;
    
    private List<AccountBalance> accountBalances;
    
    private TrialBalanceData trialBalance;
    
    @Builder.Default
    private LocalDateTime responseTime = LocalDateTime.now();
    
    private String errorCode;
    
    private String errorMessage;
    
    private Map<String, Object> metadata;

    public enum ResponseStatus {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILED,
        VALIDATION_ERROR
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerEntry {
        private UUID entryId;
        private String journalId;
        private String accountCode;
        private String accountName;
        private BigDecimal debitAmount;
        private BigDecimal creditAmount;
        private String currency;
        private String description;
        private LocalDateTime transactionDate;
        private LocalDateTime valueDate;
        private String reference;
        private String transactionType;
        private Map<String, String> tags;
        private EntryStatus status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor  
    public static class AccountBalance {
        private String accountCode;
        private String accountName;
        private String accountType;
        private BigDecimal balance;
        private BigDecimal debitBalance;
        private BigDecimal creditBalance;
        private String currency;
        private LocalDateTime balanceDate;
        private boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrialBalanceData {
        private LocalDateTime balanceDate;
        private String currency;
        private BigDecimal totalDebits;
        private BigDecimal totalCredits;
        private boolean isBalanced;
        private List<AccountBalance> accountBalances;
        private List<BalanceVariance> variances;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceVariance {
        private String accountCode;
        private BigDecimal variance;
        private String description;
        private VarianceType type;
    }

    public enum EntryStatus {
        POSTED,
        PENDING,
        REJECTED,
        REVERSED
    }

    public enum VarianceType {
        ROUNDING,
        TIMING,
        DATA_ENTRY,
        SYSTEM_ERROR
    }

    public boolean isSuccessful() {
        return ResponseStatus.SUCCESS.equals(status);
    }

    public boolean hasEntries() {
        return ledgerEntries != null && !ledgerEntries.isEmpty();
    }

    public boolean hasBalances() {
        return accountBalances != null && !accountBalances.isEmpty();
    }

    public boolean hasTrialBalance() {
        return trialBalance != null;
    }

    public boolean isTrialBalanced() {
        return trialBalance != null && trialBalance.isBalanced();
    }

    public int getEntryCount() {
        return ledgerEntries != null ? ledgerEntries.size() : 0;
    }

    public BigDecimal getTotalDebits() {
        if (ledgerEntries == null) return BigDecimal.ZERO;
        return ledgerEntries.stream()
                .map(LedgerEntry::getDebitAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalCredits() {
        if (ledgerEntries == null) return BigDecimal.ZERO;
        return ledgerEntries.stream()
                .map(LedgerEntry::getCreditAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}