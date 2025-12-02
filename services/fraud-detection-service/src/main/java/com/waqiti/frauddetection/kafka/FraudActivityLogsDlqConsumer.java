package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.math.MoneyMath;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.common.notification.NotificationService;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade DLQ consumer for fraud activity logs
 * Handles critical fraud detection events that failed normal processing
 * Provides emergency fraud protection and executive escalation
 *
 * Critical for: Fraud prevention, risk management, regulatory compliance
 * SLA: Must process DLQ fraud events within 30 seconds for emergency protection
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FraudActivityLogsDlqConsumer {

    private final FraudDetectionService fraudDetectionService;
    private final FraudIncidentService fraudIncidentService;
    private final FraudPreventionService fraudPreventionService;
    private final EmergencyFraudProtectionService emergencyProtectionService;
    private final FraudAnalyticsService fraudAnalyticsService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 48-hour TTL for DLQ events
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 48;

    // Metrics
    private final Counter dlqEventCounter = Counter.builder("fraud_activity_logs_dlq_processed_total")
            .description("Total number of fraud activity DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter criticalFraudDlqCounter = Counter.builder("critical_fraud_dlq_events_total")
            .description("Total number of critical fraud DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("fraud_dlq_processing_duration")
            .description("Time taken to process fraud activity DLQ events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"fraud-activity-logs-dlq"},
        groupId = "fraud-service-dlq-processor",
        containerFactory = "dlqKafkaListenerContainerFactory",
        concurrency = "2"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 8000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "fraud-dlq-processor", fallbackMethod = "handleFraudDlqFailure")
    @Retry(name = "fraud-dlq-processor")
    public void processFraudActivityLogsDlq(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.error("FRAUD DLQ: Processing failed fraud activity log: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Fraud DLQ event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate fraud data
            FraudActivityData fraudData = extractFraudData(event.getPayload());
            validateFraudData(fraudData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Emergency fraud assessment
            FraudDlqAssessment assessment = assessDlqFraudEvent(fraudData, event);

            // Process DLQ fraud event
            processDlqFraudEvent(fraudData, assessment, event);

            // Record successful processing metrics
            dlqEventCounter.increment();

            if ("CRITICAL".equals(assessment.getRiskLevel())) {
                criticalFraudDlqCounter.increment();
            }

            // Audit the DLQ processing
            auditFraudDlqProcessing(fraudData, event, assessment, "SUCCESS");

            log.error("FRAUD DLQ: Successfully processed DLQ event: {} - Risk: {} Type: {} Action: {}",
                    eventId, assessment.getRiskLevel(), fraudData.getFraudType(), assessment.getEmergencyAction());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("FRAUD DLQ: Invalid fraud DLQ data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("FRAUD DLQ: Failed to process fraud DLQ event: {}", eventId, e);
            auditFraudDlqProcessing(null, event, null, "FAILED: " + e.getMessage());
            throw new RuntimeException("Fraud DLQ processing failed", e);

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

    private FraudActivityData extractFraudData(Map<String, Object> payload) {
        return FraudActivityData.builder()
                .eventId(extractString(payload, "eventId"))
                .accountId(extractString(payload, "accountId"))
                .userId(extractString(payload, "userId"))
                .transactionId(extractString(payload, "transactionId"))
                .fraudType(extractString(payload, "fraudType"))
                .riskScore(extractDouble(payload, "riskScore"))
                .riskLevel(extractString(payload, "riskLevel"))
                .suspiciousPatterns(extractStringList(payload, "suspiciousPatterns"))
                .fraudIndicators(extractStringList(payload, "fraudIndicators"))
                .originalEvent(extractMap(payload, "originalEvent"))
                .failureReason(extractString(payload, "failureReason"))
                .retryCount(extractInteger(payload, "retryCount"))
                .dlqTimestamp(extractInstant(payload, "dlqTimestamp"))
                .originalTimestamp(extractInstant(payload, "originalTimestamp"))
                .deviceInfo(extractMap(payload, "deviceInfo"))
                .locationData(extractMap(payload, "locationData"))
                .transactionAmount(extractDouble(payload, "transactionAmount"))
                .transactionCurrency(extractString(payload, "transactionCurrency"))
                .merchantInfo(extractMap(payload, "merchantInfo"))
                .behaviorAnalysis(extractMap(payload, "behaviorAnalysis"))
                .mlModelResults(extractMap(payload, "mlModelResults"))
                .complianceFlags(extractStringList(payload, "complianceFlags"))
                .customerSegment(extractString(payload, "customerSegment"))
                .build();
    }

    private void validateFraudData(FraudActivityData fraudData) {
        if (fraudData.getEventId() == null || fraudData.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (fraudData.getAccountId() == null || fraudData.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }

        if (fraudData.getFraudType() == null || fraudData.getFraudType().trim().isEmpty()) {
            throw new IllegalArgumentException("Fraud type is required");
        }

        if (fraudData.getRiskScore() == null || fraudData.getRiskScore() < 0 || fraudData.getRiskScore() > 100) {
            throw new IllegalArgumentException("Valid risk score (0-100) is required");
        }

        List<String> validRiskLevels = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
        if (fraudData.getRiskLevel() == null || !validRiskLevels.contains(fraudData.getRiskLevel())) {
            throw new IllegalArgumentException("Valid risk level is required");
        }
    }

    private FraudDlqAssessment assessDlqFraudEvent(FraudActivityData fraudData, GenericKafkaEvent event) {
        // Enhanced risk assessment for DLQ events
        String enhancedRiskLevel = determineEnhancedRiskLevel(fraudData);
        String emergencyAction = determineEmergencyAction(fraudData, enhancedRiskLevel);
        boolean requiresImmediateAction = requiresImmediateAction(fraudData, enhancedRiskLevel);
        List<String> protectionMeasures = determineProtectionMeasures(fraudData, enhancedRiskLevel);
        String businessImpact = assessBusinessImpact(fraudData);

        return FraudDlqAssessment.builder()
                .riskLevel(enhancedRiskLevel)
                .emergencyAction(emergencyAction)
                .requiresImmediateAction(requiresImmediateAction)
                .protectionMeasures(protectionMeasures)
                .businessImpact(businessImpact)
                .executiveEscalation(determineExecutiveEscalation(fraudData, enhancedRiskLevel))
                .customerNotificationRequired(determineCustomerNotification(fraudData))
                .regulatoryReporting(determineRegulatoryReporting(fraudData))
                .build();
    }

    private String determineEnhancedRiskLevel(FraudActivityData fraudData) {
        // DLQ events automatically get elevated risk assessment
        if (fraudData.getRiskScore() >= 80 || "CRITICAL".equals(fraudData.getRiskLevel())) {
            return "CRITICAL";
        } else if (fraudData.getRiskScore() >= 60 || "HIGH".equals(fraudData.getRiskLevel())) {
            return "HIGH";
        } else if (fraudData.getRiskScore() >= 40) {
            return "ELEVATED";
        } else {
            return "MEDIUM"; // Even low-risk events in DLQ get elevated to medium
        }
    }

    private String determineEmergencyAction(FraudActivityData fraudData, String riskLevel) {
        if ("CRITICAL".equals(riskLevel)) {
            if (fraudData.getFraudType().contains("ACCOUNT_TAKEOVER")) {
                return "IMMEDIATE_ACCOUNT_FREEZE";
            } else if (fraudData.getFraudType().contains("PAYMENT_FRAUD")) {
                return "PAYMENT_CHANNEL_LOCKDOWN";
            } else {
                return "COMPREHENSIVE_SECURITY_LOCKDOWN";
            }
        } else if ("HIGH".equals(riskLevel)) {
            return "ENHANCED_MONITORING_AND_RESTRICTIONS";
        } else {
            return "INCREASED_VERIFICATION_REQUIREMENTS";
        }
    }

    private boolean requiresImmediateAction(FraudActivityData fraudData, String riskLevel) {
        return "CRITICAL".equals(riskLevel) ||
               fraudData.getFraudType().contains("ACCOUNT_TAKEOVER") ||
               fraudData.getFraudType().contains("PAYMENT_FRAUD") ||
               (fraudData.getTransactionAmount() != null && fraudData.getTransactionAmount() > 10000);
    }

    private List<String> determineProtectionMeasures(FraudActivityData fraudData, String riskLevel) {
        List<String> measures = List.of();

        if ("CRITICAL".equals(riskLevel)) {
            measures = List.of(
                "IMMEDIATE_ACCOUNT_FREEZE",
                "TRANSACTION_BLOCKING",
                "DEVICE_BLACKLISTING",
                "IP_ADDRESS_BLOCKING",
                "MERCHANT_ALERT",
                "LAW_ENFORCEMENT_NOTIFICATION"
            );
        } else if ("HIGH".equals(riskLevel)) {
            measures = List.of(
                "ENHANCED_AUTHENTICATION",
                "TRANSACTION_LIMITS",
                "VELOCITY_CONTROLS",
                "DEVICE_VERIFICATION",
                "LOCATION_VERIFICATION"
            );
        } else {
            measures = List.of(
                "ADDITIONAL_VERIFICATION",
                "ENHANCED_MONITORING",
                "BEHAVIOR_ANALYSIS"
            );
        }

        return measures;
    }

    private String assessBusinessImpact(FraudActivityData fraudData) {
        if (fraudData.getTransactionAmount() != null) {
            if (fraudData.getTransactionAmount() > 50000) {
                return "SEVERE";
            } else if (fraudData.getTransactionAmount() > 10000) {
                return "HIGH";
            } else if (fraudData.getTransactionAmount() > 1000) {
                return "MEDIUM";
            }
        }
        return "LOW";
    }

    private boolean determineExecutiveEscalation(FraudActivityData fraudData, String riskLevel) {
        return "CRITICAL".equals(riskLevel) ||
               (fraudData.getTransactionAmount() != null && fraudData.getTransactionAmount() > 25000) ||
               fraudData.getFraudType().contains("ACCOUNT_TAKEOVER");
    }

    private boolean determineCustomerNotification(FraudActivityData fraudData) {
        return fraudData.getFraudType().contains("ACCOUNT_TAKEOVER") ||
               fraudData.getFraudType().contains("PAYMENT_FRAUD") ||
               (fraudData.getTransactionAmount() != null && fraudData.getTransactionAmount() > 500);
    }

    private boolean determineRegulatoryReporting(FraudActivityData fraudData) {
        return (fraudData.getTransactionAmount() != null && fraudData.getTransactionAmount() > 10000) ||
               fraudData.getFraudType().contains("MONEY_LAUNDERING") ||
               fraudData.getComplianceFlags().contains("SAR_REQUIRED");
    }

    private void processDlqFraudEvent(FraudActivityData fraudData, FraudDlqAssessment assessment, GenericKafkaEvent event) {
        log.error("FRAUD DLQ: Processing DLQ fraud event - Account: {}, Type: {}, Risk: {}, Action: {}",
                fraudData.getAccountId(), fraudData.getFraudType(),
                assessment.getRiskLevel(), assessment.getEmergencyAction());

        try {
            // Create high-priority fraud incident
            String incidentId = fraudIncidentService.createDlqFraudIncident(fraudData, assessment);

            // Execute emergency protection measures
            executeEmergencyProtection(fraudData, assessment, incidentId);

            // Send immediate notifications
            sendImmediateNotifications(fraudData, assessment, incidentId);

            // Apply protection measures
            applyProtectionMeasures(fraudData, assessment, incidentId);

            // Update fraud analytics
            updateFraudAnalytics(fraudData, assessment);

            // Executive escalation if required
            if (assessment.isExecutiveEscalation()) {
                escalateToExecutives(fraudData, assessment, incidentId);
            }

            // Customer notification if required
            if (assessment.isCustomerNotificationRequired()) {
                notifyCustomer(fraudData, assessment);
            }

            // Regulatory reporting if required
            if (assessment.isRegulatoryReporting()) {
                initiateRegulatoryReporting(fraudData, assessment, incidentId);
            }

            log.error("FRAUD DLQ: DLQ event processed - IncidentId: {}, ProtectionApplied: {}, BusinessImpact: {}",
                    incidentId, assessment.getProtectionMeasures().size(), assessment.getBusinessImpact());

        } catch (Exception e) {
            log.error("FRAUD DLQ: Failed to process DLQ fraud event for account: {}", fraudData.getAccountId(), e);

            // Emergency fallback procedures
            executeEmergencyFallback(fraudData, e);

            throw new RuntimeException("Fraud DLQ processing failed", e);
        }
    }

    private void executeEmergencyProtection(FraudActivityData fraudData, FraudDlqAssessment assessment, String incidentId) {
        // Immediate emergency actions based on assessment
        switch (assessment.getEmergencyAction()) {
            case "IMMEDIATE_ACCOUNT_FREEZE":
                emergencyProtectionService.freezeAccount(fraudData.getAccountId(), incidentId, "DLQ_FRAUD_PROTECTION");
                break;

            case "PAYMENT_CHANNEL_LOCKDOWN":
                emergencyProtectionService.lockdownPaymentChannels(fraudData.getAccountId(), incidentId);
                break;

            case "COMPREHENSIVE_SECURITY_LOCKDOWN":
                emergencyProtectionService.comprehensiveSecurityLockdown(fraudData.getAccountId(), incidentId);
                break;

            case "ENHANCED_MONITORING_AND_RESTRICTIONS":
                emergencyProtectionService.applyEnhancedMonitoring(fraudData.getAccountId(), incidentId);
                break;

            default:
                emergencyProtectionService.applyBasicProtection(fraudData.getAccountId(), incidentId);
        }
    }

    private void sendImmediateNotifications(FraudActivityData fraudData, FraudDlqAssessment assessment, String incidentId) {
        // Critical and high-risk DLQ events require immediate escalation
        if ("CRITICAL".equals(assessment.getRiskLevel()) || "HIGH".equals(assessment.getRiskLevel())) {
            notificationService.sendCriticalAlert(
                    "FRAUD DLQ EVENT - IMMEDIATE ACTION REQUIRED",
                    String.format("Critical fraud event failed processing and is now in DLQ: Account %s, Type: %s, Risk: %s",
                            fraudData.getAccountId(), fraudData.getFraudType(), assessment.getRiskLevel()),
                    Map.of(
                            "accountId", fraudData.getAccountId(),
                            "fraudType", fraudData.getFraudType(),
                            "riskLevel", assessment.getRiskLevel(),
                            "incidentId", incidentId,
                            "emergencyAction", assessment.getEmergencyAction()
                    )
            );

            // Page fraud team
            notificationService.pageSecurityTeam(
                    "FRAUD_DLQ_CRITICAL",
                    fraudData.getFraudType(),
                    assessment.getRiskLevel(),
                    incidentId
            );
        }
    }

    private void applyProtectionMeasures(FraudActivityData fraudData, FraudDlqAssessment assessment, String incidentId) {
        for (String measure : assessment.getProtectionMeasures()) {
            try {
                switch (measure) {
                    case "IMMEDIATE_ACCOUNT_FREEZE":
                        fraudPreventionService.freezeAccount(fraudData.getAccountId(), incidentId);
                        break;

                    case "TRANSACTION_BLOCKING":
                        fraudPreventionService.blockTransactions(fraudData.getAccountId(), incidentId);
                        break;

                    case "DEVICE_BLACKLISTING":
                        if (fraudData.getDeviceInfo() != null) {
                            fraudPreventionService.blacklistDevice(fraudData.getDeviceInfo(), incidentId);
                        }
                        break;

                    case "IP_ADDRESS_BLOCKING":
                        if (fraudData.getLocationData() != null) {
                            fraudPreventionService.blockIpAddress(fraudData.getLocationData(), incidentId);
                        }
                        break;

                    case "ENHANCED_AUTHENTICATION":
                        fraudPreventionService.requireEnhancedAuth(fraudData.getAccountId(), incidentId);
                        break;

                    case "TRANSACTION_LIMITS":
                        fraudPreventionService.applyTransactionLimits(fraudData.getAccountId(), incidentId);
                        break;

                    default:
                        fraudPreventionService.applyGenericProtection(measure, fraudData.getAccountId(), incidentId);
                }
            } catch (Exception e) {
                log.error("Failed to apply protection measure: {}", measure, e);
            }
        }
    }

    private void updateFraudAnalytics(FraudActivityData fraudData, FraudDlqAssessment assessment) {
        // Update fraud patterns and analytics
        fraudAnalyticsService.updateDlqFraudPatterns(
                fraudData.getFraudType(),
                assessment.getRiskLevel(),
                fraudData.getSuspiciousPatterns(),
                fraudData.getFraudIndicators()
        );

        // Update risk scoring models based on DLQ events
        fraudAnalyticsService.updateRiskModels(
                fraudData.getAccountId(),
                fraudData.getFraudType(),
                fraudData.getRiskScore(),
                assessment.getRiskLevel()
        );
    }

    private void escalateToExecutives(FraudActivityData fraudData, FraudDlqAssessment assessment, String incidentId) {
        notificationService.sendExecutiveAlert(
                "Critical Fraud DLQ Event - Executive Action Required",
                String.format("High-value fraud event failed processing: Account %s, Amount: $%.2f, Type: %s",
                        fraudData.getAccountId(),
                        fraudData.getTransactionAmount() != null ? fraudData.getTransactionAmount() : 0.0,
                        fraudData.getFraudType()),
                Map.of(
                        "incidentId", incidentId,
                        "accountId", fraudData.getAccountId(),
                        "businessImpact", assessment.getBusinessImpact(),
                        "riskLevel", assessment.getRiskLevel()
                )
        );
    }

    private void notifyCustomer(FraudActivityData fraudData, FraudDlqAssessment assessment) {
        if (fraudData.getUserId() != null) {
            notificationService.sendSecurityAlert(
                    fraudData.getUserId(),
                    "Security Alert - Account Protection Activated",
                    "We've detected suspicious activity on your account and have activated additional security measures to protect you.",
                    Map.of("severity", "HIGH", "actionRequired", true)
            );
        }
    }

    private void initiateRegulatoryReporting(FraudActivityData fraudData, FraudDlqAssessment assessment, String incidentId) {
        // Create regulatory reports for significant fraud events
        fraudIncidentService.initiateRegulatoryReporting(
                incidentId,
                fraudData.getFraudType(),
                fraudData.getTransactionAmount(),
                assessment.getBusinessImpact()
        );
    }

    private void executeEmergencyFallback(FraudActivityData fraudData, Exception error) {
        log.error("EMERGENCY: Executing emergency fraud fallback procedures due to DLQ processing failure");

        try {
            // Emergency account protection
            emergencyProtectionService.emergencyAccountProtection(fraudData.getAccountId());

            // Emergency notification
            notificationService.sendEmergencyAlert(
                    "CRITICAL: Fraud DLQ Processing Failed",
                    String.format("Failed to process critical fraud DLQ event for account %s: %s",
                            fraudData.getAccountId(), error.getMessage())
            );

            // Manual intervention alert
            notificationService.escalateToManualIntervention(
                    fraudData.getEventId(),
                    "FRAUD_DLQ_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency fraud fallback procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("FRAUD DLQ: Validation failed for DLQ event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "FRAUD_DLQ_VALIDATION_ERROR",
                null,
                "Fraud DLQ validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditFraudDlqProcessing(FraudActivityData fraudData, GenericKafkaEvent event,
                                       FraudDlqAssessment assessment, String status) {
        try {
            auditService.auditSecurityEvent(
                    "FRAUD_DLQ_EVENT_PROCESSED",
                    fraudData != null ? fraudData.getAccountId() : null,
                    String.format("Fraud DLQ event processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "fraudType", fraudData != null ? fraudData.getFraudType() : "unknown",
                            "riskLevel", assessment != null ? assessment.getRiskLevel() : "unknown",
                            "emergencyAction", assessment != null ? assessment.getEmergencyAction() : "none",
                            "businessImpact", assessment != null ? assessment.getBusinessImpact() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit fraud DLQ processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: Fraud DLQ event sent to final DLT - EventId: {}", event.getEventId());

        try {
            FraudActivityData fraudData = extractFraudData(event.getPayload());

            // Emergency fraud protection for final DLT events
            emergencyProtectionService.emergencyAccountProtection(fraudData.getAccountId());

            // Critical alert for DLT
            notificationService.sendEmergencyAlert(
                    "CRITICAL: Fraud DLQ in Final DLT",
                    "Critical fraud event could not be processed even in DLQ - immediate manual intervention required"
            );

        } catch (Exception e) {
            log.error("Failed to handle fraud DLQ final DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleFraudDlqFailure(GenericKafkaEvent event, String topic, int partition,
                                    long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for fraud DLQ processing - EventId: {}",
                event.getEventId(), e);

        try {
            FraudActivityData fraudData = extractFraudData(event.getPayload());

            // Emergency protection
            emergencyProtectionService.emergencyAccountProtection(fraudData.getAccountId());

            // Emergency alert
            notificationService.sendEmergencyAlert(
                    "Fraud DLQ Circuit Breaker Open",
                    "Fraud DLQ processing is failing - fraud protection severely compromised"
            );

        } catch (Exception ex) {
            log.error("Failed to handle fraud DLQ circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer extractInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private Double extractDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;

        // Handle BigDecimal with MoneyMath for precision
        if (value instanceof BigDecimal) {
            return (double) MoneyMath.toMLFeature((BigDecimal) value);
        }

        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
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
    public static class FraudActivityData {
        private String eventId;
        private String accountId;
        private String userId;
        private String transactionId;
        private String fraudType;
        private Double riskScore;
        private String riskLevel;
        private List<String> suspiciousPatterns;
        private List<String> fraudIndicators;
        private Map<String, Object> originalEvent;
        private String failureReason;
        private Integer retryCount;
        private Instant dlqTimestamp;
        private Instant originalTimestamp;
        private Map<String, Object> deviceInfo;
        private Map<String, Object> locationData;
        private Double transactionAmount;
        private String transactionCurrency;
        private Map<String, Object> merchantInfo;
        private Map<String, Object> behaviorAnalysis;
        private Map<String, Object> mlModelResults;
        private List<String> complianceFlags;
        private String customerSegment;
    }

    @lombok.Data
    @lombok.Builder
    public static class FraudDlqAssessment {
        private String riskLevel;
        private String emergencyAction;
        private boolean requiresImmediateAction;
        private List<String> protectionMeasures;
        private String businessImpact;
        private boolean executiveEscalation;
        private boolean customerNotificationRequired;
        private boolean regulatoryReporting;
    }
}