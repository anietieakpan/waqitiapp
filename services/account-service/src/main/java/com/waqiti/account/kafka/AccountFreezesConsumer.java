package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.domain.Account;
import com.waqiti.account.domain.AccountFreeze;
import com.waqiti.account.domain.AccountStatus;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.repository.AccountFreezeRepository;
import com.waqiti.account.service.AccountFreezeService;
import com.waqiti.account.service.AccountNotificationService;
import com.waqiti.account.service.ComplianceReportingService;
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
public class AccountFreezesConsumer {

    private final AccountRepository accountRepository;
    private final AccountFreezeRepository freezeRepository;
    private final AccountFreezeService freezeService;
    private final AccountNotificationService notificationService;
    private final ComplianceReportingService complianceService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter accountFrozenCounter;
    private final Counter sanctionsFreezeCounter;
    private final Counter fraudFreezeCounter;
    private final Timer freezeProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AccountFreezesConsumer(
            AccountRepository accountRepository,
            AccountFreezeRepository freezeRepository,
            AccountFreezeService freezeService,
            AccountNotificationService notificationService,
            ComplianceReportingService complianceService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.accountRepository = accountRepository;
        this.freezeRepository = freezeRepository;
        this.freezeService = freezeService;
        this.notificationService = notificationService;
        this.complianceService = complianceService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.accountFrozenCounter = Counter.builder("account.frozen.events")
            .description("Count of account freeze events")
            .register(meterRegistry);
        
        this.sanctionsFreezeCounter = Counter.builder("account.sanctions.freeze.events")
            .description("Count of sanctions-related freezes")
            .register(meterRegistry);
        
        this.fraudFreezeCounter = Counter.builder("account.fraud.freeze.events")
            .description("Count of fraud-related freezes")
            .register(meterRegistry);
        
        this.freezeProcessingTimer = Timer.builder("account.freeze.processing.duration")
            .description("Time taken to process account freeze events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "account-freezes",
        groupId = "account-freezes-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "account-freezes-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleAccountFreezeEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received account freeze event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String userId = (String) eventData.get("userId");
            String freezeId = (String) eventData.get("freezeId");
            String freezeReason = (String) eventData.get("freezeReason");
            String freezeScope = (String) eventData.get("freezeScope");
            String severity = (String) eventData.get("severity");
            String description = (String) eventData.get("description");
            Boolean requiresManualReview = (Boolean) eventData.get("requiresManualReview");
            Boolean notifyRegulators = (Boolean) eventData.getOrDefault("notifyRegulators", false);
            String freezingSystem = (String) eventData.get("freezingSystem");
            
            String correlationId = String.format("account-freeze-%s-%s-%d", 
                userId, freezeId, System.currentTimeMillis());
            
            log.warn("CRITICAL: Processing account freeze - userId: {}, freezeId: {}, reason: {}, correlationId: {}", 
                userId, freezeId, freezeReason, correlationId);
            
            accountFrozenCounter.increment();
            
            processAccountFreeze(userId, freezeId, freezeReason, freezeScope, severity, description,
                requiresManualReview, notifyRegulators, freezingSystem, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(freezeProcessingTimer);
            
            log.info("Successfully processed account freeze event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process account freeze event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Account freeze processing failed", e);
        }
    }

    @CircuitBreaker(name = "account", fallbackMethod = "processAccountFreezeFallback")
    @Retry(name = "account")
    private void processAccountFreeze(
            String userId,
            String freezeId,
            String freezeReason,
            String freezeScope,
            String severity,
            String description,
            Boolean requiresManualReview,
            Boolean notifyRegulators,
            String freezingSystem,
            Map<String, Object> eventData,
            String correlationId) {
        
        Account account = accountRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("Account not found for user: " + userId));
        
        AccountFreeze freeze = AccountFreeze.builder()
            .id(freezeId)
            .accountId(account.getId())
            .userId(userId)
            .reason(freezeReason)
            .scope(freezeScope)
            .severity(severity)
            .description(description)
            .requiresManualReview(requiresManualReview)
            .notifyRegulators(notifyRegulators)
            .freezingSystem(freezingSystem)
            .frozenAt(LocalDateTime.now())
            .isActive(true)
            .build();
        
        freezeRepository.save(freeze);
        
        AccountStatus previousStatus = account.getStatus();
        account.setStatus(AccountStatus.FROZEN);
        account.setFrozenAt(LocalDateTime.now());
        account.setFreezeReason(freezeReason);
        accountRepository.save(account);
        
        freezeService.applyAccountFreeze(account, freeze, correlationId);
        
        if ("SANCTIONS_VIOLATION".equals(freezeReason)) {
            sanctionsFreezeCounter.increment();
            handleSanctionsFreeze(account, freeze, correlationId);
        } else if (freezeReason != null && freezeReason.contains("FRAUD")) {
            fraudFreezeCounter.increment();
            handleFraudFreeze(account, freeze, correlationId);
        }
        
        if (notifyRegulators) {
            complianceService.notifyRegulatorsOfFreeze(
                account,
                freeze,
                freezeReason,
                correlationId
            );
        }
        
        notificationService.sendAccountFrozenNotification(
            userId,
            freezeReason,
            description,
            correlationId
        );
        
        kafkaTemplate.send("account-status-changes", Map.of(
            "accountId", account.getId(),
            "userId", userId,
            "previousStatus", previousStatus.toString(),
            "newStatus", "FROZEN",
            "reason", freezeReason,
            "freezeId", freezeId,
            "eventType", "ACCOUNT_FROZEN",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logAccountEvent(
            "ACCOUNT_FROZEN",
            account.getId(),
            Map.of(
                "userId", userId,
                "freezeId", freezeId,
                "freezeReason", freezeReason,
                "freezeScope", freezeScope,
                "severity", severity,
                "description", description,
                "previousStatus", previousStatus.toString(),
                "requiresManualReview", requiresManualReview,
                "notifyRegulators", notifyRegulators,
                "freezingSystem", freezingSystem,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        if (requiresManualReview) {
            kafkaTemplate.send("compliance-review-required", Map.of(
                "reviewType", "ACCOUNT_FREEZE_REVIEW",
                "accountId", account.getId(),
                "userId", userId,
                "freezeId", freezeId,
                "freezeReason", freezeReason,
                "severity", severity,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        log.error("CRITICAL: Account frozen - userId: {}, freezeId: {}, reason: {}, correlationId: {}", 
            userId, freezeId, freezeReason, correlationId);
    }

    private void handleSanctionsFreeze(Account account, AccountFreeze freeze, String correlationId) {
        log.error("COMPLIANCE ALERT: Sanctions freeze applied - userId: {}, freezeId: {}, correlationId: {}", 
            account.getUserId(), freeze.getId(), correlationId);
        
        complianceService.reportSanctionsFreeze(account, freeze, correlationId);
        
        kafkaTemplate.send("sanctions-freeze-alerts", Map.of(
            "accountId", account.getId(),
            "userId", account.getUserId(),
            "freezeId", freeze.getId(),
            "reason", freeze.getReason(),
            "severity", "CRITICAL",
            "regulatoryNotificationRequired", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        notificationService.sendRegulatoryAlert(
            "Sanctions Freeze Applied",
            String.format("Account %s frozen due to sanctions violation", account.getUserId()),
            Map.of(
                "accountId", account.getId(),
                "userId", account.getUserId(),
                "freezeId", freeze.getId(),
                "correlationId", correlationId
            )
        );
    }

    private void handleFraudFreeze(Account account, AccountFreeze freeze, String correlationId) {
        log.error("FRAUD ALERT: Fraud freeze applied - userId: {}, freezeId: {}, correlationId: {}", 
            account.getUserId(), freeze.getId(), correlationId);
        
        kafkaTemplate.send("fraud-freeze-alerts", Map.of(
            "accountId", account.getId(),
            "userId", account.getUserId(),
            "freezeId", freeze.getId(),
            "reason", freeze.getReason(),
            "severity", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void processAccountFreezeFallback(
            String userId,
            String freezeId,
            String freezeReason,
            String freezeScope,
            String severity,
            String description,
            Boolean requiresManualReview,
            Boolean notifyRegulators,
            String freezingSystem,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for account freeze - userId: {}, freezeId: {}, correlationId: {}, error: {}", 
            userId, freezeId, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("userId", userId);
        fallbackEvent.put("freezeId", freezeId);
        fallbackEvent.put("freezeReason", freezeReason);
        fallbackEvent.put("freezeScope", freezeScope);
        fallbackEvent.put("severity", severity);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("account-freeze-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Account freeze message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("account-freeze-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Account Freeze Processing Failed",
                String.format("CRITICAL: Failed to process account freeze after max retries. Error: %s", exceptionMessage),
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