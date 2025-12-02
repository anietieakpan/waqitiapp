package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TransactionCancellationResult {
    private UUID transactionId;
    private boolean success;
    private String message;
}