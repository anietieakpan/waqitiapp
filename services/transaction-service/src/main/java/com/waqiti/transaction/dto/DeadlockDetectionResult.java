package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
public class DeadlockDetectionResult {
    private String transactionId;
    private boolean deadlockDetected;
    private List<List<String>> deadlockCycles;
    private String victimTransaction;
    private Set<String> resourcesInvolved;
    private LocalDateTime detectedAt;
    private LocalDateTime checkedAt;
    private DeadlockSeverity severity;
    private String errorMessage;
}