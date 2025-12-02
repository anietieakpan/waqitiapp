package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.ContentAmplificationEvent;
import com.waqiti.monitoring.service.ContentMonitoringService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.InfrastructureMetricsService;
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
public class ContentAmplificationConsumer {

    private final ContentMonitoringService contentMonitoringService;
    private final AlertingService alertingService;
    private final InfrastructureMetricsService metricsService;
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
        successCounter = Counter.builder("content_amplification_processed_total")
            .description("Total number of successfully processed content amplification events")
            .register(meterRegistry);
        errorCounter = Counter.builder("content_amplification_errors_total")
            .description("Total number of content amplification processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("content_amplification_processing_duration")
            .description("Time taken to process content amplification events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"content-amplification", "viral-content-alerts", "content-distribution-events"},
        groupId = "content-monitoring-group",
        containerFactory = "criticalContentKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "content-amplification", fallbackMethod = "handleContentAmplificationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleContentAmplificationEvent(
            @Payload ContentAmplificationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("content-amp-%s-p%d-o%d", event.getContentId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getContentId(), event.getAmplificationType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing content amplification: contentId={}, type={}, reach={}, velocity={}",
                event.getContentId(), event.getAmplificationType(), event.getReach(), event.getAmplificationVelocity());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getAmplificationType()) {
                case VIRAL_SPREAD:
                    handleViralSpread(event, correlationId);
                    break;

                case ORGANIC_AMPLIFICATION:
                    handleOrganicAmplification(event, correlationId);
                    break;

                case PAID_PROMOTION:
                    handlePaidPromotion(event, correlationId);
                    break;

                case INFLUENCER_BOOST:
                    handleInfluencerBoost(event, correlationId);
                    break;

                case ALGORITHM_BOOST:
                    handleAlgorithmBoost(event, correlationId);
                    break;

                case SUSPICIOUS_AMPLIFICATION:
                    handleSuspiciousAmplification(event, correlationId);
                    break;

                case CROSS_PLATFORM_SPREAD:
                    handleCrossPlatformSpread(event, correlationId);
                    break;

                case AMPLIFICATION_THROTTLED:
                    handleAmplificationThrottled(event, correlationId);
                    break;

                case AMPLIFICATION_BLOCKED:
                    handleAmplificationBlocked(event, correlationId);
                    break;

                default:
                    log.warn("Unknown content amplification type: {}", event.getAmplificationType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logContentEvent("CONTENT_AMPLIFICATION_PROCESSED", event.getContentId(),
                Map.of("amplificationType", event.getAmplificationType(), "reach", event.getReach(),
                    "velocity", event.getAmplificationVelocity(), "platform", event.getPlatform(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process content amplification event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("content-amplification-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleContentAmplificationEventFallback(
            ContentAmplificationEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("content-amp-fallback-%s-p%d-o%d", event.getContentId(), partition, offset);

        log.error("Circuit breaker fallback triggered for content amplification: contentId={}, error={}",
            event.getContentId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("content-amplification-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Content Amplification Circuit Breaker Triggered",
                String.format("Content %s amplification processing failed: %s", event.getContentId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltContentAmplificationEvent(
            @Payload ContentAmplificationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-content-amp-%s-%d", event.getContentId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Content amplification permanently failed: contentId={}, topic={}, error={}",
            event.getContentId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logContentEvent("CONTENT_AMPLIFICATION_DLT_EVENT", event.getContentId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "amplificationType", event.getAmplificationType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Content Amplification Dead Letter Event",
                String.format("Content %s amplification sent to DLT: %s", event.getContentId(), exceptionMessage),
                Map.of("contentId", event.getContentId(), "topic", topic, "correlationId", correlationId)
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

    private void handleViralSpread(ContentAmplificationEvent event, String correlationId) {
        log.info("Viral content spread detected: contentId={}, reach={}, velocity={}/hour",
            event.getContentId(), event.getReach(), event.getAmplificationVelocity());

        contentMonitoringService.handleViralContent(event.getContentId(), event.getReach(), event.getAmplificationVelocity());

        if (event.getReach() > 1000000) { // Over 1M reach
            alertingService.sendHighPriorityAlert(
                "Viral Content Detected",
                String.format("Content %s has gone viral with %d reach and %d/hour velocity",
                    event.getContentId(), event.getReach(), event.getAmplificationVelocity()),
                correlationId
            );
        }

        // Monitor for potential policy violations in viral content
        contentMonitoringService.checkViralContentCompliance(event.getContentId());

        metricsService.recordViralContent(event.getContentId(), event.getReach());

        // Send to content analysis for trend monitoring
        kafkaTemplate.send("content-trend-analysis", Map.of(
            "contentId", event.getContentId(),
            "trendType", "VIRAL_SPREAD",
            "reach", event.getReach(),
            "velocity", event.getAmplificationVelocity(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleOrganicAmplification(ContentAmplificationEvent event, String correlationId) {
        log.info("Organic amplification detected: contentId={}, reach={}, engagementRate={}",
            event.getContentId(), event.getReach(), event.getEngagementRate());

        contentMonitoringService.trackOrganicGrowth(event.getContentId(), event.getReach(), event.getEngagementRate());

        // High organic engagement is typically positive
        if (event.getEngagementRate() > 0.15) { // 15% engagement rate
            alertingService.sendInfoAlert(
                "High Organic Engagement",
                String.format("Content %s showing strong organic performance (%.1f%% engagement)",
                    event.getContentId(), event.getEngagementRate() * 100),
                correlationId
            );
        }

        metricsService.recordOrganicAmplification(event.getContentId(), event.getEngagementRate());
    }

    private void handlePaidPromotion(ContentAmplificationEvent event, String correlationId) {
        log.info("Paid promotion amplification: contentId={}, reach={}, adSpend={}",
            event.getContentId(), event.getReach(), event.getAdSpend());

        contentMonitoringService.trackPaidPromotion(event.getContentId(), event.getReach(), event.getAdSpend());

        // Monitor ROI and compliance for paid content
        contentMonitoringService.validatePaidContentCompliance(event.getContentId(), event.getAdSpend());

        double roi = event.getReach() / Math.max(event.getAdSpend(), 1.0);
        metricsService.recordPaidPromotionROI(event.getContentId(), roi);

        // Low ROI alert
        if (roi < 10) { // Less than 10 reach per dollar
            alertingService.sendMediumPriorityAlert(
                "Low Paid Promotion ROI",
                String.format("Content %s has low ROI: %.2f reach per dollar",
                    event.getContentId(), roi),
                correlationId
            );
        }
    }

    private void handleInfluencerBoost(ContentAmplificationEvent event, String correlationId) {
        log.info("Influencer boost detected: contentId={}, influencer={}, reach={}",
            event.getContentId(), event.getInfluencerId(), event.getReach());

        contentMonitoringService.trackInfluencerAmplification(
            event.getContentId(), event.getInfluencerId(), event.getReach());

        // Monitor influencer compliance and disclosure
        contentMonitoringService.validateInfluencerDisclosure(event.getContentId(), event.getInfluencerId());

        metricsService.recordInfluencerBoost(event.getInfluencerId(), event.getReach());

        // Send to influencer analytics
        kafkaTemplate.send("influencer-analytics", Map.of(
            "influencerId", event.getInfluencerId(),
            "contentId", event.getContentId(),
            "reach", event.getReach(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleAlgorithmBoost(ContentAmplificationEvent event, String correlationId) {
        log.info("Algorithm boost applied: contentId={}, algorithm={}, boostFactor={}",
            event.getContentId(), event.getAlgorithmType(), event.getBoostFactor());

        contentMonitoringService.trackAlgorithmicAmplification(
            event.getContentId(), event.getAlgorithmType(), event.getBoostFactor());

        // Monitor algorithm fairness and bias
        contentMonitoringService.analyzeAlgorithmBias(event.getContentId(), event.getAlgorithmType());

        metricsService.recordAlgorithmBoost(event.getAlgorithmType(), event.getBoostFactor());
    }

    private void handleSuspiciousAmplification(ContentAmplificationEvent event, String correlationId) {
        log.warn("Suspicious amplification detected: contentId={}, suspicionScore={}, indicators={}",
            event.getContentId(), event.getSuspicionScore(), event.getSuspicionIndicators());

        contentMonitoringService.investigateSuspiciousAmplification(
            event.getContentId(), event.getSuspicionScore(), event.getSuspicionIndicators());

        alertingService.sendHighPriorityAlert(
            "Suspicious Content Amplification",
            String.format("Content %s showing suspicious amplification patterns (score: %.2f)",
                event.getContentId(), event.getSuspicionScore()),
            correlationId
        );

        // Potentially throttle or block suspicious content
        if (event.getSuspicionScore() > 0.8) {
            contentMonitoringService.throttleContent(event.getContentId(), "HIGH_SUSPICION");
        }

        metricsService.recordSuspiciousAmplification(event.getContentId(), event.getSuspicionScore());

        // Send to fraud detection
        kafkaTemplate.send("fraud-detection-events", Map.of(
            "eventType", "SUSPICIOUS_CONTENT_AMPLIFICATION",
            "contentId", event.getContentId(),
            "suspicionScore", event.getSuspicionScore(),
            "indicators", event.getSuspicionIndicators(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleCrossPlatformSpread(ContentAmplificationEvent event, String correlationId) {
        log.info("Cross-platform spread detected: contentId={}, platforms={}, totalReach={}",
            event.getContentId(), event.getPlatforms(), event.getTotalReach());

        contentMonitoringService.trackCrossPlatformSpread(
            event.getContentId(), event.getPlatforms(), event.getTotalReach());

        // Monitor for coordinated inauthentic behavior across platforms
        contentMonitoringService.analyzeCrossPlatformCoordination(event.getContentId(), event.getPlatforms());

        metricsService.recordCrossPlatformSpread(event.getContentId(), event.getPlatforms().size());
    }

    private void handleAmplificationThrottled(ContentAmplificationEvent event, String correlationId) {
        log.info("Content amplification throttled: contentId={}, reason={}, throttleLevel={}",
            event.getContentId(), event.getThrottleReason(), event.getThrottleLevel());

        contentMonitoringService.handleAmplificationThrottling(
            event.getContentId(), event.getThrottleReason(), event.getThrottleLevel());

        alertingService.sendMediumPriorityAlert(
            "Content Amplification Throttled",
            String.format("Content %s amplification throttled: %s (level: %s)",
                event.getContentId(), event.getThrottleReason(), event.getThrottleLevel()),
            correlationId
        );

        metricsService.recordAmplificationThrottled(event.getContentId(), event.getThrottleLevel());
    }

    private void handleAmplificationBlocked(ContentAmplificationEvent event, String correlationId) {
        log.warn("Content amplification blocked: contentId={}, reason={}, blockDuration={}",
            event.getContentId(), event.getBlockReason(), event.getBlockDurationHours());

        contentMonitoringService.handleAmplificationBlocking(
            event.getContentId(), event.getBlockReason(), event.getBlockDurationHours());

        alertingService.sendHighPriorityAlert(
            "Content Amplification Blocked",
            String.format("Content %s amplification blocked: %s (duration: %d hours)",
                event.getContentId(), event.getBlockReason(), event.getBlockDurationHours()),
            correlationId
        );

        metricsService.recordAmplificationBlocked(event.getContentId(), event.getBlockReason());

        // Send to compliance monitoring
        kafkaTemplate.send("content-compliance-events", Map.of(
            "eventType", "AMPLIFICATION_BLOCKED",
            "contentId", event.getContentId(),
            "blockReason", event.getBlockReason(),
            "duration", event.getBlockDurationHours(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }
}