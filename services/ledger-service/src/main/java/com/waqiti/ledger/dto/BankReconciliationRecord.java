package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankReconciliationRecord {
    private UUID recordId;
    private UUID bankAccountId;
    private String bankAccountNumber;
    private String bankAccountName;
    private LocalDate reconciliationDate;
    private BigDecimal bankStatementBalance;
    private BigDecimal ledgerBalance;
    private BigDecimal reconciledBalance;
    private BigDecimal totalAdjustments;
    private List<ReconciliationAdjustment> adjustments;
    private List<OutstandingItem> outstandingItems;
    private ReconciliationStatus status;
    private String reconciledBy;
    private LocalDateTime reconciledAt;
    private String notes;
    private List<UUID> supportingDocuments;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ReconciliationAdjustment {
    private UUID adjustmentId;
    private String adjustmentType;
    private String description;
    private BigDecimal amount;
    private UUID accountId;
    private String accountCode;
    private String reference;
    private boolean requiresApproval;
    private String approvedBy;
    private LocalDateTime approvedAt;
}

enum ReconciliationStatus {
    DRAFT,
    IN_PROGRESS,
    COMPLETED,
    APPROVED,
    REJECTED,
    UNDER_REVIEW
}