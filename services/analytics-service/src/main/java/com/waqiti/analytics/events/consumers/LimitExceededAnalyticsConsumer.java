package com.waqiti.analytics.events.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.model.*;
import com.waqiti.analytics.repository.LimitExceededAnalyticsRepository;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for limit exceeded analytics events
 * Analyzes limit exceeded patterns and generates insights with enterprise patterns
 * 
 * Critical for: Risk management, compliance monitoring, customer experience optimization
 * SLA: Must process limit analytics within 30 seconds for real-time insights
 */
@Component
@Slf4j
public class LimitExceededAnalyticsConsumer {

    private final LimitExceededAnalyticsRepository analyticsRepository;
    private final LimitPatternAnalysisService patternAnalysisService;
    private final RiskAnalyticsService riskAnalyticsService;
    private final CustomerBehaviorAnalysisService behaviorAnalysisService;
    private final ComplianceAnalyticsService complianceAnalyticsService;
    private final BusinessIntelligenceService biService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter limitExceededAnalyticsCounter;
    private final Counter highValueLimitCounter;
    private final Counter complianceLimitCounter;
    private final Counter suspiciousLimitPatternCounter;
    private final Timer analyticsProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("50000.00");

    public LimitExceededAnalyticsConsumer(
            LimitExceededAnalyticsRepository analyticsRepository,
            LimitPatternAnalysisService patternAnalysisService,
            RiskAnalyticsService riskAnalyticsService,
            CustomerBehaviorAnalysisService behaviorAnalysisService,
            ComplianceAnalyticsService complianceAnalyticsService,
            BusinessIntelligenceService biService,
            NotificationService notificationService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.analyticsRepository = analyticsRepository;
        this.patternAnalysisService = patternAnalysisService;
        this.riskAnalyticsService = riskAnalyticsService;
        this.behaviorAnalysisService = behaviorAnalysisService;
        this.complianceAnalyticsService = complianceAnalyticsService;
        this.biService = biService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.limitExceededAnalyticsCounter = Counter.builder("limit.exceeded.analytics.events")
            .description("Count of limit exceeded analytics events")
            .register(meterRegistry);
        
        this.highValueLimitCounter = Counter.builder("limit.exceeded.analytics.high.value.events")
            .description("Count of high-value limit exceeded analytics")
            .register(meterRegistry);
        
        this.complianceLimitCounter = Counter.builder("limit.exceeded.analytics.compliance.events")
            .description("Count of compliance-related limit analytics")
            .register(meterRegistry);
        
        this.suspiciousLimitPatternCounter = Counter.builder("limit.exceeded.analytics.suspicious.pattern.events")
            .description("Count of suspicious limit pattern analytics")
            .register(meterRegistry);
        
        this.analyticsProcessingTimer = Timer.builder("limit.exceeded.analytics.processing.duration")
            .description("Time taken to process limit analytics events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "analytics.limit.exceeded",
        groupId = "limit-exceeded-analytics-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "analytics-limit-exceeded-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleLimitExceededAnalyticsEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received limit exceeded analytics event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String userId = (String) eventData.get("userId");
            String accountId = (String) eventData.get("accountId");
            String limitType = (String) eventData.get("limitType");
            BigDecimal attemptedAmount = new BigDecimal(eventData.get("attemptedAmount").toString());
            BigDecimal limitAmount = new BigDecimal(eventData.get("limitAmount").toString());
            BigDecimal excessAmount = new BigDecimal(eventData.get("excessAmount").toString());
            String transactionType = (String) eventData.get("transactionType");
            String currency = (String) eventData.get("currency");
            String limitPeriod = (String) eventData.get("limitPeriod");
            Boolean isInternational = (Boolean) eventData.getOrDefault("isInternational", false);
            Boolean isRecurring = (Boolean) eventData.getOrDefault("isRecurring", false);
            String merchantCategory = (String) eventData.get("merchantCategory");
            String countryCode = (String) eventData.get("countryCode");
            String customerSegment = (String) eventData.get("customerSegment");
            Integer consecutiveExceeded = (Integer) eventData.getOrDefault("consecutiveExceeded", 1);
            
            String correlationId = String.format("limit-exceeded-analytics-%s-%s-%d", 
                userId, limitType, System.currentTimeMillis());
            
            log.warn("Processing limit exceeded analytics - userId: {}, limitType: {}, excess: {}, correlationId: {}", 
                userId, limitType, excessAmount, correlationId);
            
            limitExceededAnalyticsCounter.increment();
            
            processLimitExceededAnalytics(userId, accountId, limitType, attemptedAmount, limitAmount,
                excessAmount, transactionType, currency, limitPeriod, isInternational, isRecurring,
                merchantCategory, countryCode, customerSegment, consecutiveExceeded, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(analyticsProcessingTimer);
            
            log.info("Successfully processed limit exceeded analytics event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process limit exceeded analytics event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Limit exceeded analytics processing failed", e);
        }
    }

    @CircuitBreaker(name = "analytics", fallbackMethod = "processLimitExceededAnalyticsFallback")
    @Retry(name = "analytics")
    private void processLimitExceededAnalytics(
            String userId,
            String accountId,
            String limitType,
            BigDecimal attemptedAmount,
            BigDecimal limitAmount,
            BigDecimal excessAmount,
            String transactionType,
            String currency,
            String limitPeriod,
            Boolean isInternational,
            Boolean isRecurring,
            String merchantCategory,
            String countryCode,
            String customerSegment,
            Integer consecutiveExceeded,
            Map<String, Object> eventData,
            String correlationId) {
        
        // Create limit exceeded analytics record
        LimitExceededAnalytics analytics = LimitExceededAnalytics.builder()
            .id(java.util.UUID.randomUUID().toString())
            .userId(userId)
            .accountId(accountId)
            .limitType(LimitType.fromString(limitType))
            .attemptedAmount(attemptedAmount)
            .limitAmount(limitAmount)
            .excessAmount(excessAmount)
            .transactionType(transactionType)
            .currency(currency)
            .limitPeriod(LimitPeriod.fromString(limitPeriod))
            .isInternational(isInternational)
            .isRecurring(isRecurring)
            .merchantCategory(merchantCategory)
            .countryCode(countryCode)
            .customerSegment(CustomerSegment.fromString(customerSegment))
            .consecutiveExceeded(consecutiveExceeded)
            .eventTimestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        analyticsRepository.save(analytics);
        
        // Analyze limit patterns
        analyzeLimitPatterns(analytics, correlationId);
        
        // Perform risk analysis
        analyzeRiskPatterns(analytics, correlationId);
        
        // Analyze customer behavior
        analyzeCustomerBehavior(analytics, correlationId);
        
        // Check compliance implications
        analyzeComplianceImpact(analytics, correlationId);
        
        // Detect suspicious patterns
        detectSuspiciousPatterns(analytics, correlationId);
        
        // Generate business insights
        generateBusinessInsights(analytics, correlationId);
        
        // Check for high-value limits
        if (attemptedAmount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            handleHighValueLimitExceeded(analytics, correlationId);
        }
        
        // Update real-time dashboards
        updateRealTimeDashboards(analytics, correlationId);
        
        // Publish analytics insights
        publishAnalyticsInsights(analytics, correlationId);
        
        // Audit the analytics processing
        auditService.logAnalyticsEvent(
            "LIMIT_EXCEEDED_ANALYTICS_PROCESSED",
            analytics.getId(),
            Map.of(
                "userId", userId,
                "accountId", accountId,
                "limitType", limitType,
                "attemptedAmount", attemptedAmount,
                "limitAmount", limitAmount,
                "excessAmount", excessAmount,
                "transactionType", transactionType,
                "currency", currency,
                "consecutiveExceeded", consecutiveExceeded,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.info("Limit exceeded analytics processed - userId: {}, limitType: {}, excess: {}, correlationId: {}", 
            userId, limitType, excessAmount, correlationId);
    }

    private void analyzeLimitPatterns(LimitExceededAnalytics analytics, String correlationId) {
        // Analyze temporal patterns of limit exceeded events
        LimitPatternAnalysis patternAnalysis = patternAnalysisService.analyzeLimitPatterns(
            analytics.getUserId(),
            analytics.getLimitType(),
            analytics.getEventTimestamp(),
            analytics.getConsecutiveExceeded()
        );
        
        // Check for anomalous patterns
        if (patternAnalysis.isAnomalousPattern()) {
            kafkaTemplate.send("limit-pattern-anomalies", Map.of(
                "userId", analytics.getUserId(),
                "limitType", analytics.getLimitType().toString(),
                "anomalyType", patternAnalysis.getAnomalyType(),
                "severity", patternAnalysis.getSeverity(),
                "consecutiveExceeded", analytics.getConsecutiveExceeded(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        // Update pattern statistics
        patternAnalysisService.updateLimitPatternStatistics(analytics);
        
        log.debug("Limit pattern analysis completed - correlationId: {}, anomalous: {}", 
            correlationId, patternAnalysis.isAnomalousPattern());
    }

    private void analyzeRiskPatterns(LimitExceededAnalytics analytics, String correlationId) {
        // Analyze risk implications of limit exceeded events
        RiskAnalysis riskAnalysis = riskAnalyticsService.analyzeLimitRisk(
            analytics.getUserId(),
            analytics.getAttemptedAmount(),
            analytics.getExcessAmount(),
            analytics.getIsInternational(),
            analytics.getMerchantCategory()
        );
        
        // Check for high-risk patterns
        if (riskAnalysis.isHighRisk()) {
            kafkaTemplate.send("high-risk-limit-alerts", Map.of(
                "userId", analytics.getUserId(),
                "accountId", analytics.getAccountId(),
                "riskScore", riskAnalysis.getRiskScore(),
                "riskFactors", riskAnalysis.getRiskFactors(),
                "limitType", analytics.getLimitType().toString(),
                "excessAmount", analytics.getExcessAmount(),
                "currency", analytics.getCurrency(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
            
            // Send risk alert
            notificationService.sendRiskAlert(
                "High Risk Limit Exceeded",
                String.format("High-risk limit exceeded by user %s: %s %s excess", 
                    analytics.getUserId(), analytics.getExcessAmount(), analytics.getCurrency()),
                Map.of(
                    "userId", analytics.getUserId(),
                    "riskScore", riskAnalysis.getRiskScore(),
                    "excessAmount", analytics.getExcessAmount(),
                    "correlationId", correlationId
                )
            );
        }
        
        // Update risk models
        riskAnalyticsService.updateRiskModels(analytics);
    }

    private void analyzeCustomerBehavior(LimitExceededAnalytics analytics, String correlationId) {
        // Analyze customer behavior patterns
        CustomerBehaviorAnalysis behaviorAnalysis = behaviorAnalysisService.analyzeLimitBehavior(
            analytics.getUserId(),
            analytics.getLimitType(),
            analytics.getAttemptedAmount(),
            analytics.getCustomerSegment(),
            analytics.getIsRecurring()
        );
        
        // Check for behavior changes
        if (behaviorAnalysis.isBehaviorChange()) {
            kafkaTemplate.send("customer-behavior-changes", Map.of(
                "userId", analytics.getUserId(),
                "behaviorChangeType", behaviorAnalysis.getChangeType(),
                "previousPattern", behaviorAnalysis.getPreviousPattern(),
                "currentPattern", behaviorAnalysis.getCurrentPattern(),
                "limitType", analytics.getLimitType().toString(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        // Update customer profiles
        behaviorAnalysisService.updateCustomerProfile(analytics);
    }

    private void analyzeComplianceImpact(LimitExceededAnalytics analytics, String correlationId) {
        // Check for compliance implications
        if (requiresComplianceReview(analytics)) {
            complianceLimitCounter.increment();
            
            ComplianceAnalysis complianceAnalysis = complianceAnalyticsService.analyzeLimitCompliance(
                analytics.getUserId(),
                analytics.getAttemptedAmount(),
                analytics.getLimitType(),
                analytics.getIsInternational(),
                analytics.getCountryCode()
            );
            
            if (complianceAnalysis.requiresReporting()) {
                kafkaTemplate.send("compliance-limit-reports", Map.of(
                    "userId", analytics.getUserId(),
                    "reportType", complianceAnalysis.getReportType(),
                    "regulatoryRequirement", complianceAnalysis.getRegulatoryRequirement(),
                    "limitType", analytics.getLimitType().toString(),
                    "attemptedAmount", analytics.getAttemptedAmount(),
                    "currency", analytics.getCurrency(),
                    "countryCode", analytics.getCountryCode(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now().toString()
                ));
                
                // Send compliance alert
                notificationService.sendComplianceAlert(
                    "Compliance Limit Review Required",
                    String.format("Limit exceeded requires compliance review: User %s, Amount %s %s", 
                        analytics.getUserId(), analytics.getAttemptedAmount(), analytics.getCurrency()),
                    Map.of(
                        "userId", analytics.getUserId(),
                        "reportType", complianceAnalysis.getReportType(),
                        "correlationId", correlationId
                    )
                );
            }
        }
    }

    private void detectSuspiciousPatterns(LimitExceededAnalytics analytics, String correlationId) {
        // Detect patterns that might indicate suspicious activity
        if (analytics.getConsecutiveExceeded() > 3 || 
            analytics.getExcessAmount().compareTo(analytics.getLimitAmount()) > 0) {
            
            suspiciousLimitPatternCounter.increment();
            
            SuspiciousPatternAnalysis suspiciousAnalysis = patternAnalysisService.analyzeSuspiciousLimitPatterns(
                analytics.getUserId(),
                analytics.getConsecutiveExceeded(),
                analytics.getExcessAmount(),
                analytics.getLimitAmount()
            );
            
            if (suspiciousAnalysis.isSuspicious()) {
                kafkaTemplate.send("suspicious-limit-pattern-alerts", Map.of(
                    "userId", analytics.getUserId(),
                    "accountId", analytics.getAccountId(),
                    "suspicionLevel", suspiciousAnalysis.getSuspicionLevel(),
                    "patternType", suspiciousAnalysis.getPatternType(),
                    "consecutiveExceeded", analytics.getConsecutiveExceeded(),
                    "excessAmount", analytics.getExcessAmount(),
                    "limitAmount", analytics.getLimitAmount(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now().toString()
                ));
                
                // Send security alert
                notificationService.sendSecurityAlert(
                    "Suspicious Limit Pattern Detected",
                    String.format("Suspicious limit pattern for user %s: %s", 
                        analytics.getUserId(), suspiciousAnalysis.getPatternType()),
                    Map.of(
                        "userId", analytics.getUserId(),
                        "suspicionLevel", suspiciousAnalysis.getSuspicionLevel(),
                        "correlationId", correlationId
                    )
                );
            }
        }
    }

    private void generateBusinessInsights(LimitExceededAnalytics analytics, String correlationId) {
        // Generate business intelligence insights
        BusinessInsight insight = biService.generateLimitInsight(analytics);
        
        if (insight != null) {
            kafkaTemplate.send("business-insights", Map.of(
                "insightType", "LIMIT_EXCEEDED_ANALYSIS",
                "category", insight.getCategory(),
                "impact", insight.getImpact(),
                "recommendation", insight.getRecommendation(),
                "priority", insight.getPriority(),
                "limitType", analytics.getLimitType().toString(),
                "customerSegment", analytics.getCustomerSegment().toString(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        // Update business metrics
        biService.updateLimitMetrics(analytics);
    }

    private void handleHighValueLimitExceeded(LimitExceededAnalytics analytics, String correlationId) {
        highValueLimitCounter.increment();
        
        log.error("HIGH VALUE LIMIT EXCEEDED: User {} attempted {} {}, exceeding limit by {} {}, correlationId: {}", 
            analytics.getUserId(), analytics.getAttemptedAmount(), analytics.getCurrency(),
            analytics.getExcessAmount(), analytics.getCurrency(), correlationId);
        
        // Send high-value alert
        kafkaTemplate.send("high-value-limit-exceeded-alerts", Map.of(
            "userId", analytics.getUserId(),
            "accountId", analytics.getAccountId(),
            "limitType", analytics.getLimitType().toString(),
            "attemptedAmount", analytics.getAttemptedAmount(),
            "limitAmount", analytics.getLimitAmount(),
            "excessAmount", analytics.getExcessAmount(),
            "currency", analytics.getCurrency(),
            "priority", "URGENT",
            "requiresReview", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Notify executive team
        notificationService.sendExecutiveAlert(
            "High-Value Limit Exceeded",
            String.format("High-value limit exceeded: User %s attempted %s %s, exceeding limit by %s %s", 
                analytics.getUserId(), analytics.getAttemptedAmount(), analytics.getCurrency(),
                analytics.getExcessAmount(), analytics.getCurrency()),
            Map.of(
                "userId", analytics.getUserId(),
                "attemptedAmount", analytics.getAttemptedAmount(),
                "excessAmount", analytics.getExcessAmount(),
                "currency", analytics.getCurrency(),
                "correlationId", correlationId
            )
        );
    }

    private boolean requiresComplianceReview(LimitExceededAnalytics analytics) {
        // Check if limit exceeded requires compliance review
        return analytics.getAttemptedAmount().compareTo(new BigDecimal("10000")) > 0 ||
               analytics.getIsInternational() ||
               "WIRE_TRANSFER".equals(analytics.getTransactionType()) ||
               "CASH_WITHDRAWAL".equals(analytics.getTransactionType());
    }

    private void updateRealTimeDashboards(LimitExceededAnalytics analytics, String correlationId) {
        // Update real-time analytics dashboards
        kafkaTemplate.send("realtime-dashboard-updates", Map.of(
            "dashboardType", "LIMIT_EXCEEDED_ANALYTICS",
            "metricType", "LIMIT_EXCEEDED_EVENT",
            "userId", analytics.getUserId(),
            "limitType", analytics.getLimitType().toString(),
            "attemptedAmount", analytics.getAttemptedAmount(),
            "excessAmount", analytics.getExcessAmount(),
            "currency", analytics.getCurrency(),
            "customerSegment", analytics.getCustomerSegment().toString(),
            "isInternational", analytics.getIsInternational(),
            "consecutiveExceeded", analytics.getConsecutiveExceeded(),
            "eventTimestamp", analytics.getEventTimestamp().toInstant(ZoneOffset.UTC).toString(),
            "correlationId", correlationId
        ));
    }

    private void publishAnalyticsInsights(LimitExceededAnalytics analytics, String correlationId) {
        // Publish processed analytics for downstream consumers
        kafkaTemplate.send("limit-analytics-insights", Map.of(
            "analyticsId", analytics.getId(),
            "userId", analytics.getUserId(),
            "accountId", analytics.getAccountId(),
            "limitType", analytics.getLimitType().toString(),
            "attemptedAmount", analytics.getAttemptedAmount(),
            "limitAmount", analytics.getLimitAmount(),
            "excessAmount", analytics.getExcessAmount(),
            "transactionType", analytics.getTransactionType(),
            "currency", analytics.getCurrency(),
            "limitPeriod", analytics.getLimitPeriod().toString(),
            "isInternational", analytics.getIsInternational(),
            "isRecurring", analytics.getIsRecurring(),
            "customerSegment", analytics.getCustomerSegment().toString(),
            "consecutiveExceeded", analytics.getConsecutiveExceeded(),
            "eventType", "LIMIT_EXCEEDED_ANALYTICS_PROCESSED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void processLimitExceededAnalyticsFallback(
            String userId,
            String accountId,
            String limitType,
            BigDecimal attemptedAmount,
            BigDecimal limitAmount,
            BigDecimal excessAmount,
            String transactionType,
            String currency,
            String limitPeriod,
            Boolean isInternational,
            Boolean isRecurring,
            String merchantCategory,
            String countryCode,
            String customerSegment,
            Integer consecutiveExceeded,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for limit exceeded analytics - userId: {}, correlationId: {}, error: {}", 
            userId, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("userId", userId);
        fallbackEvent.put("accountId", accountId);
        fallbackEvent.put("limitType", limitType);
        fallbackEvent.put("attemptedAmount", attemptedAmount);
        fallbackEvent.put("limitAmount", limitAmount);
        fallbackEvent.put("excessAmount", excessAmount);
        fallbackEvent.put("transactionType", transactionType);
        fallbackEvent.put("currency", currency);
        fallbackEvent.put("limitPeriod", limitPeriod);
        fallbackEvent.put("isInternational", isInternational);
        fallbackEvent.put("isRecurring", isRecurring);
        fallbackEvent.put("merchantCategory", merchantCategory);
        fallbackEvent.put("countryCode", countryCode);
        fallbackEvent.put("customerSegment", customerSegment);
        fallbackEvent.put("consecutiveExceeded", consecutiveExceeded);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("limit-exceeded-analytics-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Limit exceeded analytics message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("limit-exceeded-analytics-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Limit Exceeded Analytics Processing Failed",
                String.format("CRITICAL: Failed to process limit analytics after max retries. Error: %s", exceptionMessage),
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