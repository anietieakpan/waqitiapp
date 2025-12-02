package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.PaymentMonitoringService;
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
 * Payment Alerts Events Consumer
 * 
 * CRITICAL CONSUMER - Processes payment system alerts from payment monitoring
 * 
 * EVENT SOURCE:
 * - payment-commons PaymentMonitoringService: Line 578 publishes payment alerts
 * 
 * BUSINESS CRITICALITY:
 * - Real-time payment system health monitoring
 * - Payment provider failure detection
 * - SLA breach prevention
 * - Payment processing incident response
 * - System availability tracking
 * 
 * ALERT TYPES:
 * - HIGH_FAILURE_RATE: Payment failure rate exceeded threshold
 * - PROVIDER_DEGRADED: Payment provider experiencing issues
 * - PROVIDER_DOWN: Payment provider unavailable
 * - LATENCY_SPIKE: Payment processing latency increased
 * - TRANSACTION_VOLUME_SPIKE: Unusual transaction volume
 * - FRAUD_PATTERN_DETECTED: Suspicious payment patterns
 * - SETTLEMENT_DELAY: Settlement processing delayed
 * 
 * PROCESSING ACTIONS:
 * - Route alerts to appropriate teams
 * - Trigger incident response workflows
 * - Update payment provider health status
 * - Track alert resolution time
 * - Generate alert trend reports
 * - Escalate critical alerts to on-call
 * 
 * BUSINESS VALUE:
 * - Availability: Rapid incident detection and response
 * - Revenue protection: Minimize payment processing downtime
 * - Customer experience: Proactive issue resolution
 * - SLA compliance: Meet payment processing SLAs
 * - Operations: Alert-driven monitoring
 * 
 * FAILURE IMPACT:
 * - Missed critical payment system alerts
 * - Delayed incident response
 * - Extended payment system outages
 * - SLA violations
 * - Revenue loss from undetected issues
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Real-time alert routing
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentAlertsEventsConsumer {
    
    private final AlertingService alertingService;
    private final PaymentMonitoringService paymentMonitoringService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "payment-alerts";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter criticalAlertsCounter;
    private Counter highFailureRateAlertsCounter;
    private Counter providerDownAlertsCounter;
    private Timer processingTimer;
    
    public PaymentAlertsEventsConsumer(
            AlertingService alertingService,
            PaymentMonitoringService paymentMonitoringService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.alertingService = alertingService;
        this.paymentMonitoringService = paymentMonitoringService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("payment_alerts_processed_total")
                .description("Total number of payment alert events processed")
                .tag("consumer", "payment-alerts-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("payment_alerts_failed_total")
                .description("Total number of payment alert events that failed processing")
                .tag("consumer", "payment-alerts-consumer")
                .register(meterRegistry);
        
        this.criticalAlertsCounter = Counter.builder("payment_critical_alerts_total")
                .description("Total number of critical payment alerts")
                .register(meterRegistry);
        
        this.highFailureRateAlertsCounter = Counter.builder("payment_high_failure_rate_alerts_total")
                .description("Total number of high failure rate alerts")
                .register(meterRegistry);
        
        this.providerDownAlertsCounter = Counter.builder("payment_provider_down_alerts_total")
                .description("Total number of payment provider down alerts")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("payment_alert_processing_duration")
                .description("Time taken to process payment alert events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.payment-alerts:payment-alerts}",
        groupId = "${kafka.consumer.group-id:monitoring-service-payment-alerts-group}",
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
    public void handlePaymentAlertEvent(
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
            log.info("Received payment alert event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String alertType = extractString(eventData, "alertType");
            String provider = extractString(eventData, "provider");
            String severity = extractString(eventData, "severity");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = UUID.randomUUID().toString();
            }
            if (correlationId == null) {
                correlationId = provider != null ? provider : UUID.randomUUID().toString();
            }
            
            if (alertType == null || provider == null) {
                log.error("Invalid payment alert event - missing required fields: alertType={}, provider={}",
                        alertType, provider);
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
                log.warn("Duplicate payment alert event detected - eventId: {}, alertType: {}, provider: {}, correlationId: {}",
                        eventId, alertType, provider, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processPaymentAlertEvent(alertType, provider, severity, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed payment alert event - eventId: {}, alertType: {}, " +
                    "provider: {}, severity: {}, correlationId: {}",
                    eventId, alertType, provider, severity, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process payment alert event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process payment alert event", e);
        }
    }
    
    @CircuitBreaker(name = "monitoring", fallbackMethod = "processPaymentAlertEventFallback")
    @Retry(name = "monitoring")
    private void processPaymentAlertEvent(String alertType, String provider, String severity,
                                         Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing payment alert event - alertType: {}, provider: {}, severity: {}, correlationId: {}",
                alertType, provider, severity, correlationId);
        
        paymentMonitoringService.recordAlert(alertType, provider, severity, eventData, correlationId);
        
        switch (alertType) {
            case "HIGH_FAILURE_RATE":
                highFailureRateAlertsCounter.increment();
                handleHighFailureRate(provider, eventData, correlationId);
                break;
            case "PROVIDER_DOWN":
                providerDownAlertsCounter.increment();
                criticalAlertsCounter.increment();
                handleProviderDown(provider, eventData, correlationId);
                break;
            case "PROVIDER_DEGRADED":
                handleProviderDegraded(provider, eventData, correlationId);
                break;
            case "LATENCY_SPIKE":
                handleLatencySpike(provider, eventData, correlationId);
                break;
            case "TRANSACTION_VOLUME_SPIKE":
                handleVolumeSpike(provider, eventData, correlationId);
                break;
            case "FRAUD_PATTERN_DETECTED":
                criticalAlertsCounter.increment();
                handleFraudPattern(provider, eventData, correlationId);
                break;
            case "SETTLEMENT_DELAY":
                handleSettlementDelay(provider, eventData, correlationId);
                break;
            default:
                log.debug("Payment alert type: {} - provider: {}", alertType, provider);
        }
        
        alertingService.routePaymentAlert(alertType, provider, severity, eventData, correlationId);
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("alertType", alertType);
        auditMetadata.put("provider", provider);
        auditMetadata.put("severity", severity);
        auditMetadata.put("timestamp", eventData.get("timestamp"));
        auditMetadata.putAll(eventData);
        
        auditService.logPaymentAlertProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : alertType,
            alertType,
            provider,
            severity,
            correlationId,
            auditMetadata
        );
        
        log.info("Payment alert event processed successfully - alertType: {}, provider: {}, correlationId: {}",
                alertType, provider, correlationId);
    }
    
    private void handleHighFailureRate(String provider, Map<String, Object> eventData, String correlationId) {
        log.warn("HIGH FAILURE RATE ALERT - provider: {}, data: {}, correlationId: {}",
                provider, eventData, correlationId);
        
        paymentMonitoringService.updateProviderHealthStatus(provider, "DEGRADED", eventData, correlationId);
        alertingService.sendHighFailureRateAlert(provider, eventData, correlationId);
        paymentMonitoringService.triggerFailureRateInvestigation(provider, eventData, correlationId);
    }
    
    private void handleProviderDown(String provider, Map<String, Object> eventData, String correlationId) {
        log.error("CRITICAL: PAYMENT PROVIDER DOWN - provider: {}, correlationId: {}", provider, correlationId);
        
        paymentMonitoringService.updateProviderHealthStatus(provider, "DOWN", eventData, correlationId);
        alertingService.sendCriticalProviderDownAlert(provider, eventData, correlationId);
        paymentMonitoringService.triggerProviderFailover(provider, correlationId);
        
        auditService.logCriticalAlert(
            "PROVIDER_DOWN",
            provider,
            correlationId,
            eventData
        );
    }
    
    private void handleProviderDegraded(String provider, Map<String, Object> eventData, String correlationId) {
        log.warn("Provider degraded - provider: {}, correlationId: {}", provider, correlationId);
        
        paymentMonitoringService.updateProviderHealthStatus(provider, "DEGRADED", eventData, correlationId);
        alertingService.sendProviderDegradedAlert(provider, eventData, correlationId);
    }
    
    private void handleLatencySpike(String provider, Map<String, Object> eventData, String correlationId) {
        log.warn("Latency spike detected - provider: {}, correlationId: {}", provider, correlationId);
        
        paymentMonitoringService.recordLatencySpike(provider, eventData, correlationId);
        alertingService.sendLatencySpikeAlert(provider, eventData, correlationId);
    }
    
    private void handleVolumeSpike(String provider, Map<String, Object> eventData, String correlationId) {
        log.info("Transaction volume spike - provider: {}, correlationId: {}", provider, correlationId);
        
        paymentMonitoringService.recordVolumeSpike(provider, eventData, correlationId);
        alertingService.sendVolumeAlert(provider, eventData, correlationId);
    }
    
    private void handleFraudPattern(String provider, Map<String, Object> eventData, String correlationId) {
        log.error("CRITICAL: Fraud pattern detected - provider: {}, correlationId: {}", provider, correlationId);
        
        paymentMonitoringService.recordFraudPattern(provider, eventData, correlationId);
        alertingService.sendCriticalFraudPatternAlert(provider, eventData, correlationId);
        
        auditService.logCriticalAlert(
            "FRAUD_PATTERN_DETECTED",
            provider,
            correlationId,
            eventData
        );
    }
    
    private void handleSettlementDelay(String provider, Map<String, Object> eventData, String correlationId) {
        log.warn("Settlement delay - provider: {}, correlationId: {}", provider, correlationId);
        
        paymentMonitoringService.recordSettlementDelay(provider, eventData, correlationId);
        alertingService.sendSettlementDelayAlert(provider, eventData, correlationId);
    }
    
    private void processPaymentAlertEventFallback(String alertType, String provider, String severity,
                                                 Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process payment alert - alertType: {}, " +
                "provider: {}, severity: {}, correlationId: {}, error: {}",
                alertType, provider, severity, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "PAYMENT_ALERT_PROCESSING",
            alertType,
            correlationId,
            Map.of(
                "alertType", alertType,
                "provider", provider,
                "severity", severity,
                "error", e.getMessage()
            )
        );
        
        alertingService.sendProcessingFailureAlert(
            "PAYMENT_ALERT_PROCESSING_FAILED",
            alertType,
            provider,
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
            String alertType = extractString(eventData, "alertType");
            String provider = extractString(eventData, "provider");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("Payment alert event moved to DLT - eventId: {}, alertType: {}, provider: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, alertType, provider, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("alertType", alertType);
            dltMetadata.put("provider", provider);
            dltMetadata.put("severity", extractString(eventData, "severity"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "PAYMENT_ALERT_DLT",
                exceptionMessage,
                correlationId,
                dltMetadata
            );
            
            alertingService.sendDLTAlert(
                "PAYMENT_ALERT",
                alertType,
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
}