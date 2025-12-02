package com.waqiti.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.user.service.UserAccountService;
import com.waqiti.user.service.NotificationService;
import com.waqiti.user.service.AuditService;
import com.waqiti.user.service.IdempotencyService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Account Restrictions Applied Consumer
 * 
 * CRITICAL CONSUMER - Processes account restriction events from compliance systems
 * 
 * EVENT SOURCE:
 * - kyc-service KycDocumentExpiredConsumer: Line 284 publishes account restrictions
 * 
 * BUSINESS CRITICALITY:
 * - Enforces compliance-based account restrictions
 * - Synchronizes user account permissions across services
 * - Notifies users of account limitations
 * - Maintains audit trail for regulatory compliance
 * - Supports fraud prevention and AML enforcement
 * 
 * RESTRICTION TYPES:
 * - KYC_EXPIRED_CRITICAL: Complete account freeze
 * - KYC_EXPIRED_HIGH: Moderate transaction limits
 * - KYC_EXPIRED_MEDIUM: Light restrictions with verification required
 * - FRAUD_SUSPECTED: Fraud-based restrictions
 * - AML_ALERT: Anti-money laundering restrictions
 * - REGULATORY_HOLD: Regulatory compliance restrictions
 * 
 * PROCESSING ACTIONS:
 * - Update user account restriction status
 * - Synchronize permissions across microservices
 * - Send user notifications about restrictions
 * - Record restriction audit trail
 * - Update user session permissions in real-time
 * - Trigger compliance workflows if needed
 * 
 * BUSINESS VALUE:
 * - Compliance: Regulatory requirement enforcement
 * - Risk management: Real-time fraud prevention
 * - Customer protection: Automated account security
 * - Audit trail: Complete restriction history
 * 
 * FAILURE IMPACT:
 * - Compliance violations if restrictions not applied
 * - Security gaps allowing unauthorized transactions
 * - Regulatory penalties for non-enforcement
 * - User confusion without proper notifications
 * - Missing audit trail for compliance reviews
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Real-time metrics tracking
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccountRestrictionsAppliedConsumer {
    
    private final UserAccountService userAccountService;
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "account-restrictions-applied";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter criticalRestrictionsCounter;
    private Counter highRestrictionsCounter;
    private Timer processingTimer;
    
    public AccountRestrictionsAppliedConsumer(
            UserAccountService userAccountService,
            NotificationService notificationService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.userAccountService = userAccountService;
        this.notificationService = notificationService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("account_restrictions_applied_events_processed_total")
                .description("Total number of account restriction events processed")
                .tag("consumer", "account-restrictions-applied-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("account_restrictions_applied_events_failed_total")
                .description("Total number of account restriction events that failed processing")
                .tag("consumer", "account-restrictions-applied-consumer")
                .register(meterRegistry);
        
        this.criticalRestrictionsCounter = Counter.builder("critical_account_restrictions_total")
                .description("Total number of critical account restrictions applied")
                .register(meterRegistry);
        
        this.highRestrictionsCounter = Counter.builder("high_account_restrictions_total")
                .description("Total number of high severity account restrictions applied")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("account_restrictions_applied_event_processing_duration")
                .description("Time taken to process account restriction events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.account-restrictions-applied:account-restrictions-applied}",
        groupId = "${kafka.consumer.group-id:user-service-account-restrictions-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:3}"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        include = {Exception.class},
        dltTopicSuffix = "-dlt",
        autoCreateTopics = "true",
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED, timeout = 30)
    public void handleAccountRestrictionEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = null;
        String correlationId = null;
        
        try {
            log.info("Received account restriction event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "id");
            String userId = extractString(eventData, "userId");
            String restrictionType = extractString(eventData, "restrictionType");
            String reason = extractString(eventData, "reason");
            String severity = extractString(eventData, "severity");
            correlationId = extractString(eventData, "correlationId");
            Map<String, Object> restrictions = extractMap(eventData, "restrictions");
            
            if (eventId == null) {
                eventId = UUID.randomUUID().toString();
            }
            if (correlationId == null) {
                correlationId = UUID.randomUUID().toString();
            }
            
            if (userId == null || restrictionType == null) {
                log.error("Invalid account restriction event - missing required fields: userId={}, type={}",
                        userId, restrictionType);
                auditService.logEventProcessingFailure(
                    eventId,
                    TOPIC_NAME,
                    "VALIDATION_FAILED",
                    "Missing required fields",
                    correlationId,
                    Map.of("event", eventData)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate account restriction event detected - eventId: {}, userId: {}, correlationId: {}",
                        eventId, userId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processAccountRestriction(userId, restrictionType, reason, severity, restrictions, 
                    eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed account restriction event - eventId: {}, userId: {}, " +
                    "type: {}, severity: {}, correlationId: {}",
                    eventId, userId, restrictionType, severity, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process account restriction event - eventId: {}, correlationId: {}, error: {}",
                    eventId, correlationId, e.getMessage(), e);
            
            auditService.logEventProcessingFailure(
                eventId,
                TOPIC_NAME,
                "PROCESSING_FAILED",
                e.getMessage(),
                correlationId,
                Map.of(
                    "error", e.getClass().getName(),
                    "errorMessage", e.getMessage()
                )
            );
            
            throw new RuntimeException("Failed to process account restriction event", e);
        }
    }
    
    @CircuitBreaker(name = "userAccount", fallbackMethod = "processAccountRestrictionFallback")
    @Retry(name = "userAccount")
    private void processAccountRestriction(String userId, String restrictionType, String reason,
                                          String severity, Map<String, Object> restrictions,
                                          Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing account restriction - userId: {}, type: {}, severity: {}, reason: {}, correlationId: {}",
                userId, restrictionType, severity, reason, correlationId);
        
        userAccountService.applyAccountRestrictions(
            userId,
            restrictionType,
            severity,
            reason,
            restrictions,
            correlationId
        );
        
        if ("CRITICAL".equals(severity)) {
            criticalRestrictionsCounter.increment();
            handleCriticalRestriction(userId, restrictionType, reason, restrictions, correlationId);
        } else if ("HIGH".equals(severity)) {
            highRestrictionsCounter.increment();
            handleHighSeverityRestriction(userId, restrictionType, reason, restrictions, correlationId);
        }
        
        notificationService.notifyUserOfRestrictions(
            userId,
            restrictionType,
            severity,
            reason,
            restrictions,
            correlationId
        );
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("userId", userId);
        auditMetadata.put("restrictionType", restrictionType);
        auditMetadata.put("severity", severity);
        auditMetadata.put("reason", reason);
        auditMetadata.put("restrictions", restrictions);
        auditMetadata.put("appliedAt", eventData.get("appliedAt"));
        
        auditService.logAccountRestrictionApplied(
            eventData.get("id") != null ? eventData.get("id").toString() : userId,
            userId,
            restrictionType,
            severity,
            reason,
            correlationId,
            auditMetadata
        );
        
        log.info("Account restriction processed successfully - userId: {}, type: {}, correlationId: {}",
                userId, restrictionType, correlationId);
    }
    
    private void handleCriticalRestriction(String userId, String restrictionType, String reason,
                                          Map<String, Object> restrictions, String correlationId) {
        log.warn("CRITICAL RESTRICTION APPLIED - userId: {}, type: {}, reason: {}",
                userId, restrictionType, reason);
        
        userAccountService.freezeUserSessions(userId, correlationId);
        
        notificationService.sendCriticalRestrictionAlert(
            userId,
            restrictionType,
            reason,
            restrictions,
            correlationId
        );
        
        auditService.logCriticalRestrictionAlert(
            userId,
            restrictionType,
            reason,
            correlationId,
            restrictions
        );
    }
    
    private void handleHighSeverityRestriction(String userId, String restrictionType, String reason,
                                              Map<String, Object> restrictions, String correlationId) {
        log.info("HIGH SEVERITY RESTRICTION APPLIED - userId: {}, type: {}, reason: {}",
                userId, restrictionType, reason);
        
        userAccountService.updateUserPermissions(userId, restrictions, correlationId);
        
        notificationService.sendHighSeverityRestrictionAlert(
            userId,
            restrictionType,
            reason,
            restrictions,
            correlationId
        );
    }
    
    private void processAccountRestrictionFallback(String userId, String restrictionType, String reason,
                                                   String severity, Map<String, Object> restrictions,
                                                   Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process account restriction - userId: {}, " +
                "type: {}, severity: {}, correlationId: {}, error: {}",
                userId, restrictionType, severity, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "ACCOUNT_RESTRICTION_PROCESSING",
            userId,
            correlationId,
            Map.of(
                "restrictionType", restrictionType,
                "severity", severity,
                "reason", reason,
                "error", e.getMessage()
            )
        );
    }
    
    @DltHandler
    public void handleDeadLetterTopic(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        try {
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            String eventId = extractString(eventData, "id");
            String userId = extractString(eventData, "userId");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("Account restriction event moved to DLT - eventId: {}, userId: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, userId, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("userId", userId);
            dltMetadata.put("restrictionType", extractString(eventData, "restrictionType"));
            dltMetadata.put("severity", extractString(eventData, "severity"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("appliedAt"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "ACCOUNT_RESTRICTION_DLT",
                exceptionMessage,
                correlationId,
                dltMetadata
            );
        } catch (Exception e) {
            log.error("Failed to process DLT event: {}", e.getMessage(), e);
        }
    }
    
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }
}