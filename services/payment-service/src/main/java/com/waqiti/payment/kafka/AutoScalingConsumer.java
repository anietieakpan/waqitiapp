package com.waqiti.payment.kafka;

import com.waqiti.common.events.AutoScalingEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.domain.AutoScalingConfiguration;
import com.waqiti.payment.repository.AutoScalingConfigurationRepository;
import com.waqiti.payment.service.AutoScalingService;
import com.waqiti.payment.service.PaymentProcessingService;
import com.waqiti.payment.metrics.PaymentMetricsService;
import com.waqiti.common.audit.AuditService;
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

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class AutoScalingConsumer {

    private final AutoScalingConfigurationRepository autoScalingConfigRepository;
    private final AutoScalingService autoScalingService;
    private final PaymentProcessingService paymentProcessingService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("auto_scaling_processed_total")
            .description("Total number of successfully processed auto-scaling events")
            .register(meterRegistry);
        errorCounter = Counter.builder("auto_scaling_errors_total")
            .description("Total number of auto-scaling processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("auto_scaling_processing_duration")
            .description("Time taken to process auto-scaling events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"auto-scaling", "payment-processing-scaling", "load-balancer-scaling"},
        groupId = "auto-scaling-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "auto-scaling", fallbackMethod = "handleAutoScalingEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAutoScalingEvent(
            @Payload AutoScalingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("scaling-%s-p%d-o%d", event.getServiceName(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getServiceName(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing auto-scaling: service={}, action={}, currentInstances={}",
                event.getServiceName(), event.getScalingAction(), event.getCurrentInstances());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case SCALE_UP_TRIGGERED:
                    processScaleUpTriggered(event, correlationId);
                    break;

                case SCALE_DOWN_TRIGGERED:
                    processScaleDownTriggered(event, correlationId);
                    break;

                case SCALE_UP_COMPLETED:
                    processScaleUpCompleted(event, correlationId);
                    break;

                case SCALE_DOWN_COMPLETED:
                    processScaleDownCompleted(event, correlationId);
                    break;

                case SCALING_FAILED:
                    processScalingFailed(event, correlationId);
                    break;

                case CAPACITY_THRESHOLD_REACHED:
                    processCapacityThresholdReached(event, correlationId);
                    break;

                case AUTO_SCALING_DISABLED:
                    processAutoScalingDisabled(event, correlationId);
                    break;

                case AUTO_SCALING_ENABLED:
                    processAutoScalingEnabled(event, correlationId);
                    break;

                default:
                    log.warn("Unknown auto-scaling event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("AUTO_SCALING_EVENT_PROCESSED", event.getServiceName(),
                Map.of("eventType", event.getEventType(), "scalingAction", event.getScalingAction(),
                    "currentInstances", event.getCurrentInstances(), "targetInstances", event.getTargetInstances(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process auto-scaling event: {}", e.getMessage(), e);

            // Send to DLQ with context
            dlqHandler.handleFailedMessage(
                "auto-scaling",
                event,
                e,
                Map.of(
                    "serviceName", event.getServiceName(),
                    "eventType", event.getEventType().toString(),
                    "scalingAction", event.getScalingAction(),
                    "currentInstances", String.valueOf(event.getCurrentInstances()),
                    "targetInstances", String.valueOf(event.getTargetInstances()),
                    "correlationId", correlationId,
                    "partition", String.valueOf(partition),
                    "offset", String.valueOf(offset)
                )
            );

            // Send fallback event
            kafkaTemplate.send("auto-scaling-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleAutoScalingEventFallback(
            AutoScalingEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("scaling-fallback-%s-p%d-o%d", event.getServiceName(), partition, offset);

        log.error("Circuit breaker fallback triggered for auto-scaling: service={}, error={}",
            event.getServiceName(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("auto-scaling-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Auto-Scaling Circuit Breaker Triggered",
                String.format("Service %s auto-scaling failed: %s", event.getServiceName(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAutoScalingEvent(
            @Payload AutoScalingEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-scaling-%s-%d", event.getServiceName(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Auto-scaling permanently failed: service={}, topic={}, error={}",
            event.getServiceName(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("AUTO_SCALING_DLT_EVENT", event.getServiceName(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Auto-Scaling Dead Letter Event",
                String.format("Service %s auto-scaling sent to DLT: %s", event.getServiceName(), exceptionMessage),
                Map.of("serviceName", event.getServiceName(), "topic", topic, "correlationId", correlationId)
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

    private void processScaleUpTriggered(AutoScalingEvent event, String correlationId) {
        // Record scale-up trigger
        autoScalingService.recordScaleUpTrigger(
            event.getServiceName(),
            event.getCurrentInstances(),
            event.getTargetInstances(),
            event.getTriggerReason(),
            correlationId
        );

        // Initialize scaling process
        autoScalingService.initiateScaleUp(
            event.getServiceName(),
            event.getTargetInstances(),
            event.getScalingPolicy(),
            correlationId
        );

        // Update scaling metrics
        metricsService.recordScalingTrigger("SCALE_UP", event.getServiceName());

        // Send notification for critical services
        if (event.isCriticalService()) {
            notificationService.sendOperationalAlert(
                "Critical Service Scale-Up Triggered",
                String.format("Service %s is scaling up from %d to %d instances due to: %s",
                    event.getServiceName(), event.getCurrentInstances(), event.getTargetInstances(),
                    event.getTriggerReason()),
                "HIGH"
            );
        }

        log.info("Scale-up triggered: service={}, from={} to={} instances, reason={}",
            event.getServiceName(), event.getCurrentInstances(), event.getTargetInstances(),
            event.getTriggerReason());
    }

    private void processScaleDownTriggered(AutoScalingEvent event, String correlationId) {
        // Record scale-down trigger
        autoScalingService.recordScaleDownTrigger(
            event.getServiceName(),
            event.getCurrentInstances(),
            event.getTargetInstances(),
            event.getTriggerReason(),
            correlationId
        );

        // Check if scale-down is safe for payment processing
        boolean safeToScaleDown = autoScalingService.isSafeToScaleDown(
            event.getServiceName(),
            event.getTargetInstances()
        );

        if (safeToScaleDown) {
            // Initialize scaling process
            autoScalingService.initiateScaleDown(
                event.getServiceName(),
                event.getTargetInstances(),
                event.getScalingPolicy(),
                correlationId
            );
        } else {
            // Defer scale-down due to safety concerns
            autoScalingService.deferScaleDown(
                event.getServiceName(),
                "Payment processing load too high for scale-down",
                correlationId
            );
        }

        // Update scaling metrics
        metricsService.recordScalingTrigger("SCALE_DOWN", event.getServiceName());

        log.info("Scale-down triggered: service={}, from={} to={} instances, reason={}, safe={}",
            event.getServiceName(), event.getCurrentInstances(), event.getTargetInstances(),
            event.getTriggerReason(), safeToScaleDown);
    }

    private void processScaleUpCompleted(AutoScalingEvent event, String correlationId) {
        // Update scaling configuration
        autoScalingService.updateScalingCompletion(
            event.getServiceName(),
            "SCALE_UP_COMPLETED",
            event.getTargetInstances(),
            correlationId
        );

        // Verify new instances are healthy
        autoScalingService.verifyInstanceHealth(
            event.getServiceName(),
            event.getTargetInstances(),
            correlationId
        );

        // Update capacity metrics
        metricsService.recordScalingCompletion("SCALE_UP", event.getServiceName(),
            event.getCurrentInstances(), event.getTargetInstances());

        // Send success notification
        notificationService.sendOperationalAlert(
            "Service Scale-Up Completed",
            String.format("Service %s successfully scaled up to %d instances",
                event.getServiceName(), event.getTargetInstances()),
            "INFO"
        );

        log.info("Scale-up completed: service={}, new instance count={}",
            event.getServiceName(), event.getTargetInstances());
    }

    private void processScaleDownCompleted(AutoScalingEvent event, String correlationId) {
        // Update scaling configuration
        autoScalingService.updateScalingCompletion(
            event.getServiceName(),
            "SCALE_DOWN_COMPLETED",
            event.getTargetInstances(),
            correlationId
        );

        // Clean up terminated instances
        autoScalingService.cleanupTerminatedInstances(
            event.getServiceName(),
            event.getTerminatedInstances(),
            correlationId
        );

        // Update capacity metrics
        metricsService.recordScalingCompletion("SCALE_DOWN", event.getServiceName(),
            event.getCurrentInstances(), event.getTargetInstances());

        // Send success notification
        notificationService.sendOperationalAlert(
            "Service Scale-Down Completed",
            String.format("Service %s successfully scaled down to %d instances",
                event.getServiceName(), event.getTargetInstances()),
            "INFO"
        );

        log.info("Scale-down completed: service={}, new instance count={}",
            event.getServiceName(), event.getTargetInstances());
    }

    private void processScalingFailed(AutoScalingEvent event, String correlationId) {
        // Record scaling failure
        autoScalingService.recordScalingFailure(
            event.getServiceName(),
            event.getScalingAction(),
            event.getFailureReason(),
            correlationId
        );

        // Update failure metrics
        metricsService.recordScalingFailure(event.getScalingAction(), event.getServiceName());

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Auto-Scaling Failed",
            String.format("Service %s scaling failed: %s. Action: %s",
                event.getServiceName(), event.getFailureReason(), event.getScalingAction()),
            Map.of("serviceName", event.getServiceName(), "scalingAction", event.getScalingAction(),
                "correlationId", correlationId)
        );

        // Trigger manual intervention workflow
        autoScalingService.triggerManualIntervention(
            event.getServiceName(),
            event.getScalingAction(),
            event.getFailureReason(),
            correlationId
        );

        log.error("Scaling failed: service={}, action={}, reason={}",
            event.getServiceName(), event.getScalingAction(), event.getFailureReason());
    }

    private void processCapacityThresholdReached(AutoScalingEvent event, String correlationId) {
        // Record threshold breach
        autoScalingService.recordCapacityThresholdBreach(
            event.getServiceName(),
            event.getThresholdType(),
            event.getCurrentUtilization(),
            event.getThresholdValue(),
            correlationId
        );

        // Evaluate if immediate scaling is needed
        boolean needsImmediateScaling = autoScalingService.evaluateImmediateScalingNeed(
            event.getServiceName(),
            event.getCurrentUtilization(),
            event.getThresholdType()
        );

        if (needsImmediateScaling) {
            // Trigger emergency scaling
            autoScalingService.triggerEmergencyScaling(
                event.getServiceName(),
                event.getThresholdType(),
                correlationId
            );
        }

        // Update threshold metrics
        metricsService.recordThresholdBreach(event.getServiceName(), event.getThresholdType());

        // Send threshold alert
        notificationService.sendOperationalAlert(
            "Capacity Threshold Reached",
            String.format("Service %s %s threshold reached: %.2f%% (limit: %.2f%%)",
                event.getServiceName(), event.getThresholdType(),
                event.getCurrentUtilization(), event.getThresholdValue()),
            needsImmediateScaling ? "CRITICAL" : "HIGH"
        );

        log.warn("Capacity threshold reached: service={}, type={}, utilization={}%, threshold={}%",
            event.getServiceName(), event.getThresholdType(),
            event.getCurrentUtilization(), event.getThresholdValue());
    }

    private void processAutoScalingDisabled(AutoScalingEvent event, String correlationId) {
        // Update auto-scaling configuration
        autoScalingService.disableAutoScaling(
            event.getServiceName(),
            event.getDisabledReason(),
            correlationId
        );

        // Record configuration change
        metricsService.recordAutoScalingConfigChange(event.getServiceName(), "DISABLED");

        // Send notification
        notificationService.sendOperationalAlert(
            "Auto-Scaling Disabled",
            String.format("Auto-scaling disabled for service %s: %s",
                event.getServiceName(), event.getDisabledReason()),
            "HIGH"
        );

        log.warn("Auto-scaling disabled: service={}, reason={}",
            event.getServiceName(), event.getDisabledReason());
    }

    private void processAutoScalingEnabled(AutoScalingEvent event, String correlationId) {
        // Update auto-scaling configuration
        autoScalingService.enableAutoScaling(
            event.getServiceName(),
            event.getEnabledReason(),
            correlationId
        );

        // Record configuration change
        metricsService.recordAutoScalingConfigChange(event.getServiceName(), "ENABLED");

        // Send notification
        notificationService.sendOperationalAlert(
            "Auto-Scaling Enabled",
            String.format("Auto-scaling enabled for service %s: %s",
                event.getServiceName(), event.getEnabledReason()),
            "INFO"
        );

        log.info("Auto-scaling enabled: service={}, reason={}",
            event.getServiceName(), event.getEnabledReason());
    }
}