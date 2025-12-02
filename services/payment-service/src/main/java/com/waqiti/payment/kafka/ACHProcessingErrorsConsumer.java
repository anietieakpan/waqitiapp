package com.waqiti.payment.kafka;

import com.waqiti.common.events.ACHProcessingErrorEvent;
import com.waqiti.payment.domain.ACHProcessingError;
import com.waqiti.payment.repository.ACHProcessingErrorRepository;
import com.waqiti.payment.service.ACHService;
import com.waqiti.payment.service.ACHBatchService;
import com.waqiti.payment.service.ACHReturnService;
import com.waqiti.payment.metrics.ACHMetricsService;
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
public class ACHProcessingErrorsConsumer {

    private final ACHProcessingErrorRepository errorRepository;
    private final ACHService achService;
    private final ACHBatchService batchService;
    private final ACHReturnService returnService;
    private final ACHMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Financial impact tracking
    private final AtomicLong totalACHErrorAmount = new AtomicLong(0);
    private final AtomicLong achErrorCount = new AtomicLong(0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter financialImpactCounter;
    private Timer processingTimer;
    private Gauge achErrorAmountGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("ach_processing_errors_processed_total")
            .description("Total number of successfully processed ACH processing error events")
            .register(meterRegistry);
        errorCounter = Counter.builder("ach_processing_errors_errors_total")
            .description("Total number of ACH processing error processing errors")
            .register(meterRegistry);
        financialImpactCounter = Counter.builder("ach_processing_errors_financial_impact")
            .description("Financial impact of ACH processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("ach_processing_errors_processing_duration")
            .description("Time taken to process ACH processing error events")
            .register(meterRegistry);
        achErrorAmountGauge = Gauge.builder("ach_processing_error_amount_total")
            .description("Total amount affected by ACH processing errors")
            .register(meterRegistry, totalACHErrorAmount, AtomicLong::get);
    }

    @KafkaListener(
        topics = {"ach-processing-errors", "ach-batch-failures", "ach-return-errors"},
        groupId = "ach-processing-errors-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "ach-processing-errors", fallbackMethod = "handleACHErrorFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleACHProcessingErrorEvent(
            @Payload ACHProcessingErrorEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("ach-error-%s-p%d-o%d", event.getBatchId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getBatchId(), event.getErrorCode(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing ACH processing error: batchId={}, errorCode={}, errorType={}, affectedTransactions={}",
                event.getBatchId(), event.getErrorCode(), event.getErrorType(), event.getAffectedTransactionCount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Financial impact assessment
            assessFinancialImpact(event.getTotalAmount(), event.getErrorCode(), correlationId);

            switch (event.getErrorType()) {
                case BATCH_VALIDATION_ERROR:
                    handleBatchValidationError(event, correlationId);
                    break;

                case NACHA_FORMAT_ERROR:
                    handleNACHAFormatError(event, correlationId);
                    break;

                case ROUTING_NUMBER_INVALID:
                    handleRoutingNumberInvalid(event, correlationId);
                    break;

                case ACCOUNT_NUMBER_INVALID:
                    handleAccountNumberInvalid(event, correlationId);
                    break;

                case INSUFFICIENT_FUNDS:
                    handleInsufficientFunds(event, correlationId);
                    break;

                case ACCOUNT_CLOSED:
                    handleAccountClosed(event, correlationId);
                    break;

                case RETURN_PROCESSING_ERROR:
                    handleReturnProcessingError(event, correlationId);
                    break;

                case DUPLICATE_ENTRY:
                    handleDuplicateEntry(event, correlationId);
                    break;

                case CUTOFF_TIME_MISSED:
                    handleCutoffTimeMissed(event, correlationId);
                    break;

                case REGULATORY_COMPLIANCE_ERROR:
                    handleRegulatoryComplianceError(event, correlationId);
                    break;

                default:
                    log.warn("Unknown ACH processing error type: {}", event.getErrorType());
                    handleGenericACHError(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logACHEvent("ACH_PROCESSING_ERROR_PROCESSED", event.getBatchId(),
                Map.of("errorType", event.getErrorType(), "errorCode", event.getErrorCode(),
                    "affectedTransactions", event.getAffectedTransactionCount(),
                    "totalAmount", event.getTotalAmount(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process ACH processing error event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("ach-error-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleACHErrorFallback(
            ACHProcessingErrorEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("ach-error-fallback-%s-p%d-o%d", event.getBatchId(), partition, offset);

        log.error("Circuit breaker fallback triggered for ACH error: batchId={}, error={}",
            event.getBatchId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("ach-processing-errors-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for high-impact errors
        if (event.getAffectedTransactionCount() > 1000 ||
            event.getTotalAmount().compareTo(BigDecimal.valueOf(1000000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical ACH Processing Error - Circuit Breaker Triggered",
                    String.format("ACH batch %s error affecting %d transactions ($%,.2f): %s",
                        event.getBatchId(), event.getAffectedTransactionCount(),
                        event.getTotalAmount(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltACHErrorEvent(
            @Payload ACHProcessingErrorEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-ach-error-%s-%d", event.getBatchId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - ACH error permanently failed: batchId={}, topic={}, error={}",
            event.getBatchId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logACHEvent("ACH_ERROR_DLT_EVENT", event.getBatchId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "errorType", event.getErrorType(), "totalAmount", event.getTotalAmount(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "ACH Processing Error Dead Letter Event",
                String.format("ACH batch %s error sent to DLT (Impact: %d transactions, $%,.2f): %s",
                    event.getBatchId(), event.getAffectedTransactionCount(),
                    event.getTotalAmount(), exceptionMessage),
                Map.of("batchId", event.getBatchId(), "topic", topic,
                    "correlationId", correlationId, "totalAmount", event.getTotalAmount())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessFinancialImpact(BigDecimal totalAmount, String errorCode, String correlationId) {
        long amountCents = totalAmount.multiply(BigDecimal.valueOf(100)).longValue();
        totalACHErrorAmount.addAndGet(amountCents);
        achErrorCount.incrementAndGet();
        financialImpactCounter.increment(amountCents);

        // Alert if cumulative ACH error amount exceeds threshold
        if (totalACHErrorAmount.get() > 5000000000L) { // $50M in cents
            try {
                notificationService.sendExecutiveAlert(
                    "CRITICAL: ACH Processing Errors Exceed $50M",
                    String.format("Cumulative ACH error impact: $%,.2f. Payment operations review required.",
                        totalACHErrorAmount.get() / 100.0),
                    "CRITICAL"
                );
                // Reset counter after alert
                totalACHErrorAmount.set(0);
            } catch (Exception ex) {
                log.error("Failed to send financial impact alert: {}", ex.getMessage());
            }
        }

        // High-impact ACH error alert
        if (totalAmount.compareTo(BigDecimal.valueOf(1000000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Impact ACH Processing Error",
                    String.format("ACH error impact: $%,.2f, Error: %s", totalAmount, errorCode),
                    "HIGH"
                );
            } catch (Exception ex) {
                log.error("Failed to send high-impact ACH error alert: {}", ex.getMessage());
            }
        }
    }

    private void handleBatchValidationError(ACHProcessingErrorEvent event, String correlationId) {
        createErrorRecord(event, "BATCH_VALIDATION_ERROR", correlationId);

        // Validate batch header and control records
        kafkaTemplate.send("ach-batch-validation", Map.of(
            "batchId", event.getBatchId(),
            "validationType", "HEADER_CONTROL_VALIDATION",
            "batchData", event.getBatchData(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Correct batch structure
        kafkaTemplate.send("ach-batch-correction", Map.of(
            "batchId", event.getBatchId(),
            "correctionType", "STRUCTURE_FIX",
            "validationErrors", event.getValidationErrors(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("ACH Batch Validation Error",
            String.format("Batch validation error for ACH batch %s, correcting structure",
                event.getBatchId()),
            "HIGH");

        metricsService.recordACHError("BATCH_VALIDATION_ERROR", event.getTotalAmount());
    }

    private void handleNACHAFormatError(ACHProcessingErrorEvent event, String correlationId) {
        createErrorRecord(event, "NACHA_FORMAT_ERROR", correlationId);

        // Validate NACHA format compliance
        kafkaTemplate.send("nacha-format-validation", Map.of(
            "batchId", event.getBatchId(),
            "formatErrors", event.getFormatErrors(),
            "validationType", "NACHA_COMPLIANCE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Reformat to NACHA standards
        kafkaTemplate.send("nacha-format-correction", Map.of(
            "batchId", event.getBatchId(),
            "correctionType", "NACHA_COMPLIANCE",
            "formatErrors", event.getFormatErrors(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("NACHA Format Error",
            String.format("NACHA format error for batch %s, applying corrections",
                event.getBatchId()),
            "HIGH");

        metricsService.recordACHError("NACHA_FORMAT_ERROR", event.getTotalAmount());
    }

    private void handleRoutingNumberInvalid(ACHProcessingErrorEvent event, String correlationId) {
        createErrorRecord(event, "ROUTING_NUMBER_INVALID", correlationId);

        // Validate routing numbers
        kafkaTemplate.send("routing-number-validation", Map.of(
            "batchId", event.getBatchId(),
            "invalidRoutingNumbers", event.getInvalidRoutingNumbers(),
            "validationType", "ABA_LOOKUP",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Correct routing numbers
        kafkaTemplate.send("routing-number-correction", Map.of(
            "batchId", event.getBatchId(),
            "correctionType", "ROUTING_FIX",
            "invalidEntries", event.getInvalidEntries(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Invalid Routing Numbers",
            String.format("Invalid routing numbers in ACH batch %s, applying corrections",
                event.getBatchId()),
            "HIGH");

        metricsService.recordACHError("ROUTING_NUMBER_INVALID", event.getTotalAmount());
    }

    private void handleAccountNumberInvalid(ACHProcessingErrorEvent event, String correlationId) {
        createErrorRecord(event, "ACCOUNT_NUMBER_INVALID", correlationId);

        // Validate account numbers
        kafkaTemplate.send("account-number-validation", Map.of(
            "batchId", event.getBatchId(),
            "invalidAccountNumbers", event.getInvalidAccountNumbers(),
            "validationType", "FORMAT_CHECK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Create NOC (Notification of Change) for invalid accounts
        kafkaTemplate.send("ach-noc-creation", Map.of(
            "batchId", event.getBatchId(),
            "nocType", "INVALID_ACCOUNT_NUMBER",
            "affectedEntries", event.getInvalidEntries(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordACHError("ACCOUNT_NUMBER_INVALID", event.getTotalAmount());
    }

    private void handleInsufficientFunds(ACHProcessingErrorEvent event, String correlationId) {
        createErrorRecord(event, "INSUFFICIENT_FUNDS", correlationId);

        // Process return entries
        kafkaTemplate.send("ach-return-processing", Map.of(
            "batchId", event.getBatchId(),
            "returnCode", "R01", // Insufficient Funds
            "returnEntries", event.getReturnEntries(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check for retry eligibility
        kafkaTemplate.send("ach-retry-eligibility-checks", Map.of(
            "batchId", event.getBatchId(),
            "checkType", "INSUFFICIENT_FUNDS_RETRY",
            "affectedEntries", event.getReturnEntries(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("ACH Insufficient Funds",
            String.format("Insufficient funds returns in ACH batch %s: %d entries",
                event.getBatchId(), event.getReturnEntries().size()),
            "MEDIUM");

        metricsService.recordACHError("INSUFFICIENT_FUNDS", event.getTotalAmount());
    }

    private void handleAccountClosed(ACHProcessingErrorEvent event, String correlationId) {
        createErrorRecord(event, "ACCOUNT_CLOSED", correlationId);

        // Process return entries for closed accounts
        kafkaTemplate.send("ach-return-processing", Map.of(
            "batchId", event.getBatchId(),
            "returnCode", "R02", // Account Closed
            "returnEntries", event.getReturnEntries(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Update customer account status
        kafkaTemplate.send("customer-account-updates", Map.of(
            "updateType", "ACCOUNT_CLOSED_NOTIFICATION",
            "affectedAccounts", event.getClosedAccounts(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordACHError("ACCOUNT_CLOSED", event.getTotalAmount());
    }

    private void handleReturnProcessingError(ACHProcessingErrorEvent event, String correlationId) {
        createErrorRecord(event, "RETURN_PROCESSING_ERROR", correlationId);

        // Reprocess returns
        kafkaTemplate.send("ach-return-reprocessing", Map.of(
            "batchId", event.getBatchId(),
            "reprocessingType", "ERROR_CORRECTION",
            "failedReturns", event.getFailedReturns(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Validate return format
        kafkaTemplate.send("ach-return-validation", Map.of(
            "batchId", event.getBatchId(),
            "validationType", "RETURN_FORMAT",
            "returnData", event.getReturnData(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordACHError("RETURN_PROCESSING_ERROR", event.getTotalAmount());
    }

    private void handleDuplicateEntry(ACHProcessingErrorEvent event, String correlationId) {
        createErrorRecord(event, "DUPLICATE_ENTRY", correlationId);

        // Identify and remove duplicates
        kafkaTemplate.send("ach-duplicate-removal", Map.of(
            "batchId", event.getBatchId(),
            "duplicateEntries", event.getDuplicateEntries(),
            "removalStrategy", "KEEP_FIRST",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Validate deduplication
        kafkaTemplate.send("ach-deduplication-validation", Map.of(
            "batchId", event.getBatchId(),
            "validationType", "POST_DEDUPLICATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordACHError("DUPLICATE_ENTRY", BigDecimal.ZERO);
    }

    private void handleCutoffTimeMissed(ACHProcessingErrorEvent event, String correlationId) {
        createErrorRecord(event, "CUTOFF_TIME_MISSED", correlationId);

        // Schedule for next processing window
        kafkaTemplate.send("ach-processing-rescheduling", Map.of(
            "batchId", event.getBatchId(),
            "missedCutoff", event.getCutoffTime(),
            "nextProcessingWindow", event.getNextProcessingWindow(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check same-day processing availability
        kafkaTemplate.send("same-day-ach-eligibility", Map.of(
            "batchId", event.getBatchId(),
            "checkType", "SAME_DAY_PROCESSING",
            "urgencyLevel", "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("ACH Cutoff Time Missed",
            String.format("ACH batch %s missed cutoff, rescheduled for next window",
                event.getBatchId()),
            "MEDIUM");

        metricsService.recordACHError("CUTOFF_TIME_MISSED", event.getTotalAmount());
    }

    private void handleRegulatoryComplianceError(ACHProcessingErrorEvent event, String correlationId) {
        createErrorRecord(event, "REGULATORY_COMPLIANCE_ERROR", correlationId);

        // Enhanced compliance screening
        kafkaTemplate.send("ach-compliance-screening", Map.of(
            "batchId", event.getBatchId(),
            "complianceErrors", event.getComplianceErrors(),
            "screeningType", "ENHANCED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Create compliance case
        kafkaTemplate.send("compliance-case-creation", Map.of(
            "caseType", "ACH_REGULATORY_COMPLIANCE",
            "batchId", event.getBatchId(),
            "complianceErrors", event.getComplianceErrors(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendComplianceAlert("ACH Regulatory Compliance Error",
            String.format("Regulatory compliance error in ACH batch %s",
                event.getBatchId()),
            correlationId);

        metricsService.recordACHError("REGULATORY_COMPLIANCE_ERROR", event.getTotalAmount());
    }

    private void handleGenericACHError(ACHProcessingErrorEvent event, String correlationId) {
        createErrorRecord(event, "UNKNOWN_ERROR", correlationId);

        // Log for investigation
        auditService.logACHEvent("UNKNOWN_ACH_ERROR", event.getBatchId(),
            Map.of("errorType", event.getErrorType(), "errorCode", event.getErrorCode(),
                "errorMessage", event.getErrorMessage(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown ACH Processing Error",
            String.format("Unknown ACH error for batch %s: %s",
                event.getBatchId(), event.getErrorMessage()),
            "HIGH");

        metricsService.recordACHError("UNKNOWN", event.getTotalAmount());
    }

    private void createErrorRecord(ACHProcessingErrorEvent event, String errorType, String correlationId) {
        try {
            ACHProcessingError error = ACHProcessingError.builder()
                .batchId(event.getBatchId())
                .errorType(errorType)
                .errorCode(event.getErrorCode())
                .errorMessage(event.getErrorMessage())
                .affectedTransactionCount(event.getAffectedTransactionCount())
                .totalAmount(event.getTotalAmount())
                .processingDate(event.getProcessingDate())
                .errorTime(LocalDateTime.now())
                .status("OPEN")
                .correlationId(correlationId)
                .build();

            errorRepository.save(error);
        } catch (Exception e) {
            log.error("Failed to create ACH processing error record: {}", e.getMessage());
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