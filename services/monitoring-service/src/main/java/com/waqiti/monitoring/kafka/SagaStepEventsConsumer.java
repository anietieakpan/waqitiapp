package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.service.SagaMonitoringService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.AuditService;
import com.waqiti.monitoring.service.IdempotencyService;
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
 * Saga Step Events Consumer
 * 
 * CRITICAL CONSUMER - Monitors distributed transaction saga execution
 * 
 * EVENT SOURCE:
 * - payment-service PaymentSagaOrchestrator: Line 799 publishes saga step events
 * - saga-orchestration-service: Line 320 publishes saga step events
 * 
 * BUSINESS CRITICALITY:
 * - Monitors distributed transaction health
 * - Detects saga execution failures
 * - Tracks compensation workflow execution
 * - Provides saga performance metrics
 * - Enables real-time saga debugging
 * 
 * SAGA STEP STATUSES:
 * - STARTED: Step execution began
 * - COMPLETED: Step completed successfully
 * - FAILED: Step execution failed
 * - COMPENSATING: Compensation in progress
 * - COMPENSATED: Compensation completed
 * - COMPENSATION_FAILED: Compensation failed (critical)
 * 
 * PROCESSING ACTIONS:
 * - Track saga step execution metrics
 * - Monitor saga completion rates
 * - Alert on saga failures and timeouts
 * - Track compensation workflow success
 * - Measure saga execution duration
 * - Identify saga bottlenecks
 * 
 * BUSINESS VALUE:
 * - Reliability: Real-time saga health monitoring
 * - Performance: Saga execution metrics
 * - Operations: Proactive failure detection
 * - Debugging: Complete saga execution trace
 * - SLA monitoring: Transaction completion tracking
 * 
 * FAILURE IMPACT:
 * - Loss of saga execution visibility
 * - Delayed detection of distributed transaction failures
 * - Missing compensation workflow monitoring
 * - Inability to troubleshoot saga issues
 * - Reduced system reliability insights
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Real-time alerting
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SagaStepEventsConsumer {
    
    private final SagaMonitoringService sagaMonitoringService;
    private final AlertingService alertingService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "saga-step-events";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final long SAGA_TIMEOUT_THRESHOLD_MS = 30000; // 30 seconds
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter sagaStepsCompletedCounter;
    private Counter sagaStepsFailedCounter;
    private Counter compensationsCounter;
    private Counter compensationFailuresCounter;
    private Timer processingTimer;
    
    public SagaStepEventsConsumer(
            SagaMonitoringService sagaMonitoringService,
            AlertingService alertingService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.sagaMonitoringService = sagaMonitoringService;
        this.alertingService = alertingService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("saga_step_events_processed_total")
                .description("Total number of saga step events processed")
                .tag("consumer", "saga-step-events-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("saga_step_events_failed_total")
                .description("Total number of saga step events that failed processing")
                .tag("consumer", "saga-step-events-consumer")
                .register(meterRegistry);
        
        this.sagaStepsCompletedCounter = Counter.builder("saga_steps_completed_total")
                .description("Total number of saga steps completed successfully")
                .register(meterRegistry);
        
        this.sagaStepsFailedCounter = Counter.builder("saga_steps_failed_total")
                .description("Total number of saga steps that failed")
                .register(meterRegistry);
        
        this.compensationsCounter = Counter.builder("saga_compensations_total")
                .description("Total number of saga compensations executed")
                .register(meterRegistry);
        
        this.compensationFailuresCounter = Counter.builder("saga_compensation_failures_total")
                .description("Total number of saga compensations that failed")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("saga_step_event_processing_duration")
                .description("Time taken to process saga step events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.saga-step-events:saga-step-events}",
        groupId = "${kafka.consumer.group-id:monitoring-service-saga-step-group}",
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
    public void handleSagaStepEvent(
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
            log.info("Received saga step event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            String sagaId = extractString(eventData, "sagaId");
            String stepName = extractString(eventData, "stepName");
            String status = extractString(eventData, "status");
            correlationId = extractString(eventData, "correlationId");
            
            eventId = sagaId + "-" + stepName + "-" + System.currentTimeMillis();
            
            if (correlationId == null) {
                correlationId = sagaId;
            }
            
            if (sagaId == null || stepName == null || status == null) {
                log.error("Invalid saga step event - missing required fields: sagaId={}, stepName={}, status={}",
                        sagaId, stepName, status);
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
                log.warn("Duplicate saga step event detected - eventId: {}, sagaId: {}, step: {}, correlationId: {}",
                        eventId, sagaId, stepName, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processSagaStepEvent(sagaId, stepName, status, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed saga step event - sagaId: {}, step: {}, status: {}, correlationId: {}",
                    sagaId, stepName, status, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process saga step event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process saga step event", e);
        }
    }
    
    @CircuitBreaker(name = "monitoring", fallbackMethod = "processSagaStepEventFallback")
    @Retry(name = "monitoring")
    private void processSagaStepEvent(String sagaId, String stepName, String status,
                                     Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing saga step event - sagaId: {}, step: {}, status: {}, correlationId: {}",
                sagaId, stepName, status, correlationId);
        
        sagaMonitoringService.recordSagaStepEvent(sagaId, stepName, status, eventData, correlationId);
        
        switch (status) {
            case "COMPLETED":
                sagaStepsCompletedCounter.increment();
                handleStepCompleted(sagaId, stepName, eventData, correlationId);
                break;
            case "FAILED":
                sagaStepsFailedCounter.increment();
                handleStepFailed(sagaId, stepName, eventData, correlationId);
                break;
            case "COMPENSATING":
                compensationsCounter.increment();
                handleCompensationStarted(sagaId, stepName, eventData, correlationId);
                break;
            case "COMPENSATION_FAILED":
                compensationFailuresCounter.increment();
                handleCompensationFailed(sagaId, stepName, eventData, correlationId);
                break;
            case "COMPENSATED":
                handleCompensationCompleted(sagaId, stepName, eventData, correlationId);
                break;
            default:
                log.debug("Saga step status: {} - sagaId: {}, step: {}", status, sagaId, stepName);
        }
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("sagaId", sagaId);
        auditMetadata.put("stepName", stepName);
        auditMetadata.put("status", status);
        auditMetadata.put("timestamp", eventData.get("timestamp"));
        auditMetadata.put("result", eventData.get("result"));
        
        auditService.logSagaStepEventProcessed(
            sagaId + "-" + stepName,
            sagaId,
            stepName,
            status,
            correlationId,
            auditMetadata
        );
        
        log.info("Saga step event processed successfully - sagaId: {}, step: {}, status: {}, correlationId: {}",
                sagaId, stepName, status, correlationId);
    }
    
    private void handleStepCompleted(String sagaId, String stepName, Map<String, Object> eventData, String correlationId) {
        log.info("Saga step completed - sagaId: {}, step: {}, correlationId: {}", sagaId, stepName, correlationId);
        sagaMonitoringService.trackStepSuccess(sagaId, stepName, eventData, correlationId);
    }
    
    private void handleStepFailed(String sagaId, String stepName, Map<String, Object> eventData, String correlationId) {
        log.warn("Saga step failed - sagaId: {}, step: {}, correlationId: {}", sagaId, stepName, correlationId);
        
        sagaMonitoringService.trackStepFailure(sagaId, stepName, eventData, correlationId);
        
        alertingService.sendSagaStepFailureAlert(
            sagaId,
            stepName,
            extractString(eventData, "errorMessage"),
            correlationId
        );
    }
    
    private void handleCompensationStarted(String sagaId, String stepName, Map<String, Object> eventData, String correlationId) {
        log.info("Saga compensation started - sagaId: {}, step: {}, correlationId: {}", sagaId, stepName, correlationId);
        sagaMonitoringService.trackCompensationStarted(sagaId, stepName, eventData, correlationId);
    }
    
    private void handleCompensationCompleted(String sagaId, String stepName, Map<String, Object> eventData, String correlationId) {
        log.info("Saga compensation completed - sagaId: {}, step: {}, correlationId: {}", sagaId, stepName, correlationId);
        sagaMonitoringService.trackCompensationSuccess(sagaId, stepName, eventData, correlationId);
    }
    
    private void handleCompensationFailed(String sagaId, String stepName, Map<String, Object> eventData, String correlationId) {
        log.error("CRITICAL: Saga compensation failed - sagaId: {}, step: {}, correlationId: {}",
                sagaId, stepName, correlationId);
        
        sagaMonitoringService.trackCompensationFailure(sagaId, stepName, eventData, correlationId);
        
        alertingService.sendCriticalSagaCompensationFailureAlert(
            sagaId,
            stepName,
            extractString(eventData, "errorMessage"),
            correlationId
        );
        
        auditService.logCriticalSagaFailure(
            sagaId,
            stepName,
            "COMPENSATION_FAILED",
            correlationId,
            eventData
        );
    }
    
    private void processSagaStepEventFallback(String sagaId, String stepName, String status,
                                             Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process saga step event - sagaId: {}, " +
                "step: {}, status: {}, correlationId: {}, error: {}",
                sagaId, stepName, status, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "SAGA_STEP_EVENT_PROCESSING",
            sagaId,
            correlationId,
            Map.of(
                "sagaId", sagaId,
                "stepName", stepName,
                "status", status,
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
            String sagaId = extractString(eventData, "sagaId");
            String stepName = extractString(eventData, "stepName");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("Saga step event moved to DLT - sagaId: {}, step: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    sagaId, stepName, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("sagaId", sagaId);
            dltMetadata.put("stepName", stepName);
            dltMetadata.put("status", extractString(eventData, "status"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                sagaId + "-" + stepName,
                TOPIC_NAME,
                "SAGA_STEP_EVENT_DLT",
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