package com.waqiti.analytics.events.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.model.*;
import com.waqiti.analytics.repository.SettlementFailureAnalyticsRepository;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for settlement failure analytics events
 * Analyzes settlement failure patterns with enterprise patterns
 * 
 * Critical for: Financial operations, merchant relations, business intelligence
 * SLA: Must process settlement analytics within 30 seconds for real-time insights
 */
@Component
@Slf4j
public class SettlementFailureAnalyticsConsumer {

    private final SettlementFailureAnalyticsRepository analyticsRepository;
    private final SettlementPatternAnalysisService patternAnalysisService;
    private final MerchantAnalyticsService merchantAnalyticsService;
    private final FinancialAnalyticsService financialAnalyticsService;
    private final BusinessIntelligenceService biService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter settlementFailureAnalyticsCounter;
    private final Counter highValueSettlementFailureCounter;
    private final Counter criticalMerchantFailureCounter;
    private final Timer analyticsProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("25000.00");
    private static final double CRITICAL_FAILURE_RATE = 0.15; // 15%

    public SettlementFailureAnalyticsConsumer(
            SettlementFailureAnalyticsRepository analyticsRepository,
            SettlementPatternAnalysisService patternAnalysisService,
            MerchantAnalyticsService merchantAnalyticsService,
            FinancialAnalyticsService financialAnalyticsService,
            BusinessIntelligenceService biService,
            NotificationService notificationService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.analyticsRepository = analyticsRepository;
        this.patternAnalysisService = patternAnalysisService;
        this.merchantAnalyticsService = merchantAnalyticsService;
        this.financialAnalyticsService = financialAnalyticsService;
        this.biService = biService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.settlementFailureAnalyticsCounter = Counter.builder("settlement.failure.analytics.events")
            .description("Count of settlement failure analytics events")
            .register(meterRegistry);
        
        this.highValueSettlementFailureCounter = Counter.builder("settlement.failure.analytics.high.value.events")
            .description("Count of high-value settlement failure analytics")
            .register(meterRegistry);
        
        this.criticalMerchantFailureCounter = Counter.builder("settlement.failure.analytics.critical.merchant.events")
            .description("Count of critical merchant settlement failures")
            .register(meterRegistry);
        
        this.analyticsProcessingTimer = Timer.builder("settlement.failure.analytics.processing.duration")
            .description("Time taken to process settlement failure analytics")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "analytics.settlement.failure",
        groupId = "settlement-failure-analytics-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "analytics-settlement-failure-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleSettlementFailureAnalyticsEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received settlement failure analytics event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String settlementId = (String) eventData.get("settlementId");
            String merchantId = (String) eventData.get("merchantId");
            String merchantName = (String) eventData.get("merchantName");
            BigDecimal settlementAmount = new BigDecimal(eventData.get("settlementAmount").toString());
            String currency = (String) eventData.get("currency");
            String failureReason = (String) eventData.get("failureReason");
            String failureCode = (String) eventData.get("failureCode");
            String bankResponseCode = (String) eventData.get("bankResponseCode");
            String settlementMethod = (String) eventData.get("settlementMethod");
            String merchantCategory = (String) eventData.get("merchantCategory");
            String merchantTier = (String) eventData.get("merchantTier");
            Integer retryAttempt = (Integer) eventData.getOrDefault("retryAttempt", 0);
            Boolean isRecurring = (Boolean) eventData.getOrDefault("isRecurring", false);
            
            String correlationId = String.format("settlement-failure-analytics-%s-%d", 
                settlementId, System.currentTimeMillis());
            
            log.error("Processing settlement failure analytics - settlementId: {}, merchant: {}, amount: {}, reason: {}, correlationId: {}", 
                settlementId, merchantName, settlementAmount, failureReason, correlationId);
            
            settlementFailureAnalyticsCounter.increment();
            
            processSettlementFailureAnalytics(settlementId, merchantId, merchantName, settlementAmount,
                currency, failureReason, failureCode, bankResponseCode, settlementMethod,
                merchantCategory, merchantTier, retryAttempt, isRecurring, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(analyticsProcessingTimer);
            
            log.info("Successfully processed settlement failure analytics event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process settlement failure analytics event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Settlement failure analytics processing failed", e);
        }
    }

