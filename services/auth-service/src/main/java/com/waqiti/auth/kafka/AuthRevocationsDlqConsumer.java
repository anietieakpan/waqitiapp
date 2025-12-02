package com.waqiti.auth.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.auth.service.*;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade DLQ consumer for authentication revocations
 * Handles critical auth revocation events that failed normal processing
 * Provides emergency security protection and executive escalation
 *
 * Critical for: Authentication security, access control, regulatory compliance
 * SLA: Must process DLQ auth revocations within 15 seconds for security containment
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthRevocationsDlqConsumer {

    private final AuthenticationService authenticationService;
    private final TokenRevocationService tokenRevocationService;
    private final SessionManagementService sessionManagementService;
    private final SecurityIncidentService securityIncidentService;
    private final EmergencyAuthProtectionService emergencyProtectionService;
    private final AuthComplianceService authComplianceService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 48-hour TTL for DLQ events
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 48;

    // Metrics
    private final Counter dlqEventCounter = Counter.builder("auth_revocations_dlq_processed_total")
            .description("Total number of auth revocation DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter criticalAuthDlqCounter = Counter.builder("critical_auth_revocations_dlq_total")
            .description("Total number of critical auth revocation DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("auth_revocations_dlq_processing_duration")
            .description("Time taken to process auth revocation DLQ events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"auth-revocations-dlq"},
        groupId = "auth-service-revocations-dlq-processor",
        containerFactory = "dlqKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 6000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "auth-revocations-dlq-processor", fallbackMethod = "handleAuthRevocationDlqFailure")
    @Retry(name = "auth-revocations-dlq-processor")
    public void processAuthRevocationsDlq(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.error("AUTH DLQ: Processing failed auth revocation: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Auth revocation DLQ event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate auth revocation data
            AuthRevocationData revocationData = extractRevocationData(event.getPayload());
            validateRevocationData(revocationData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Emergency security assessment
            AuthRevocationDlqAssessment assessment = assessDlqRevocationEvent(revocationData, event);

            // Process DLQ auth revocation event
            processDlqAuthRevocation(revocationData, assessment, event);

            // Record successful processing metrics
            dlqEventCounter.increment();

            if ("CRITICAL".equals(assessment.getSecurityLevel())) {
                criticalAuthDlqCounter.increment();
            }

            // Audit the DLQ processing
            auditAuthRevocationDlqProcessing(revocationData, event, assessment, "SUCCESS");

            log.error("AUTH DLQ: Successfully processed revocation DLQ: {} - Security: {} Type: {} Action: {}",
                    eventId, assessment.getSecurityLevel(), revocationData.getRevocationType(),
                    assessment.getEmergencyAction());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("AUTH DLQ: Invalid auth revocation DLQ data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("AUTH DLQ: Failed to process auth revocation DLQ event: {}", eventId, e);
            auditAuthRevocationDlqProcessing(null, event, null, "FAILED: " + e.getMessage());
            throw new RuntimeException("Auth revocation DLQ processing failed", e);

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

    private AuthRevocationData extractRevocationData(Map<String, Object> payload) {
        return AuthRevocationData.builder()
                .eventId(extractString(payload, "eventId"))
                .userId(extractString(payload, "userId"))
                .accountId(extractString(payload, "accountId"))
                .sessionId(extractString(payload, "sessionId"))
                .tokenId(extractString(payload, "tokenId"))
                .revocationType(extractString(payload, "revocationType"))
                .securityLevel(extractString(payload, "securityLevel"))
                .revocationReason(extractString(payload, "revocationReason"))
                .originalEvent(extractMap(payload, "originalEvent"))
                .failureReason(extractString(payload, "failureReason"))
                .retryCount(extractInteger(payload, "retryCount"))
                .dlqTimestamp(extractInstant(payload, "dlqTimestamp"))
                .originalTimestamp(extractInstant(payload, "originalTimestamp"))
                .deviceInfo(extractMap(payload, "deviceInfo"))
                .locationData(extractMap(payload, "locationData"))
                .ipAddress(extractString(payload, "ipAddress"))
                .userAgent(extractString(payload, "userAgent"))
                .authenticationMethod(extractString(payload, "authenticationMethod"))
                .securityFlags(extractStringList(payload, "securityFlags"))
                .complianceContext(extractMap(payload, "complianceContext"))
                .riskIndicators(extractStringList(payload, "riskIndicators"))
                .privilegeLevel(extractString(payload, "privilegeLevel"))
                .accessScope(extractStringList(payload, "accessScope"))
                .businessImpact(extractString(payload, "businessImpact"))
                .build();
    }

    private void validateRevocationData(AuthRevocationData revocationData) {
        if (revocationData.getEventId() == null || revocationData.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (revocationData.getUserId() == null || revocationData.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (revocationData.getRevocationType() == null || revocationData.getRevocationType().trim().isEmpty()) {
            throw new IllegalArgumentException("Revocation type is required");
        }

        List<String> validRevocationTypes = List.of(
            "TOKEN_REVOCATION", "SESSION_TERMINATION", "ACCOUNT_LOCKOUT",
            "PRIVILEGE_REVOCATION", "DEVICE_REVOCATION", "EMERGENCY_LOGOUT"
        );
        if (!validRevocationTypes.contains(revocationData.getRevocationType())) {
            throw new IllegalArgumentException("Invalid revocation type: " + revocationData.getRevocationType());
        }

        List<String> validSecurityLevels = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
        if (revocationData.getSecurityLevel() == null ||
            !validSecurityLevels.contains(revocationData.getSecurityLevel())) {
            throw new IllegalArgumentException("Valid security level is required");
        }
    }

    private AuthRevocationDlqAssessment assessDlqRevocationEvent(AuthRevocationData revocationData, GenericKafkaEvent event) {
        // Enhanced security assessment for DLQ events
        String enhancedSecurityLevel = determineEnhancedSecurityLevel(revocationData);
        String emergencyAction = determineEmergencyAction(revocationData, enhancedSecurityLevel);
        boolean requiresImmediateAction = requiresImmediateAction(revocationData, enhancedSecurityLevel);
        List<String> securityMeasures = determineSecurityMeasures(revocationData, enhancedSecurityLevel);
        String businessImpact = assessBusinessImpact(revocationData);

        return AuthRevocationDlqAssessment.builder()
                .securityLevel(enhancedSecurityLevel)
                .emergencyAction(emergencyAction)
                .requiresImmediateAction(requiresImmediateAction)
                .securityMeasures(securityMeasures)
                .businessImpact(businessImpact)
                .executiveEscalation(determineExecutiveEscalation(revocationData, enhancedSecurityLevel))
                .customerNotificationRequired(determineCustomerNotification(revocationData))
                .complianceReporting(determineComplianceReporting(revocationData))
                .securityIncidentRequired(determineSecurityIncident(revocationData, enhancedSecurityLevel))
                .build();
    }

    private String determineEnhancedSecurityLevel(AuthRevocationData revocationData) {
        // DLQ events automatically get elevated security assessment
        if ("CRITICAL".equals(revocationData.getSecurityLevel()) ||
            "EMERGENCY_LOGOUT".equals(revocationData.getRevocationType()) ||
            "ACCOUNT_LOCKOUT".equals(revocationData.getRevocationType())) {
            return "CRITICAL";
        } else if ("HIGH".equals(revocationData.getSecurityLevel()) ||
                   "PRIVILEGE_REVOCATION".equals(revocationData.getRevocationType())) {
            return "HIGH";
        } else if ("MEDIUM".equals(revocationData.getSecurityLevel())) {
            return "ELEVATED";
        } else {
            return "MEDIUM"; // Even low-security events in DLQ get elevated to medium
        }
    }

    private String determineEmergencyAction(AuthRevocationData revocationData, String securityLevel) {
        if ("CRITICAL".equals(securityLevel)) {
            if ("EMERGENCY_LOGOUT".equals(revocationData.getRevocationType())) {
                return "IMMEDIATE_GLOBAL_LOGOUT";
            } else if ("ACCOUNT_LOCKOUT".equals(revocationData.getRevocationType())) {
                return "COMPREHENSIVE_ACCOUNT_LOCKDOWN";
            } else if ("PRIVILEGE_REVOCATION".equals(revocationData.getRevocationType())) {
                return "EMERGENCY_PRIVILEGE_STRIP";
            } else {
                return "COMPREHENSIVE_AUTH_REVOCATION";
            }
        } else if ("HIGH".equals(securityLevel)) {
            return "ENHANCED_AUTH_VALIDATION";
        } else {
            return "STANDARD_REVOCATION_RETRY";
        }
    }

    private boolean requiresImmediateAction(AuthRevocationData revocationData, String securityLevel) {
        return "CRITICAL".equals(securityLevel) ||
               "EMERGENCY_LOGOUT".equals(revocationData.getRevocationType()) ||
               "ACCOUNT_LOCKOUT".equals(revocationData.getRevocationType()) ||
               revocationData.getRiskIndicators().contains("ACCOUNT_COMPROMISE") ||
               revocationData.getRiskIndicators().contains("PRIVILEGE_ESCALATION");
    }

    private List<String> determineSecurityMeasures(AuthRevocationData revocationData, String securityLevel) {
        if ("CRITICAL".equals(securityLevel)) {
            return List.of(
                "IMMEDIATE_TOKEN_REVOCATION",
                "ALL_SESSION_TERMINATION",
                "DEVICE_BLACKLISTING",
                "IP_ADDRESS_BLOCKING",
                "PRIVILEGE_SUSPENSION",
                "SECURITY_AUDIT_INITIATION"
            );
        } else if ("HIGH".equals(securityLevel)) {
            return List.of(
                "TOKEN_REVOCATION",
                "SESSION_TERMINATION",
                "ENHANCED_VALIDATION",
                "ACTIVITY_MONITORING"
            );
        } else {
            return List.of(
                "STANDARD_REVOCATION",
                "SESSION_VALIDATION",
                "AUDIT_LOGGING"
            );
        }
    }

    private String assessBusinessImpact(AuthRevocationData revocationData) {
        if ("EMERGENCY_LOGOUT".equals(revocationData.getRevocationType()) ||
            "ACCOUNT_LOCKOUT".equals(revocationData.getRevocationType())) {
            return "HIGH";
        } else if ("PRIVILEGE_REVOCATION".equals(revocationData.getRevocationType())) {
            return "MEDIUM";
        } else if (revocationData.getPrivilegeLevel() != null &&
                   ("ADMIN".equals(revocationData.getPrivilegeLevel()) ||
                    "SUPER_USER".equals(revocationData.getPrivilegeLevel()))) {
            return "HIGH";
        } else {
            return "LOW";
        }
    }

    private boolean determineExecutiveEscalation(AuthRevocationData revocationData, String securityLevel) {
        return "CRITICAL".equals(securityLevel) ||
               "EMERGENCY_LOGOUT".equals(revocationData.getRevocationType()) ||
               ("ADMIN".equals(revocationData.getPrivilegeLevel()) ||
                "SUPER_USER".equals(revocationData.getPrivilegeLevel())) ||
               revocationData.getRiskIndicators().contains("ACCOUNT_COMPROMISE");
    }

    private boolean determineCustomerNotification(AuthRevocationData revocationData) {
        return "EMERGENCY_LOGOUT".equals(revocationData.getRevocationType()) ||
               "ACCOUNT_LOCKOUT".equals(revocationData.getRevocationType()) ||
               revocationData.getRiskIndicators().contains("ACCOUNT_COMPROMISE") ||
               revocationData.getRiskIndicators().contains("UNAUTHORIZED_ACCESS");
    }

    private boolean determineComplianceReporting(AuthRevocationData revocationData) {
        return revocationData.getSecurityFlags().contains("COMPLIANCE_REQUIRED") ||
               revocationData.getRiskIndicators().contains("REGULATORY_VIOLATION") ||
               ("ADMIN".equals(revocationData.getPrivilegeLevel()) ||
                "SUPER_USER".equals(revocationData.getPrivilegeLevel()));
    }

    private boolean determineSecurityIncident(AuthRevocationData revocationData, String securityLevel) {
        return "CRITICAL".equals(securityLevel) ||
               revocationData.getRiskIndicators().contains("ACCOUNT_COMPROMISE") ||
               revocationData.getRiskIndicators().contains("PRIVILEGE_ESCALATION") ||
               "EMERGENCY_LOGOUT".equals(revocationData.getRevocationType());
    }

    private void processDlqAuthRevocation(AuthRevocationData revocationData,
                                        AuthRevocationDlqAssessment assessment,
                                        GenericKafkaEvent event) {
        log.error("AUTH DLQ: Processing auth revocation DLQ - User: {}, Type: {}, Security: {}, Action: {}",
                revocationData.getUserId(), revocationData.getRevocationType(),
                assessment.getSecurityLevel(), assessment.getEmergencyAction());

        try {
            // Create high-priority security incident if required
            String incidentId = null;
            if (assessment.isSecurityIncidentRequired()) {
                incidentId = securityIncidentService.createAuthRevocationIncident(revocationData, assessment);
            }

            // Execute emergency security measures
            executeEmergencySecurityMeasures(revocationData, assessment, incidentId);

            // Send immediate notifications
            sendImmediateNotifications(revocationData, assessment, incidentId);

            // Apply security measures
            applySecurityMeasures(revocationData, assessment, incidentId);

            // Update auth compliance
            updateAuthCompliance(revocationData, assessment);

            // Executive escalation if required
            if (assessment.isExecutiveEscalation()) {
                escalateToExecutives(revocationData, assessment, incidentId);
            }

            // Customer notification if required
            if (assessment.isCustomerNotificationRequired()) {
                notifyCustomer(revocationData, assessment);
            }

            // Compliance reporting if required
            if (assessment.isComplianceReporting()) {
                initiateComplianceReporting(revocationData, assessment, incidentId);
            }

            log.error("AUTH DLQ: Revocation DLQ processed - IncidentId: {}, SecurityMeasures: {}, BusinessImpact: {}",
                    incidentId, assessment.getSecurityMeasures().size(), assessment.getBusinessImpact());

        } catch (Exception e) {
            log.error("AUTH DLQ: Failed to process auth revocation DLQ for user: {}", revocationData.getUserId(), e);

            // Emergency fallback procedures
            executeEmergencyFallback(revocationData, e);

            throw new RuntimeException("Auth revocation DLQ processing failed", e);
        }
    }

    private void executeEmergencySecurityMeasures(AuthRevocationData revocationData,
                                                AuthRevocationDlqAssessment assessment,
                                                String incidentId) {
        // Immediate emergency actions based on assessment
        switch (assessment.getEmergencyAction()) {
            case "IMMEDIATE_GLOBAL_LOGOUT":
                emergencyProtectionService.globalUserLogout(revocationData.getUserId(), incidentId);
                break;

            case "COMPREHENSIVE_ACCOUNT_LOCKDOWN":
                emergencyProtectionService.comprehensiveAccountLockdown(revocationData.getAccountId(), incidentId);
                break;

            case "EMERGENCY_PRIVILEGE_STRIP":
                emergencyProtectionService.emergencyPrivilegeRevocation(revocationData.getUserId(), incidentId);
                break;

            case "COMPREHENSIVE_AUTH_REVOCATION":
                emergencyProtectionService.comprehensiveAuthRevocation(revocationData.getUserId(), incidentId);
                break;

            case "ENHANCED_AUTH_VALIDATION":
                emergencyProtectionService.enableEnhancedAuthValidation(revocationData.getUserId(), incidentId);
                break;

            default:
                emergencyProtectionService.standardRevocationRetry(revocationData, incidentId);
        }
    }

    private void sendImmediateNotifications(AuthRevocationData revocationData,
                                          AuthRevocationDlqAssessment assessment,
                                          String incidentId) {
        // Critical and high-security DLQ events require immediate escalation
        if ("CRITICAL".equals(assessment.getSecurityLevel()) || "HIGH".equals(assessment.getSecurityLevel())) {
            notificationService.sendCriticalAlert(
                    "AUTH REVOCATION DLQ - SECURITY INCIDENT",
                    String.format("Critical auth revocation failed processing: User %s, Type: %s, Security: %s",
                            revocationData.getUserId(), revocationData.getRevocationType(),
                            assessment.getSecurityLevel()),
                    Map.of(
                            "userId", revocationData.getUserId(),
                            "revocationType", revocationData.getRevocationType(),
                            "securityLevel", assessment.getSecurityLevel(),
                            "incidentId", incidentId != null ? incidentId : "N/A",
                            "emergencyAction", assessment.getEmergencyAction()
                    )
            );

            // Page security team
            notificationService.pageSecurityTeam(
                    "AUTH_REVOCATION_DLQ_CRITICAL",
                    revocationData.getRevocationType(),
                    assessment.getSecurityLevel(),
                    incidentId != null ? incidentId : "N/A"
            );
        }
    }

    private void applySecurityMeasures(AuthRevocationData revocationData,
                                     AuthRevocationDlqAssessment assessment,
                                     String incidentId) {
        for (String measure : assessment.getSecurityMeasures()) {
            try {
                switch (measure) {
                    case "IMMEDIATE_TOKEN_REVOCATION":
                        tokenRevocationService.emergencyTokenRevocation(revocationData.getUserId(), incidentId);
                        break;

                    case "ALL_SESSION_TERMINATION":
                        sessionManagementService.terminateAllSessions(revocationData.getUserId(), incidentId);
                        break;

                    case "DEVICE_BLACKLISTING":
                        if (revocationData.getDeviceInfo() != null) {
                            emergencyProtectionService.blacklistDevice(revocationData.getDeviceInfo(), incidentId);
                        }
                        break;

                    case "IP_ADDRESS_BLOCKING":
                        if (revocationData.getIpAddress() != null) {
                            emergencyProtectionService.blockIpAddress(revocationData.getIpAddress(), incidentId);
                        }
                        break;

                    case "PRIVILEGE_SUSPENSION":
                        authenticationService.suspendPrivileges(revocationData.getUserId(), incidentId);
                        break;

                    case "ENHANCED_VALIDATION":
                        authenticationService.enableEnhancedValidation(revocationData.getUserId(), incidentId);
                        break;

                    default:
                        emergencyProtectionService.applyGenericSecurityMeasure(measure, revocationData.getUserId(), incidentId);
                }
            } catch (Exception e) {
                log.error("Failed to apply security measure: {}", measure, e);
            }
        }
    }

    private void updateAuthCompliance(AuthRevocationData revocationData, AuthRevocationDlqAssessment assessment) {
        // Update auth compliance monitoring
        authComplianceService.updateRevocationCompliance(
                revocationData.getRevocationType(),
                assessment.getSecurityLevel(),
                revocationData.getComplianceContext()
        );

        // Update auth risk assessment
        authComplianceService.updateAuthRiskAssessment(
                revocationData.getUserId(),
                revocationData.getRevocationType(),
                assessment.getBusinessImpact()
        );
    }

    private void escalateToExecutives(AuthRevocationData revocationData,
                                    AuthRevocationDlqAssessment assessment,
                                    String incidentId) {
        notificationService.sendExecutiveAlert(
                "Critical Auth Revocation DLQ Event - Executive Action Required",
                String.format("High-impact auth revocation failed processing: User %s, Type: %s, Privilege: %s",
                        revocationData.getUserId(),
                        revocationData.getRevocationType(),
                        revocationData.getPrivilegeLevel()),
                Map.of(
                        "incidentId", incidentId != null ? incidentId : "N/A",
                        "userId", revocationData.getUserId(),
                        "businessImpact", assessment.getBusinessImpact(),
                        "securityLevel", assessment.getSecurityLevel()
                )
        );
    }

    private void notifyCustomer(AuthRevocationData revocationData, AuthRevocationDlqAssessment assessment) {
        notificationService.sendSecurityAlert(
                revocationData.getUserId(),
                "Security Alert - Account Access Changed",
                "We've made changes to your account access for security reasons. If you did not request this, please contact us immediately.",
                Map.of("severity", "HIGH", "actionRequired", true)
        );
    }

    private void initiateComplianceReporting(AuthRevocationData revocationData,
                                           AuthRevocationDlqAssessment assessment,
                                           String incidentId) {
        // Create compliance reports for auth revocation events
        authComplianceService.initiateComplianceReporting(
                incidentId != null ? incidentId : revocationData.getEventId(),
                revocationData.getRevocationType(),
                revocationData.getPrivilegeLevel(),
                assessment.getBusinessImpact()
        );
    }

    private void executeEmergencyFallback(AuthRevocationData revocationData, Exception error) {
        log.error("EMERGENCY: Executing emergency auth revocation fallback due to DLQ processing failure");

        try {
            // Emergency auth protection
            emergencyProtectionService.emergencyUserProtection(revocationData.getUserId());

            // Emergency notification
            notificationService.sendEmergencyAlert(
                    "CRITICAL: Auth Revocation DLQ Processing Failed",
                    String.format("Failed to process critical auth revocation DLQ for user %s: %s",
                            revocationData.getUserId(), error.getMessage())
            );

            // Manual intervention alert
            notificationService.escalateToManualIntervention(
                    revocationData.getEventId(),
                    "AUTH_REVOCATION_DLQ_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency auth revocation fallback procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("AUTH DLQ: Validation failed for revocation DLQ event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "AUTH_REVOCATION_DLQ_VALIDATION_ERROR",
                null,
                "Auth revocation DLQ validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditAuthRevocationDlqProcessing(AuthRevocationData revocationData,
                                                GenericKafkaEvent event,
                                                AuthRevocationDlqAssessment assessment,
                                                String status) {
        try {
            auditService.auditSecurityEvent(
                    "AUTH_REVOCATION_DLQ_EVENT_PROCESSED",
                    revocationData != null ? revocationData.getUserId() : null,
                    String.format("Auth revocation DLQ event processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "revocationType", revocationData != null ? revocationData.getRevocationType() : "unknown",
                            "securityLevel", assessment != null ? assessment.getSecurityLevel() : "unknown",
                            "emergencyAction", assessment != null ? assessment.getEmergencyAction() : "none",
                            "businessImpact", assessment != null ? assessment.getBusinessImpact() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit auth revocation DLQ processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: Auth revocation DLQ event sent to final DLT - EventId: {}", event.getEventId());

        try {
            AuthRevocationData revocationData = extractRevocationData(event.getPayload());

            // Emergency auth protection for final DLT events
            emergencyProtectionService.emergencyUserProtection(revocationData.getUserId());

            // Critical alert for DLT
            notificationService.sendEmergencyAlert(
                    "CRITICAL: Auth Revocation DLQ in Final DLT",
                    "Critical auth revocation could not be processed even in DLQ - immediate manual intervention required"
            );

        } catch (Exception e) {
            log.error("Failed to handle auth revocation DLQ final DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleAuthRevocationDlqFailure(GenericKafkaEvent event, String topic, int partition,
                                             long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for auth revocation DLQ processing - EventId: {}",
                event.getEventId(), e);

        try {
            AuthRevocationData revocationData = extractRevocationData(event.getPayload());

            // Emergency protection
            emergencyProtectionService.emergencyUserProtection(revocationData.getUserId());

            // Emergency alert
            notificationService.sendEmergencyAlert(
                    "Auth Revocation DLQ Circuit Breaker Open",
                    "Auth revocation DLQ processing is failing - authentication security severely compromised"
            );

        } catch (Exception ex) {
            log.error("Failed to handle auth revocation DLQ circuit breaker fallback", ex);
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
    public static class AuthRevocationData {
        private String eventId;
        private String userId;
        private String accountId;
        private String sessionId;
        private String tokenId;
        private String revocationType;
        private String securityLevel;
        private String revocationReason;
        private Map<String, Object> originalEvent;
        private String failureReason;
        private Integer retryCount;
        private Instant dlqTimestamp;
        private Instant originalTimestamp;
        private Map<String, Object> deviceInfo;
        private Map<String, Object> locationData;
        private String ipAddress;
        private String userAgent;
        private String authenticationMethod;
        private List<String> securityFlags;
        private Map<String, Object> complianceContext;
        private List<String> riskIndicators;
        private String privilegeLevel;
        private List<String> accessScope;
        private String businessImpact;
    }

    @lombok.Data
    @lombok.Builder
    public static class AuthRevocationDlqAssessment {
        private String securityLevel;
        private String emergencyAction;
        private boolean requiresImmediateAction;
        private List<String> securityMeasures;
        private String businessImpact;
        private boolean executiveEscalation;
        private boolean customerNotificationRequired;
        private boolean complianceReporting;
        private boolean securityIncidentRequired;
    }
}