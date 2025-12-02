package com.waqiti.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.audit.domain.AuditEvent;
import com.waqiti.audit.repository.AuditEventRepository;
import com.waqiti.audit.service.AuditService;
import com.waqiti.audit.service.ComprehensiveAuditService;
import com.waqiti.audit.service.AuditAnalyticsEngine;
import com.waqiti.audit.service.SuspiciousActivityDetectionEngine;
import com.waqiti.audit.service.ComprehensiveAuditTrailService;
import com.waqiti.audit.service.AuditNotificationService;
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
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditStreamConsumer {

    private final AuditEventRepository auditEventRepository;
    private final AuditService auditService;
    private final ComprehensiveAuditService comprehensiveAuditService;
    private final AuditAnalyticsEngine auditAnalyticsEngine;
    private final SuspiciousActivityDetectionEngine suspiciousActivityDetectionEngine;
    private final ComprehensiveAuditTrailService auditTrailService;
    private final AuditNotificationService auditNotificationService;
    private final CommonAuditService commonAuditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Stream processing state
    private final ConcurrentHashMap<String, List<Map<String, Object>>> streamBuffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> streamTimestamps = new ConcurrentHashMap<>();

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Counter streamEventCounter;
    private Counter batchProcessingCounter;
    private Counter realTimeProcessingCounter;
    private Counter aggregationCounter;
    private Counter correlationCounter;
    private Counter patternDetectionCounter;
    private Counter streamingAnalyticsCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("audit_stream_processed_total")
            .description("Total number of successfully processed audit stream events")
            .register(meterRegistry);
        errorCounter = Counter.builder("audit_stream_errors_total")
            .description("Total number of audit stream processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("audit_stream_processing_duration")
            .description("Time taken to process audit stream events")
            .register(meterRegistry);
        streamEventCounter = Counter.builder("audit_stream_events_total")
            .description("Total number of audit stream events received")
            .register(meterRegistry);
        batchProcessingCounter = Counter.builder("audit_stream_batch_processing_total")
            .description("Total number of batch processing operations")
            .register(meterRegistry);
        realTimeProcessingCounter = Counter.builder("audit_stream_realtime_processing_total")
            .description("Total number of real-time processing operations")
            .register(meterRegistry);
        aggregationCounter = Counter.builder("audit_stream_aggregations_total")
            .description("Total number of stream aggregations performed")
            .register(meterRegistry);
        correlationCounter = Counter.builder("audit_stream_correlations_total")
            .description("Total number of stream correlations found")
            .register(meterRegistry);
        patternDetectionCounter = Counter.builder("audit_stream_patterns_detected_total")
            .description("Total number of patterns detected in audit streams")
            .register(meterRegistry);
        streamingAnalyticsCounter = Counter.builder("audit_streaming_analytics_total")
            .description("Total number of streaming analytics operations")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"audit-stream"},
        groupId = "audit-stream-processor-group",
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
    @CircuitBreaker(name = "audit-stream", fallbackMethod = "handleAuditStreamEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuditStreamEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("audit-stream-%d-p%d-o%d", System.currentTimeMillis(), partition, offset);
        String eventKey = String.format("stream-%d-%d-%d", partition, offset, System.currentTimeMillis());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing audit stream event: partition={}, offset={}, correlationId={}",
                partition, offset, correlationId);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);

            String streamType = (String) eventData.get("streamType");
            String processingMode = (String) eventData.get("processingMode");
            Boolean isRealTime = (Boolean) eventData.getOrDefault("isRealTime", true);
            String aggregationWindow = (String) eventData.get("aggregationWindow");
            String streamKey = (String) eventData.get("streamKey");
            List<Map<String, Object>> events = (List<Map<String, Object>>) eventData.get("events");
            Map<String, Object> streamMetadata = (Map<String, Object>) eventData.get("streamMetadata");
            String patternDefinition = (String) eventData.get("patternDefinition");
            Boolean enableCorrelation = (Boolean) eventData.getOrDefault("enableCorrelation", true);
            Boolean enableAggregation = (Boolean) eventData.getOrDefault("enableAggregation", false);

            streamEventCounter.increment();
            if (isRealTime) {
                realTimeProcessingCounter.increment();
            } else {
                batchProcessingCounter.increment();
            }

            // Process audit stream
            processAuditStream(eventData, streamType, processingMode, isRealTime, aggregationWindow,
                streamKey, events, streamMetadata, patternDefinition, enableCorrelation,
                enableAggregation, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            commonAuditService.logAuditEvent("AUDIT_STREAM_PROCESSED", correlationId,
                Map.of("streamType", streamType, "processingMode", processingMode, "isRealTime", isRealTime,
                    "eventCount", events != null ? events.size() : 0, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process audit stream event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("audit-stream-fallback-events", Map.of(
                "originalMessage", message, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuditStreamEventFallback(
            String message,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("audit-stream-fallback-%d-p%d-o%d",
            System.currentTimeMillis(), partition, offset);

        log.error("Circuit breaker fallback triggered for audit stream: partition={}, offset={}, error={}",
            partition, offset, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("audit-stream-dlq", Map.of(
            "originalMessage", message,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Audit Stream Circuit Breaker Triggered",
                String.format("Audit stream processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuditStreamEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-audit-stream-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Audit stream permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        commonAuditService.logAuditEvent("AUDIT_STREAM_DLT_EVENT", correlationId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Audit Stream Dead Letter Event",
                String.format("Audit stream processing sent to DLT: %s", exceptionMessage),
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

        // Clean stream buffers and timestamps
        cleanStreamBuffers();
    }

    private void cleanStreamBuffers() {
        long currentTime = System.currentTimeMillis();
        streamTimestamps.entrySet().removeIf(entry -> {
            boolean expired = currentTime - entry.getValue() > TTL_24_HOURS;
            if (expired) {
                streamBuffers.remove(entry.getKey());
            }
            return expired;
        });
    }

    private void processAuditStream(Map<String, Object> eventData, String streamType,
            String processingMode, Boolean isRealTime, String aggregationWindow, String streamKey,
            List<Map<String, Object>> events, Map<String, Object> streamMetadata,
            String patternDefinition, Boolean enableCorrelation, Boolean enableAggregation,
            String correlationId) {

        log.info("Processing audit stream: type={}, mode={}, realTime={}, events={}, correlationId={}",
            streamType, processingMode, isRealTime, events != null ? events.size() : 0, correlationId);

        // Process based on stream type
        switch (streamType.toUpperCase()) {
            case "CONTINUOUS_AUDIT":
                processContinuousAuditStream(events, isRealTime, streamMetadata, correlationId);
                break;
            case "TRANSACTION_FLOW":
                processTransactionFlowStream(events, aggregationWindow, streamKey, correlationId);
                break;
            case "USER_BEHAVIOR":
                processUserBehaviorStream(events, patternDefinition, enableCorrelation, correlationId);
                break;
            case "SECURITY_MONITORING":
                processSecurityMonitoringStream(events, isRealTime, patternDefinition, correlationId);
                break;
            case "COMPLIANCE_TRACKING":
                processComplianceTrackingStream(events, streamMetadata, correlationId);
                break;
            case "PERFORMANCE_METRICS":
                processPerformanceMetricsStream(events, aggregationWindow, enableAggregation, correlationId);
                break;
            case "ANOMALY_DETECTION":
                processAnomalyDetectionStream(events, patternDefinition, streamMetadata, correlationId);
                break;
            case "CORRELATION_ANALYSIS":
                processCorrelationAnalysisStream(events, streamKey, enableCorrelation, correlationId);
                break;
            default:
                processGenericAuditStream(events, processingMode, correlationId);
        }

        // Real-time processing
        if (isRealTime) {
            processRealTimeAuditStream(events, streamType, correlationId);
        }

        // Pattern detection
        if (patternDefinition != null) {
            detectStreamPatterns(events, patternDefinition, correlationId);
        }

        // Correlation analysis
        if (enableCorrelation) {
            performStreamCorrelationAnalysis(events, streamKey, correlationId);
        }

        // Aggregation processing
        if (enableAggregation && aggregationWindow != null) {
            performStreamAggregation(events, aggregationWindow, streamKey, correlationId);
        }

        // Streaming analytics
        performStreamingAnalytics(events, streamType, streamMetadata, correlationId);

        // Send downstream events
        kafkaTemplate.send("audit-stream-processed", Map.of(
            "streamType", streamType,
            "processingMode", processingMode,
            "isRealTime", isRealTime,
            "eventCount", events != null ? events.size() : 0,
            "streamKey", streamKey != null ? streamKey : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Completed audit stream processing: type={}, events={}, correlationId={}",
            streamType, events != null ? events.size() : 0, correlationId);
    }

    private void processContinuousAuditStream(List<Map<String, Object>> events, Boolean isRealTime,
            Map<String, Object> streamMetadata, String correlationId) {

        log.info("Processing continuous audit stream: events={}, realTime={}, correlationId={}",
            events.size(), isRealTime, correlationId);

        List<AuditEvent> auditEvents = new ArrayList<>();

        for (Map<String, Object> eventData : events) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(eventData, correlationId);
                auditEvents.add(auditEvent);

                // Real-time continuous monitoring
                if (isRealTime) {
                    auditTrailService.addToContinuousTrail(auditEvent, correlationId);
                }

            } catch (Exception e) {
                log.error("Error processing continuous audit event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }

        // Batch save for performance
        if (!auditEvents.isEmpty()) {
            auditEventRepository.saveAll(auditEvents);
        }

        // Send to continuous audit monitoring
        kafkaTemplate.send("continuous-audit-monitoring", Map.of(
            "eventsProcessed", auditEvents.size(),
            "isRealTime", isRealTime,
            "streamMetadata", streamMetadata != null ? streamMetadata : new HashMap<>(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processTransactionFlowStream(List<Map<String, Object>> events, String aggregationWindow,
            String streamKey, String correlationId) {

        log.info("Processing transaction flow stream: events={}, window={}, key={}, correlationId={}",
            events.size(), aggregationWindow, streamKey, correlationId);

        Map<String, List<Map<String, Object>>> transactionFlows = groupEventsByTransaction(events);

        for (Map.Entry<String, List<Map<String, Object>>> entry : transactionFlows.entrySet()) {
            String transactionId = entry.getKey();
            List<Map<String, Object>> transactionEvents = entry.getValue();

            // Analyze transaction flow
            analyzeTransactionFlow(transactionId, transactionEvents, correlationId);
        }

        // Send transaction flow summary
        kafkaTemplate.send("transaction-flow-analysis", Map.of(
            "transactionFlows", transactionFlows.size(),
            "totalEvents", events.size(),
            "aggregationWindow", aggregationWindow != null ? aggregationWindow : "",
            "streamKey", streamKey != null ? streamKey : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processUserBehaviorStream(List<Map<String, Object>> events, String patternDefinition,
            Boolean enableCorrelation, String correlationId) {

        log.info("Processing user behavior stream: events={}, pattern={}, correlation={}, correlationId={}",
            events.size(), patternDefinition, enableCorrelation, correlationId);

        Map<String, List<Map<String, Object>>> userBehaviors = groupEventsByUser(events);

        for (Map.Entry<String, List<Map<String, Object>>> entry : userBehaviors.entrySet()) {
            String userId = entry.getKey();
            List<Map<String, Object>> userEvents = entry.getValue();

            // Analyze user behavior patterns
            analyzeUserBehavior(userId, userEvents, patternDefinition, correlationId);

            // Check for suspicious behavior
            if (userEvents.size() > 1) {
                checkForSuspiciousBehavior(userId, userEvents, correlationId);
            }
        }

        // Send user behavior analysis
        kafkaTemplate.send("user-behavior-analysis", Map.of(
            "uniqueUsers", userBehaviors.size(),
            "totalEvents", events.size(),
            "patternDefinition", patternDefinition != null ? patternDefinition : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processSecurityMonitoringStream(List<Map<String, Object>> events, Boolean isRealTime,
            String patternDefinition, String correlationId) {

        log.info("Processing security monitoring stream: events={}, realTime={}, pattern={}, correlationId={}",
            events.size(), isRealTime, patternDefinition, correlationId);

        List<Map<String, Object>> securityEvents = events.stream()
            .filter(event -> isSecurityEvent(event))
            .collect(Collectors.toList());

        for (Map<String, Object> securityEvent : securityEvents) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(securityEvent, correlationId);
                auditEventRepository.save(auditEvent);

                // Real-time security monitoring
                if (isRealTime && isHighRiskSecurityEvent(securityEvent)) {
                    handleHighRiskSecurityEvent(auditEvent, correlationId);
                }

            } catch (Exception e) {
                log.error("Error processing security event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }

        // Send security monitoring summary
        kafkaTemplate.send("security-monitoring-analysis", Map.of(
            "securityEvents", securityEvents.size(),
            "totalEvents", events.size(),
            "isRealTime", isRealTime,
            "patternDefinition", patternDefinition != null ? patternDefinition : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processComplianceTrackingStream(List<Map<String, Object>> events,
            Map<String, Object> streamMetadata, String correlationId) {

        log.info("Processing compliance tracking stream: events={}, correlationId={}",
            events.size(), correlationId);

        List<Map<String, Object>> complianceEvents = events.stream()
            .filter(event -> hasComplianceImplication(event))
            .collect(Collectors.toList());

        for (Map<String, Object> complianceEvent : complianceEvents) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(complianceEvent, correlationId);
                auditEventRepository.save(auditEvent);

                // Check for compliance violations
                checkComplianceViolation(auditEvent, correlationId);

            } catch (Exception e) {
                log.error("Error processing compliance event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }

        // Send compliance tracking summary
        kafkaTemplate.send("compliance-tracking-analysis", Map.of(
            "complianceEvents", complianceEvents.size(),
            "totalEvents", events.size(),
            "streamMetadata", streamMetadata != null ? streamMetadata : new HashMap<>(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processPerformanceMetricsStream(List<Map<String, Object>> events, String aggregationWindow,
            Boolean enableAggregation, String correlationId) {

        log.info("Processing performance metrics stream: events={}, window={}, aggregation={}, correlationId={}",
            events.size(), aggregationWindow, enableAggregation, correlationId);

        Map<String, Object> performanceMetrics = calculatePerformanceMetrics(events);

        if (enableAggregation) {
            aggregatePerformanceMetrics(performanceMetrics, aggregationWindow, correlationId);
        }

        // Send performance metrics analysis
        kafkaTemplate.send("performance-metrics-analysis", Map.of(
            "eventsProcessed", events.size(),
            "performanceMetrics", performanceMetrics,
            "aggregationWindow", aggregationWindow != null ? aggregationWindow : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processAnomalyDetectionStream(List<Map<String, Object>> events, String patternDefinition,
            Map<String, Object> streamMetadata, String correlationId) {

        log.info("Processing anomaly detection stream: events={}, pattern={}, correlationId={}",
            events.size(), patternDefinition, correlationId);

        List<Map<String, Object>> anomalies = new ArrayList<>();

        for (Map<String, Object> event : events) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(event, correlationId);

                // Detect anomalies
                boolean isAnomaly = suspiciousActivityDetectionEngine.detectAnomaly(auditEvent, patternDefinition);
                if (isAnomaly) {
                    anomalies.add(event);
                    handleDetectedAnomaly(auditEvent, correlationId);
                }

            } catch (Exception e) {
                log.error("Error processing anomaly detection event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }

        // Send anomaly detection summary
        kafkaTemplate.send("anomaly-detection-analysis", Map.of(
            "anomaliesDetected", anomalies.size(),
            "totalEvents", events.size(),
            "patternDefinition", patternDefinition != null ? patternDefinition : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processCorrelationAnalysisStream(List<Map<String, Object>> events, String streamKey,
            Boolean enableCorrelation, String correlationId) {

        log.info("Processing correlation analysis stream: events={}, key={}, correlation={}, correlationId={}",
            events.size(), streamKey, enableCorrelation, correlationId);

        if (enableCorrelation) {
            Map<String, List<Map<String, Object>>> correlatedEvents = performEventCorrelation(events, streamKey);

            for (Map.Entry<String, List<Map<String, Object>>> entry : correlatedEvents.entrySet()) {
                if (entry.getValue().size() > 1) {
                    correlationCounter.increment();
                    handleCorrelatedEvents(entry.getKey(), entry.getValue(), correlationId);
                }
            }

            // Send correlation analysis summary
            kafkaTemplate.send("correlation-analysis-results", Map.of(
                "correlationGroups", correlatedEvents.size(),
                "totalEvents", events.size(),
                "streamKey", streamKey != null ? streamKey : "",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
    }

    private void processGenericAuditStream(List<Map<String, Object>> events, String processingMode,
            String correlationId) {

        log.info("Processing generic audit stream: events={}, mode={}, correlationId={}",
            events.size(), processingMode, correlationId);

        for (Map<String, Object> event : events) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(event, correlationId);
                auditEventRepository.save(auditEvent);
            } catch (Exception e) {
                log.error("Error processing generic audit stream event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }
    }

    private void processRealTimeAuditStream(List<Map<String, Object>> events, String streamType,
            String correlationId) {

        for (Map<String, Object> event : events) {
            try {
                AuditEvent auditEvent = createAuditEventFromMap(event, correlationId);

                // Real-time critical event handling
                if (isCriticalEvent(auditEvent)) {
                    handleCriticalRealTimeEvent(auditEvent, correlationId);
                }

                // Real-time streaming analytics
                auditAnalyticsEngine.processRealTimeEvent(auditEvent, streamType, correlationId);

            } catch (Exception e) {
                log.error("Error processing real-time audit stream event: correlationId={}, error={}",
                    correlationId, e.getMessage());
            }
        }
    }

    private void detectStreamPatterns(List<Map<String, Object>> events, String patternDefinition,
            String correlationId) {

        try {
            for (Map<String, Object> event : events) {
                AuditEvent auditEvent = createAuditEventFromMap(event, correlationId);

                boolean patternDetected = suspiciousActivityDetectionEngine.detectPattern(auditEvent, patternDefinition);
                if (patternDetected) {
                    patternDetectionCounter.increment();
                    handleDetectedPattern(auditEvent, patternDefinition, correlationId);
                }
            }
        } catch (Exception e) {
            log.error("Error detecting stream patterns: correlationId={}, error={}",
                correlationId, e.getMessage());
        }
    }

    private void performStreamCorrelationAnalysis(List<Map<String, Object>> events, String streamKey,
            String correlationId) {

        try {
            Map<String, List<Map<String, Object>>> correlatedEvents = performEventCorrelation(events, streamKey);

            for (Map.Entry<String, List<Map<String, Object>>> entry : correlatedEvents.entrySet()) {
                if (entry.getValue().size() > 1) {
                    correlationCounter.increment();
                    handleCorrelatedEvents(entry.getKey(), entry.getValue(), correlationId);
                }
            }
        } catch (Exception e) {
            log.error("Error performing stream correlation analysis: correlationId={}, error={}",
                correlationId, e.getMessage());
        }
    }

    private void performStreamAggregation(List<Map<String, Object>> events, String aggregationWindow,
            String streamKey, String correlationId) {

        try {
            aggregationCounter.increment();

            // Buffer events for aggregation
            if (streamKey != null) {
                List<Map<String, Object>> buffer = streamBuffers.computeIfAbsent(streamKey, k -> new ArrayList<>());
                buffer.addAll(events);
                streamTimestamps.put(streamKey, System.currentTimeMillis());

                // Check if aggregation window is complete
                if (shouldTriggerAggregation(streamKey, aggregationWindow)) {
                    triggerAggregation(streamKey, correlationId);
                }
            }
        } catch (Exception e) {
            log.error("Error performing stream aggregation: correlationId={}, error={}",
                correlationId, e.getMessage());
        }
    }

    private void performStreamingAnalytics(List<Map<String, Object>> events, String streamType,
            Map<String, Object> streamMetadata, String correlationId) {

        try {
            streamingAnalyticsCounter.increment();

            // Perform streaming analytics
            auditAnalyticsEngine.performStreamingAnalytics(events, streamType, streamMetadata, correlationId);

        } catch (Exception e) {
            log.error("Error performing streaming analytics: correlationId={}, error={}",
                correlationId, e.getMessage());
        }
    }

    // Helper methods for specific processing logic

    private Map<String, List<Map<String, Object>>> groupEventsByTransaction(List<Map<String, Object>> events) {
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        for (Map<String, Object> event : events) {
            String transactionId = (String) event.get("transactionId");
            if (transactionId != null) {
                grouped.computeIfAbsent(transactionId, k -> new ArrayList<>()).add(event);
            }
        }
        return grouped;
    }

    private Map<String, List<Map<String, Object>>> groupEventsByUser(List<Map<String, Object>> events) {
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        for (Map<String, Object> event : events) {
            String userId = (String) event.get("userId");
            if (userId != null) {
                grouped.computeIfAbsent(userId, k -> new ArrayList<>()).add(event);
            }
        }
        return grouped;
    }

    private Map<String, List<Map<String, Object>>> performEventCorrelation(List<Map<String, Object>> events,
            String streamKey) {

        Map<String, List<Map<String, Object>>> correlatedEvents = new HashMap<>();

        for (Map<String, Object> event : events) {
            String userId = (String) event.get("userId");
            String sessionId = (String) event.get("sessionId");
            String transactionId = (String) event.get("transactionId");

            if (userId != null) {
                correlatedEvents.computeIfAbsent("user:" + userId, k -> new ArrayList<>()).add(event);
            }
            if (sessionId != null) {
                correlatedEvents.computeIfAbsent("session:" + sessionId, k -> new ArrayList<>()).add(event);
            }
            if (transactionId != null) {
                correlatedEvents.computeIfAbsent("transaction:" + transactionId, k -> new ArrayList<>()).add(event);
            }
        }

        return correlatedEvents;
    }

    private void analyzeTransactionFlow(String transactionId, List<Map<String, Object>> transactionEvents,
            String correlationId) {

        log.debug("Analyzing transaction flow: transactionId={}, events={}, correlationId={}",
            transactionId, transactionEvents.size(), correlationId);

        // Send transaction flow analysis
        kafkaTemplate.send("transaction-flow-analyzed", Map.of(
            "transactionId", transactionId,
            "eventCount", transactionEvents.size(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void analyzeUserBehavior(String userId, List<Map<String, Object>> userEvents,
            String patternDefinition, String correlationId) {

        log.debug("Analyzing user behavior: userId={}, events={}, pattern={}, correlationId={}",
            userId, userEvents.size(), patternDefinition, correlationId);

        // Send user behavior analysis
        kafkaTemplate.send("user-behavior-analyzed", Map.of(
            "userId", userId,
            "eventCount", userEvents.size(),
            "pattern", patternDefinition != null ? patternDefinition : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void checkForSuspiciousBehavior(String userId, List<Map<String, Object>> userEvents,
            String correlationId) {

        try {
            // Simple suspicious behavior check - multiple failed attempts
            long failedAttempts = userEvents.stream()
                .filter(event -> "FAILURE".equals(event.get("result")))
                .count();

            if (failedAttempts >= 3) {
                handleSuspiciousBehavior(userId, userEvents, correlationId);
            }
        } catch (Exception e) {
            log.error("Error checking for suspicious behavior: userId={}, correlationId={}, error={}",
                userId, correlationId, e.getMessage());
        }
    }

    private void handleSuspiciousBehavior(String userId, List<Map<String, Object>> userEvents,
            String correlationId) {

        log.warn("Suspicious behavior detected: userId={}, events={}, correlationId={}",
            userId, userEvents.size(), correlationId);

        kafkaTemplate.send("suspicious-behavior-detected", Map.of(
            "userId", userId,
            "eventCount", userEvents.size(),
            "severity", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private boolean isSecurityEvent(Map<String, Object> event) {
        String eventType = (String) event.get("eventType");
        return eventType != null && eventType.contains("SECURITY");
    }

    private boolean isHighRiskSecurityEvent(Map<String, Object> event) {
        String severity = (String) event.get("severity");
        return "HIGH".equals(severity) || "CRITICAL".equals(severity);
    }

    private void handleHighRiskSecurityEvent(AuditEvent auditEvent, String correlationId) {
        log.error("HIGH RISK security event detected: eventType={}, action={}, correlationId={}",
            auditEvent.getEventType(), auditEvent.getAction(), correlationId);

        kafkaTemplate.send("high-risk-security-events", Map.of(
            "eventId", auditEvent.getId(),
            "eventType", auditEvent.getEventType(),
            "action", auditEvent.getAction(),
            "severity", auditEvent.getSeverity().toString(),
            "requiresImmediateAction", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private boolean hasComplianceImplication(Map<String, Object> event) {
        String complianceTags = (String) event.get("complianceTags");
        return complianceTags != null && !complianceTags.isEmpty();
    }

    private void checkComplianceViolation(AuditEvent auditEvent, String correlationId) {
        if (auditEvent.getResult() == AuditEvent.AuditResult.UNAUTHORIZED ||
            auditEvent.getResult() == AuditEvent.AuditResult.FORBIDDEN) {

            handleComplianceViolation(auditEvent, correlationId);
        }
    }

    private void handleComplianceViolation(AuditEvent auditEvent, String correlationId) {
        log.warn("Compliance violation detected: eventType={}, result={}, correlationId={}",
            auditEvent.getEventType(), auditEvent.getResult(), correlationId);

        kafkaTemplate.send("compliance-violations-detected", Map.of(
            "eventId", auditEvent.getId(),
            "eventType", auditEvent.getEventType(),
            "result", auditEvent.getResult().toString(),
            "complianceTags", auditEvent.getComplianceTags() != null ? auditEvent.getComplianceTags() : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private Map<String, Object> calculatePerformanceMetrics(List<Map<String, Object>> events) {
        Map<String, Object> metrics = new HashMap<>();

        long totalDuration = events.stream()
            .filter(event -> event.get("durationMs") != null)
            .mapToLong(event -> Long.valueOf(event.get("durationMs").toString()))
            .sum();

        metrics.put("totalEvents", events.size());
        metrics.put("totalDurationMs", totalDuration);
        metrics.put("averageDurationMs", events.isEmpty() ? 0 : totalDuration / events.size());

        return metrics;
    }

    private void aggregatePerformanceMetrics(Map<String, Object> performanceMetrics,
            String aggregationWindow, String correlationId) {

        kafkaTemplate.send("performance-metrics-aggregated", Map.of(
            "metrics", performanceMetrics,
            "aggregationWindow", aggregationWindow,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleDetectedAnomaly(AuditEvent auditEvent, String correlationId) {
        log.warn("Anomaly detected: eventType={}, action={}, correlationId={}",
            auditEvent.getEventType(), auditEvent.getAction(), correlationId);

        kafkaTemplate.send("anomalies-detected", Map.of(
            "eventId", auditEvent.getId(),
            "eventType", auditEvent.getEventType(),
            "action", auditEvent.getAction(),
            "anomalyType", "PATTERN_DEVIATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleCorrelatedEvents(String correlationKey, List<Map<String, Object>> correlatedEvents,
            String correlationId) {

        log.info("Correlated events found: key={}, count={}, correlationId={}",
            correlationKey, correlatedEvents.size(), correlationId);

        kafkaTemplate.send("correlated-events-found", Map.of(
            "correlationKey", correlationKey,
            "eventCount", correlatedEvents.size(),
            "correlationType", "STREAM_CORRELATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private boolean isCriticalEvent(AuditEvent auditEvent) {
        return auditEvent.getSeverity() == AuditEvent.AuditSeverity.CRITICAL ||
               auditEvent.getResult() == AuditEvent.AuditResult.UNAUTHORIZED ||
               auditEvent.getResult() == AuditEvent.AuditResult.SYSTEM_ERROR;
    }

    private void handleCriticalRealTimeEvent(AuditEvent auditEvent, String correlationId) {
        log.error("CRITICAL real-time event: eventType={}, action={}, result={}, correlationId={}",
            auditEvent.getEventType(), auditEvent.getAction(), auditEvent.getResult(), correlationId);

        kafkaTemplate.send("critical-realtime-audit-events", Map.of(
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

    private void handleDetectedPattern(AuditEvent auditEvent, String patternDefinition, String correlationId) {
        log.info("Pattern detected: eventType={}, pattern={}, correlationId={}",
            auditEvent.getEventType(), patternDefinition, correlationId);

        kafkaTemplate.send("patterns-detected", Map.of(
            "eventId", auditEvent.getId(),
            "eventType", auditEvent.getEventType(),
            "pattern", patternDefinition,
            "patternType", "STREAM_PATTERN",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private boolean shouldTriggerAggregation(String streamKey, String aggregationWindow) {
        Long timestamp = streamTimestamps.get(streamKey);
        if (timestamp == null) return false;

        long windowMs = switch (aggregationWindow.toUpperCase()) {
            case "1_MINUTE" -> 60 * 1000;
            case "5_MINUTES" -> 5 * 60 * 1000;
            case "15_MINUTES" -> 15 * 60 * 1000;
            case "1_HOUR" -> 60 * 60 * 1000;
            default -> 5 * 60 * 1000; // Default 5 minutes
        };

        return System.currentTimeMillis() - timestamp > windowMs;
    }

    private void triggerAggregation(String streamKey, String correlationId) {
        List<Map<String, Object>> buffer = streamBuffers.remove(streamKey);
        streamTimestamps.remove(streamKey);

        if (buffer != null && !buffer.isEmpty()) {
            kafkaTemplate.send("stream-aggregation-triggered", Map.of(
                "streamKey", streamKey,
                "eventCount", buffer.size(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
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