    @CircuitBreaker(name = "analytics", fallbackMethod = "processSettlementFailureAnalyticsFallback")
    @Retry(name = "analytics")
    private void processSettlementFailureAnalytics(
            String settlementId,
            String merchantId,
            String merchantName,
            BigDecimal settlementAmount,
            String currency,
            String failureReason,
            String failureCode,
            String bankResponseCode,
            String settlementMethod,
            String merchantCategory,
            String merchantTier,
            Integer retryAttempt,
            Boolean isRecurring,
            Map<String, Object> eventData,
            String correlationId) {
        
        // Create settlement failure analytics record
        SettlementFailureAnalytics analytics = SettlementFailureAnalytics.builder()
            .id(java.util.UUID.randomUUID().toString())
            .settlementId(settlementId)
            .merchantId(merchantId)
            .merchantName(merchantName)
            .settlementAmount(settlementAmount)
            .currency(currency)
            .failureReason(FailureReason.fromString(failureReason))
            .failureCode(failureCode)
            .bankResponseCode(bankResponseCode)
            .settlementMethod(settlementMethod)
            .merchantCategory(merchantCategory)
            .merchantTier(MerchantTier.fromString(merchantTier))
            .retryAttempt(retryAttempt)
            .isRecurring(isRecurring)
            .eventTimestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        analyticsRepository.save(analytics);
        
        // Analyze settlement patterns
        analyzeSettlementPatterns(analytics, correlationId);
        
        // Analyze merchant impact
        analyzeMerchantImpact(analytics, correlationId);
        
        // Analyze financial impact
        analyzeFinancialImpact(analytics, correlationId);
        
        // Check for high-value failures
        if (settlementAmount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            handleHighValueSettlementFailure(analytics, correlationId);
        }
        
        // Detect critical patterns
        detectCriticalPatterns(analytics, correlationId);
        
        // Generate business insights
        generateBusinessInsights(analytics, correlationId);
        
        // Update dashboards
        updateRealTimeDashboards(analytics, correlationId);
        
        // Publish insights
        publishAnalyticsInsights(analytics, correlationId);
        
        // Audit the analytics processing
        auditService.logAnalyticsEvent(
            "SETTLEMENT_FAILURE_ANALYTICS_PROCESSED",
            analytics.getId(),
            Map.of(
                "settlementId", settlementId,
                "merchantId", merchantId,
                "settlementAmount", settlementAmount,
                "currency", currency,
                "failureReason", failureReason,
                "failureCode", failureCode,
                "merchantCategory", merchantCategory,
                "retryAttempt", retryAttempt,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.error("Settlement failure analytics processed - settlementId: {}, merchant: {}, amount: {}, correlationId: {}", 
            settlementId, merchantName, settlementAmount, correlationId);
    }

    private void analyzeSettlementPatterns(SettlementFailureAnalytics analytics, String correlationId) {
        // Analyze temporal and pattern-based settlement failures
        SettlementPatternAnalysis patternAnalysis = patternAnalysisService.analyzeSettlementPatterns(
            analytics.getFailureReason(),
            analytics.getSettlementMethod(),
            analytics.getMerchantCategory(),
            analytics.getEventTimestamp()
        );
        
        if (patternAnalysis.isAnomalousPattern()) {
            kafkaTemplate.send("settlement-pattern-anomalies", Map.of(
                "failureReason", analytics.getFailureReason().toString(),
                "settlementMethod", analytics.getSettlementMethod(),
                "merchantCategory", analytics.getMerchantCategory(),
                "anomalyType", patternAnalysis.getAnomalyType(),
                "severity", patternAnalysis.getSeverity(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        // Update pattern statistics
        patternAnalysisService.updateSettlementPatternStatistics(analytics);
    }

    private void analyzeMerchantImpact(SettlementFailureAnalytics analytics, String correlationId) {
        // Analyze merchant-specific settlement failure patterns
        MerchantSettlementAnalysis merchantAnalysis = merchantAnalyticsService.analyzeMerchantSettlementFailures(
            analytics.getMerchantId(),
            analytics.getFailureReason(),
            analytics.getSettlementAmount(),
            analytics.getRetryAttempt()
        );
        
        // Check for high failure rates
        if (merchantAnalysis.getFailureRate() > CRITICAL_FAILURE_RATE) {
            criticalMerchantFailureCounter.increment();
            
            kafkaTemplate.send("critical-merchant-settlement-alerts", Map.of(
                "merchantId", analytics.getMerchantId(),
                "merchantName", analytics.getMerchantName(),
                "failureRate", merchantAnalysis.getFailureRate(),
                "threshold", CRITICAL_FAILURE_RATE,
                "settlementAmount", analytics.getSettlementAmount(),
                "currency", analytics.getCurrency(),
                "alertType", "CRITICAL_SETTLEMENT_FAILURE_RATE",
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
            
            // Notify merchant operations
            notificationService.sendMerchantOperationsAlert(
                "Critical Merchant Settlement Failure Rate",
                String.format("Merchant %s has critical settlement failure rate: %.2f%%", 
                    analytics.getMerchantName(), merchantAnalysis.getFailureRate() * 100),
                Map.of(
                    "merchantId", analytics.getMerchantId(),
                    "failureRate", merchantAnalysis.getFailureRate(),
                    "correlationId", correlationId
                )
            );
        }
        
        // Update merchant analytics
        merchantAnalyticsService.updateMerchantSettlementMetrics(analytics);
    }

    private void analyzeFinancialImpact(SettlementFailureAnalytics analytics, String correlationId) {
        // Calculate financial impact of settlement failures
        FinancialImpactAnalysis financialAnalysis = financialAnalyticsService.analyzeSettlementFailureImpact(
            analytics.getSettlementAmount(),
            analytics.getCurrency(),
            analytics.getFailureReason(),
            analytics.getMerchantTier()
        );
        
        // Update financial metrics
        financialAnalyticsService.updateSettlementFailureMetrics(analytics);
        
        // Send financial impact alerts for significant amounts
        if (financialAnalysis.isSignificantImpact()) {
            kafkaTemplate.send("financial-impact-alerts", Map.of(
                "impactType", "SETTLEMENT_FAILURE",
                "amount", analytics.getSettlementAmount(),
                "currency", analytics.getCurrency(),
                "impactScore", financialAnalysis.getImpactScore(),
                "merchantId", analytics.getMerchantId(),
                "failureReason", analytics.getFailureReason().toString(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
    }

    private void handleHighValueSettlementFailure(SettlementFailureAnalytics analytics, String correlationId) {
        highValueSettlementFailureCounter.increment();
        
        log.error("HIGH VALUE SETTLEMENT FAILURE: Settlement {} for merchant {} failed with amount {} {}, correlationId: {}", 
            analytics.getSettlementId(), analytics.getMerchantName(), 
            analytics.getSettlementAmount(), analytics.getCurrency(), correlationId);
        
        // Send high-value alert
        kafkaTemplate.send("high-value-settlement-failure-alerts", Map.of(
            "settlementId", analytics.getSettlementId(),
            "merchantId", analytics.getMerchantId(),
            "merchantName", analytics.getMerchantName(),
            "settlementAmount", analytics.getSettlementAmount(),
            "currency", analytics.getCurrency(),
            "failureReason", analytics.getFailureReason().toString(),
            "failureCode", analytics.getFailureCode(),
            "priority", "URGENT",
            "requiresEscalation", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Notify executive team
        notificationService.sendExecutiveAlert(
            "High-Value Settlement Failure",
            String.format("High-value settlement failure: %s %s for merchant %s", 
                analytics.getSettlementAmount(), analytics.getCurrency(), analytics.getMerchantName()),
            Map.of(
                "settlementId", analytics.getSettlementId(),
                "merchantName", analytics.getMerchantName(),
                "settlementAmount", analytics.getSettlementAmount(),
                "currency", analytics.getCurrency(),
                "failureReason", analytics.getFailureReason().toString(),
                "correlationId", correlationId
            )
        );
    }

    private void detectCriticalPatterns(SettlementFailureAnalytics analytics, String correlationId) {
        // Detect critical patterns that need immediate attention
        if (analytics.getRetryAttempt() > 3 || 
            "BANK_ACCOUNT_CLOSED".equals(analytics.getFailureCode()) ||
            "INSUFFICIENT_FUNDS".equals(analytics.getFailureCode())) {
            
            kafkaTemplate.send("critical-settlement-pattern-alerts", Map.of(
                "patternType", determineCriticalPatternType(analytics),
                "settlementId", analytics.getSettlementId(),
                "merchantId", analytics.getMerchantId(),
                "failureReason", analytics.getFailureReason().toString(),
                "failureCode", analytics.getFailureCode(),
                "retryAttempt", analytics.getRetryAttempt(),
                "severity", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
    }

    private String determineCriticalPatternType(SettlementFailureAnalytics analytics) {
        if (analytics.getRetryAttempt() > 3) {
            return "REPEATED_SETTLEMENT_FAILURE";
        } else if ("BANK_ACCOUNT_CLOSED".equals(analytics.getFailureCode())) {
            return "CLOSED_BANK_ACCOUNT";
        } else if ("INSUFFICIENT_FUNDS".equals(analytics.getFailureCode())) {
            return "INSUFFICIENT_FUNDS_PATTERN";
        } else {
            return "UNKNOWN_CRITICAL_PATTERN";
        }
    }

    private void generateBusinessInsights(SettlementFailureAnalytics analytics, String correlationId) {
        // Generate business intelligence insights
        BusinessInsight insight = biService.generateSettlementFailureInsight(analytics);
        
        if (insight != null) {
            kafkaTemplate.send("business-insights", Map.of(
                "insightType", "SETTLEMENT_FAILURE_ANALYSIS",
                "category", insight.getCategory(),
                "impact", insight.getImpact(),
                "recommendation", insight.getRecommendation(),
                "priority", insight.getPriority(),
                "failureReason", analytics.getFailureReason().toString(),
                "merchantCategory", analytics.getMerchantCategory(),
                "settlementMethod", analytics.getSettlementMethod(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        // Update business metrics
        biService.updateSettlementFailureMetrics(analytics);
    }

    private void updateRealTimeDashboards(SettlementFailureAnalytics analytics, String correlationId) {
        // Update real-time settlement analytics dashboards
        kafkaTemplate.send("realtime-dashboard-updates", Map.of(
            "dashboardType", "SETTLEMENT_FAILURE_ANALYTICS",
            "metricType", "SETTLEMENT_FAILURE_EVENT",
            "settlementId", analytics.getSettlementId(),
            "merchantId", analytics.getMerchantId(),
            "settlementAmount", analytics.getSettlementAmount(),
            "currency", analytics.getCurrency(),
            "failureReason", analytics.getFailureReason().toString(),
            "merchantCategory", analytics.getMerchantCategory(),
            "settlementMethod", analytics.getSettlementMethod(),
            "retryAttempt", analytics.getRetryAttempt(),
            "eventTimestamp", analytics.getEventTimestamp().toString(),
            "correlationId", correlationId
        ));
    }

    private void publishAnalyticsInsights(SettlementFailureAnalytics analytics, String correlationId) {
        // Publish processed analytics for downstream consumers
        kafkaTemplate.send("settlement-failure-analytics-insights", Map.of(
            "analyticsId", analytics.getId(),
            "settlementId", analytics.getSettlementId(),
            "merchantId", analytics.getMerchantId(),
            "merchantName", analytics.getMerchantName(),
            "settlementAmount", analytics.getSettlementAmount(),
            "currency", analytics.getCurrency(),
            "failureReason", analytics.getFailureReason().toString(),
            "failureCode", analytics.getFailureCode(),
            "bankResponseCode", analytics.getBankResponseCode(),
            "settlementMethod", analytics.getSettlementMethod(),
            "merchantCategory", analytics.getMerchantCategory(),
            "merchantTier", analytics.getMerchantTier().toString(),
            "retryAttempt", analytics.getRetryAttempt(),
            "isRecurring", analytics.getIsRecurring(),
            "eventType", "SETTLEMENT_FAILURE_ANALYTICS_PROCESSED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void processSettlementFailureAnalyticsFallback(
            String settlementId,
            String merchantId,
            String merchantName,
            BigDecimal settlementAmount,
            String currency,
            String failureReason,
            String failureCode,
            String bankResponseCode,
            String settlementMethod,
            String merchantCategory,
            String merchantTier,
            Integer retryAttempt,
            Boolean isRecurring,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for settlement failure analytics - settlementId: {}, correlationId: {}, error: {}", 
            settlementId, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("settlementId", settlementId);
        fallbackEvent.put("merchantId", merchantId);
        fallbackEvent.put("merchantName", merchantName);
        fallbackEvent.put("settlementAmount", settlementAmount);
        fallbackEvent.put("currency", currency);
        fallbackEvent.put("failureReason", failureReason);
        fallbackEvent.put("failureCode", failureCode);
        fallbackEvent.put("bankResponseCode", bankResponseCode);
        fallbackEvent.put("settlementMethod", settlementMethod);
        fallbackEvent.put("merchantCategory", merchantCategory);
        fallbackEvent.put("merchantTier", merchantTier);
        fallbackEvent.put("retryAttempt", retryAttempt);
        fallbackEvent.put("isRecurring", isRecurring);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("settlement-failure-analytics-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Settlement failure analytics message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("settlement-failure-analytics-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Settlement Failure Analytics Processing Failed",
                String.format("CRITICAL: Failed to process settlement failure analytics after max retries. Error: %s", exceptionMessage),
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