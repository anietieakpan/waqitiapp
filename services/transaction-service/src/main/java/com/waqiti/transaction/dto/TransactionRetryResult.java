package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionRetryResult {
    private UUID transactionId;
    private boolean retrySuccessful;
    private boolean finalFailure;
    private String failureReason;
    private int totalAttempts;
    private UUID newTransactionId;
    private LocalDateTime successfulAt;
}