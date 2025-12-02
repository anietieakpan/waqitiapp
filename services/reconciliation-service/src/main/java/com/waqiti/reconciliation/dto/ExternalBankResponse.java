package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalBankResponse {

    private String responseId;
    
    private ResponseStatus status;
    
    private String bankCode;
    
    private String bankName;
    
    @Builder.Default
    private LocalDateTime responseTime = LocalDateTime.now();
    
    private String requestId;
    
    private BankTransaction transaction;
    
    private List<BankTransaction> transactions;
    
    private AccountBalance balance;
    
    private String errorCode;
    
    private String errorMessage;
    
    private Map<String, Object> additionalData;
    
    private String signature;
    
    private boolean verified;

    public enum ResponseStatus {
        SUCCESS,
        PENDING,
        FAILED,
        TIMEOUT,
        REJECTED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankTransaction {
        private String transactionId;
        private String externalReference;
        private BigDecimal amount;
        private String currency;
        private TransactionType type;
        private LocalDateTime timestamp;
        private String description;
        private String fromAccount;
        private String toAccount;
        private TransactionStatus status;
        private BigDecimal fees;
        private String channel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountBalance {
        private String accountNumber;
        private BigDecimal availableBalance;
        private BigDecimal ledgerBalance;
        private String currency;
        private LocalDateTime balanceDate;
        private List<BalanceHold> holds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceHold {
        private String holdId;
        private BigDecimal amount;
        private String reason;
        private LocalDateTime expiryDate;
    }

    public enum TransactionType {
        DEBIT,
        CREDIT,
        TRANSFER,
        REVERSAL,
        FEE
    }

    public enum TransactionStatus {
        PENDING,
        COMPLETED,
        FAILED,
        REVERSED
    }

    public boolean isSuccessful() {
        return ResponseStatus.SUCCESS.equals(status);
    }

    public boolean hasFailed() {
        return ResponseStatus.FAILED.equals(status) ||
               ResponseStatus.TIMEOUT.equals(status) ||
               ResponseStatus.REJECTED.equals(status);
    }

    public boolean hasTransactions() {
        return transactions != null && !transactions.isEmpty();
    }

    public boolean hasError() {
        return errorCode != null || errorMessage != null;
    }

    public boolean isVerified() {
        return verified && signature != null;
    }

    public int getTransactionCount() {
        return transactions != null ? transactions.size() : 0;
    }
}