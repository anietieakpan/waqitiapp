package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.domain.Account;
import com.waqiti.payment.domain.AccountControl;
import com.waqiti.payment.domain.AccountControlAction;
import com.waqiti.payment.repository.AccountRepository;
import com.waqiti.payment.repository.AccountControlRepository;
import com.waqiti.payment.service.AccountControlService;
import com.waqiti.payment.service.PaymentNotificationService;
import com.waqiti.payment.service.ComplianceReportingService;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AccountControlConsumer {

    private final AccountRepository accountRepository;
    private final AccountControlRepository controlRepository;
    private final AccountControlService controlService;
    private final PaymentNotificationService notificationService;
    private final ComplianceReportingService complianceService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;
    
    private final Counter accountControlCounter;
    private final Counter sanctionsControlCounter;
    private final Counter freezeControlCounter;
    private final Timer controlProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AccountControlConsumer(
            AccountRepository accountRepository,
            AccountControlRepository controlRepository,
            AccountControlService controlService,
            PaymentNotificationService notificationService,
            ComplianceReportingService complianceService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.accountRepository = accountRepository;
        this.controlRepository = controlRepository;
        this.controlService = controlService;
        this.notificationService = notificationService;
        this.complianceService = complianceService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.accountControlCounter = Counter.builder("account.control.events")
            .description("Count of account control events")
            .register(meterRegistry);
        
        this.sanctionsControlCounter = Counter.builder("account.sanctions.control.events")
            .description("Count of sanctions-related control actions")
            .register(meterRegistry);
        
        this.freezeControlCounter = Counter.builder("account.freeze.control.events")
            .description("Count of freeze control actions")
            .register(meterRegistry);
        
        this.controlProcessingTimer = Timer.builder("account.control.processing.duration")
            .description("Time taken to process account control events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "account-control",
        groupId = "account-control-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "account-control-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleAccountControlEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received account control event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String referenceNumber = (String) eventData.get("referenceNumber");
            String action = (String) eventData.get("action");
            String reason = (String) eventData.get("reason");
            Object timestampObj = eventData.get("timestamp");
            String userId = (String) eventData.get("userId");
            String scope = (String) eventData.get("scope");
            String severity = (String) eventData.getOrDefault("severity", "HIGH");
            
            String correlationId = String.format("account-control-%s-%s-%d", 
                referenceNumber, action, System.currentTimeMillis());
            
            log.warn("CRITICAL: Processing account control action - ref: {}, action: {}, reason: {}, correlationId: {}", 
                referenceNumber, action, reason, correlationId);
            
            accountControlCounter.increment();
            
            processAccountControl(referenceNumber, action, reason, userId, scope, severity, 
                eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();

            controlProcessingTimer.stop(sample);

            log.info("Successfully processed account control event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process account control event {}: {}", eventId, e.getMessage(), e);

            // Send to DLQ with context
            try {
                Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
                String referenceNumber = (String) eventData.get("referenceNumber");
                String action = (String) eventData.get("action");
                String userId = (String) eventData.get("userId");

                dlqHandler.handleFailedMessage(
                    topic,
                    message,
                    e,
                    Map.of(
                        "referenceNumber", referenceNumber != null ? referenceNumber : "unknown",
                        "action", action != null ? action : "unknown",
                        "userId", userId != null ? userId : "unknown",
                        "partition", String.valueOf(partition),
                        "offset", String.valueOf(offset)
                    )
                );
            } catch (Exception parseEx) {
                log.error("Failed to parse event for DLQ: {}", parseEx.getMessage());
                dlqHandler.handleFailedMessage(
                    topic,
                    message,
                    e,
                    Map.of(
                        "partition", String.valueOf(partition),
                        "offset", String.valueOf(offset),
                        "parseError", parseEx.getMessage()
                    )
                );
            }

            throw new RuntimeException("Account control processing failed", e);
        }
    }

    @CircuitBreaker(name = "account", fallbackMethod = "processAccountControlFallback")
    @Retry(name = "account")
    private void processAccountControl(
            String referenceNumber,
            String action,
            String reason,
            String userId,
            String scope,
            String severity,
            Map<String, Object> eventData,
            String correlationId) {
        
        AccountControlAction controlAction = AccountControlAction.valueOf(action);
        
        AccountControl control = AccountControl.builder()
            .referenceNumber(referenceNumber)
            .action(controlAction)
            .reason(reason)
            .userId(userId)
            .scope(scope)
            .severity(severity)
            .appliedAt(LocalDateTime.now())
            .isActive(true)
            .correlationId(correlationId)
            .build();
        
        controlRepository.save(control);
        
        switch (controlAction) {
            case FREEZE_ACCOUNTS -> {
                freezeControlCounter.increment();
                handleAccountFreeze(control, correlationId);
            }
            case SUSPEND_PAYMENTS -> {
                handlePaymentSuspension(control, correlationId);
            }
            case RESTRICT_TRANSACTIONS -> {
                handleTransactionRestriction(control, correlationId);
            }
            case BLOCK_CARD_OPERATIONS -> {
                handleCardBlock(control, correlationId);
            }
            default -> {
                log.warn("Unknown control action: {}", action);
            }
        }
        
        if ("SANCTIONS_HIT".equals(reason)) {
            sanctionsControlCounter.increment();
            handleSanctionsControl(control, correlationId);
        }
        
        controlService.applyAccountControl(control, correlationId);
        
        notificationService.sendAccountControlNotification(
            userId,
            action,
            reason,
            correlationId
        );
        
        kafkaTemplate.send("account-control-applied", Map.of(
            "referenceNumber", referenceNumber,
            "userId", userId,
            "action", action,
            "reason", reason,
            "scope", scope,
            "severity", severity,
            "eventType", "ACCOUNT_CONTROL_APPLIED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logAccountEvent(
            "ACCOUNT_CONTROL_APPLIED",
            userId,
            Map.of(
                "referenceNumber", referenceNumber,
                "action", action,
                "reason", reason,
                "scope", scope,
                "severity", severity,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.error("CRITICAL: Account control applied - ref: {}, action: {}, reason: {}, correlationId: {}", 
            referenceNumber, action, reason, correlationId);
    }

    private void handleAccountFreeze(AccountControl control, String correlationId) {
        log.error("COMPLIANCE ALERT: Account freeze control applied - ref: {}, correlationId: {}", 
            control.getReferenceNumber(), correlationId);
        
        complianceService.reportAccountFreeze(control, correlationId);
        
        kafkaTemplate.send("account-freeze-controls", Map.of(
            "referenceNumber", control.getReferenceNumber(),
            "userId", control.getUserId(),
            "reason", control.getReason(),
            "severity", "CRITICAL",
            "regulatoryNotificationRequired", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handlePaymentSuspension(AccountControl control, String correlationId) {
        log.warn("Payment suspension control applied - ref: {}, correlationId: {}", 
            control.getReferenceNumber(), correlationId);
        
        kafkaTemplate.send("payment-suspension-alerts", Map.of(
            "referenceNumber", control.getReferenceNumber(),
            "userId", control.getUserId(),
            "reason", control.getReason(),
            "severity", control.getSeverity(),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleTransactionRestriction(AccountControl control, String correlationId) {
        log.warn("Transaction restriction control applied - ref: {}, correlationId: {}", 
            control.getReferenceNumber(), correlationId);
        
        kafkaTemplate.send("transaction-restriction-alerts", Map.of(
            "referenceNumber", control.getReferenceNumber(),
            "userId", control.getUserId(),
            "reason", control.getReason(),
            "severity", control.getSeverity(),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleCardBlock(AccountControl control, String correlationId) {
        log.warn("Card block control applied - ref: {}, correlationId: {}", 
            control.getReferenceNumber(), correlationId);
        
        kafkaTemplate.send("card-block-alerts", Map.of(
            "referenceNumber", control.getReferenceNumber(),
            "userId", control.getUserId(),
            "reason", control.getReason(),
            "severity", control.getSeverity(),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleSanctionsControl(AccountControl control, String correlationId) {
        log.error("COMPLIANCE ALERT: Sanctions control applied - ref: {}, correlationId: {}", 
            control.getReferenceNumber(), correlationId);
        
        complianceService.reportSanctionsControl(control, correlationId);
        
        kafkaTemplate.send("sanctions-control-alerts", Map.of(
            "referenceNumber", control.getReferenceNumber(),
            "userId", control.getUserId(),
            "action", control.getAction().toString(),
            "reason", control.getReason(),
            "severity", "CRITICAL",
            "regulatoryNotificationRequired", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        notificationService.sendRegulatoryAlert(
            "Sanctions Control Applied",
            String.format("Account control %s applied due to sanctions violation", control.getAction()),
            Map.of(
                "referenceNumber", control.getReferenceNumber(),
                "userId", control.getUserId(),
                "action", control.getAction().toString(),
                "correlationId", correlationId
            )
        );
    }

    private void processAccountControlFallback(
            String referenceNumber,
            String action,
            String reason,
            String userId,
            String scope,
            String severity,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for account control - ref: {}, action: {}, correlationId: {}, error: {}", 
            referenceNumber, action, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("referenceNumber", referenceNumber);
        fallbackEvent.put("action", action);
        fallbackEvent.put("reason", reason);
        fallbackEvent.put("userId", userId);
        fallbackEvent.put("scope", scope);
        fallbackEvent.put("severity", severity);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("account-control-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Account control message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("account-control-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Account Control Processing Failed",
                String.format("CRITICAL: Failed to process account control after max retries. Error: %s", exceptionMessage),
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
        
        processedEvents.entrySet().removeIf(entry -> {
            long timestamp = Long.parseLong(entry.getValue());
            return System.currentTimeMillis() - timestamp > IDEMPOTENCY_TTL_MS;
        });
    }
}