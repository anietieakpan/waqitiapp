package com.waqiti.analytics.events.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.analytics.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for chargeback analytics events
 * Analyzes chargeback patterns for fraud detection and merchant risk assessment
 * 
 * Critical for: Chargeback pattern analysis, merchant risk scoring, fraud prevention
 * SLA: Must process chargeback analytics within 20 seconds for trend analysis
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChargebackAnalyticsConsumer {

    private final ChargebackAnalyticsService chargebackAnalyticsService;
    private final FraudPatternAnalysisService fraudPatternAnalysisService;
    private final MerchantAnalyticsService merchantAnalyticsService;
    private final AnalyticsNotificationService analyticsNotificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // Metrics
    private final Counter chargebackAnalyticsCounter = Counter.builder("chargeback_analytics_processed_total")
            .description("Total number of chargeback analytics events processed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("chargeback_analytics_processing_duration")
            .description("Time taken to process chargeback analytics events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"chargeback-analytics"},
        groupId = "analytics-service-chargeback-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "chargeback-analytics-processor", fallbackMethod = "handleChargebackAnalyticsFailure")
    @Retry(name = "chargeback-analytics-processor")
    public void processChargebackAnalytics(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();
        
        log.info("Processing chargeback analytics: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Chargeback analytics event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate chargeback data
            ChargebackAnalyticsData analyticsData = extractChargebackData(event.getPayload());
            validateChargebackData(analyticsData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process chargeback analytics
            processChargebackData(analyticsData, event);

            // Record successful processing metrics
            chargebackAnalyticsCounter.increment();
            
            // Audit the analytics processing
            auditChargebackAnalyticsProcessing(analyticsData, event, "SUCCESS");

            log.info("Successfully processed chargeback analytics: {} for merchant: {} - amount: {} reason: {}", 
                    eventId, analyticsData.getMerchantId(), 
                    analyticsData.getChargebackAmount(), analyticsData.getReasonCode());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("Invalid chargeback analytics data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process chargeback analytics: {}", eventId, e);
            auditChargebackAnalyticsProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("Chargeback analytics processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        Instant processedTime = processedEventIds.get(eventId);
        if (processedTime != null) {
            if (ChronoUnit.HOURS.between(processedTime, Instant.now()) < IDEMPOTENCY_TTL_HOURS) {
                return true;
            } else {
                processedEventIds.remove(eventId);
            }
        }
        return false;
    }

    private void markEventAsProcessed(String eventId) {
        processedEventIds.put(eventId, Instant.now());
    }

    private void cleanupIdempotencyCache() {
        Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
        processedEventIds.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private ChargebackAnalyticsData extractChargebackData(Map<String, Object> payload) {
        return ChargebackAnalyticsData.builder()
                .chargebackId(extractString(payload, "chargebackId"))
                .transactionId(extractString(payload, "transactionId"))
                .merchantId(extractString(payload, "merchantId"))
                .merchantName(extractString(payload, "merchantName"))
                .accountId(extractString(payload, "accountId"))
                .cardNumber(extractString(payload, "cardNumber"))
                .chargebackAmount(extractBigDecimal(payload, "chargebackAmount"))
                .originalAmount(extractBigDecimal(payload, "originalAmount"))
                .currency(extractString(payload, "currency"))
                .reasonCode(extractString(payload, "reasonCode"))
                .reasonDescription(extractString(payload, "reasonDescription"))
                .chargebackType(extractString(payload, "chargebackType"))
                .issuingBank(extractString(payload, "issuingBank"))
                .acquiringBank(extractString(payload, "acquiringBank"))
                .transactionDate(extractInstant(payload, "transactionDate"))
                .chargebackDate(extractInstant(payload, "chargebackDate"))
                .dueDate(extractInstant(payload, "dueDate"))
                .status(extractString(payload, "status"))
                .merchantCategory(extractString(payload, "merchantCategory"))
                .customerSegment(extractString(payload, "customerSegment"))
                .transactionMetadata(extractMap(payload, "transactionMetadata"))
                .build();
    }

    private void validateChargebackData(ChargebackAnalyticsData analyticsData) {
        if (analyticsData.getChargebackId() == null || analyticsData.getChargebackId().trim().isEmpty()) {
            throw new IllegalArgumentException("Chargeback ID is required");
        }
        
        if (analyticsData.getMerchantId() == null || analyticsData.getMerchantId().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant ID is required");
        }
        
        if (analyticsData.getChargebackAmount() == null || analyticsData.getChargebackAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid chargeback amount is required");
        }
        
        if (analyticsData.getReasonCode() == null || analyticsData.getReasonCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Reason code is required");
        }
    }

    private void processChargebackData(ChargebackAnalyticsData analyticsData, GenericKafkaEvent event) {
        log.info("Processing chargeback analytics - ChargebackId: {}, Merchant: {}, Amount: {} {}, Reason: {}", 
                analyticsData.getChargebackId(), analyticsData.getMerchantId(),
                analyticsData.getChargebackAmount(), analyticsData.getCurrency(),
                analyticsData.getReasonCode());

        try {
            // Analyze chargeback patterns
            ChargebackPatternAnalysis patternAnalysis = analyzeChargebackPatterns(analyticsData);

            // Update merchant risk profile
            updateMerchantRiskProfile(analyticsData, patternAnalysis);

            // Analyze fraud indicators
            FraudIndicatorAnalysis fraudAnalysis = analyzeFraudIndicators(analyticsData);

            // Generate insights and recommendations
            ChargebackInsights insights = generateChargebackInsights(analyticsData, patternAnalysis, fraudAnalysis);

            // Update analytics dashboards
            updateAnalyticsDashboards(analyticsData, insights);

            // Send notifications if needed
            sendAnalyticsNotifications(analyticsData, insights);

            log.info("Chargeback analytics processed - Insights: {}, Risk Level: {}", 
                    insights.getInsightCount(), insights.getMerchantRiskLevel());

        } catch (Exception e) {
            log.error("Failed to process chargeback analytics for chargeback: {}", analyticsData.getChargebackId(), e);
            throw new RuntimeException("Chargeback analytics processing failed", e);
        }
    }

    private ChargebackPatternAnalysis analyzeChargebackPatterns(ChargebackAnalyticsData analyticsData) {
        return chargebackAnalyticsService.analyzeChargebackPatterns(
                analyticsData.getMerchantId(),
                analyticsData.getReasonCode(),
                analyticsData.getChargebackAmount(),
                analyticsData.getMerchantCategory(),
                analyticsData.getTransactionDate(),
                analyticsData.getChargebackDate()
        );
    }

    private void updateMerchantRiskProfile(ChargebackAnalyticsData analyticsData, ChargebackPatternAnalysis patternAnalysis) {
        merchantAnalyticsService.updateMerchantRiskProfile(
                analyticsData.getMerchantId(),
                analyticsData.getChargebackAmount(),
                analyticsData.getReasonCode(),
                patternAnalysis.getRiskScore()
        );
    }

    private FraudIndicatorAnalysis analyzeFraudIndicators(ChargebackAnalyticsData analyticsData) {
        return fraudPatternAnalysisService.analyzeFraudIndicators(
                analyticsData.getChargebackId(),
                analyticsData.getReasonCode(),
                analyticsData.getTransactionMetadata(),
                analyticsData.getCustomerSegment()
        );
    }

    private ChargebackInsights generateChargebackInsights(ChargebackAnalyticsData analyticsData,
                                                         ChargebackPatternAnalysis patternAnalysis,
                                                         FraudIndicatorAnalysis fraudAnalysis) {
        return chargebackAnalyticsService.generateInsights(
                analyticsData,
                patternAnalysis,
                fraudAnalysis
        );
    }

    private void updateAnalyticsDashboards(ChargebackAnalyticsData analyticsData, ChargebackInsights insights) {
        chargebackAnalyticsService.updateChargebackDashboard(
                analyticsData.getMerchantId(),
                analyticsData.getReasonCode(),
                analyticsData.getChargebackAmount(),
                insights.getMerchantRiskLevel()
        );
    }

    private void sendAnalyticsNotifications(ChargebackAnalyticsData analyticsData, ChargebackInsights insights) {
        if ("HIGH".equals(insights.getMerchantRiskLevel()) || "CRITICAL".equals(insights.getMerchantRiskLevel())) {
            analyticsNotificationService.sendHighRiskMerchantAlert(
                    "High Risk Merchant Chargeback Pattern",
                    analyticsData,
                    insights
            );
        }

        if (insights.getFraudProbability() > 0.7) {
            analyticsNotificationService.sendFraudPatternAlert(
                    "Potential Fraud Pattern in Chargeback",
                    analyticsData,
                    insights
            );
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("Chargeback analytics validation failed for event: {}", event.getEventId(), e);
        
        auditService.auditFinancialEvent(
                "CHARGEBACK_ANALYTICS_VALIDATION_ERROR",
                null,
                "Chargeback analytics validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditChargebackAnalyticsProcessing(ChargebackAnalyticsData analyticsData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditFinancialEvent(
                    "CHARGEBACK_ANALYTICS_PROCESSED",
                    analyticsData != null ? analyticsData.getAccountId() : null,
                    String.format("Chargeback analytics processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "chargebackId", analyticsData != null ? analyticsData.getChargebackId() : "unknown",
                            "merchantId", analyticsData != null ? analyticsData.getMerchantId() : "unknown",
                            "chargebackAmount", analyticsData != null ? analyticsData.getChargebackAmount().toString() : "0",
                            "reasonCode", analyticsData != null ? analyticsData.getReasonCode() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit chargeback analytics processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Chargeback analytics event sent to DLT - EventId: {}", event.getEventId());
    }

    public void handleChargebackAnalyticsFailure(GenericKafkaEvent event, String topic, int partition,
                                               long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for chargeback analytics processing - EventId: {}", event.getEventId(), e);
        acknowledgment.acknowledge();
    }

    // Helper methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }

    // Data classes
    @lombok.Data
    @lombok.Builder
    public static class ChargebackAnalyticsData {
        private String chargebackId;
        private String transactionId;
        private String merchantId;
        private String merchantName;
        private String accountId;
        private String cardNumber;
        private BigDecimal chargebackAmount;
        private BigDecimal originalAmount;
        private String currency;
        private String reasonCode;
        private String reasonDescription;
        private String chargebackType;
        private String issuingBank;
        private String acquiringBank;
        private Instant transactionDate;
        private Instant chargebackDate;
        private Instant dueDate;
        private String status;
        private String merchantCategory;
        private String customerSegment;
        private Map<String, Object> transactionMetadata;
    }

    @lombok.Data
    @lombok.Builder
    public static class ChargebackPatternAnalysis {
        private Double riskScore;
        private String patternType;
        private Integer frequencyScore;
    }

    @lombok.Data
    @lombok.Builder
    public static class FraudIndicatorAnalysis {
        private Double fraudProbability;
        private String fraudType;
        private java.util.List<String> indicators;
    }

    @lombok.Data
    @lombok.Builder
    public static class ChargebackInsights {
        private Integer insightCount;
        private String merchantRiskLevel;
        private Double fraudProbability;
        private java.util.List<String> recommendations;
    }
}