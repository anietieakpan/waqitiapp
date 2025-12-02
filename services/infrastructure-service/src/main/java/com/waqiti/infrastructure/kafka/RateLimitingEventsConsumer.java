package com.waqiti.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.infrastructure.model.RateLimitingEvent;
import com.waqiti.infrastructure.service.InfrastructureAlertService;
import com.waqiti.infrastructure.service.RateLimitingMetricsService;
import com.waqiti.infrastructure.service.RecoveryService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumer for processing rate limiting events and throttling violations.
 * Handles P2 priority events related to system rate limiting and API throttling.
 */
@Component
public class RateLimitingEventsConsumer {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingEventsConsumer.class);
    private static final String CIRCUIT_BREAKER_NAME = "rateLimitingEventsConsumer";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    @Autowired
    private InfrastructureAlertService infrastructureAlertService;

    @Autowired
    private RateLimitingMetricsService rateLimitingMetricsService;

    @Autowired
    private RecoveryService recoveryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    // Idempotency tracking
    private final Map<String, LocalDateTime> processedEvents = new ConcurrentHashMap<>();

    // Metrics
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Timer processingTimer;
    private final AtomicLong throttledRequestsGauge = new AtomicLong(0);
    private final AtomicLong activeRateLimitsGauge = new AtomicLong(0);

    public RateLimitingEventsConsumer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.eventsProcessedCounter = Counter.builder("rate_limiting_events_processed_total")
                .description("Total number of rate limiting events processed")
                .register(meterRegistry);
        this.eventsFailedCounter = Counter.builder("rate_limiting_events_failed_total")
                .description("Total number of rate limiting events that failed processing")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("rate_limiting_events_processing_duration")
                .description("Time taken to process rate limiting events")
                .register(meterRegistry);

        Gauge.builder("rate_limiting_throttled_requests_active")
                .description("Number of currently throttled requests")
                .register(meterRegistry, throttledRequestsGauge, AtomicLong::get);

        Gauge.builder("rate_limiting_active_limits")
                .description("Number of active rate limits")
                .register(meterRegistry, activeRateLimitsGauge, AtomicLong::get);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
            include = {Exception.class}
    )
    @KafkaListener(topics = "rate-limiting-events", groupId = "infrastructure-service-rate-limiting-group")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "fallbackProcessRateLimitingEvent")
    public void processRateLimitingEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_KEY) String eventKey,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            logger.info("Processing rate limiting event: key={}, topic={}, partition={}, offset={}",
                       eventKey, topic, partition, offset);

            // Check idempotency
            if (isDuplicate(eventKey)) {
                logger.warn("Duplicate rate limiting event detected: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            // Parse event
            RateLimitingEvent event = objectMapper.readValue(eventPayload, RateLimitingEvent.class);

            // Validate event
            validateRateLimitingEvent(event);

            // Process based on rate limiting event type
            processRateLimitingEventByType(event);

            // Update metrics
            updateMetrics(event);

            // Record processing
            recordProcessedEvent(eventKey);

            eventsProcessedCounter.increment();
            acknowledgment.acknowledge();

            logger.info("Successfully processed rate limiting event: {}", eventKey);

        } catch (Exception e) {
            logger.error("Error processing rate limiting event: key={}, error={}", eventKey, e.getMessage(), e);
            eventsFailedCounter.increment();

            // Send alert for processing failure
            infrastructureAlertService.sendOperationalAlert(
                "Rate Limiting Event Processing Failed",
                String.format("Failed to process rate limiting event %s: %s", eventKey, e.getMessage()),
                "HIGH"
            );

            throw e; // Trigger retry mechanism
        } finally {
            sample.stop(processingTimer);
            cleanupExpiredEvents();
        }
    }

    private void processRateLimitingEventByType(RateLimitingEvent event) {
        switch (event.getEventType()) {
            case "RATE_LIMIT_EXCEEDED":
                handleRateLimitExceeded(event);
                break;
            case "THROTTLING_ACTIVATED":
                handleThrottlingActivated(event);
                break;
            case "BURST_CAPACITY_TRIGGERED":
                handleBurstCapacityTriggered(event);
                break;
            case "QUOTA_EXHAUSTED":
                handleQuotaExhausted(event);
                break;
            case "CLIENT_BLOCKED":
                handleClientBlocked(event);
                break;
            case "RATE_LIMIT_RECOVERED":
                handleRateLimitRecovered(event);
                break;
            case "THROTTLING_DISABLED":
                handleThrottlingDisabled(event);
                break;
            case "QUOTA_RESET":
                handleQuotaReset(event);
                break;
            case "CONFIGURATION_UPDATED":
                handleConfigurationUpdated(event);
                break;
            case "ADAPTIVE_SCALING":
                handleAdaptiveScaling(event);
                break;
            default:
                logger.warn("Unknown rate limiting event type: {}", event.getEventType());
                handleUnknownEventType(event);
        }
    }

    private void handleRateLimitExceeded(RateLimitingEvent event) {
        logger.warn("Rate limit exceeded: service={}, endpoint={}, client={}, rate={}",
                   event.getServiceName(), event.getEndpoint(), event.getClientId(), event.getCurrentRate());

        // Track throttled requests
        throttledRequestsGauge.addAndGet(event.getThrottledCount());

        // Check if critical threshold reached
        if (event.getCurrentRate() > event.getThreshold() * 1.5) {
            infrastructureAlertService.sendExecutiveAlert(
                "Critical Rate Limit Violation",
                String.format("Service %s exceeded rate limit by 50%% - Current: %d, Threshold: %d",
                             event.getServiceName(), event.getCurrentRate(), event.getThreshold()),
                "CRITICAL"
            );
        } else {
            infrastructureAlertService.sendOperationalAlert(
                "Rate Limit Exceeded",
                String.format("Service %s rate limit exceeded - Current: %d, Threshold: %d",
                             event.getServiceName(), event.getCurrentRate(), event.getThreshold()),
                "HIGH"
            );
        }

        // Initiate recovery actions
        rateLimitingMetricsService.recordRateLimitViolation(event);
        recoveryService.initiateRateLimitRecovery(event);
    }

    private void handleThrottlingActivated(RateLimitingEvent event) {
        logger.info("Throttling activated: service={}, level={}, duration={}",
                   event.getServiceName(), event.getThrottlingLevel(), event.getDuration());

        activeRateLimitsGauge.incrementAndGet();

        // Check if severe throttling
        if ("SEVERE".equals(event.getThrottlingLevel())) {
            infrastructureAlertService.sendExecutiveAlert(
                "Severe Throttling Activated",
                String.format("Severe throttling activated for service %s - Duration: %s",
                             event.getServiceName(), event.getDuration()),
                "HIGH"
            );
        }

        rateLimitingMetricsService.recordThrottlingActivation(event);
    }

    private void handleBurstCapacityTriggered(RateLimitingEvent event) {
        logger.info("Burst capacity triggered: service={}, burstRate={}, duration={}",
                   event.getServiceName(), event.getBurstRate(), event.getDuration());

        // Monitor burst capacity usage
        if (event.getBurstUtilization() > 80) {
            infrastructureAlertService.sendOperationalAlert(
                "High Burst Capacity Usage",
                String.format("Service %s using %d%% of burst capacity",
                             event.getServiceName(), event.getBurstUtilization()),
                "MEDIUM"
            );
        }

        rateLimitingMetricsService.recordBurstCapacityUsage(event);
    }

    private void handleQuotaExhausted(RateLimitingEvent event) {
        logger.warn("Quota exhausted: client={}, quotaType={}, period={}",
                   event.getClientId(), event.getQuotaType(), event.getQuotaPeriod());

        // Check for premium clients
        if ("PREMIUM".equals(event.getClientTier())) {
            infrastructureAlertService.sendExecutiveAlert(
                "Premium Client Quota Exhausted",
                String.format("Premium client %s exhausted %s quota for period %s",
                             event.getClientId(), event.getQuotaType(), event.getQuotaPeriod()),
                "HIGH"
            );
        }

        rateLimitingMetricsService.recordQuotaExhaustion(event);
        recoveryService.initiateQuotaRecovery(event);
    }

    private void handleClientBlocked(RateLimitingEvent event) {
        logger.warn("Client blocked: clientId={}, reason={}, duration={}",
                   event.getClientId(), event.getBlockReason(), event.getBlockDuration());

        // Immediate alert for client blocking
        infrastructureAlertService.sendOperationalAlert(
            "Client Blocked",
            String.format("Client %s blocked - Reason: %s, Duration: %s",
                         event.getClientId(), event.getBlockReason(), event.getBlockDuration()),
            "HIGH"
        );

        rateLimitingMetricsService.recordClientBlock(event);
    }

    private void handleRateLimitRecovered(RateLimitingEvent event) {
        logger.info("Rate limit recovered: service={}, endpoint={}",
                   event.getServiceName(), event.getEndpoint());

        throttledRequestsGauge.addAndGet(-event.getRecoveredCount());
        activeRateLimitsGauge.decrementAndGet();

        rateLimitingMetricsService.recordRateLimitRecovery(event);
    }

    private void handleThrottlingDisabled(RateLimitingEvent event) {
        logger.info("Throttling disabled: service={}, reason={}",
                   event.getServiceName(), event.getDisableReason());

        activeRateLimitsGauge.decrementAndGet();
        rateLimitingMetricsService.recordThrottlingDisabled(event);
    }

    private void handleQuotaReset(RateLimitingEvent event) {
        logger.info("Quota reset: client={}, quotaType={}, newPeriod={}",
                   event.getClientId(), event.getQuotaType(), event.getQuotaPeriod());

        rateLimitingMetricsService.recordQuotaReset(event);
    }

    private void handleConfigurationUpdated(RateLimitingEvent event) {
        logger.info("Rate limiting configuration updated: service={}, changes={}",
                   event.getServiceName(), event.getConfigurationChanges());

        // Alert for significant configuration changes
        if (event.getConfigurationChanges().contains("threshold")) {
            infrastructureAlertService.sendOperationalAlert(
                "Rate Limiting Configuration Updated",
                String.format("Rate limiting thresholds updated for service %s: %s",
                             event.getServiceName(), event.getConfigurationChanges()),
                "MEDIUM"
            );
        }

        rateLimitingMetricsService.recordConfigurationUpdate(event);
    }

    private void handleAdaptiveScaling(RateLimitingEvent event) {
        logger.info("Adaptive rate limiting scaling: service={}, action={}, newThreshold={}",
                   event.getServiceName(), event.getScalingAction(), event.getNewThreshold());

        rateLimitingMetricsService.recordAdaptiveScaling(event);
    }

    private void handleUnknownEventType(RateLimitingEvent event) {
        logger.warn("Processing unknown rate limiting event type as generic event: {}", event.getEventType());

        infrastructureAlertService.sendOperationalAlert(
            "Unknown Rate Limiting Event",
            String.format("Unknown rate limiting event type %s for service %s",
                         event.getEventType(), event.getServiceName()),
            "LOW"
        );

        rateLimitingMetricsService.recordGenericRateLimitingEvent(event);
    }

    private void updateMetrics(RateLimitingEvent event) {
        // Update operational metrics
        meterRegistry.counter("rate_limiting_events_by_type", "type", event.getEventType()).increment();
        meterRegistry.counter("rate_limiting_events_by_service", "service", event.getServiceName()).increment();

        if (event.getSeverity() != null) {
            meterRegistry.counter("rate_limiting_events_by_severity", "severity", event.getSeverity()).increment();
        }

        // Track impact metrics
        if (event.getImpactScore() > 0) {
            meterRegistry.gauge("rate_limiting_impact_score", event.getImpactScore());
        }
    }

    private void validateRateLimitingEvent(RateLimitingEvent event) {
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Rate limiting event type cannot be null or empty");
        }

        if (event.getServiceName() == null || event.getServiceName().trim().isEmpty()) {
            throw new IllegalArgumentException("Service name cannot be null or empty");
        }

        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private boolean isDuplicate(String eventKey) {
        LocalDateTime lastProcessed = processedEvents.get(eventKey);
        return lastProcessed != null &&
               ChronoUnit.HOURS.between(lastProcessed, LocalDateTime.now()) < IDEMPOTENCY_TTL_HOURS;
    }

    private void recordProcessedEvent(String eventKey) {
        processedEvents.put(eventKey, LocalDateTime.now());
    }

    private void cleanupExpiredEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(IDEMPOTENCY_TTL_HOURS);
        processedEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    @DltHandler
    public void handleDltRateLimitingEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_KEY) String eventKey,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        logger.error("Rate limiting event sent to DLT: key={}, exception={}", eventKey, exceptionMessage);

        eventsFailedCounter.increment();

        // Send critical alert for DLT events
        infrastructureAlertService.sendExecutiveAlert(
            "Rate Limiting Event Processing Failed - DLT",
            String.format("Rate limiting event %s sent to dead letter topic. Exception: %s", eventKey, exceptionMessage),
            "CRITICAL"
        );

        // Attempt manual recovery process
        recoveryService.initiateManualRecovery("RATE_LIMITING_DLT", eventKey, eventPayload);
    }

    // Circuit breaker fallback method
    public void fallbackProcessRateLimitingEvent(
            String eventPayload, String eventKey, String topic, int partition, long offset,
            Acknowledgment acknowledgment, Exception ex) {

        logger.error("Circuit breaker activated for rate limiting event processing: key={}, error={}",
                    eventKey, ex.getMessage());

        // Send alert about circuit breaker activation
        infrastructureAlertService.sendOperationalAlert(
            "Rate Limiting Consumer Circuit Breaker Activated",
            String.format("Circuit breaker activated for rate limiting event %s: %s", eventKey, ex.getMessage()),
            "HIGH"
        );

        // Attempt alternative processing or store for later retry
        recoveryService.storeForRetry("RATE_LIMITING_EVENT", eventKey, eventPayload);

        acknowledgment.acknowledge();
    }
}