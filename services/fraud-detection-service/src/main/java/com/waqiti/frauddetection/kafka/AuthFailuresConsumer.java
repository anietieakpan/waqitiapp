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
 * Production-grade Kafka consumer for authentication failure events
 * Handles authentication failures and security incident response
 *
 * Critical for: Authentication security, brute force detection, account protection
 * SLA: Must process auth failures within 3 seconds for immediate threat response
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthFailuresConsumer {

    private final AuthFailureAnalysisService authFailureAnalysisService;
    private final AuthenticationSecurityService authenticationSecurityService;
    private final BruteForceDetectionService bruteForceDetectionService;
    private final AccountProtectionService accountProtectionService;
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
        successCounter = Counter.builder("auth_failures_processed_total")
            .description("Total number of successfully processed auth failure events")
            .register(meterRegistry);
        errorCounter = Counter.builder("auth_failures_errors_total")
            .description("Total number of auth failure processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("auth_failures_processing_duration")
            .description("Time taken to process auth failure events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"auth-failures", "authentication-failures", "login-failures"},
        groupId = "fraud-auth-failures-group",
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
    @CircuitBreaker(name = "auth-failures", fallbackMethod = "handleAuthFailureEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuthFailureEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("auth-fail-%s-p%d-o%d", event.getId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing auth failure: id={}, type={}, userId={}",
                event.getId(), event.getEventType(), event.getData().get("userId"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String userId = (String) event.getData().get("userId");
            String ipAddress = (String) event.getData().get("ipAddress");
            String deviceId = (String) event.getData().get("deviceId");

            switch (event.getEventType()) {
                case "LOGIN_FAILED":
                    handleLoginFailed(event, userId, ipAddress, deviceId, correlationId);
                    break;

                case "PASSWORD_INCORRECT":
                    handlePasswordIncorrect(event, userId, ipAddress, deviceId, correlationId);
                    break;

                case "ACCOUNT_LOCKED":
                    handleAccountLocked(event, userId, correlationId);
                    break;

                case "BRUTE_FORCE_DETECTED":
                    handleBruteForceDetected(event, userId, ipAddress, deviceId, correlationId);
                    break;

                case "SUSPICIOUS_LOGIN_ATTEMPT":
                    handleSuspiciousLoginAttempt(event, userId, ipAddress, deviceId, correlationId);
                    break;

                case "MFA_FAILURE":
                    handleMfaFailure(event, userId, deviceId, correlationId);
                    break;

                case "SESSION_EXPIRED":
                    handleSessionExpired(event, userId, correlationId);
                    break;

                case "ACCOUNT_DISABLED":
                    handleAccountDisabled(event, userId, correlationId);
                    break;

                case "IP_BLOCKED":
                    handleIpBlocked(event, ipAddress, correlationId);
                    break;

                default:
                    log.warn("Unknown auth failure event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("AUTH_FAILURE_PROCESSED", userId,
                Map.of("eventType", event.getEventType(), "ipAddress", ipAddress,
                    "deviceId", deviceId, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process auth failure event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("auth-failures-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuthFailureEventFallback(
            GenericKafkaEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("auth-fail-fallback-%s-p%d-o%d", event.getId(), partition, offset);

        log.error("Circuit breaker fallback triggered for auth failure: id={}, error={}",
            event.getId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("auth-failures-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to security team
        try {
            notificationService.sendSecurityAlert(
                "Auth Failures Consumer Circuit Breaker Triggered",
                String.format("Security alert: Auth failure event %s failed: %s", event.getId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send security alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuthFailureEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-auth-fail-%s-%d", event.getId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Auth failure permanently failed: id={}, topic={}, error={}",
            event.getId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSecurityEvent("AUTH_FAILURE_DLT_EVENT",
            String.valueOf(event.getData().get("userId")),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresSecurityReview", true, "timestamp", Instant.now()));

        // Send critical security alert
        try {
            notificationService.sendCriticalAlert(
                "CRITICAL: Auth Failure Event Dead Letter",
                String.format("Security escalation: Auth failure event %s sent to DLT: %s", event.getId(), exceptionMessage),
                Map.of("eventId", event.getId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical security alert: {}", ex.getMessage());
        }
    }

    private void handleLoginFailed(GenericKafkaEvent event, String userId, String ipAddress, String deviceId, String correlationId) {
        String failureReason = (String) event.getData().get("failureReason");
        Integer attemptCount = (Integer) event.getData().get("attemptCount");
        log.warn("Login failed: userId={}, reason={}, attempts={}, ip={}", userId, failureReason, attemptCount, ipAddress);

        // Record login failure
        authFailureAnalysisService.recordLoginFailure(userId, ipAddress, deviceId, failureReason);

        // Check for suspicious patterns
        boolean isSuspicious = authFailureAnalysisService.analyzeSuspiciousPattern(userId, ipAddress, attemptCount);

        if (isSuspicious) {
            // Trigger enhanced security measures
            authenticationSecurityService.triggerEnhancedSecurity(userId, ipAddress, deviceId);
        }

        // Check if account lockout threshold reached
        if (attemptCount >= 5) {
            accountProtectionService.considerAccountLockout(userId, attemptCount, correlationId);
        }

        log.info("Login failure processed: userId={}, suspicious={}", userId, isSuspicious);
    }

    private void handlePasswordIncorrect(GenericKafkaEvent event, String userId, String ipAddress, String deviceId, String correlationId) {
        Integer consecutiveFailures = (Integer) event.getData().get("consecutiveFailures");
        log.warn("Password incorrect: userId={}, consecutive={}, ip={}", userId, consecutiveFailures, ipAddress);

        // Track password failures
        authFailureAnalysisService.trackPasswordFailures(userId, ipAddress, consecutiveFailures);

        // Check for brute force attack
        boolean isBruteForce = bruteForceDetectionService.detectBruteForceAttempt(userId, ipAddress, consecutiveFailures);

        if (isBruteForce) {
            // Block IP and lock account
            authenticationSecurityService.blockIpAddress(ipAddress, "BRUTE_FORCE_DETECTED");
            accountProtectionService.lockAccount(userId, "BRUTE_FORCE_PROTECTION", correlationId);

            // Send security alert
            fraudNotificationService.sendBruteForceAlert(userId, ipAddress, consecutiveFailures, correlationId);
        }

        log.info("Password incorrect processed: userId={}, bruteForce={}", userId, isBruteForce);
    }

    private void handleAccountLocked(GenericKafkaEvent event, String userId, String correlationId) {
        String lockReason = (String) event.getData().get("lockReason");
        Long lockDuration = (Long) event.getData().get("lockDuration");
        log.warn("Account locked: userId={}, reason={}, duration={}min", userId, lockReason, lockDuration);

        // Record account lockout
        accountProtectionService.recordAccountLockout(userId, lockReason, lockDuration);

        // Notify customer of lockout
        customerCommunicationService.sendAccountLockedNotification(userId, lockReason, lockDuration);

        // Schedule automatic unlock if applicable
        if (lockDuration != null && lockDuration > 0) {
            accountProtectionService.scheduleAccountUnlock(userId, lockDuration);
        }

        log.info("Account lockout processed: userId={}, duration={}min", userId, lockDuration);
    }

    private void handleBruteForceDetected(GenericKafkaEvent event, String userId, String ipAddress, String deviceId, String correlationId) {
        Integer attackIntensity = (Integer) event.getData().get("attackIntensity");
        Long attackDuration = (Long) event.getData().get("attackDuration");
        log.error("Brute force detected: userId={}, ip={}, intensity={}, duration={}s",
            userId, ipAddress, attackIntensity, attackDuration);

        // Activate brute force protection
        bruteForceDetectionService.activateBruteForceProtection(userId, ipAddress, deviceId, attackIntensity);

        // Block IP immediately
        authenticationSecurityService.blockIpAddress(ipAddress, "BRUTE_FORCE_ATTACK");

        // Lock account for protection
        accountProtectionService.lockAccount(userId, "BRUTE_FORCE_DETECTED", correlationId);

        // Send critical security alert
        fraudNotificationService.sendCriticalSecurityAlert(
            "Brute Force Attack Detected",
            String.format("User %s under brute force attack from IP %s", userId, ipAddress),
            correlationId
        );

        log.info("Brute force attack handled: userId={}, ip={}", userId, ipAddress);
    }

    private void handleSuspiciousLoginAttempt(GenericKafkaEvent event, String userId, String ipAddress, String deviceId, String correlationId) {
        String suspiciousIndicators = (String) event.getData().get("suspiciousIndicators");
        Double riskScore = (Double) event.getData().get("riskScore");
        log.warn("Suspicious login attempt: userId={}, ip={}, indicators={}, riskScore={}",
            userId, ipAddress, suspiciousIndicators, riskScore);

        // Analyze suspicious login
        authFailureAnalysisService.analyzeSuspiciousLogin(userId, ipAddress, deviceId, suspiciousIndicators, riskScore);

        // Apply additional verification based on risk score
        if (riskScore > 0.7) {
            // Require additional authentication
            authenticationSecurityService.requireAdditionalAuthentication(userId, "HIGH_RISK_LOGIN");

            // Send security notification
            customerCommunicationService.sendSuspiciousActivityAlert(userId, ipAddress, suspiciousIndicators);
        }

        log.info("Suspicious login attempt processed: userId={}, riskScore={}", userId, riskScore);
    }

    private void handleMfaFailure(GenericKafkaEvent event, String userId, String deviceId, String correlationId) {
        String mfaMethod = (String) event.getData().get("mfaMethod");
        String failureReason = (String) event.getData().get("failureReason");
        Integer attemptCount = (Integer) event.getData().get("attemptCount");
        log.warn("MFA failure: userId={}, method={}, reason={}, attempts={}", userId, mfaMethod, failureReason, attemptCount);

        // Record MFA failure
        authFailureAnalysisService.recordMfaFailure(userId, deviceId, mfaMethod, failureReason);

        // Check for MFA bypass attempts
        boolean isBypassAttempt = authFailureAnalysisService.detectMfaBypassAttempt(userId, mfaMethod, attemptCount);

        if (isBypassAttempt) {
            // Lock account temporarily
            accountProtectionService.lockAccount(userId, "MFA_BYPASS_ATTEMPT", correlationId);

            // Send security alert
            fraudNotificationService.sendMfaBypassAlert(userId, mfaMethod, attemptCount, correlationId);
        }

        log.info("MFA failure processed: userId={}, bypassAttempt={}", userId, isBypassAttempt);
    }

    private void handleSessionExpired(GenericKafkaEvent event, String userId, String correlationId) {
        String sessionId = (String) event.getData().get("sessionId");
        String expirationReason = (String) event.getData().get("expirationReason");
        log.info("Session expired: userId={}, sessionId={}, reason={}", userId, sessionId, expirationReason);

        // Clean up expired session
        authenticationSecurityService.cleanupExpiredSession(userId, sessionId);

        // Check for suspicious session patterns
        boolean suspiciousPattern = authFailureAnalysisService.checkSuspiciousSessionPattern(userId, sessionId, expirationReason);

        if (suspiciousPattern) {
            // Investigate session activity
            authFailureAnalysisService.investigateSessionActivity(userId, sessionId, correlationId);
        }

        log.info("Session expiration processed: userId={}, suspicious={}", userId, suspiciousPattern);
    }

    private void handleAccountDisabled(GenericKafkaEvent event, String userId, String correlationId) {
        String disableReason = (String) event.getData().get("disableReason");
        log.warn("Account disabled: userId={}, reason={}", userId, disableReason);

        // Record account disabling
        accountProtectionService.recordAccountDisabling(userId, disableReason);

        // Notify customer of account status
        customerCommunicationService.sendAccountDisabledNotification(userId, disableReason);

        // Check if disabling was fraud-related
        if ("FRAUD_DETECTED".equals(disableReason) || "SECURITY_BREACH".equals(disableReason)) {
            // Escalate to fraud investigation
            fraudNotificationService.escalateToFraudInvestigation(userId, disableReason, correlationId);
        }

        log.info("Account disabling processed: userId={}, reason={}", userId, disableReason);
    }

    private void handleIpBlocked(GenericKafkaEvent event, String ipAddress, String correlationId) {
        String blockReason = (String) event.getData().get("blockReason");
        Long blockDuration = (Long) event.getData().get("blockDuration");
        log.warn("IP blocked: ip={}, reason={}, duration={}min", ipAddress, blockReason, blockDuration);

        // Record IP blocking
        authenticationSecurityService.recordIpBlocking(ipAddress, blockReason, blockDuration);

        // Add to threat intelligence
        authFailureAnalysisService.addToThreatIntelligence(ipAddress, blockReason);

        // Schedule automatic unblock if applicable
        if (blockDuration != null && blockDuration > 0) {
            authenticationSecurityService.scheduleIpUnblock(ipAddress, blockDuration);
        }

        log.info("IP blocking processed: ip={}, duration={}min", ipAddress, blockDuration);
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