package com.waqiti.dispute.kafka;

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
public class CircuitBreakerRecommendationsConsumer {

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
        successCounter = Counter.builder("circuit_breaker_recommendations_processed_total")
            .description("Total number of successfully processed circuit breaker recommendation events")
            .register(meterRegistry);
        errorCounter = Counter.builder("circuit_breaker_recommendations_errors_total")
            .description("Total number of circuit breaker recommendation processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("circuit_breaker_recommendations_processing_duration")
            .description("Time taken to process circuit breaker recommendation events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"circuit-breaker-recommendations"},
        groupId = "dispute-circuit-breaker-recommendations-group",
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
    @CircuitBreaker(name = "circuit-breaker-recommendations", fallbackMethod = "handleCircuitBreakerRecommendationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCircuitBreakerRecommendationEvent(
            @Payload Map<String, Object> recommendationEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        String correlationId = String.format("cb-recommendation-%s-p%d-o%d", circuitBreakerName, partition, offset);
        String eventKey = String.format("%s-%s-%s", circuitBreakerName,
            recommendationEvent.get("recommendationType"), recommendationEvent.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing circuit breaker recommendation: name={}, type={}, priority={}",
                circuitBreakerName, recommendationEvent.get("recommendationType"),
                recommendationEvent.get("priority"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Process circuit breaker recommendation
            processCircuitBreakerRecommendation(recommendationEvent, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSystemEvent("CIRCUIT_BREAKER_RECOMMENDATION_PROCESSED", circuitBreakerName,
                Map.of("circuitBreakerName", circuitBreakerName,
                    "recommendationType", recommendationEvent.get("recommendationType"),
                    "priority", recommendationEvent.get("priority"),
                    "autoApplicable", recommendationEvent.get("autoApplicable"),
                    "impact", recommendationEvent.get("impact"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process circuit breaker recommendation: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("circuit-breaker-recommendation-fallback-events", Map.of(
                "originalEvent", recommendationEvent, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCircuitBreakerRecommendationEventFallback(
            Map<String, Object> recommendationEvent,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        String correlationId = String.format("cb-recommendation-fallback-%s-p%d-o%d", circuitBreakerName, partition, offset);

        log.error("Circuit breaker fallback triggered for recommendation: name={}, error={}",
            circuitBreakerName, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("circuit-breaker-recommendations-dlq", Map.of(
            "originalEvent", recommendationEvent,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Circuit Breaker Recommendation Circuit Breaker Triggered",
                String.format("Circuit breaker %s recommendation processing failed: %s", circuitBreakerName, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCircuitBreakerRecommendationEvent(
            @Payload Map<String, Object> recommendationEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        String correlationId = String.format("dlt-cb-recommendation-%s-%d", circuitBreakerName, System.currentTimeMillis());

        log.error("Dead letter topic handler - Circuit breaker recommendation permanently failed: name={}, topic={}, error={}",
            circuitBreakerName, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSystemEvent("CIRCUIT_BREAKER_RECOMMENDATION_DLT_EVENT", circuitBreakerName,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "circuitBreakerName", circuitBreakerName, "recommendationEvent", recommendationEvent,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Circuit Breaker Recommendation Dead Letter Event",
                String.format("Circuit breaker %s recommendation sent to DLT: %s", circuitBreakerName, exceptionMessage),
                Map.of("circuitBreakerName", circuitBreakerName, "topic", topic, "correlationId", correlationId)
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

    private void processCircuitBreakerRecommendation(Map<String, Object> recommendationEvent, String correlationId) {
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        String recommendationType = (String) recommendationEvent.get("recommendationType");
        String priority = (String) recommendationEvent.getOrDefault("priority", "MEDIUM");
        Boolean autoApplicable = (Boolean) recommendationEvent.getOrDefault("autoApplicable", false);
        String impact = (String) recommendationEvent.getOrDefault("impact", "UNKNOWN");

        log.info("Processing circuit breaker recommendation: name={}, type={}, priority={}, autoApplicable={}, impact={}",
            circuitBreakerName, recommendationType, priority, autoApplicable, impact);

        // Process based on recommendation type
        switch (recommendationType) {
            case "THRESHOLD_ADJUSTMENT":
                processThresholdAdjustmentRecommendation(recommendationEvent, correlationId);
                break;

            case "TIMEOUT_CONFIGURATION":
                processTimeoutConfigurationRecommendation(recommendationEvent, correlationId);
                break;

            case "FALLBACK_STRATEGY":
                processFallbackStrategyRecommendation(recommendationEvent, correlationId);
                break;

            case "SCALING_RECOMMENDATION":
                processScalingRecommendation(recommendationEvent, correlationId);
                break;

            case "MONITORING_ENHANCEMENT":
                processMonitoringEnhancementRecommendation(recommendationEvent, correlationId);
                break;

            case "PERFORMANCE_OPTIMIZATION":
                processPerformanceOptimizationRecommendation(recommendationEvent, correlationId);
                break;

            case "MAINTENANCE_WINDOW":
                processMaintenanceWindowRecommendation(recommendationEvent, correlationId);
                break;

            case "CONFIGURATION_REVIEW":
                processConfigurationReviewRecommendation(recommendationEvent, correlationId);
                break;

            default:
                processGenericRecommendation(recommendationEvent, correlationId);
                break;
        }

        // Handle auto-applicable recommendations
        if (Boolean.TRUE.equals(autoApplicable)) {
            processAutoApplicableRecommendation(recommendationEvent, correlationId);
        } else {
            // Send to manual review
            sendToManualReview(recommendationEvent, correlationId);
        }

        // Track recommendation metrics
        trackRecommendationMetrics(circuitBreakerName, recommendationType, priority, autoApplicable, impact);

        log.info("Circuit breaker recommendation processed: name={}, type={}", circuitBreakerName, recommendationType);
    }

    private void processThresholdAdjustmentRecommendation(Map<String, Object> recommendationEvent, String correlationId) {
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        Map<String, Object> currentThresholds = (Map<String, Object>) recommendationEvent.get("currentThresholds");
        Map<String, Object> recommendedThresholds = (Map<String, Object>) recommendationEvent.get("recommendedThresholds");
        String reasoning = (String) recommendationEvent.get("reasoning");

        log.info("Processing threshold adjustment recommendation: name={}, reasoning={}",
            circuitBreakerName, reasoning);

        // Validate threshold recommendations
        validateThresholdRecommendations(circuitBreakerName, currentThresholds, recommendedThresholds, correlationId);

        // Calculate impact assessment
        assessThresholdImpact(circuitBreakerName, currentThresholds, recommendedThresholds, correlationId);

        // Generate implementation plan
        generateThresholdImplementationPlan(circuitBreakerName, recommendedThresholds, correlationId);

        // Send to configuration management
        sendToConfigurationManagement(circuitBreakerName, "THRESHOLD_UPDATE", recommendedThresholds, correlationId);
    }

    private void processTimeoutConfigurationRecommendation(Map<String, Object> recommendationEvent, String correlationId) {
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        Integer currentTimeout = (Integer) recommendationEvent.get("currentTimeout");
        Integer recommendedTimeout = (Integer) recommendationEvent.get("recommendedTimeout");
        String reasoning = (String) recommendationEvent.get("reasoning");

        log.info("Processing timeout configuration recommendation: name={}, current={}ms, recommended={}ms, reasoning={}",
            circuitBreakerName, currentTimeout, recommendedTimeout, reasoning);

        // Validate timeout recommendations
        if (isValidTimeoutRecommendation(currentTimeout, recommendedTimeout)) {
            // Create timeout update plan
            createTimeoutUpdatePlan(circuitBreakerName, currentTimeout, recommendedTimeout, correlationId);

            // Send to configuration management
            sendToConfigurationManagement(circuitBreakerName, "TIMEOUT_UPDATE",
                Map.of("timeout", recommendedTimeout, "reasoning", reasoning), correlationId);
        } else {
            log.warn("Invalid timeout recommendation rejected: name={}, recommended={}ms",
                circuitBreakerName, recommendedTimeout);
        }
    }

    private void processFallbackStrategyRecommendation(Map<String, Object> recommendationEvent, String correlationId) {
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        String currentStrategy = (String) recommendationEvent.get("currentFallbackStrategy");
        String recommendedStrategy = (String) recommendationEvent.get("recommendedFallbackStrategy");
        String reasoning = (String) recommendationEvent.get("reasoning");

        log.info("Processing fallback strategy recommendation: name={}, current={}, recommended={}, reasoning={}",
            circuitBreakerName, currentStrategy, recommendedStrategy, reasoning);

        // Evaluate fallback strategy viability
        evaluateFallbackStrategyViability(circuitBreakerName, recommendedStrategy, correlationId);

        // Create fallback implementation guide
        createFallbackImplementationGuide(circuitBreakerName, recommendedStrategy, correlationId);

        // Send to development team for review
        sendToDevelopmentTeam(circuitBreakerName, "FALLBACK_STRATEGY_UPDATE",
            Map.of("strategy", recommendedStrategy, "reasoning", reasoning), correlationId);
    }

    private void processScalingRecommendation(Map<String, Object> recommendationEvent, String correlationId) {
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        String scalingAction = (String) recommendationEvent.get("scalingAction"); // SCALE_UP, SCALE_DOWN, SCALE_OUT
        Integer recommendedInstances = (Integer) recommendationEvent.get("recommendedInstances");
        String reasoning = (String) recommendationEvent.get("reasoning");

        log.info("Processing scaling recommendation: name={}, action={}, instances={}, reasoning={}",
            circuitBreakerName, scalingAction, recommendedInstances, reasoning);

        // Evaluate scaling feasibility
        evaluateScalingFeasibility(circuitBreakerName, scalingAction, recommendedInstances, correlationId);

        // Create scaling plan
        createScalingPlan(circuitBreakerName, scalingAction, recommendedInstances, correlationId);

        // Send to infrastructure team
        sendToInfrastructureTeam(circuitBreakerName, scalingAction,
            Map.of("instances", recommendedInstances, "reasoning", reasoning), correlationId);
    }

    private void processMonitoringEnhancementRecommendation(Map<String, Object> recommendationEvent, String correlationId) {
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        List<String> enhancementTypes = (List<String>) recommendationEvent.get("enhancementTypes");
        String reasoning = (String) recommendationEvent.get("reasoning");

        log.info("Processing monitoring enhancement recommendation: name={}, enhancements={}, reasoning={}",
            circuitBreakerName, enhancementTypes, reasoning);

        // Process each enhancement type
        for (String enhancementType : enhancementTypes) {
            processMonitoringEnhancement(circuitBreakerName, enhancementType, correlationId);
        }

        // Create monitoring implementation plan
        createMonitoringImplementationPlan(circuitBreakerName, enhancementTypes, correlationId);

        // Send to monitoring team
        sendToMonitoringTeam(circuitBreakerName, "MONITORING_ENHANCEMENT",
            Map.of("enhancements", enhancementTypes, "reasoning", reasoning), correlationId);
    }

    private void processPerformanceOptimizationRecommendation(Map<String, Object> recommendationEvent, String correlationId) {
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        List<String> optimizationStrategies = (List<String>) recommendationEvent.get("optimizationStrategies");
        String expectedImprovement = (String) recommendationEvent.get("expectedImprovement");
        String reasoning = (String) recommendationEvent.get("reasoning");

        log.info("Processing performance optimization recommendation: name={}, strategies={}, improvement={}, reasoning={}",
            circuitBreakerName, optimizationStrategies, expectedImprovement, reasoning);

        // Evaluate optimization strategies
        evaluateOptimizationStrategies(circuitBreakerName, optimizationStrategies, correlationId);

        // Create performance improvement plan
        createPerformanceImprovementPlan(circuitBreakerName, optimizationStrategies, expectedImprovement, correlationId);

        // Send to performance team
        sendToPerformanceTeam(circuitBreakerName, "PERFORMANCE_OPTIMIZATION",
            Map.of("strategies", optimizationStrategies, "expectedImprovement", expectedImprovement,
                   "reasoning", reasoning), correlationId);
    }

    private void processMaintenanceWindowRecommendation(Map<String, Object> recommendationEvent, String correlationId) {
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        LocalDateTime recommendedStartTime = (LocalDateTime) recommendationEvent.get("recommendedStartTime");
        Integer durationMinutes = (Integer) recommendationEvent.get("durationMinutes");
        String maintenanceType = (String) recommendationEvent.get("maintenanceType");
        String reasoning = (String) recommendationEvent.get("reasoning");

        log.info("Processing maintenance window recommendation: name={}, start={}, duration={}min, type={}, reasoning={}",
            circuitBreakerName, recommendedStartTime, durationMinutes, maintenanceType, reasoning);

        // Validate maintenance window timing
        validateMaintenanceWindowTiming(circuitBreakerName, recommendedStartTime, durationMinutes, correlationId);

        // Create maintenance plan
        createMaintenancePlan(circuitBreakerName, recommendedStartTime, durationMinutes, maintenanceType, correlationId);

        // Send to operations team
        sendToOperationsTeam(circuitBreakerName, "MAINTENANCE_WINDOW",
            Map.of("startTime", recommendedStartTime, "duration", durationMinutes,
                   "type", maintenanceType, "reasoning", reasoning), correlationId);
    }

    private void processConfigurationReviewRecommendation(Map<String, Object> recommendationEvent, String correlationId) {
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        List<String> reviewAreas = (List<String>) recommendationEvent.get("reviewAreas");
        String priority = (String) recommendationEvent.get("priority");
        String reasoning = (String) recommendationEvent.get("reasoning");

        log.info("Processing configuration review recommendation: name={}, areas={}, priority={}, reasoning={}",
            circuitBreakerName, reviewAreas, priority, reasoning);

        // Create configuration review checklist
        createConfigurationReviewChecklist(circuitBreakerName, reviewAreas, correlationId);

        // Schedule configuration review
        scheduleConfigurationReview(circuitBreakerName, reviewAreas, priority, correlationId);

        // Send to configuration management team
        sendToConfigurationManagement(circuitBreakerName, "CONFIGURATION_REVIEW",
            Map.of("reviewAreas", reviewAreas, "priority", priority, "reasoning", reasoning), correlationId);
    }

    private void processGenericRecommendation(Map<String, Object> recommendationEvent, String correlationId) {
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        String recommendationType = (String) recommendationEvent.get("recommendationType");

        log.info("Processing generic recommendation: name={}, type={}", circuitBreakerName, recommendationType);

        // Store for manual review
        storeForManualReview(circuitBreakerName, recommendationEvent, correlationId);

        // Send to appropriate team based on type
        routeToAppropriateTeam(circuitBreakerName, recommendationEvent, correlationId);
    }

    private void processAutoApplicableRecommendation(Map<String, Object> recommendationEvent, String correlationId) {
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        String recommendationType = (String) recommendationEvent.get("recommendationType");

        log.info("Processing auto-applicable recommendation: name={}, type={}", circuitBreakerName, recommendationType);

        // Apply recommendation automatically
        applyRecommendationAutomatically(recommendationEvent, correlationId);

        // Send confirmation notification
        sendAutoApplicationConfirmation(circuitBreakerName, recommendationType, correlationId);

        // Monitor post-application metrics
        schedulePostApplicationMonitoring(circuitBreakerName, recommendationType, correlationId);
    }

    private void sendToManualReview(Map<String, Object> recommendationEvent, String correlationId) {
        String circuitBreakerName = (String) recommendationEvent.get("circuitBreakerName");
        String priority = (String) recommendationEvent.getOrDefault("priority", "MEDIUM");

        kafkaTemplate.send("manual-recommendations-queue", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "recommendationEvent", recommendationEvent,
            "priority", priority,
            "requiresApproval", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Notify appropriate team
        notifyTeamForManualReview(circuitBreakerName, recommendationEvent, correlationId);
    }

    private void trackRecommendationMetrics(String circuitBreakerName, String recommendationType,
                                           String priority, Boolean autoApplicable, String impact) {
        meterRegistry.counter("circuit_breaker_recommendations_total",
            "circuit_breaker", circuitBreakerName,
            "type", recommendationType,
            "priority", priority,
            "auto_applicable", autoApplicable.toString(),
            "impact", impact).increment();
    }

    // Helper methods for various recommendation processing
    private void validateThresholdRecommendations(String circuitBreakerName, Map<String, Object> current,
                                                 Map<String, Object> recommended, String correlationId) {
        kafkaTemplate.send("threshold-validation", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "currentThresholds", current,
            "recommendedThresholds", recommended,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void assessThresholdImpact(String circuitBreakerName, Map<String, Object> current,
                                      Map<String, Object> recommended, String correlationId) {
        kafkaTemplate.send("threshold-impact-assessment", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "currentThresholds", current,
            "recommendedThresholds", recommended,
            "assessmentType", "THRESHOLD_CHANGE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void generateThresholdImplementationPlan(String circuitBreakerName, Map<String, Object> thresholds, String correlationId) {
        kafkaTemplate.send("implementation-plans", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "planType", "THRESHOLD_UPDATE",
            "thresholds", thresholds,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void sendToConfigurationManagement(String circuitBreakerName, String actionType,
                                              Object configuration, String correlationId) {
        kafkaTemplate.send("configuration-management", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "actionType", actionType,
            "configuration", configuration,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private boolean isValidTimeoutRecommendation(Integer current, Integer recommended) {
        // Basic validation logic
        return recommended != null && recommended > 0 && recommended <= 60000 && // max 60 seconds
               (current == null || Math.abs(recommended - current) > 100); // significant change
    }

    private void createTimeoutUpdatePlan(String circuitBreakerName, Integer current, Integer recommended, String correlationId) {
        kafkaTemplate.send("timeout-update-plans", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "currentTimeout", current,
            "recommendedTimeout", recommended,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void evaluateFallbackStrategyViability(String circuitBreakerName, String strategy, String correlationId) {
        kafkaTemplate.send("fallback-strategy-evaluation", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "strategy", strategy,
            "evaluationType", "VIABILITY_ASSESSMENT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void createFallbackImplementationGuide(String circuitBreakerName, String strategy, String correlationId) {
        kafkaTemplate.send("implementation-guides", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "guideType", "FALLBACK_STRATEGY",
            "strategy", strategy,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void sendToDevelopmentTeam(String circuitBreakerName, String taskType, Object details, String correlationId) {
        kafkaTemplate.send("development-team-tasks", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "taskType", taskType,
            "details", details,
            "team", "DEVELOPMENT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void evaluateScalingFeasibility(String circuitBreakerName, String action, Integer instances, String correlationId) {
        kafkaTemplate.send("scaling-feasibility", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "scalingAction", action,
            "recommendedInstances", instances,
            "evaluationType", "FEASIBILITY_CHECK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void createScalingPlan(String circuitBreakerName, String action, Integer instances, String correlationId) {
        kafkaTemplate.send("scaling-plans", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "scalingAction", action,
            "targetInstances", instances,
            "planType", "SCALING_STRATEGY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void sendToInfrastructureTeam(String circuitBreakerName, String action, Object details, String correlationId) {
        kafkaTemplate.send("infrastructure-team-tasks", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "action", action,
            "details", details,
            "team", "INFRASTRUCTURE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processMonitoringEnhancement(String circuitBreakerName, String enhancementType, String correlationId) {
        kafkaTemplate.send("monitoring-enhancements", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "enhancementType", enhancementType,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void createMonitoringImplementationPlan(String circuitBreakerName, List<String> enhancements, String correlationId) {
        kafkaTemplate.send("monitoring-implementation-plans", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "enhancements", enhancements,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void sendToMonitoringTeam(String circuitBreakerName, String taskType, Object details, String correlationId) {
        kafkaTemplate.send("monitoring-team-tasks", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "taskType", taskType,
            "details", details,
            "team", "MONITORING",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void evaluateOptimizationStrategies(String circuitBreakerName, List<String> strategies, String correlationId) {
        kafkaTemplate.send("optimization-strategy-evaluation", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "strategies", strategies,
            "evaluationType", "STRATEGY_ASSESSMENT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void createPerformanceImprovementPlan(String circuitBreakerName, List<String> strategies,
                                                 String expectedImprovement, String correlationId) {
        kafkaTemplate.send("performance-improvement-plans", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "strategies", strategies,
            "expectedImprovement", expectedImprovement,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void sendToPerformanceTeam(String circuitBreakerName, String taskType, Object details, String correlationId) {
        kafkaTemplate.send("performance-team-tasks", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "taskType", taskType,
            "details", details,
            "team", "PERFORMANCE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void validateMaintenanceWindowTiming(String circuitBreakerName, LocalDateTime startTime,
                                                Integer duration, String correlationId) {
        kafkaTemplate.send("maintenance-window-validation", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "startTime", startTime,
            "duration", duration,
            "validationType", "TIMING_CHECK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void createMaintenancePlan(String circuitBreakerName, LocalDateTime startTime, Integer duration,
                                      String maintenanceType, String correlationId) {
        kafkaTemplate.send("maintenance-plans", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "startTime", startTime,
            "duration", duration,
            "maintenanceType", maintenanceType,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void sendToOperationsTeam(String circuitBreakerName, String taskType, Object details, String correlationId) {
        kafkaTemplate.send("operations-team-tasks", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "taskType", taskType,
            "details", details,
            "team", "OPERATIONS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void createConfigurationReviewChecklist(String circuitBreakerName, List<String> reviewAreas, String correlationId) {
        kafkaTemplate.send("configuration-review-checklists", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "reviewAreas", reviewAreas,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void scheduleConfigurationReview(String circuitBreakerName, List<String> reviewAreas,
                                           String priority, String correlationId) {
        kafkaTemplate.send("configuration-review-scheduling", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "reviewAreas", reviewAreas,
            "priority", priority,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void storeForManualReview(String circuitBreakerName, Map<String, Object> recommendation, String correlationId) {
        kafkaTemplate.send("manual-review-storage", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "recommendation", recommendation,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void routeToAppropriateTeam(String circuitBreakerName, Map<String, Object> recommendation, String correlationId) {
        String recommendationType = (String) recommendation.get("recommendationType");
        String team = determineAppropriateTeam(recommendationType);

        kafkaTemplate.send("team-routing", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "recommendation", recommendation,
            "targetTeam", team,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private String determineAppropriateTeam(String recommendationType) {
        switch (recommendationType) {
            case "THRESHOLD_ADJUSTMENT":
            case "TIMEOUT_CONFIGURATION":
                return "CONFIGURATION";
            case "SCALING_RECOMMENDATION":
                return "INFRASTRUCTURE";
            case "MONITORING_ENHANCEMENT":
                return "MONITORING";
            case "PERFORMANCE_OPTIMIZATION":
                return "PERFORMANCE";
            case "FALLBACK_STRATEGY":
                return "DEVELOPMENT";
            default:
                return "OPERATIONS";
        }
    }

    private void applyRecommendationAutomatically(Map<String, Object> recommendation, String correlationId) {
        kafkaTemplate.send("auto-recommendation-application", Map.of(
            "recommendation", recommendation,
            "applicationType", "AUTOMATIC",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void sendAutoApplicationConfirmation(String circuitBreakerName, String recommendationType, String correlationId) {
        notificationService.sendOperationalAlert(
            "Circuit Breaker Recommendation Auto-Applied",
            String.format("Recommendation %s for circuit breaker %s has been automatically applied",
                recommendationType, circuitBreakerName),
            "MEDIUM"
        );
    }

    private void schedulePostApplicationMonitoring(String circuitBreakerName, String recommendationType, String correlationId) {
        kafkaTemplate.send("post-application-monitoring", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "recommendationType", recommendationType,
            "monitoringDuration", "24_HOURS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void notifyTeamForManualReview(String circuitBreakerName, Map<String, Object> recommendation, String correlationId) {
        String recommendationType = (String) recommendation.get("recommendationType");
        String priority = (String) recommendation.getOrDefault("priority", "MEDIUM");

        notificationService.sendOperationalAlert(
            "Circuit Breaker Recommendation Requires Review",
            String.format("Circuit breaker %s has recommendation %s requiring manual review",
                circuitBreakerName, recommendationType),
            priority
        );
    }
}