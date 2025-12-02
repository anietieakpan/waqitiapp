package com.waqiti.payment.kafka;

import com.waqiti.common.events.AutoScalingTriggerEvent;
import com.waqiti.payment.domain.ScalingTrigger;
import com.waqiti.payment.repository.ScalingTriggerRepository;
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
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class AutoScalingTriggersConsumer {

    private final ScalingTriggerRepository scalingTriggerRepository;
    private final AutoScalingService autoScalingService;
    private final PaymentProcessingService paymentProcessingService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("auto_scaling_triggers_processed_total")
            .description("Total number of successfully processed auto-scaling trigger events")
            .register(meterRegistry);
        errorCounter = Counter.builder("auto_scaling_triggers_errors_total")
            .description("Total number of auto-scaling trigger processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("auto_scaling_triggers_processing_duration")
            .description("Time taken to process auto-scaling trigger events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"auto-scaling-triggers", "scaling-policy-triggers", "metric-threshold-alerts"},
        groupId = "auto-scaling-triggers-service-group",
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
    @CircuitBreaker(name = "auto-scaling-triggers", fallbackMethod = "handleAutoScalingTriggerEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAutoScalingTriggerEvent(
            @Payload AutoScalingTriggerEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("trigger-%s-p%d-o%d", event.getServiceName(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getServiceName(), event.getTriggerType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing auto-scaling trigger: service={}, trigger={}, metric={}",
                event.getServiceName(), event.getTriggerType(), event.getMetricName());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getTriggerType()) {
                case CPU_THRESHOLD_EXCEEDED:
                    processCpuThresholdExceeded(event, correlationId);
                    break;

                case MEMORY_THRESHOLD_EXCEEDED:
                    processMemoryThresholdExceeded(event, correlationId);
                    break;

                case QUEUE_DEPTH_THRESHOLD_EXCEEDED:
                    processQueueDepthThresholdExceeded(event, correlationId);
                    break;

                case RESPONSE_TIME_THRESHOLD_EXCEEDED:
                    processResponseTimeThresholdExceeded(event, correlationId);
                    break;

                case ERROR_RATE_THRESHOLD_EXCEEDED:
                    processErrorRateThresholdExceeded(event, correlationId);
                    break;

                case TRANSACTION_VOLUME_THRESHOLD_EXCEEDED:
                    processTransactionVolumeThresholdExceeded(event, correlationId);
                    break;

                case CUSTOM_METRIC_THRESHOLD_EXCEEDED:
                    processCustomMetricThresholdExceeded(event, correlationId);
                    break;

                case THRESHOLD_NORMALIZED:
                    processThresholdNormalized(event, correlationId);
                    break;

                case SCALING_COOLDOWN_EXPIRED:
                    processScalingCooldownExpired(event, correlationId);
                    break;

                default:
                    log.warn("Unknown auto-scaling trigger type: {}", event.getTriggerType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("AUTO_SCALING_TRIGGER_EVENT_PROCESSED", event.getServiceName(),
                Map.of("triggerType", event.getTriggerType(), "metricName", event.getMetricName(),
                    "currentValue", event.getCurrentValue(), "thresholdValue", event.getThresholdValue(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process auto-scaling trigger event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("auto-scaling-triggers-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleAutoScalingTriggerEventFallback(
            AutoScalingTriggerEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("trigger-fallback-%s-p%d-o%d", event.getServiceName(), partition, offset);

        log.error("Circuit breaker fallback triggered for auto-scaling trigger: service={}, error={}",
            event.getServiceName(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("auto-scaling-triggers-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Auto-Scaling Trigger Circuit Breaker Triggered",
                String.format("Service %s scaling trigger failed: %s", event.getServiceName(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAutoScalingTriggerEvent(
            @Payload AutoScalingTriggerEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-trigger-%s-%d", event.getServiceName(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Auto-scaling trigger permanently failed: service={}, topic={}, error={}",
            event.getServiceName(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("AUTO_SCALING_TRIGGER_DLT_EVENT", event.getServiceName(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "triggerType", event.getTriggerType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Auto-Scaling Trigger Dead Letter Event",
                String.format("Service %s scaling trigger sent to DLT: %s", event.getServiceName(), exceptionMessage),
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

    private void processCpuThresholdExceeded(AutoScalingTriggerEvent event, String correlationId) {
        // Record CPU threshold breach
        ScalingTrigger trigger = autoScalingService.createScalingTrigger(
            event.getServiceName(),
            "CPU_THRESHOLD_EXCEEDED",
            event.getCurrentValue(),
            event.getThresholdValue(),
            correlationId
        );

        // Evaluate scaling decision
        boolean shouldScale = autoScalingService.evaluateScalingDecision(
            event.getServiceName(),
            "CPU",
            event.getCurrentValue(),
            event.getThresholdValue()
        );

        if (shouldScale) {
            // Calculate target instances based on CPU utilization
            int targetInstances = autoScalingService.calculateTargetInstancesForCpu(
                event.getServiceName(),
                event.getCurrentValue(),
                event.getThresholdValue()
            );

            // Trigger scale-up
            autoScalingService.triggerScaleUp(
                event.getServiceName(),
                targetInstances,
                "CPU threshold exceeded",
                correlationId
            );
        }

        // Record metrics
        metricsService.recordThresholdBreach(event.getServiceName(), "CPU", event.getCurrentValue());

        log.info("CPU threshold exceeded: service={}, current={}%, threshold={}%, scaling={}",
            event.getServiceName(), event.getCurrentValue(), event.getThresholdValue(), shouldScale);
    }

    private void processMemoryThresholdExceeded(AutoScalingTriggerEvent event, String correlationId) {
        // Record memory threshold breach
        autoScalingService.createScalingTrigger(
            event.getServiceName(),
            "MEMORY_THRESHOLD_EXCEEDED",
            event.getCurrentValue(),
            event.getThresholdValue(),
            correlationId
        );

        // Check if memory usage indicates memory leak
        boolean potentialMemoryLeak = autoScalingService.checkForMemoryLeak(
            event.getServiceName(),
            event.getCurrentValue()
        );

        if (potentialMemoryLeak) {
            // Send critical alert for potential memory leak
            notificationService.sendCriticalAlert(
                "Potential Memory Leak Detected",
                String.format("Service %s showing potential memory leak: %.2f%% usage",
                    event.getServiceName(), event.getCurrentValue()),
                Map.of("serviceName", event.getServiceName(), "memoryUsage", event.getCurrentValue(),
                    "correlationId", correlationId)
            );
        } else {
            // Calculate target instances based on memory utilization
            int targetInstances = autoScalingService.calculateTargetInstancesForMemory(
                event.getServiceName(),
                event.getCurrentValue(),
                event.getThresholdValue()
            );

            // Trigger scale-up
            autoScalingService.triggerScaleUp(
                event.getServiceName(),
                targetInstances,
                "Memory threshold exceeded",
                correlationId
            );
        }

        // Record metrics
        metricsService.recordThresholdBreach(event.getServiceName(), "MEMORY", event.getCurrentValue());

        log.info("Memory threshold exceeded: service={}, current={}%, threshold={}%, memoryLeak={}",
            event.getServiceName(), event.getCurrentValue(), event.getThresholdValue(), potentialMemoryLeak);
    }

    private void processQueueDepthThresholdExceeded(AutoScalingTriggerEvent event, String correlationId) {
        // Record queue depth threshold breach
        autoScalingService.createScalingTrigger(
            event.getServiceName(),
            "QUEUE_DEPTH_THRESHOLD_EXCEEDED",
            event.getCurrentValue(),
            event.getThresholdValue(),
            correlationId
        );

        // Check if this is a payment processing queue
        boolean isPaymentQueue = autoScalingService.isPaymentProcessingQueue(event.getQueueName());

        if (isPaymentQueue) {
            // Priority scaling for payment queues
            int targetInstances = autoScalingService.calculateTargetInstancesForQueue(
                event.getServiceName(),
                event.getCurrentValue(),
                event.getThresholdValue(),
                true // isPriority
            );

            // Immediate scale-up for payment processing
            autoScalingService.triggerImmediateScaleUp(
                event.getServiceName(),
                targetInstances,
                "Payment queue depth exceeded",
                correlationId
            );

            // Send high priority alert
            notificationService.sendOperationalAlert(
                "Payment Queue Depth Critical",
                String.format("Payment processing queue %s depth exceeded: %.0f messages (threshold: %.0f)",
                    event.getQueueName(), event.getCurrentValue(), event.getThresholdValue()),
                "CRITICAL"
            );
        } else {
            // Standard scaling for other queues
            int targetInstances = autoScalingService.calculateTargetInstancesForQueue(
                event.getServiceName(),
                event.getCurrentValue(),
                event.getThresholdValue(),
                false // isPriority
            );

            autoScalingService.triggerScaleUp(
                event.getServiceName(),
                targetInstances,
                "Queue depth exceeded",
                correlationId
            );
        }

        // Record metrics
        metricsService.recordQueueDepthBreach(event.getServiceName(), event.getQueueName(), event.getCurrentValue());

        log.info("Queue depth threshold exceeded: service={}, queue={}, depth={}, threshold={}",
            event.getServiceName(), event.getQueueName(), event.getCurrentValue(), event.getThresholdValue());
    }

    private void processResponseTimeThresholdExceeded(AutoScalingTriggerEvent event, String correlationId) {
        // Record response time threshold breach
        autoScalingService.createScalingTrigger(
            event.getServiceName(),
            "RESPONSE_TIME_THRESHOLD_EXCEEDED",
            event.getCurrentValue(),
            event.getThresholdValue(),
            correlationId
        );

        // Check if this affects payment processing
        boolean affectsPayments = autoScalingService.isPaymentCriticalService(event.getServiceName());

        if (affectsPayments && event.getCurrentValue() > event.getThresholdValue() * 2.0) {
            // Critical response time degradation for payment services
            autoScalingService.triggerEmergencyScaling(
                event.getServiceName(),
                "RESPONSE_TIME_CRITICAL",
                correlationId
            );

            notificationService.sendCriticalAlert(
                "Payment Service Response Time Critical",
                String.format("Service %s response time critically degraded: %.2fms (threshold: %.2fms)",
                    event.getServiceName(), event.getCurrentValue(), event.getThresholdValue()),
                Map.of("serviceName", event.getServiceName(), "responseTime", event.getCurrentValue(),
                    "correlationId", correlationId)
            );
        } else {
            // Standard scaling for response time issues
            int targetInstances = autoScalingService.calculateTargetInstancesForResponseTime(
                event.getServiceName(),
                event.getCurrentValue(),
                event.getThresholdValue()
            );

            autoScalingService.triggerScaleUp(
                event.getServiceName(),
                targetInstances,
                "Response time threshold exceeded",
                correlationId
            );
        }

        // Record metrics
        metricsService.recordResponseTimeBreach(event.getServiceName(), event.getCurrentValue());

        log.info("Response time threshold exceeded: service={}, current={}ms, threshold={}ms",
            event.getServiceName(), event.getCurrentValue(), event.getThresholdValue());
    }

    private void processErrorRateThresholdExceeded(AutoScalingTriggerEvent event, String correlationId) {
        // Record error rate threshold breach
        autoScalingService.createScalingTrigger(
            event.getServiceName(),
            "ERROR_RATE_THRESHOLD_EXCEEDED",
            event.getCurrentValue(),
            event.getThresholdValue(),
            correlationId
        );

        // Analyze if scaling can help with error rate
        boolean scalingCanHelp = autoScalingService.analyzeIfScalingHelpsWithErrors(
            event.getServiceName(),
            event.getCurrentValue()
        );

        if (scalingCanHelp) {
            // Scale up to reduce load and potentially reduce errors
            int targetInstances = autoScalingService.calculateTargetInstancesForErrorRate(
                event.getServiceName(),
                event.getCurrentValue(),
                event.getThresholdValue()
            );

            autoScalingService.triggerScaleUp(
                event.getServiceName(),
                targetInstances,
                "Error rate threshold exceeded",
                correlationId
            );
        } else {
            // Errors might be due to code issues, not load
            notificationService.sendOperationalAlert(
                "High Error Rate Detected - Investigation Needed",
                String.format("Service %s error rate high: %.2f%% (threshold: %.2f%%). May require code investigation.",
                    event.getServiceName(), event.getCurrentValue(), event.getThresholdValue()),
                "HIGH"
            );
        }

        // Record metrics
        metricsService.recordErrorRateBreach(event.getServiceName(), event.getCurrentValue());

        log.warn("Error rate threshold exceeded: service={}, rate={}%, threshold={}%, scalingHelps={}",
            event.getServiceName(), event.getCurrentValue(), event.getThresholdValue(), scalingCanHelp);
    }

    private void processTransactionVolumeThresholdExceeded(AutoScalingTriggerEvent event, String correlationId) {
        // Record transaction volume threshold breach
        autoScalingService.createScalingTrigger(
            event.getServiceName(),
            "TRANSACTION_VOLUME_THRESHOLD_EXCEEDED",
            event.getCurrentValue(),
            event.getThresholdValue(),
            correlationId
        );

        // Check transaction processing capacity
        double currentCapacity = autoScalingService.getCurrentTransactionProcessingCapacity(event.getServiceName());
        double requiredCapacity = event.getCurrentValue();

        if (requiredCapacity > currentCapacity * 1.2) {
            // Significant capacity shortage - emergency scaling
            int targetInstances = autoScalingService.calculateTargetInstancesForTransactionVolume(
                event.getServiceName(),
                requiredCapacity,
                currentCapacity
            );

            autoScalingService.triggerEmergencyScaling(
                event.getServiceName(),
                "TRANSACTION_VOLUME_CRITICAL",
                correlationId
            );

            notificationService.sendCriticalAlert(
                "Transaction Volume Capacity Critical",
                String.format("Service %s transaction volume critical: %.0f TPS (capacity: %.0f TPS)",
                    event.getServiceName(), requiredCapacity, currentCapacity),
                Map.of("serviceName", event.getServiceName(), "volume", requiredCapacity,
                    "correlationId", correlationId)
            );
        } else {
            // Standard scaling for transaction volume
            int targetInstances = autoScalingService.calculateTargetInstancesForTransactionVolume(
                event.getServiceName(),
                requiredCapacity,
                currentCapacity
            );

            autoScalingService.triggerScaleUp(
                event.getServiceName(),
                targetInstances,
                "Transaction volume threshold exceeded",
                correlationId
            );
        }

        // Record metrics
        metricsService.recordTransactionVolumeBreach(event.getServiceName(), event.getCurrentValue());

        log.info("Transaction volume threshold exceeded: service={}, volume={}, capacity={}",
            event.getServiceName(), requiredCapacity, currentCapacity);
    }

    private void processCustomMetricThresholdExceeded(AutoScalingTriggerEvent event, String correlationId) {
        // Record custom metric threshold breach
        autoScalingService.createScalingTrigger(
            event.getServiceName(),
            "CUSTOM_METRIC_THRESHOLD_EXCEEDED",
            event.getCurrentValue(),
            event.getThresholdValue(),
            correlationId
        );

        // Process based on custom metric type
        autoScalingService.processCustomMetricTrigger(
            event.getServiceName(),
            event.getMetricName(),
            event.getCurrentValue(),
            event.getThresholdValue(),
            correlationId
        );

        // Record metrics
        metricsService.recordCustomMetricBreach(event.getServiceName(), event.getMetricName(), event.getCurrentValue());

        log.info("Custom metric threshold exceeded: service={}, metric={}, value={}, threshold={}",
            event.getServiceName(), event.getMetricName(), event.getCurrentValue(), event.getThresholdValue());
    }

    private void processThresholdNormalized(AutoScalingTriggerEvent event, String correlationId) {
        // Record threshold normalization
        autoScalingService.recordThresholdNormalization(
            event.getServiceName(),
            event.getMetricName(),
            event.getCurrentValue(),
            event.getThresholdValue(),
            correlationId
        );

        // Check if scale-down is appropriate
        boolean canScaleDown = autoScalingService.evaluateScaleDownOpportunity(
            event.getServiceName(),
            event.getMetricName(),
            event.getCurrentValue()
        );

        if (canScaleDown) {
            int targetInstances = autoScalingService.calculateScaleDownTarget(
                event.getServiceName(),
                event.getMetricName(),
                event.getCurrentValue()
            );

            autoScalingService.triggerScaleDown(
                event.getServiceName(),
                targetInstances,
                "Threshold normalized - scale down opportunity",
                correlationId
            );
        }

        log.info("Threshold normalized: service={}, metric={}, canScaleDown={}",
            event.getServiceName(), event.getMetricName(), canScaleDown);
    }

    private void processScalingCooldownExpired(AutoScalingTriggerEvent event, String correlationId) {
        // Record cooldown expiration
        autoScalingService.recordCooldownExpiration(
            event.getServiceName(),
            event.getCooldownType(),
            correlationId
        );

        // Check if there are pending scaling actions
        boolean hasPendingActions = autoScalingService.checkPendingScalingActions(event.getServiceName());

        if (hasPendingActions) {
            // Process pending scaling actions
            autoScalingService.processPendingScalingActions(
                event.getServiceName(),
                correlationId
            );
        }

        log.info("Scaling cooldown expired: service={}, cooldownType={}, pendingActions={}",
            event.getServiceName(), event.getCooldownType(), hasPendingActions);
    }
}