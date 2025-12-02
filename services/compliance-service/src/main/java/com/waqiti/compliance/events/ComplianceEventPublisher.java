package com.waqiti.compliance.events;

import com.waqiti.compliance.events.model.ComplianceDomainEvent;
import com.waqiti.compliance.events.model.DeadLetterComplianceEvent;
import com.waqiti.compliance.events.store.ComplianceEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Enterprise-grade compliance event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ComplianceEventStore eventStore;
    
    // Topic constants
    private static final String AML_REPORT_EVENTS_TOPIC = "aml-report-events";
    private static final String OFAC_SANCTIONS_SCREENING_EVENTS_TOPIC = "ofac-sanctions-screening-events";
    private static final String KYC_VERIFICATION_EVENTS_TOPIC = "kyc-verification-events";
    private static final String PEP_SCREENING_EVENTS_TOPIC = "pep-screening-events";
    private static final String TRADE_BASED_MONEY_LAUNDERING_EVENTS_TOPIC = "trade-based-money-laundering-events";
    private static final String WHISTLEBLOWER_REPORT_EVENTS_TOPIC = "whistleblower-report-events";
    private static final String REGULATORY_EXAMINATION_EVENTS_TOPIC = "regulatory-examination-events";
    private static final String COMPLIANCE_AUDIT_EVENTS_TOPIC = "compliance-audit-events";
    private static final String REG_E_COMPLIANCE_EVENTS_TOPIC = "reg-e-compliance-events";
    private static final String UCC_FILING_EVENTS_TOPIC = "ucc-filing-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<ComplianceDomainEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter complianceEventsPublished;
    private Counter complianceEventsFailure;
    private Timer complianceEventPublishLatency;
    
    /**
     * Publishes AML report event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishAMLReport(
            String complianceId, String reportId, String customerId, String transactionId,
            BigDecimal amount, String currency, String riskLevel, String suspiciousActivity) {
        
        ComplianceDomainEvent event = ComplianceDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("AML_REPORT")
                .complianceId(complianceId)
                .reportId(reportId)
                .customerId(customerId)
                .transactionId(transactionId)
                .amount(amount)
                .currency(currency)
                .riskLevel(riskLevel)
                .suspiciousActivity(suspiciousActivity)
                .reportType("SAR")
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(AML_REPORT_EVENTS_TOPIC, event, complianceId);
    }
    
    /**
     * Publishes OFAC sanctions screening event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishOFACSanctionsScreening(
            String complianceId, String customerId, String entityId, String screeningType,
            String matchStatus, String sanctionsList, String riskScore, String userId) {
        
        ComplianceDomainEvent event = ComplianceDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("OFAC_SANCTIONS_SCREENING")
                .complianceId(complianceId)
                .customerId(customerId)
                .entityId(entityId)
                .screeningType(screeningType)
                .matchStatus(matchStatus)
                .sanctionsList(sanctionsList)
                .riskScore(riskScore)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(OFAC_SANCTIONS_SCREENING_EVENTS_TOPIC, event, complianceId);
    }
    
    /**
     * Publishes KYC verification event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishKYCVerification(
            String complianceId, String customerId, String kycTier, String kycDocumentType,
            String verificationStatus, String documentStatus, String riskLevel, String userId) {
        
        ComplianceDomainEvent event = ComplianceDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("KYC_VERIFICATION")
                .complianceId(complianceId)
                .customerId(customerId)
                .kycTier(kycTier)
                .kycDocumentType(kycDocumentType)
                .verificationStatus(verificationStatus)
                .documentStatus(documentStatus)
                .riskLevel(riskLevel)
                .userId(userId)
                .reviewDate(Instant.now())
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(KYC_VERIFICATION_EVENTS_TOPIC, event, complianceId);
    }
    
    /**
     * Publishes PEP screening event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishPEPScreening(
            String complianceId, String customerId, String pepStatus, String pepCategory,
            String pepRiskRating, String matchStatus, String screeningType, String userId) {
        
        ComplianceDomainEvent event = ComplianceDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PEP_SCREENING")
                .complianceId(complianceId)
                .customerId(customerId)
                .pepStatus(pepStatus)
                .pepCategory(pepCategory)
                .pepRiskRating(pepRiskRating)
                .matchStatus(matchStatus)
                .screeningType(screeningType)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(PEP_SCREENING_EVENTS_TOPIC, event, complianceId);
    }
    
    /**
     * Publishes trade-based money laundering event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishTradeBasedMoneyLaundering(
            String complianceId, String transactionId, String moneyLaunderingType, String tradeFinanceType,
            BigDecimal amount, String currency, String riskLevel, String investigationId) {
        
        ComplianceDomainEvent event = ComplianceDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("TRADE_BASED_MONEY_LAUNDERING")
                .complianceId(complianceId)
                .transactionId(transactionId)
                .moneyLaunderingType(moneyLaunderingType)
                .tradeFinanceType(tradeFinanceType)
                .amount(amount)
                .currency(currency)
                .riskLevel(riskLevel)
                .investigationId(investigationId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(TRADE_BASED_MONEY_LAUNDERING_EVENTS_TOPIC, event, complianceId);
    }
    
    /**
     * Publishes whistleblower report event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishWhistleblowerReport(
            String complianceId, String reportId, String whistleblowerCategory, String reportingChannel,
            String severity, String investigationId, String userId, String description) {
        
        ComplianceDomainEvent event = ComplianceDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("WHISTLEBLOWER_REPORT")
                .complianceId(complianceId)
                .reportId(reportId)
                .whistleblowerCategory(whistleblowerCategory)
                .reportingChannel(reportingChannel)
                .severity(severity)
                .investigationId(investigationId)
                .userId(userId)
                .description(description)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(WHISTLEBLOWER_REPORT_EVENTS_TOPIC, event, complianceId);
    }
    
    /**
     * Publishes regulatory examination event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishRegulatoryExamination(
            String complianceId, String examinationType, String regulatoryBody, String status,
            String complianceFramework, Instant reviewDate, String userId, String description) {
        
        ComplianceDomainEvent event = ComplianceDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("REGULATORY_EXAMINATION")
                .complianceId(complianceId)
                .examinationType(examinationType)
                .regulatoryBody(regulatoryBody)
                .status(status)
                .complianceFramework(complianceFramework)
                .reviewDate(reviewDate)
                .userId(userId)
                .description(description)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(REGULATORY_EXAMINATION_EVENTS_TOPIC, event, complianceId);
    }
    
    /**
     * Publishes compliance audit event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishComplianceAudit(
            String complianceId, String auditType, String auditScope, String auditResult,
            String complianceFramework, String status, String userId, Instant reviewDate) {
        
        ComplianceDomainEvent event = ComplianceDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("COMPLIANCE_AUDIT")
                .complianceId(complianceId)
                .auditType(auditType)
                .auditScope(auditScope)
                .auditResult(auditResult)
                .complianceFramework(complianceFramework)
                .status(status)
                .userId(userId)
                .reviewDate(reviewDate)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(COMPLIANCE_AUDIT_EVENTS_TOPIC, event, complianceId);
    }
    
    /**
     * Publishes Reg E compliance event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishRegECompliance(
            String complianceId, String customerId, String transactionId, String regulationName,
            String complianceStatus, BigDecimal amount, String currency, String userId) {
        
        ComplianceDomainEvent event = ComplianceDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("REG_E_COMPLIANCE")
                .complianceId(complianceId)
                .customerId(customerId)
                .transactionId(transactionId)
                .regulationName(regulationName)
                .complianceStatus(complianceStatus)
                .amount(amount)
                .currency(currency)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(REG_E_COMPLIANCE_EVENTS_TOPIC, event, complianceId);
    }
    
    /**
     * Publishes UCC filing event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishUCCFiling(
            String complianceId, String filingType, String filingStatus, String filingReference,
            Instant filingDate, Instant expiryDate, String entityId, String userId) {
        
        ComplianceDomainEvent event = ComplianceDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("UCC_FILING")
                .complianceId(complianceId)
                .filingType(filingType)
                .filingStatus(filingStatus)
                .filingReference(filingReference)
                .filingDate(filingDate)
                .expiryDate(expiryDate)
                .entityId(entityId)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(UCC_FILING_EVENTS_TOPIC, event, complianceId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<ComplianceDomainEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<ComplianceDomainEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<ComplianceDomainEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<ComplianceDomainEvent> topicEvents = entry.getValue();
            
            for (ComplianceDomainEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future = 
                    publishEvent(topic, event, event.getComplianceId());
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getComplianceEventPublishLatency());
                if (ex == null) {
                    log.info("Batch compliance events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getComplianceEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch compliance events", ex);
                    getComplianceEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, ComplianceDomainEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing compliance event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for compliance events")
            );
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Store event first for durability
            eventStore.storeEvent(event);
            
            // Create Kafka record with headers
            ProducerRecord<String, Object> record = createKafkaRecord(topic, key, event);
            
            // Publish with callback
            CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(record).toCompletableFuture();
            
            future.whenComplete((sendResult, ex) -> {
                sample.stop(getComplianceEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getComplianceEventPublishLatency());
            log.error("Failed to publish compliance event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, ComplianceDomainEvent event, String key) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Store event first
            eventStore.storeEvent(event);
            
            // Create record with high priority header
            ProducerRecord<String, Object> record = createKafkaRecord(topic, key, event);
            record.headers().add("priority", "HIGH".getBytes(StandardCharsets.UTF_8));
            
            // Synchronous send with timeout for high-priority events
            SendResult<String, Object> result = kafkaTemplate.send(record)
                .get(5, TimeUnit.SECONDS);
            
            sample.stop(getComplianceEventPublishLatency());
            onPublishSuccess(event, result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            sample.stop(getComplianceEventPublishLatency());
            log.error("Failed to publish high-priority compliance event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Event replay capability for recovering failed events
     */
    public CompletableFuture<Void> replayFailedEvents() {
        if (failedEvents.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        log.info("Replaying {} failed compliance events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        ComplianceDomainEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, event.getComplianceId());
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed compliance event replay"));
    }
    
    // Helper methods
    
    private void onPublishSuccess(ComplianceDomainEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getComplianceEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("Compliance event published successfully: type={}, complianceId={}, offset={}", 
            event.getEventType(), event.getComplianceId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(ComplianceDomainEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getComplianceEventsFailure().increment();
        
        log.error("Failed to publish compliance event: type={}, complianceId={}, topic={}", 
            event.getEventType(), event.getComplianceId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(ComplianceDomainEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed compliance event for retry: type={}, complianceId={}", 
            event.getEventType(), event.getComplianceId());
    }
    
    private boolean isCircuitBreakerOpen() {
        if (!circuitBreakerOpen) {
            return false;
        }
        
        if (System.currentTimeMillis() - circuitBreakerOpenTime > CIRCUIT_BREAKER_TIMEOUT) {
            closeCircuitBreaker();
            return false;
        }
        
        return true;
    }
    
    private void openCircuitBreaker() {
        circuitBreakerOpen = true;
        circuitBreakerOpenTime = System.currentTimeMillis();
        log.error("Compliance event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Compliance event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, ComplianceDomainEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterComplianceEvent dlqEvent = DeadLetterComplianceEvent.builder()
                .originalEvent(event)
                .originalTopic(originalTopic)
                .errorMessage(error.getMessage())
                .failureTimestamp(Instant.now())
                .retryCount(1)
                .build();
            
            ProducerRecord<String, Object> dlqRecord = createKafkaRecord(dlqTopic, key, dlqEvent);
            dlqRecord.headers().add("original-topic", originalTopic.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("error-message", error.getMessage().getBytes(StandardCharsets.UTF_8));
            
            kafkaTemplate.send(dlqRecord).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.warn("Compliance event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send compliance event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish compliance event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "compliance-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(ComplianceDomainEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getComplianceId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<ComplianceDomainEvent>> groupEventsByTopic(List<ComplianceDomainEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "AML_REPORT":
                return AML_REPORT_EVENTS_TOPIC;
            case "OFAC_SANCTIONS_SCREENING":
                return OFAC_SANCTIONS_SCREENING_EVENTS_TOPIC;
            case "KYC_VERIFICATION":
                return KYC_VERIFICATION_EVENTS_TOPIC;
            case "PEP_SCREENING":
                return PEP_SCREENING_EVENTS_TOPIC;
            case "TRADE_BASED_MONEY_LAUNDERING":
                return TRADE_BASED_MONEY_LAUNDERING_EVENTS_TOPIC;
            case "WHISTLEBLOWER_REPORT":
                return WHISTLEBLOWER_REPORT_EVENTS_TOPIC;
            case "REGULATORY_EXAMINATION":
                return REGULATORY_EXAMINATION_EVENTS_TOPIC;
            case "COMPLIANCE_AUDIT":
                return COMPLIANCE_AUDIT_EVENTS_TOPIC;
            case "REG_E_COMPLIANCE":
                return REG_E_COMPLIANCE_EVENTS_TOPIC;
            case "UCC_FILING":
                return UCC_FILING_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown compliance event type: " + eventType);
        }
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "compliance-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getComplianceEventsPublished() {
        if (complianceEventsPublished == null) {
            complianceEventsPublished = Counter.builder("compliance.events.published")
                .description("Number of compliance events published")
                .register(meterRegistry);
        }
        return complianceEventsPublished;
    }
    
    private Counter getComplianceEventsFailure() {
        if (complianceEventsFailure == null) {
            complianceEventsFailure = Counter.builder("compliance.events.failure")
                .description("Number of compliance events that failed to publish")
                .register(meterRegistry);
        }
        return complianceEventsFailure;
    }
    
    private Timer getComplianceEventPublishLatency() {
        if (complianceEventPublishLatency == null) {
            complianceEventPublishLatency = Timer.builder("compliance.events.publish.latency")
                .description("Latency of compliance event publishing")
                .register(meterRegistry);
        }
        return complianceEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String complianceId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String complianceId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.complianceId = complianceId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}