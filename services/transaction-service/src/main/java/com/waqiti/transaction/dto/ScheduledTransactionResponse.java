package com.waqiti.transaction.dto;

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
public class ScheduledTransactionResponse {
    
    private UUID id;
    private String reference;
    private String fromWalletId;
    private String toWalletId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private LocalDateTime scheduledFor;
    private ScheduledStatus status;
    private UUID executedTransactionId;
    private LocalDateTime executedAt;
    private String failureReason;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private Map<String, String> metadata;
    private String message;  // Added for feature status messaging
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum ScheduledStatus {
        PENDING,
        EXECUTING,
        COMPLETED,
        FAILED,
        CANCELLED,
        EXPIRED,
        NOT_IMPLEMENTED  // Added for Phase 2 features
    }
}