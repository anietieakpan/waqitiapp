package com.waqiti.dispute.kafka;

import com.waqiti.payment.dto.PaymentChargebackEvent;
import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.service.TransactionDisputeService;
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
public class ChargebackPreventionEventsConsumer {

    private final TransactionDisputeService disputeService;
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
        successCounter = Counter.builder("chargeback_prevention_events_processed_total")
            .description("Total number of successfully processed chargeback prevention events")
            .register(meterRegistry);
        errorCounter = Counter.builder("chargeback_prevention_events_errors_total")
            .description("Total number of chargeback prevention event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("chargeback_prevention_events_processing_duration")
            .description("Time taken to process chargeback prevention events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"chargeback-prevention-events"},
        groupId = "dispute-chargeback-prevention-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "chargeback-prevention", fallbackMethod = "handleChargebackPreventionEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleChargebackPreventionEvent(
            @Payload Map<String, Object> preventionEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String chargebackId = (String) preventionEvent.get("chargebackId");
        String correlationId = String.format("prevention-%s-p%d-o%d", chargebackId, partition, offset);
        String eventKey = String.format("%s-%s-%s", chargebackId,
            preventionEvent.get("analysisType"), preventionEvent.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing chargeback prevention event: chargebackId={}, analysisType={}, preventionGoal={}",
                chargebackId, preventionEvent.get("analysisType"), preventionEvent.get("preventionGoal"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Process prevention event
            processChargebackPrevention(preventionEvent, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logDisputeEvent("CHARGEBACK_PREVENTION_EVENT_PROCESSED", chargebackId,
                Map.of("chargebackId", chargebackId, "transactionId", preventionEvent.get("transactionId"),
                    "merchantId", preventionEvent.get("merchantId"),
                    "analysisType", preventionEvent.get("analysisType"),
                    "preventionGoal", preventionEvent.get("preventionGoal"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process chargeback prevention event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("chargeback-prevention-fallback-events", Map.of(
                "originalEvent", preventionEvent, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleChargebackPreventionEventFallback(
            Map<String, Object> preventionEvent,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String chargebackId = (String) preventionEvent.get("chargebackId");
        String correlationId = String.format("prevention-fallback-%s-p%d-o%d", chargebackId, partition, offset);

        log.error("Circuit breaker fallback triggered for chargeback prevention: chargebackId={}, error={}",
            chargebackId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("chargeback-prevention-events-dlq", Map.of(
            "originalEvent", preventionEvent,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Chargeback Prevention Circuit Breaker Triggered",
                String.format("Chargeback %s prevention processing failed: %s", chargebackId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltChargebackPreventionEvent(
            @Payload Map<String, Object> preventionEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String chargebackId = (String) preventionEvent.get("chargebackId");
        String correlationId = String.format("dlt-prevention-%s-%d", chargebackId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Chargeback prevention permanently failed: chargebackId={}, topic={}, error={}",
            chargebackId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logDisputeEvent("CHARGEBACK_PREVENTION_DLT_EVENT", chargebackId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "chargebackId", chargebackId, "preventionEvent", preventionEvent,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Chargeback Prevention Dead Letter Event",
                String.format("Chargeback %s prevention sent to DLT: %s", chargebackId, exceptionMessage),
                Map.of("chargebackId", chargebackId, "topic", topic, "correlationId", correlationId)
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

    private void processChargebackPrevention(Map<String, Object> preventionEvent, String correlationId) {
        String chargebackId = (String) preventionEvent.get("chargebackId");
        String transactionId = (String) preventionEvent.get("transactionId");
        String merchantId = (String) preventionEvent.get("merchantId");
        String analysisType = (String) preventionEvent.getOrDefault("analysisType", "STANDARD");
        String preventionGoal = (String) preventionEvent.getOrDefault("preventionGoal", "REDUCE_FUTURE_CHARGEBACKS");

        log.info("Processing chargeback prevention: chargebackId={}, analysisType={}, preventionGoal={}",
            chargebackId, analysisType, preventionGoal);

        // Process based on analysis type
        switch (analysisType) {
            case "POST_CHARGEBACK":
                processPostChargebackAnalysis(preventionEvent, correlationId);
                break;

            case "PATTERN_ANALYSIS":
                processPatternAnalysis(preventionEvent, correlationId);
                break;

            case "MERCHANT_RISK_ANALYSIS":
                processMerchantRiskAnalysis(preventionEvent, correlationId);
                break;

            case "FRAUD_PREVENTION":
                processFraudPreventionAnalysis(preventionEvent, correlationId);
                break;

            case "TRANSACTION_MONITORING":
                processTransactionMonitoringAnalysis(preventionEvent, correlationId);
                break;

            case "CUSTOMER_BEHAVIOR":
                processCustomerBehaviorAnalysis(preventionEvent, correlationId);
                break;

            case "INDUSTRY_TRENDS":
                processIndustryTrendsAnalysis(preventionEvent, correlationId);
                break;

            default:
                processStandardPreventionAnalysis(preventionEvent, correlationId);
                break;
        }

        // Generate prevention recommendations
        generatePreventionRecommendations(preventionEvent, correlationId);

        // Update prevention metrics
        updatePreventionMetrics(analysisType, preventionGoal, merchantId);

        log.info("Chargeback prevention processed: chargebackId={}, analysisType={}",
            chargebackId, analysisType);
    }

    private void processPostChargebackAnalysis(Map<String, Object> preventionEvent, String correlationId) {
        String chargebackId = (String) preventionEvent.get("chargebackId");
        String reasonCode = (String) preventionEvent.get("reasonCode");

        log.info("Processing post-chargeback analysis: chargebackId={}, reasonCode={}", chargebackId, reasonCode);

        // Analyze chargeback root cause
        analyzeChargebackRootCause(chargebackId, reasonCode, correlationId);

        // Identify similar transaction patterns
        identifySimilarTransactionPatterns(chargebackId, correlationId);

        // Generate merchant-specific recommendations
        generateMerchantRecommendations(chargebackId, correlationId);

        // Update fraud detection rules if fraud-related
        if (Boolean.TRUE.equals(preventionEvent.get("fraudRelated"))) {
            updateFraudDetectionRules(chargebackId, correlationId);
        }
    }

    private void processPatternAnalysis(Map<String, Object> preventionEvent, String correlationId) {
        String merchantId = (String) preventionEvent.get("merchantId");

        log.info("Processing pattern analysis for merchant: {}", merchantId);

        // Analyze chargeback patterns
        analyzeChargebackPatterns(merchantId, correlationId);

        // Identify high-risk transaction characteristics
        identifyHighRiskCharacteristics(merchantId, correlationId);

        // Generate pattern-based rules
        generatePatternBasedRules(merchantId, correlationId);

        // Send pattern analysis results
        sendPatternAnalysisResults(merchantId, correlationId);
    }

    private void processMerchantRiskAnalysis(Map<String, Object> preventionEvent, String correlationId) {
        String merchantId = (String) preventionEvent.get("merchantId");

        log.info("Processing merchant risk analysis: {}", merchantId);

        // Calculate merchant risk score
        calculateMerchantRiskScore(merchantId, correlationId);

        // Analyze merchant chargeback history
        analyzeMerchantChargebackHistory(merchantId, correlationId);

        // Assess industry risk factors
        assessIndustryRiskFactors(merchantId, correlationId);

        // Generate risk mitigation strategies
        generateRiskMitigationStrategies(merchantId, correlationId);
    }

    private void processFraudPreventionAnalysis(Map<String, Object> preventionEvent, String correlationId) {
        String chargebackId = (String) preventionEvent.get("chargebackId");

        log.info("Processing fraud prevention analysis: {}", chargebackId);

        // Analyze fraud indicators
        analyzeFraudIndicators(chargebackId, correlationId);

        // Update fraud detection models
        updateFraudDetectionModels(chargebackId, correlationId);

        // Generate fraud prevention rules
        generateFraudPreventionRules(chargebackId, correlationId);

        // Send to fraud prevention system
        sendToFraudPreventionSystem(chargebackId, correlationId);
    }

    private void processTransactionMonitoringAnalysis(Map<String, Object> preventionEvent, String correlationId) {
        String transactionId = (String) preventionEvent.get("transactionId");

        log.info("Processing transaction monitoring analysis: {}", transactionId);

        // Analyze transaction characteristics
        analyzeTransactionCharacteristics(transactionId, correlationId);

        // Identify monitoring gaps
        identifyMonitoringGaps(transactionId, correlationId);

        // Update monitoring rules
        updateMonitoringRules(transactionId, correlationId);

        // Generate monitoring recommendations
        generateMonitoringRecommendations(transactionId, correlationId);
    }

    private void processCustomerBehaviorAnalysis(Map<String, Object> preventionEvent, String correlationId) {
        String customerId = (String) preventionEvent.get("customerId");

        log.info("Processing customer behavior analysis: {}", customerId);

        // Analyze customer dispute patterns
        analyzeCustomerDisputePatterns(customerId, correlationId);

        // Identify problematic customer behaviors
        identifyProblematicBehaviors(customerId, correlationId);

        // Generate customer risk profile
        generateCustomerRiskProfile(customerId, correlationId);

        // Send customer insights
        sendCustomerInsights(customerId, correlationId);
    }

    private void processIndustryTrendsAnalysis(Map<String, Object> preventionEvent, String correlationId) {
        String industryCategory = (String) preventionEvent.get("industryCategory");

        log.info("Processing industry trends analysis: {}", industryCategory);

        // Analyze industry chargeback trends
        analyzeIndustryChargebackTrends(industryCategory, correlationId);

        // Compare with industry benchmarks
        compareWithIndustryBenchmarks(industryCategory, correlationId);

        // Generate industry-specific recommendations
        generateIndustryRecommendations(industryCategory, correlationId);

        // Send trend analysis results
        sendTrendAnalysisResults(industryCategory, correlationId);
    }

    private void processStandardPreventionAnalysis(Map<String, Object> preventionEvent, String correlationId) {
        String chargebackId = (String) preventionEvent.get("chargebackId");

        log.info("Processing standard prevention analysis: {}", chargebackId);

        // Basic chargeback analysis
        performBasicChargebackAnalysis(chargebackId, correlationId);

        // Generate standard recommendations
        generateStandardRecommendations(chargebackId, correlationId);
    }

    private void analyzeChargebackRootCause(String chargebackId, String reasonCode, String correlationId) {
        kafkaTemplate.send("root-cause-analysis", Map.of(
            "chargebackId", chargebackId,
            "reasonCode", reasonCode,
            "analysisType", "ROOT_CAUSE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void identifySimilarTransactionPatterns(String chargebackId, String correlationId) {
        kafkaTemplate.send("pattern-identification", Map.of(
            "chargebackId", chargebackId,
            "patternType", "SIMILAR_TRANSACTIONS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void generateMerchantRecommendations(String chargebackId, String correlationId) {
        kafkaTemplate.send("merchant-recommendations", Map.of(
            "chargebackId", chargebackId,
            "recommendationType", "CHARGEBACK_PREVENTION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void updateFraudDetectionRules(String chargebackId, String correlationId) {
        kafkaTemplate.send("fraud-rule-updates", Map.of(
            "chargebackId", chargebackId,
            "updateType", "CHARGEBACK_BASED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void analyzeChargebackPatterns(String merchantId, String correlationId) {
        kafkaTemplate.send("chargeback-pattern-analysis", Map.of(
            "merchantId", merchantId,
            "analysisScope", "PATTERN_DETECTION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void identifyHighRiskCharacteristics(String merchantId, String correlationId) {
        kafkaTemplate.send("risk-characteristic-analysis", Map.of(
            "merchantId", merchantId,
            "analysisType", "HIGH_RISK_IDENTIFICATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void generatePatternBasedRules(String merchantId, String correlationId) {
        kafkaTemplate.send("pattern-rule-generation", Map.of(
            "merchantId", merchantId,
            "ruleType", "PATTERN_BASED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void calculateMerchantRiskScore(String merchantId, String correlationId) {
        kafkaTemplate.send("risk-score-calculation", Map.of(
            "merchantId", merchantId,
            "scoreType", "CHARGEBACK_RISK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void analyzeMerchantChargebackHistory(String merchantId, String correlationId) {
        kafkaTemplate.send("merchant-history-analysis", Map.of(
            "merchantId", merchantId,
            "analysisType", "CHARGEBACK_HISTORY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void generatePreventionRecommendations(Map<String, Object> preventionEvent, String correlationId) {
        String chargebackId = (String) preventionEvent.get("chargebackId");
        String preventionGoal = (String) preventionEvent.get("preventionGoal");

        kafkaTemplate.send("prevention-recommendations", Map.of(
            "chargebackId", chargebackId,
            "preventionGoal", preventionGoal,
            "recommendationType", "AUTOMATED_ANALYSIS",
            "priority", "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Prevention recommendations generated for chargeback: {}", chargebackId);
    }

    private void updatePreventionMetrics(String analysisType, String preventionGoal, String merchantId) {
        meterRegistry.counter("chargeback_prevention_analysis_total",
            "analysis_type", analysisType,
            "prevention_goal", preventionGoal).increment();

        if (merchantId != null) {
            meterRegistry.counter("merchant_prevention_analysis_total",
                "merchant_id", merchantId,
                "analysis_type", analysisType).increment();
        }
    }

    // Placeholder methods for complex analysis operations
    private void sendPatternAnalysisResults(String merchantId, String correlationId) {
        kafkaTemplate.send("pattern-analysis-results", Map.of(
            "merchantId", merchantId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void assessIndustryRiskFactors(String merchantId, String correlationId) {
        kafkaTemplate.send("industry-risk-assessment", Map.of(
            "merchantId", merchantId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void generateRiskMitigationStrategies(String merchantId, String correlationId) {
        kafkaTemplate.send("risk-mitigation-strategies", Map.of(
            "merchantId", merchantId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void analyzeFraudIndicators(String chargebackId, String correlationId) {
        kafkaTemplate.send("fraud-indicator-analysis", Map.of(
            "chargebackId", chargebackId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void updateFraudDetectionModels(String chargebackId, String correlationId) {
        kafkaTemplate.send("fraud-model-updates", Map.of(
            "chargebackId", chargebackId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void generateFraudPreventionRules(String chargebackId, String correlationId) {
        kafkaTemplate.send("fraud-prevention-rules", Map.of(
            "chargebackId", chargebackId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void sendToFraudPreventionSystem(String chargebackId, String correlationId) {
        kafkaTemplate.send("fraud-prevention-system", Map.of(
            "chargebackId", chargebackId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void analyzeTransactionCharacteristics(String transactionId, String correlationId) {
        kafkaTemplate.send("transaction-characteristic-analysis", Map.of(
            "transactionId", transactionId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void identifyMonitoringGaps(String transactionId, String correlationId) {
        kafkaTemplate.send("monitoring-gap-analysis", Map.of(
            "transactionId", transactionId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void updateMonitoringRules(String transactionId, String correlationId) {
        kafkaTemplate.send("monitoring-rule-updates", Map.of(
            "transactionId", transactionId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void generateMonitoringRecommendations(String transactionId, String correlationId) {
        kafkaTemplate.send("monitoring-recommendations", Map.of(
            "transactionId", transactionId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void analyzeCustomerDisputePatterns(String customerId, String correlationId) {
        kafkaTemplate.send("customer-dispute-analysis", Map.of(
            "customerId", customerId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void identifyProblematicBehaviors(String customerId, String correlationId) {
        kafkaTemplate.send("problematic-behavior-analysis", Map.of(
            "customerId", customerId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void generateCustomerRiskProfile(String customerId, String correlationId) {
        kafkaTemplate.send("customer-risk-profiling", Map.of(
            "customerId", customerId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void sendCustomerInsights(String customerId, String correlationId) {
        kafkaTemplate.send("customer-insights", Map.of(
            "customerId", customerId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void analyzeIndustryChargebackTrends(String industryCategory, String correlationId) {
        kafkaTemplate.send("industry-trend-analysis", Map.of(
            "industryCategory", industryCategory, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void compareWithIndustryBenchmarks(String industryCategory, String correlationId) {
        kafkaTemplate.send("industry-benchmark-comparison", Map.of(
            "industryCategory", industryCategory, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void generateIndustryRecommendations(String industryCategory, String correlationId) {
        kafkaTemplate.send("industry-recommendations", Map.of(
            "industryCategory", industryCategory, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void sendTrendAnalysisResults(String industryCategory, String correlationId) {
        kafkaTemplate.send("trend-analysis-results", Map.of(
            "industryCategory", industryCategory, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void performBasicChargebackAnalysis(String chargebackId, String correlationId) {
        kafkaTemplate.send("basic-chargeback-analysis", Map.of(
            "chargebackId", chargebackId, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void generateStandardRecommendations(String chargebackId, String correlationId) {
        kafkaTemplate.send("standard-recommendations", Map.of(
            "chargebackId", chargebackId, "correlationId", correlationId, "timestamp", Instant.now()));
    }
}