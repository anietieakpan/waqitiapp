package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaResponse {
    
    private String sagaId;
    private UUID transactionId;
    private SagaStatus status;
    private String sagaType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String currentStep;
    private String failureReason;
    
    public enum SagaStatus {
        INITIATED,
        RUNNING,
        COMPENSATING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}