package com.waqiti.analytics.events.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.service.FamilyAnalyticsService;
import com.waqiti.analytics.service.AuditService;
import com.waqiti.analytics.service.IdempotencyService;
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
 * Family Account Events Consumer
 * 
 * CRITICAL CONSUMER - Processes family account lifecycle events
 * 
 * EVENT SOURCE:
 * - family-account-service FamilyAccountService: Line 612 publishes family account events
 * 
 * BUSINESS CRITICALITY:
 * - Tracks family account creation and member management
 * - Enables family financial wellness analytics
 * - Supports parental control monitoring
 * - Tracks allowance and spending patterns
 * - Provides insights for family banking products
 * 
 * EVENT TYPES:
 * - FAMILY_ACCOUNT_CREATED: New family account created
 * - FAMILY_MEMBER_ADDED: Member added to family account  
 * - FAMILY_MEMBER_REMOVED: Member removed from family account
 * - ALLOWANCE_SET: Allowance configured for family member
 * - SPENDING_RULE_APPLIED: Spending restrictions applied
 * - FAMILY_GOAL_CREATED: Family savings goal created
 * 
 * PROCESSING ACTIONS:
 * - Record family account metrics and analytics
 * - Track family member growth and engagement
 * - Analyze spending patterns across family members
 * - Generate insights for product recommendations
 * - Track allowance payment patterns
 * - Monitor parental control effectiveness
 * 
 * BUSINESS VALUE:
 * - Product intelligence: Family banking feature adoption
 * - Customer retention: Family account engagement tracking
 * - Marketing: Targeted family product campaigns
 * - Analytics: Family financial wellness insights
 * 
 * FAILURE IMPACT:
 * - Loss of family account analytics
 * - Missing product adoption metrics
 * - Inability to measure family feature effectiveness
 * - Reduced insights for product development
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FamilyAccountEventsConsumer {
    
    private final FamilyAnalyticsService familyAnalyticsService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "family-account-events";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter familyAccountsCreatedCounter;
    private Counter familyMembersAddedCounter;
    private Timer processingTimer;
    
    public FamilyAccountEventsConsumer(
            FamilyAnalyticsService familyAnalyticsService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.familyAnalyticsService = familyAnalyticsService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("family_account_events_processed_total")
                .description("Total number of family account events processed")
                .tag("consumer", "family-account-events-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("family_account_events_failed_total")
                .description("Total number of family account events that failed processing")
                .tag("consumer", "family-account-events-consumer")
                .register(meterRegistry);
        
        this.familyAccountsCreatedCounter = Counter.builder("family_accounts_created_total")
                .description("Total number of family accounts created")
                .register(meterRegistry);
        
        this.familyMembersAddedCounter = Counter.builder("family_members_added_total")
                .description("Total number of family members added")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("family_account_event_processing_duration")
                .description("Time taken to process family account events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.family-account-events:family-account-events}",
        groupId = "${kafka.consumer.group-id:analytics-service-family-account-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:2}"
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
    public void handleFamilyAccountEvent(
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
            log.info("Received family account event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String familyId = extractString(eventData, "familyId");
            String eventType = extractString(eventData, "eventType");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = UUID.randomUUID().toString();
            }
            if (correlationId == null) {
                correlationId = UUID.randomUUID().toString();
            }
            
            if (familyId == null) {
                log.error("Invalid family account event - missing familyId");
                auditService.logEventProcessingFailure(
                    eventId,
                    TOPIC_NAME,
                    "VALIDATION_FAILED",
                    "Missing familyId",
                    correlationId,
                    Map.of("event", eventData)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate family account event detected - eventId: {}, familyId: {}, correlationId: {}",
                        eventId, familyId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processFamilyAccountEvent(eventType, familyId, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed family account event - eventId: {}, familyId: {}, " +
                    "eventType: {}, correlationId: {}",
                    eventId, familyId, eventType, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process family account event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process family account event", e);
        }
    }
    
    @CircuitBreaker(name = "analytics", fallbackMethod = "processFamilyAccountEventFallback")
    @Retry(name = "analytics")
    private void processFamilyAccountEvent(String eventType, String familyId,
                                          Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing family account event - familyId: {}, eventType: {}, correlationId: {}",
                familyId, eventType, correlationId);
        
        if ("FAMILY_ACCOUNT_CREATED".equals(eventType) || eventData.containsKey("familyName")) {
            handleFamilyAccountCreated(familyId, eventData, correlationId);
            familyAccountsCreatedCounter.increment();
        } else if ("FAMILY_MEMBER_ADDED".equals(eventType) || eventData.containsKey("memberId")) {
            handleFamilyMemberAdded(familyId, eventData, correlationId);
            familyMembersAddedCounter.increment();
        } else if ("ALLOWANCE_SET".equals(eventType)) {
            handleAllowanceSet(familyId, eventData, correlationId);
        } else if ("SPENDING_RULE_APPLIED".equals(eventType)) {
            handleSpendingRuleApplied(familyId, eventData, correlationId);
        } else if ("FAMILY_GOAL_CREATED".equals(eventType)) {
            handleFamilyGoalCreated(familyId, eventData, correlationId);
        } else {
            log.warn("Unknown family account event type: {}", eventType);
        }
        
        familyAnalyticsService.recordFamilyAccountEvent(familyId, eventType, eventData, correlationId);
        
        Map<String, Object> auditMetadata = new HashMap<>(eventData);
        auditMetadata.put("eventType", eventType);
        
        auditService.logFamilyAccountEventProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : familyId,
            familyId,
            eventType,
            correlationId,
            auditMetadata
        );
        
        log.info("Family account event processed successfully - familyId: {}, eventType: {}, correlationId: {}",
                familyId, eventType, correlationId);
    }
    
    private void handleFamilyAccountCreated(String familyId, Map<String, Object> eventData, String correlationId) {
        log.info("Processing family account created - familyId: {}, correlationId: {}", familyId, correlationId);
        
        String familyName = extractString(eventData, "familyName");
        String primaryParentUserId = extractString(eventData, "primaryParentUserId");
        String familyWalletId = extractString(eventData, "familyWalletId");
        
        familyAnalyticsService.trackFamilyAccountCreation(
            familyId,
            familyName,
            primaryParentUserId,
            familyWalletId,
            eventData,
            correlationId
        );
    }
    
    private void handleFamilyMemberAdded(String familyId, Map<String, Object> eventData, String correlationId) {
        log.info("Processing family member added - familyId: {}, correlationId: {}", familyId, correlationId);
        
        String memberId = extractString(eventData, "memberId");
        String memberRole = extractString(eventData, "memberRole");
        String addedBy = extractString(eventData, "addedBy");
        
        familyAnalyticsService.trackFamilyMemberAdded(
            familyId,
            memberId,
            memberRole,
            addedBy,
            eventData,
            correlationId
        );
    }
    
    private void handleAllowanceSet(String familyId, Map<String, Object> eventData, String correlationId) {
        log.info("Processing allowance set - familyId: {}, correlationId: {}", familyId, correlationId);
        familyAnalyticsService.trackAllowanceSet(familyId, eventData, correlationId);
    }
    
    private void handleSpendingRuleApplied(String familyId, Map<String, Object> eventData, String correlationId) {
        log.info("Processing spending rule applied - familyId: {}, correlationId: {}", familyId, correlationId);
        familyAnalyticsService.trackSpendingRuleApplied(familyId, eventData, correlationId);
    }
    
    private void handleFamilyGoalCreated(String familyId, Map<String, Object> eventData, String correlationId) {
        log.info("Processing family goal created - familyId: {}, correlationId: {}", familyId, correlationId);
        familyAnalyticsService.trackFamilyGoalCreated(familyId, eventData, correlationId);
    }
    
    private void processFamilyAccountEventFallback(String eventType, String familyId,
                                                   Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process family account event - familyId: {}, " +
                "eventType: {}, correlationId: {}, error: {}",
                familyId, eventType, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "FAMILY_ACCOUNT_EVENT_PROCESSING",
            familyId,
            correlationId,
            Map.of(
                "eventType", eventType,
                "familyId", familyId,
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
            String eventId = extractString(eventData, "eventId");
            String familyId = extractString(eventData, "familyId");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("Family account event moved to DLT - eventId: {}, familyId: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, familyId, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("familyId", familyId);
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "FAMILY_ACCOUNT_EVENT_DLT",
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
}