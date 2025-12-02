package com.waqiti.payment.kafka;

import com.waqiti.common.events.BatchPaymentValidationErrorEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.domain.ValidationError;
import com.waqiti.payment.repository.ValidationErrorRepository;
import com.waqiti.payment.service.ValidationService;
import com.waqiti.payment.service.BatchProcessingService;
import com.waqiti.payment.metrics.PaymentMetricsService;
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
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class BatchPaymentValidationErrorsConsumer {

    private final ValidationErrorRepository validationErrorRepository;
    private final ValidationService validationService;
    private final BatchProcessingService batchProcessingService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("batch_payment_validation_errors_processed_total")
            .description("Total number of successfully processed batch payment validation error events")
            .register(meterRegistry);
        errorCounter = Counter.builder("batch_payment_validation_errors_errors_total")
            .description("Total number of batch payment validation error processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("batch_payment_validation_errors_processing_duration")
            .description("Time taken to process batch payment validation error events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"batch-payment-validation-errors", "bulk-validation-failures", "batch-data-validation-errors"},
        groupId = "batch-payment-validation-errors-service-group",
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
    @CircuitBreaker(name = "batch-payment-validation-errors", fallbackMethod = "handleBatchPaymentValidationErrorEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBatchPaymentValidationErrorEvent(
            @Payload BatchPaymentValidationErrorEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("batch-validation-error-%s-p%d-o%d", event.getBatchId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getBatchId(), event.getErrorType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing batch payment validation error: batchId={}, type={}, count={}",
                event.getBatchId(), event.getErrorType(), event.getErrorCount());

            cleanExpiredEntries();

            switch (event.getErrorType()) {
                case INVALID_PAYMENT_FORMAT:
                    processInvalidPaymentFormat(event, correlationId);
                    break;

                case MISSING_REQUIRED_FIELDS:
                    processMissingRequiredFields(event, correlationId);
                    break;

                case INVALID_ACCOUNT_NUMBER:
                    processInvalidAccountNumber(event, correlationId);
                    break;

                case INVALID_ROUTING_NUMBER:
                    processInvalidRoutingNumber(event, correlationId);
                    break;

                case INSUFFICIENT_FUNDS:
                    processInsufficientFunds(event, correlationId);
                    break;

                case AMOUNT_VALIDATION_ERROR:
                    processAmountValidationError(event, correlationId);
                    break;

                case CURRENCY_VALIDATION_ERROR:
                    processCurrencyValidationError(event, correlationId);
                    break;

                case DATE_VALIDATION_ERROR:
                    processDateValidationError(event, correlationId);
                    break;

                case DUPLICATE_PAYMENT_REFERENCE:
                    processDuplicatePaymentReference(event, correlationId);
                    break;

                case BENEFICIARY_VALIDATION_ERROR:
                    processBeneficiaryValidationError(event, correlationId);
                    break;

                case REGULATORY_COMPLIANCE_ERROR:
                    processRegulatoryComplianceError(event, correlationId);
                    break;

                case SANCTION_SCREENING_ERROR:
                    processSanctionScreeningError(event, correlationId);
                    break;

                default:
                    log.warn("Unknown batch payment validation error type: {}", event.getErrorType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("BATCH_PAYMENT_VALIDATION_ERROR_PROCESSED", event.getBatchId(),
                Map.of("errorType", event.getErrorType(), "errorCount", event.getErrorCount(),
                    "severity", event.getSeverity(), "affectedTransactions", event.getAffectedTransactionIds().size(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process batch payment validation error: {}", e.getMessage(), e);

            // Send to DLQ with context
            dlqHandler.handleFailedMessage(
                "batch-payment-validation-errors",
                event,
                e,
                Map.of(
                    "batchId", event.getBatchId(),
                    "errorType", event.getErrorType().toString(),
                    "errorCount", String.valueOf(event.getErrorCount()),
                    "severity", event.getSeverity(),
                    "correlationId", correlationId,
                    "partition", String.valueOf(partition),
                    "offset", String.valueOf(offset)
                )
            );

            kafkaTemplate.send("batch-payment-validation-errors-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleBatchPaymentValidationErrorEventFallback(
            BatchPaymentValidationErrorEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("batch-validation-error-fallback-%s-p%d-o%d", event.getBatchId(), partition, offset);

        log.error("Circuit breaker fallback triggered for batch payment validation error: batchId={}, error={}",
            event.getBatchId(), ex.getMessage());

        kafkaTemplate.send("batch-payment-validation-errors-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBatchPaymentValidationErrorEvent(
            @Payload BatchPaymentValidationErrorEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-batch-validation-error-%s-%d", event.getBatchId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Batch payment validation error permanently failed: batchId={}, error={}",
            event.getBatchId(), exceptionMessage);

        auditService.logPaymentEvent("BATCH_PAYMENT_VALIDATION_ERROR_DLT_EVENT", event.getBatchId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "errorType", event.getErrorType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) return false;
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

    private void processInvalidPaymentFormat(BatchPaymentValidationErrorEvent event, String correlationId) {
        ValidationError error = ValidationError.builder()
            .batchId(event.getBatchId())
            .errorType("INVALID_PAYMENT_FORMAT")
            .severity(event.getSeverity())
            .errorCount(event.getErrorCount())
            .errorMessage(event.getErrorMessage())
            .affectedTransactions(event.getAffectedTransactionIds())
            .formatErrors(event.getFormatErrors())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        validationErrorRepository.save(error);

        // Handle format validation errors
        validationService.handleFormatValidationErrors(
            event.getBatchId(),
            event.getAffectedTransactionIds(),
            event.getFormatErrors(),
            correlationId
        );

        // Attempt auto-correction where possible
        validationService.attemptAutoCorrection(
            event.getAffectedTransactionIds(),
            event.getFormatErrors(),
            correlationId
        );

        // Send validation error notification
        notificationService.sendOperationalAlert(
            "Batch Payment Format Validation Errors",
            String.format("Batch %s: %d payments have format validation errors",
                event.getBatchId(), event.getErrorCount()),
            "MEDIUM"
        );

        metricsService.recordBatchValidationError(event.getBatchId(), "INVALID_PAYMENT_FORMAT", event.getErrorCount());
    }

    private void processMissingRequiredFields(BatchPaymentValidationErrorEvent event, String correlationId) {
        ValidationError error = ValidationError.builder()
            .batchId(event.getBatchId())
            .errorType("MISSING_REQUIRED_FIELDS")
            .severity(event.getSeverity())
            .errorCount(event.getErrorCount())
            .errorMessage(event.getErrorMessage())
            .affectedTransactions(event.getAffectedTransactionIds())
            .missingFields(event.getMissingFields())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        validationErrorRepository.save(error);

        // Handle missing required fields
        validationService.handleMissingRequiredFields(
            event.getBatchId(),
            event.getAffectedTransactionIds(),
            event.getMissingFields(),
            correlationId
        );

        // Check if fields can be populated from defaults
        validationService.populateDefaultFields(
            event.getAffectedTransactionIds(),
            event.getMissingFields(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Batch Payment Missing Required Fields",
            String.format("Batch %s: %d payments missing required fields",
                event.getBatchId(), event.getErrorCount()),
            "HIGH"
        );

        metricsService.recordBatchValidationError(event.getBatchId(), "MISSING_REQUIRED_FIELDS", event.getErrorCount());
    }

    private void processInvalidAccountNumber(BatchPaymentValidationErrorEvent event, String correlationId) {
        ValidationError error = ValidationError.builder()
            .batchId(event.getBatchId())
            .errorType("INVALID_ACCOUNT_NUMBER")
            .severity(event.getSeverity())
            .errorCount(event.getErrorCount())
            .errorMessage(event.getErrorMessage())
            .affectedTransactions(event.getAffectedTransactionIds())
            .invalidAccounts(event.getInvalidAccountNumbers())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        validationErrorRepository.save(error);

        // Handle invalid account numbers
        validationService.handleInvalidAccountNumbers(
            event.getBatchId(),
            event.getAffectedTransactionIds(),
            event.getInvalidAccountNumbers(),
            correlationId
        );

        // Attempt account number validation and correction
        validationService.validateAndCorrectAccountNumbers(
            event.getInvalidAccountNumbers(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Batch Payment Invalid Account Numbers",
            String.format("Batch %s: %d payments have invalid account numbers",
                event.getBatchId(), event.getErrorCount()),
            "HIGH"
        );

        metricsService.recordBatchValidationError(event.getBatchId(), "INVALID_ACCOUNT_NUMBER", event.getErrorCount());
    }

    private void processInvalidRoutingNumber(BatchPaymentValidationErrorEvent event, String correlationId) {
        ValidationError error = ValidationError.builder()
            .batchId(event.getBatchId())
            .errorType("INVALID_ROUTING_NUMBER")
            .severity(event.getSeverity())
            .errorCount(event.getErrorCount())
            .errorMessage(event.getErrorMessage())
            .affectedTransactions(event.getAffectedTransactionIds())
            .invalidRoutingNumbers(event.getInvalidRoutingNumbers())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        validationErrorRepository.save(error);

        // Handle invalid routing numbers
        validationService.handleInvalidRoutingNumbers(
            event.getBatchId(),
            event.getAffectedTransactionIds(),
            event.getInvalidRoutingNumbers(),
            correlationId
        );

        // Validate routing numbers against bank directory
        validationService.validateRoutingNumbersAgainstDirectory(
            event.getInvalidRoutingNumbers(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Batch Payment Invalid Routing Numbers",
            String.format("Batch %s: %d payments have invalid routing numbers",
                event.getBatchId(), event.getErrorCount()),
            "HIGH"
        );

        metricsService.recordBatchValidationError(event.getBatchId(), "INVALID_ROUTING_NUMBER", event.getErrorCount());
    }

    private void processInsufficientFunds(BatchPaymentValidationErrorEvent event, String correlationId) {
        ValidationError error = ValidationError.builder()
            .batchId(event.getBatchId())
            .errorType("INSUFFICIENT_FUNDS")
            .severity(event.getSeverity())
            .errorCount(event.getErrorCount())
            .errorMessage(event.getErrorMessage())
            .affectedTransactions(event.getAffectedTransactionIds())
            .insufficientFundsDetails(event.getInsufficientFundsDetails())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        validationErrorRepository.save(error);

        // Handle insufficient funds
        validationService.handleInsufficientFunds(
            event.getBatchId(),
            event.getAffectedTransactionIds(),
            event.getInsufficientFundsDetails(),
            correlationId
        );

        // Check for overdraft protection options
        validationService.checkOverdraftProtection(
            event.getAffectedTransactionIds(),
            event.getInsufficientFundsDetails(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Batch Payment Insufficient Funds",
            String.format("Batch %s: %d payments failed due to insufficient funds",
                event.getBatchId(), event.getErrorCount()),
            "HIGH"
        );

        metricsService.recordBatchValidationError(event.getBatchId(), "INSUFFICIENT_FUNDS", event.getErrorCount());
    }

    private void processAmountValidationError(BatchPaymentValidationErrorEvent event, String correlationId) {
        ValidationError error = ValidationError.builder()
            .batchId(event.getBatchId())
            .errorType("AMOUNT_VALIDATION_ERROR")
            .severity(event.getSeverity())
            .errorCount(event.getErrorCount())
            .errorMessage(event.getErrorMessage())
            .affectedTransactions(event.getAffectedTransactionIds())
            .amountErrors(event.getAmountErrors())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        validationErrorRepository.save(error);

        // Handle amount validation errors
        validationService.handleAmountValidationErrors(
            event.getBatchId(),
            event.getAffectedTransactionIds(),
            event.getAmountErrors(),
            correlationId
        );

        // Check amount limits and restrictions
        validationService.validateAmountLimits(
            event.getAffectedTransactionIds(),
            event.getAmountErrors(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Batch Payment Amount Validation Errors",
            String.format("Batch %s: %d payments have amount validation errors",
                event.getBatchId(), event.getErrorCount()),
            "MEDIUM"
        );

        metricsService.recordBatchValidationError(event.getBatchId(), "AMOUNT_VALIDATION_ERROR", event.getErrorCount());
    }

    private void processCurrencyValidationError(BatchPaymentValidationErrorEvent event, String correlationId) {
        ValidationError error = ValidationError.builder()
            .batchId(event.getBatchId())
            .errorType("CURRENCY_VALIDATION_ERROR")
            .severity(event.getSeverity())
            .errorCount(event.getErrorCount())
            .errorMessage(event.getErrorMessage())
            .affectedTransactions(event.getAffectedTransactionIds())
            .currencyErrors(event.getCurrencyErrors())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        validationErrorRepository.save(error);

        // Handle currency validation errors
        validationService.handleCurrencyValidationErrors(
            event.getBatchId(),
            event.getAffectedTransactionIds(),
            event.getCurrencyErrors(),
            correlationId
        );

        // Validate currency codes against ISO standards
        validationService.validateCurrencyCodes(
            event.getCurrencyErrors(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Batch Payment Currency Validation Errors",
            String.format("Batch %s: %d payments have currency validation errors",
                event.getBatchId(), event.getErrorCount()),
            "MEDIUM"
        );

        metricsService.recordBatchValidationError(event.getBatchId(), "CURRENCY_VALIDATION_ERROR", event.getErrorCount());
    }

    private void processDateValidationError(BatchPaymentValidationErrorEvent event, String correlationId) {
        ValidationError error = ValidationError.builder()
            .batchId(event.getBatchId())
            .errorType("DATE_VALIDATION_ERROR")
            .severity(event.getSeverity())
            .errorCount(event.getErrorCount())
            .errorMessage(event.getErrorMessage())
            .affectedTransactions(event.getAffectedTransactionIds())
            .dateErrors(event.getDateErrors())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        validationErrorRepository.save(error);

        // Handle date validation errors
        validationService.handleDateValidationErrors(
            event.getBatchId(),
            event.getAffectedTransactionIds(),
            event.getDateErrors(),
            correlationId
        );

        // Validate business days and cut-off times
        validationService.validateBusinessDaysAndCutoffs(
            event.getAffectedTransactionIds(),
            event.getDateErrors(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Batch Payment Date Validation Errors",
            String.format("Batch %s: %d payments have date validation errors",
                event.getBatchId(), event.getErrorCount()),
            "MEDIUM"
        );

        metricsService.recordBatchValidationError(event.getBatchId(), "DATE_VALIDATION_ERROR", event.getErrorCount());
    }

    private void processDuplicatePaymentReference(BatchPaymentValidationErrorEvent event, String correlationId) {
        ValidationError error = ValidationError.builder()
            .batchId(event.getBatchId())
            .errorType("DUPLICATE_PAYMENT_REFERENCE")
            .severity(event.getSeverity())
            .errorCount(event.getErrorCount())
            .errorMessage(event.getErrorMessage())
            .affectedTransactions(event.getAffectedTransactionIds())
            .duplicateReferences(event.getDuplicateReferences())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        validationErrorRepository.save(error);

        // Handle duplicate payment references
        validationService.handleDuplicatePaymentReferences(
            event.getBatchId(),
            event.getAffectedTransactionIds(),
            event.getDuplicateReferences(),
            correlationId
        );

        // Generate unique references for duplicates
        validationService.generateUniqueReferences(
            event.getAffectedTransactionIds(),
            event.getDuplicateReferences(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Batch Payment Duplicate References",
            String.format("Batch %s: %d payments have duplicate references",
                event.getBatchId(), event.getErrorCount()),
            "MEDIUM"
        );

        metricsService.recordBatchValidationError(event.getBatchId(), "DUPLICATE_PAYMENT_REFERENCE", event.getErrorCount());
    }

    private void processBeneficiaryValidationError(BatchPaymentValidationErrorEvent event, String correlationId) {
        ValidationError error = ValidationError.builder()
            .batchId(event.getBatchId())
            .errorType("BENEFICIARY_VALIDATION_ERROR")
            .severity(event.getSeverity())
            .errorCount(event.getErrorCount())
            .errorMessage(event.getErrorMessage())
            .affectedTransactions(event.getAffectedTransactionIds())
            .beneficiaryErrors(event.getBeneficiaryErrors())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        validationErrorRepository.save(error);

        // Handle beneficiary validation errors
        validationService.handleBeneficiaryValidationErrors(
            event.getBatchId(),
            event.getAffectedTransactionIds(),
            event.getBeneficiaryErrors(),
            correlationId
        );

        // Validate beneficiary details against database
        validationService.validateBeneficiaryDetails(
            event.getBeneficiaryErrors(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Batch Payment Beneficiary Validation Errors",
            String.format("Batch %s: %d payments have beneficiary validation errors",
                event.getBatchId(), event.getErrorCount()),
            "HIGH"
        );

        metricsService.recordBatchValidationError(event.getBatchId(), "BENEFICIARY_VALIDATION_ERROR", event.getErrorCount());
    }

    private void processRegulatoryComplianceError(BatchPaymentValidationErrorEvent event, String correlationId) {
        ValidationError error = ValidationError.builder()
            .batchId(event.getBatchId())
            .errorType("REGULATORY_COMPLIANCE_ERROR")
            .severity(event.getSeverity())
            .errorCount(event.getErrorCount())
            .errorMessage(event.getErrorMessage())
            .affectedTransactions(event.getAffectedTransactionIds())
            .complianceErrors(event.getComplianceErrors())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        validationErrorRepository.save(error);

        // Handle regulatory compliance errors
        validationService.handleRegulatoryComplianceErrors(
            event.getBatchId(),
            event.getAffectedTransactionIds(),
            event.getComplianceErrors(),
            correlationId
        );

        // Escalate to compliance team
        validationService.escalateToComplianceTeam(
            event.getBatchId(),
            event.getComplianceErrors(),
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Batch Payment Regulatory Compliance Errors",
            String.format("Batch %s: %d payments have regulatory compliance errors",
                event.getBatchId(), event.getErrorCount()),
            Map.of("batchId", event.getBatchId(), "errorCount", event.getErrorCount(), "correlationId", correlationId)
        );

        metricsService.recordBatchValidationError(event.getBatchId(), "REGULATORY_COMPLIANCE_ERROR", event.getErrorCount());
    }

    private void processSanctionScreeningError(BatchPaymentValidationErrorEvent event, String correlationId) {
        ValidationError error = ValidationError.builder()
            .batchId(event.getBatchId())
            .errorType("SANCTION_SCREENING_ERROR")
            .severity(event.getSeverity())
            .errorCount(event.getErrorCount())
            .errorMessage(event.getErrorMessage())
            .affectedTransactions(event.getAffectedTransactionIds())
            .sanctionScreeningResults(event.getSanctionScreeningResults())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        validationErrorRepository.save(error);

        // Handle sanction screening errors
        validationService.handleSanctionScreeningErrors(
            event.getBatchId(),
            event.getAffectedTransactionIds(),
            event.getSanctionScreeningResults(),
            correlationId
        );

        // Immediately freeze flagged transactions
        validationService.freezeSanctionedTransactions(
            event.getAffectedTransactionIds(),
            event.getSanctionScreeningResults(),
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Batch Payment Sanction Screening Errors",
            String.format("Batch %s: %d payments flagged by sanction screening",
                event.getBatchId(), event.getErrorCount()),
            Map.of("batchId", event.getBatchId(), "errorCount", event.getErrorCount(), "correlationId", correlationId)
        );

        metricsService.recordBatchValidationError(event.getBatchId(), "SANCTION_SCREENING_ERROR", event.getErrorCount());
    }
}