package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BatchRollbackResult {
    private String batchId;
    private int totalTransactions;
    private int rolledBackTransactions;
    private List<UUID> rolledBackTransactionIds;
    private List<String> errors;
    private boolean success;
    private LocalDateTime completedAt;
}