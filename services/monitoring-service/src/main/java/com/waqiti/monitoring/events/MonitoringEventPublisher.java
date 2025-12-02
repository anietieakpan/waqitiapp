package com.waqiti.monitoring.events;

import com.waqiti.monitoring.events.model.MonitoringEvent;
import com.waqiti.monitoring.events.model.DeadLetterMonitoringEvent;
import com.waqiti.monitoring.events.store.MonitoringEventStore;
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
 * Enterprise-grade monitoring event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MonitoringEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final MonitoringEventStore eventStore;
    
    // Topic constants
    private static final String SYSTEM_PERFORMANCE_EVENTS_TOPIC = "system-performance-events";
    private static final String ALERT_MANAGEMENT_EVENTS_TOPIC = "alert-management-events";
    private static final String LOG_ANALYTICS_EVENTS_TOPIC = "log-analytics-events";
    private static final String HEALTH_CHECK_EVENTS_TOPIC = "health-check-events";
    private static final String METRICS_COLLECTION_EVENTS_TOPIC = "metrics-collection-events";
    private static final String INCIDENT_MANAGEMENT_EVENTS_TOPIC = "incident-management-events";
    private static final String CAPACITY_PLANNING_EVENTS_TOPIC = "capacity-planning-events";
    private static final String COMPLIANCE_MONITORING_EVENTS_TOPIC = "compliance-monitoring-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<MonitoringEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter monitoringEventsPublished;
    private Counter monitoringEventsFailure;
    private Timer monitoringEventPublishLatency;
    
    /**
     * Publishes system performance event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishSystemPerformance(
            String monitoringId, String serviceId, String performanceMetric, BigDecimal metricValue,
            String systemComponent, BigDecimal responseTime, BigDecimal throughput, BigDecimal errorRate) {
        
        MonitoringEvent event = MonitoringEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("SYSTEM_PERFORMANCE")
                .monitoringId(monitoringId)
                .serviceId(serviceId)
                .performanceMetric(performanceMetric)
                .metricValue(metricValue)
                .systemComponent(systemComponent)
                .responseTime(responseTime)
                .throughput(throughput)
                .errorRate(errorRate)
                .measurementTime(Instant.now())
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(SYSTEM_PERFORMANCE_EVENTS_TOPIC, event, monitoringId);
    }
    
    /**
     * Publishes alert management event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishAlertManagement(
            String monitoringId, String alertId, String alertType, String alertSeverity,
            String alertStatus, String serviceId, String metricName, BigDecimal threshold) {
        
        MonitoringEvent event = MonitoringEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ALERT_MANAGEMENT")
                .monitoringId(monitoringId)
                .alertId(alertId)
                .alertType(alertType)
                .alertSeverity(alertSeverity)
                .alertStatus(alertStatus)
                .serviceId(serviceId)
                .metricName(metricName)
                .threshold(threshold)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(ALERT_MANAGEMENT_EVENTS_TOPIC, event, monitoringId);
    }
    
    /**
     * Publishes log analytics event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishLogAnalytics(
            String monitoringId, String logLevel, String logMessage, String logSource,
            String serviceId, String environment, String userId) {
        
        MonitoringEvent event = MonitoringEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("LOG_ANALYTICS")
                .monitoringId(monitoringId)
                .logLevel(logLevel)
                .logMessage(logMessage)
                .logSource(logSource)
                .serviceId(serviceId)
                .environment(environment)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(LOG_ANALYTICS_EVENTS_TOPIC, event, monitoringId);
    }
    
    /**
     * Publishes health check event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishHealthCheck(
            String monitoringId, String serviceId, String healthCheckType, String healthStatus,
            String nodeId, String clusterId, String region, String environment) {
        
        MonitoringEvent event = MonitoringEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("HEALTH_CHECK")
                .monitoringId(monitoringId)
                .serviceId(serviceId)
                .healthCheckType(healthCheckType)
                .healthStatus(healthStatus)
                .nodeId(nodeId)
                .clusterId(clusterId)
                .region(region)
                .environment(environment)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(HEALTH_CHECK_EVENTS_TOPIC, event, monitoringId);
    }
    
    /**
     * Publishes metrics collection event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishMetricsCollection(
            String monitoringId, String metricName, String metricType, BigDecimal metricValue,
            String serviceId, String resourceType, Instant measurementTime, String userId) {
        
        MonitoringEvent event = MonitoringEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("METRICS_COLLECTION")
                .monitoringId(monitoringId)
                .metricName(metricName)
                .metricType(metricType)
                .metricValue(metricValue)
                .serviceId(serviceId)
                .resourceType(resourceType)
                .measurementTime(measurementTime)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(METRICS_COLLECTION_EVENTS_TOPIC, event, monitoringId);
    }
    
    /**
     * Publishes incident management event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishIncidentManagement(
            String monitoringId, String incidentId, String incidentType, String incidentStatus,
            String incidentSeverity, String serviceId, String description, String userId) {
        
        MonitoringEvent event = MonitoringEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("INCIDENT_MANAGEMENT")
                .monitoringId(monitoringId)
                .incidentId(incidentId)
                .incidentType(incidentType)
                .incidentStatus(incidentStatus)
                .incidentSeverity(incidentSeverity)
                .serviceId(serviceId)
                .description(description)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishHighPriorityEvent(INCIDENT_MANAGEMENT_EVENTS_TOPIC, event, monitoringId);
    }
    
    /**
     * Publishes capacity planning event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCapacityPlanning(
            String monitoringId, String serviceId, String capacityMetric, BigDecimal capacityUtilization,
            BigDecimal capacityThreshold, String resourceType, String region, String userId) {
        
        MonitoringEvent event = MonitoringEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CAPACITY_PLANNING")
                .monitoringId(monitoringId)
                .serviceId(serviceId)
                .capacityMetric(capacityMetric)
                .capacityUtilization(capacityUtilization)
                .capacityThreshold(capacityThreshold)
                .resourceType(resourceType)
                .region(region)
                .userId(userId)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(CAPACITY_PLANNING_EVENTS_TOPIC, event, monitoringId);
    }
    
    /**
     * Publishes compliance monitoring event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishComplianceMonitoring(
            String monitoringId, String serviceId, String complianceRule, String complianceStatus,
            String violationType, String environment, String userId, String description) {
        
        MonitoringEvent event = MonitoringEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("COMPLIANCE_MONITORING")
                .monitoringId(monitoringId)
                .serviceId(serviceId)
                .complianceRule(complianceRule)
                .complianceStatus(complianceStatus)
                .violationType(violationType)
                .environment(environment)
                .userId(userId)
                .description(description)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(COMPLIANCE_MONITORING_EVENTS_TOPIC, event, monitoringId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<MonitoringEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<MonitoringEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<MonitoringEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<MonitoringEvent> topicEvents = entry.getValue();
            
            for (MonitoringEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future = 
                    publishEvent(topic, event, event.getMonitoringId());
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getMonitoringEventPublishLatency());
                if (ex == null) {
                    log.info("Batch monitoring events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getMonitoringEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch monitoring events", ex);
                    getMonitoringEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, MonitoringEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing monitoring event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for monitoring events")
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
                sample.stop(getMonitoringEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getMonitoringEventPublishLatency());
            log.error("Failed to publish monitoring event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, MonitoringEvent event, String key) {
        
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
            
            sample.stop(getMonitoringEventPublishLatency());
            onPublishSuccess(event, result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            sample.stop(getMonitoringEventPublishLatency());
            log.error("Failed to publish high-priority monitoring event: {}", event.getEventType(), e);
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
        
        log.info("Replaying {} failed monitoring events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        MonitoringEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, event.getMonitoringId());
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed monitoring event replay"));
    }
    
    // Helper methods
    
    private void onPublishSuccess(MonitoringEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getMonitoringEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("Monitoring event published successfully: type={}, monitoringId={}, offset={}", 
            event.getEventType(), event.getMonitoringId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(MonitoringEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getMonitoringEventsFailure().increment();
        
        log.error("Failed to publish monitoring event: type={}, monitoringId={}, topic={}", 
            event.getEventType(), event.getMonitoringId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(MonitoringEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed monitoring event for retry: type={}, monitoringId={}", 
            event.getEventType(), event.getMonitoringId());
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
        log.error("Monitoring event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Monitoring event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, MonitoringEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterMonitoringEvent dlqEvent = DeadLetterMonitoringEvent.builder()
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
                    log.warn("Monitoring event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send monitoring event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish monitoring event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "monitoring-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(MonitoringEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getMonitoringId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<MonitoringEvent>> groupEventsByTopic(List<MonitoringEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "SYSTEM_PERFORMANCE":
                return SYSTEM_PERFORMANCE_EVENTS_TOPIC;
            case "ALERT_MANAGEMENT":
                return ALERT_MANAGEMENT_EVENTS_TOPIC;
            case "LOG_ANALYTICS":
                return LOG_ANALYTICS_EVENTS_TOPIC;
            case "HEALTH_CHECK":
                return HEALTH_CHECK_EVENTS_TOPIC;
            case "METRICS_COLLECTION":
                return METRICS_COLLECTION_EVENTS_TOPIC;
            case "INCIDENT_MANAGEMENT":
                return INCIDENT_MANAGEMENT_EVENTS_TOPIC;
            case "CAPACITY_PLANNING":
                return CAPACITY_PLANNING_EVENTS_TOPIC;
            case "COMPLIANCE_MONITORING":
                return COMPLIANCE_MONITORING_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown monitoring event type: " + eventType);
        }
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "monitoring-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getMonitoringEventsPublished() {
        if (monitoringEventsPublished == null) {
            monitoringEventsPublished = Counter.builder("monitoring.events.published")
                .description("Number of monitoring events published")
                .register(meterRegistry);
        }
        return monitoringEventsPublished;
    }
    
    private Counter getMonitoringEventsFailure() {
        if (monitoringEventsFailure == null) {
            monitoringEventsFailure = Counter.builder("monitoring.events.failure")
                .description("Number of monitoring events that failed to publish")
                .register(meterRegistry);
        }
        return monitoringEventsFailure;
    }
    
    private Timer getMonitoringEventPublishLatency() {
        if (monitoringEventPublishLatency == null) {
            monitoringEventPublishLatency = Timer.builder("monitoring.events.publish.latency")
                .description("Latency of monitoring event publishing")
                .register(meterRegistry);
        }
        return monitoringEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String monitoringId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String monitoringId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.monitoringId = monitoringId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}