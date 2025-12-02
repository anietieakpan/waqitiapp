package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.model.*;
import com.waqiti.payment.repository.AsyncReversalRepository;
import com.waqiti.payment.repository.ValidationErrorRepository;
import com.waqiti.payment.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.idempotency.RedisIdempotencyService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for async reversal validation errors
 * Handles validation errors in async reversals with enterprise patterns
 * 
 * Critical for: Payment integrity, error tracking, compliance reporting
 * SLA: Must process validation errors within 15 seconds for proper error handling
 */
@Component
@Slf4j
public class AsyncReversalValidationErrorsConsumer {

    private final AsyncReversalRepository reversalRepository;
    private final ValidationErrorRepository validationErrorRepository;
    private final PaymentService paymentService;
    private final ReversalTrackingService reversalTrackingService;
    private final ValidationService validationService;
    private final NotificationService notificationService;
    private final ComplianceReportingService complianceService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RedisIdempotencyService idempotencyService;

    private final Counter validationErrorCounter;
    private final Counter criticalValidationErrorCounter;
    private final Counter complianceValidationErrorCounter;
    private final Timer validationErrorProcessingTimer;

    public AsyncReversalValidationErrorsConsumer(
            AsyncReversalRepository reversalRepository,
            ValidationErrorRepository validationErrorRepository,
            PaymentService paymentService,
            ReversalTrackingService reversalTrackingService,
            ValidationService validationService,
            NotificationService notificationService,
            ComplianceReportingService complianceService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            RedisIdempotencyService idempotencyService) {

        this.reversalRepository = reversalRepository;
        this.validationErrorRepository = validationErrorRepository;
        this.paymentService = paymentService;
        this.reversalTrackingService = reversalTrackingService;
        this.validationService = validationService;
        this.notificationService = notificationService;
        this.complianceService = complianceService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
        
        this.validationErrorCounter = Counter.builder("async.reversal.validation.error.events")
            .description("Count of async reversal validation error events")
            .register(meterRegistry);
        
        this.criticalValidationErrorCounter = Counter.builder("async.reversal.validation.error.critical.events")
            .description("Count of critical validation error events")
            .register(meterRegistry);
        
        this.complianceValidationErrorCounter = Counter.builder("async.reversal.validation.error.compliance.events")
            .description("Count of compliance-related validation errors")
            .register(meterRegistry);
        
        this.validationErrorProcessingTimer = Timer.builder("async.reversal.validation.error.processing.duration")
            .description("Time taken to process validation error events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "async-reversal-validation-errors",
        groupId = "async-reversal-validation-errors-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "async-reversal-validation-errors-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleAsyncReversalValidationErrorEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();

        try {
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            String reversalId = (String) eventData.get("reversalId");

            // Build idempotency key
            String idempotencyKey = idempotencyService.buildIdempotencyKey(
                "payment-service",
                "AsyncReversalValidationError",
                reversalId + ":" + partition + ":" + offset
            );

            log.info("Received async reversal validation error event - reversalId: {}, partition: {}, offset: {}",
                reversalId, partition, offset);

            // Universal idempotency check (30-day TTL for financial operations)
            if (idempotencyService.isProcessed(idempotencyKey)) {
                log.info("⏭️ Async reversal validation error already processed, skipping: reversalId={}", reversalId);
                acknowledgment.acknowledge();
                return;
            }
            String originalTransactionId = (String) eventData.get("originalTransactionId");
            String paymentId = (String) eventData.get("paymentId");
            String errorCode = (String) eventData.get("errorCode");
            String errorMessage = (String) eventData.get("errorMessage");
            String errorType = (String) eventData.get("errorType");
            String severity = (String) eventData.getOrDefault("severity", "MEDIUM");
            String validationField = (String) eventData.get("validationField");
            String expectedValue = (String) eventData.get("expectedValue");
            String actualValue = (String) eventData.get("actualValue");
            List<String> validationRules = (List<String>) eventData.get("failedValidationRules");
            Boolean isRecoverable = (Boolean) eventData.getOrDefault("isRecoverable", false);
            Boolean requiresManualReview = (Boolean) eventData.getOrDefault("requiresManualReview", false);
            
            String correlationId = String.format("async-reversal-validation-error-%s-%d", 
                reversalId, System.currentTimeMillis());
            
            log.error("CRITICAL: Processing async reversal validation error - reversalId: {}, errorCode: {}, severity: {}, correlationId: {}", 
                reversalId, errorCode, severity, correlationId);
            
            validationErrorCounter.increment();
            
            processAsyncReversalValidationError(reversalId, originalTransactionId, paymentId, errorCode,
                errorMessage, errorType, severity, validationField, expectedValue, actualValue,
                validationRules, isRecoverable, requiresManualReview, eventData, correlationId);

            // Mark as processed (30-day TTL for financial operations)
            idempotencyService.markFinancialOperationProcessed(idempotencyKey);

            acknowledgment.acknowledge();

            validationErrorProcessingTimer.stop(sample);

            log.info("Successfully processed async reversal validation error event: reversalId={}", reversalId);

        } catch (Exception e) {
            log.error("Failed to process async reversal validation error event: partition={}, offset={}, error={}",
                partition, offset, e.getMessage(), e);
            throw new RuntimeException("Async reversal validation error processing failed", e);
        }
    }

    @CircuitBreaker(name = "async-reversal", fallbackMethod = "processAsyncReversalValidationErrorFallback")
    @Retry(name = "async-reversal")
    private void processAsyncReversalValidationError(
            String reversalId,
            String originalTransactionId,
            String paymentId,
            String errorCode,
            String errorMessage,
            String errorType,
            String severity,
            String validationField,
            String expectedValue,
            String actualValue,
            List<String> validationRules,
            Boolean isRecoverable,
            Boolean requiresManualReview,
            Map<String, Object> eventData,
            String correlationId) {
        
        // Create validation error record
        ValidationError validationError = ValidationError.builder()
            .id(java.util.UUID.randomUUID().toString())
            .reversalId(reversalId)
            .originalTransactionId(originalTransactionId)
            .paymentId(paymentId)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .errorType(ValidationErrorType.fromString(errorType))
            .severity(ValidationSeverity.fromString(severity))
            .validationField(validationField)
            .expectedValue(expectedValue)
            .actualValue(actualValue)
            .failedValidationRules(validationRules)
            .isRecoverable(isRecoverable)
            .requiresManualReview(requiresManualReview)
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        validationErrorRepository.save(validationError);
        
        // Update async reversal status if exists
        reversalRepository.findById(reversalId).ifPresent(asyncReversal -> {
            asyncReversal.setStatus(AsyncReversalStatus.VALIDATION_ERROR);
            asyncReversal.setErrorCode(errorCode);
            asyncReversal.setErrorMessage(errorMessage);
            asyncReversal.setLastUpdatedAt(LocalDateTime.now());
            reversalRepository.save(asyncReversal);
        });
        
        // Handle critical validation errors
        if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
            criticalValidationErrorCounter.increment();
            handleCriticalValidationError(validationError, correlationId);
        }
        
        // Handle compliance-related validation errors
        if (isComplianceRelatedError(errorCode, errorType)) {
            complianceValidationErrorCounter.increment();
            handleComplianceValidationError(validationError, correlationId);
        }
        
        // Attempt automatic recovery if possible
        if (isRecoverable) {
            attemptAutomaticRecovery(validationError, correlationId);
        }
        
        // Send notifications based on severity
        sendValidationErrorNotifications(validationError, correlationId);
        
        // Update payment status if needed
        if (paymentId != null) {
            paymentService.updatePaymentStatus(paymentId, PaymentStatus.VALIDATION_ERROR, 
                String.format("Validation error: %s", errorMessage));
        }
        
        // Create remediation workflow
        createRemediationWorkflow(validationError, correlationId);
        
        // Publish validation error analytics
        kafkaTemplate.send("validation-error-analytics", Map.of(
            "reversalId", reversalId,
            "originalTransactionId", originalTransactionId,
            "paymentId", paymentId,
            "errorCode", errorCode,
            "errorType", errorType,
            "severity", severity,
            "validationField", validationField,
            "isRecoverable", isRecoverable,
            "requiresManualReview", requiresManualReview,
            "eventType", "VALIDATION_ERROR_OCCURRED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Audit the validation error
        auditService.logValidationErrorEvent(
            "ASYNC_REVERSAL_VALIDATION_ERROR",
            reversalId,
            Map.of(
                "originalTransactionId", originalTransactionId,
                "paymentId", paymentId,
                "errorCode", errorCode,
                "errorMessage", errorMessage,
                "errorType", errorType,
                "severity", severity,
                "validationField", validationField,
                "expectedValue", expectedValue,
                "actualValue", actualValue,
                "failedValidationRules", validationRules,
                "isRecoverable", isRecoverable,
                "requiresManualReview", requiresManualReview,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.error("CRITICAL: Async reversal validation error processed - reversalId: {}, errorCode: {}, severity: {}, correlationId: {}", 
            reversalId, errorCode, severity, correlationId);
    }

    private void handleCriticalValidationError(ValidationError validationError, String correlationId) {
        log.error("CRITICAL VALIDATION ERROR: Critical async reversal validation error - reversalId: {}, errorCode: {}, correlationId: {}", 
            validationError.getReversalId(), validationError.getErrorCode(), correlationId);
        
        // Create urgent escalation
        reversalTrackingService.createUrgentEscalation(validationError, correlationId);
        
        // Send critical alert
        kafkaTemplate.send("critical-validation-error-alerts", Map.of(
            "reversalId", validationError.getReversalId(),
            "originalTransactionId", validationError.getOriginalTransactionId(),
            "paymentId", validationError.getPaymentId(),
            "errorCode", validationError.getErrorCode(),
            "errorMessage", validationError.getErrorMessage(),
            "severity", "CRITICAL",
            "requiresImmediateAction", true,
            "alertType", "CRITICAL_VALIDATION_ERROR",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Notify executive team
        notificationService.sendExecutiveAlert(
            "Critical Validation Error",
            String.format("Critical validation error in async reversal %s: %s", 
                validationError.getReversalId(), validationError.getErrorMessage()),
            Map.of(
                "reversalId", validationError.getReversalId(),
                "errorCode", validationError.getErrorCode(),
                "errorMessage", validationError.getErrorMessage(),
                "correlationId", correlationId
            )
        );
    }

    private void handleComplianceValidationError(ValidationError validationError, String correlationId) {
        log.error("COMPLIANCE VALIDATION ERROR: Compliance-related validation error - reversalId: {}, errorCode: {}, correlationId: {}", 
            validationError.getReversalId(), validationError.getErrorCode(), correlationId);
        
        // Report to compliance team
        complianceService.reportValidationError(validationError, correlationId);
        
        // Send compliance alert
        kafkaTemplate.send("compliance-validation-error-alerts", Map.of(
            "reversalId", validationError.getReversalId(),
            "originalTransactionId", validationError.getOriginalTransactionId(),
            "paymentId", validationError.getPaymentId(),
            "errorCode", validationError.getErrorCode(),
            "errorMessage", validationError.getErrorMessage(),
            "validationField", validationError.getValidationField(),
            "priority", "HIGH",
            "alertType", "COMPLIANCE_VALIDATION_ERROR",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Notify compliance team
        notificationService.sendComplianceAlert(
            "Compliance Validation Error",
            String.format("Compliance validation error in async reversal %s: %s", 
                validationError.getReversalId(), validationError.getErrorMessage()),
            Map.of(
                "reversalId", validationError.getReversalId(),
                "errorCode", validationError.getErrorCode(),
                "validationField", validationError.getValidationField(),
                "correlationId", correlationId
            )
        );
    }

    private void attemptAutomaticRecovery(ValidationError validationError, String correlationId) {
        log.info("Attempting automatic recovery for validation error - reversalId: {}, errorCode: {}, correlationId: {}", 
            validationError.getReversalId(), validationError.getErrorCode(), correlationId);
        
        try {
            // Attempt validation service recovery
            boolean recoverySuccessful = validationService.attemptRecovery(validationError, correlationId);
            
            if (recoverySuccessful) {
                // Update validation error status
                validationError.setRecoveryAttempted(true);
                validationError.setRecoverySuccessful(true);
                validationError.setRecoveryTimestamp(LocalDateTime.now());
                validationErrorRepository.save(validationError);
                
                // Publish recovery success event
                kafkaTemplate.send("validation-error-recovery-success", Map.of(
                    "reversalId", validationError.getReversalId(),
                    "errorCode", validationError.getErrorCode(),
                    "recoveryMethod", "AUTOMATIC",
                    "correlationId", correlationId,
                    "timestamp", Instant.now().toString()
                ));
                
                // Retry reversal processing
                kafkaTemplate.send("async-reversal-retry", Map.of(
                    "reversalId", validationError.getReversalId(),
                    "retryReason", "VALIDATION_ERROR_RECOVERED",
                    "correlationId", correlationId,
                    "timestamp", Instant.now().toString()
                ));
                
                log.info("Automatic recovery successful for validation error - reversalId: {}, correlationId: {}", 
                    validationError.getReversalId(), correlationId);
            } else {
                // Mark recovery failed
                validationError.setRecoveryAttempted(true);
                validationError.setRecoverySuccessful(false);
                validationError.setRecoveryTimestamp(LocalDateTime.now());
                validationErrorRepository.save(validationError);
                
                log.warn("Automatic recovery failed for validation error - reversalId: {}, correlationId: {}", 
                    validationError.getReversalId(), correlationId);
            }
            
        } catch (Exception e) {
            log.error("Error during automatic recovery attempt - reversalId: {}, correlationId: {}, error: {}", 
                validationError.getReversalId(), correlationId, e.getMessage(), e);
            
            validationError.setRecoveryAttempted(true);
            validationError.setRecoverySuccessful(false);
            validationError.setRecoveryTimestamp(LocalDateTime.now());
            validationError.setRecoveryError(e.getMessage());
            validationErrorRepository.save(validationError);
        }
    }

    private void sendValidationErrorNotifications(ValidationError validationError, String correlationId) {
        // Notify operations team based on severity
        String severity = validationError.getSeverity().toString();
        
        if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
            notificationService.sendUrgentNotification(
                "Urgent Validation Error",
                String.format("Urgent validation error in async reversal %s: %s", 
                    validationError.getReversalId(), validationError.getErrorMessage()),
                Map.of(
                    "reversalId", validationError.getReversalId(),
                    "errorCode", validationError.getErrorCode(),
                    "severity", severity,
                    "isRecoverable", validationError.getIsRecoverable(),
                    "correlationId", correlationId
                )
            );
        } else {
            notificationService.sendOperationsNotification(
                "Validation Error",
                String.format("Validation error in async reversal %s: %s", 
                    validationError.getReversalId(), validationError.getErrorMessage()),
                Map.of(
                    "reversalId", validationError.getReversalId(),
                    "errorCode", validationError.getErrorCode(),
                    "severity", severity,
                    "correlationId", correlationId
                )
            );
        }
        
        // Send developer notification for system errors
        if ("SYSTEM_ERROR".equals(validationError.getErrorType().toString())) {
            notificationService.sendDeveloperAlert(
                "System Validation Error",
                String.format("System validation error detected: %s", validationError.getErrorMessage()),
                Map.of(
                    "reversalId", validationError.getReversalId(),
                    "errorCode", validationError.getErrorCode(),
                    "validationField", validationError.getValidationField(),
                    "correlationId", correlationId
                )
            );
        }
    }

    private void createRemediationWorkflow(ValidationError validationError, String correlationId) {
        // Create workflow based on error type and severity
        if (validationError.getRequiresManualReview()) {
            kafkaTemplate.send("manual-remediation-required", Map.of(
                "workflowType", "VALIDATION_ERROR_REMEDIATION",
                "reversalId", validationError.getReversalId(),
                "errorCode", validationError.getErrorCode(),
                "severity", validationError.getSeverity().toString(),
                "priority", "HIGH",
                "assigneeRole", "VALIDATION_SPECIALIST",
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        } else if (!validationError.getIsRecoverable()) {
            kafkaTemplate.send("automated-remediation-required", Map.of(
                "workflowType", "AUTOMATED_VALIDATION_FIX",
                "reversalId", validationError.getReversalId(),
                "errorCode", validationError.getErrorCode(),
                "remediationStrategy", determineRemediationStrategy(validationError),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
    }

    private boolean isComplianceRelatedError(String errorCode, String errorType) {
        return errorCode.startsWith("COMPLIANCE_") ||
               errorCode.startsWith("REGULATORY_") ||
               errorCode.startsWith("AML_") ||
               errorCode.startsWith("SANCTIONS_") ||
               "COMPLIANCE_ERROR".equals(errorType);
    }

    private String determineRemediationStrategy(ValidationError validationError) {
        String errorCode = validationError.getErrorCode();
        
        if (errorCode.startsWith("AMOUNT_")) {
            return "AMOUNT_CORRECTION";
        } else if (errorCode.startsWith("CURRENCY_")) {
            return "CURRENCY_VALIDATION";
        } else if (errorCode.startsWith("ACCOUNT_")) {
            return "ACCOUNT_VERIFICATION";
        } else if (errorCode.startsWith("REFERENCE_")) {
            return "REFERENCE_UPDATE";
        } else {
            return "MANUAL_REVIEW";
        }
    }

    private void processAsyncReversalValidationErrorFallback(
            String reversalId,
            String originalTransactionId,
            String paymentId,
            String errorCode,
            String errorMessage,
            String errorType,
            String severity,
            String validationField,
            String expectedValue,
            String actualValue,
            List<String> validationRules,
            Boolean isRecoverable,
            Boolean requiresManualReview,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for async reversal validation error - reversalId: {}, correlationId: {}, error: {}", 
            reversalId, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("reversalId", reversalId);
        fallbackEvent.put("originalTransactionId", originalTransactionId);
        fallbackEvent.put("paymentId", paymentId);
        fallbackEvent.put("errorCode", errorCode);
        fallbackEvent.put("errorMessage", errorMessage);
        fallbackEvent.put("errorType", errorType);
        fallbackEvent.put("severity", severity);
        fallbackEvent.put("validationField", validationField);
        fallbackEvent.put("expectedValue", expectedValue);
        fallbackEvent.put("actualValue", actualValue);
        fallbackEvent.put("validationRules", validationRules);
        fallbackEvent.put("isRecoverable", isRecoverable);
        fallbackEvent.put("requiresManualReview", requiresManualReview);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("async-reversal-validation-errors-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Async reversal validation error message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
            topic, partition, offset, exceptionMessage);
        
        try {
            Map<String, Object> dltEvent = Map.of(
                "originalTopic", topic,
                "partition", partition,
                "offset", offset,
                "message", message,
                "error", exceptionMessage,
                "timestamp", Instant.now().toString(),
                "dltReason", "MAX_RETRIES_EXCEEDED"
            );
            
            kafkaTemplate.send("async-reversal-validation-errors-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Async Reversal Validation Error Processing Failed",
                String.format("CRITICAL: Failed to process validation error after max retries. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process DLT message: {}", e.getMessage(), e);
        }
    }

}