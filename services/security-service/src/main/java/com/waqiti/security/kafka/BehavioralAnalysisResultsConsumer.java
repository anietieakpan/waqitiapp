package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.BehavioralAnalysisService;
import com.waqiti.security.service.RiskScoringService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.security.service.ThreatResponseService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
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
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class BehavioralAnalysisResultsConsumer {

    private final BehavioralAnalysisService behavioralAnalysisService;
    private final RiskScoringService riskScoringService;
    private final SecurityNotificationService securityNotificationService;
    private final ThreatResponseService threatResponseService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
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
        successCounter = Counter.builder("behavioral_analysis_results_processed_total")
            .description("Total number of successfully processed behavioral analysis result events")
            .register(meterRegistry);
        errorCounter = Counter.builder("behavioral_analysis_results_errors_total")
            .description("Total number of behavioral analysis result processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("behavioral_analysis_results_processing_duration")
            .description("Time taken to process behavioral analysis result events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"behavioral-analysis-results", "ml-behavior-analysis", "user-behavior-insights"},
        groupId = "security-service-behavioral-analysis-results-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "behavioral-analysis-results", fallbackMethod = "handleBehavioralAnalysisResultFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBehavioralAnalysisResult(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("behavioral-analysis-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String analysisId = (String) event.get("analysisId");
            String userId = (String) event.get("userId");
            String analysisType = (String) event.get("analysisType");
            String resultStatus = (String) event.get("resultStatus");
            String eventKey = String.format("%s-%s-%s", analysisId, userId, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing behavioral analysis result: analysisId={}, userId={}, type={}, status={}",
                analysisId, userId, analysisType, resultStatus);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Double riskScore = ((Number) event.get("riskScore")).doubleValue();
            String riskLevel = (String) event.get("riskLevel");
            LocalDateTime analyzedAt = LocalDateTime.parse((String) event.get("analyzedAt"));
            @SuppressWarnings("unchecked")
            Map<String, Object> analysisResults = (Map<String, Object>) event.get("analysisResults");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> behaviorPatterns = (List<Map<String, Object>>) event.getOrDefault("behaviorPatterns", List.of());
            @SuppressWarnings("unchecked")
            List<String> anomalies = (List<String>) event.getOrDefault("anomalies", List.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> mlModelOutput = (Map<String, Object>) event.getOrDefault("mlModelOutput", Map.of());
            String modelVersion = (String) event.get("modelVersion");
            Double confidence = ((Number) event.getOrDefault("confidence", 0.0)).doubleValue();
            @SuppressWarnings("unchecked")
            List<String> recommendations = (List<String>) event.getOrDefault("recommendations", List.of());
            Boolean requiresAction = (Boolean) event.getOrDefault("requiresAction", false);

            // Process analysis result based on type
            switch (analysisType) {
                case "TRANSACTION_BEHAVIOR":
                    processTransactionBehaviorAnalysis(analysisId, userId, riskScore, riskLevel,
                        analysisResults, behaviorPatterns, anomalies, recommendations, correlationId);
                    break;

                case "LOGIN_BEHAVIOR":
                    processLoginBehaviorAnalysis(analysisId, userId, riskScore, riskLevel,
                        analysisResults, behaviorPatterns, anomalies, recommendations, correlationId);
                    break;

                case "DEVICE_BEHAVIOR":
                    processDeviceBehaviorAnalysis(analysisId, userId, riskScore, riskLevel,
                        analysisResults, behaviorPatterns, anomalies, recommendations, correlationId);
                    break;

                case "SPENDING_BEHAVIOR":
                    processSpendingBehaviorAnalysis(analysisId, userId, riskScore, riskLevel,
                        analysisResults, behaviorPatterns, anomalies, recommendations, correlationId);
                    break;

                case "TEMPORAL_BEHAVIOR":
                    processTemporalBehaviorAnalysis(analysisId, userId, riskScore, riskLevel,
                        analysisResults, behaviorPatterns, analyzedAt, recommendations, correlationId);
                    break;

                case "COMPREHENSIVE_BEHAVIOR":
                    processComprehensiveBehaviorAnalysis(analysisId, userId, riskScore, riskLevel,
                        analysisResults, behaviorPatterns, anomalies, mlModelOutput, recommendations, correlationId);
                    break;

                default:
                    processGenericAnalysisResult(analysisId, userId, analysisType, riskScore,
                        riskLevel, analysisResults, recommendations, correlationId);
                    break;
            }

            // Update user risk profile with analysis results
            updateUserRiskProfile(userId, analysisType, riskScore, riskLevel, analysisResults,
                behaviorPatterns, modelVersion, confidence, correlationId);

            // Take action if required
            if (requiresAction || "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
                executeRecommendedActions(userId, analysisId, analysisType, riskLevel,
                    recommendations, anomalies, correlationId);
            }

            // Store analysis results for future reference
            storeAnalysisResults(analysisId, userId, analysisType, riskScore, riskLevel,
                analysisResults, behaviorPatterns, mlModelOutput, modelVersion, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("BEHAVIORAL_ANALYSIS_RESULT_PROCESSED", userId,
                Map.of("analysisId", analysisId, "analysisType", analysisType, "riskScore", riskScore,
                    "riskLevel", riskLevel, "confidence", confidence, "requiresAction", requiresAction,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process behavioral analysis result event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("behavioral-analysis-results-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBehavioralAnalysisResultFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("behavioral-analysis-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for behavioral analysis result: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("behavioral-analysis-results-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Behavioral Analysis Result Circuit Breaker Triggered",
                String.format("Behavioral analysis result processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBehavioralAnalysisResult(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-behavioral-analysis-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Behavioral analysis result permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String analysisId = (String) event.get("analysisId");
            String userId = (String) event.get("userId");
            String analysisType = (String) event.get("analysisType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("BEHAVIORAL_ANALYSIS_RESULT_DLT_EVENT", userId,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "analysisId", analysisId, "analysisType", analysisType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Behavioral Analysis Result Dead Letter Event",
                String.format("Behavioral analysis result %s for user %s sent to DLT: %s", analysisId, userId, exceptionMessage),
                Map.of("analysisId", analysisId, "userId", userId, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse behavioral analysis result DLT event: {}", eventJson, ex);
        }
    }

    private void processTransactionBehaviorAnalysis(String analysisId, String userId, Double riskScore,
                                                  String riskLevel, Map<String, Object> analysisResults,
                                                  List<Map<String, Object>> behaviorPatterns,
                                                  List<String> anomalies, List<String> recommendations,
                                                  String correlationId) {
        try {
            behavioralAnalysisService.processTransactionBehaviorAnalysis(userId, analysisId,
                riskScore, riskLevel, analysisResults, behaviorPatterns, anomalies);

            if (!anomalies.isEmpty() && ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel))) {
                threatResponseService.handleTransactionBehaviorAnomalies(userId, analysisId,
                    anomalies, riskScore, correlationId);
            }

            log.info("Transaction behavior analysis processed: analysisId={}, userId={}, score={}",
                analysisId, userId, riskScore);

        } catch (Exception e) {
            log.error("Failed to process transaction behavior analysis: analysisId={}, userId={}",
                analysisId, userId, e);
            throw new RuntimeException("Transaction behavior analysis processing failed", e);
        }
    }

    private void processLoginBehaviorAnalysis(String analysisId, String userId, Double riskScore,
                                            String riskLevel, Map<String, Object> analysisResults,
                                            List<Map<String, Object>> behaviorPatterns,
                                            List<String> anomalies, List<String> recommendations,
                                            String correlationId) {
        try {
            behavioralAnalysisService.processLoginBehaviorAnalysis(userId, analysisId,
                riskScore, riskLevel, analysisResults, behaviorPatterns, anomalies);

            if (!anomalies.isEmpty() && ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel))) {
                threatResponseService.handleLoginBehaviorAnomalies(userId, analysisId,
                    anomalies, riskScore, correlationId);
            }

            log.info("Login behavior analysis processed: analysisId={}, userId={}, score={}",
                analysisId, userId, riskScore);

        } catch (Exception e) {
            log.error("Failed to process login behavior analysis: analysisId={}, userId={}",
                analysisId, userId, e);
            throw new RuntimeException("Login behavior analysis processing failed", e);
        }
    }

    private void processDeviceBehaviorAnalysis(String analysisId, String userId, Double riskScore,
                                             String riskLevel, Map<String, Object> analysisResults,
                                             List<Map<String, Object>> behaviorPatterns,
                                             List<String> anomalies, List<String> recommendations,
                                             String correlationId) {
        try {
            behavioralAnalysisService.processDeviceBehaviorAnalysis(userId, analysisId,
                riskScore, riskLevel, analysisResults, behaviorPatterns, anomalies);

            if (!anomalies.isEmpty() && ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel))) {
                threatResponseService.handleDeviceBehaviorAnomalies(userId, analysisId,
                    anomalies, riskScore, correlationId);
            }

            log.info("Device behavior analysis processed: analysisId={}, userId={}, score={}",
                analysisId, userId, riskScore);

        } catch (Exception e) {
            log.error("Failed to process device behavior analysis: analysisId={}, userId={}",
                analysisId, userId, e);
            throw new RuntimeException("Device behavior analysis processing failed", e);
        }
    }

    private void processSpendingBehaviorAnalysis(String analysisId, String userId, Double riskScore,
                                               String riskLevel, Map<String, Object> analysisResults,
                                               List<Map<String, Object>> behaviorPatterns,
                                               List<String> anomalies, List<String> recommendations,
                                               String correlationId) {
        try {
            behavioralAnalysisService.processSpendingBehaviorAnalysis(userId, analysisId,
                riskScore, riskLevel, analysisResults, behaviorPatterns, anomalies);

            if (!anomalies.isEmpty() && ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel))) {
                threatResponseService.handleSpendingBehaviorAnomalies(userId, analysisId,
                    anomalies, riskScore, correlationId);
            }

            log.info("Spending behavior analysis processed: analysisId={}, userId={}, score={}",
                analysisId, userId, riskScore);

        } catch (Exception e) {
            log.error("Failed to process spending behavior analysis: analysisId={}, userId={}",
                analysisId, userId, e);
            throw new RuntimeException("Spending behavior analysis processing failed", e);
        }
    }

    private void processTemporalBehaviorAnalysis(String analysisId, String userId, Double riskScore,
                                               String riskLevel, Map<String, Object> analysisResults,
                                               List<Map<String, Object>> behaviorPatterns,
                                               LocalDateTime analyzedAt, List<String> recommendations,
                                               String correlationId) {
        try {
            behavioralAnalysisService.processTemporalBehaviorAnalysis(userId, analysisId,
                riskScore, riskLevel, analysisResults, behaviorPatterns, analyzedAt);

            log.info("Temporal behavior analysis processed: analysisId={}, userId={}, score={}",
                analysisId, userId, riskScore);

        } catch (Exception e) {
            log.error("Failed to process temporal behavior analysis: analysisId={}, userId={}",
                analysisId, userId, e);
            throw new RuntimeException("Temporal behavior analysis processing failed", e);
        }
    }

    private void processComprehensiveBehaviorAnalysis(String analysisId, String userId, Double riskScore,
                                                    String riskLevel, Map<String, Object> analysisResults,
                                                    List<Map<String, Object>> behaviorPatterns,
                                                    List<String> anomalies, Map<String, Object> mlModelOutput,
                                                    List<String> recommendations, String correlationId) {
        try {
            behavioralAnalysisService.processComprehensiveBehaviorAnalysis(userId, analysisId,
                riskScore, riskLevel, analysisResults, behaviorPatterns, anomalies, mlModelOutput);

            if (!anomalies.isEmpty() && ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel))) {
                threatResponseService.handleComprehensiveBehaviorAnomalies(userId, analysisId,
                    anomalies, riskScore, mlModelOutput, correlationId);
            }

            log.info("Comprehensive behavior analysis processed: analysisId={}, userId={}, score={}",
                analysisId, userId, riskScore);

        } catch (Exception e) {
            log.error("Failed to process comprehensive behavior analysis: analysisId={}, userId={}",
                analysisId, userId, e);
            throw new RuntimeException("Comprehensive behavior analysis processing failed", e);
        }
    }

    private void processGenericAnalysisResult(String analysisId, String userId, String analysisType,
                                            Double riskScore, String riskLevel,
                                            Map<String, Object> analysisResults,
                                            List<String> recommendations, String correlationId) {
        try {
            behavioralAnalysisService.processGenericAnalysisResult(userId, analysisId,
                analysisType, riskScore, riskLevel, analysisResults);

            log.info("Generic analysis result processed: analysisId={}, userId={}, type={}",
                analysisId, userId, analysisType);

        } catch (Exception e) {
            log.error("Failed to process generic analysis result: analysisId={}, userId={}",
                analysisId, userId, e);
            throw new RuntimeException("Generic analysis result processing failed", e);
        }
    }

    private void updateUserRiskProfile(String userId, String analysisType, Double riskScore,
                                     String riskLevel, Map<String, Object> analysisResults,
                                     List<Map<String, Object>> behaviorPatterns,
                                     String modelVersion, Double confidence, String correlationId) {
        try {
            riskScoringService.updateUserRiskProfile(userId, analysisType, riskScore, riskLevel,
                analysisResults, behaviorPatterns, modelVersion, confidence);

            log.debug("User risk profile updated: userId={}, analysisType={}, score={}",
                userId, analysisType, riskScore);

        } catch (Exception e) {
            log.error("Failed to update user risk profile: userId={}, analysisType={}",
                userId, analysisType, e);
            // Don't throw exception as profile update failure shouldn't block processing
        }
    }

    private void executeRecommendedActions(String userId, String analysisId, String analysisType,
                                         String riskLevel, List<String> recommendations,
                                         List<String> anomalies, String correlationId) {
        try {
            threatResponseService.executeRecommendedActions(userId, analysisId, analysisType,
                riskLevel, recommendations, anomalies, correlationId);

            log.info("Recommended actions executed: userId={}, analysisId={}, actions={}",
                userId, analysisId, recommendations.size());

        } catch (Exception e) {
            log.error("Failed to execute recommended actions: userId={}, analysisId={}",
                userId, analysisId, e);
            // Don't throw exception as action execution failure shouldn't block processing
        }
    }

    private void storeAnalysisResults(String analysisId, String userId, String analysisType,
                                    Double riskScore, String riskLevel, Map<String, Object> analysisResults,
                                    List<Map<String, Object>> behaviorPatterns,
                                    Map<String, Object> mlModelOutput, String modelVersion,
                                    String correlationId) {
        try {
            behavioralAnalysisService.storeAnalysisResults(analysisId, userId, analysisType,
                riskScore, riskLevel, analysisResults, behaviorPatterns, mlModelOutput, modelVersion);

            log.debug("Analysis results stored: analysisId={}, userId={}, type={}",
                analysisId, userId, analysisType);

        } catch (Exception e) {
            log.error("Failed to store analysis results: analysisId={}, userId={}",
                analysisId, userId, e);
            // Don't throw exception as storage failure shouldn't block processing
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
}