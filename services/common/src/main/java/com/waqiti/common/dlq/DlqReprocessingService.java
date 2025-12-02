package com.waqiti.common.dlq;

import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DLQ Reprocessing Service
 *
 * CRITICAL OPERATIONS SERVICE
 *
 * Provides comprehensive Dead Letter Queue reprocessing capabilities:
 * - Manual message reprocessing from DLQ
 * - Batch reprocessing with progress tracking
 * - Message inspection and analysis
 * - Reprocessing rules and filters
 * - Audit trail for all reprocessing operations
 * - Metrics and monitoring
 *
 * USE CASES:
 * - Reprocess messages after fixing underlying issues
 * - Recover from temporary service outages
 * - Handle transient failures (network, database)
 * - Manual intervention for business-critical messages
 * - Testing and validation of fixes
 *
 * SAFETY FEATURES:
 * - Message deduplication (prevent double-processing)
 * - Rate limiting (prevent overwhelming target service)
 * - Dry-run mode (test without actual reprocessing)
 * - Rollback support (mark messages as failed if reprocessing fails)
 * - Comprehensive audit logging
 *
 * COMPLIANCE:
 * - SOC 2 - Audit trail for all reprocessing operations
 * - GDPR - Data retention and deletion policies
 * - PCI DSS - Secure handling of payment-related messages
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-10-05
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DlqReprocessingService {

    private final DlqMessageRepository dlqMessageRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ComprehensiveAuditService auditService;
    private final SecurityAuditLogger securityAuditLogger;
    private final MeterRegistry meterRegistry;

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 1000;
    private static final long RATE_LIMIT_DELAY_MS = 100; // 10 messages per second

    /**
     * Reprocess a single DLQ message
     *
     * @param messageId DLQ message ID
     * @param reprocessedBy User/system initiating reprocessing
     * @param options Reprocessing options
     * @return Reprocessing result
     */
    @Transactional
    public ReprocessingResult reprocessMessage(String messageId, String reprocessedBy, ReprocessingOptions options) {

        log.info("Reprocessing DLQ message: messageId={}, by={}", messageId, reprocessedBy);

        Timer.Sample timer = Timer.start(meterRegistry);

        try {
            // Load DLQ message
            Optional<DlqMessage> dlqMessageOpt = dlqMessageRepository.findById(messageId);
            if (dlqMessageOpt.isEmpty()) {
                return ReprocessingResult.failure(messageId, "Message not found in DLQ");
            }

            DlqMessage dlqMessage = dlqMessageOpt.get();

            // Check if message is eligible for reprocessing
            ValidationResult validation = validateMessageForReprocessing(dlqMessage, options);
            if (!validation.isValid()) {
                return ReprocessingResult.failure(messageId, validation.getReason());
            }

            // Check for duplicate reprocessing
            if (isDuplicateReprocessing(dlqMessage, options)) {
                return ReprocessingResult.skipped(messageId, "Message already reprocessed recently");
            }

            // Dry-run mode - don't actually reprocess
            if (options.isDryRun()) {
                log.info("DRY-RUN: Would reprocess message: {}", messageId);
                return ReprocessingResult.dryRun(messageId, "Dry-run successful");
            }

            // Send message to original topic for reprocessing
            String targetTopic = determineTargetTopic(dlqMessage, options);
            kafkaTemplate.send(targetTopic, dlqMessage.getOriginalKey(), dlqMessage.getOriginalPayload())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send message for reprocessing: messageId={}", messageId, ex);
                        markReprocessingFailed(dlqMessage, ex.getMessage(), reprocessedBy);
                    } else {
                        log.info("Successfully sent message for reprocessing: messageId={}", messageId);
                        markReprocessingSuccessful(dlqMessage, reprocessedBy);
                    }
                });

            // Update DLQ message status
            dlqMessage.setReprocessingAttempts(dlqMessage.getReprocessingAttempts() + 1);
            dlqMessage.setLastReprocessingAttempt(Instant.now());
            dlqMessage.setLastReprocessedBy(reprocessedBy);
            dlqMessage.setStatus("REPROCESSING");
            dlqMessageRepository.save(dlqMessage);

            // Audit log
            auditReprocessing(dlqMessage, reprocessedBy, "SINGLE_MESSAGE");

            // Metrics
            incrementReprocessingMetrics("single", "success");
            timer.stop(meterRegistry.timer("dlq.reprocessing.duration", "type", "single"));

            return ReprocessingResult.success(messageId, targetTopic);

        } catch (Exception e) {
            log.error("Error reprocessing DLQ message: messageId={}", messageId, e);
            incrementReprocessingMetrics("single", "error");
            timer.stop(meterRegistry.timer("dlq.reprocessing.duration", "type", "single", "status", "error"));
            return ReprocessingResult.failure(messageId, "Reprocessing error: " + e.getMessage());
        }
    }

    /**
     * Batch reprocess DLQ messages
     *
     * @param criteria Filter criteria for selecting messages
     * @param reprocessedBy User/system initiating reprocessing
     * @param options Reprocessing options
     * @return Batch reprocessing result with progress tracking
     */
    @Async
    @Transactional
    public CompletableFuture<BatchReprocessingResult> reprocessMessagesBatch(
            DlqFilterCriteria criteria,
            String reprocessedBy,
            ReprocessingOptions options) {

        log.info("Starting batch DLQ reprocessing: criteria={}, by={}", criteria, reprocessedBy);

        String batchId = UUID.randomUUID().toString();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);
        List<String> failedMessageIds = Collections.synchronizedList(new ArrayList<>());

        Timer.Sample timer = Timer.start(meterRegistry);

        try {
            // Query messages based on criteria
            Page<DlqMessage> messages = queryDlqMessages(criteria, options.getBatchSize());

            int totalMessages = (int) messages.getTotalElements();
            log.info("Batch reprocessing {} DLQ messages: batchId={}", totalMessages, batchId);

            // Security audit for batch operation
            securityAuditLogger.logSecurityEvent(
                "DLQ_BATCH_REPROCESSING_STARTED",
                reprocessedBy,
                String.format("Batch DLQ reprocessing started: %d messages", totalMessages),
                Map.of(
                    "batchId", batchId,
                    "messageCount", totalMessages,
                    "criteria", criteria.toString(),
                    "dryRun", options.isDryRun()
                )
            );

            // Process each message
            for (DlqMessage message : messages) {

                // Rate limiting
                if (options.isRateLimitEnabled()) {
                    Thread.sleep(RATE_LIMIT_DELAY_MS);
                }

                // Reprocess message
                ReprocessingResult result = reprocessMessage(
                    message.getId(),
                    reprocessedBy,
                    options
                );

                // Track results
                switch (result.getStatus()) {
                    case SUCCESS:
                        successCount.incrementAndGet();
                        break;
                    case FAILURE:
                        failureCount.incrementAndGet();
                        failedMessageIds.add(message.getId());
                        break;
                    case SKIPPED:
                        skippedCount.incrementAndGet();
                        break;
                    default:
                        break;
                }

                // Log progress every 100 messages
                int processed = successCount.get() + failureCount.get() + skippedCount.get();
                if (processed % 100 == 0) {
                    log.info("Batch reprocessing progress: {}/{} messages processed", processed, totalMessages);
                }
            }

            // Create batch result
            BatchReprocessingResult batchResult = BatchReprocessingResult.builder()
                .batchId(batchId)
                .totalMessages(totalMessages)
                .successCount(successCount.get())
                .failureCount(failureCount.get())
                .skippedCount(skippedCount.get())
                .failedMessageIds(failedMessageIds)
                .reprocessedBy(reprocessedBy)
                .startTime(Instant.now().minusMillis(timer.stop(meterRegistry.timer("dlq.batch.reprocessing.duration"))))
                .endTime(Instant.now())
                .build();

            // Audit log for batch completion
            auditBatchReprocessing(batchResult, criteria);

            // Security audit
            securityAuditLogger.logSecurityEvent(
                "DLQ_BATCH_REPROCESSING_COMPLETED",
                reprocessedBy,
                String.format("Batch DLQ reprocessing completed: %d success, %d failures",
                    successCount.get(), failureCount.get()),
                Map.of(
                    "batchId", batchId,
                    "totalMessages", totalMessages,
                    "successCount", successCount.get(),
                    "failureCount", failureCount.get(),
                    "skippedCount", skippedCount.get()
                )
            );

            // Metrics
            incrementReprocessingMetrics("batch", "completed");

            log.info("Batch DLQ reprocessing completed: batchId={}, success={}, failures={}, skipped={}",
                batchId, successCount.get(), failureCount.get(), skippedCount.get());

            return CompletableFuture.completedFuture(batchResult);

        } catch (Exception e) {
            log.error("Error in batch DLQ reprocessing: batchId={}", batchId, e);
            incrementReprocessingMetrics("batch", "error");

            securityAuditLogger.logSecurityEvent(
                "DLQ_BATCH_REPROCESSING_FAILED",
                reprocessedBy,
                String.format("Batch DLQ reprocessing failed: %s", e.getMessage()),
                Map.of("batchId", batchId, "error", e.getMessage())
            );

            return CompletableFuture.completedFuture(
                BatchReprocessingResult.error(batchId, e.getMessage())
            );
        }
    }

    /**
     * Get DLQ message details for inspection
     */
    public DlqMessageDetails getMessageDetails(String messageId) {
        Optional<DlqMessage> dlqMessageOpt = dlqMessageRepository.findById(messageId);
        if (dlqMessageOpt.isEmpty()) {
            throw new IllegalArgumentException("DLQ message not found: " + messageId);
        }

        DlqMessage message = dlqMessageOpt.get();

        return DlqMessageDetails.builder()
            .messageId(message.getId())
            .originalTopic(message.getOriginalTopic())
            .originalKey(message.getOriginalKey())
            .originalPayload(message.getOriginalPayload())
            .errorMessage(message.getErrorMessage())
            .stackTrace(message.getStackTrace())
            .failureTimestamp(message.getFailureTimestamp())
            .reprocessingAttempts(message.getReprocessingAttempts())
            .lastReprocessingAttempt(message.getLastReprocessingAttempt())
            .lastReprocessedBy(message.getLastReprocessedBy())
            .status(message.getStatus())
            .metadata(message.getMetadata())
            .build();
    }

    /**
     * Query DLQ messages with filters
     */
    public Page<DlqMessage> queryDlqMessages(DlqFilterCriteria criteria, int pageSize) {
        PageRequest pageRequest = PageRequest.of(
            0,
            Math.min(pageSize, MAX_BATCH_SIZE),
            Sort.by(Sort.Direction.ASC, "failureTimestamp")
        );

        if (criteria.getTopicPattern() != null) {
            return dlqMessageRepository.findByTopicPattern(criteria.getTopicPattern(), pageRequest);
        }

        if (criteria.getErrorPattern() != null) {
            return dlqMessageRepository.findByErrorPattern(criteria.getErrorPattern(), pageRequest);
        }

        if (criteria.getStatus() != null) {
            // Convert String status to EscalationLevel enum
            try {
                DlqMessage.EscalationLevel statusLevel = DlqMessage.EscalationLevel.valueOf(criteria.getStatus().toUpperCase());
                return dlqMessageRepository.findByStatus(statusLevel, pageRequest);
            } catch (IllegalArgumentException e) {
                // Invalid status, return empty page
                return Page.empty(pageRequest);
            }
        }

        if (criteria.getFromDate() != null && criteria.getToDate() != null) {
            return dlqMessageRepository.findByTimestampRange(
                criteria.getFromDate(),
                criteria.getToDate(),
                pageRequest
            );
        }

        return dlqMessageRepository.findAll(pageRequest);
    }

    /**
     * Get DLQ statistics
     */
    public DlqStatistics getStatistics() {
        // Convert Map<EscalationLevel, Long> to Map<String, Long>
        Map<DlqMessage.EscalationLevel, Long> statusMap = dlqMessageRepository.countByStatus();
        Map<String, Long> statusStringMap = new java.util.HashMap<>();
        if (statusMap != null) {
            statusMap.forEach((level, count) -> statusStringMap.put(level.name(), count));
        }

        return DlqStatistics.builder()
            .totalMessages(dlqMessageRepository.count())
            .byStatus(statusStringMap)
            .byTopic(dlqMessageRepository.countByTopic())
            .oldestMessage(dlqMessageRepository.findOldestMessage().orElse(null))
            .averageReprocessingAttempts(dlqMessageRepository.averageReprocessingAttempts())
            .reprocessingSuccessRate(dlqMessageRepository.calculateSuccessRate())
            .build();
    }

    // Helper methods

    private ValidationResult validateMessageForReprocessing(DlqMessage message, ReprocessingOptions options) {
        // Check max reprocessing attempts
        if (message.getReprocessingAttempts() >= options.getMaxAttempts()) {
            return ValidationResult.invalid(
                String.format("Message exceeded max reprocessing attempts (%d)", options.getMaxAttempts())
            );
        }

        // Check message age
        if (options.getMaxAgeHours() > 0) {
            long ageHours = java.time.Duration.between(
                message.getFailureTimestamp(),
                Instant.now()
            ).toHours();

            if (ageHours > options.getMaxAgeHours()) {
                return ValidationResult.invalid(
                    String.format("Message too old (%d hours, max: %d)", ageHours, options.getMaxAgeHours())
                );
            }
        }

        // Check if message is already being reprocessed
        if ("REPROCESSING".equals(message.getStatus())) {
            return ValidationResult.invalid("Message is currently being reprocessed");
        }

        return ValidationResult.valid();
    }

    private boolean isDuplicateReprocessing(DlqMessage message, ReprocessingOptions options) {
        if (message.getLastReprocessingAttempt() == null) {
            return false;
        }

        long minutesSinceLastAttempt = java.time.Duration.between(
            message.getLastReprocessingAttempt(),
            Instant.now()
        ).toMinutes();

        return minutesSinceLastAttempt < options.getMinutesBeforeRetry();
    }

    private String determineTargetTopic(DlqMessage message, ReprocessingOptions options) {
        if (options.getTargetTopic() != null) {
            return options.getTargetTopic();
        }

        // Remove .DLQ suffix to get original topic
        String originalTopic = message.getOriginalTopic();
        if (originalTopic.endsWith(".DLQ")) {
            return originalTopic.substring(0, originalTopic.length() - 4);
        }

        return originalTopic;
    }

    private void markReprocessingSuccessful(DlqMessage message, String reprocessedBy) {
        message.setStatus("REPROCESSED_SUCCESS");
        message.setReprocessedAt(Instant.now());
        dlqMessageRepository.save(message);
    }

    private void markReprocessingFailed(DlqMessage message, String errorMessage, String reprocessedBy) {
        message.setStatus("REPROCESSED_FAILED");
        message.setLastReprocessingError(errorMessage);
        dlqMessageRepository.save(message);
    }

    private void auditReprocessing(DlqMessage message, String reprocessedBy, String type) {
        auditService.auditCriticalComplianceEvent(
            "DLQ_MESSAGE_REPROCESSED",
            reprocessedBy,
            "DLQ message reprocessed",
            Map.of(
                "messageId", message.getId(),
                "originalTopic", message.getOriginalTopic(),
                "reprocessingType", type,
                "reprocessingAttempt", message.getReprocessingAttempts()
            )
        );
    }

    private void auditBatchReprocessing(BatchReprocessingResult result, DlqFilterCriteria criteria) {
        auditService.auditCriticalComplianceEvent(
            "DLQ_BATCH_REPROCESSING",
            result.getReprocessedBy(),
            "DLQ batch reprocessing completed",
            Map.of(
                "batchId", result.getBatchId(),
                "totalMessages", result.getTotalMessages(),
                "successCount", result.getSuccessCount(),
                "failureCount", result.getFailureCount(),
                "criteria", criteria.toString()
            )
        );
    }

    private void incrementReprocessingMetrics(String type, String status) {
        Counter.builder("dlq.reprocessing")
            .tag("type", type)
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }

    // DTOs and supporting classes

    @lombok.Data
    @lombok.Builder
    public static class ReprocessingOptions {
        @lombok.Builder.Default
        private boolean dryRun = false;
        @lombok.Builder.Default
        private int maxAttempts = 3;
        @lombok.Builder.Default
        private int maxAgeHours = 72; // 3 days
        @lombok.Builder.Default
        private int minutesBeforeRetry = 30;
        @lombok.Builder.Default
        private int batchSize = DEFAULT_BATCH_SIZE;
        @lombok.Builder.Default
        private boolean rateLimitEnabled = true;
        private String targetTopic;
    }

    @lombok.Data
    @lombok.Builder
    public static class DlqFilterCriteria {
        private String topicPattern;
        private String errorPattern;
        private String status;
        private Instant fromDate;
        private Instant toDate;
    }

    @lombok.Data
    @lombok.Builder
    public static class ReprocessingResult {
        private String messageId;
        private ReprocessingStatus status;
        private String reason;
        private String targetTopic;

        public static ReprocessingResult success(String messageId, String targetTopic) {
            return ReprocessingResult.builder()
                .messageId(messageId)
                .status(ReprocessingStatus.SUCCESS)
                .targetTopic(targetTopic)
                .build();
        }

        public static ReprocessingResult failure(String messageId, String reason) {
            return ReprocessingResult.builder()
                .messageId(messageId)
                .status(ReprocessingStatus.FAILURE)
                .reason(reason)
                .build();
        }

        public static ReprocessingResult skipped(String messageId, String reason) {
            return ReprocessingResult.builder()
                .messageId(messageId)
                .status(ReprocessingStatus.SKIPPED)
                .reason(reason)
                .build();
        }

        public static ReprocessingResult dryRun(String messageId, String reason) {
            return ReprocessingResult.builder()
                .messageId(messageId)
                .status(ReprocessingStatus.DRY_RUN)
                .reason(reason)
                .build();
        }
    }

    public enum ReprocessingStatus {
        SUCCESS, FAILURE, SKIPPED, DRY_RUN
    }

    @lombok.Data
    @lombok.Builder
    public static class BatchReprocessingResult {
        private String batchId;
        private int totalMessages;
        private int successCount;
        private int failureCount;
        private int skippedCount;
        private List<String> failedMessageIds;
        private String reprocessedBy;
        private Instant startTime;
        private Instant endTime;
        private String errorMessage;

        public static BatchReprocessingResult error(String batchId, String errorMessage) {
            return BatchReprocessingResult.builder()
                .batchId(batchId)
                .errorMessage(errorMessage)
                .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class DlqMessageDetails {
        private String messageId;
        private String originalTopic;
        private String originalKey;
        private Object originalPayload;
        private String errorMessage;
        private String stackTrace;
        private Instant failureTimestamp;
        private int reprocessingAttempts;
        private Instant lastReprocessingAttempt;
        private String lastReprocessedBy;
        private String status;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    public static class DlqStatistics {
        private long totalMessages;
        private Map<String, Long> byStatus;
        private Map<String, Long> byTopic;
        private Instant oldestMessage;
        private double averageReprocessingAttempts;
        private double reprocessingSuccessRate;
    }

    private static class ValidationResult {
        private final boolean valid;
        private final String reason;

        private ValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }

        public boolean isValid() { return valid; }
        public String getReason() { return reason; }
    }
}
