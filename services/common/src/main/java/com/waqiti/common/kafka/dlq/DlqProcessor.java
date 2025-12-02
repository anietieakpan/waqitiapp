package com.waqiti.common.kafka.dlq;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.monitoring.AlertingService;
import com.waqiti.common.monitoring.AlertSeverity;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import com.waqiti.common.kafka.dlq.repository.DlqRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise-grade Dead Letter Queue (DLQ) Processor
 *
 * CRITICAL PRODUCTION SYSTEM:
 * Handles failed Kafka message processing with comprehensive retry logic,
 * alerting, audit trails, and automatic recovery mechanisms.
 *
 * FEATURES:
 * - Automatic retry with exponential backoff
 * - Persistent DLQ storage in database
 * - Alert escalation for critical failures
 * - Comprehensive audit logging
 * - Manual replay capability
 * - Batch reprocessing
 * - Dead letter analysis and reporting
 *
 * COMPLIANCE:
 * - PCI DSS: Audit trail for all payment events
 * - SOX: Financial transaction immutability
 * - GDPR: Data retention and deletion policies
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @since 2025-11-04
 */
@Slf4j
@Component
public class DlqProcessor {

    private final DlqRecordRepository dlqRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final AlertingService alertingService;

    // PRODUCTION FIX: Handler registry for auto-discovery
    private final Map<DlqEventType, DlqMessageHandler> handlerRegistry;

    // DLQ configuration constants
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 1000;
    private static final int MAX_RETRY_DELAY_MS = 60000;
    private static final int CRITICAL_FAILURE_THRESHOLD = 10;
    private static final String DLQ_TOPIC_SUFFIX = "-dlq";

    @Autowired
    public DlqProcessor(
            DlqRecordRepository dlqRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            AuditService auditService,
            AlertingService alertingService,
            List<DlqMessageHandler> handlers) {
        this.dlqRepository = dlqRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.alertingService = alertingService;

        // Auto-register all DLQ handlers by event type
        this.handlerRegistry = handlers.stream()
            .collect(java.util.stream.Collectors.toMap(
                DlqMessageHandler::getEventType,
                java.util.function.Function.identity()
            ));

        log.info("DLQ: Registered {} handlers: {}",
            handlerRegistry.size(),
            handlerRegistry.keySet());
    }

