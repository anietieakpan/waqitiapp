package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for authentication revocation events
 * Handles authentication credential and session revocations for security purposes
 *
 * Critical for: Access control, security incident response, credential management
 * SLA: Must process revocations within 5 seconds for immediate access termination
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthRevocationsConsumer {

    private final AuthRevocationService authRevocationService;
    private final AuthenticationSecurityService authenticationSecurityService;
    private final SessionManagementService sessionManagementService;
    private final CredentialManagementService credentialManagementService;
    private final CustomerCommunicationService customerCommunicationService;
    private final FraudNotificationService fraudNotificationService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("auth_revocations_processed_total")
            .description("Total number of successfully processed auth revocation events")
            .register(meterRegistry);
        errorCounter = Counter.builder("auth_revocations_errors_total")
            .description("Total number of auth revocation processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("auth_revocations_processing_duration")
            .description("Time taken to process auth revocation events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"auth-revocations", "credential-revocations", "session-revocations"},
        groupId = "fraud-auth-revocations-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "auth-revocations", fallbackMethod = "handleAuthRevocationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuthRevocationEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("auth-revoke-%s-p%d-o%d", event.getId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing auth revocation: id={}, type={}, userId={}",
                event.getId(), event.getEventType(), event.getData().get("userId"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String userId = (String) event.getData().get("userId");
            String sessionId = (String) event.getData().get("sessionId");
            String deviceId = (String) event.getData().get("deviceId");

            switch (event.getEventType()) {
                case "SESSION_REVOKED":
                    handleSessionRevoked(event, userId, sessionId, correlationId);
                    break;

                case "TOKEN_REVOKED":
                    handleTokenRevoked(event, userId, sessionId, correlationId);
                    break;

                case "CREDENTIAL_REVOKED":
                    handleCredentialRevoked(event, userId, correlationId);
                    break;

                case "DEVICE_REVOKED":
                    handleDeviceRevoked(event, userId, deviceId, correlationId);
                    break;

                case "API_KEY_REVOKED":
                    handleApiKeyRevoked(event, userId, correlationId);
                    break;

                case "CERTIFICATE_REVOKED":
                    handleCertificateRevoked(event, userId, correlationId);
                    break;

                case "EMERGENCY_REVOCATION":
                    handleEmergencyRevocation(event, userId, sessionId, deviceId, correlationId);
                    break;

                case "BULK_REVOCATION":
                    handleBulkRevocation(event, userId, correlationId);
                    break;

                case "MFA_DEVICE_REVOKED":
                    handleMfaDeviceRevoked(event, userId, deviceId, correlationId);
                    break;

                case "ACCOUNT_ACCESS_REVOKED":
                    handleAccountAccessRevoked(event, userId, correlationId);
                    break;

                default:
                    log.warn("Unknown auth revocation event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("AUTH_REVOCATION_PROCESSED", userId,
                Map.of("eventType", event.getEventType(), "sessionId", sessionId,
                    "deviceId", deviceId, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process auth revocation event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("auth-revocations-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuthRevocationEventFallback(
            GenericKafkaEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("auth-revoke-fallback-%s-p%d-o%d", event.getId(), partition, offset);

        log.error("Circuit breaker fallback triggered for auth revocation: id={}, error={}",
            event.getId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("auth-revocations-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical notification for revocation failures
        try {
            notificationService.sendCriticalAlert(
                "CRITICAL: Auth Revocations Consumer Circuit Breaker Triggered",
                String.format("Security alert: Auth revocation event %s failed: %s", event.getId(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuthRevocationEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-auth-revoke-%s-%d", event.getId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Auth revocation permanently failed: id={}, topic={}, error={}",
            event.getId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSecurityEvent("AUTH_REVOCATION_DLT_EVENT",
            String.valueOf(event.getData().get("userId")),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresImmediateAction", true, "timestamp", Instant.now()));

        // Send emergency alert for revocation failures
        try {
            notificationService.sendEmergencyAlert(
                "EMERGENCY: Auth Revocation Event Dead Letter",
                String.format("CRITICAL SECURITY: Auth revocation event %s permanently failed: %s",
                    event.getId(), exceptionMessage),
                Map.of("eventId", event.getId(), "topic", topic, "correlationId", correlationId,
                    "securityImplication", "HIGH")
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
        }
    }

    private void handleSessionRevoked(GenericKafkaEvent event, String userId, String sessionId, String correlationId) {
        String revocationReason = (String) event.getData().get("revocationReason");
        String revokedBy = (String) event.getData().get("revokedBy");
        log.info("Session revoked: userId={}, sessionId={}, reason={}, by={}", userId, sessionId, revocationReason, revokedBy);

        // Revoke the session immediately
        sessionManagementService.revokeSession(userId, sessionId, revocationReason, revokedBy);

        // Invalidate all associated tokens
        authRevocationService.invalidateSessionTokens(sessionId);

        // Log security event
        auditService.logSecurityEvent("SESSION_REVOKED", userId,
            Map.of("sessionId", sessionId, "reason", revocationReason, "revokedBy", revokedBy));

        // Notify customer if appropriate
        if (!"LOGOUT".equals(revocationReason)) {
            customerCommunicationService.sendSessionRevocationNotification(userId, sessionId, revocationReason);
        }

        log.info("Session revocation completed: userId={}, sessionId={}", userId, sessionId);
    }

    private void handleTokenRevoked(GenericKafkaEvent event, String userId, String sessionId, String correlationId) {
        String tokenType = (String) event.getData().get("tokenType");
        String tokenId = (String) event.getData().get("tokenId");
        String revocationReason = (String) event.getData().get("revocationReason");
        log.info("Token revoked: userId={}, type={}, tokenId={}, reason={}", userId, tokenType, tokenId, revocationReason);

        // Revoke the specific token
        authRevocationService.revokeToken(userId, tokenId, tokenType, revocationReason);

        // Add to revocation list
        authRevocationService.addToRevocationList(tokenId, tokenType, revocationReason);

        // Check if session should be terminated
        if ("ACCESS_TOKEN".equals(tokenType)) {
            boolean shouldTerminate = authRevocationService.shouldTerminateSession(sessionId, tokenId);
            if (shouldTerminate) {
                sessionManagementService.terminateSession(userId, sessionId, "TOKEN_REVOKED");
            }
        }

        log.info("Token revocation completed: userId={}, tokenType={}", userId, tokenType);
    }

    private void handleCredentialRevoked(GenericKafkaEvent event, String userId, String correlationId) {
        String credentialType = (String) event.getData().get("credentialType");
        String revocationReason = (String) event.getData().get("revocationReason");
        log.warn("Credential revoked: userId={}, type={}, reason={}", userId, credentialType, revocationReason);

        // Revoke the credential
        credentialManagementService.revokeCredential(userId, credentialType, revocationReason);

        // Terminate all active sessions for security
        sessionManagementService.terminateAllUserSessions(userId, "CREDENTIAL_REVOKED");

        // Force password reset if applicable
        if ("PASSWORD".equals(credentialType)) {
            credentialManagementService.forcePasswordReset(userId, revocationReason);
        }

        // Send critical notification
        customerCommunicationService.sendCredentialRevocationNotification(userId, credentialType, revocationReason);

        // Alert security team
        fraudNotificationService.sendCredentialRevocationAlert(userId, credentialType, revocationReason, correlationId);

        log.info("Credential revocation completed: userId={}, type={}", userId, credentialType);
    }

    private void handleDeviceRevoked(GenericKafkaEvent event, String userId, String deviceId, String correlationId) {
        String revocationReason = (String) event.getData().get("revocationReason");
        log.warn("Device revoked: userId={}, deviceId={}, reason={}", userId, deviceId, revocationReason);

        // Revoke device access
        authRevocationService.revokeDeviceAccess(userId, deviceId, revocationReason);

        // Terminate all sessions on the device
        sessionManagementService.terminateDeviceSessions(userId, deviceId, "DEVICE_REVOKED");

        // Remove device from trusted devices
        authenticationSecurityService.removeFromTrustedDevices(userId, deviceId);

        // Notify customer
        customerCommunicationService.sendDeviceRevocationNotification(userId, deviceId, revocationReason);

        log.info("Device revocation completed: userId={}, deviceId={}", userId, deviceId);
    }

    private void handleApiKeyRevoked(GenericKafkaEvent event, String userId, String correlationId) {
        String apiKeyId = (String) event.getData().get("apiKeyId");
        String revocationReason = (String) event.getData().get("revocationReason");
        log.info("API key revoked: userId={}, apiKeyId={}, reason={}", userId, apiKeyId, revocationReason);

        // Revoke API key
        authRevocationService.revokeApiKey(userId, apiKeyId, revocationReason);

        // Add to API key blacklist
        authRevocationService.addApiKeyToBlacklist(apiKeyId, revocationReason);

        // Audit API key usage
        auditService.logSecurityEvent("API_KEY_REVOKED", userId,
            Map.of("apiKeyId", apiKeyId, "reason", revocationReason));

        log.info("API key revocation completed: userId={}, apiKeyId={}", userId, apiKeyId);
    }

    private void handleCertificateRevoked(GenericKafkaEvent event, String userId, String correlationId) {
        String certificateId = (String) event.getData().get("certificateId");
        String revocationReason = (String) event.getData().get("revocationReason");
        log.warn("Certificate revoked: userId={}, certificateId={}, reason={}", userId, certificateId, revocationReason);

        // Revoke certificate
        credentialManagementService.revokeCertificate(userId, certificateId, revocationReason);

        // Update certificate revocation list
        credentialManagementService.updateCertificateRevocationList(certificateId, revocationReason);

        // Terminate certificate-based sessions
        sessionManagementService.terminateCertificateBasedSessions(userId, certificateId);

        log.info("Certificate revocation completed: userId={}, certificateId={}", userId, certificateId);
    }

    private void handleEmergencyRevocation(GenericKafkaEvent event, String userId, String sessionId, String deviceId, String correlationId) {
        String emergencyReason = (String) event.getData().get("emergencyReason");
        String triggeredBy = (String) event.getData().get("triggeredBy");
        log.error("EMERGENCY revocation: userId={}, reason={}, triggeredBy={}", userId, emergencyReason, triggeredBy);

        // Execute emergency revocation protocol
        authRevocationService.executeEmergencyRevocation(userId, emergencyReason, triggeredBy);

        // Terminate all user sessions immediately
        sessionManagementService.emergencyTerminateAllSessions(userId, emergencyReason);

        // Disable account temporarily
        authenticationSecurityService.disableAccountTemporarily(userId, emergencyReason);

        // Send emergency notifications
        fraudNotificationService.sendEmergencyRevocationAlert(userId, emergencyReason, triggeredBy, correlationId);
        customerCommunicationService.sendEmergencyAccountActionNotification(userId, emergencyReason);

        log.info("Emergency revocation completed: userId={}, reason={}", userId, emergencyReason);
    }

    private void handleBulkRevocation(GenericKafkaEvent event, String userId, String correlationId) {
        String bulkReason = (String) event.getData().get("bulkReason");
        Integer affectedSessions = (Integer) event.getData().get("affectedSessions");
        log.warn("Bulk revocation: userId={}, reason={}, sessions={}", userId, bulkReason, affectedSessions);

        // Execute bulk revocation
        authRevocationService.executeBulkRevocation(userId, bulkReason);

        // Terminate all sessions
        sessionManagementService.terminateAllUserSessions(userId, bulkReason);

        // Invalidate all tokens
        authRevocationService.invalidateAllUserTokens(userId, bulkReason);

        // Send bulk revocation notification
        customerCommunicationService.sendBulkRevocationNotification(userId, bulkReason, affectedSessions);

        log.info("Bulk revocation completed: userId={}, sessions={}", userId, affectedSessions);
    }

    private void handleMfaDeviceRevoked(GenericKafkaEvent event, String userId, String deviceId, String correlationId) {
        String mfaDeviceType = (String) event.getData().get("mfaDeviceType");
        String revocationReason = (String) event.getData().get("revocationReason");
        log.warn("MFA device revoked: userId={}, deviceId={}, type={}, reason={}",
            userId, deviceId, mfaDeviceType, revocationReason);

        // Revoke MFA device
        authRevocationService.revokeMfaDevice(userId, deviceId, mfaDeviceType, revocationReason);

        // Force MFA re-enrollment
        authenticationSecurityService.forceMfaReEnrollment(userId, mfaDeviceType);

        // Notify customer
        customerCommunicationService.sendMfaDeviceRevocationNotification(userId, mfaDeviceType, revocationReason);

        log.info("MFA device revocation completed: userId={}, type={}", userId, mfaDeviceType);
    }

    private void handleAccountAccessRevoked(GenericKafkaEvent event, String userId, String correlationId) {
        String accessLevel = (String) event.getData().get("accessLevel");
        String revocationReason = (String) event.getData().get("revocationReason");
        log.error("Account access revoked: userId={}, level={}, reason={}", userId, accessLevel, revocationReason);

        // Revoke account access
        authRevocationService.revokeAccountAccess(userId, accessLevel, revocationReason);

        // Terminate all sessions
        sessionManagementService.terminateAllUserSessions(userId, "ACCESS_REVOKED");

        // Disable account
        authenticationSecurityService.disableAccount(userId, revocationReason);

        // Send critical notification
        customerCommunicationService.sendAccountAccessRevocationNotification(userId, accessLevel, revocationReason);

        // Alert compliance team
        fraudNotificationService.sendAccountAccessRevocationAlert(userId, accessLevel, revocationReason, correlationId);

        log.info("Account access revocation completed: userId={}, level={}", userId, accessLevel);
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