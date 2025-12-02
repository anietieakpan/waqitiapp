package com.waqiti.common.transaction;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of a distributed transaction
 */
@Data
@Builder
public class TransactionResult {
    
    private String transactionId;
    private TransactionStatus status;
    private LocalDateTime completedAt;
    private Long durationMs;
    private List<ParticipantResult> participantResults;
    private Map<String, Object> metadata;
    private String errorMessage;
    private String compensationResult;
    
    /**
     * Result from a single participant
     */
    @Data
    @Builder
    public static class ParticipantResult {
        private String participantId;
        private String serviceId;
        private ParticipantStatus status;
        private String errorMessage;
        private Map<String, Object> result;
        private LocalDateTime completedAt;
        private Long durationMs;
    }
    
    /**
     * Check if transaction was successful
     */
    public boolean isSuccessful() {
        return status == TransactionStatus.COMMITTED;
    }
    
    /**
     * Check if transaction was compensated (saga)
     */
    public boolean isCompensated() {
        return status == TransactionStatus.COMPENSATED;
    }
    
    /**
     * Check if transaction failed
     */
    public boolean isFailed() {
        return status == TransactionStatus.FAILED || status == TransactionStatus.ABORTED;
    }
    
    /**
     * Create a successful result
     */
    public static TransactionResult success(String transactionId, List<ParticipantResult> participants) {
        return TransactionResult.builder()
                .transactionId(transactionId)
                .status(TransactionStatus.COMMITTED)
                .completedAt(LocalDateTime.now())
                .participantResults(participants)
                .build();
    }
    
    /**
     * Create a simple successful result
     */
    public static TransactionResult success(String message) {
        return TransactionResult.builder()
                .status(TransactionStatus.COMMITTED)
                .completedAt(LocalDateTime.now())
                .errorMessage(message) // Use errorMessage for simple message storage
                .build();
    }
    
    /**
     * Create a failed result
     */
    public static TransactionResult failure(String transactionId, String error, List<ParticipantResult> participants) {
        return TransactionResult.builder()
                .transactionId(transactionId)
                .status(TransactionStatus.FAILED)
                .completedAt(LocalDateTime.now())
                .errorMessage(error)
                .participantResults(participants)
                .build();
    }
    
    /**
     * Create a simple failure result
     */
    public static TransactionResult failure(String message) {
        return TransactionResult.builder()
                .status(TransactionStatus.FAILED)
                .completedAt(LocalDateTime.now())
                .errorMessage(message)
                .build();
    }
}