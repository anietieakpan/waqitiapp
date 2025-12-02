package com.waqiti.reconciliation.dto;

import com.waqiti.reconciliation.domain.ReconciliationVariance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealTimeReconciliationResult {

    private UUID transactionId;

    private boolean reconciled;

    private UUID breakId;

    private List<ReconciliationVariance> variances;

    private String message;

    @Builder.Default
    private LocalDateTime reconciledAt = LocalDateTime.now();

    private String reconciledBy;

    private Long processingTimeMs;

    private ReconciliationStatus status;

    private String errorCode;

    private String errorDetails;

    public enum ReconciliationStatus {
        SUCCESS,
        BREAK_DETECTED,
        ERROR,
        TIMEOUT,
        VALIDATION_FAILED
    }

    public boolean hasBreaks() {
        return variances != null && !variances.isEmpty();
    }

    public boolean isSuccessful() {
        return reconciled && ReconciliationStatus.SUCCESS.equals(status);
    }

    public boolean hasErrors() {
        return ReconciliationStatus.ERROR.equals(status) || 
               ReconciliationStatus.VALIDATION_FAILED.equals(status);
    }

    public static RealTimeReconciliationResult success(UUID transactionId, String message) {
        return RealTimeReconciliationResult.builder()
            .transactionId(transactionId)
            .reconciled(true)
            .status(ReconciliationStatus.SUCCESS)
            .message(message)
            .build();
    }

    public static RealTimeReconciliationResult breakDetected(UUID transactionId, UUID breakId, 
                                                           List<ReconciliationVariance> variances, String message) {
        return RealTimeReconciliationResult.builder()
            .transactionId(transactionId)
            .reconciled(false)
            .breakId(breakId)
            .variances(variances)
            .status(ReconciliationStatus.BREAK_DETECTED)
            .message(message)
            .build();
    }

    public static RealTimeReconciliationResult error(UUID transactionId, String message, String errorCode) {
        return RealTimeReconciliationResult.builder()
            .transactionId(transactionId)
            .reconciled(false)
            .status(ReconciliationStatus.ERROR)
            .message(message)
            .errorCode(errorCode)
            .build();
    }
}