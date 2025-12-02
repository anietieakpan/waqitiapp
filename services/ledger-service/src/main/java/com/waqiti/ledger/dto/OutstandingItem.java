package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutstandingItem {
    private UUID itemId;
    private String itemType;
    private String itemNumber;
    private LocalDate itemDate;
    private LocalDate dueDate;
    private BigDecimal amount;
    private String payee;
    private String description;
    private String reference;
    private OutstandingItemStatus status;
    private int daysPending;
    private boolean isStale;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private UUID relatedTransactionId;
    private UUID relatedJournalEntryId;
}

enum OutstandingItemStatus {
    OUTSTANDING,
    CLEARED,
    VOIDED,
    STOP_PAYMENT,
    REPLACED,
    STALE
}