package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.domain.Account;
import com.waqiti.account.domain.FreezeRequest;
import com.waqiti.account.domain.FreezeRequestStatus;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.repository.FreezeRequestRepository;
import com.waqiti.account.service.AccountFreezeService;
import com.waqiti.account.service.FreezeRequestValidationService;
import com.waqiti.account.service.AccountNotificationService;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AccountFreezeRequestsConsumer {

    private final AccountRepository accountRepository;
    private final FreezeRequestRepository freezeRequestRepository;
    private final AccountFreezeService freezeService;
    private final FreezeRequestValidationService validationService;
    private final AccountNotificationService notificationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter freezeRequestCounter;
    private final Counter freezeApprovedCounter;
    private final Counter freezeRejectedCounter;
    private final Timer requestProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AccountFreezeRequestsConsumer(
            AccountRepository accountRepository,
            FreezeRequestRepository freezeRequestRepository,
            AccountFreezeService freezeService,
            FreezeRequestValidationService validationService,
            AccountNotificationService notificationService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.accountRepository = accountRepository;
        this.freezeRequestRepository = freezeRequestRepository;
        this.freezeService = freezeService;
        this.validationService = validationService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.freezeRequestCounter = Counter.builder("account.freeze.request.events")
            .description("Count of account freeze request events")
            .register(meterRegistry);
        
        this.freezeApprovedCounter = Counter.builder("account.freeze.approved.events")
            .description("Count of approved freeze requests")
            .register(meterRegistry);
        
        this.freezeRejectedCounter = Counter.builder("account.freeze.rejected.events")
            .description("Count of rejected freeze requests")
            .register(meterRegistry);
        
        this.requestProcessingTimer = Timer.builder("account.freeze.request.processing.duration")
            .description("Time taken to process freeze request events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "account-freeze-requests",
        groupId = "account-freeze-requests-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "account-freeze-requests-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleAccountFreezeRequest(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received account freeze request - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String customerId = (String) eventData.get("customerId");
            String reason = (String) eventData.get("reason");
            String disputeId = (String) eventData.get("disputeId");
            String requestedBy = (String) eventData.getOrDefault("requestedBy", "SYSTEM");
            
            String correlationId = String.format("freeze-request-%s-%s-%d", 
                customerId, UUID.randomUUID().toString().substring(0, 8), System.currentTimeMillis());
            
            log.warn("Processing account freeze request - customerId: {}, reason: {}, correlationId: {}", 
                customerId, reason, correlationId);
            
            freezeRequestCounter.increment();
            
            processFreezeRequest(customerId, reason, disputeId, requestedBy, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(requestProcessingTimer);
            
            log.info("Successfully processed account freeze request: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process account freeze request {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Account freeze request processing failed", e);
        }
    }

    @CircuitBreaker(name = "account", fallbackMethod = "processFreezeRequestFallback")
    @Retry(name = "account")
    private void processFreezeRequest(
            String customerId,
            String reason,
            String disputeId,
            String requestedBy,
            Map<String, Object> eventData,
            String correlationId) {
        
        Account account = accountRepository.findByUserId(customerId)
            .orElseThrow(() -> new RuntimeException("Account not found for customer: " + customerId));
        
        String requestId = UUID.randomUUID().toString();
        
        FreezeRequest freezeRequest = FreezeRequest.builder()
            .id(requestId)
            .accountId(account.getId())
            .customerId(customerId)
            .reason(reason)
            .disputeId(disputeId)
            .requestedBy(requestedBy)
            .status(FreezeRequestStatus.PENDING)
            .requestedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        freezeRequestRepository.save(freezeRequest);
        
        boolean isAutoApproved = validationService.shouldAutoApproveFreezeRequest(
            account,
            reason,
            disputeId,
            correlationId
        );
        
        if (isAutoApproved) {
            approveFreezeRequest(account, freezeRequest, correlationId);
        } else {
            requireManualReview(account, freezeRequest, correlationId);
        }
        
        auditService.logAccountEvent(
            "FREEZE_REQUEST_RECEIVED",
            account.getId(),
            Map.of(
                "requestId", requestId,
                "customerId", customerId,
                "reason", reason,
                "disputeId", disputeId != null ? disputeId : "N/A",
                "requestedBy", requestedBy,
                "autoApproved", isAutoApproved,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.info("Freeze request processed - requestId: {}, autoApproved: {}, correlationId: {}", 
            requestId, isAutoApproved, correlationId);
    }

    private void approveFreezeRequest(Account account, FreezeRequest freezeRequest, String correlationId) {
        freezeRequest.setStatus(FreezeRequestStatus.APPROVED);
        freezeRequest.setApprovedAt(LocalDateTime.now());
        freezeRequest.setApprovedBy("AUTO_APPROVAL_SYSTEM");
        freezeRequestRepository.save(freezeRequest);
        
        freezeApprovedCounter.increment();
        
        String freezeId = freezeService.freezeAccount(
            account,
            freezeRequest.getReason(),
            "FULL_ACCOUNT",
            correlationId
        );
        
        kafkaTemplate.send("account-freezes", Map.of(
            "userId", account.getUserId(),
            "freezeId", freezeId,
            "freezeReason", freezeRequest.getReason(),
            "freezeScope", "FULL_ACCOUNT",
            "severity", "HIGH",
            "description", String.format("Auto-approved freeze request: %s", freezeRequest.getReason()),
            "requiresManualReview", false,
            "notifyRegulators", false,
            "freezingSystem", "FREEZE_REQUEST_AUTO_APPROVAL",
            "requestId", freezeRequest.getId(),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        notificationService.sendFreezeRequestApprovedNotification(
            account.getUserId(),
            freezeRequest.getId(),
            freezeId,
            correlationId
        );
        
        log.info("Freeze request auto-approved and executed - requestId: {}, freezeId: {}, correlationId: {}", 
            freezeRequest.getId(), freezeId, correlationId);
    }

    private void requireManualReview(Account account, FreezeRequest freezeRequest, String correlationId) {
        freezeRequest.setStatus(FreezeRequestStatus.PENDING_REVIEW);
        freezeRequest.setReviewRequiredAt(LocalDateTime.now());
        freezeRequestRepository.save(freezeRequest);
        
        kafkaTemplate.send("compliance-review-required", Map.of(
            "reviewType", "FREEZE_REQUEST_REVIEW",
            "requestId", freezeRequest.getId(),
            "accountId", account.getId(),
            "customerId", account.getUserId(),
            "reason", freezeRequest.getReason(),
            "disputeId", freezeRequest.getDisputeId() != null ? freezeRequest.getDisputeId() : "N/A",
            "requestedBy", freezeRequest.getRequestedBy(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        notificationService.sendFreezeRequestReviewNotification(
            freezeRequest.getId(),
            account.getUserId(),
            freezeRequest.getReason(),
            correlationId
        );
        
        log.info("Freeze request requires manual review - requestId: {}, correlationId: {}", 
            freezeRequest.getId(), correlationId);
    }

    private void processFreezeRequestFallback(
            String customerId,
            String reason,
            String disputeId,
            String requestedBy,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for freeze request - customerId: {}, correlationId: {}, error: {}", 
            customerId, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("customerId", customerId);
        fallbackEvent.put("reason", reason);
        fallbackEvent.put("disputeId", disputeId);
        fallbackEvent.put("requestedBy", requestedBy);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("freeze-request-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Freeze request sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("freeze-request-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Freeze Request Processing Failed",
                String.format("CRITICAL: Failed to process freeze request after max retries. Error: %s", exceptionMessage),
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
