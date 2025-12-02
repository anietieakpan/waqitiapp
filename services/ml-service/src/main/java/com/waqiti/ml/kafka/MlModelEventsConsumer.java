package com.waqiti.ml.kafka;

import com.waqiti.common.events.MlModelEvent;
import com.waqiti.ml.service.ModelManagementService;
import com.waqiti.ml.service.ModelDeploymentService;
import com.waqiti.ml.service.ModelMetricsService;
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
public class MlModelEventsConsumer {

    private final ModelManagementService modelManagementService;
    private final ModelDeploymentService deploymentService;
    private final ModelMetricsService metricsService;
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
        successCounter = Counter.builder("ml_model_events_processed_total")
            .description("Total number of successfully processed ML model events")
            .register(meterRegistry);
        errorCounter = Counter.builder("ml_model_events_errors_total")
            .description("Total number of ML model events processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("ml_model_events_processing_duration")
            .description("Time taken to process ML model events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"ml-model-events", "model-lifecycle-events", "model-management-events"},
        groupId = "ml-model-events-service-group",
        containerFactory = "mlKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "ml-model-events", fallbackMethod = "handleMlModelEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleMlModelEvent(
            @Payload MlModelEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("model-%s-p%d-o%d", event.getModelId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getModelId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing ML model event: modelId={}, eventType={}, version={}",
                event.getModelId(), event.getEventType(), event.getModelVersion());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case MODEL_CREATED:
                    processModelCreated(event, correlationId);
                    break;

                case MODEL_TRAINED:
                    processModelTrained(event, correlationId);
                    break;

                case MODEL_VALIDATED:
                    processModelValidated(event, correlationId);
                    break;

                case MODEL_DEPLOYED:
                    processModelDeployed(event, correlationId);
                    break;

                case MODEL_PROMOTED:
                    processModelPromoted(event, correlationId);
                    break;

                case MODEL_DEPRECATED:
                    processModelDeprecated(event, correlationId);
                    break;

                case MODEL_RETIRED:
                    processModelRetired(event, correlationId);
                    break;

                case MODEL_PERFORMANCE_DEGRADED:
                    processModelPerformanceDegraded(event, correlationId);
                    break;

                case MODEL_RETRAINED:
                    processModelRetrained(event, correlationId);
                    break;

                case MODEL_ROLLBACK:
                    processModelRollback(event, correlationId);
                    break;

                default:
                    log.warn("Unknown ML model event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logMlEvent("ML_MODEL_EVENT_PROCESSED", event.getModelId(),
                Map.of("eventType", event.getEventType(), "modelVersion", event.getModelVersion(),
                    "modelType", event.getModelType(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process ML model event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("ml-model-events-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleMlModelEventFallback(
            MlModelEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("model-fallback-%s-p%d-o%d", event.getModelId(), partition, offset);

        log.error("Circuit breaker fallback triggered for ML model event: modelId={}, error={}",
            event.getModelId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("ml-model-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to ML team
        try {
            notificationService.sendOperationalAlert(
                "ML Model Event Circuit Breaker Triggered",
                String.format("ML model %s event processing failed: %s", event.getModelId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltMlModelEvent(
            @Payload MlModelEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-model-%s-%d", event.getModelId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - ML model event permanently failed: modelId={}, topic={}, error={}",
            event.getModelId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logMlEvent("ML_MODEL_EVENT_DLT", event.getModelId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "ML Model Event Dead Letter",
                String.format("ML model %s event sent to DLT: %s", event.getModelId(), exceptionMessage),
                Map.of("modelId", event.getModelId(), "topic", topic, "correlationId", correlationId)
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

    private void processModelCreated(MlModelEvent event, String correlationId) {
        // Register new model in model registry
        modelManagementService.registerModel(
            event.getModelId(),
            event.getModelName(),
            event.getModelType(),
            event.getModelVersion(),
            event.getCreatedBy(),
            event.getModelMetadata(),
            correlationId
        );

        // Initialize model tracking
        metricsService.initializeModelTracking(
            event.getModelId(),
            event.getModelType(),
            event.getBusinessUseCase()
        );

        // Send model creation notification
        notificationService.sendNotification(
            event.getCreatedBy(),
            "ML Model Created",
            String.format("Your ML model '%s' (ID: %s) has been successfully created and registered.",
                event.getModelName(), event.getModelId()),
            correlationId
        );

        // Schedule initial validation
        kafkaTemplate.send("model-validation-queue", Map.of(
            "modelId", event.getModelId(),
            "modelVersion", event.getModelVersion(),
            "validationType", "INITIAL",
            "priority", "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.incrementModelsCreated(event.getModelType());

        log.info("Model created and registered: modelId={}, type={}, version={}",
            event.getModelId(), event.getModelType(), event.getModelVersion());
    }

    private void processModelTrained(MlModelEvent event, String correlationId) {
        // Update model with training results
        modelManagementService.updateModelTrainingResults(
            event.getModelId(),
            event.getModelVersion(),
            event.getTrainingMetrics(),
            event.getTrainingDatasetId(),
            event.getTrainingDuration(),
            event.getHyperparameters(),
            correlationId
        );

        // Record training metrics
        metricsService.recordTrainingMetrics(
            event.getModelId(),
            event.getTrainingMetrics(),
            event.getTrainingDuration()
        );

        // Send for validation if training was successful
        if (event.getTrainingStatus().equals("SUCCESS")) {
            kafkaTemplate.send("model-validation-queue", Map.of(
                "modelId", event.getModelId(),
                "modelVersion", event.getModelVersion(),
                "validationType", "POST_TRAINING",
                "trainingMetrics", event.getTrainingMetrics(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            // Handle training failure
            kafkaTemplate.send("model-training-failures", Map.of(
                "modelId", event.getModelId(),
                "modelVersion", event.getModelVersion(),
                "failureReason", event.getFailureReason(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Generate training report
        kafkaTemplate.send("model-training-reports", Map.of(
            "modelId", event.getModelId(),
            "modelVersion", event.getModelVersion(),
            "trainingMetrics", event.getTrainingMetrics(),
            "trainingStatus", event.getTrainingStatus(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.incrementModelsTrained(event.getModelType(), event.getTrainingStatus());

        log.info("Model training completed: modelId={}, version={}, status={}",
            event.getModelId(), event.getModelVersion(), event.getTrainingStatus());
    }

    private void processModelValidated(MlModelEvent event, String correlationId) {
        // Update model validation results
        modelManagementService.updateModelValidationResults(
            event.getModelId(),
            event.getModelVersion(),
            event.getValidationMetrics(),
            event.getValidationStatus(),
            event.getValidationDatasetId(),
            event.getValidatedBy(),
            correlationId
        );

        // Check if model meets deployment criteria
        boolean meetsDeploymentCriteria = modelManagementService.meetsDeploymentCriteria(
            event.getModelId(),
            event.getValidationMetrics()
        );

        if (meetsDeploymentCriteria && event.getValidationStatus().equals("PASSED")) {
            // Queue for deployment approval
            kafkaTemplate.send("model-deployment-approval-queue", Map.of(
                "modelId", event.getModelId(),
                "modelVersion", event.getModelVersion(),
                "validationMetrics", event.getValidationMetrics(),
                "recommendedAction", "APPROVE_FOR_DEPLOYMENT",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            // Handle validation failure or criteria not met
            kafkaTemplate.send("model-validation-failures", Map.of(
                "modelId", event.getModelId(),
                "modelVersion", event.getModelVersion(),
                "validationStatus", event.getValidationStatus(),
                "failureReasons", event.getValidationIssues(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Update model registry status
        modelManagementService.updateModelStatus(
            event.getModelId(),
            event.getValidationStatus().equals("PASSED") ? "VALIDATED" : "VALIDATION_FAILED",
            correlationId
        );

        metricsService.recordValidationMetrics(
            event.getModelId(),
            event.getValidationMetrics(),
            event.getValidationStatus()
        );

        log.info("Model validation completed: modelId={}, version={}, status={}, meetsDeploymentCriteria={}",
            event.getModelId(), event.getModelVersion(), event.getValidationStatus(), meetsDeploymentCriteria);
    }

    private void processModelDeployed(MlModelEvent event, String correlationId) {
        // Update deployment status
        deploymentService.recordDeployment(
            event.getModelId(),
            event.getModelVersion(),
            event.getDeploymentEnvironment(),
            event.getDeploymentConfiguration(),
            event.getDeployedBy(),
            correlationId
        );

        // Start performance monitoring
        metricsService.startPerformanceMonitoring(
            event.getModelId(),
            event.getDeploymentEnvironment(),
            event.getExpectedPerformanceMetrics()
        );

        // Send deployment notification
        notificationService.sendOperationalAlert(
            "ML Model Deployed",
            String.format("ML model %s version %s has been deployed to %s environment",
                event.getModelId(), event.getModelVersion(), event.getDeploymentEnvironment()),
            "INFO"
        );

        // Schedule performance evaluation
        kafkaTemplate.send("model-performance-evaluation-schedule", Map.of(
            "modelId", event.getModelId(),
            "modelVersion", event.getModelVersion(),
            "environment", event.getDeploymentEnvironment(),
            "evaluationInterval", "DAILY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Update model status
        modelManagementService.updateModelStatus(event.getModelId(), "DEPLOYED", correlationId);

        metricsService.incrementModelsDeployed(event.getModelType(), event.getDeploymentEnvironment());

        log.info("Model deployed: modelId={}, version={}, environment={}",
            event.getModelId(), event.getModelVersion(), event.getDeploymentEnvironment());
    }

    private void processModelPromoted(MlModelEvent event, String correlationId) {
        // Promote model to production
        deploymentService.promoteModelToProduction(
            event.getModelId(),
            event.getModelVersion(),
            event.getFromEnvironment(),
            event.getToEnvironment(),
            event.getPromotedBy(),
            event.getPromotionReason(),
            correlationId
        );

        // Update traffic routing
        deploymentService.updateTrafficRouting(
            event.getModelId(),
            event.getModelVersion(),
            event.getTrafficPercentage(),
            correlationId
        );

        // Send promotion notification
        notificationService.sendOperationalAlert(
            "ML Model Promoted",
            String.format("ML model %s version %s has been promoted from %s to %s with %s%% traffic",
                event.getModelId(), event.getModelVersion(),
                event.getFromEnvironment(), event.getToEnvironment(), event.getTrafficPercentage()),
            "HIGH"
        );

        // Start A/B testing if configured
        if (event.getEnableAbTesting()) {
            kafkaTemplate.send("ml-ab-test-events", Map.of(
                "modelId", event.getModelId(),
                "modelVersion", event.getModelVersion(),
                "testType", "CHAMPION_CHALLENGER",
                "trafficSplit", event.getTrafficPercentage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.incrementModelsPromoted(event.getModelType());

        log.info("Model promoted: modelId={}, version={}, from={}, to={}, traffic={}%",
            event.getModelId(), event.getModelVersion(),
            event.getFromEnvironment(), event.getToEnvironment(), event.getTrafficPercentage());
    }

    private void processModelDeprecated(MlModelEvent event, String correlationId) {
        // Mark model as deprecated
        modelManagementService.deprecateModel(
            event.getModelId(),
            event.getModelVersion(),
            event.getDeprecationReason(),
            event.getDeprecatedBy(),
            event.getEndOfLifeDate(),
            correlationId
        );

        // Schedule gradual traffic reduction
        deploymentService.scheduleTrafficReduction(
            event.getModelId(),
            event.getModelVersion(),
            event.getTrafficReductionPlan(),
            correlationId
        );

        // Send deprecation notification
        notificationService.sendOperationalAlert(
            "ML Model Deprecated",
            String.format("ML model %s version %s has been deprecated. End of life: %s. Reason: %s",
                event.getModelId(), event.getModelVersion(),
                event.getEndOfLifeDate(), event.getDeprecationReason()),
            "MEDIUM"
        );

        // Stop new feature development for deprecated model
        kafkaTemplate.send("model-development-restrictions", Map.of(
            "modelId", event.getModelId(),
            "restriction", "NO_NEW_FEATURES",
            "reason", "MODEL_DEPRECATED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.incrementModelsDeprecated(event.getModelType());

        log.info("Model deprecated: modelId={}, version={}, reason={}, endOfLife={}",
            event.getModelId(), event.getModelVersion(), event.getDeprecationReason(), event.getEndOfLifeDate());
    }

    private void processModelRetired(MlModelEvent event, String correlationId) {
        // Retire model from all environments
        deploymentService.retireModel(
            event.getModelId(),
            event.getModelVersion(),
            event.getRetiredBy(),
            event.getRetirementReason(),
            correlationId
        );

        // Archive model artifacts
        modelManagementService.archiveModelArtifacts(
            event.getModelId(),
            event.getModelVersion(),
            event.getArchiveLocation(),
            correlationId
        );

        // Stop all monitoring and alerts
        metricsService.stopModelMonitoring(event.getModelId(), event.getModelVersion());

        // Send retirement notification
        notificationService.sendOperationalAlert(
            "ML Model Retired",
            String.format("ML model %s version %s has been retired and archived. Reason: %s",
                event.getModelId(), event.getModelVersion(), event.getRetirementReason()),
            "INFO"
        );

        // Update model registry
        modelManagementService.updateModelStatus(event.getModelId(), "RETIRED", correlationId);

        metricsService.incrementModelsRetired(event.getModelType());

        log.info("Model retired: modelId={}, version={}, reason={}",
            event.getModelId(), event.getModelVersion(), event.getRetirementReason());
    }

    private void processModelPerformanceDegraded(MlModelEvent event, String correlationId) {
        // Record performance degradation
        metricsService.recordPerformanceDegradation(
            event.getModelId(),
            event.getModelVersion(),
            event.getPerformanceMetrics(),
            event.getDegradationSeverity(),
            correlationId
        );

        // Send performance alert
        String alertLevel = event.getDegradationSeverity().equals("CRITICAL") ? "CRITICAL" : "HIGH";
        notificationService.sendAlert(
            "ML Model Performance Degraded",
            String.format("ML model %s version %s performance has degraded. Severity: %s. Metrics: %s",
                event.getModelId(), event.getModelVersion(),
                event.getDegradationSeverity(), event.getPerformanceMetrics()),
            alertLevel
        );

        // Trigger automatic remediation if configured
        if (modelManagementService.hasAutomaticRemediation(event.getModelId())) {
            kafkaTemplate.send("model-remediation-actions", Map.of(
                "modelId", event.getModelId(),
                "modelVersion", event.getModelVersion(),
                "remediationType", "PERFORMANCE_DEGRADATION",
                "severity", event.getDegradationSeverity(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Consider model retraining
        if (event.getDegradationSeverity().equals("CRITICAL")) {
            kafkaTemplate.send("model-retraining-recommendations", Map.of(
                "modelId", event.getModelId(),
                "reason", "CRITICAL_PERFORMANCE_DEGRADATION",
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.incrementPerformanceDegradations(event.getModelType(), event.getDegradationSeverity());

        log.warn("Model performance degraded: modelId={}, version={}, severity={}, metrics={}",
            event.getModelId(), event.getModelVersion(), event.getDegradationSeverity(), event.getPerformanceMetrics());
    }

    private void processModelRetrained(MlModelEvent event, String correlationId) {
        // Update model with retraining results
        modelManagementService.updateModelRetrainingResults(
            event.getModelId(),
            event.getModelVersion(),
            event.getNewModelVersion(),
            event.getRetrainingMetrics(),
            event.getRetrainingReason(),
            correlationId
        );

        // Compare performance with previous version
        var performanceComparison = metricsService.compareModelPerformance(
            event.getModelId(),
            event.getModelVersion(),
            event.getNewModelVersion()
        );

        // Send retraining completion notification
        notificationService.sendOperationalAlert(
            "ML Model Retrained",
            String.format("ML model %s has been retrained. New version: %s. Performance comparison: %s",
                event.getModelId(), event.getNewModelVersion(), performanceComparison.getSummary()),
            "INFO"
        );

        // Queue for validation and deployment if performance improved
        if (performanceComparison.isImproved()) {
            kafkaTemplate.send("model-validation-queue", Map.of(
                "modelId", event.getModelId(),
                "modelVersion", event.getNewModelVersion(),
                "validationType", "POST_RETRAINING",
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.incrementModelsRetrained(event.getModelType());
        metricsService.recordRetrainingMetrics(event.getModelId(), event.getRetrainingMetrics());

        log.info("Model retrained: modelId={}, oldVersion={}, newVersion={}, performanceImproved={}",
            event.getModelId(), event.getModelVersion(), event.getNewModelVersion(), performanceComparison.isImproved());
    }

    private void processModelRollback(MlModelEvent event, String correlationId) {
        // Execute model rollback
        deploymentService.rollbackModel(
            event.getModelId(),
            event.getCurrentVersion(),
            event.getRollbackToVersion(),
            event.getRollbackReason(),
            event.getRolledBackBy(),
            correlationId
        );

        // Update traffic routing
        deploymentService.updateTrafficRouting(
            event.getModelId(),
            event.getRollbackToVersion(),
            100, // Full traffic to rollback version
            correlationId
        );

        // Send rollback alert
        notificationService.sendCriticalAlert(
            "ML Model Rollback Executed",
            String.format("URGENT: ML model %s has been rolled back from version %s to %s. Reason: %s",
                event.getModelId(), event.getCurrentVersion(), event.getRollbackToVersion(), event.getRollbackReason()),
            Map.of("modelId", event.getModelId(), "correlationId", correlationId)
        );

        // Create incident report
        kafkaTemplate.send("incident-reports", Map.of(
            "type", "MODEL_ROLLBACK",
            "modelId", event.getModelId(),
            "severity", "HIGH",
            "description", String.format("Model %s rolled back due to: %s", event.getModelId(), event.getRollbackReason()),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Update model status
        modelManagementService.updateModelStatus(event.getModelId(), "ROLLED_BACK", correlationId);

        metricsService.incrementModelRollbacks(event.getModelType());

        log.error("Model rollback executed: modelId={}, from={}, to={}, reason={}",
            event.getModelId(), event.getCurrentVersion(), event.getRollbackToVersion(), event.getRollbackReason());
    }
}