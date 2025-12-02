package com.waqiti.transaction.rollback;

import com.waqiti.transaction.domain.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Production-grade rollback audit service for compliance and forensics
 * Maintains comprehensive audit trails for all rollback operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RollbackAuditService {

    private final RollbackAuditRepository rollbackAuditRepository;

    /**
     * Create audit record for single transaction rollback
     */
    @Transactional
    public RollbackAuditRecord createRollbackRecord(Transaction transaction, String reason, String initiatedBy) {
        RollbackAuditRecord auditRecord = RollbackAuditRecord.builder()
                .id(UUID.randomUUID())
                .transactionId(transaction.getId())
                .originalAmount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .fromUserId(transaction.getFromUserId())
                .toUserId(transaction.getToUserId())
                .rollbackReason(reason)
                .initiatedBy(initiatedBy)
                .initiatedAt(LocalDateTime.now())
                .status(RollbackAuditStatus.IN_PROGRESS)
                .build();

        return rollbackAuditRepository.save(auditRecord);
    }

    /**
     * Create audit record for batch rollback
     */
    @Transactional
    public BatchRollbackAuditRecord createBatchRollbackRecord(List<UUID> transactionIds, String reason, String initiatedBy) {
        BatchRollbackAuditRecord batchRecord = BatchRollbackAuditRecord.builder()
                .id(UUID.randomUUID())
                .transactionCount(transactionIds.size())
                .rollbackReason(reason)
                .initiatedBy(initiatedBy)
                .initiatedAt(LocalDateTime.now())
                .status(BatchRollbackAuditStatus.IN_PROGRESS)
                .build();

        return rollbackAuditRepository.save(batchRecord);
    }

    /**
     * Complete rollback audit record
     */
    @Transactional
    public void completeRollbackRecord(RollbackAuditRecord auditRecord, CompensationResult compensationResult) {
        auditRecord.setStatus(RollbackAuditStatus.COMPLETED);
        auditRecord.setCompletedAt(LocalDateTime.now());
        auditRecord.setCompensationActionsExecuted(compensationResult.getActionsExecuted());
        auditRecord.setCompensationResult(compensationResult.getOverallResult());

        rollbackAuditRepository.save(auditRecord);
    }

    /**
     * Complete batch rollback audit record
     */
    @Transactional
    public void completeBatchRollbackRecord(BatchRollbackAuditRecord batchRecord, 
                                          BatchRollbackStatus status, 
                                          int successfulCount, 
                                          int failedCount) {
        batchRecord.setStatus(mapToBatchAuditStatus(status));
        batchRecord.setCompletedAt(LocalDateTime.now());
        batchRecord.setSuccessfulRollbacks(successfulCount);
        batchRecord.setFailedRollbacks(failedCount);

        rollbackAuditRepository.save(batchRecord);
    }

    /**
     * Log emergency rollback initiation
     */
    @Transactional
    public void logEmergencyRollbackInitiation(EmergencyRollbackRequest request) {
        EmergencyRollbackAuditRecord emergencyRecord = EmergencyRollbackAuditRecord.builder()
                .id(UUID.randomUUID())
                .emergencyId(request.getEmergencyId())
                .emergencyType(request.getEmergencyType().toString())
                .reason(request.getReason())
                .initiatedBy(request.getInitiatedBy())
                .initiatedAt(LocalDateTime.now())
                .status(EmergencyRollbackAuditStatus.IN_PROGRESS)
                .build();

        rollbackAuditRepository.save(emergencyRecord);
    }

    /**
     * Log rollback failure for audit purposes
     */
    @Transactional
    public void logRollbackFailure(UUID transactionId, String reason, String initiatedBy, Exception error) {
        RollbackAuditRecord auditRecord = RollbackAuditRecord.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .rollbackReason(reason)
                .initiatedBy(initiatedBy)
                .initiatedAt(LocalDateTime.now())
                .status(RollbackAuditStatus.FAILED)
                .failedAt(LocalDateTime.now())
                .failureReason(error.getMessage())
                .build();

        rollbackAuditRepository.save(auditRecord);
    }

    /**
     * Log batch rollback failure
     */
    @Transactional
    public void logBatchRollbackFailure(BatchRollbackAuditRecord batchRecord, Exception error) {
        batchRecord.setStatus(BatchRollbackAuditStatus.FAILED);
        batchRecord.setFailedAt(LocalDateTime.now());
        batchRecord.setFailureReason(error.getMessage());

        rollbackAuditRepository.save(batchRecord);
    }

    private BatchRollbackAuditStatus mapToBatchAuditStatus(BatchRollbackStatus status) {
        return switch (status) {
            case ALL_COMPLETED -> BatchRollbackAuditStatus.COMPLETED;
            case PARTIAL_SUCCESS -> BatchRollbackAuditStatus.PARTIAL_SUCCESS;
            case ALL_FAILED -> BatchRollbackAuditStatus.ALL_FAILED;
            case BATCH_FAILED -> BatchRollbackAuditStatus.FAILED;
        };
    }

    // Audit Status Enums
    public enum RollbackAuditStatus {
        IN_PROGRESS, COMPLETED, FAILED
    }

    public enum BatchRollbackAuditStatus {
        IN_PROGRESS, COMPLETED, PARTIAL_SUCCESS, ALL_FAILED, FAILED
    }

    public enum EmergencyRollbackAuditStatus {
        IN_PROGRESS, COMPLETED, PARTIAL_SUCCESS, FAILED
    }

    // Audit Record DTOs
    @lombok.Builder
    @lombok.Data
    public static class RollbackAuditRecord {
        private UUID id;
        private UUID transactionId;
        private java.math.BigDecimal originalAmount;
        private String currency;
        private String fromUserId;
        private String toUserId;
        private String rollbackReason;
        private String initiatedBy;
        private LocalDateTime initiatedAt;
        private RollbackAuditStatus status;
        private LocalDateTime completedAt;
        private LocalDateTime failedAt;
        private String failureReason;
        private Integer compensationActionsExecuted;
        private String compensationResult;
    }

    @lombok.Builder
    @lombok.Data
    public static class BatchRollbackAuditRecord {
        private UUID id;
        private Integer transactionCount;
        private String rollbackReason;
        private String initiatedBy;
        private LocalDateTime initiatedAt;
        private BatchRollbackAuditStatus status;
        private LocalDateTime completedAt;
        private LocalDateTime failedAt;
        private String failureReason;
        private Integer successfulRollbacks;
        private Integer failedRollbacks;
    }

    @lombok.Builder
    @lombok.Data
    public static class EmergencyRollbackAuditRecord {
        private UUID id;
        private String emergencyId;
        private String emergencyType;
        private String reason;
        private String initiatedBy;
        private LocalDateTime initiatedAt;
        private EmergencyRollbackAuditStatus status;
        private LocalDateTime completedAt;
        private LocalDateTime failedAt;
        private String failureReason;
    }
}