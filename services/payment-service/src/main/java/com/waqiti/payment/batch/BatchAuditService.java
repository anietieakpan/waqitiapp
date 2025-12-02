package com.waqiti.payment.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Production-grade batch audit service for comprehensive tracking
 * Maintains audit trails for all batch processing operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchAuditService {

    private final BatchAuditRepository batchAuditRepository;

    /**
     * Create audit record for batch processing initiation
     */
    @Transactional
    public BatchAuditRecord createBatchAuditRecord(BatchPaymentRequest batchRequest) {
        log.info("SECURITY: Creating batch audit record for batch: {}", batchRequest.getBatchId());

        // Calculate batch totals
        BigDecimal totalAmount = batchRequest.getPayments().stream()
                .map(PaymentRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BatchAuditRecord auditRecord = BatchAuditRecord.builder()
                .id(UUID.randomUUID())
                .batchId(batchRequest.getBatchId())
                .paymentCount(batchRequest.getPayments().size())
                .totalAmount(totalAmount)
                .currency(extractPrimaryCurrency(batchRequest.getPayments()))
                .initiatedBy(batchRequest.getInitiatedBy())
                .initiatedAt(LocalDateTime.now())
                .status(BatchAuditStatus.IN_PROGRESS)
                .processingStartedAt(LocalDateTime.now())
                .build();

        BatchAuditRecord savedRecord = batchAuditRepository.save(auditRecord);
        
        log.debug("SECURITY: Batch audit record created with ID: {}", savedRecord.getId());
        return savedRecord;
    }

    /**
     * Complete batch audit record with final results
     */
    @Transactional
    public void completeBatchAuditRecord(BatchAuditRecord auditRecord, 
                                       PaymentBatchProcessor.BatchProcessingStatus batchStatus,
                                       int successfulCount, 
                                       int failedCount) {
        log.info("SECURITY: Completing batch audit record: {}, status: {}", 
                auditRecord.getId(), batchStatus);

        auditRecord.setStatus(mapToAuditStatus(batchStatus));
        auditRecord.setSuccessfulPayments(successfulCount);
        auditRecord.setFailedPayments(failedCount);
        auditRecord.setProcessingCompletedAt(LocalDateTime.now());

        // Calculate processing duration
        if (auditRecord.getProcessingStartedAt() != null) {
            auditRecord.setProcessingDurationMs(
                java.time.Duration.between(auditRecord.getProcessingStartedAt(), 
                                         auditRecord.getProcessingCompletedAt()).toMillis());
        }

        batchAuditRepository.save(auditRecord);
        
        log.info("SECURITY: Batch audit record completed - successful: {}, failed: {}", 
                successfulCount, failedCount);
    }

    /**
     * Mark batch audit record as failed
     */
    @Transactional
    public void markBatchAuditRecordFailed(BatchAuditRecord auditRecord, String errorMessage) {
        log.error("SECURITY: Marking batch audit record as failed: {}, error: {}", 
                auditRecord.getId(), errorMessage);

        auditRecord.setStatus(BatchAuditStatus.FAILED);
        auditRecord.setErrorMessage(errorMessage);
        auditRecord.setProcessingFailedAt(LocalDateTime.now());

        // Calculate processing duration up to failure
        if (auditRecord.getProcessingStartedAt() != null) {
            auditRecord.setProcessingDurationMs(
                java.time.Duration.between(auditRecord.getProcessingStartedAt(), 
                                         auditRecord.getProcessingFailedAt()).toMillis());
        }

        batchAuditRepository.save(auditRecord);
    }

    /**
     * Add processing milestone to audit record
     */
    @Transactional
    public void addProcessingMilestone(UUID auditRecordId, String milestone, String details) {
        log.debug("SECURITY: Adding processing milestone to audit record: {}, milestone: {}", 
                auditRecordId, milestone);

        BatchProcessingMilestone milestoneRecord = BatchProcessingMilestone.builder()
                .id(UUID.randomUUID())
                .auditRecordId(auditRecordId)
                .milestone(milestone)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();

        batchAuditRepository.saveMilestone(milestoneRecord);
    }

    /**
     * Log security event during batch processing
     */
    @Transactional
    public void logSecurityEvent(UUID auditRecordId, SecurityEventType eventType, 
                                String description, String additionalData) {
        log.warn("SECURITY: Logging security event for batch audit: {}, event: {}, description: {}", 
                auditRecordId, eventType, description);

        BatchSecurityEvent securityEvent = BatchSecurityEvent.builder()
                .id(UUID.randomUUID())
                .auditRecordId(auditRecordId)
                .eventType(eventType)
                .description(description)
                .additionalData(additionalData)
                .timestamp(LocalDateTime.now())
                .severity(determineSeverity(eventType))
                .build();

        batchAuditRepository.saveSecurityEvent(securityEvent);

        // Trigger alerts for high-severity events
        if (securityEvent.getSeverity() == SecurityEventSeverity.HIGH || 
            securityEvent.getSeverity() == SecurityEventSeverity.CRITICAL) {
            
            triggerSecurityAlert(securityEvent);
        }
    }

    /**
     * Get comprehensive audit report for a batch
     */
    public BatchAuditReport getBatchAuditReport(UUID auditRecordId) {
        BatchAuditRecord auditRecord = batchAuditRepository.findById(auditRecordId)
                .orElseThrow(() -> new IllegalArgumentException("Audit record not found: " + auditRecordId));

        List<BatchProcessingMilestone> milestones = batchAuditRepository
                .findMilestonesByAuditRecordId(auditRecordId);

        List<BatchSecurityEvent> securityEvents = batchAuditRepository
                .findSecurityEventsByAuditRecordId(auditRecordId);

        return BatchAuditReport.builder()
                .auditRecord(auditRecord)
                .processingMilestones(milestones)
                .securityEvents(securityEvents)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Find audit records by various criteria for investigations
     */
    public List<BatchAuditRecord> findAuditRecordsByCriteria(BatchAuditSearchCriteria criteria) {
        return batchAuditRepository.findByCriteria(criteria);
    }

    /**
     * Get batch processing statistics for monitoring
     */
    public BatchProcessingStatistics getBatchProcessingStatistics(LocalDateTime startDate, 
                                                                LocalDateTime endDate) {
        return batchAuditRepository.getProcessingStatistics(startDate, endDate);
    }

    /**
     * Utility methods
     */
    private String extractPrimaryCurrency(List<PaymentRequest> payments) {
        // For multi-currency batches, return the most common currency
        return payments.stream()
                .map(PaymentRequest::getCurrency)
                .collect(java.util.stream.Collectors.groupingBy(
                    currency -> currency, 
                    java.util.stream.Collectors.counting()))
                .entrySet()
                .stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse("USD");
    }

    private BatchAuditStatus mapToAuditStatus(PaymentBatchProcessor.BatchProcessingStatus batchStatus) {
        return switch (batchStatus) {
            case PENDING -> BatchAuditStatus.PENDING;
            case IN_PROGRESS -> BatchAuditStatus.IN_PROGRESS;
            case COMPLETED -> BatchAuditStatus.COMPLETED;
            case PARTIAL_SUCCESS -> BatchAuditStatus.PARTIAL_SUCCESS;
            case FAILED -> BatchAuditStatus.FAILED;
            case TIMEOUT -> BatchAuditStatus.TIMEOUT;
        };
    }

    private SecurityEventSeverity determineSeverity(SecurityEventType eventType) {
        return switch (eventType) {
            case SUSPICIOUS_PATTERN_DETECTED -> SecurityEventSeverity.HIGH;
            case RATE_LIMIT_EXCEEDED -> SecurityEventSeverity.MEDIUM;
            case VALIDATION_FAILURE -> SecurityEventSeverity.LOW;
            case UNAUTHORIZED_ACCESS_ATTEMPT -> SecurityEventSeverity.CRITICAL;
            case LARGE_AMOUNT_DETECTED -> SecurityEventSeverity.HIGH;
            case CIRCUIT_BREAKER_TRIGGERED -> SecurityEventSeverity.MEDIUM;
            case PROCESSING_TIMEOUT -> SecurityEventSeverity.MEDIUM;
        };
    }

    private void triggerSecurityAlert(BatchSecurityEvent securityEvent) {
        // Implementation would send alerts via email, Slack, PagerDuty, etc.
        log.error("SECURITY ALERT: {} - {} (Batch: {})", 
                securityEvent.getEventType(), 
                securityEvent.getDescription(),
                securityEvent.getAuditRecordId());
        
        // In production, this would integrate with alerting systems
    }

    // Enums and DTOs
    public enum BatchAuditStatus {
        PENDING, IN_PROGRESS, COMPLETED, PARTIAL_SUCCESS, FAILED, TIMEOUT
    }

    public enum SecurityEventType {
        SUSPICIOUS_PATTERN_DETECTED,
        RATE_LIMIT_EXCEEDED,
        VALIDATION_FAILURE,
        UNAUTHORIZED_ACCESS_ATTEMPT,
        LARGE_AMOUNT_DETECTED,
        CIRCUIT_BREAKER_TRIGGERED,
        PROCESSING_TIMEOUT
    }

    public enum SecurityEventSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    @lombok.Builder
    @lombok.Data
    public static class BatchAuditRecord {
        private UUID id;
        private String batchId;
        private Integer paymentCount;
        private BigDecimal totalAmount;
        private String currency;
        private String initiatedBy;
        private LocalDateTime initiatedAt;
        private BatchAuditStatus status;
        private LocalDateTime processingStartedAt;
        private LocalDateTime processingCompletedAt;
        private LocalDateTime processingFailedAt;
        private Long processingDurationMs;
        private Integer successfulPayments;
        private Integer failedPayments;
        private String errorMessage;
        private String ipAddress;
        private String userAgent;
        private String additionalMetadata;
    }

    @lombok.Builder
    @lombok.Data
    public static class BatchProcessingMilestone {
        private UUID id;
        private UUID auditRecordId;
        private String milestone;
        private String details;
        private LocalDateTime timestamp;
    }

    @lombok.Builder
    @lombok.Data
    public static class BatchSecurityEvent {
        private UUID id;
        private UUID auditRecordId;
        private SecurityEventType eventType;
        private String description;
        private String additionalData;
        private LocalDateTime timestamp;
        private SecurityEventSeverity severity;
    }

    @lombok.Builder
    @lombok.Data
    public static class BatchAuditReport {
        private BatchAuditRecord auditRecord;
        private List<BatchProcessingMilestone> processingMilestones;
        private List<BatchSecurityEvent> securityEvents;
        private LocalDateTime generatedAt;
    }

    @lombok.Builder
    @lombok.Data
    public static class BatchAuditSearchCriteria {
        private String batchId;
        private String initiatedBy;
        private BatchAuditStatus status;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private String currency;
    }

    @lombok.Builder
    @lombok.Data
    public static class BatchProcessingStatistics {
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
        private Long totalBatches;
        private Long successfulBatches;
        private Long failedBatches;
        private Long partialSuccessBatches;
        private BigDecimal totalAmountProcessed;
        private Long totalPaymentsProcessed;
        private Double averageProcessingTimeMs;
        private Double averageBatchSize;
        private Long securityEventsCount;
    }
}