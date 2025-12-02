package com.waqiti.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.audit.domain.AuditEvent;
import com.waqiti.audit.repository.AuditEventRepository;
import com.waqiti.audit.service.AuditService;
import com.waqiti.audit.service.AuditAnalyticsEngine;
import com.waqiti.audit.service.SuspiciousActivityDetectionEngine;
import com.waqiti.audit.service.AuditNotificationService;
import com.waqiti.audit.service.ComprehensiveAuditService;
import com.waqiti.common.audit.AuditService as CommonAuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditEventsStreamConsumer {

    private final AuditEventRepository auditEventRepository;
    private final AuditService auditService;
    private final AuditAnalyticsEngine auditAnalyticsEngine;
    private final SuspiciousActivityDetectionEngine suspiciousActivityDetectionEngine;
    private final AuditNotificationService auditNotificationService;
    private final ComprehensiveAuditService comprehensiveAuditService;
    private final CommonAuditService commonAuditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Counter streamEventCounter;
    private Counter realTimeEventCounter;
    private Counter batchEventCounter;
    private Counter anomalyCounter;
    private Counter correlationCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("audit_events_stream_processed_total")
            .description("Total number of successfully processed audit events stream")
            .register(meterRegistry);
        errorCounter = Counter.builder("audit_events_stream_errors_total")
            .description("Total number of audit events stream processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("audit_events_stream_processing_duration")
            .description("Time taken to process audit events stream")
            .register(meterRegistry);
        streamEventCounter = Counter.builder("audit_events_stream_events_total")
            .description("Total number of audit events stream events received")
            .register(meterRegistry);
        realTimeEventCounter = Counter.builder("audit_events_realtime_stream_total")
            .description("Total number of real-time audit events stream events")
            .register(meterRegistry);
        batchEventCounter = Counter.builder("audit_events_batch_stream_total")
            .description("Total number of batch audit events stream events")
            .register(meterRegistry);
        anomalyCounter = Counter.builder("audit_events_stream_anomalies_total")
            .description("Total number of anomalies detected in audit events stream")
            .register(meterRegistry);
        correlationCounter = Counter.builder("audit_events_stream_correlations_total")
            .description("Total number of event correlations identified")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"audit.events.stream"},
        groupId = "audit-events-stream-processor-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "audit-events-stream", fallbackMethod = "handleAuditEventsStreamEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuditEventsStreamEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("audit-stream-event-%d-p%d-o%d", System.currentTimeMillis(), partition, offset);
        String eventKey = String.format("stream-event-%d-%d-%d", partition, offset, System.currentTimeMillis());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing audit events stream: partition={}, offset={}, correlationId={}",
                partition, offset, correlationId);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);

            String streamType = (String) eventData.get("streamType");
            Boolean isRealTime = (Boolean) eventData.getOrDefault("isRealTime", false);
            Boolean isBatch = (Boolean) eventData.getOrDefault("isBatch", false);
            String eventPattern = (String) eventData.get("eventPattern");
            String aggregationType = (String) eventData.get("aggregationType");
            Integer eventCount = eventData.get("eventCount") != null ?
                Integer.valueOf(eventData.get("eventCount").toString()) : 1;
            List<Map<String, Object>> events = (List<Map<String, Object>>) eventData.get("events");

            streamEventCounter.increment();
            if (isRealTime) {
                realTimeEventCounter.increment();
            }
            if (isBatch) {
                batchEventCounter.increment();
            }

            // Process audit events stream
            processAuditEventsStream(eventData, streamType, isRealTime, isBatch, eventPattern,
                aggregationType, eventCount, events, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            commonAuditService.logAuditEvent("AUDIT_EVENTS_STREAM_PROCESSED", correlationId,
                Map.of("streamType", streamType, "isRealTime", isRealTime, "isBatch", isBatch,
                    "eventCount", eventCount, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process audit events stream: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("audit-events-stream-fallback-events", Map.of(
                "originalMessage", message, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuditEventsStreamEventFallback(
            String message,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("audit-stream-event-fallback-%d-p%d-o%d",
            System.currentTimeMillis(), partition, offset);

        log.error("Circuit breaker fallback triggered for audit events stream: partition={}, offset={}, error={}",
            partition, offset, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("audit-events-stream-dlq", Map.of(
            "originalMessage", message,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Audit Events Stream Circuit Breaker Triggered",
                String.format("Audit events stream processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuditEventsStreamEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-audit-stream-event-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Audit events stream permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        commonAuditService.logAuditEvent("AUDIT_EVENTS_STREAM_DLT_EVENT", correlationId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Audit Events Stream Dead Letter Event",
                String.format("Audit events stream sent to DLT: %s", exceptionMessage),
                Map.of("topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processAuditEventsStream(Map<String, Object> eventData, String streamType,
            Boolean isRealTime, Boolean isBatch, String eventPattern, String aggregationType,
            Integer eventCount, List<Map<String, Object>> events, String correlationId) {

        log.info("Processing audit events stream: type={}, realTime={}, batch={}, count={}, correlationId={}",
            streamType, isRealTime, isBatch, eventCount, correlationId);

        // Process based on stream type
        switch (streamType.toUpperCase()) {
            case "LIVE_STREAM":
                processLiveStream(events, eventPattern, correlationId);
                break;
            case "BATCH_STREAM":
                processBatchStream(events, aggregationType, correlationId);
                break;
            case "ANALYTICAL_STREAM":
                processAnalyticalStream(events, eventPattern, correlationId);
                break;
            case "COMPLIANCE_STREAM":
                processComplianceStream(events, correlationId);
                break;
            case "SECURITY_STREAM":
                processSecurityStream(events, correlationId);
                break;
            case "CORRELATION_STREAM":
                processCorrelationStream(events, eventPattern, correlationId);
                break;
            default:
                processGenericStream(events, correlationId);
        }

        // Real-time processing for immediate actions
        if (isRealTime) {
            processRealTimeStream(events, correlationId);
        }

        // Batch processing for aggregated analytics
        if (isBatch) {
            processBatchAnalytics(events, aggregationType, correlationId);
        }

        // Pattern-based anomaly detection
        if (eventPattern != null) {
            detectStreamAnomalies(events, eventPattern, correlationId);
        }

        // Stream correlation analysis
        performStreamCorrelation(events, correlationId);

        // Send downstream events
        kafkaTemplate.send("audit-events-stream-processed", Map.of(
            "streamType", streamType,
            "isRealTime", isRealTime,
            "isBatch", isBatch,
            "eventCount", eventCount,
            "pattern", eventPattern != null ? eventPattern : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Completed audit events stream processing: type={}, count={}, correlationId={}",
            streamType, eventCount, correlationId);
    }

    private void processLiveStream(List<Map<String, Object>> events, String eventPattern, String correlationId) {
        log.info("Processing live stream with {} events, pattern={}, correlationId={}",
            events.size(), eventPattern, correlationId);

        for (Map<String, Object> eventData : events) {
            try {
                // Create and store audit event
                AuditEvent auditEvent = createAuditEventFromMap(eventData, correlationId);
                auditEventRepository.save(auditEvent);

                // Real-time suspicious activity detection
                if (auditEvent.getUserId() != null) {
                    boolean isSuspicious = suspiciousActivityDetectionEngine.analyzeBehavior(auditEvent);
                    if (isSuspicious) {
                        handleSuspiciousActivity(auditEvent, correlationId);
                    }
                }

                // Real-time analytics
                auditAnalyticsEngine.processAuditEvent(auditEvent, correlationId);

            } catch (Exception e) {
                log.error("Error processing live stream event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }

        // Send live stream summary
        kafkaTemplate.send("audit-live-stream-summary", Map.of(
            "eventsProcessed", events.size(),
            "pattern", eventPattern != null ? eventPattern : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processBatchStream(List<Map<String, Object>> events, String aggregationType, String correlationId) {
        log.info("Processing batch stream with {} events, aggregation={}, correlationId={}",
            events.size(), aggregationType, correlationId);

        List<AuditEvent> auditEvents = new ArrayList<>();

        for (Map<String, Object> eventData : events) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(eventData, correlationId);
                auditEvents.add(auditEvent);
            } catch (Exception e) {
                log.error("Error processing batch event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }

        // Batch save for performance
        if (!auditEvents.isEmpty()) {
            auditEventRepository.saveAll(auditEvents);
        }

        // Perform batch analytics
        if (aggregationType != null) {
            performBatchAggregation(auditEvents, aggregationType, correlationId);
        }

        // Send batch processing summary
        kafkaTemplate.send("audit-batch-stream-summary", Map.of(
            "eventsProcessed", auditEvents.size(),
            "aggregationType", aggregationType != null ? aggregationType : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processAnalyticalStream(List<Map<String, Object>> events, String eventPattern, String correlationId) {
        log.info("Processing analytical stream with {} events, pattern={}, correlationId={}",
            events.size(), eventPattern, correlationId);

        for (Map<String, Object> eventData : events) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(eventData, correlationId);
                auditEventRepository.save(auditEvent);

                // Advanced analytics processing
                auditAnalyticsEngine.performAdvancedAnalytics(auditEvent, eventPattern, correlationId);

            } catch (Exception e) {
                log.error("Error processing analytical event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }

        // Send to analytics engine
        kafkaTemplate.send("audit-analytics-stream", Map.of(
            "eventsCount", events.size(),
            "pattern", eventPattern != null ? eventPattern : "",
            "analysisType", "STREAM_ANALYTICS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processComplianceStream(List<Map<String, Object>> events, String correlationId) {
        log.info("Processing compliance stream with {} events, correlationId={}",
            events.size(), correlationId);

        int complianceViolations = 0;

        for (Map<String, Object> eventData : events) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(eventData, correlationId);
                auditEventRepository.save(auditEvent);

                // Check for compliance violations
                if (hasComplianceViolation(auditEvent)) {
                    complianceViolations++;
                    handleComplianceViolation(auditEvent, correlationId);
                }

            } catch (Exception e) {
                log.error("Error processing compliance event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }

        // Send compliance summary
        kafkaTemplate.send("compliance-stream-summary", Map.of(
            "eventsProcessed", events.size(),
            "complianceViolations", complianceViolations,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processSecurityStream(List<Map<String, Object>> events, String correlationId) {
        log.info("Processing security stream with {} events, correlationId={}",
            events.size(), correlationId);

        int securityIncidents = 0;

        for (Map<String, Object> eventData : events) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(eventData, correlationId);
                auditEventRepository.save(auditEvent);

                // Check for security incidents
                if (isSecurityIncident(auditEvent)) {
                    securityIncidents++;
                    handleSecurityIncident(auditEvent, correlationId);
                }

            } catch (Exception e) {
                log.error("Error processing security event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }

        // Send security summary
        kafkaTemplate.send("security-stream-summary", Map.of(
            "eventsProcessed", events.size(),
            "securityIncidents", securityIncidents,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processCorrelationStream(List<Map<String, Object>> events, String eventPattern, String correlationId) {
        log.info("Processing correlation stream with {} events, pattern={}, correlationId={}",
            events.size(), eventPattern, correlationId);

        List<AuditEvent> auditEvents = new ArrayList<>();

        for (Map<String, Object> eventData : events) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(eventData, correlationId);
                auditEvents.add(auditEvent);
            } catch (Exception e) {
                log.error("Error processing correlation event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }

        if (!auditEvents.isEmpty()) {
            auditEventRepository.saveAll(auditEvents);

            // Perform event correlation analysis
            performEventCorrelation(auditEvents, eventPattern, correlationId);
        }
    }

    private void processGenericStream(List<Map<String, Object>> events, String correlationId) {
        log.info("Processing generic stream with {} events, correlationId={}",
            events.size(), correlationId);

        for (Map<String, Object> eventData : events) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(eventData, correlationId);
                auditEventRepository.save(auditEvent);
            } catch (Exception e) {
                log.error("Error processing generic stream event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }
    }

    private void processRealTimeStream(List<Map<String, Object>> events, String correlationId) {
        for (Map<String, Object> eventData : events) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(eventData, correlationId);

                // Critical real-time checks
                if (auditEvent.getSeverity() == AuditEvent.AuditSeverity.CRITICAL ||
                    auditEvent.getResult() == AuditEvent.AuditResult.UNAUTHORIZED) {

                    handleCriticalRealTimeEvent(auditEvent, correlationId);
                }

            } catch (Exception e) {
                log.error("Error processing real-time event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }
    }

    private void processBatchAnalytics(List<Map<String, Object>> events, String aggregationType, String correlationId) {
        try {
            auditAnalyticsEngine.performBatchAnalytics(events, aggregationType, correlationId);
        } catch (Exception e) {
            log.error("Error performing batch analytics: correlationId={}, error={}",
                correlationId, e.getMessage());
        }
    }

    private void detectStreamAnomalies(List<Map<String, Object>> events, String eventPattern, String correlationId) {
        try {
            for (Map<String, Object> eventData : events) {
                AuditEvent auditEvent = createAuditEventFromMap(eventData, correlationId);

                boolean isAnomaly = suspiciousActivityDetectionEngine.detectPatternAnomaly(auditEvent, eventPattern);
                if (isAnomaly) {
                    anomalyCounter.increment();
                    handleStreamAnomaly(auditEvent, eventPattern, correlationId);
                }
            }
        } catch (Exception e) {
            log.error("Error detecting stream anomalies: correlationId={}, error={}",
                correlationId, e.getMessage());
        }
    }

    private void performStreamCorrelation(List<Map<String, Object>> events, String correlationId) {
        try {
            // Group events by various correlation criteria
            Map<String, List<Map<String, Object>>> correlatedEvents = groupEventsByCorrelation(events);

            for (Map.Entry<String, List<Map<String, Object>>> entry : correlatedEvents.entrySet()) {
                if (entry.getValue().size() > 1) {
                    correlationCounter.increment();
                    handleEventCorrelation(entry.getKey(), entry.getValue(), correlationId);
                }
            }
        } catch (Exception e) {
            log.error("Error performing stream correlation: correlationId={}, error={}",
                correlationId, e.getMessage());
        }
    }

    private void performBatchAggregation(List<AuditEvent> auditEvents, String aggregationType, String correlationId) {
        switch (aggregationType.toUpperCase()) {
            case "USER_ACTIVITY":
                aggregateByUser(auditEvents, correlationId);
                break;
            case "SERVICE_ACTIVITY":
                aggregateByService(auditEvents, correlationId);
                break;
            case "TIME_WINDOW":
                aggregateByTimeWindow(auditEvents, correlationId);
                break;
            case "TRANSACTION_FLOW":
                aggregateByTransactionFlow(auditEvents, correlationId);
                break;
            default:
                log.debug("Unknown aggregation type: {}", aggregationType);
        }
    }

    private void performEventCorrelation(List<AuditEvent> auditEvents, String eventPattern, String correlationId) {
        // Complex correlation logic based on pattern
        // This is a simplified implementation
        Map<String, List<AuditEvent>> correlatedByUser = new HashMap<>();
        Map<String, List<AuditEvent>> correlatedByTransaction = new HashMap<>();

        for (AuditEvent event : auditEvents) {
            if (event.getUserId() != null) {
                correlatedByUser.computeIfAbsent(event.getUserId(), k -> new ArrayList<>()).add(event);
            }
            if (event.getTransactionId() != null) {
                correlatedByTransaction.computeIfAbsent(event.getTransactionId(), k -> new ArrayList<>()).add(event);
            }
        }

        // Send correlation results
        kafkaTemplate.send("audit-event-correlations", Map.of(
            "userCorrelations", correlatedByUser.size(),
            "transactionCorrelations", correlatedByTransaction.size(),
            "pattern", eventPattern != null ? eventPattern : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleSuspiciousActivity(AuditEvent auditEvent, String correlationId) {
        log.warn("Suspicious activity detected in stream: user={}, action={}, correlationId={}",
            auditEvent.getUserId(), auditEvent.getAction(), correlationId);

        kafkaTemplate.send("suspicious-activity-stream", Map.of(
            "userId", auditEvent.getUserId(),
            "action", auditEvent.getAction(),
            "eventType", auditEvent.getEventType(),
            "severity", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleComplianceViolation(AuditEvent auditEvent, String correlationId) {
        log.warn("Compliance violation detected: eventType={}, action={}, correlationId={}",
            auditEvent.getEventType(), auditEvent.getAction(), correlationId);

        kafkaTemplate.send("compliance-violations", Map.of(
            "eventId", auditEvent.getId(),
            "eventType", auditEvent.getEventType(),
            "action", auditEvent.getAction(),
            "userId", auditEvent.getUserId() != null ? auditEvent.getUserId() : "",
            "violationType", "COMPLIANCE_STREAM_VIOLATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleSecurityIncident(AuditEvent auditEvent, String correlationId) {
        log.error("Security incident detected: eventType={}, action={}, correlationId={}",
            auditEvent.getEventType(), auditEvent.getAction(), correlationId);

        kafkaTemplate.send("security-incidents", Map.of(
            "eventId", auditEvent.getId(),
            "eventType", auditEvent.getEventType(),
            "action", auditEvent.getAction(),
            "userId", auditEvent.getUserId() != null ? auditEvent.getUserId() : "",
            "severity", auditEvent.getSeverity().toString(),
            "incidentType", "SECURITY_STREAM_INCIDENT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleCriticalRealTimeEvent(AuditEvent auditEvent, String correlationId) {
        log.error("CRITICAL real-time event: eventType={}, action={}, result={}, correlationId={}",
            auditEvent.getEventType(), auditEvent.getAction(), auditEvent.getResult(), correlationId);

        kafkaTemplate.send("critical-realtime-events", Map.of(
            "eventId", auditEvent.getId(),
            "eventType", auditEvent.getEventType(),
            "action", auditEvent.getAction(),
            "result", auditEvent.getResult().toString(),
            "severity", auditEvent.getSeverity().toString(),
            "requiresImmediateAction", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleStreamAnomaly(AuditEvent auditEvent, String eventPattern, String correlationId) {
        log.warn("Stream anomaly detected: pattern={}, eventType={}, action={}, correlationId={}",
            eventPattern, auditEvent.getEventType(), auditEvent.getAction(), correlationId);

        kafkaTemplate.send("audit-stream-anomalies", Map.of(
            "eventId", auditEvent.getId(),
            "pattern", eventPattern,
            "eventType", auditEvent.getEventType(),
            "action", auditEvent.getAction(),
            "anomalyType", "PATTERN_DEVIATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleEventCorrelation(String correlationKey, List<Map<String, Object>> correlatedEvents, String correlationId) {
        log.info("Event correlation found: key={}, eventCount={}, correlationId={}",
            correlationKey, correlatedEvents.size(), correlationId);

        kafkaTemplate.send("audit-event-correlation-found", Map.of(
            "correlationKey", correlationKey,
            "eventCount", correlatedEvents.size(),
            "correlationType", "STREAM_CORRELATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private boolean hasComplianceViolation(AuditEvent auditEvent) {
        // Simplified compliance check
        return auditEvent.getResult() == AuditEvent.AuditResult.UNAUTHORIZED ||
               auditEvent.getResult() == AuditEvent.AuditResult.FORBIDDEN ||
               (auditEvent.getComplianceTags() != null && auditEvent.getComplianceTags().contains("VIOLATION"));
    }

    private boolean isSecurityIncident(AuditEvent auditEvent) {
        // Simplified security incident check
        return auditEvent.getSeverity() == AuditEvent.AuditSeverity.CRITICAL ||
               "SECURITY".equals(auditEvent.getEventType()) ||
               auditEvent.getResult() == AuditEvent.AuditResult.UNAUTHORIZED;
    }

    private Map<String, List<Map<String, Object>>> groupEventsByCorrelation(List<Map<String, Object>> events) {
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();

        for (Map<String, Object> event : events) {
            String userId = (String) event.get("userId");
            String transactionId = (String) event.get("transactionId");
            String sessionId = (String) event.get("sessionId");

            if (userId != null) {
                grouped.computeIfAbsent("user:" + userId, k -> new ArrayList<>()).add(event);
            }
            if (transactionId != null) {
                grouped.computeIfAbsent("transaction:" + transactionId, k -> new ArrayList<>()).add(event);
            }
            if (sessionId != null) {
                grouped.computeIfAbsent("session:" + sessionId, k -> new ArrayList<>()).add(event);
            }
        }

        return grouped;
    }

    private void aggregateByUser(List<AuditEvent> events, String correlationId) {
        Map<String, Long> userCounts = new HashMap<>();
        for (AuditEvent event : events) {
            if (event.getUserId() != null) {
                userCounts.merge(event.getUserId(), 1L, Long::sum);
            }
        }

        kafkaTemplate.send("audit-user-aggregations", Map.of(
            "userCounts", userCounts,
            "totalEvents", events.size(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void aggregateByService(List<AuditEvent> events, String correlationId) {
        Map<String, Long> serviceCounts = new HashMap<>();
        for (AuditEvent event : events) {
            if (event.getServiceName() != null) {
                serviceCounts.merge(event.getServiceName(), 1L, Long::sum);
            }
        }

        kafkaTemplate.send("audit-service-aggregations", Map.of(
            "serviceCounts", serviceCounts,
            "totalEvents", events.size(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void aggregateByTimeWindow(List<AuditEvent> events, String correlationId) {
        // Time-based aggregation logic
        kafkaTemplate.send("audit-time-aggregations", Map.of(
            "totalEvents", events.size(),
            "timeWindow", "BATCH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void aggregateByTransactionFlow(List<AuditEvent> events, String correlationId) {
        Map<String, Long> transactionCounts = new HashMap<>();
        for (AuditEvent event : events) {
            if (event.getTransactionId() != null) {
                transactionCounts.merge(event.getTransactionId(), 1L, Long::sum);
            }
        }

        kafkaTemplate.send("audit-transaction-aggregations", Map.of(
            "transactionCounts", transactionCounts,
            "totalEvents", events.size(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private AuditEvent createAuditEventFromMap(Map<String, Object> eventData, String correlationId) {
        return AuditEvent.builder()
            .eventType((String) eventData.get("eventType"))
            .serviceName((String) eventData.get("serviceName"))
            .userId((String) eventData.get("userId"))
            .sessionId((String) eventData.get("sessionId"))
            .transactionId((String) eventData.get("transactionId"))
            .resourceId((String) eventData.get("resourceId"))
            .resourceType((String) eventData.get("resourceType"))
            .action((String) eventData.get("action"))
            .description((String) eventData.get("description"))
            .result(mapAuditResult((String) eventData.get("result")))
            .severity(mapAuditSeverity((String) eventData.get("severity")))
            .ipAddress((String) eventData.get("ipAddress"))
            .userAgent((String) eventData.get("userAgent"))
            .correlationId(correlationId)
            .metadata(convertToStringMap((Map<String, Object>) eventData.get("metadata")))
            .beforeState((String) eventData.get("beforeState"))
            .afterState((String) eventData.get("afterState"))
            .durationMs(eventData.get("durationMs") != null ?
                Long.valueOf(eventData.get("durationMs").toString()) : null)
            .complianceTags((String) eventData.get("complianceTags"))
            .build();
    }

    private AuditEvent.AuditResult mapAuditResult(String result) {
        if (result == null) return AuditEvent.AuditResult.SUCCESS;

        return switch (result.toUpperCase()) {
            case "SUCCESS" -> AuditEvent.AuditResult.SUCCESS;
            case "FAILURE" -> AuditEvent.AuditResult.FAILURE;
            case "PARTIAL_SUCCESS" -> AuditEvent.AuditResult.PARTIAL_SUCCESS;
            case "UNAUTHORIZED" -> AuditEvent.AuditResult.UNAUTHORIZED;
            case "FORBIDDEN" -> AuditEvent.AuditResult.FORBIDDEN;
            case "NOT_FOUND" -> AuditEvent.AuditResult.NOT_FOUND;
            case "VALIDATION_ERROR" -> AuditEvent.AuditResult.VALIDATION_ERROR;
            case "SYSTEM_ERROR" -> AuditEvent.AuditResult.SYSTEM_ERROR;
            default -> AuditEvent.AuditResult.SUCCESS;
        };
    }

    private AuditEvent.AuditSeverity mapAuditSeverity(String severity) {
        if (severity == null) return AuditEvent.AuditSeverity.MEDIUM;

        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> AuditEvent.AuditSeverity.CRITICAL;
            case "HIGH" -> AuditEvent.AuditSeverity.HIGH;
            case "MEDIUM" -> AuditEvent.AuditSeverity.MEDIUM;
            case "LOW" -> AuditEvent.AuditSeverity.LOW;
            default -> AuditEvent.AuditSeverity.MEDIUM;
        };
    }

    private Map<String, String> convertToStringMap(Map<String, Object> objectMap) {
        if (objectMap == null) return new HashMap<>();

        Map<String, String> stringMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : objectMap.entrySet()) {
            stringMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
        }
        return stringMap;
    }
}