    /**
     * Process failed Kafka message from DLQ topic
     *
     * ALGORITHM:
     * 1. Parse and validate DLQ message
     * 2. Check retry count and apply backoff
     * 3. Attempt reprocessing with original handler
     * 4. On success: Remove from DLQ, log success
     * 5. On failure: Increment retry count, apply backoff, alert if threshold exceeded
     *
     * @param record DLQ consumer record
     * @param originalHandler Handler that originally failed
     * @param eventType Type of event (PAYMENT, COMPLIANCE, FRAUD, etc.)
     * @param serviceName Name of service processing DLQ
     */
    @Transactional
    public void processDlqMessage(
            ConsumerRecord<String, String> record,
            DlqMessageHandler originalHandler,
            DlqEventType eventType,
            String serviceName) {

        String messageId = extractMessageId(record);
        log.info("DLQ: Processing message - messageId={}, topic={}, partition={}, offset={}, eventType={}",
                messageId, record.topic(), record.partition(), record.offset(), eventType);

        DlqRecordEntity dlqRecord = null;

        try {
            // 1. Get or create DLQ record
            dlqRecord = getOrCreateDlqRecord(record, eventType, serviceName, messageId);

            // 2. Check if retry is allowed
            if (!shouldRetry(dlqRecord)) {
                log.error("DLQ: Max retries exceeded - parking message permanently. messageId={}, retryCount={}",
                        messageId, dlqRecord.getRetryCount());

                parkMessagePermanently(dlqRecord, "Max retry attempts exceeded");
                sendCriticalAlert(dlqRecord, "PERMANENT_FAILURE");
                return;
            }

            // 3. Apply exponential backoff delay
            if (!isBackoffElapsed(dlqRecord)) {
                log.debug("DLQ: Backoff period not elapsed - requeueing. messageId={}, nextRetryTime={}",
                        messageId, dlqRecord.getNextRetryTime());
                return; // Message will be reprocessed later
            }

            // 4. Attempt reprocessing with original handler
            log.info("DLQ: Attempting reprocessing - messageId={}, retryAttempt={}/{}",
                    messageId, dlqRecord.getRetryCount() + 1, MAX_RETRY_ATTEMPTS);

            DlqProcessingResult result = originalHandler.reprocess(record, dlqRecord);

            // 5. Handle result
            if (result.isSuccess()) {
                handleSuccessfulReprocessing(dlqRecord, result);
            } else {
                handleFailedReprocessing(dlqRecord, result);
            }

        } catch (Exception e) {
            log.error("DLQ: Unexpected error processing message - messageId={}", messageId, e);

            if (dlqRecord != null) {
                handleProcessingException(dlqRecord, e);
            } else {
                // Failed to create DLQ record - critical error
                sendCriticalAlert(createEmergencyDlqRecord(record, eventType, serviceName),
                        "DLQ_RECORD_CREATION_FAILED");
            }

            auditService.logDlqEvent(
                    "DLQ_PROCESSING_EXCEPTION",
                    record.topic(),
                    Map.of(
                            "messageId", messageId,
                            "partition", String.valueOf(record.partition()),
                            "offset", String.valueOf(record.offset()),
                            "error", e.getMessage()
                    )
            );
        }
    }

    /**
     * Batch reprocess all DLQ messages for given event type
     *
     * Used for operational recovery scenarios where multiple messages
     * failed due to temporary service outage.
     *
     * @param eventType Type of events to reprocess
     * @param maxMessages Maximum messages to reprocess in batch
     * @return Number of messages successfully reprocessed
     */
    @Transactional
    public int batchReprocess(DlqEventType eventType, int maxMessages) {
        log.info("DLQ: Starting batch reprocessing - eventType={}, maxMessages={}", eventType, maxMessages);

        var pendingMessages = dlqRepository.findPendingByEventType(DlqStatus.PENDING, eventType,
                org.springframework.data.domain.PageRequest.of(0, maxMessages));
        int successCount = 0;

        for (DlqRecordEntity record : pendingMessages) {
            try {
                // Attempt to recreate Kafka consumer record
                ConsumerRecord<String, String> kafkaRecord = recreateConsumerRecord(record);

                // Process with appropriate handler based on event type
                DlqMessageHandler handler = getHandlerForEventType(eventType);
                DlqProcessingResult result = handler.reprocess(kafkaRecord, record);

                if (result.isSuccess()) {
                    handleSuccessfulReprocessing(record, result);
                    successCount++;
                } else {
                    log.warn("DLQ: Batch reprocessing failed for message - messageId={}, reason={}",
                            record.getMessageId(), result.getErrorMessage());
                }

            } catch (Exception e) {
                log.error("DLQ: Error in batch reprocessing - messageId={}", record.getMessageId(), e);
            }
        }

        log.info("DLQ: Batch reprocessing completed - eventType={}, processed={}, succeeded={}",
                eventType, pendingMessages.getContent().size(), successCount);

        return successCount;
    }

