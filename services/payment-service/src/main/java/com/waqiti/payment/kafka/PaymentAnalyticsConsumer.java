package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentAnalyticsEvent;
import com.waqiti.payment.service.PaymentAnalyticsService;
import com.waqiti.payment.service.PaymentMetricsService;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentAnalyticsConsumer {

    private final PaymentAnalyticsService analyticsService;
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
        successCounter = Counter.builder("payment_analytics_processed_total")
            .description("Total number of successfully processed payment analytics events")
            .register(meterRegistry);
        errorCounter = Counter.builder("payment_analytics_errors_total")
            .description("Total number of payment analytics processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("payment_analytics_processing_duration")
            .description("Time taken to process payment analytics events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"payment-analytics", "payment-metrics-events", "payment-insights"},
        groupId = "payment-analytics-service-group",
        containerFactory = "analyticsKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "payment-analytics", fallbackMethod = "handlePaymentAnalyticsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handlePaymentAnalyticsEvent(
            @Payload PaymentAnalyticsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("analytics-%s-p%d-o%d", event.getPaymentId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getPaymentId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing payment analytics: paymentId={}, type={}, amount={}, currency={}",
                event.getPaymentId(), event.getEventType(), event.getAmount(), event.getCurrency());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case PAYMENT_INITIATED:
                    processPaymentInitiated(event, correlationId);
                    break;

                case PAYMENT_COMPLETED:
                    processPaymentCompleted(event, correlationId);
                    break;

                case PAYMENT_FAILED:
                    processPaymentFailed(event, correlationId);
                    break;

                case PAYMENT_REFUNDED:
                    processPaymentRefunded(event, correlationId);
                    break;

                case PAYMENT_DISPUTED:
                    processPaymentDisputed(event, correlationId);
                    break;

                case PAYMENT_VOLUME_SPIKE:
                    processVolumeSpike(event, correlationId);
                    break;

                case PAYMENT_ANOMALY_DETECTED:
                    processAnomalyDetected(event, correlationId);
                    break;

                case PAYMENT_PATTERN_ANALYSIS:
                    processPatternAnalysis(event, correlationId);
                    break;

                default:
                    log.warn("Unknown payment analytics event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("PAYMENT_ANALYTICS_EVENT_PROCESSED", event.getPaymentId(),
                Map.of("eventType", event.getEventType(), "amount", event.getAmount(),
                    "currency", event.getCurrency(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process payment analytics event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("payment-analytics-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handlePaymentAnalyticsEventFallback(
            PaymentAnalyticsEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("analytics-fallback-%s-p%d-o%d", event.getPaymentId(), partition, offset);

        log.error("Circuit breaker fallback triggered for payment analytics: paymentId={}, error={}",
            event.getPaymentId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("payment-analytics-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Payment Analytics Circuit Breaker Triggered",
                String.format("Payment %s analytics processing failed: %s", event.getPaymentId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltPaymentAnalyticsEvent(
            @Payload PaymentAnalyticsEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-analytics-%s-%d", event.getPaymentId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Payment analytics permanently failed: paymentId={}, topic={}, error={}",
            event.getPaymentId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("PAYMENT_ANALYTICS_DLT_EVENT", event.getPaymentId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Payment Analytics Dead Letter Event",
                String.format("Payment %s analytics sent to DLT: %s", event.getPaymentId(), exceptionMessage),
                Map.of("paymentId", event.getPaymentId(), "topic", topic, "correlationId", correlationId)
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

    private void processPaymentInitiated(PaymentAnalyticsEvent event, String correlationId) {
        analyticsService.recordPaymentInitiation(
            event.getPaymentId(),
            event.getAmount(),
            event.getCurrency(),
            event.getPaymentMethod(),
            event.getMerchantId(),
            event.getCustomerId(),
            event.getTimestamp()
        );

        metricsService.incrementPaymentInitiations(event.getPaymentMethod(), event.getCurrency());

        log.info("Payment initiation analytics recorded: paymentId={}, amount={}",
            event.getPaymentId(), event.getAmount());
    }

    private void processPaymentCompleted(PaymentAnalyticsEvent event, String correlationId) {
        analyticsService.recordPaymentCompletion(
            event.getPaymentId(),
            event.getAmount(),
            event.getCurrency(),
            event.getPaymentMethod(),
            event.getProcessingTime(),
            event.getTimestamp()
        );

        metricsService.incrementSuccessfulPayments(event.getPaymentMethod(), event.getCurrency());
        metricsService.recordPaymentProcessingTime(event.getPaymentMethod(), event.getProcessingTime());

        // Check for volume patterns
        analyticsService.analyzePaymentVolume(event.getMerchantId(), event.getAmount(), event.getTimestamp());

        log.info("Payment completion analytics recorded: paymentId={}, processingTime={}ms",
            event.getPaymentId(), event.getProcessingTime());
    }

    private void processPaymentFailed(PaymentAnalyticsEvent event, String correlationId) {
        analyticsService.recordPaymentFailure(
            event.getPaymentId(),
            event.getAmount(),
            event.getCurrency(),
            event.getPaymentMethod(),
            event.getFailureReason(),
            event.getErrorCode(),
            event.getTimestamp()
        );

        metricsService.incrementFailedPayments(event.getPaymentMethod(), event.getErrorCode());

        // Check for failure patterns
        analyticsService.analyzeFailurePatterns(event.getMerchantId(), event.getErrorCode(), event.getTimestamp());

        // Send alert if failure rate is high
        if (analyticsService.isFailureRateHigh(event.getMerchantId())) {
            kafkaTemplate.send("payment-alerts", Map.of(
                "type", "HIGH_FAILURE_RATE",
                "merchantId", event.getMerchantId(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.warn("Payment failure analytics recorded: paymentId={}, reason={}",
            event.getPaymentId(), event.getFailureReason());
    }

    private void processPaymentRefunded(PaymentAnalyticsEvent event, String correlationId) {
        analyticsService.recordPaymentRefund(
            event.getPaymentId(),
            event.getOriginalPaymentId(),
            event.getAmount(),
            event.getCurrency(),
            event.getRefundReason(),
            event.getTimestamp()
        );

        metricsService.incrementRefunds(event.getPaymentMethod(), event.getRefundReason());

        // Analyze refund patterns
        analyticsService.analyzeRefundPatterns(event.getMerchantId(), event.getRefundReason(), event.getTimestamp());

        log.info("Payment refund analytics recorded: paymentId={}, originalPaymentId={}, reason={}",
            event.getPaymentId(), event.getOriginalPaymentId(), event.getRefundReason());
    }

    private void processPaymentDisputed(PaymentAnalyticsEvent event, String correlationId) {
        analyticsService.recordPaymentDispute(
            event.getPaymentId(),
            event.getAmount(),
            event.getCurrency(),
            event.getDisputeReason(),
            event.getTimestamp()
        );

        metricsService.incrementDisputes(event.getPaymentMethod(), event.getDisputeReason());

        // Analyze dispute patterns
        analyticsService.analyzeDisputePatterns(event.getMerchantId(), event.getDisputeReason(), event.getTimestamp());

        // Send alert for dispute patterns
        if (analyticsService.isDisputeRateHigh(event.getMerchantId())) {
            kafkaTemplate.send("payment-alerts", Map.of(
                "type", "HIGH_DISPUTE_RATE",
                "merchantId", event.getMerchantId(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.warn("Payment dispute analytics recorded: paymentId={}, reason={}",
            event.getPaymentId(), event.getDisputeReason());
    }

    private void processVolumeSpike(PaymentAnalyticsEvent event, String correlationId) {
        analyticsService.recordVolumeSpike(
            event.getMerchantId(),
            event.getVolumeIncrease(),
            event.getBaselineVolume(),
            event.getCurrentVolume(),
            event.getTimestamp()
        );

        // Send volume spike alert
        kafkaTemplate.send("payment-alerts", Map.of(
            "type", "VOLUME_SPIKE",
            "merchantId", event.getMerchantId(),
            "volumeIncrease", event.getVolumeIncrease(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.warn("Payment volume spike detected: merchantId={}, increase={}%",
            event.getMerchantId(), event.getVolumeIncrease());
    }

    private void processAnomalyDetected(PaymentAnalyticsEvent event, String correlationId) {
        analyticsService.recordAnomaly(
            event.getPaymentId(),
            event.getAnomalyType(),
            event.getAnomalyScore(),
            event.getAnomalyDetails(),
            event.getTimestamp()
        );

        // Send anomaly alert
        kafkaTemplate.send("fraud-detection-events", Map.of(
            "type", "PAYMENT_ANOMALY",
            "paymentId", event.getPaymentId(),
            "anomalyType", event.getAnomalyType(),
            "score", event.getAnomalyScore(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.warn("Payment anomaly detected: paymentId={}, type={}, score={}",
            event.getPaymentId(), event.getAnomalyType(), event.getAnomalyScore());
    }

    private void processPatternAnalysis(PaymentAnalyticsEvent event, String correlationId) {
        analyticsService.recordPatternAnalysis(
            event.getPattern(),
            event.getPatternType(),
            event.getConfidence(),
            event.getPatternDetails(),
            event.getTimestamp()
        );

        // If high-confidence pattern detected, send to ML service
        if (event.getConfidence() > 0.8) {
            kafkaTemplate.send("ml-model-events", Map.of(
                "type", "PAYMENT_PATTERN",
                "pattern", event.getPattern(),
                "confidence", event.getConfidence(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Payment pattern analysis recorded: pattern={}, confidence={}",
            event.getPattern(), event.getConfidence());
    }
}