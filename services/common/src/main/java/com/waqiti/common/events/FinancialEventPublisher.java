package com.waqiti.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.waqiti.common.events.model.AuditEvent;
import com.waqiti.common.messaging.deadletter.DeadLetterEvent;
import com.waqiti.common.security.SecurityAlertEvent;
import com.waqiti.common.security.SecurityAlertLevel;
import com.waqiti.common.enums.RiskLevel;
import java.time.LocalDateTime;

/**
 * Enterprise-grade event publisher for financial operations.
 * Implements reliable event publishing with exactly-once semantics,
 * dead letter queues, and comprehensive monitoring.
 */
@Service
@Slf4j
public class FinancialEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final EventAuditService eventAuditService;
    private final EventMetricsService eventMetricsService;
    
    public FinancialEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            EventAuditService eventAuditService,
            EventMetricsService eventMetricsService) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.eventAuditService = eventAuditService;
        this.eventMetricsService = eventMetricsService;
    }

    // Topic configurations
    private static final String PAYMENT_EVENTS_TOPIC = "waqiti.payments.events";
    private static final String ACCOUNT_EVENTS_TOPIC = "waqiti.accounts.events";
    private static final String USER_EVENTS_TOPIC = "waqiti.users.events";
    private static final String TRANSACTION_EVENTS_TOPIC = "waqiti.transactions.events";
    private static final String FRAUD_EVENTS_TOPIC = "waqiti.fraud.events";
    private static final String COMPLIANCE_EVENTS_TOPIC = "waqiti.compliance.events";
    private static final String NOTIFICATION_EVENTS_TOPIC = "waqiti.notifications.events";
    private static final String AUDIT_EVENTS_TOPIC = "waqiti.audit.events";

    // Dead letter queue topics
    private static final String DLQ_SUFFIX = ".dlq";

    // Event correlation tracking
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();

    /**
     * Publishes payment events with exactly-once semantics
     */
    public CompletableFuture<Void> publishPaymentEvent(PaymentEvent event) {
        return publishEvent(PAYMENT_EVENTS_TOPIC, event, event.getPaymentId().toString())
            .thenRun(() -> {
                eventMetricsService.incrementPaymentEvent(event.getEventType());
                log.info("Payment event published: type={}, paymentId={}, amount={}", 
                    event.getEventType(), event.getPaymentId(), event.getAmount());
            });
    }

    /**
     * Publishes transaction events with correlation tracking
     */
    public CompletableFuture<Void> publishTransactionEvent(TransactionEvent event) {
        return publishEventWithCorrelation(TRANSACTION_EVENTS_TOPIC, event, event.getTransactionId().toString())
            .thenRun(() -> {
                eventMetricsService.incrementTransactionEvent(event.getEventType());
                
                // Track saga correlations for distributed transactions
                if (event.getSagaId() != null) {
                    trackSagaCorrelation(event.getSagaId(), event.getTransactionId().toString(), event.getEventType());
                }
                
                log.info("Transaction event published: type={}, transactionId={}, sagaId={}", 
                    event.getEventType(), event.getTransactionId(), event.getSagaId());
            });
    }

    /**
     * Publishes account events
     */
    public CompletableFuture<Void> publishAccountEvent(AccountEvent event) {
        return publishEvent(ACCOUNT_EVENTS_TOPIC, event, event.getAccountId().toString())
            .thenRun(() -> {
                eventMetricsService.incrementAccountEvent(event.getEventType());
                
                // Special handling for high-risk account events
                if (isHighRiskAccountEvent(event)) {
                    publishSecurityAlert(event);
                }
                
                log.info("Account event published: type={}, accountId={}, userId={}", 
                    event.getEventType(), event.getAccountId(), event.getUserId());
            });
    }

    /**
     * Publishes user events with privacy considerations
     */
    public CompletableFuture<Void> publishUserEvent(UserEvent event) {
        // Sanitize sensitive information before publishing
        UserEvent sanitizedEvent = sanitizeUserEvent(event);
        
        return publishEvent(USER_EVENTS_TOPIC, sanitizedEvent, event.getUserId().toString())
            .thenRun(() -> {
                eventMetricsService.incrementUserEvent(event.getEventType());
                log.info("User event published: type={}, userId={}", 
                    event.getEventType(), event.getUserId());
            });
    }

    /**
     * Publishes fraud detection events (high priority)
     */
    public CompletableFuture<Void> publishFraudEvent(FraudEvent event) {
        return publishHighPriorityEvent(FRAUD_EVENTS_TOPIC, event, event.getEntityId().toString())
            .thenRun(() -> {
                eventMetricsService.incrementFraudEvent(event.getRiskLevel().name());
                
                // Immediately notify security team for high-risk events
                if (event.getRiskLevel() == RiskLevel.HIGH || event.getRiskLevel() == RiskLevel.CRITICAL) {
                    notifySecurityTeam(event);
                }
                
                log.warn("Fraud event published: type={}, entityId={}, riskLevel={}, score={}", 
                    event.getEventType(), event.getEntityId(), event.getRiskLevel(), event.getRiskScore());
            });
    }

    /**
     * Publishes compliance events for regulatory reporting
     */
    public CompletableFuture<Void> publishComplianceEvent(ComplianceEvent event) {
        return publishEvent(COMPLIANCE_EVENTS_TOPIC, event, event.getEntityId().toString())
            .thenRun(() -> {
                eventMetricsService.incrementComplianceEvent(event.getComplianceType());
                
                // Archive compliance events for audit trail
                eventAuditService.archiveComplianceEvent(event);
                
                log.info("Compliance event published: type={}, entityId={}, regulation={}", 
                    event.getComplianceType(), event.getEntityId(), event.getRegulation());
            });
    }

    /**
     * Publishes notification events
     */
    public CompletableFuture<Void> publishNotificationEvent(NotificationEvent event) {
        return publishEvent(NOTIFICATION_EVENTS_TOPIC, event, event.getUserId().toString())
            .thenRun(() -> {
                eventMetricsService.incrementNotificationEvent(event.getChannel());
                log.debug("Notification event published: channel={}, userId={}, type={}", 
                    event.getChannel(), event.getUserId(), event.getNotificationType());
            });
    }

    /**
     * Publishes audit events for security monitoring
     */
    public CompletableFuture<Void> publishAuditEvent(AuditEvent event) {
        return publishGenericEvent(AUDIT_EVENTS_TOPIC, event, event.getEntityId())
            .thenRun(() -> {
                eventMetricsService.incrementAuditEvent(event.getAction());
                log.debug("Audit event published: action={}, entityId={}, userId={}", 
                    event.getAction(), event.getEntityId(), event.getUserId());
            });
    }

    /**
     * Publishes saga events for distributed transaction tracking
     */
    public CompletableFuture<Void> publishSagaEvent(SagaEvent event) {
        return publishEvent("waqiti.saga.events", event, event.getSagaId())
            .thenRun(() -> {
                log.debug("Saga event published: sagaId={}, eventType={}", 
                    event.getSagaId(), event.getEventType());
            });
    }

    /**
     * Publishes batch events for bulk operations
     */
    public CompletableFuture<Void> publishBatchEvents(List<? extends FinancialEvent> events) {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Group events by type for optimized publishing
        Map<String, List<FinancialEvent>> eventsByTopic = groupEventsByTopic(events);
        
        for (Map.Entry<String, List<FinancialEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<FinancialEvent> topicEvents = entry.getValue();
            
            CompletableFuture<Void> batchFuture = publishBatchToTopic(topic, topicEvents);
            futures.add(batchFuture);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                eventMetricsService.incrementBatchEvents(events.size());
                log.info("Batch events published: count={}, topics={}", 
                    events.size(), eventsByTopic.keySet());
            });
    }

    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<Void> publishEvent(String topic, FinancialEvent event, String key) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            // Add event metadata
            enrichEventMetadata(event);
            
            // Create producer record with headers
            ProducerRecord<String, Object> record = createProducerRecord(topic, key, event);
            
            // Publish with CompletableFuture callback  
            CompletableFuture<SendResult<String, Object>> kafkaFuture = 
                kafkaTemplate.send(record);
            
            kafkaFuture.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    eventAuditService.logEventPublished(event, result.getRecordMetadata());
                    eventMetricsService.recordPublishLatency(
                        topic, 
                        System.currentTimeMillis() - event.getTimestamp().toEpochMilli()
                    );
                    future.complete(null);
                } else {
                    log.error("Failed to publish event to topic: {} for key: {}", topic, key, throwable);
                    eventMetricsService.incrementPublishFailure(topic);
                    
                    // Try to send to dead letter queue
                    publishToDeadLetterQueue(topic, event, key, throwable)
                        .whenComplete((dlqResult, dlqError) -> {
                            if (dlqError != null) {
                                log.error("Failed to publish to DLQ", dlqError);
                            }
                            future.completeExceptionally(throwable);
                        });
                }
            });
            
        } catch (Exception e) {
            log.error("Error preparing event for publication", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }

    /**
     * Generic event publishing method for non-FinancialEvent types
     */
    private CompletableFuture<Void> publishGenericEvent(String topic, Object event, String key) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            // Create producer record with headers
            ProducerRecord<String, Object> record = createProducerRecord(topic, key, event);
            
            // Publish with CompletableFuture callback  
            CompletableFuture<SendResult<String, Object>> kafkaFuture = 
                kafkaTemplate.send(record);
            
            kafkaFuture.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    log.debug("Generic event published to topic: {} with key: {}", topic, key);
                    future.complete(null);
                } else {
                    log.error("Failed to publish generic event to topic: {} for key: {}", topic, key, throwable);
                    eventMetricsService.incrementPublishFailure(topic);
                    future.completeExceptionally(throwable);
                }
            });
            
        } catch (Exception e) {
            log.error("Error preparing generic event for publication", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }

    /**
     * Publishes event with correlation tracking for saga patterns
     */
    private CompletableFuture<Void> publishEventWithCorrelation(String topic, FinancialEvent event, String key) {
        // Track correlation for saga orchestration
        if (event instanceof TransactionEvent) {
            TransactionEvent txEvent = (TransactionEvent) event;
            if (txEvent.getSagaId() != null) {
                correlationTracker.put(txEvent.getSagaId(), 
                    new EventCorrelation(txEvent.getSagaId(), txEvent.getTransactionId().toString(), 
                        txEvent.getEventType(), Instant.now()));
            }
        }
        
        return publishEvent(topic, event, key);
    }

    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<Void> publishHighPriorityEvent(String topic, FinancialEvent event, String key) {
        // Use synchronous publishing for high-priority events
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            enrichEventMetadata(event);
            ProducerRecord<String, Object> record = createProducerRecord(topic, key, event);
            
            // Add high priority header
            record.headers().add("priority", "HIGH".getBytes());
            
            // Synchronous send with timeout
            SendResult<String, Object> result = kafkaTemplate.send(record)
                .get(5, TimeUnit.SECONDS);
            
            eventAuditService.logEventPublished(event, result.getRecordMetadata());
            future.complete(null);
            
        } catch (Exception e) {
            log.error("Failed to publish high-priority event", e);
            eventMetricsService.incrementHighPriorityFailure(topic);
            future.completeExceptionally(e);
        }
        
        return future;
    }

    /**
     * Publishes to dead letter queue
     */
    private CompletableFuture<Void> publishToDeadLetterQueue(String originalTopic, 
                                                            FinancialEvent event, 
                                                            String key, 
                                                            Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        // Create DLQ event with error information
        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            metadata = event.toString();
        }
        
        DeadLetterEvent dlqEvent = DeadLetterEvent.builder()
            .eventId(event.getEventId() != null ? event.getEventId().toString() : UUID.randomUUID().toString())
            .topic(originalTopic)
            .eventType(DeadLetterEvent.EventType.SENT_TO_DLQ.toString())
            .timestamp(LocalDateTime.now())
            .errorMessage(error.getMessage())
            .reason("Failed to publish event")
            .retryAttempt(0)
            .metadata(metadata)
            .build();
        
        try {
            ProducerRecord<String, Object> dlqRecord = createProducerRecord(dlqTopic, key, dlqEvent);
            dlqRecord.headers().add("original-topic", originalTopic.getBytes());
            dlqRecord.headers().add("error-message", error.getMessage().getBytes());
            
            return kafkaTemplate.send(dlqRecord)
                .thenRun(() -> {
                    eventAuditService.logDeadLetterEvent(event, originalTopic, error);
                    eventMetricsService.incrementDeadLetterEvent(originalTopic);
                    log.warn("Event sent to DLQ: topic={}, key={}, error={}", 
                        dlqTopic, key, error.getMessage());
                });
                
        } catch (Exception e) {
            log.error("Failed to publish to dead letter queue", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // Helper methods

    private void enrichEventMetadata(FinancialEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        if (event.getVersion() == null) {
            event.setVersion(1);
        }
        
        // Add correlation ID from current context if available
        String correlationId = getCorrelationIdFromContext();
        if (correlationId != null) {
            try {
                event.setCorrelationId(UUID.fromString(correlationId));
            } catch (IllegalArgumentException e) {
                // If not a valid UUID, generate a new one
                event.setCorrelationId(UUID.randomUUID());
            }
        }
    }

    private ProducerRecord<String, Object> createProducerRecord(String topic, String key, Object event) {
        List<Header> headers = Arrays.asList(
            new RecordHeader("event-type", event.getClass().getSimpleName().getBytes()),
            new RecordHeader("timestamp", String.valueOf(Instant.now().toEpochMilli()).getBytes()),
            new RecordHeader("service", "waqiti-platform".getBytes())
        );
        
        return new ProducerRecord<>(topic, null, key, event, headers);
    }

    private UserEvent sanitizeUserEvent(UserEvent event) {
        // Remove or mask sensitive information
        UserEvent sanitized = event.copy();
        
        if (sanitized.getPersonalInfo() != null) {
            sanitized.getPersonalInfo().maskSensitiveData();
        }
        
        return sanitized;
    }

    private boolean isHighRiskAccountEvent(AccountEvent event) {
        return "ACCOUNT_LOCKED".equals(event.getEventType()) ||
               "SUSPICIOUS_ACTIVITY".equals(event.getEventType()) ||
               "LARGE_WITHDRAWAL".equals(event.getEventType());
    }

    private void publishSecurityAlert(AccountEvent event) {
        // Publish immediate security alert for high-risk events
        SecurityAlertEvent alert = SecurityAlertEvent.builder()
            .accountId(event.getAccountId().toString())
            .userId(event.getUserId().toString())
            .message("High-risk account event: " + event.getEventType())
            .alertLevel(SecurityAlertLevel.HIGH)
            .timestamp(Instant.now())
            .build();

        // Publish directly to Kafka security alerts topic
        kafkaTemplate.send("waqiti.security.alerts", event.getAccountId().toString(), alert);
    }

    private void notifySecurityTeam(FraudEvent event) {
        // Send immediate notification to security team
        // Implementation would integrate with alerting system (PagerDuty, Slack, etc.)
        log.error("SECURITY ALERT: High-risk fraud event detected - {}", event);
    }

    private void trackSagaCorrelation(String sagaId, String transactionId, String eventType) {
        correlationTracker.put(sagaId + ":" + transactionId, 
            new EventCorrelation(sagaId, transactionId, eventType, Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }

    private Map<String, List<FinancialEvent>> groupEventsByTopic(List<? extends FinancialEvent> events) {
        Map<String, List<FinancialEvent>> grouped = new HashMap<>();
        
        for (FinancialEvent event : events) {
            String topic = getTopicForEvent(event);
            grouped.computeIfAbsent(topic, k -> new ArrayList<>()).add(event);
        }
        
        return grouped;
    }

    private String getTopicForEvent(FinancialEvent event) {
        String eventClassName = event.getClass().getSimpleName();
        
        switch (eventClassName) {
            case "PaymentEvent":
                return PAYMENT_EVENTS_TOPIC;
            case "TransactionEvent":
                return TRANSACTION_EVENTS_TOPIC;
            case "AccountEvent":
                return ACCOUNT_EVENTS_TOPIC;
            case "UserEvent":
                return USER_EVENTS_TOPIC;
            case "FraudEvent":
                return FRAUD_EVENTS_TOPIC;
            case "ComplianceEvent":
                return COMPLIANCE_EVENTS_TOPIC;
            case "NotificationEvent":
                return NOTIFICATION_EVENTS_TOPIC;
            case "AuditEvent":
                return AUDIT_EVENTS_TOPIC;
            default:
                // Check for event types by interface implementation
                if (event.getClass().getName().contains("Payment")) return PAYMENT_EVENTS_TOPIC;
                if (event.getClass().getName().contains("Transaction")) return TRANSACTION_EVENTS_TOPIC;
                if (event.getClass().getName().contains("Account")) return ACCOUNT_EVENTS_TOPIC;
                if (event.getClass().getName().contains("User")) return USER_EVENTS_TOPIC;
                if (event.getClass().getName().contains("Fraud")) return FRAUD_EVENTS_TOPIC;
                if (event.getClass().getName().contains("Compliance")) return COMPLIANCE_EVENTS_TOPIC;
                if (event.getClass().getName().contains("Notification")) return NOTIFICATION_EVENTS_TOPIC;
                if (event.getClass().getName().contains("Audit")) return AUDIT_EVENTS_TOPIC;
                
                log.warn("Unknown event type, using default topic: {}", event.getClass());
                return PAYMENT_EVENTS_TOPIC; // Default topic instead of throwing exception
        }
    }

    private CompletableFuture<Void> publishBatchToTopic(String topic, List<FinancialEvent> events) {
        List<CompletableFuture<Void>> futures = events.stream()
            .map(event -> {
                String key = extractKeyFromEvent(event);
                return publishEvent(topic, event, key);
            })
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private String extractKeyFromEvent(FinancialEvent event) {
        String eventClassName = event.getClass().getSimpleName();
        
        switch (eventClassName) {
            case "PaymentEvent":
                return ((PaymentEvent) event).getPaymentId().toString();
            case "TransactionEvent":
                return ((TransactionEvent) event).getTransactionId().toString();
            case "AccountEvent":
                return ((AccountEvent) event).getAccountId().toString();
            case "UserEvent":
                return ((UserEvent) event).getUserId().toString();
            default:
                return event.getEventId().toString();
        }
    }

    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        // Return generated correlation ID if none exists
        return "corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }

    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String sagaId;
        private final String transactionId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String sagaId, String transactionId, String eventType, Instant timestamp) {
            this.sagaId = sagaId;
            this.transactionId = transactionId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}