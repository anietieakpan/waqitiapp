package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    private UUID entryId;
    
    private UUID transactionId;
    
    private String reference;
    
    private UUID accountId;
    
    private String accountCode;
    
    private String accountName;
    
    private BigDecimal amount;
    
    private String currency;
    
    private EntryType entryType;
    
    private LocalDateTime transactionDate;
    
    private LocalDateTime valueDate;
    
    private LocalDateTime postingDate;
    
    private String description;
    
    private String transactionType;
    
    private UUID batchId;
    
    private String postedBy;
    
    private LedgerEntryStatus status;
    
    private String reversalReference;
    
    private UUID originalEntryId;
    
    private Map<String, String> additionalFields;

    public enum EntryType {
        DEBIT("DR"),
        CREDIT("CR");

        private final String code;

        EntryType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public enum LedgerEntryStatus {
        PENDING,
        POSTED,
        REVERSED,
        CANCELLED,
        SUSPENDED
    }

    public boolean isDebit() {
        return EntryType.DEBIT.equals(entryType);
    }

    public boolean isCredit() {
        return EntryType.CREDIT.equals(entryType);
    }

    public boolean isPosted() {
        return LedgerEntryStatus.POSTED.equals(status);
    }

    public boolean isReversed() {
        return LedgerEntryStatus.REVERSED.equals(status);
    }

    public boolean isPending() {
        return LedgerEntryStatus.PENDING.equals(status);
    }

    public BigDecimal getSignedAmount() {
        if (amount == null) return BigDecimal.ZERO;
        return isDebit() ? amount : amount.negate();
    }
}