package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DeadlockRecoveryResult {
    private String deadlockId;
    private String victimTransactionId;
    private boolean recoverySuccessful;
    private boolean retryScheduled;
    private List<String> resourcesReleased;
    private LocalDateTime recoveryCompletedAt;
    private String errorMessage;
}