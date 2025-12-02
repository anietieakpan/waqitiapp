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
 * Production-grade DLQ consumer for authentication failures
 * Handles critical auth failure events that failed normal processing
 * Provides brute force protection, attack detection, and security incident management
 *
 * Critical for: Authentication security, attack prevention, regulatory compliance
 * SLA: Must process DLQ auth failures within 10 seconds for attack mitigation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthFailuresDlqConsumer {

    private final AuthenticationService authenticationService;
    private final BruteForceProtectionService bruteForceProtectionService;
    private final AttackDetectionService attackDetectionService;
    private final SecurityIncidentService securityIncidentService;
    private final ThreatIntelligenceService threatIntelligenceService;
    private final AuthFailureAnalyticsService authFailureAnalyticsService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 48-hour TTL for DLQ events
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 48;

    // Metrics
    private final Counter dlqEventCounter = Counter.builder("auth_failures_dlq_processed_total")
            .description("Total number of auth failure DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter attackDetectionCounter = Counter.builder("auth_attacks_detected_dlq_total")
            .description("Total number of auth attacks detected from DLQ events")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("auth_failures_dlq_processing_duration")
            .description("Time taken to process auth failure DLQ events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"auth-failures-dlq", "auth-failures-fallback-events"},
        groupId = "auth-service-failures-dlq-processor",
        containerFactory = "dlqKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 4000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "auth-failures-dlq-processor", fallbackMethod = "handleAuthFailureDlqFailure")
    @Retry(name = "auth-failures-dlq-processor")
    public void processAuthFailuresDlq(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.error("AUTH FAILURE DLQ: Processing failed auth failure: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Auth failure DLQ event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate auth failure data
            AuthFailureData failureData = extractFailureData(event.getPayload());
            validateFailureData(failureData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Enhanced threat assessment for DLQ events
            AuthFailureThreatAssessment assessment = assessDlqFailureEvent(failureData, event);

            // Process DLQ auth failure event
            processDlqAuthFailure(failureData, assessment, event);

            // Record successful processing metrics
            dlqEventCounter.increment();

            if (assessment.isAttackDetected()) {
                attackDetectionCounter.increment();
            }

            // Audit the DLQ processing
            auditAuthFailureDlqProcessing(failureData, event, assessment, "SUCCESS");

            log.error("AUTH FAILURE DLQ: Successfully processed failure DLQ: {} - Threat: {} Type: {} Action: {}",
                    eventId, assessment.getThreatLevel(), failureData.getFailureType(),
                    assessment.getProtectionAction());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("AUTH FAILURE DLQ: Invalid auth failure DLQ data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("AUTH FAILURE DLQ: Failed to process auth failure DLQ event: {}", eventId, e);
            auditAuthFailureDlqProcessing(null, event, null, "FAILED: " + e.getMessage());
            throw new RuntimeException("Auth failure DLQ processing failed", e);

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

    private AuthFailureData extractFailureData(Map<String, Object> payload) {
        return AuthFailureData.builder()
                .eventId(extractString(payload, "eventId"))
                .userId(extractString(payload, "userId"))
                .accountId(extractString(payload, "accountId"))
                .sessionId(extractString(payload, "sessionId"))
                .failureType(extractString(payload, "failureType"))
                .failureReason(extractString(payload, "failureReason"))
                .threatLevel(extractString(payload, "threatLevel"))
                .attemptCount(extractInteger(payload, "attemptCount"))
                .originalEvent(extractMap(payload, "originalEvent"))
                .dlqFailureReason(extractString(payload, "dlqFailureReason"))
                .retryCount(extractInteger(payload, "retryCount"))
                .dlqTimestamp(extractInstant(payload, "dlqTimestamp"))
                .originalTimestamp(extractInstant(payload, "originalTimestamp"))
                .ipAddress(extractString(payload, "ipAddress"))
                .userAgent(extractString(payload, "userAgent"))
                .deviceFingerprint(extractString(payload, "deviceFingerprint"))
                .geolocation(extractMap(payload, "geolocation"))
                .authMethod(extractString(payload, "authMethod"))
                .credentialType(extractString(payload, "credentialType"))
                .attackSignatures(extractStringList(payload, "attackSignatures"))
                .riskIndicators(extractStringList(payload, "riskIndicators"))
                .behaviorAnalysis(extractMap(payload, "behaviorAnalysis"))
                .threatIntelligence(extractMap(payload, "threatIntelligence"))
                .complianceFlags(extractStringList(payload, "complianceFlags"))
                .sourceSystem(extractString(payload, "sourceSystem"))
                .networkInfo(extractMap(payload, "networkInfo"))
                .build();
    }

    private void validateFailureData(AuthFailureData failureData) {
        if (failureData.getEventId() == null || failureData.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (failureData.getFailureType() == null || failureData.getFailureType().trim().isEmpty()) {
            throw new IllegalArgumentException("Failure type is required");
        }

        List<String> validFailureTypes = List.of(
            "INVALID_CREDENTIALS", "ACCOUNT_LOCKED", "EXPIRED_PASSWORD",
            "MFA_FAILURE", "BRUTE_FORCE_DETECTED", "SUSPICIOUS_LOCATION",
            "DEVICE_NOT_RECOGNIZED", "RATE_LIMIT_EXCEEDED", "SYSTEM_ERROR"
        );
        if (!validFailureTypes.contains(failureData.getFailureType())) {
            throw new IllegalArgumentException("Invalid failure type: " + failureData.getFailureType());
        }

        if (failureData.getIpAddress() == null || failureData.getIpAddress().trim().isEmpty()) {
            throw new IllegalArgumentException("IP address is required for threat analysis");
        }

        List<String> validThreatLevels = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
        if (failureData.getThreatLevel() != null &&
            !validThreatLevels.contains(failureData.getThreatLevel())) {
            throw new IllegalArgumentException("Invalid threat level: " + failureData.getThreatLevel());
        }
    }

    private AuthFailureThreatAssessment assessDlqFailureEvent(AuthFailureData failureData, GenericKafkaEvent event) {
        // Enhanced threat assessment for DLQ events
        String enhancedThreatLevel = determineEnhancedThreatLevel(failureData);
        boolean attackDetected = detectAttackPatterns(failureData);
        String protectionAction = determineProtectionAction(failureData, enhancedThreatLevel, attackDetected);
        List<String> protectionMeasures = determineProtectionMeasures(failureData, enhancedThreatLevel, attackDetected);
        String attackType = identifyAttackType(failureData);

        return AuthFailureThreatAssessment.builder()
                .threatLevel(enhancedThreatLevel)
                .attackDetected(attackDetected)
                .attackType(attackType)
                .protectionAction(protectionAction)
                .protectionMeasures(protectionMeasures)
                .requiresIncident(determineIncidentRequired(failureData, enhancedThreatLevel, attackDetected))
                .executiveEscalation(determineExecutiveEscalation(failureData, enhancedThreatLevel, attackDetected))
                .customerNotification(determineCustomerNotification(failureData, attackDetected))
                .threatIntelligenceUpdate(determineThreatIntelUpdate(failureData, attackDetected))
                .complianceReporting(determineComplianceReporting(failureData))
                .networkBlocking(determineNetworkBlocking(failureData, attackDetected))
                .build();
    }

    private String determineEnhancedThreatLevel(AuthFailureData failureData) {
        // DLQ events automatically get elevated threat assessment
        if ("BRUTE_FORCE_DETECTED".equals(failureData.getFailureType()) ||
            failureData.getAttackSignatures().contains("COORDINATED_ATTACK") ||
            (failureData.getAttemptCount() != null && failureData.getAttemptCount() > 10)) {
            return "CRITICAL";
        } else if ("SUSPICIOUS_LOCATION".equals(failureData.getFailureType()) ||
                   "DEVICE_NOT_RECOGNIZED".equals(failureData.getFailureType()) ||
                   failureData.getRiskIndicators().contains("ANOMALOUS_BEHAVIOR")) {
            return "HIGH";
        } else if ("RATE_LIMIT_EXCEEDED".equals(failureData.getFailureType()) ||
                   (failureData.getAttemptCount() != null && failureData.getAttemptCount() > 5)) {
            return "MEDIUM";
        } else {
            return "ELEVATED"; // Even low-threat events in DLQ get elevated
        }
    }

    private boolean detectAttackPatterns(AuthFailureData failureData) {
        // Enhanced attack detection for DLQ events
        return "BRUTE_FORCE_DETECTED".equals(failureData.getFailureType()) ||
               failureData.getAttackSignatures().contains("CREDENTIAL_STUFFING") ||
               failureData.getAttackSignatures().contains("PASSWORD_SPRAYING") ||
               failureData.getAttackSignatures().contains("COORDINATED_ATTACK") ||
               (failureData.getAttemptCount() != null && failureData.getAttemptCount() > 5) ||
               failureData.getRiskIndicators().contains("BOT_DETECTED") ||
               failureData.getRiskIndicators().contains("TOR_NETWORK");
    }

    private String identifyAttackType(AuthFailureData failureData) {
        if (failureData.getAttackSignatures().contains("CREDENTIAL_STUFFING")) {
            return "CREDENTIAL_STUFFING";
        } else if (failureData.getAttackSignatures().contains("PASSWORD_SPRAYING")) {
            return "PASSWORD_SPRAYING";
        } else if (failureData.getAttackSignatures().contains("COORDINATED_ATTACK")) {
            return "COORDINATED_ATTACK";
        } else if ("BRUTE_FORCE_DETECTED".equals(failureData.getFailureType())) {
            return "BRUTE_FORCE";
        } else if (failureData.getRiskIndicators().contains("BOT_DETECTED")) {
            return "AUTOMATED_ATTACK";
        } else {
            return "GENERIC_AUTH_FAILURE";
        }
    }

    private String determineProtectionAction(AuthFailureData failureData, String threatLevel, boolean attackDetected) {
        if (attackDetected && "CRITICAL".equals(threatLevel)) {
            return "IMMEDIATE_IP_BLOCK_AND_ACCOUNT_PROTECTION";
        } else if (attackDetected) {
            return "ENHANCED_RATE_LIMITING_AND_MONITORING";
        } else if ("CRITICAL".equals(threatLevel)) {
            return "ACCOUNT_TEMPORARY_LOCKOUT";
        } else if ("HIGH".equals(threatLevel)) {
            return "ENHANCED_VERIFICATION_REQUIRED";
        } else {
            return "STANDARD_FAILURE_PROCESSING";
        }
    }

    private List<String> determineProtectionMeasures(AuthFailureData failureData, String threatLevel, boolean attackDetected) {
        if (attackDetected && "CRITICAL".equals(threatLevel)) {
            return List.of(
                "IMMEDIATE_IP_BLOCKING",
                "ACCOUNT_PROTECTION_LOCKDOWN",
                "DEVICE_BLACKLISTING",
                "RATE_LIMIT_ENFORCEMENT",
                "THREAT_INTELLIGENCE_UPDATE",
                "SECURITY_MONITORING_ENHANCEMENT"
            );
        } else if (attackDetected) {
            return List.of(
                "ENHANCED_RATE_LIMITING",
                "IP_REPUTATION_CHECK",
                "DEVICE_VERIFICATION",
                "BEHAVIORAL_ANALYSIS",
                "THREAT_TRACKING"
            );
        } else if ("CRITICAL".equals(threatLevel) || "HIGH".equals(threatLevel)) {
            return List.of(
                "ACCOUNT_VERIFICATION",
                "ENHANCED_MONITORING",
                "MFA_REQUIREMENT",
                "ACTIVITY_LOGGING"
            );
        } else {
            return List.of(
                "STANDARD_LOGGING",
                "BASIC_MONITORING"
            );
        }
    }

    private boolean determineIncidentRequired(AuthFailureData failureData, String threatLevel, boolean attackDetected) {
        return attackDetected ||
               "CRITICAL".equals(threatLevel) ||
               failureData.getAttackSignatures().contains("COORDINATED_ATTACK") ||
               (failureData.getAttemptCount() != null && failureData.getAttemptCount() > 20);
    }

    private boolean determineExecutiveEscalation(AuthFailureData failureData, String threatLevel, boolean attackDetected) {
        return attackDetected && "CRITICAL".equals(threatLevel) ||
               failureData.getAttackSignatures().contains("COORDINATED_ATTACK") ||
               (failureData.getAttemptCount() != null && failureData.getAttemptCount() > 50);
    }

    private boolean determineCustomerNotification(AuthFailureData failureData, boolean attackDetected) {
        return attackDetected ||
               "SUSPICIOUS_LOCATION".equals(failureData.getFailureType()) ||
               "DEVICE_NOT_RECOGNIZED".equals(failureData.getFailureType()) ||
               (failureData.getAttemptCount() != null && failureData.getAttemptCount() > 10);
    }

    private boolean determineThreatIntelUpdate(AuthFailureData failureData, boolean attackDetected) {
        return attackDetected ||
               failureData.getRiskIndicators().contains("TOR_NETWORK") ||
               failureData.getRiskIndicators().contains("BOT_DETECTED") ||
               failureData.getAttackSignatures().size() > 0;
    }

    private boolean determineComplianceReporting(AuthFailureData failureData) {
        return failureData.getComplianceFlags().contains("REGULATORY_REPORTING_REQUIRED") ||
               failureData.getAttackSignatures().contains("COORDINATED_ATTACK") ||
               (failureData.getAttemptCount() != null && failureData.getAttemptCount() > 100);
    }

    private boolean determineNetworkBlocking(AuthFailureData failureData, boolean attackDetected) {
        return attackDetected ||
               failureData.getRiskIndicators().contains("TOR_NETWORK") ||
               failureData.getRiskIndicators().contains("MALICIOUS_IP") ||
               (failureData.getAttemptCount() != null && failureData.getAttemptCount() > 15);
    }

    private void processDlqAuthFailure(AuthFailureData failureData,
                                     AuthFailureThreatAssessment assessment,
                                     GenericKafkaEvent event) {
        log.error("AUTH FAILURE DLQ: Processing auth failure DLQ - IP: {}, Type: {}, Threat: {}, Attack: {}",
                failureData.getIpAddress(), failureData.getFailureType(),
                assessment.getThreatLevel(), assessment.isAttackDetected());

        try {
            // Create security incident if required
            String incidentId = null;
            if (assessment.isRequiresIncident()) {
                incidentId = securityIncidentService.createAuthFailureIncident(failureData, assessment);
            }

            // Execute immediate protection measures
            executeProtectionMeasures(failureData, assessment, incidentId);

            // Send immediate notifications
            sendImmediateNotifications(failureData, assessment, incidentId);

            // Update threat intelligence if required
            if (assessment.isThreatIntelligenceUpdate()) {
                updateThreatIntelligence(failureData, assessment);
            }

            // Apply network blocking if required
            if (assessment.isNetworkBlocking()) {
                applyNetworkBlocking(failureData, incidentId);
            }

            // Update analytics and patterns
            updateFailureAnalytics(failureData, assessment);

            // Executive escalation if required
            if (assessment.isExecutiveEscalation()) {
                escalateToExecutives(failureData, assessment, incidentId);
            }

            // Customer notification if required
            if (assessment.isCustomerNotification()) {
                notifyCustomer(failureData, assessment);
            }

            // Compliance reporting if required
            if (assessment.isComplianceReporting()) {
                initiateComplianceReporting(failureData, assessment, incidentId);
            }

            log.error("AUTH FAILURE DLQ: Failure DLQ processed - IncidentId: {}, ProtectionApplied: {}, ThreatLevel: {}",
                    incidentId, assessment.getProtectionMeasures().size(), assessment.getThreatLevel());

        } catch (Exception e) {
            log.error("AUTH FAILURE DLQ: Failed to process auth failure DLQ for IP: {}", failureData.getIpAddress(), e);

            // Emergency fallback procedures
            executeEmergencyFallback(failureData, e);

            throw new RuntimeException("Auth failure DLQ processing failed", e);
        }
    }

    private void executeProtectionMeasures(AuthFailureData failureData,
                                         AuthFailureThreatAssessment assessment,
                                         String incidentId) {
        // Execute protection action based on assessment
        switch (assessment.getProtectionAction()) {
            case "IMMEDIATE_IP_BLOCK_AND_ACCOUNT_PROTECTION":
                bruteForceProtectionService.emergencyIpBlock(failureData.getIpAddress(), incidentId);
                if (failureData.getAccountId() != null) {
                    bruteForceProtectionService.emergencyAccountProtection(failureData.getAccountId(), incidentId);
                }
                break;

            case "ENHANCED_RATE_LIMITING_AND_MONITORING":
                bruteForceProtectionService.enhancedRateLimiting(failureData.getIpAddress(), incidentId);
                attackDetectionService.enhanceMonitoring(failureData.getIpAddress(), incidentId);
                break;

            case "ACCOUNT_TEMPORARY_LOCKOUT":
                if (failureData.getAccountId() != null) {
                    authenticationService.temporaryAccountLockout(failureData.getAccountId(), incidentId);
                }
                break;

            case "ENHANCED_VERIFICATION_REQUIRED":
                if (failureData.getAccountId() != null) {
                    authenticationService.requireEnhancedVerification(failureData.getAccountId(), incidentId);
                }
                break;

            default:
                bruteForceProtectionService.standardFailureProcessing(failureData, incidentId);
        }

        // Apply additional protection measures
        for (String measure : assessment.getProtectionMeasures()) {
            try {
                switch (measure) {
                    case "IMMEDIATE_IP_BLOCKING":
                        bruteForceProtectionService.blockIpAddress(failureData.getIpAddress(), incidentId);
                        break;

                    case "DEVICE_BLACKLISTING":
                        if (failureData.getDeviceFingerprint() != null) {
                            bruteForceProtectionService.blacklistDevice(failureData.getDeviceFingerprint(), incidentId);
                        }
                        break;

                    case "ENHANCED_RATE_LIMITING":
                        bruteForceProtectionService.applyEnhancedRateLimit(failureData.getIpAddress(), incidentId);
                        break;

                    case "BEHAVIORAL_ANALYSIS":
                        attackDetectionService.enhanceBehavioralAnalysis(failureData.getIpAddress(), incidentId);
                        break;

                    case "MFA_REQUIREMENT":
                        if (failureData.getAccountId() != null) {
                            authenticationService.forceMfaRequirement(failureData.getAccountId(), incidentId);
                        }
                        break;

                    default:
                        bruteForceProtectionService.applyGenericProtection(measure, failureData, incidentId);
                }
            } catch (Exception e) {
                log.error("Failed to apply protection measure: {}", measure, e);
            }
        }
    }

    private void sendImmediateNotifications(AuthFailureData failureData,
                                          AuthFailureThreatAssessment assessment,
                                          String incidentId) {
        // Critical threat levels and attacks require immediate escalation
        if (assessment.isAttackDetected() || "CRITICAL".equals(assessment.getThreatLevel())) {
            notificationService.sendCriticalAlert(
                    "AUTH ATTACK DETECTED IN DLQ - IMMEDIATE ACTION REQUIRED",
                    String.format("Authentication attack detected from DLQ processing: IP %s, Type: %s, Attack: %s",
                            failureData.getIpAddress(), failureData.getFailureType(), assessment.getAttackType()),
                    Map.of(
                            "ipAddress", failureData.getIpAddress(),
                            "failureType", failureData.getFailureType(),
                            "attackType", assessment.getAttackType(),
                            "threatLevel", assessment.getThreatLevel(),
                            "incidentId", incidentId != null ? incidentId : "N/A",
                            "attemptCount", failureData.getAttemptCount() != null ? failureData.getAttemptCount() : 0
                    )
            );

            // Page security team for attacks
            if (assessment.isAttackDetected()) {
                notificationService.pageSecurityTeam(
                        "AUTH_ATTACK_DLQ_CRITICAL",
                        assessment.getAttackType(),
                        assessment.getThreatLevel(),
                        incidentId != null ? incidentId : "N/A"
                );
            }
        }
    }

    private void updateThreatIntelligence(AuthFailureData failureData, AuthFailureThreatAssessment assessment) {
        // Update threat intelligence with attack patterns
        threatIntelligenceService.updateAuthAttackIntelligence(
                failureData.getIpAddress(),
                assessment.getAttackType(),
                failureData.getAttackSignatures(),
                failureData.getRiskIndicators()
        );

        // Update IP reputation
        threatIntelligenceService.updateIpReputation(
                failureData.getIpAddress(),
                assessment.getThreatLevel(),
                assessment.getAttackType()
        );
    }

    private void applyNetworkBlocking(AuthFailureData failureData, String incidentId) {
        // Apply network-level blocking for severe threats
        bruteForceProtectionService.applyNetworkBlocking(
                failureData.getIpAddress(),
                failureData.getNetworkInfo(),
                incidentId
        );
    }

    private void updateFailureAnalytics(AuthFailureData failureData, AuthFailureThreatAssessment assessment) {
        // Update auth failure patterns and analytics
        authFailureAnalyticsService.updateFailurePatterns(
                failureData.getFailureType(),
                assessment.getThreatLevel(),
                failureData.getAttackSignatures(),
                failureData.getRiskIndicators()
        );

        // Update attack detection models
        authFailureAnalyticsService.updateAttackDetectionModels(
                failureData.getIpAddress(),
                assessment.getAttackType(),
                failureData.getBehaviorAnalysis()
        );
    }

    private void escalateToExecutives(AuthFailureData failureData,
                                    AuthFailureThreatAssessment assessment,
                                    String incidentId) {
        notificationService.sendExecutiveAlert(
                "Critical Authentication Attack - Executive Action Required",
                String.format("Large-scale authentication attack detected: IP %s, Attack Type: %s, Attempts: %d",
                        failureData.getIpAddress(),
                        assessment.getAttackType(),
                        failureData.getAttemptCount() != null ? failureData.getAttemptCount() : 0),
                Map.of(
                        "incidentId", incidentId != null ? incidentId : "N/A",
                        "attackType", assessment.getAttackType(),
                        "threatLevel", assessment.getThreatLevel(),
                        "ipAddress", failureData.getIpAddress()
                )
        );
    }

    private void notifyCustomer(AuthFailureData failureData, AuthFailureThreatAssessment assessment) {
        if (failureData.getUserId() != null) {
            notificationService.sendSecurityAlert(
                    failureData.getUserId(),
                    "Security Alert - Suspicious Login Attempts",
                    "We've detected suspicious login attempts on your account. If this wasn't you, please change your password immediately.",
                    Map.of("severity", "HIGH", "actionRequired", true)
            );
        }
    }

    private void initiateComplianceReporting(AuthFailureData failureData,
                                           AuthFailureThreatAssessment assessment,
                                           String incidentId) {
        // Create compliance reports for significant auth attacks
        securityIncidentService.initiateComplianceReporting(
                incidentId != null ? incidentId : failureData.getEventId(),
                assessment.getAttackType(),
                assessment.getThreatLevel(),
                failureData.getAttemptCount()
        );
    }

    private void executeEmergencyFallback(AuthFailureData failureData, Exception error) {
        log.error("EMERGENCY: Executing emergency auth failure fallback due to DLQ processing failure");

        try {
            // Emergency IP blocking
            bruteForceProtectionService.emergencyIpBlock(failureData.getIpAddress(), "EMERGENCY_FALLBACK");

            // Emergency notification
            notificationService.sendEmergencyAlert(
                    "CRITICAL: Auth Failure DLQ Processing Failed",
                    String.format("Failed to process critical auth failure DLQ for IP %s: %s",
                            failureData.getIpAddress(), error.getMessage())
            );

            // Manual intervention alert
            notificationService.escalateToManualIntervention(
                    failureData.getEventId(),
                    "AUTH_FAILURE_DLQ_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency auth failure fallback procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("AUTH FAILURE DLQ: Validation failed for failure DLQ event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "AUTH_FAILURE_DLQ_VALIDATION_ERROR",
                null,
                "Auth failure DLQ validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditAuthFailureDlqProcessing(AuthFailureData failureData,
                                             GenericKafkaEvent event,
                                             AuthFailureThreatAssessment assessment,
                                             String status) {
        try {
            auditService.auditSecurityEvent(
                    "AUTH_FAILURE_DLQ_EVENT_PROCESSED",
                    failureData != null ? failureData.getUserId() : null,
                    String.format("Auth failure DLQ event processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "failureType", failureData != null ? failureData.getFailureType() : "unknown",
                            "threatLevel", assessment != null ? assessment.getThreatLevel() : "unknown",
                            "attackDetected", assessment != null ? assessment.isAttackDetected() : false,
                            "attackType", assessment != null ? assessment.getAttackType() : "none",
                            "protectionAction", assessment != null ? assessment.getProtectionAction() : "none",
                            "ipAddress", failureData != null ? failureData.getIpAddress() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit auth failure DLQ processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: Auth failure DLQ event sent to final DLT - EventId: {}", event.getEventId());

        try {
            AuthFailureData failureData = extractFailureData(event.getPayload());

            // Emergency protection for final DLT events
            bruteForceProtectionService.emergencyIpBlock(failureData.getIpAddress(), "FINAL_DLT_PROTECTION");

            // Critical alert for DLT
            notificationService.sendEmergencyAlert(
                    "CRITICAL: Auth Failure DLQ in Final DLT",
                    "Critical auth failure could not be processed even in DLQ - immediate manual intervention required"
            );

        } catch (Exception e) {
            log.error("Failed to handle auth failure DLQ final DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleAuthFailureDlqFailure(GenericKafkaEvent event, String topic, int partition,
                                          long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for auth failure DLQ processing - EventId: {}",
                event.getEventId(), e);

        try {
            AuthFailureData failureData = extractFailureData(event.getPayload());

            // Emergency protection
            bruteForceProtectionService.emergencyIpBlock(failureData.getIpAddress(), "CIRCUIT_BREAKER_FALLBACK");

            // Emergency alert
            notificationService.sendEmergencyAlert(
                    "Auth Failure DLQ Circuit Breaker Open",
                    "Auth failure DLQ processing is failing - authentication security severely compromised"
            );

        } catch (Exception ex) {
            log.error("Failed to handle auth failure DLQ circuit breaker fallback", ex);
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
    public static class AuthFailureData {
        private String eventId;
        private String userId;
        private String accountId;
        private String sessionId;
        private String failureType;
        private String failureReason;
        private String threatLevel;
        private Integer attemptCount;
        private Map<String, Object> originalEvent;
        private String dlqFailureReason;
        private Integer retryCount;
        private Instant dlqTimestamp;
        private Instant originalTimestamp;
        private String ipAddress;
        private String userAgent;
        private String deviceFingerprint;
        private Map<String, Object> geolocation;
        private String authMethod;
        private String credentialType;
        private List<String> attackSignatures;
        private List<String> riskIndicators;
        private Map<String, Object> behaviorAnalysis;
        private Map<String, Object> threatIntelligence;
        private List<String> complianceFlags;
        private String sourceSystem;
        private Map<String, Object> networkInfo;
    }

    @lombok.Data
    @lombok.Builder
    public static class AuthFailureThreatAssessment {
        private String threatLevel;
        private boolean attackDetected;
        private String attackType;
        private String protectionAction;
        private List<String> protectionMeasures;
        private boolean requiresIncident;
        private boolean executiveEscalation;
        private boolean customerNotification;
        private boolean threatIntelligenceUpdate;
        private boolean complianceReporting;
        private boolean networkBlocking;
    }
}