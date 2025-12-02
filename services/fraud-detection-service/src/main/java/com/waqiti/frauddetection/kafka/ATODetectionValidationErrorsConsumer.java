package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.service.*;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for ATO detection validation errors
 * Handles validation error events from ATO detection system with error
 * analysis, data quality monitoring, and corrective action workflows
 * 
 * Critical for: Data quality, system reliability, error analysis
 * SLA: Must process validation errors within 15 seconds for error tracking
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ATODetectionValidationErrorsConsumer {

    private final ValidationErrorService validationErrorService;
    private final DataQualityService dataQualityService;
    private final ATODetectionService atoDetectionService;
    private final FraudNotificationService fraudNotificationService;
    private final SystemHealthService systemHealthService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // Metrics
    private final Counter validationErrorsCounter = Counter.builder("ato_validation_errors_total")
            .description("Total number of ATO validation errors processed")
            .register(metricsService.getMeterRegistry());

    private final Counter processingFailuresCounter = Counter.builder("ato_validation_errors_processing_failed_total")
            .description("Total number of ATO validation error events that failed processing")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("ato_validation_error_processing_duration")
            .description("Time taken to process ATO validation error events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"ato-detection-validation-errors"},
        groupId = "fraud-service-ato-validation-errors-processor",
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
    @CircuitBreaker(name = "ato-validation-errors-processor", fallbackMethod = "handleValidationErrorProcessingFailure")
    @Retry(name = "ato-validation-errors-processor")
    public void processValidationErrorEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();
        
        log.warn("Processing ATO validation error: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Validation error event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate error data
            ATOValidationErrorData errorData = extractValidationErrorData(event.getPayload());
            validateErrorEvent(errorData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process validation error
            processValidationError(errorData, event);

            // Record successful processing metrics
            validationErrorsCounter.increment();
            
            // Audit the error processing
            auditValidationErrorProcessing(errorData, event, "SUCCESS");

            log.info("Successfully processed ATO validation error: {} for account: {} - error type: {}", 
                    eventId, errorData.getAccountId(), errorData.getErrorType());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("Invalid ATO validation error event data: {}", eventId, e);
            handleEventValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process ATO validation error event: {}", eventId, e);
            processingFailuresCounter.increment();
            auditValidationErrorProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("ATO validation error event processing failed", e);

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

    private ATOValidationErrorData extractValidationErrorData(Map<String, Object> payload) throws JsonProcessingException {
        return ATOValidationErrorData.builder()
                .errorId(extractString(payload, "errorId"))
                .originalEventId(extractString(payload, "originalEventId"))
                .accountId(extractString(payload, "accountId"))
                .userId(extractString(payload, "userId"))
                .errorType(extractString(payload, "errorType"))
                .errorCode(extractString(payload, "errorCode"))
                .errorMessage(extractString(payload, "errorMessage"))
                .fieldName(extractString(payload, "fieldName"))
                .fieldValue(extractString(payload, "fieldValue"))
                .expectedFormat(extractString(payload, "expectedFormat"))
                .validationRule(extractString(payload, "validationRule"))
                .severity(extractString(payload, "severity"))
                .originalPayload(extractMap(payload, "originalPayload"))
                .validationContext(extractMap(payload, "validationContext"))
                .errorTimestamp(extractInstant(payload, "errorTimestamp"))
                .detectionTimestamp(extractInstant(payload, "detectionTimestamp"))
                .systemComponent(extractString(payload, "systemComponent"))
                .dataSource(extractString(payload, "dataSource"))
                .correctionSuggestions(extractStringList(payload, "correctionSuggestions"))
                .build();
    }

    private void validateErrorEvent(ATOValidationErrorData errorData) {
        if (errorData.getErrorId() == null || errorData.getErrorId().trim().isEmpty()) {
            throw new IllegalArgumentException("Error ID is required");
        }
        
        if (errorData.getErrorType() == null || errorData.getErrorType().trim().isEmpty()) {
            throw new IllegalArgumentException("Error type is required");
        }
        
        if (errorData.getErrorMessage() == null || errorData.getErrorMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Error message is required");
        }
        
        if (errorData.getErrorTimestamp() == null) {
            throw new IllegalArgumentException("Error timestamp is required");
        }

        List<String> validErrorTypes = List.of(
                "FIELD_VALIDATION", "FORMAT_VALIDATION", "RANGE_VALIDATION", 
                "REQUIRED_FIELD_MISSING", "INVALID_DATA_TYPE", "CONSTRAINT_VIOLATION",
                "SCHEMA_VALIDATION", "BUSINESS_RULE_VALIDATION"
        );
        if (!validErrorTypes.contains(errorData.getErrorType())) {
            throw new IllegalArgumentException("Invalid error type: " + errorData.getErrorType());
        }
    }

    private void processValidationError(ATOValidationErrorData errorData, GenericKafkaEvent event) {
        log.info("Processing ATO validation error - Type: {}, Field: {}, Account: {}, Message: {}", 
                errorData.getErrorType(), errorData.getFieldName(), 
                errorData.getAccountId(), errorData.getErrorMessage());

        try {
            // Record validation error for analysis
            String errorRecordId = validationErrorService.recordValidationError(errorData);

            // Analyze error patterns and trends
            ValidationErrorAnalysis analysis = analyzeValidationError(errorData);

            // Update data quality metrics
            updateDataQualityMetrics(errorData, analysis);

            // Determine if corrective action is needed
            if (requiresCorrectiveAction(errorData, analysis)) {
                executeCorrectiveActions(errorData, analysis);
            }

            // Check for systematic issues
            if (analysis.indicatesSystematicIssue()) {
                handleSystematicIssue(errorData, analysis);
            }

            // Update system health indicators
            updateSystemHealthIndicators(errorData);

            // Send notifications if needed
            sendValidationErrorNotifications(errorData, analysis);

            // Attempt data correction if possible
            attemptDataCorrection(errorData);

            log.info("ATO validation error processed - ErrorId: {}, RecordId: {}, RequiresAction: {}", 
                    errorData.getErrorId(), errorRecordId, requiresCorrectiveAction(errorData, analysis));

        } catch (Exception e) {
            log.error("Failed to process ATO validation error for account: {}", 
                    errorData.getAccountId(), e);
            
            // Fallback error handling
            handleValidationErrorProcessingFailure(errorData, e);
            
            throw new RuntimeException("Validation error processing failed", e);
        }
    }

    private ValidationErrorAnalysis analyzeValidationError(ATOValidationErrorData errorData) {
        // Analyze error frequency and patterns
        ErrorFrequencyAnalysis frequency = validationErrorService.analyzeErrorFrequency(
                errorData.getErrorType(),
                errorData.getFieldName(),
                errorData.getDataSource()
        );

        // Check for data quality trends
        DataQualityTrends trends = dataQualityService.analyzeDataQualityTrends(
                errorData.getDataSource(),
                errorData.getSystemComponent()
        );

        // Assess impact on ATO detection
        ATOImpactAssessment impact = atoDetectionService.assessValidationErrorImpact(
                errorData.getAccountId(),
                errorData.getErrorType(),
                errorData.getFieldName()
        );

        return ValidationErrorAnalysis.builder()
                .errorFrequency(frequency)
                .dataQualityTrends(trends)
                .atoImpact(impact)
                .requiresSystematicFix(frequency.getErrorCount() > 100) // High frequency indicates systematic issue
                .affectsSecurityDetection(impact.isSecurityCritical())
                .recommendedActions(generateRecommendedActions(frequency, trends, impact))
                .build();
    }

    private List<String> generateRecommendedActions(ErrorFrequencyAnalysis frequency, 
                                                   DataQualityTrends trends, 
                                                   ATOImpactAssessment impact) {
        List<String> actions = new java.util.ArrayList<>();

        if (frequency.getErrorCount() > 100) {
            actions.add("INVESTIGATE_DATA_SOURCE");
            actions.add("UPDATE_VALIDATION_RULES");
        }

        if (trends.isQualityDeclining()) {
            actions.add("IMPROVE_DATA_PIPELINE");
            actions.add("ENHANCE_DATA_VALIDATION");
        }

        if (impact.isSecurityCritical()) {
            actions.add("PRIORITY_FIX_REQUIRED");
            actions.add("NOTIFY_SECURITY_TEAM");
        }

        if (actions.isEmpty()) {
            actions.add("MONITOR_TREND");
        }

        return actions;
    }

    private void updateDataQualityMetrics(ATOValidationErrorData errorData, ValidationErrorAnalysis analysis) {
        // Update overall data quality score
        dataQualityService.updateDataQualityScore(
                errorData.getDataSource(),
                errorData.getErrorType(),
                analysis.getDataQualityTrends().getQualityScore()
        );

        // Track field-specific quality metrics
        dataQualityService.updateFieldQualityMetrics(
                errorData.getFieldName(),
                errorData.getErrorType(),
                errorData.getDataSource()
        );

        // Update system component health
        systemHealthService.updateComponentHealth(
                errorData.getSystemComponent(),
                "DATA_QUALITY",
                analysis.getDataQualityTrends().getHealthStatus()
        );
    }

    private boolean requiresCorrectiveAction(ATOValidationErrorData errorData, ValidationErrorAnalysis analysis) {
        return analysis.isRequiresSystematicFix() || 
               analysis.isAffectsSecurityDetection() ||
               "HIGH".equals(errorData.getSeverity());
    }

    private void executeCorrectiveActions(ATOValidationErrorData errorData, ValidationErrorAnalysis analysis) {
        for (String action : analysis.getRecommendedActions()) {
            try {
                switch (action) {
                    case "INVESTIGATE_DATA_SOURCE":
                        dataQualityService.triggerDataSourceInvestigation(errorData.getDataSource());
                        break;
                        
                    case "UPDATE_VALIDATION_RULES":
                        validationErrorService.reviewValidationRules(
                                errorData.getErrorType(), 
                                errorData.getFieldName()
                        );
                        break;
                        
                    case "IMPROVE_DATA_PIPELINE":
                        dataQualityService.flagDataPipelineForImprovement(errorData.getDataSource());
                        break;
                        
                    case "PRIORITY_FIX_REQUIRED":
                        validationErrorService.createPriorityTicket(errorData);
                        break;
                        
                    case "NOTIFY_SECURITY_TEAM":
                        fraudNotificationService.notifySecurityTeam(
                                "ATO Validation Error Affecting Security",
                                errorData
                        );
                        break;
                        
                    default:
                        log.debug("Standard action: {}", action);
                }
            } catch (Exception e) {
                log.error("Failed to execute corrective action: {}", action, e);
            }
        }
    }

    private void handleSystematicIssue(ATOValidationErrorData errorData, ValidationErrorAnalysis analysis) {
        log.warn("Systematic validation issue detected - ErrorType: {}, DataSource: {}, FrequencyCount: {}", 
                errorData.getErrorType(), errorData.getDataSource(), 
                analysis.getErrorFrequency().getErrorCount());

        // Create systematic issue ticket
        String issueId = validationErrorService.createSystematicIssueTicket(
                errorData.getErrorType(),
                errorData.getDataSource(),
                analysis.getErrorFrequency()
        );

        // Escalate to data engineering team
        fraudNotificationService.escalateToDataEngineering(
                "Systematic ATO Validation Issue",
                errorData,
                issueId
        );

        // Temporarily adjust validation rules if safe
        if (analysis.isSafeToAdjustRules()) {
            validationErrorService.temporarilyAdjustValidationRules(
                    errorData.getErrorType(),
                    errorData.getFieldName(),
                    issueId
            );
        }
    }

    private void updateSystemHealthIndicators(ATOValidationErrorData errorData) {
        // Update component health based on error frequency
        systemHealthService.recordValidationError(
                errorData.getSystemComponent(),
                errorData.getErrorType()
        );

        // Update overall system health score
        systemHealthService.updateSystemHealthScore(
                "ATO_DETECTION",
                calculateHealthImpact(errorData)
        );
    }

    private double calculateHealthImpact(ATOValidationErrorData errorData) {
        // Calculate impact based on error severity and type
        double impact = 0.1; // Base impact
        
        if ("HIGH".equals(errorData.getSeverity())) {
            impact += 0.3;
        } else if ("MEDIUM".equals(errorData.getSeverity())) {
            impact += 0.1;
        }
        
        if ("BUSINESS_RULE_VALIDATION".equals(errorData.getErrorType()) ||
            "SCHEMA_VALIDATION".equals(errorData.getErrorType())) {
            impact += 0.2; // Higher impact for critical validation types
        }
        
        return Math.min(1.0, impact);
    }

    private void sendValidationErrorNotifications(ATOValidationErrorData errorData, ValidationErrorAnalysis analysis) {
        // Send notifications based on severity and impact
        if ("HIGH".equals(errorData.getSeverity()) || analysis.isAffectsSecurityDetection()) {
            fraudNotificationService.sendValidationErrorAlert(
                    "High Severity ATO Validation Error",
                    errorData,
                    analysis
            );
        }

        // Send daily summary for medium/low severity errors
        if ("MEDIUM".equals(errorData.getSeverity()) || "LOW".equals(errorData.getSeverity())) {
            fraudNotificationService.addToDailySummary(
                    "validation_errors",
                    errorData
            );
        }
    }

    private void attemptDataCorrection(ATOValidationErrorData errorData) {
        if (errorData.getCorrectionSuggestions() != null && !errorData.getCorrectionSuggestions().isEmpty()) {
            try {
                // Attempt automatic correction for safe cases
                if (validationErrorService.isSafeForAutoCorrection(errorData.getErrorType())) {
                    boolean corrected = validationErrorService.attemptAutoCorrection(
                            errorData.getOriginalEventId(),
                            errorData.getFieldName(),
                            errorData.getCorrectionSuggestions().get(0)
                    );
                    
                    if (corrected) {
                        log.info("Successfully auto-corrected validation error: {}", errorData.getErrorId());
                        
                        // Retry ATO detection with corrected data
                        atoDetectionService.retryDetectionWithCorrectedData(
                                errorData.getOriginalEventId(),
                                errorData.getAccountId()
                        );
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to auto-correct validation error: {}", errorData.getErrorId(), e);
            }
        }
    }

    private void handleValidationErrorProcessingFailure(ATOValidationErrorData errorData, Exception error) {
        log.error("Failed to process validation error, applying fallback measures");
        
        try {
            // Record processing failure
            validationErrorService.recordProcessingFailure(
                    errorData != null ? errorData.getErrorId() : "unknown",
                    error.getMessage()
            );
            
            // Send failure notification
            fraudNotificationService.sendProcessingFailureAlert(
                    "ATO Validation Error Processing Failed",
                    errorData,
                    error.getMessage()
            );
            
        } catch (Exception e) {
            log.error("Fallback error handling also failed", e);
        }
    }

    private void handleEventValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("ATO validation error event validation failed for event: {}", event.getEventId(), e);
        
        auditService.auditSecurityEvent(
                "ATO_VALIDATION_ERROR_EVENT_VALIDATION_FAILED",
                null,
                "ATO validation error event validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );

        // Send to validation errors topic for analysis (meta-validation error)
        fraudNotificationService.sendMetaValidationErrorAlert(event, e.getMessage());
    }

    private void auditValidationErrorProcessing(ATOValidationErrorData errorData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "ATO_VALIDATION_ERROR_PROCESSED",
                    errorData != null ? errorData.getAccountId() : null,
                    String.format("ATO validation error event processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "errorId", errorData != null ? errorData.getErrorId() : "unknown",
                            "accountId", errorData != null ? errorData.getAccountId() : "unknown",
                            "errorType", errorData != null ? errorData.getErrorType() : "unknown",
                            "fieldName", errorData != null ? errorData.getFieldName() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit ATO validation error processing", e);
        }
    }

    @DltHandler
    public void handleDlt(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "kafka_dlt-original-topic", required = false) String originalTopic) {
        
        log.error("ATO validation error event sent to DLT - EventId: {}, OriginalTopic: {}", 
                event.getEventId(), originalTopic);

        try {
            ATOValidationErrorData errorData = extractValidationErrorData(event.getPayload());
            
            // Record DLT event
            validationErrorService.recordDLTEvent(errorData.getErrorId(), "Validation error processing failed");

            // Send DLT alert
            fraudNotificationService.sendDLTAlert(
                    "ATO Validation Error in DLT",
                    "Validation error event could not be processed - manual review required"
            );

            // Audit DLT handling
            auditService.auditSecurityEvent(
                    "ATO_VALIDATION_ERROR_DLT",
                    errorData.getAccountId(),
                    "ATO validation error event sent to Dead Letter Queue - manual review required",
                    Map.of(
                            "eventId", event.getEventId(),
                            "errorId", errorData.getErrorId(),
                            "accountId", errorData.getAccountId(),
                            "errorType", errorData.getErrorType(),
                            "originalTopic", originalTopic
                    )
            );

        } catch (Exception e) {
            log.error("Failed to handle ATO validation error DLT event: {}", event.getEventId(), e);
        }
    }

    // Circuit breaker fallback method
    public void handleValidationErrorProcessingFailure(GenericKafkaEvent event, String topic, int partition,
                                                      long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for ATO validation error processing - EventId: {}", 
                event.getEventId(), e);

        try {
            // Send system alert
            fraudNotificationService.sendSystemAlert(
                    "ATO Validation Error Circuit Breaker Open",
                    "ATO validation error processing is failing - data quality monitoring compromised"
            );

        } catch (Exception ex) {
            log.error("Failed to handle validation error circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of();
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
    public static class ATOValidationErrorData {
        private String errorId;
        private String originalEventId;
        private String accountId;
        private String userId;
        private String errorType;
        private String errorCode;
        private String errorMessage;
        private String fieldName;
        private String fieldValue;
        private String expectedFormat;
        private String validationRule;
        private String severity;
        private Map<String, Object> originalPayload;
        private Map<String, Object> validationContext;
        private Instant errorTimestamp;
        private Instant detectionTimestamp;
        private String systemComponent;
        private String dataSource;
        private List<String> correctionSuggestions;
    }

    @lombok.Data
    @lombok.Builder
    public static class ValidationErrorAnalysis {
        private ErrorFrequencyAnalysis errorFrequency;
        private DataQualityTrends dataQualityTrends;
        private ATOImpactAssessment atoImpact;
        private boolean requiresSystematicFix;
        private boolean affectsSecurityDetection;
        private List<String> recommendedActions;
        
        public boolean indicatesSystematicIssue() {
            return requiresSystematicFix;
        }
        
        public boolean isSafeToAdjustRules() {
            return !affectsSecurityDetection && errorFrequency.getErrorCount() > 50;
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class ErrorFrequencyAnalysis {
        private String errorType;
        private String fieldName;
        private Integer errorCount;
        private Double errorRate;
        private boolean trendingUp;
    }

    @lombok.Data
    @lombok.Builder
    public static class DataQualityTrends {
        private String dataSource;
        private Double qualityScore;
        private String healthStatus;
        private boolean qualityDeclining;
    }

    @lombok.Data
    @lombok.Builder
    public static class ATOImpactAssessment {
        private String accountId;
        private boolean securityCritical;
        private String impactLevel;
        private List<String> affectedDetections;
    }
}