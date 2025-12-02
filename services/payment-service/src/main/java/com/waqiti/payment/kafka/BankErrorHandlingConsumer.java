package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.model.*;
import com.waqiti.payment.repository.BankErrorRepository;
import com.waqiti.payment.service.*;
import com.waqiti.common.audit.AuditService;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for bank error handling events
 * Handles bank-related errors and failures with enterprise patterns
 * 
 * Critical for: Payment processing, bank integration, error recovery
 * SLA: Must process bank errors within 15 seconds for immediate remediation
 */
@Component
@Slf4j
public class BankErrorHandlingConsumer {

    private final BankErrorRepository bankErrorRepository;
    private final PaymentService paymentService;
    private final BankIntegrationService bankIntegrationService;
    private final ErrorRecoveryService errorRecoveryService;
    private final NotificationService notificationService;
    private final BankProviderService bankProviderService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter bankErrorCounter;
    private final Counter criticalBankErrorCounter;
    private final Counter systemBankErrorCounter;
    private final Timer errorProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public BankErrorHandlingConsumer(
            BankErrorRepository bankErrorRepository,
            PaymentService paymentService,
            BankIntegrationService bankIntegrationService,
            ErrorRecoveryService errorRecoveryService,
            NotificationService notificationService,
            BankProviderService bankProviderService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.bankErrorRepository = bankErrorRepository;
        this.paymentService = paymentService;
        this.bankIntegrationService = bankIntegrationService;
        this.errorRecoveryService = errorRecoveryService;
        this.notificationService = notificationService;
        this.bankProviderService = bankProviderService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.bankErrorCounter = Counter.builder("bank.error.handling.events")
            .description("Count of bank error handling events")
            .register(meterRegistry);
        
        this.criticalBankErrorCounter = Counter.builder("bank.error.handling.critical.events")
            .description("Count of critical bank error events")
            .register(meterRegistry);
        
        this.systemBankErrorCounter = Counter.builder("bank.error.handling.system.events")
            .description("Count of system bank error events")
            .register(meterRegistry);
        
        this.errorProcessingTimer = Timer.builder("bank.error.handling.processing.duration")
            .description("Time taken to process bank error events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "bank-error-handling",
        groupId = "bank-error-handling-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "bank-error-handling-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleBankErrorEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received bank error handling event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String transactionId = (String) eventData.get("transactionId");
            String paymentId = (String) eventData.get("paymentId");
            String bankCode = (String) eventData.get("bankCode");
            String bankName = (String) eventData.get("bankName");
            String errorCode = (String) eventData.get("errorCode");
            String errorMessage = (String) eventData.get("errorMessage");
            String errorType = (String) eventData.get("errorType");
            String severity = (String) eventData.getOrDefault("severity", "MEDIUM");
            BigDecimal transactionAmount = eventData.get("transactionAmount") != null ? 
                new BigDecimal(eventData.get("transactionAmount").toString()) : null;
            String currency = (String) eventData.get("currency");
            String operation = (String) eventData.get("operation");
            Boolean isRetryable = (Boolean) eventData.getOrDefault("isRetryable", false);
            Boolean requiresManualIntervention = (Boolean) eventData.getOrDefault("requiresManualIntervention", false);
            String userId = (String) eventData.get("userId");
            String accountId = (String) eventData.get("accountId");
            
            String correlationId = String.format("bank-error-handling-%s-%d", 
                transactionId != null ? transactionId : "unknown", System.currentTimeMillis());
            
            log.error("CRITICAL: Processing bank error - transactionId: {}, bank: {}, errorCode: {}, severity: {}, correlationId: {}", 
                transactionId, bankName, errorCode, severity, correlationId);
            
            bankErrorCounter.increment();
            
            processBankError(transactionId, paymentId, bankCode, bankName, errorCode, errorMessage,
                errorType, severity, transactionAmount, currency, operation, isRetryable,
                requiresManualIntervention, userId, accountId, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(errorProcessingTimer);
            
            log.info("Successfully processed bank error handling event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process bank error handling event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Bank error handling processing failed", e);
        }
    }

    @CircuitBreaker(name = "bank-error", fallbackMethod = "processBankErrorFallback")
    @Retry(name = "bank-error")
    private void processBankError(
            String transactionId,
            String paymentId,
            String bankCode,
            String bankName,
            String errorCode,
            String errorMessage,
            String errorType,
            String severity,
            BigDecimal transactionAmount,
            String currency,
            String operation,
            Boolean isRetryable,
            Boolean requiresManualIntervention,
            String userId,
            String accountId,
            Map<String, Object> eventData,
            String correlationId) {
        
        // Create bank error record
        BankError bankError = BankError.builder()
            .id(java.util.UUID.randomUUID().toString())
            .transactionId(transactionId)
            .paymentId(paymentId)
            .bankCode(bankCode)
            .bankName(bankName)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .errorType(BankErrorType.fromString(errorType))
            .severity(ErrorSeverity.fromString(severity))
            .transactionAmount(transactionAmount)
            .currency(currency)
            .operation(operation)
            .isRetryable(isRetryable)
            .requiresManualIntervention(requiresManualIntervention)
            .userId(userId)
            .accountId(accountId)
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        bankErrorRepository.save(bankError);
        
        // Handle critical errors
        if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
            handleCriticalBankError(bankError, correlationId);
        }
        
        // Handle system errors
        if ("SYSTEM_ERROR".equals(errorType) || "CONNECTIVITY_ERROR".equals(errorType)) {
            handleSystemBankError(bankError, correlationId);
        }
        
        // Attempt error recovery if retryable
        if (isRetryable && !requiresManualIntervention) {
            attemptErrorRecovery(bankError, correlationId);
        }
        
        // Update bank provider status
        updateBankProviderStatus(bankError, correlationId);
        
        // Update transaction/payment status
        updateTransactionStatus(bankError, correlationId);
        
        // Send notifications
        sendBankErrorNotifications(bankError, correlationId);
        
        // Create escalation if needed
        if (requiresManualIntervention) {
            createManualEscalation(bankError, correlationId);
        }
        
        // Update bank metrics
        updateBankMetrics(bankError, correlationId);
        
        // Publish bank error analytics
        publishBankErrorAnalytics(bankError, correlationId);
        
        // Audit the bank error
        auditService.logBankErrorEvent(
            "BANK_ERROR_PROCESSED",
            bankError.getId(),
            Map.of(
                "transactionId", transactionId,
                "paymentId", paymentId,
                "bankCode", bankCode,
                "bankName", bankName,
                "errorCode", errorCode,
                "errorMessage", errorMessage,
                "errorType", errorType,
                "severity", severity,
                "operation", operation,
                "isRetryable", isRetryable,
                "requiresManualIntervention", requiresManualIntervention,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.error("CRITICAL: Bank error processed - transactionId: {}, bank: {}, errorCode: {}, severity: {}, correlationId: {}", 
            transactionId, bankName, errorCode, severity, correlationId);
    }

    private void handleCriticalBankError(BankError bankError, String correlationId) {
        criticalBankErrorCounter.increment();
        
        log.error("CRITICAL BANK ERROR: Critical bank error detected - transactionId: {}, bank: {}, errorCode: {}, correlationId: {}", 
            bankError.getTransactionId(), bankError.getBankName(), bankError.getErrorCode(), correlationId);
        
        // Send critical alert
        kafkaTemplate.send("critical-bank-error-alerts", Map.of(
            "transactionId", bankError.getTransactionId(),
            "paymentId", bankError.getPaymentId(),
            "bankCode", bankError.getBankCode(),
            "bankName", bankError.getBankName(),
            "errorCode", bankError.getErrorCode(),
            "errorMessage", bankError.getErrorMessage(),
            "severity", "CRITICAL",
            "transactionAmount", bankError.getTransactionAmount(),
            "currency", bankError.getCurrency(),
            "operation", bankError.getOperation(),
            "requiresImmediateAction", true,
            "alertType", "CRITICAL_BANK_ERROR",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Notify executive team
        notificationService.sendExecutiveAlert(
            "Critical Bank Error",
            String.format("Critical bank error with %s: %s - %s", 
                bankError.getBankName(), bankError.getErrorCode(), bankError.getErrorMessage()),
            Map.of(
                "transactionId", bankError.getTransactionId(),
                "bankName", bankError.getBankName(),
                "errorCode", bankError.getErrorCode(),
                "transactionAmount", bankError.getTransactionAmount(),
                "currency", bankError.getCurrency(),
                "correlationId", correlationId
            )
        );
    }

    private void handleSystemBankError(BankError bankError, String correlationId) {
        systemBankErrorCounter.increment();
        
        log.error("SYSTEM BANK ERROR: System bank error detected - transactionId: {}, bank: {}, errorType: {}, correlationId: {}", 
            bankError.getTransactionId(), bankError.getBankName(), bankError.getErrorType(), correlationId);
        
        // Check bank connectivity
        boolean bankOnline = bankIntegrationService.checkBankConnectivity(bankError.getBankCode());
        
        if (!bankOnline) {
            // Bank is offline - trigger failover
            kafkaTemplate.send("bank-failover-required", Map.of(
                "bankCode", bankError.getBankCode(),
                "bankName", bankError.getBankName(),
                "errorType", bankError.getErrorType().toString(),
                "failoverReason", "BANK_CONNECTIVITY_ISSUE",
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
            
            // Notify operations team
            notificationService.sendOperationsAlert(
                "Bank Connectivity Issue",
                String.format("Bank %s appears to be offline. Failover initiated.", bankError.getBankName()),
                Map.of(
                    "bankCode", bankError.getBankCode(),
                    "bankName", bankError.getBankName(),
                    "correlationId", correlationId
                )
            );
        }
        
        // Send system error alert
        kafkaTemplate.send("system-bank-error-alerts", Map.of(
            "bankCode", bankError.getBankCode(),
            "bankName", bankError.getBankName(),
            "errorType", bankError.getErrorType().toString(),
            "errorCode", bankError.getErrorCode(),
            "bankOnline", bankOnline,
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void attemptErrorRecovery(BankError bankError, String correlationId) {
        log.info("Attempting error recovery for bank error - transactionId: {}, errorCode: {}, correlationId: {}", 
            bankError.getTransactionId(), bankError.getErrorCode(), correlationId);
        
        try {
            // Attempt automatic recovery
            boolean recoverySuccessful = errorRecoveryService.attemptBankErrorRecovery(bankError, correlationId);
            
            if (recoverySuccessful) {
                // Update bank error status
                bankError.setRecoveryAttempted(true);
                bankError.setRecoverySuccessful(true);
                bankError.setRecoveryTimestamp(LocalDateTime.now());
                bankErrorRepository.save(bankError);
                
                // Publish recovery success event
                kafkaTemplate.send("bank-error-recovery-success", Map.of(
                    "transactionId", bankError.getTransactionId(),
                    "errorCode", bankError.getErrorCode(),
                    "bankCode", bankError.getBankCode(),
                    "recoveryMethod", "AUTOMATIC",
                    "correlationId", correlationId,
                    "timestamp", Instant.now().toString()
                ));
                
                // Retry original operation
                if (bankError.getPaymentId() != null) {
                    kafkaTemplate.send("payment-retry", Map.of(
                        "paymentId", bankError.getPaymentId(),
                        "retryReason", "BANK_ERROR_RECOVERED",
                        "correlationId", correlationId,
                        "timestamp", Instant.now().toString()
                    ));
                }
                
                log.info("Bank error recovery successful - transactionId: {}, correlationId: {}", 
                    bankError.getTransactionId(), correlationId);
            } else {
                // Mark recovery failed
                bankError.setRecoveryAttempted(true);
                bankError.setRecoverySuccessful(false);
                bankError.setRecoveryTimestamp(LocalDateTime.now());
                bankErrorRepository.save(bankError);
                
                log.warn("Bank error recovery failed - transactionId: {}, correlationId: {}", 
                    bankError.getTransactionId(), correlationId);
            }
            
        } catch (Exception e) {
            log.error("Error during bank error recovery attempt - transactionId: {}, correlationId: {}, error: {}", 
                bankError.getTransactionId(), correlationId, e.getMessage(), e);
            
            bankError.setRecoveryAttempted(true);
            bankError.setRecoverySuccessful(false);
            bankError.setRecoveryTimestamp(LocalDateTime.now());
            bankError.setRecoveryError(e.getMessage());
            bankErrorRepository.save(bankError);
        }
    }

    private void updateBankProviderStatus(BankError bankError, String correlationId) {
        // Update bank provider health status
        bankProviderService.updateProviderHealth(
            bankError.getBankCode(),
            bankError.getErrorType(),
            bankError.getSeverity(),
            correlationId
        );
        
        // Check if bank should be temporarily disabled
        boolean shouldDisable = bankProviderService.shouldTemporarilyDisableBank(
            bankError.getBankCode(),
            bankError.getErrorCode()
        );
        
        if (shouldDisable) {
            kafkaTemplate.send("bank-provider-disable", Map.of(
                "bankCode", bankError.getBankCode(),
                "bankName", bankError.getBankName(),
                "disableReason", "HIGH_ERROR_RATE",
                "errorCode", bankError.getErrorCode(),
                "duration", "TEMPORARY",
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
    }

    private void updateTransactionStatus(BankError bankError, String correlationId) {
        // Update transaction status based on error
        if (bankError.getTransactionId() != null) {
            TransactionStatus newStatus = determineTransactionStatus(bankError);
            
            paymentService.updateTransactionStatus(
                bankError.getTransactionId(),
                newStatus,
                String.format("Bank error: %s - %s", bankError.getErrorCode(), bankError.getErrorMessage())
            );
        }
        
        // Update payment status if applicable
        if (bankError.getPaymentId() != null) {
            PaymentStatus newPaymentStatus = determinePaymentStatus(bankError);
            
            paymentService.updatePaymentStatus(
                bankError.getPaymentId(),
                newPaymentStatus,
                String.format("Bank error: %s", bankError.getErrorMessage())
            );
        }
    }

    private TransactionStatus determineTransactionStatus(BankError bankError) {
        if (bankError.getIsRetryable()) {
            return TransactionStatus.PENDING_RETRY;
        } else if (bankError.getRequiresManualIntervention()) {
            return TransactionStatus.PENDING_MANUAL_REVIEW;
        } else {
            return TransactionStatus.FAILED;
        }
    }

    private PaymentStatus determinePaymentStatus(BankError bankError) {
        if (bankError.getIsRetryable()) {
            return PaymentStatus.PENDING_RETRY;
        } else if (bankError.getRequiresManualIntervention()) {
            return PaymentStatus.REQUIRES_INTERVENTION;
        } else {
            return PaymentStatus.FAILED;
        }
    }

    private void sendBankErrorNotifications(BankError bankError, String correlationId) {
        // Notify operations team
        notificationService.sendOperationsNotification(
            "Bank Error Detected",
            String.format("Bank error with %s: %s - %s", 
                bankError.getBankName(), bankError.getErrorCode(), bankError.getErrorMessage()),
            Map.of(
                "transactionId", bankError.getTransactionId(),
                "bankName", bankError.getBankName(),
                "errorCode", bankError.getErrorCode(),
                "severity", bankError.getSeverity().toString(),
                "isRetryable", bankError.getIsRetryable(),
                "correlationId", correlationId
            )
        );
        
        // Notify customer if transaction failed permanently
        if (!bankError.getIsRetryable() && bankError.getUserId() != null) {
            notificationService.sendCustomerNotification(
                bankError.getUserId(),
                "Transaction Failed",
                "We're sorry, but your transaction could not be completed due to a bank processing issue. Please try again later or contact support.",
                Map.of(
                    "transactionId", bankError.getTransactionId(),
                    "errorCode", bankError.getErrorCode(),
                    "correlationId", correlationId
                )
            );
        }
    }

    private void createManualEscalation(BankError bankError, String correlationId) {
        // Create manual intervention workflow
        kafkaTemplate.send("manual-intervention-required", Map.of(
            "workflowType", "BANK_ERROR_RESOLUTION",
            "transactionId", bankError.getTransactionId(),
            "paymentId", bankError.getPaymentId(),
            "bankCode", bankError.getBankCode(),
            "errorCode", bankError.getErrorCode(),
            "severity", bankError.getSeverity().toString(),
            "priority", "HIGH",
            "assigneeRole", "BANK_OPERATIONS_SPECIALIST",
            "escalationReason", "MANUAL_INTERVENTION_REQUIRED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void updateBankMetrics(BankError bankError, String correlationId) {
        // Update bank error metrics
        kafkaTemplate.send("bank-metrics-update", Map.of(
            "metricType", "BANK_ERROR",
            "bankCode", bankError.getBankCode(),
            "errorCode", bankError.getErrorCode(),
            "errorType", bankError.getErrorType().toString(),
            "severity", bankError.getSeverity().toString(),
            "operation", bankError.getOperation(),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void publishBankErrorAnalytics(BankError bankError, String correlationId) {
        // Publish bank error analytics
        kafkaTemplate.send("bank-error-analytics", Map.of(
            "errorId", bankError.getId(),
            "transactionId", bankError.getTransactionId(),
            "bankCode", bankError.getBankCode(),
            "bankName", bankError.getBankName(),
            "errorCode", bankError.getErrorCode(),
            "errorType", bankError.getErrorType().toString(),
            "severity", bankError.getSeverity().toString(),
            "operation", bankError.getOperation(),
            "transactionAmount", bankError.getTransactionAmount(),
            "currency", bankError.getCurrency(),
            "isRetryable", bankError.getIsRetryable(),
            "requiresManualIntervention", bankError.getRequiresManualIntervention(),
            "eventType", "BANK_ERROR_OCCURRED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void processBankErrorFallback(
            String transactionId,
            String paymentId,
            String bankCode,
            String bankName,
            String errorCode,
            String errorMessage,
            String errorType,
            String severity,
            BigDecimal transactionAmount,
            String currency,
            String operation,
            Boolean isRetryable,
            Boolean requiresManualIntervention,
            String userId,
            String accountId,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for bank error handling - transactionId: {}, correlationId: {}, error: {}", 
            transactionId, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("transactionId", transactionId);
        fallbackEvent.put("paymentId", paymentId);
        fallbackEvent.put("bankCode", bankCode);
        fallbackEvent.put("bankName", bankName);
        fallbackEvent.put("errorCode", errorCode);
        fallbackEvent.put("errorMessage", errorMessage);
        fallbackEvent.put("errorType", errorType);
        fallbackEvent.put("severity", severity);
        fallbackEvent.put("transactionAmount", transactionAmount);
        fallbackEvent.put("currency", currency);
        fallbackEvent.put("operation", operation);
        fallbackEvent.put("isRetryable", isRetryable);
        fallbackEvent.put("requiresManualIntervention", requiresManualIntervention);
        fallbackEvent.put("userId", userId);
        fallbackEvent.put("accountId", accountId);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("bank-error-handling-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Bank error handling message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("bank-error-handling-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Bank Error Handling Processing Failed",
                String.format("CRITICAL: Failed to process bank error after max retries. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process DLT message: {}", e.getMessage(), e);
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEvents.containsKey(eventId);
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, String.valueOf(System.currentTimeMillis()));
        
        // Cleanup old entries
        processedEvents.entrySet().removeIf(entry -> {
            long timestamp = Long.parseLong(entry.getValue());
            return System.currentTimeMillis() - timestamp > IDEMPOTENCY_TTL_MS;
        });
    }
}