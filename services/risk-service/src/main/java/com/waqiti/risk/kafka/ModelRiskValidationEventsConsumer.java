package com.waqiti.risk.kafka;

import com.waqiti.common.events.ModelRiskValidationEvent;
import com.waqiti.risk.domain.ModelRiskValidation;
import com.waqiti.risk.repository.ModelRiskValidationRepository;
import com.waqiti.risk.service.ModelRiskService;
import com.waqiti.risk.service.ModelValidationService;
import com.waqiti.risk.service.RiskMetricsService;
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
public class ModelRiskValidationEventsConsumer {

    private final ModelRiskValidationRepository modelRiskValidationRepository;
    private final ModelRiskService modelRiskService;
    private final ModelValidationService modelValidationService;
    private final RiskMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("model_risk_validation_events_processed_total")
            .description("Total number of successfully processed model risk validation events")
            .register(meterRegistry);
        errorCounter = Counter.builder("model_risk_validation_events_errors_total")
            .description("Total number of model risk validation event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("model_risk_validation_events_processing_duration")
            .description("Time taken to process model risk validation events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"model-risk-validation-events"},
        groupId = "model-risk-validation-events-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "model-risk-validation-events", fallbackMethod = "handleModelRiskValidationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleModelRiskValidationEvent(
            @Payload ModelRiskValidationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("model-risk-validation-%s-p%d-o%d", event.getModelId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getModelId(), event.getValidationType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing model risk validation event: modelId={}, validationType={}, validationResult={}, severity={}",
                event.getModelId(), event.getValidationType(), event.getValidationResult(), event.getSeverity());

            cleanExpiredEntries();

            switch (event.getValidationType()) {
                case MODEL_PERFORMANCE_VALIDATION:
                    processModelPerformanceValidation(event, correlationId);
                    break;
                case BACKTESTING_VALIDATION:
                    processBacktestingValidation(event, correlationId);
                    break;
                case STRESS_TESTING_VALIDATION:
                    processStressTestingValidation(event, correlationId);
                    break;
                case DATA_QUALITY_VALIDATION:
                    processDataQualityValidation(event, correlationId);
                    break;
                case MODEL_GOVERNANCE_VALIDATION:
                    processModelGovernanceValidation(event, correlationId);
                    break;
                case BENCHMARKING_VALIDATION:
                    processBenchmarkingValidation(event, correlationId);
                    break;
                case SENSITIVITY_ANALYSIS:
                    processSensitivityAnalysis(event, correlationId);
                    break;
                case CHAMPION_CHALLENGER_VALIDATION:
                    processChampionChallengerValidation(event, correlationId);
                    break;
                case REGULATORY_COMPLIANCE_VALIDATION:
                    processRegulatoryComplianceValidation(event, correlationId);
                    break;
                case MODEL_DOCUMENTATION_VALIDATION:
                    processModelDocumentationValidation(event, correlationId);
                    break;
                case CONCEPTUAL_SOUNDNESS_VALIDATION:
                    processConceptualSoundnessValidation(event, correlationId);
                    break;
                case IMPLEMENTATION_VALIDATION:
                    processImplementationValidation(event, correlationId);
                    break;
                case ONGOING_MONITORING_VALIDATION:
                    processOngoingMonitoringValidation(event, correlationId);
                    break;
                case OUTCOME_ANALYSIS:
                    processOutcomeAnalysis(event, correlationId);
                    break;
                case MODEL_INVENTORY_VALIDATION:
                    processModelInventoryValidation(event, correlationId);
                    break;
                default:
                    log.warn("Unknown model risk validation type: {}", event.getValidationType());
                    processGenericModelRiskValidation(event, correlationId);
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("MODEL_RISK_VALIDATION_EVENT_PROCESSED", event.getModelId(),
                Map.of("validationType", event.getValidationType(), "validationResult", event.getValidationResult(),
                    "severity", event.getSeverity(), "modelType", event.getModelType(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process model risk validation event: {}", e.getMessage(), e);

            kafkaTemplate.send("model-risk-validation-events-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleModelRiskValidationEventFallback(
            ModelRiskValidationEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("model-risk-validation-fallback-%s-p%d-o%d", event.getModelId(), partition, offset);

        log.error("Circuit breaker fallback triggered for model risk validation event: modelId={}, error={}",
            event.getModelId(), ex.getMessage());

        kafkaTemplate.send("model-risk-validation-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Model Risk Validation Event Circuit Breaker Triggered",
                String.format("CRITICAL: Model risk validation event processing failed for model %s: %s",
                    event.getModelId(), ex.getMessage()),
                Map.of("modelId", event.getModelId(), "validationType", event.getValidationType(), "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltModelRiskValidationEvent(
            @Payload ModelRiskValidationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-model-risk-validation-%s-%d", event.getModelId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Model risk validation event permanently failed: modelId={}, topic={}, error={}",
            event.getModelId(), topic, exceptionMessage);

        auditService.logRiskEvent("MODEL_RISK_VALIDATION_EVENT_DLT", event.getModelId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "validationType", event.getValidationType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Model Risk Validation Event Dead Letter Event",
                String.format("CRITICAL: Model risk validation event for model %s sent to DLT: %s",
                    event.getModelId(), exceptionMessage),
                Map.of("modelId", event.getModelId(), "validationType", event.getValidationType(),
                       "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) return false;
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
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processModelPerformanceValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setPerformanceMetrics(event.getPerformanceMetrics());
        validation.setPerformanceThresholds(event.getPerformanceThresholds());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processModelPerformanceValidation(event.getModelId(), event.getPerformanceMetrics());

        if ("FAIL".equals(event.getValidationResult())) {
            kafkaTemplate.send("model-risk-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "MODEL_PERFORMANCE_VALIDATION_FAILURE",
                "severity", event.getSeverity(),
                "performanceMetrics", event.getPerformanceMetrics(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            notificationService.sendHighPriorityNotification("model-risk-team",
                "Model Performance Validation Failed",
                String.format("Model %s failed performance validation", event.getModelId()),
                correlationId);
        }

        metricsService.recordModelRiskValidation("MODEL_PERFORMANCE", event.getValidationResult());
        log.info("Model performance validation processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processBacktestingValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setBacktestResults(event.getBacktestResults());
        validation.setBacktestPeriod(event.getBacktestPeriod());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processBacktestingValidation(event.getModelId(), event.getBacktestResults());

        if ("FAIL".equals(event.getValidationResult()) || "WARNING".equals(event.getValidationResult())) {
            kafkaTemplate.send("model-risk-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "BACKTESTING_VALIDATION_ISSUE",
                "severity", event.getSeverity(),
                "backtestResults", event.getBacktestResults(),
                "backtestPeriod", event.getBacktestPeriod(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordModelRiskValidation("BACKTESTING", event.getValidationResult());
        log.info("Backtesting validation processed: modelId={}, result={}, period={}",
            event.getModelId(), event.getValidationResult(), event.getBacktestPeriod());
    }

    private void processStressTestingValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setStressTestResults(event.getStressTestResults());
        validation.setStressScenarios(event.getStressScenarios());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processStressTestingValidation(event.getModelId(), event.getStressTestResults());

        if ("FAIL".equals(event.getValidationResult())) {
            kafkaTemplate.send("model-risk-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "STRESS_TESTING_VALIDATION_FAILURE",
                "severity", event.getSeverity(),
                "stressTestResults", event.getStressTestResults(),
                "stressScenarios", event.getStressScenarios(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            kafkaTemplate.send("stress-test-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "MODEL_STRESS_TEST_FAILURE",
                "stressTestResults", event.getStressTestResults(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordModelRiskValidation("STRESS_TESTING", event.getValidationResult());
        log.warn("Stress testing validation processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processDataQualityValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setDataQualityMetrics(event.getDataQualityMetrics());
        validation.setDataQualityIssues(event.getDataQualityIssues());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processDataQualityValidation(event.getModelId(), event.getDataQualityMetrics());

        if ("FAIL".equals(event.getValidationResult()) || "WARNING".equals(event.getValidationResult())) {
            kafkaTemplate.send("data-quality-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "MODEL_DATA_QUALITY_ISSUE",
                "severity", event.getSeverity(),
                "dataQualityMetrics", event.getDataQualityMetrics(),
                "dataQualityIssues", event.getDataQualityIssues(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordModelRiskValidation("DATA_QUALITY", event.getValidationResult());
        log.info("Data quality validation processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processModelGovernanceValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setGovernanceCompliance(event.getGovernanceCompliance());
        validation.setGovernanceGaps(event.getGovernanceGaps());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processModelGovernanceValidation(event.getModelId(), event.getGovernanceCompliance());

        if ("FAIL".equals(event.getValidationResult()) || "NON_COMPLIANT".equals(event.getValidationResult())) {
            kafkaTemplate.send("governance-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "MODEL_GOVERNANCE_VIOLATION",
                "severity", event.getSeverity(),
                "governanceCompliance", event.getGovernanceCompliance(),
                "governanceGaps", event.getGovernanceGaps(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            notificationService.sendHighPriorityNotification("model-governance-team",
                "Model Governance Validation Failed",
                String.format("Model %s failed governance validation", event.getModelId()),
                correlationId);
        }

        metricsService.recordModelRiskValidation("MODEL_GOVERNANCE", event.getValidationResult());
        log.warn("Model governance validation processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processBenchmarkingValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setBenchmarkResults(event.getBenchmarkResults());
        validation.setBenchmarkComparisons(event.getBenchmarkComparisons());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processBenchmarkingValidation(event.getModelId(), event.getBenchmarkResults());

        if ("UNDERPERFORMING".equals(event.getValidationResult()) || "FAIL".equals(event.getValidationResult())) {
            kafkaTemplate.send("model-risk-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "BENCHMARKING_VALIDATION_ISSUE",
                "severity", event.getSeverity(),
                "benchmarkResults", event.getBenchmarkResults(),
                "benchmarkComparisons", event.getBenchmarkComparisons(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordModelRiskValidation("BENCHMARKING", event.getValidationResult());
        log.info("Benchmarking validation processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processSensitivityAnalysis(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setSensitivityResults(event.getSensitivityResults());
        validation.setSensitivityFactors(event.getSensitivityFactors());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processSensitivityAnalysis(event.getModelId(), event.getSensitivityResults());

        if ("HIGH_SENSITIVITY".equals(event.getValidationResult()) || "UNSTABLE".equals(event.getValidationResult())) {
            kafkaTemplate.send("model-risk-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "HIGH_SENSITIVITY_DETECTED",
                "severity", event.getSeverity(),
                "sensitivityResults", event.getSensitivityResults(),
                "sensitivityFactors", event.getSensitivityFactors(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordModelRiskValidation("SENSITIVITY_ANALYSIS", event.getValidationResult());
        log.info("Sensitivity analysis processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processChampionChallengerValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setChampionChallengerResults(event.getChampionChallengerResults());
        validation.setChallengerModels(event.getChallengerModels());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processChampionChallengerValidation(event.getModelId(), event.getChampionChallengerResults());

        if ("CHAMPION_OUTPERFORMED".equals(event.getValidationResult())) {
            kafkaTemplate.send("model-risk-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "CHAMPION_MODEL_OUTPERFORMED",
                "severity", event.getSeverity(),
                "championChallengerResults", event.getChampionChallengerResults(),
                "challengerModels", event.getChallengerModels(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            kafkaTemplate.send("model-governance-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "CONSIDER_MODEL_REPLACEMENT",
                "championChallengerResults", event.getChampionChallengerResults(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordModelRiskValidation("CHAMPION_CHALLENGER", event.getValidationResult());
        log.info("Champion challenger validation processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processRegulatoryComplianceValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setRegulatoryCompliance(event.getRegulatoryCompliance());
        validation.setRegulatoryRequirements(event.getRegulatoryRequirements());
        validation.setSeverity("HIGH"); // Regulatory issues are always high severity
        modelRiskValidationRepository.save(validation);

        modelRiskService.processRegulatoryComplianceValidation(event.getModelId(), event.getRegulatoryCompliance());

        if ("NON_COMPLIANT".equals(event.getValidationResult()) || "FAIL".equals(event.getValidationResult())) {
            kafkaTemplate.send("regulatory-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "MODEL_REGULATORY_NON_COMPLIANCE",
                "severity", "HIGH",
                "regulatoryCompliance", event.getRegulatoryCompliance(),
                "regulatoryRequirements", event.getRegulatoryRequirements(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            notificationService.sendCriticalAlert(
                "Model Regulatory Compliance Failure",
                String.format("CRITICAL: Model %s failed regulatory compliance validation", event.getModelId()),
                Map.of("modelId", event.getModelId(), "compliance", event.getRegulatoryCompliance(), "correlationId", correlationId)
            );
        }

        metricsService.recordModelRiskValidation("REGULATORY_COMPLIANCE", event.getValidationResult());
        log.error("Regulatory compliance validation processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processModelDocumentationValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setDocumentationCompleteness(event.getDocumentationCompleteness());
        validation.setDocumentationGaps(event.getDocumentationGaps());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processModelDocumentationValidation(event.getModelId(), event.getDocumentationCompleteness());

        if ("INCOMPLETE".equals(event.getValidationResult()) || "INADEQUATE".equals(event.getValidationResult())) {
            kafkaTemplate.send("model-governance-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "MODEL_DOCUMENTATION_INADEQUATE",
                "severity", event.getSeverity(),
                "documentationCompleteness", event.getDocumentationCompleteness(),
                "documentationGaps", event.getDocumentationGaps(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordModelRiskValidation("MODEL_DOCUMENTATION", event.getValidationResult());
        log.info("Model documentation validation processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processConceptualSoundnessValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setConceptualSoundness(event.getConceptualSoundness());
        validation.setConceptualIssues(event.getConceptualIssues());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processConceptualSoundnessValidation(event.getModelId(), event.getConceptualSoundness());

        if ("UNSOUND".equals(event.getValidationResult()) || "QUESTIONABLE".equals(event.getValidationResult())) {
            kafkaTemplate.send("model-risk-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "CONCEPTUAL_SOUNDNESS_ISSUE",
                "severity", "HIGH",
                "conceptualSoundness", event.getConceptualSoundness(),
                "conceptualIssues", event.getConceptualIssues(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            notificationService.sendHighPriorityNotification("model-risk-team",
                "Model Conceptual Soundness Issue",
                String.format("Model %s has conceptual soundness issues", event.getModelId()),
                correlationId);
        }

        metricsService.recordModelRiskValidation("CONCEPTUAL_SOUNDNESS", event.getValidationResult());
        log.warn("Conceptual soundness validation processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processImplementationValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setImplementationDetails(event.getImplementationDetails());
        validation.setImplementationIssues(event.getImplementationIssues());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processImplementationValidation(event.getModelId(), event.getImplementationDetails());

        if ("INCORRECT_IMPLEMENTATION".equals(event.getValidationResult()) || "FAIL".equals(event.getValidationResult())) {
            kafkaTemplate.send("model-risk-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "MODEL_IMPLEMENTATION_ISSUE",
                "severity", event.getSeverity(),
                "implementationDetails", event.getImplementationDetails(),
                "implementationIssues", event.getImplementationIssues(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordModelRiskValidation("IMPLEMENTATION", event.getValidationResult());
        log.warn("Implementation validation processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processOngoingMonitoringValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setMonitoringResults(event.getMonitoringResults());
        validation.setMonitoringAlerts(event.getMonitoringAlerts());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processOngoingMonitoringValidation(event.getModelId(), event.getMonitoringResults());

        if ("MONITORING_GAPS".equals(event.getValidationResult()) || "ALERTS_TRIGGERED".equals(event.getValidationResult())) {
            kafkaTemplate.send("model-monitoring-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "ONGOING_MONITORING_ISSUE",
                "severity", event.getSeverity(),
                "monitoringResults", event.getMonitoringResults(),
                "monitoringAlerts", event.getMonitoringAlerts(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordModelRiskValidation("ONGOING_MONITORING", event.getValidationResult());
        log.info("Ongoing monitoring validation processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processOutcomeAnalysis(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setOutcomeAnalysisResults(event.getOutcomeAnalysisResults());
        validation.setOutcomeMetrics(event.getOutcomeMetrics());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processOutcomeAnalysis(event.getModelId(), event.getOutcomeAnalysisResults());

        if ("POOR_OUTCOMES".equals(event.getValidationResult()) || "SIGNIFICANT_DEVIATION".equals(event.getValidationResult())) {
            kafkaTemplate.send("model-risk-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "POOR_MODEL_OUTCOMES",
                "severity", event.getSeverity(),
                "outcomeAnalysisResults", event.getOutcomeAnalysisResults(),
                "outcomeMetrics", event.getOutcomeMetrics(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordModelRiskValidation("OUTCOME_ANALYSIS", event.getValidationResult());
        log.info("Outcome analysis processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processModelInventoryValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        validation.setInventoryCompleteness(event.getInventoryCompleteness());
        validation.setInventoryGaps(event.getInventoryGaps());
        modelRiskValidationRepository.save(validation);

        modelRiskService.processModelInventoryValidation(event.getModelId(), event.getInventoryCompleteness());

        if ("INCOMPLETE_INVENTORY".equals(event.getValidationResult()) || "MISSING_MODELS".equals(event.getValidationResult())) {
            kafkaTemplate.send("model-governance-alerts", Map.of(
                "modelId", event.getModelId(),
                "alertType", "MODEL_INVENTORY_GAPS",
                "severity", event.getSeverity(),
                "inventoryCompleteness", event.getInventoryCompleteness(),
                "inventoryGaps", event.getInventoryGaps(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordModelRiskValidation("MODEL_INVENTORY", event.getValidationResult());
        log.info("Model inventory validation processed: modelId={}, result={}", event.getModelId(), event.getValidationResult());
    }

    private void processGenericModelRiskValidation(ModelRiskValidationEvent event, String correlationId) {
        ModelRiskValidation validation = createModelRiskValidation(event, correlationId);
        modelRiskValidationRepository.save(validation);

        modelRiskService.processGenericModelRiskValidation(event.getModelId(), event.getValidationType());

        metricsService.recordModelRiskValidation("GENERIC", event.getValidationResult());
        log.info("Generic model risk validation processed: modelId={}, validationType={}, result={}",
            event.getModelId(), event.getValidationType(), event.getValidationResult());
    }

    private ModelRiskValidation createModelRiskValidation(ModelRiskValidationEvent event, String correlationId) {
        return ModelRiskValidation.builder()
            .modelId(event.getModelId())
            .modelName(event.getModelName())
            .modelType(event.getModelType())
            .validationType(event.getValidationType())
            .validationResult(event.getValidationResult())
            .severity(event.getSeverity())
            .validatedAt(LocalDateTime.now())
            .validationStatus("COMPLETED")
            .correlationId(correlationId)
            .validationDetails(event.getValidationDetails())
            .recommendedActions(event.getRecommendedActions())
            .build();
    }
}