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
public class TransactionDetails {

    private UUID transactionId;
    
    private String externalReference;
    
    private String transactionType;
    
    private TransactionStatus status;
    
    private BigDecimal amount;
    
    private String currency;
    
    private UUID fromAccountId;
    
    private UUID toAccountId;
    
    private String fromAccountNumber;
    
    private String toAccountNumber;
    
    private LocalDateTime transactionDate;
    
    private LocalDateTime valueDate;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime lastUpdatedAt;
    
    private String description;
    
    private String narrative;
    
    private TransactionChannel channel;
    
    private String initiatedBy;
    
    private String authorizedBy;
    
    private BigDecimal feeAmount;
    
    private String feeCurrency;
    
    private BigDecimal exchangeRate;
    
    private String beneficiaryName;
    
    private String beneficiaryAccount;
    
    private String beneficiaryBankCode;
    
    private String purposeCode;
    
    private Map<String, String> additionalInfo;
    
    private List<TransactionLeg> transactionLegs;
    
    private AuditInfo auditInfo;

    public enum TransactionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REVERSED,
        SUSPENDED,
        EXPIRED
    }

    public enum TransactionChannel {
        ONLINE_BANKING,
        MOBILE_APP,
        ATM,
        BRANCH,
        API,
        BATCH_FILE,
        SWIFT,
        ACH,
        CARD_PAYMENT
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionLeg {
        private UUID legId;
        private UUID accountId;
        private String accountNumber;
        private BigDecimal amount;
        private String currency;
        private LegType legType;
        private String description;
    }

    public enum LegType {
        DEBIT,
        CREDIT,
        FEE,
        TAX
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditInfo {
        private String createdBy;
        private LocalDateTime createdAt;
        private String lastModifiedBy;
        private LocalDateTime lastModifiedAt;
        private String ipAddress;
        private String userAgent;
        private String sessionId;
    }

    public boolean isCompleted() {
        return TransactionStatus.COMPLETED.equals(status);
    }

    public boolean isPending() {
        return TransactionStatus.PENDING.equals(status) || 
               TransactionStatus.PROCESSING.equals(status);
    }

    public boolean isFailed() {
        return TransactionStatus.FAILED.equals(status) ||
               TransactionStatus.CANCELLED.equals(status) ||
               TransactionStatus.EXPIRED.equals(status);
    }

    public boolean isReversed() {
        return TransactionStatus.REVERSED.equals(status);
    }

    public boolean hasFees() {
        return feeAmount != null && feeAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isCrossCurrency() {
        return exchangeRate != null && exchangeRate.compareTo(BigDecimal.ONE) != 0;
    }

    public BigDecimal getTotalAmount() {
        BigDecimal total = amount != null ? amount : BigDecimal.ZERO;
        if (hasFees()) {
            total = total.add(feeAmount);
        }
        return total;
    }
}