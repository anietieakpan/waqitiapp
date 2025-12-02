package com.waqiti.analytics.events.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.model.*;
import com.waqiti.analytics.repository.CardDeclineAnalyticsRepository;
import com.waqiti.analytics.service.*;
import com.waqiti.common.audit.AuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for card decline analytics events
 * Analyzes card decline patterns and generates insights with enterprise patterns
 * 
 * Critical for: Business intelligence, fraud detection, customer experience optimization
 * SLA: Must process decline analytics within 30 seconds for real-time insights
 */
@Component
@Slf4j
public class CardDeclineAnalyticsConsumer {

    private final CardDeclineAnalyticsRepository analyticsRepository;
    private final DeclinePatternAnalysisService patternAnalysisService;
    private final MerchantAnalyticsService merchantAnalyticsService;
    private final CustomerSegmentAnalysisService customerSegmentService;
    private final FraudAnalyticsService fraudAnalyticsService;
    private final BusinessIntelligenceService biService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter declineAnalyticsCounter;
    private final Counter highVolumeDeclineCounter;
    private final Counter fraudDeclinePatternCounter;
    private final Timer analyticsProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);
    private static final int HIGH_DECLINE_VOLUME_THRESHOLD = 100;

    public CardDeclineAnalyticsConsumer(
            CardDeclineAnalyticsRepository analyticsRepository,
            DeclinePatternAnalysisService patternAnalysisService,
            MerchantAnalyticsService merchantAnalyticsService,
            CustomerSegmentAnalysisService customerSegmentService,
            FraudAnalyticsService fraudAnalyticsService,
            BusinessIntelligenceService biService,
            NotificationService notificationService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.analyticsRepository = analyticsRepository;
        this.patternAnalysisService = patternAnalysisService;
        this.merchantAnalyticsService = merchantAnalyticsService;
        this.customerSegmentService = customerSegmentService;
        this.fraudAnalyticsService = fraudAnalyticsService;
        this.biService = biService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.declineAnalyticsCounter = Counter.builder("card.decline.analytics.events")
            .description("Count of card decline analytics events")
            .register(meterRegistry);
        
        this.highVolumeDeclineCounter = Counter.builder("card.decline.analytics.high.volume.events")
            .description("Count of high-volume decline analytics")
            .register(meterRegistry);
        
        this.fraudDeclinePatternCounter = Counter.builder("card.decline.analytics.fraud.pattern.events")
            .description("Count of fraud pattern decline analytics")
            .register(meterRegistry);
        
        this.analyticsProcessingTimer = Timer.builder("card.decline.analytics.processing.duration")
            .description("Time taken to process decline analytics events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "analytics.card.decline",
        groupId = "card-decline-analytics-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "analytics-card-decline-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleCardDeclineAnalyticsEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received card decline analytics event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String declineReason = (String) eventData.get("declineReason");
            String merchantCategory = (String) eventData.get("merchantCategory");
            BigDecimal transactionAmount = new BigDecimal(eventData.get("transactionAmount").toString());
            Boolean isInternational = (Boolean) eventData.getOrDefault("isInternational", false);
            String customerSegment = (String) eventData.get("customerSegment");
            Integer timeOfDay = (Integer) eventData.get("timeOfDay");
            String dayOfWeek = (String) eventData.get("dayOfWeek");
            Integer riskFlags = (Integer) eventData.getOrDefault("riskFlags", 0);
            String processingNetwork = (String) eventData.get("processingNetwork");
            String merchantId = (String) eventData.get("merchantId");
            String cardId = (String) eventData.get("cardId");
            String userId = (String) eventData.get("userId");
            String transactionId = (String) eventData.get("transactionId");
            
            String correlationId = String.format("card-decline-analytics-%s-%d", 
                transactionId != null ? transactionId : "unknown", System.currentTimeMillis());
            
            log.info("Processing card decline analytics - reason: {}, merchant: {}, amount: {}, correlationId: {}", 
                declineReason, merchantCategory, transactionAmount, correlationId);
            
            declineAnalyticsCounter.increment();
            
            processCardDeclineAnalytics(declineReason, merchantCategory, transactionAmount, isInternational,
                customerSegment, timeOfDay, dayOfWeek, riskFlags, processingNetwork, merchantId,
                cardId, userId, transactionId, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(analyticsProcessingTimer);
            
            log.info("Successfully processed card decline analytics event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process card decline analytics event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Card decline analytics processing failed", e);
        }
    }

    @CircuitBreaker(name = "analytics", fallbackMethod = "processCardDeclineAnalyticsFallback")
    @Retry(name = "analytics")
    private void processCardDeclineAnalytics(
            String declineReason,
            String merchantCategory,
            BigDecimal transactionAmount,
            Boolean isInternational,
            String customerSegment,
            Integer timeOfDay,
            String dayOfWeek,
            Integer riskFlags,
            String processingNetwork,
            String merchantId,
            String cardId,
            String userId,
            String transactionId,
            Map<String, Object> eventData,
            String correlationId) {
        
        // Create decline analytics record
        CardDeclineAnalytics analytics = CardDeclineAnalytics.builder()
            .id(java.util.UUID.randomUUID().toString())
            .transactionId(transactionId)
            .cardId(cardId)
            .userId(userId)
            .merchantId(merchantId)
            .declineReason(DeclineReason.fromString(declineReason))
            .merchantCategory(merchantCategory)
            .transactionAmount(transactionAmount)
            .isInternational(isInternational)
            .customerSegment(CustomerSegment.fromString(customerSegment))
            .timeOfDay(timeOfDay)
            .dayOfWeek(DayOfWeek.fromString(dayOfWeek))
            .riskFlagsCount(riskFlags)
            .processingNetwork(processingNetwork)
            .eventTimestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        analyticsRepository.save(analytics);
        
        // Perform pattern analysis
        analyzeDeclinePatterns(analytics, correlationId);
        
        // Analyze merchant patterns
        analyzeMerchantDeclines(analytics, correlationId);
        
        // Analyze customer segment patterns
        analyzeCustomerSegmentDeclines(analytics, correlationId);
        
        // Detect fraud patterns
        detectFraudPatterns(analytics, correlationId);
        
        // Generate business intelligence insights
        generateBusinessInsights(analytics, correlationId);
        
        // Check for anomalies and alerts
        checkForAnomalies(analytics, correlationId);
        
        // Update real-time dashboards
        updateRealTimeDashboards(analytics, correlationId);
        
        // Publish analytics insights
        publishAnalyticsInsights(analytics, correlationId);
        
        // Audit the analytics processing
        auditService.logAnalyticsEvent(
            "CARD_DECLINE_ANALYTICS_PROCESSED",
            analytics.getId(),
            Map.of(
                "transactionId", transactionId,
                "cardId", cardId,
                "userId", userId,
                "merchantId", merchantId,
                "declineReason", declineReason,
                "merchantCategory", merchantCategory,
                "transactionAmount", transactionAmount,
                "customerSegment", customerSegment,
                "riskFlagsCount", riskFlags,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.info("Card decline analytics processed - reason: {}, merchant: {}, correlationId: {}", 
            declineReason, merchantCategory, correlationId);
    }

    private void analyzeDeclinePatterns(CardDeclineAnalytics analytics, String correlationId) {
        // Analyze temporal patterns
        DeclinePatternAnalysis patternAnalysis = patternAnalysisService.analyzePatterns(
            analytics.getDeclineReason(),
            analytics.getTimeOfDay(),
            analytics.getDayOfWeek(),
            analytics.getEventTimestamp()
        );
        
        // Check for unusual patterns
        if (patternAnalysis.isAnomalousPattern()) {
            kafkaTemplate.send("decline-pattern-anomalies", Map.of(
                "declineReason", analytics.getDeclineReason().toString(),
                "timeOfDay", analytics.getTimeOfDay(),
                "dayOfWeek", analytics.getDayOfWeek().toString(),
                "anomalyType", patternAnalysis.getAnomalyType(),
                "severity", patternAnalysis.getSeverity(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        // Update pattern statistics
        patternAnalysisService.updatePatternStatistics(analytics);
        
        log.debug("Decline pattern analysis completed - correlationId: {}, anomalous: {}", 
            correlationId, patternAnalysis.isAnomalousPattern());
    }

    private void analyzeMerchantDeclines(CardDeclineAnalytics analytics, String correlationId) {
        if (analytics.getMerchantId() != null) {
            // Analyze merchant-specific decline patterns
            MerchantDeclineAnalysis merchantAnalysis = merchantAnalyticsService.analyzeMerchantDeclines(
                analytics.getMerchantId(),
                analytics.getDeclineReason(),
                analytics.getTransactionAmount(),
                analytics.getMerchantCategory()
            );
            
            // Check for high decline rates
            if (merchantAnalysis.hasHighDeclineRate()) {
                kafkaTemplate.send("merchant-decline-alerts", Map.of(
                    "merchantId", analytics.getMerchantId(),
                    "merchantCategory", analytics.getMerchantCategory(),
                    "declineRate", merchantAnalysis.getDeclineRate(),
                    "threshold", merchantAnalysis.getThreshold(),
                    "alertType", "HIGH_DECLINE_RATE",
                    "correlationId", correlationId,
                    "timestamp", Instant.now().toString()
                ));
                
                // Notify merchant operations
                notificationService.sendMerchantOperationsAlert(
                    "High Merchant Decline Rate",
                    String.format("Merchant %s has high decline rate: %.2f%%", 
                        analytics.getMerchantId(), merchantAnalysis.getDeclineRate() * 100),
                    Map.of(
                        "merchantId", analytics.getMerchantId(),
                        "declineRate", merchantAnalysis.getDeclineRate(),
                        "correlationId", correlationId
                    )
                );
            }
            
            // Update merchant analytics
            merchantAnalyticsService.updateMerchantMetrics(analytics);
        }
    }

    private void analyzeCustomerSegmentDeclines(CardDeclineAnalytics analytics, String correlationId) {
        // Analyze customer segment patterns
        CustomerSegmentDeclineAnalysis segmentAnalysis = customerSegmentService.analyzeSegmentDeclines(
            analytics.getCustomerSegment(),
            analytics.getDeclineReason(),
            analytics.getTransactionAmount(),
            analytics.getIsInternational()
        );
        
        // Check for segment-specific issues
        if (segmentAnalysis.hasAnomalousDeclinePattern()) {
            kafkaTemplate.send("customer-segment-decline-alerts", Map.of(
                "customerSegment", analytics.getCustomerSegment().toString(),
                "declineReason", analytics.getDeclineReason().toString(),
                "anomalyScore", segmentAnalysis.getAnomalyScore(),
                "expectedRate", segmentAnalysis.getExpectedDeclineRate(),
                "actualRate", segmentAnalysis.getActualDeclineRate(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        // Update segment analytics
        customerSegmentService.updateSegmentMetrics(analytics);
    }

    private void detectFraudPatterns(CardDeclineAnalytics analytics, String correlationId) {
        // Analyze fraud indicators
        if (analytics.getRiskFlagsCount() > 0) {
            FraudPatternAnalysis fraudAnalysis = fraudAnalyticsService.analyzeFraudPatterns(
                analytics.getCardId(),
                analytics.getUserId(),
                analytics.getDeclineReason(),
                analytics.getRiskFlagsCount(),
                analytics.getIsInternational()
            );
            
            if (fraudAnalysis.isSuspiciousPattern()) {
                fraudDeclinePatternCounter.increment();
                
                kafkaTemplate.send("fraud-decline-pattern-alerts", Map.of(
                    "cardId", analytics.getCardId(),
                    "userId", analytics.getUserId(),
                    "fraudScore", fraudAnalysis.getFraudScore(),
                    "patternType", fraudAnalysis.getPatternType(),
                    "riskLevel", fraudAnalysis.getRiskLevel(),
                    "declineReason", analytics.getDeclineReason().toString(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now().toString()
                ));
                
                // Send fraud alert
                notificationService.sendFraudAnalyticsAlert(
                    "Suspicious Decline Pattern Detected",
                    String.format("Suspicious decline pattern for card %s: %s", 
                        analytics.getCardId(), fraudAnalysis.getPatternType()),
                    Map.of(
                        "cardId", analytics.getCardId(),
                        "fraudScore", fraudAnalysis.getFraudScore(),
                        "correlationId", correlationId
                    )
                );
            }
            
            // Update fraud analytics models
            fraudAnalyticsService.updateFraudModels(analytics);
        }
    }

    private void generateBusinessInsights(CardDeclineAnalytics analytics, String correlationId) {
        // Generate business intelligence insights
        BusinessInsight insight = biService.generateDeclineInsight(analytics);
        
        if (insight != null) {
            kafkaTemplate.send("business-insights", Map.of(
                "insightType", "CARD_DECLINE_ANALYSIS",
                "category", insight.getCategory(),
                "impact", insight.getImpact(),
                "recommendation", insight.getRecommendation(),
                "priority", insight.getPriority(),
                "merchantCategory", analytics.getMerchantCategory(),
                "declineReason", analytics.getDeclineReason().toString(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        // Update business metrics
        biService.updateDeclineMetrics(analytics);
    }

    private void checkForAnomalies(CardDeclineAnalytics analytics, String correlationId) {
        // Check for volume anomalies
        int recentDeclineCount = analyticsRepository.countRecentDeclines(
            analytics.getDeclineReason(),
            LocalDateTime.now().minusHours(1)
        );
        
        if (recentDeclineCount > HIGH_DECLINE_VOLUME_THRESHOLD) {
            highVolumeDeclineCounter.increment();
            
            kafkaTemplate.send("high-volume-decline-alerts", Map.of(
                "declineReason", analytics.getDeclineReason().toString(),
                "declineCount", recentDeclineCount,
                "threshold", HIGH_DECLINE_VOLUME_THRESHOLD,
                "timeWindow", "1_HOUR",
                "alertType", "HIGH_VOLUME_DECLINE",
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
            
            // Send operations alert
            notificationService.sendOperationsAlert(
                "High Volume Decline Alert",
                String.format("High volume of %s declines detected: %d in the last hour", 
                    analytics.getDeclineReason(), recentDeclineCount),
                Map.of(
                    "declineReason", analytics.getDeclineReason().toString(),
                    "declineCount", recentDeclineCount,
                    "correlationId", correlationId
                )
            );
        }
        
        // Check for geographic anomalies
        if (analytics.getIsInternational()) {
            checkGeographicAnomalies(analytics, correlationId);
        }
    }

    private void checkGeographicAnomalies(CardDeclineAnalytics analytics, String correlationId) {
        // Analyze international decline patterns
        GeographicDeclineAnalysis geoAnalysis = patternAnalysisService.analyzeGeographicPatterns(
            analytics.getDeclineReason(),
            analytics.getIsInternational(),
            analytics.getProcessingNetwork()
        );
        
        if (geoAnalysis.isAnomalous()) {
            kafkaTemplate.send("geographic-decline-anomalies", Map.of(
                "declineReason", analytics.getDeclineReason().toString(),
                "processingNetwork", analytics.getProcessingNetwork(),
                "anomalyType", geoAnalysis.getAnomalyType(),
                "severity", geoAnalysis.getSeverity(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
    }

    private void updateRealTimeDashboards(CardDeclineAnalytics analytics, String correlationId) {
        // Update real-time analytics dashboards
        kafkaTemplate.send("realtime-dashboard-updates", Map.of(
            "dashboardType", "CARD_DECLINE_ANALYTICS",
            "metricType", "DECLINE_EVENT",
            "declineReason", analytics.getDeclineReason().toString(),
            "merchantCategory", analytics.getMerchantCategory(),
            "transactionAmount", analytics.getTransactionAmount(),
            "customerSegment", analytics.getCustomerSegment().toString(),
            "timeOfDay", analytics.getTimeOfDay(),
            "dayOfWeek", analytics.getDayOfWeek().toString(),
            "eventTimestamp", analytics.getEventTimestamp().toInstant(ZoneOffset.UTC).toString(),
            "correlationId", correlationId
        ));
    }

    private void publishAnalyticsInsights(CardDeclineAnalytics analytics, String correlationId) {
        // Publish processed analytics for downstream consumers
        kafkaTemplate.send("decline-analytics-insights", Map.of(
            "analyticsId", analytics.getId(),
            "transactionId", analytics.getTransactionId(),
            "declineReason", analytics.getDeclineReason().toString(),
            "merchantCategory", analytics.getMerchantCategory(),
            "customerSegment", analytics.getCustomerSegment().toString(),
            "transactionAmount", analytics.getTransactionAmount(),
            "isInternational", analytics.getIsInternational(),
            "riskFlagsCount", analytics.getRiskFlagsCount(),
            "processingNetwork", analytics.getProcessingNetwork(),
            "timeOfDay", analytics.getTimeOfDay(),
            "dayOfWeek", analytics.getDayOfWeek().toString(),
            "eventType", "DECLINE_ANALYTICS_PROCESSED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void processCardDeclineAnalyticsFallback(
            String declineReason,
            String merchantCategory,
            BigDecimal transactionAmount,
            Boolean isInternational,
            String customerSegment,
            Integer timeOfDay,
            String dayOfWeek,
            Integer riskFlags,
            String processingNetwork,
            String merchantId,
            String cardId,
            String userId,
            String transactionId,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for card decline analytics - transactionId: {}, correlationId: {}, error: {}", 
            transactionId, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("transactionId", transactionId);
        fallbackEvent.put("cardId", cardId);
        fallbackEvent.put("userId", userId);
        fallbackEvent.put("merchantId", merchantId);
        fallbackEvent.put("declineReason", declineReason);
        fallbackEvent.put("merchantCategory", merchantCategory);
        fallbackEvent.put("transactionAmount", transactionAmount);
        fallbackEvent.put("isInternational", isInternational);
        fallbackEvent.put("customerSegment", customerSegment);
        fallbackEvent.put("timeOfDay", timeOfDay);
        fallbackEvent.put("dayOfWeek", dayOfWeek);
        fallbackEvent.put("riskFlags", riskFlags);
        fallbackEvent.put("processingNetwork", processingNetwork);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("card-decline-analytics-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Card decline analytics message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
            topic, partition, offset, exceptionMessage);
        
        try {
            Map<String, Object> dltEvent = Map.of(
                "originalTopic", topic,
                "partition", partition,
                "offset", offset,
                "message", message,
                "error", exceptionMessage,
                "timestamp", Instant.now().toString(),
                "dltReason", "MAX_RETRIES_EXCEEDED"
            );
            
            kafkaTemplate.send("card-decline-analytics-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Card Decline Analytics Processing Failed",
                String.format("CRITICAL: Failed to process decline analytics after max retries. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process DLT message: {}", e.getMessage(), e);
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEvents.containsKey(eventId);
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, String.valueOf(System.currentTimeMillis()));
        
        // Cleanup old entries
        processedEvents.entrySet().removeIf(entry -> {
            long timestamp = Long.parseLong(entry.getValue());
            return System.currentTimeMillis() - timestamp > IDEMPOTENCY_TTL_MS;
        });
    }
}