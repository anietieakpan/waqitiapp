package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealTimeReconciliationRequest {

    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @Size(max = 100, message = "External reference cannot exceed 100 characters")
    private String externalReference;

    private LocalDateTime transactionDate;

    private String initiatedBy;

    @Builder.Default
    private ReconciliationPriority priority = ReconciliationPriority.NORMAL;

    @Builder.Default
    private boolean forceReconciliation = false;

    private Map<String, Object> additionalContext;

    public enum ReconciliationPriority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    public boolean isHighPriority() {
        return ReconciliationPriority.HIGH.equals(priority) || 
               ReconciliationPriority.CRITICAL.equals(priority);
    }

    public boolean shouldForceReconciliation() {
        return forceReconciliation;
    }
}