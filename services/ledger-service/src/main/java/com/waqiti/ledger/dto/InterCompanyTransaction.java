package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for inter-company transactions
 * Represents a transaction between two entities within the same corporate group
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterCompanyTransaction {
    
    private UUID transactionId;
    private String transactionNumber;
    private UUID sourceEntityId;
    private String sourceEntityName;
    private UUID targetEntityId;
    private String targetEntityName;
    private String sourceAccountCode;
    private String targetAccountCode;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime transactionDate;
    private LocalDateTime valueDate;
    private String description;
    private String reference;
    private String transactionType;
    private String status;
    private String sourceSystemId;
    private String targetSystemId;
    private UUID journalEntryId;
    private String reconciledStatus;
    private LocalDateTime reconciledAt;
    private String reconciledBy;
    private String notes;
    private LocalDateTime createdAt;
    private String createdBy;
}