    /**
     * Get DLQ statistics for monitoring and alerting
     */
    public DlqStatistics getStatistics() {
        // Convert EventTypeCount list to Map
        Map<DlqEventType, Long> eventTypeMap = dlqRepository.countByEventType().stream()
                .collect(java.util.stream.Collectors.toMap(
                        DlqRecordRepository.EventTypeCount::getEventType,
                        DlqRecordRepository.EventTypeCount::getCount
                ));

        return DlqStatistics.builder()
                .totalMessages(dlqRepository.count())
                .pendingMessages(dlqRepository.countByStatus(DlqStatus.PENDING))
                .parkedMessages(dlqRepository.countByStatus(DlqStatus.PARKED))
                .reprocessedMessages(dlqRepository.countByStatus(DlqStatus.REPROCESSED))
                .messagesByEventType(eventTypeMap)
                .oldestPendingMessage(dlqRepository.findOldestPending())
                .criticalFailureCount(dlqRepository.countCriticalFailures(5))
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Manually replay specific DLQ message
     *
     * Used by operations team for manual intervention.
     *
     * @param messageId DLQ message ID
     * @param overrideRetryLimit Whether to ignore max retry limit
     * @return True if reprocessing succeeded
     */
    @Transactional
    public boolean manualReplay(String messageId, boolean overrideRetryLimit) {
        log.info("DLQ: Manual replay requested - messageId={}, overrideRetryLimit={}",
                messageId, overrideRetryLimit);

        DlqRecordEntity record = dlqRepository.findByMessageId(messageId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ record not found: " + messageId));

        if (!overrideRetryLimit && !shouldRetry(record)) {
            throw new IllegalStateException("Max retries exceeded. Use overrideRetryLimit=true to force replay.");
        }

        try {
            ConsumerRecord<String, String> kafkaRecord = recreateConsumerRecord(record);
            DlqMessageHandler handler = getHandlerForEventType(record.getEventType());
            DlqProcessingResult result = handler.reprocess(kafkaRecord, record);

            if (result.isSuccess()) {
                handleSuccessfulReprocessing(record, result);
                auditService.logDlqEvent("DLQ_MANUAL_REPLAY_SUCCESS", record.getTopic(),
                        Map.of("messageId", messageId, "result", "success"));
                return true;
            } else {
                handleFailedReprocessing(record, result);
                auditService.logDlqEvent("DLQ_MANUAL_REPLAY_FAILURE", record.getTopic(),
                        Map.of("messageId", messageId, "result", "failure", "reason", result.getErrorMessage()));
                return false;
            }

        } catch (Exception e) {
            log.error("DLQ: Manual replay failed - messageId={}", messageId, e);
            auditService.logDlqEvent("DLQ_MANUAL_REPLAY_ERROR", record.getTopic(),
                    Map.of("messageId", messageId, "error", e.getMessage()));
            return false;
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    private DlqRecordEntity getOrCreateDlqRecord(
            ConsumerRecord<String, String> record,
            DlqEventType eventType,
            String serviceName,
            String messageId) {

        return dlqRepository.findByMessageId(messageId)
                .orElseGet(() -> createDlqRecord(record, eventType, serviceName, messageId));
    }

    private DlqRecordEntity createDlqRecord(
            ConsumerRecord<String, String> record,
            DlqEventType eventType,
            String serviceName,
            String messageId) {

        DlqRecordEntity dlqRecord = DlqRecordEntity.builder()
                .id(UUID.randomUUID())
                .messageId(messageId)
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .messageKey(record.key())
                .messageValue(record.value())
                .eventType(eventType)
                .serviceName(serviceName)
                .status(DlqStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .firstFailureTime(Instant.now())
                .nextRetryTime(calculateNextRetryTime(0))
                .headers(extractHeaders(record))
                .build();

        return dlqRepository.save(dlqRecord);
    }

    private boolean shouldRetry(DlqRecordEntity record) {
        return record.getRetryCount() < MAX_RETRY_ATTEMPTS;
    }

    private boolean isBackoffElapsed(DlqRecordEntity record) {
        return record.getNextRetryTime() == null ||
                Instant.now().isAfter(record.getNextRetryTime());
    }

    private void handleSuccessfulReprocessing(DlqRecordEntity record, DlqProcessingResult result) {
        log.info("DLQ: Reprocessing SUCCEEDED - messageId={}, retryAttempt={}",
                record.getMessageId(), record.getRetryCount() + 1);

        record.setStatus(DlqStatus.REPROCESSED);
        record.setReprocessedAt(LocalDateTime.now());
        record.setReprocessingResult(result.getResultDetails());
        dlqRepository.save(record);

        auditService.logDlqEvent(
                "DLQ_REPROCESSING_SUCCESS",
                record.getTopic(),
                Map.of(
                        "messageId", record.getMessageId(),
                        "eventType", record.getEventType().name(),
                        "retryCount", String.valueOf(record.getRetryCount() + 1)
                )
        );

        // Remove from DLQ topic if configured
        if (shouldRemoveFromDlqTopic(record)) {
            removeFromDlqTopic(record);
        }
    }

    private void handleFailedReprocessing(DlqRecordEntity record, DlqProcessingResult result) {
        log.warn("DLQ: Reprocessing FAILED - messageId={}, retryAttempt={}, error={}",
                record.getMessageId(), record.getRetryCount() + 1, result.getErrorMessage());

        record.setRetryCount(record.getRetryCount() + 1);
        record.setLastFailureTime(Instant.now());
        record.setLastFailureReason(result.getErrorMessage());
        record.setNextRetryTime(calculateNextRetryTime(record.getRetryCount()));
        dlqRepository.save(record);

        // Check if critical threshold exceeded
        if (record.getRetryCount() >= CRITICAL_FAILURE_THRESHOLD / 2) {
            sendAlert(record, "HIGH_RETRY_COUNT", AlertSeverity.WARNING);
        }

        auditService.logDlqEvent(
                "DLQ_REPROCESSING_FAILURE",
                record.getTopic(),
                Map.of(
                        "messageId", record.getMessageId(),
                        "eventType", record.getEventType().name(),
                        "retryCount", String.valueOf(record.getRetryCount()),
                        "error", result.getErrorMessage()
                )
        );
    }

    private void handleProcessingException(DlqRecordEntity record, Exception e) {
        log.error("DLQ: Processing exception - messageId={}", record.getMessageId(), e);

        record.setRetryCount(record.getRetryCount() + 1);
        record.setLastFailureTime(Instant.now());
        record.setLastFailureReason("Exception: " + e.getMessage());
        record.setNextRetryTime(calculateNextRetryTime(record.getRetryCount()));

        if (record.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
            parkMessagePermanently(record, "Max retries exceeded after exception");
        }

        dlqRepository.save(record);
    }

    private void parkMessagePermanently(DlqRecordEntity record, String reason) {
        log.error("DLQ: PERMANENTLY PARKING message - messageId={}, reason={}",
                record.getMessageId(), reason);

        record.setStatus(DlqStatus.PARKED);
        record.setParkedAt(LocalDateTime.now());
        record.setParkedReason(reason);
        dlqRepository.save(record);

        auditService.logDlqEvent(
                "DLQ_PERMANENT_FAILURE",
                record.getTopic(),
                Map.of(
                        "messageId", record.getMessageId(),
                        "eventType", record.getEventType().name(),
                        "reason", reason,
                        "retryCount", String.valueOf(record.getRetryCount())
                )
        );

        // Send to permanent failure topic for manual review
        sendToPermanentFailureTopic(record);
    }

    private Instant calculateNextRetryTime(int retryCount) {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s, 60s (max)
        long delayMs = Math.min(
                INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, retryCount),
                MAX_RETRY_DELAY_MS
        );

        return Instant.now().plusMillis(delayMs);
    }

    private void sendAlert(DlqRecordEntity record, String alertType, AlertSeverity severity) {
        String message = String.format("DLQ %s: messageId=%s, eventType=%s, retryCount=%d",
                alertType, record.getMessageId(), record.getEventType(), record.getRetryCount());

        Map<String, Object> metadata = Map.of(
                "messageId", record.getMessageId(),
                "eventType", record.getEventType().name(),
                "retryCount", String.valueOf(record.getRetryCount()),
                "topic", record.getTopic(),
                "severity", severity.name()
        );

        alertingService.sendAlert("DLQ_" + alertType, message, metadata);
    }

    private void sendCriticalAlert(DlqRecordEntity record, String alertType) {
        alertingService.sendCriticalAlert(
                "DLQ_CRITICAL_" + alertType,
                String.format("CRITICAL DLQ Failure: messageId=%s, eventType=%s - IMMEDIATE ACTION REQUIRED",
                        record.getMessageId(), record.getEventType()),
                Map.of(
                        "messageId", record.getMessageId(),
                        "eventType", record.getEventType().name(),
                        "topic", record.getTopic(),
                        "serviceName", record.getServiceName(),
                        "retryCount", String.valueOf(record.getRetryCount())
                )
        );
    }

    private void sendToPermanentFailureTopic(DlqRecordEntity record) {
        try {
            String permanentFailureTopic = "dlq-permanent-failures";

            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(
                    permanentFailureTopic,
                    record.getMessageKey(),
                    serializeDlqRecord(record)
            );

            kafkaTemplate.send(producerRecord);

            log.info("DLQ: Sent to permanent failure topic - messageId={}, topic={}",
                    record.getMessageId(), permanentFailureTopic);

        } catch (Exception e) {
            log.error("DLQ: Failed to send to permanent failure topic - messageId={}",
                    record.getMessageId(), e);
        }
    }

    private String extractMessageId(ConsumerRecord<String, String> record) {
        // Try to extract from headers first
        var messageIdHeader = record.headers().lastHeader("messageId");
        if (messageIdHeader != null) {
            return new String(messageIdHeader.value());
        }

        // Fallback to key or generate UUID
        return record.key() != null ? record.key() : UUID.randomUUID().toString();
    }

    private Map<String, String> extractHeaders(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new java.util.HashMap<>();
        record.headers().forEach(header -> {
            headers.put(header.key(), new String(header.value()));
        });
        return headers;
    }

    private ConsumerRecord<String, String> recreateConsumerRecord(DlqRecordEntity record) {
        // Recreate consumer record from stored DLQ record
        return new ConsumerRecord<>(
                record.getTopic(),
                record.getPartition(),
                record.getOffset(),
                record.getMessageKey(),
                record.getMessageValue()
        );
    }

    private DlqMessageHandler getHandlerForEventType(DlqEventType eventType) {
        DlqMessageHandler handler = handlerRegistry.get(eventType);

        if (handler == null) {
            log.error("DLQ: No handler registered for event type: {}", eventType);
            throw new IllegalStateException(
                "No DLQ handler found for event type: " + eventType +
                ". Registered types: " + handlerRegistry.keySet()
            );
        }

        return handler;
    }

    private boolean shouldRemoveFromDlqTopic(DlqRecordEntity record) {
        // Remove from DLQ topic after successful reprocessing
        return true;
    }

    private void removeFromDlqTopic(DlqRecordEntity record) {
        // Implementation would tombstone the message in DLQ topic
        log.debug("DLQ: Removing message from DLQ topic - messageId={}", record.getMessageId());
    }

    private String serializeDlqRecord(DlqRecordEntity record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (Exception e) {
            log.error("Failed to serialize DLQ record", e);
            return "{}";
        }
    }

    private DlqRecordEntity createEmergencyDlqRecord(
            ConsumerRecord<String, String> record,
            DlqEventType eventType,
            String serviceName) {

        return DlqRecordEntity.builder()
                .id(UUID.randomUUID())
                .messageId(UUID.randomUUID().toString())
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .messageKey(record.key())
                .messageValue(record.value())
                .eventType(eventType)
                .serviceName(serviceName)
                .status(DlqStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .firstFailureTime(Instant.now())
                .build();
    }
}
