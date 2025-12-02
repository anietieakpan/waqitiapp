package com.waqiti.payment.kafka;

import com.waqiti.common.events.ClearingHouseErrorEvent;
import com.waqiti.payment.domain.ClearingHouseError;
import com.waqiti.payment.repository.ClearingHouseErrorRepository;
import com.waqiti.payment.service.ClearingHouseService;
import com.waqiti.payment.service.SettlementService;
import com.waqiti.payment.service.ClearingRecoveryService;
import com.waqiti.payment.metrics.ClearingMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import java.math.BigDecimal;

@Component
@Slf4j
@RequiredArgsConstructor
public class ClearingHouseErrorsConsumer {

    private final ClearingHouseErrorRepository errorRepository;
    private final ClearingHouseService clearingHouseService;
    private final SettlementService settlementService;
    private final ClearingRecoveryService recoveryService;
    private final ClearingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Financial impact tracking
    private final AtomicLong totalAffectedAmount = new AtomicLong(0);
    private final AtomicLong clearingErrorCount = new AtomicLong(0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter financialImpactCounter;
    private Timer processingTimer;
    private Gauge affectedAmountGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("clearing_house_errors_processed_total")
            .description("Total number of successfully processed clearing house error events")
            .register(meterRegistry);
        errorCounter = Counter.builder("clearing_house_errors_errors_total")
            .description("Total number of clearing house error processing errors")
            .register(meterRegistry);
        financialImpactCounter = Counter.builder("clearing_house_errors_financial_impact")
            .description("Financial impact of clearing house errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("clearing_house_errors_processing_duration")
            .description("Time taken to process clearing house error events")
            .register(meterRegistry);
        affectedAmountGauge = Gauge.builder("clearing_house_affected_amount_total")
            .description("Total amount affected by clearing house errors")
            .register(meterRegistry, totalAffectedAmount, AtomicLong::get);
    }

    @KafkaListener(
        topics = {"clearing-house-errors", "settlement-clearing-failures", "clearing-timeout-events"},
        groupId = "clearing-house-errors-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "clearing-house-errors", fallbackMethod = "handleClearingErrorFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleClearingHouseErrorEvent(
            @Payload ClearingHouseErrorEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("clearing-error-%s-p%d-o%d", event.getClearingHouseId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getClearingHouseId(), event.getErrorType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing clearing house error: clearingHouseId={}, errorType={}, errorCode={}, affectedTransactions={}",
                event.getClearingHouseId(), event.getErrorType(), event.getErrorCode(), event.getAffectedTransactionCount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Financial impact assessment
            assessFinancialImpact(event.getAffectedAmount(), event.getErrorType(), correlationId);

            switch (event.getErrorType()) {
                case CONNECTIVITY_FAILURE:
                    handleConnectivityFailure(event, correlationId);
                    break;

                case MESSAGE_FORMAT_ERROR:
                    handleMessageFormatError(event, correlationId);
                    break;

                case SETTLEMENT_REJECTION:
                    handleSettlementRejection(event, correlationId);
                    break;

                case BATCH_PROCESSING_ERROR:
                    handleBatchProcessingError(event, correlationId);
                    break;

                case CUTOFF_TIME_MISSED:
                    handleCutoffTimeMissed(event, correlationId);
                    break;

                case INSUFFICIENT_LIQUIDITY:
                    handleInsufficientLiquidity(event, correlationId);
                    break;

                case REGULATORY_HOLD:
                    handleRegulatoryHold(event, correlationId);
                    break;

                case SYSTEM_MAINTENANCE:
                    handleSystemMaintenance(event, correlationId);
                    break;

                case DUPLICATE_BATCH:
                    handleDuplicateBatch(event, correlationId);
                    break;

                case AUTHENTICATION_FAILURE:
                    handleAuthenticationFailure(event, correlationId);
                    break;

                default:
                    log.warn("Unknown clearing house error type: {}", event.getErrorType());
                    handleGenericClearingError(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logClearingEvent("CLEARING_HOUSE_ERROR_PROCESSED", event.getClearingHouseId(),
                Map.of("errorType", event.getErrorType(), "errorCode", event.getErrorCode(),
                    "affectedTransactions", event.getAffectedTransactionCount(),
                    "affectedAmount", event.getAffectedAmount(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process clearing house error event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("clearing-error-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleClearingErrorFallback(
            ClearingHouseErrorEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("clearing-error-fallback-%s-p%d-o%d", event.getClearingHouseId(), partition, offset);

        log.error("Circuit breaker fallback triggered for clearing error: clearingHouseId={}, error={}",
            event.getClearingHouseId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("clearing-house-errors-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for high-impact errors
        if (event.getAffectedTransactionCount() > 50 ||
            event.getAffectedAmount().compareTo(BigDecimal.valueOf(1000000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical Clearing House Error - Circuit Breaker Triggered",
                    String.format("Clearing house %s error affecting %d transactions ($%,.2f): %s",
                        event.getClearingHouseId(), event.getAffectedTransactionCount(),
                        event.getAffectedAmount(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltClearingErrorEvent(
            @Payload ClearingHouseErrorEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-clearing-error-%s-%d", event.getClearingHouseId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Clearing error permanently failed: clearingHouseId={}, topic={}, error={}",
            event.getClearingHouseId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logClearingEvent("CLEARING_ERROR_DLT_EVENT", event.getClearingHouseId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "errorType", event.getErrorType(), "affectedAmount", event.getAffectedAmount(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Clearing House Error Dead Letter Event",
                String.format("Clearing house %s error sent to DLT (Impact: %d transactions, $%,.2f): %s",
                    event.getClearingHouseId(), event.getAffectedTransactionCount(),
                    event.getAffectedAmount(), exceptionMessage),
                Map.of("clearingHouseId", event.getClearingHouseId(), "topic", topic,
                    "correlationId", correlationId, "affectedAmount", event.getAffectedAmount())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessFinancialImpact(BigDecimal affectedAmount, String errorType, String correlationId) {
        long amountCents = affectedAmount.multiply(BigDecimal.valueOf(100)).longValue();
        totalAffectedAmount.addAndGet(amountCents);
        clearingErrorCount.incrementAndGet();
        financialImpactCounter.increment(amountCents);

        // Alert if cumulative affected amount exceeds threshold
        if (totalAffectedAmount.get() > 2000000000L) { // $20M in cents
            try {
                notificationService.sendExecutiveAlert(
                    "CRITICAL: Clearing House Errors Exceed $20M Impact",
                    String.format("Cumulative clearing error impact: $%,.2f. Settlement operations review required.",
                        totalAffectedAmount.get() / 100.0),
                    "CRITICAL"
                );
                // Reset counter after alert
                totalAffectedAmount.set(0);
            } catch (Exception ex) {
                log.error("Failed to send financial impact alert: {}", ex.getMessage());
            }
        }

        // High-impact error alert
        if (affectedAmount.compareTo(BigDecimal.valueOf(1000000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Impact Clearing House Error",
                    String.format("Clearing error impact: $%,.2f, Type: %s", affectedAmount, errorType),
                    "HIGH"
                );
            } catch (Exception ex) {
                log.error("Failed to send high-impact error alert: {}", ex.getMessage());
            }
        }
    }

    private void handleConnectivityFailure(ClearingHouseErrorEvent event, String correlationId) {
        createErrorRecord(event, "CONNECTIVITY_FAILURE", correlationId);

        // Switch to backup clearing house
        kafkaTemplate.send("clearing-house-failover-requests", Map.of(
            "primaryClearingHouse", event.getClearingHouseId(),
            "failoverType", "CONNECTIVITY_FAILURE",
            "affectedBatches", event.getAffectedBatchIds(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Test connectivity
        kafkaTemplate.send("clearing-house-connectivity-tests", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "testType", "CONNECTIVITY_DIAGNOSTIC",
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule retry
        kafkaTemplate.send("clearing-batch-retry-schedules", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "retryType", "CONNECTIVITY_RECOVERY",
            "affectedBatches", event.getAffectedBatchIds(),
            "scheduleDelay", 900000, // 15 minutes
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Clearing House Connectivity Failure",
            String.format("Clearing house %s connectivity failed, initiating failover",
                event.getClearingHouseId()),
            "CRITICAL");

        metricsService.recordClearingError("CONNECTIVITY_FAILURE", event.getAffectedAmount());
    }

    private void handleMessageFormatError(ClearingHouseErrorEvent event, String correlationId) {
        createErrorRecord(event, "MESSAGE_FORMAT_ERROR", correlationId);

        // Validate message format
        kafkaTemplate.send("message-format-validation", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "messageType", event.getMessageType(),
            "invalidMessage", event.getInvalidMessage(),
            "expectedFormat", event.getExpectedFormat(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Reformat and resend messages
        kafkaTemplate.send("message-reformatting-requests", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "affectedMessages", event.getAffectedMessageIds(),
            "reformatType", "STANDARD_COMPLIANCE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Clearing Message Format Error",
            String.format("Message format error for clearing house %s, reformatting messages",
                event.getClearingHouseId()),
            "HIGH");

        metricsService.recordClearingError("MESSAGE_FORMAT_ERROR", event.getAffectedAmount());
    }

    private void handleSettlementRejection(ClearingHouseErrorEvent event, String correlationId) {
        createErrorRecord(event, "SETTLEMENT_REJECTION", correlationId);

        // Analyze rejection reason
        kafkaTemplate.send("settlement-rejection-analysis", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "rejectionCode", event.getRejectionCode(),
            "rejectionReason", event.getRejectionReason(),
            "affectedBatches", event.getAffectedBatchIds(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Correct and resubmit
        kafkaTemplate.send("settlement-correction-requests", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "correctionType", "REJECTION_RESOLUTION",
            "affectedBatches", event.getAffectedBatchIds(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Settlement Rejected by Clearing House",
            String.format("Settlement rejected by %s: %s",
                event.getClearingHouseId(), event.getRejectionReason()),
            "HIGH");

        metricsService.recordClearingError("SETTLEMENT_REJECTION", event.getAffectedAmount());
    }

    private void handleBatchProcessingError(ClearingHouseErrorEvent event, String correlationId) {
        createErrorRecord(event, "BATCH_PROCESSING_ERROR", correlationId);

        // Validate batch integrity
        kafkaTemplate.send("batch-integrity-validation", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "batchId", event.getBatchId(),
            "validationType", "COMPLETE_INTEGRITY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Split and reprocess batch
        kafkaTemplate.send("batch-splitting-requests", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "originalBatchId", event.getBatchId(),
            "splittingStrategy", "ERROR_ISOLATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClearingError("BATCH_PROCESSING_ERROR", event.getAffectedAmount());
    }

    private void handleCutoffTimeMissed(ClearingHouseErrorEvent event, String correlationId) {
        createErrorRecord(event, "CUTOFF_TIME_MISSED", correlationId);

        // Schedule for next available window
        kafkaTemplate.send("next-window-scheduling", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "missedCutoffTime", event.getCutoffTime(),
            "nextAvailableWindow", event.getNextCutoffTime(),
            "affectedBatches", event.getAffectedBatchIds(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check if express processing available
        kafkaTemplate.send("express-processing-checks", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "amount", event.getAffectedAmount(),
            "urgencyLevel", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Clearing Cutoff Time Missed",
            String.format("Missed cutoff time for clearing house %s, rescheduling batches",
                event.getClearingHouseId()),
            "MEDIUM");

        metricsService.recordClearingError("CUTOFF_TIME_MISSED", event.getAffectedAmount());
    }

    private void handleInsufficientLiquidity(ClearingHouseErrorEvent event, String correlationId) {
        createErrorRecord(event, "INSUFFICIENT_LIQUIDITY", correlationId);

        // Check liquidity sources
        kafkaTemplate.send("liquidity-source-checks", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "requiredAmount", event.getAffectedAmount(),
            "checkType", "ALTERNATIVE_SOURCES",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Request liquidity injection
        kafkaTemplate.send("liquidity-injection-requests", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "requestedAmount", event.getAffectedAmount(),
            "urgency", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Clearing House Insufficient Liquidity",
            String.format("Insufficient liquidity at clearing house %s: $%,.2f",
                event.getClearingHouseId(), event.getAffectedAmount()),
            "HIGH");

        metricsService.recordClearingError("INSUFFICIENT_LIQUIDITY", event.getAffectedAmount());
    }

    private void handleRegulatoryHold(ClearingHouseErrorEvent event, String correlationId) {
        createErrorRecord(event, "REGULATORY_HOLD", correlationId);

        // Create compliance case
        kafkaTemplate.send("compliance-case-creation", Map.of(
            "caseType", "CLEARING_REGULATORY_HOLD",
            "clearingHouseId", event.getClearingHouseId(),
            "holdReason", event.getHoldReason(),
            "affectedAmount", event.getAffectedAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Notify compliance team
        notificationService.sendComplianceAlert("Clearing Regulatory Hold",
            String.format("Regulatory hold on clearing house %s: %s",
                event.getClearingHouseId(), event.getHoldReason()),
            correlationId);

        metricsService.recordClearingError("REGULATORY_HOLD", event.getAffectedAmount());
    }

    private void handleSystemMaintenance(ClearingHouseErrorEvent event, String correlationId) {
        createErrorRecord(event, "SYSTEM_MAINTENANCE", correlationId);

        // Check maintenance schedule
        kafkaTemplate.send("maintenance-schedule-checks", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "maintenanceType", event.getMaintenanceType(),
            "estimatedDuration", event.getEstimatedDuration(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Route to alternative clearing house
        kafkaTemplate.send("clearing-house-routing", Map.of(
            "primaryClearingHouse", event.getClearingHouseId(),
            "routingType", "MAINTENANCE_BYPASS",
            "affectedBatches", event.getAffectedBatchIds(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClearingError("SYSTEM_MAINTENANCE", event.getAffectedAmount());
    }

    private void handleDuplicateBatch(ClearingHouseErrorEvent event, String correlationId) {
        createErrorRecord(event, "DUPLICATE_BATCH", correlationId);

        // Identify original batch
        kafkaTemplate.send("duplicate-batch-identification", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "duplicateBatchId", event.getBatchId(),
            "identificationType", "ORIGINAL_LOOKUP",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Cancel duplicate submission
        kafkaTemplate.send("batch-cancellation-requests", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "batchId", event.getBatchId(),
            "cancellationReason", "DUPLICATE_DETECTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClearingError("DUPLICATE_BATCH", BigDecimal.ZERO);
    }

    private void handleAuthenticationFailure(ClearingHouseErrorEvent event, String correlationId) {
        createErrorRecord(event, "AUTHENTICATION_FAILURE", correlationId);

        // Refresh authentication credentials
        kafkaTemplate.send("clearing-house-auth-refresh", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "authType", "CREDENTIAL_REFRESH",
            "priority", "IMMEDIATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Validate certificates
        kafkaTemplate.send("certificate-validation-requests", Map.of(
            "clearingHouseId", event.getClearingHouseId(),
            "certificateType", "CLEARING_AUTH",
            "validationType", "EXPIRATION_AND_VALIDITY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Clearing House Authentication Failure",
            String.format("Authentication failure for clearing house %s, refreshing credentials",
                event.getClearingHouseId()),
            "HIGH");

        metricsService.recordClearingError("AUTHENTICATION_FAILURE", event.getAffectedAmount());
    }

    private void handleGenericClearingError(ClearingHouseErrorEvent event, String correlationId) {
        createErrorRecord(event, "UNKNOWN_ERROR", correlationId);

        // Log for investigation
        auditService.logClearingEvent("UNKNOWN_CLEARING_ERROR", event.getClearingHouseId(),
            Map.of("errorType", event.getErrorType(), "errorCode", event.getErrorCode(),
                "errorMessage", event.getErrorMessage(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Clearing House Error",
            String.format("Unknown clearing error for %s: %s",
                event.getClearingHouseId(), event.getErrorMessage()),
            "HIGH");

        metricsService.recordClearingError("UNKNOWN", event.getAffectedAmount());
    }

    private void createErrorRecord(ClearingHouseErrorEvent event, String errorType, String correlationId) {
        try {
            ClearingHouseError error = ClearingHouseError.builder()
                .clearingHouseId(event.getClearingHouseId())
                .errorType(errorType)
                .errorCode(event.getErrorCode())
                .errorMessage(event.getErrorMessage())
                .affectedTransactionCount(event.getAffectedTransactionCount())
                .affectedAmount(event.getAffectedAmount())
                .batchId(event.getBatchId())
                .errorTime(LocalDateTime.now())
                .status("OPEN")
                .correlationId(correlationId)
                .build();

            errorRepository.save(error);
        } catch (Exception e) {
            log.error("Failed to create clearing house error record: {}", e.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}