package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.service.GatewayMonitoringService;
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
 * Gateway Failover Events Consumer
 * 
 * CRITICAL CONSUMER - Processes payment gateway failover events for reliability monitoring
 * 
 * EVENT SOURCE:
 * - payment-service PaymentGatewayEventsConsumer: Line 630 publishes gateway failover events
 * 
 * BUSINESS CRITICALITY:
 * - Payment gateway availability monitoring
 * - Failover detection and tracking
 * - Payment processing continuity
 * - Gateway health assessment
 * - SLA compliance monitoring
 * 
 * FAILOVER EVENT TYPES:
 * - GATEWAY_FAILED: Primary gateway failure detected
 * - FAILOVER_INITIATED: Failover to backup gateway started
 * - FAILOVER_COMPLETED: Successfully failed over
 * - FAILOVER_FAILED: Failover attempt failed
 * - GATEWAY_RECOVERED: Failed gateway back online
 * - MANUAL_FAILOVER: Operator-initiated failover
 * - AUTO_FAILBACK: Automatic return to primary
 * 
 * PROCESSING ACTIONS:
 * - Track gateway failover frequency
 * - Measure failover latency
 * - Monitor gateway availability patterns
 * - Alert on excessive failovers
 * - Generate gateway reliability reports
 * - Assess failover success rate
 * 
 * BUSINESS VALUE:
 * - Reliability: Payment processing continuity
 * - Visibility: Gateway health tracking
 * - SLA compliance: Availability monitoring
 * - Incident response: Rapid failover detection
 * - Capacity planning: Gateway usage patterns
 * 
 * FAILURE IMPACT:
 * - Loss of gateway failover visibility
 * - Delayed detection of gateway issues
 * - Reduced payment processing reliability
 * - Missing SLA compliance data
 * - Incomplete incident response
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Real-time failover tracking
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GatewayFailoverEventsConsumer {
    
    private final GatewayMonitoringService gatewayMonitoringService;
    private final AlertingService alertingService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "gateway-failover-events";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter failoversInitiatedCounter;
    private Counter failoversCompletedCounter;
    private Counter failoversFailedCounter;
    private Timer processingTimer;
    private Timer failoverDurationTimer;
    
    public GatewayFailoverEventsConsumer(
            GatewayMonitoringService gatewayMonitoringService,
            AlertingService alertingService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.gatewayMonitoringService = gatewayMonitoringService;
        this.alertingService = alertingService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("gateway_failover_events_processed_total")
                .description("Total number of gateway failover events processed")
                .tag("consumer", "gateway-failover-events-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("gateway_failover_events_failed_total")
                .description("Total number of gateway failover events that failed processing")
                .tag("consumer", "gateway-failover-events-consumer")
                .register(meterRegistry);
        
        this.failoversInitiatedCounter = Counter.builder("gateway_failovers_initiated_total")
                .description("Total number of gateway failovers initiated")
                .register(meterRegistry);
        
        this.failoversCompletedCounter = Counter.builder("gateway_failovers_completed_total")
                .description("Total number of gateway failovers completed successfully")
                .register(meterRegistry);
        
        this.failoversFailedCounter = Counter.builder("gateway_failovers_failed_total")
                .description("Total number of gateway failovers that failed")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("gateway_failover_event_processing_duration")
                .description("Time taken to process gateway failover events")
                .register(meterRegistry);
        
        this.failoverDurationTimer = Timer.builder("gateway_failover_duration")
                .description("Time taken for gateway failover completion")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.gateway-failover-events:gateway-failover-events}",
        groupId = "${kafka.consumer.group-id:monitoring-service-gateway-failover-group}",
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
    public void handleGatewayFailoverEvent(
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
            log.info("Received gateway failover event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String paymentId = extractString(eventData, "paymentId");
            String gatewayId = extractString(eventData, "gatewayId");
            String failoverReason = extractString(eventData, "failoverReason");
            String eventType = extractString(eventData, "eventType");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = paymentId != null ? paymentId : UUID.randomUUID().toString();
            }
            if (correlationId == null) {
                correlationId = gatewayId;
            }
            
            if (paymentId == null || gatewayId == null) {
                log.error("Invalid gateway failover event - missing required fields: paymentId={}, gatewayId={}",
                        paymentId, gatewayId);
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
                log.warn("Duplicate gateway failover event detected - eventId: {}, paymentId: {}, gatewayId: {}, correlationId: {}",
                        eventId, paymentId, gatewayId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processGatewayFailoverEvent(paymentId, gatewayId, eventType, failoverReason, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed gateway failover event - eventId: {}, paymentId: {}, " +
                    "gatewayId: {}, eventType: {}, correlationId: {}",
                    eventId, paymentId, gatewayId, eventType, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process gateway failover event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process gateway failover event", e);
        }
    }
    
    @CircuitBreaker(name = "monitoring", fallbackMethod = "processGatewayFailoverEventFallback")
    @Retry(name = "monitoring")
    private void processGatewayFailoverEvent(String paymentId, String gatewayId, String eventType,
                                            String failoverReason, Map<String, Object> eventData,
                                            String correlationId) {
        
        log.info("Processing gateway failover event - paymentId: {}, gatewayId: {}, eventType: {}, " +
                "reason: {}, correlationId: {}",
                paymentId, gatewayId, eventType, failoverReason, correlationId);
        
        gatewayMonitoringService.recordFailoverEvent(
            paymentId,
            gatewayId,
            eventType,
            failoverReason,
            eventData,
            correlationId
        );
        
        String actualEventType = eventType != null ? eventType : "GATEWAY_FAILED";
        
        switch (actualEventType) {
            case "GATEWAY_FAILED":
                handleGatewayFailed(paymentId, gatewayId, failoverReason, eventData, correlationId);
                break;
            case "FAILOVER_INITIATED":
                failoversInitiatedCounter.increment();
                handleFailoverInitiated(paymentId, gatewayId, failoverReason, eventData, correlationId);
                break;
            case "FAILOVER_COMPLETED":
                failoversCompletedCounter.increment();
                handleFailoverCompleted(paymentId, gatewayId, eventData, correlationId);
                break;
            case "FAILOVER_FAILED":
                failoversFailedCounter.increment();
                handleFailoverFailed(paymentId, gatewayId, failoverReason, eventData, correlationId);
                break;
            case "GATEWAY_RECOVERED":
                handleGatewayRecovered(gatewayId, eventData, correlationId);
                break;
            case "MANUAL_FAILOVER":
                handleManualFailover(paymentId, gatewayId, failoverReason, eventData, correlationId);
                break;
            case "AUTO_FAILBACK":
                handleAutoFailback(gatewayId, eventData, correlationId);
                break;
            default:
                handleGatewayFailed(paymentId, gatewayId, failoverReason, eventData, correlationId);
        }
        
        gatewayMonitoringService.updateGatewayAvailabilityMetrics(gatewayId, actualEventType, eventData, correlationId);
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("paymentId", paymentId);
        auditMetadata.put("gatewayId", gatewayId);
        auditMetadata.put("eventType", actualEventType);
        auditMetadata.put("failoverReason", failoverReason);
        auditMetadata.put("timestamp", eventData.get("timestamp"));
        
        auditService.logGatewayFailoverEventProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : paymentId,
            paymentId,
            gatewayId,
            actualEventType,
            correlationId,
            auditMetadata
        );
        
        log.info("Gateway failover event processed successfully - paymentId: {}, gatewayId: {}, eventType: {}, correlationId: {}",
                paymentId, gatewayId, actualEventType, correlationId);
    }
    
    private void handleGatewayFailed(String paymentId, String gatewayId, String reason,
                                     Map<String, Object> eventData, String correlationId) {
        log.warn("Gateway failure detected - gatewayId: {}, paymentId: {}, reason: {}, correlationId: {}",
                gatewayId, paymentId, reason, correlationId);
        
        gatewayMonitoringService.updateGatewayStatus(gatewayId, "FAILED", reason, correlationId);
        alertingService.sendGatewayFailureAlert(gatewayId, paymentId, reason, correlationId);
        gatewayMonitoringService.assessFailoverNeed(gatewayId, eventData, correlationId);
    }
    
    private void handleFailoverInitiated(String paymentId, String gatewayId, String reason,
                                        Map<String, Object> eventData, String correlationId) {
        log.info("Gateway failover initiated - fromGateway: {}, paymentId: {}, reason: {}, correlationId: {}",
                gatewayId, paymentId, reason, correlationId);
        
        gatewayMonitoringService.trackFailoverStart(paymentId, gatewayId, reason, correlationId);
        alertingService.sendFailoverInitiatedAlert(gatewayId, paymentId, reason, correlationId);
    }
    
    private void handleFailoverCompleted(String paymentId, String gatewayId,
                                        Map<String, Object> eventData, String correlationId) {
        log.info("Gateway failover completed successfully - toGateway: {}, paymentId: {}, correlationId: {}",
                gatewayId, paymentId, correlationId);
        
        Long failoverDurationMs = extractLong(eventData, "failoverDurationMs");
        if (failoverDurationMs != null) {
            failoverDurationTimer.record(Duration.ofMillis(failoverDurationMs));
        }
        
        gatewayMonitoringService.trackFailoverCompletion(paymentId, gatewayId, failoverDurationMs, correlationId);
        alertingService.sendFailoverCompletedAlert(gatewayId, paymentId, failoverDurationMs, correlationId);
    }
    
    private void handleFailoverFailed(String paymentId, String gatewayId, String reason,
                                     Map<String, Object> eventData, String correlationId) {
        log.error("CRITICAL: Gateway failover failed - gatewayId: {}, paymentId: {}, reason: {}, correlationId: {}",
                gatewayId, paymentId, reason, correlationId);
        
        gatewayMonitoringService.trackFailoverFailure(paymentId, gatewayId, reason, correlationId);
        alertingService.sendCriticalFailoverFailureAlert(gatewayId, paymentId, reason, correlationId);
        
        auditService.logCriticalAlert(
            "GATEWAY_FAILOVER_FAILED",
            gatewayId,
            correlationId,
            eventData
        );
    }
    
    private void handleGatewayRecovered(String gatewayId, Map<String, Object> eventData, String correlationId) {
        log.info("Gateway recovered - gatewayId: {}, correlationId: {}", gatewayId, correlationId);
        
        gatewayMonitoringService.updateGatewayStatus(gatewayId, "HEALTHY", "Recovered", correlationId);
        gatewayMonitoringService.trackRecoveryTime(gatewayId, eventData, correlationId);
        alertingService.sendGatewayRecoveredAlert(gatewayId, correlationId);
    }
    
    private void handleManualFailover(String paymentId, String gatewayId, String reason,
                                     Map<String, Object> eventData, String correlationId) {
        log.info("Manual failover triggered - gatewayId: {}, paymentId: {}, reason: {}, correlationId: {}",
                gatewayId, paymentId, reason, correlationId);
        
        gatewayMonitoringService.trackManualFailover(paymentId, gatewayId, reason, eventData, correlationId);
        
        auditService.logManualOperation(
            "MANUAL_GATEWAY_FAILOVER",
            gatewayId,
            extractString(eventData, "operator"),
            correlationId,
            eventData
        );
    }
    
    private void handleAutoFailback(String gatewayId, Map<String, Object> eventData, String correlationId) {
        log.info("Automatic failback to primary gateway - gatewayId: {}, correlationId: {}", gatewayId, correlationId);
        
        gatewayMonitoringService.trackAutoFailback(gatewayId, eventData, correlationId);
        alertingService.sendAutoFailbackAlert(gatewayId, correlationId);
    }
    
    private void processGatewayFailoverEventFallback(String paymentId, String gatewayId, String eventType,
                                                    String failoverReason, Map<String, Object> eventData,
                                                    String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process gateway failover event - paymentId: {}, " +
                "gatewayId: {}, eventType: {}, correlationId: {}, error: {}",
                paymentId, gatewayId, eventType, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "GATEWAY_FAILOVER_EVENT_PROCESSING",
            paymentId,
            correlationId,
            Map.of(
                "paymentId", paymentId,
                "gatewayId", gatewayId,
                "eventType", eventType != null ? eventType : "UNKNOWN",
                "failoverReason", failoverReason != null ? failoverReason : "UNKNOWN",
                "error", e.getMessage()
            )
        );
        
        alertingService.sendProcessingFailureAlert(
            "GATEWAY_FAILOVER_PROCESSING_FAILED",
            gatewayId,
            paymentId,
            e.getMessage(),
            correlationId
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
            String paymentId = extractString(eventData, "paymentId");
            String gatewayId = extractString(eventData, "gatewayId");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("Gateway failover event moved to DLT - eventId: {}, paymentId: {}, gatewayId: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, paymentId, gatewayId, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("paymentId", paymentId);
            dltMetadata.put("gatewayId", gatewayId);
            dltMetadata.put("eventType", extractString(eventData, "eventType"));
            dltMetadata.put("failoverReason", extractString(eventData, "failoverReason"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "GATEWAY_FAILOVER_EVENT_DLT",
                exceptionMessage,
                correlationId,
                dltMetadata
            );
            
            alertingService.sendDLTAlert(
                "GATEWAY_FAILOVER_EVENT",
                gatewayId,
                exceptionMessage,
                correlationId
            );
        } catch (Exception e) {
            log.error("Failed to process DLT event: {}", e.getMessage(), e);
        }
    }
    
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
    
    private Long extractLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}