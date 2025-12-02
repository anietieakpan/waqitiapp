package com.waqiti.account.kafka;

import com.waqiti.common.events.AccountTakeoverEvent;
import com.waqiti.account.domain.AccountTakeoverIncident;
import com.waqiti.account.repository.AccountTakeoverIncidentRepository;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.service.AccountTakeoverService;
import com.waqiti.account.service.SecurityService;
import com.waqiti.account.service.FraudDetectionService;
import com.waqiti.account.metrics.AccountMetricsService;
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
public class AccountTakeoverDetectionConsumer {

    private final AccountTakeoverIncidentRepository takeoverIncidentRepository;
    private final AccountRepository accountRepository;
    private final AccountTakeoverService takeoverService;
    private final SecurityService securityService;
    private final FraudDetectionService fraudDetectionService;
    private final AccountMetricsService metricsService;
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
    private Counter takeoverDetectedCounter;
    private Counter falsePositiveCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("account_takeover_detection_processed_total")
            .description("Total number of successfully processed account takeover detection events")
            .register(meterRegistry);
        errorCounter = Counter.builder("account_takeover_detection_errors_total")
            .description("Total number of account takeover detection processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("account_takeover_detection_processing_duration")
            .description("Time taken to process account takeover detection events")
            .register(meterRegistry);
        takeoverDetectedCounter = Counter.builder("account_takeover_detected_total")
            .description("Total number of account takeover attempts detected")
            .register(meterRegistry);
        falsePositiveCounter = Counter.builder("account_takeover_false_positives_total")
            .description("Total number of false positive takeover detections")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"account-takeover-detection", "account-takeover-alerts", "fraud-takeover-signals"},
        groupId = "account-takeover-detection-service-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        retryTopicSuffix = "-retry",
        dltTopicSuffix = "-dlt"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "account-takeover-detection", fallbackMethod = "handleAccountTakeoverEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAccountTakeoverEvent(
            @Payload AccountTakeoverEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("takeover-%s-p%d-o%d", event.getAccountId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAccountId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing account takeover detection event: accountId={}, threatLevel={}, indicators={}",
                event.getAccountId(), event.getThreatLevel(), event.getRiskIndicators());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case TAKEOVER_ATTEMPT_DETECTED:
                    processTakeoverAttemptDetection(event, correlationId);
                    break;

                case SUSPICIOUS_LOGIN_PATTERN:
                    processSuspiciousLoginPattern(event, correlationId);
                    break;

                case DEVICE_ANOMALY_DETECTED:
                    processDeviceAnomalyDetection(event, correlationId);
                    break;

                case CREDENTIAL_COMPROMISE_DETECTED:
                    processCredentialCompromiseDetection(event, correlationId);
                    break;

                case BEHAVIORAL_ANOMALY_DETECTED:
                    processBehavioralAnomalyDetection(event, correlationId);
                    break;

                case TAKEOVER_CONFIRMED:
                    confirmAccountTakeover(event, correlationId);
                    break;

                case TAKEOVER_FALSE_POSITIVE:
                    processFalsePositive(event, correlationId);
                    break;

                case TAKEOVER_INCIDENT_RESOLVED:
                    resolveIncident(event, correlationId);
                    break;

                case EMERGENCY_LOCKDOWN_TRIGGERED:
                    processEmergencyLockdown(event, correlationId);
                    break;

                case RECOVERY_PROCESS_INITIATED:
                    initiateAccountRecovery(event, correlationId);
                    break;

                default:
                    log.warn("Unknown account takeover detection event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("ACCOUNT_TAKEOVER_EVENT_PROCESSED", event.getAccountId(),
                Map.of("eventType", event.getEventType(), "threatLevel", event.getThreatLevel(),
                    "riskIndicators", event.getRiskIndicators(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process account takeover detection event: {}", e.getMessage(), e);

            // Send fallback event with enhanced retry information
            kafkaTemplate.send("account-takeover-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 5, "priority", "HIGH"));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAccountTakeoverEventFallback(
            AccountTakeoverEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("takeover-fallback-%s-p%d-o%d", event.getAccountId(), partition, offset);

        log.error("Circuit breaker fallback triggered for account takeover detection: accountId={}, error={}",
            event.getAccountId(), ex.getMessage());

        // Send to enhanced dead letter queue with priority handling
        kafkaTemplate.send("account-takeover-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now(),
            "priority", "CRITICAL",
            "requiresImmediateAttention", true));

        // Trigger emergency notification
        try {
            notificationService.sendCriticalAlert(
                "Account Takeover Detection Circuit Breaker Triggered",
                String.format("CRITICAL: Account %s takeover detection failed: %s. Manual intervention required immediately.",
                    event.getAccountId(), ex.getMessage()),
                Map.of("accountId", event.getAccountId(), "error", ex.getMessage(),
                    "correlationId", correlationId, "priority", "EMERGENCY")
            );

            // Also send to security operations center
            notificationService.sendSecurityAlert(
                "Takeover Detection System Failure",
                String.format("Account takeover detection system failure for account %s", event.getAccountId()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert for takeover detection failure: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAccountTakeoverEvent(
            @Payload AccountTakeoverEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        String correlationId = String.format("dlt-takeover-%s-%d", event.getAccountId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Account takeover detection permanently failed: accountId={}, topic={}, error={}",
            event.getAccountId(), topic, exceptionMessage);

        // Save to dead letter store with enhanced metadata
        auditService.logSecurityEvent("ACCOUNT_TAKEOVER_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "partition", partition, "offset", offset,
                "threatLevel", event.getThreatLevel(),
                "requiresManualIntervention", true,
                "criticalityLevel", "EMERGENCY",
                "timestamp", Instant.now()));

        // Send emergency alert to security team
        try {
            notificationService.sendEmergencyAlert(
                "Account Takeover Detection Dead Letter Event",
                String.format("EMERGENCY: Account %s takeover detection sent to DLT: %s. Immediate security review required.",
                    event.getAccountId(), exceptionMessage),
                Map.of("accountId", event.getAccountId(), "topic", topic,
                    "correlationId", correlationId, "threatLevel", event.getThreatLevel(),
                    "originalError", exceptionMessage)
            );

            // Escalate to security operations center
            kafkaTemplate.send("security-escalation-queue", Map.of(
                "accountId", event.getAccountId(),
                "incidentType", "TAKEOVER_DETECTION_FAILURE",
                "severity", "CRITICAL",
                "description", "Account takeover detection system failure - DLT event",
                "correlationId", correlationId,
                "requiresImmediateAction", true,
                "timestamp", Instant.now()
            ));

        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert for takeover detection: {}", ex.getMessage());
            // As a last resort, log to a specific security log that's monitored
            log.error("SECURITY_CRITICAL_FAILURE: Account {} takeover detection DLT failure. Manual intervention required immediately. Correlation: {}",
                event.getAccountId(), correlationId);
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

    private void processTakeoverAttemptDetection(AccountTakeoverEvent event, String correlationId) {
        AccountTakeoverIncident incident = AccountTakeoverIncident.builder()
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .threatLevel(event.getThreatLevel())
            .riskScore(event.getRiskScore())
            .riskIndicators(event.getRiskIndicators())
            .detectionSource(event.getDetectionSource())
            .detectedAt(LocalDateTime.now())
            .status("UNDER_INVESTIGATION")
            .correlationId(correlationId)
            .sourceIp(event.getSourceIp())
            .userAgent(event.getUserAgent())
            .deviceFingerprint(event.getDeviceFingerprint())
            .build();
        takeoverIncidentRepository.save(incident);

        // Immediate protective actions based on threat level
        if ("HIGH".equals(event.getThreatLevel()) || "CRITICAL".equals(event.getThreatLevel())) {
            // Immediately lock account
            takeoverService.emergencyLockAccount(event.getAccountId());

            // Send immediate notification to user
            notificationService.sendSecurityAlert(event.getUserId(),
                "Suspicious Activity Detected",
                "We've detected suspicious activity on your account and have temporarily secured it for your protection. Please contact us immediately.",
                correlationId);
        }

        // Enhanced fraud analysis
        fraudDetectionService.analyzeForAccountTakeover(event.getAccountId(), event.getRiskIndicators());

        // Send for further investigation
        kafkaTemplate.send("fraud-investigation-queue", Map.of(
            "accountId", event.getAccountId(),
            "incidentId", incident.getId(),
            "threatLevel", event.getThreatLevel(),
            "riskScore", event.getRiskScore(),
            "riskIndicators", event.getRiskIndicators(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        takeoverDetectedCounter.increment();
        metricsService.recordTakeoverAttemptDetected(event.getThreatLevel());

        log.warn("Account takeover attempt detected: accountId={}, threatLevel={}, riskScore={}, sourceIp={}",
            event.getAccountId(), event.getThreatLevel(), event.getRiskScore(), event.getSourceIp());
    }

    private void processSuspiciousLoginPattern(AccountTakeoverEvent event, String correlationId) {
        securityService.analyzeSuspiciousLoginPattern(event.getAccountId(), event.getLoginPatternData());

        // Check for velocity attacks
        if (takeoverService.isVelocityAttack(event.getAccountId(), event.getLoginPatternData())) {
            kafkaTemplate.send("account-takeover-detection", Map.of(
                "accountId", event.getAccountId(),
                "eventType", "TAKEOVER_ATTEMPT_DETECTED",
                "threatLevel", "HIGH",
                "riskIndicators", Arrays.asList("VELOCITY_ATTACK", "SUSPICIOUS_LOGIN_PATTERN"),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordSuspiciousLoginPattern();

        log.info("Suspicious login pattern detected: accountId={}, pattern={}",
            event.getAccountId(), event.getLoginPatternData());
    }

    private void processDeviceAnomalyDetection(AccountTakeoverEvent event, String correlationId) {
        securityService.analyzeDeviceAnomaly(event.getAccountId(), event.getDeviceData());

        // Check if device is known to be compromised
        if (securityService.isKnownCompromisedDevice(event.getDeviceFingerprint())) {
            kafkaTemplate.send("account-takeover-detection", Map.of(
                "accountId", event.getAccountId(),
                "eventType", "TAKEOVER_ATTEMPT_DETECTED",
                "threatLevel", "CRITICAL",
                "riskIndicators", Arrays.asList("COMPROMISED_DEVICE", "DEVICE_ANOMALY"),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordDeviceAnomalyDetected();

        log.info("Device anomaly detected: accountId={}, deviceFingerprint={}",
            event.getAccountId(), event.getDeviceFingerprint());
    }

    private void processCredentialCompromiseDetection(AccountTakeoverEvent event, String correlationId) {
        AccountTakeoverIncident incident = AccountTakeoverIncident.builder()
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .threatLevel("CRITICAL")
            .riskScore(95.0)
            .riskIndicators(Arrays.asList("CREDENTIAL_COMPROMISE"))
            .detectionSource("CREDENTIAL_MONITORING")
            .detectedAt(LocalDateTime.now())
            .status("CONFIRMED_COMPROMISE")
            .correlationId(correlationId)
            .build();
        takeoverIncidentRepository.save(incident);

        // Immediate account lockdown
        takeoverService.emergencyLockAccount(event.getAccountId());

        // Force password reset
        securityService.forcePasswordReset(event.getAccountId());

        // Invalidate all sessions
        securityService.invalidateAllSessions(event.getAccountId());

        notificationService.sendCriticalAlert(
            "Credential Compromise Detected",
            String.format("Credential compromise detected for account %s. Account locked and password reset required.",
                event.getAccountId()),
            Map.of("accountId", event.getAccountId(), "correlationId", correlationId)
        );

        metricsService.recordCredentialCompromiseDetected();

        log.error("Credential compromise detected: accountId={}, compromiseSource={}",
            event.getAccountId(), event.getCompromiseSource());
    }

    private void processBehavioralAnomalyDetection(AccountTakeoverEvent event, String correlationId) {
        fraudDetectionService.analyzeBehavioralAnomaly(event.getAccountId(), event.getBehavioralData());

        // Calculate combined risk score
        double combinedRiskScore = fraudDetectionService.calculateCombinedRiskScore(
            event.getAccountId(), event.getBehavioralData(), event.getRiskScore());

        if (combinedRiskScore > 80.0) {
            kafkaTemplate.send("account-takeover-detection", Map.of(
                "accountId", event.getAccountId(),
                "eventType", "TAKEOVER_ATTEMPT_DETECTED",
                "threatLevel", "HIGH",
                "riskScore", combinedRiskScore,
                "riskIndicators", Arrays.asList("BEHAVIORAL_ANOMALY", "HIGH_RISK_SCORE"),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordBehavioralAnomalyDetected();

        log.info("Behavioral anomaly detected: accountId={}, riskScore={}",
            event.getAccountId(), combinedRiskScore);
    }

    private void confirmAccountTakeover(AccountTakeoverEvent event, String correlationId) {
        AccountTakeoverIncident incident = takeoverIncidentRepository.findByAccountIdAndCorrelationId(
            event.getAccountId(), correlationId)
            .orElseThrow(() -> new RuntimeException("Takeover incident not found"));

        incident.setStatus("CONFIRMED");
        incident.setConfirmedAt(LocalDateTime.now());
        incident.setConfirmedBy(event.getConfirmedBy());
        takeoverIncidentRepository.save(incident);

        // Complete lockdown
        takeoverService.completeAccountLockdown(event.getAccountId());

        // Initiate recovery process
        kafkaTemplate.send("account-takeover-detection", Map.of(
            "accountId", event.getAccountId(),
            "eventType", "RECOVERY_PROCESS_INITIATED",
            "incidentId", incident.getId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Notify law enforcement if required
        if (incident.getRiskScore() > 90.0) {
            kafkaTemplate.send("law-enforcement-alerts", Map.of(
                "accountId", event.getAccountId(),
                "incidentType", "CONFIRMED_ACCOUNT_TAKEOVER",
                "severity", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        takeoverDetectedCounter.increment();
        metricsService.recordTakeoverConfirmed();

        log.error("Account takeover confirmed: accountId={}, confirmedBy={}",
            event.getAccountId(), event.getConfirmedBy());
    }

    private void processFalsePositive(AccountTakeoverEvent event, String correlationId) {
        AccountTakeoverIncident incident = takeoverIncidentRepository.findByAccountIdAndCorrelationId(
            event.getAccountId(), correlationId)
            .orElseThrow(() -> new RuntimeException("Takeover incident not found"));

        incident.setStatus("FALSE_POSITIVE");
        incident.setResolvedAt(LocalDateTime.now());
        incident.setResolutionNotes("Confirmed as false positive by investigation");
        takeoverIncidentRepository.save(incident);

        // Restore account access
        takeoverService.restoreAccountAccess(event.getAccountId());

        // Notify user
        notificationService.sendNotification(event.getUserId(),
            "Security Review Complete",
            "Our security review is complete. Your account access has been restored. We apologize for any inconvenience.",
            correlationId);

        // Update ML models to reduce false positives
        fraudDetectionService.updateModelForFalsePositive(event.getAccountId(), incident.getRiskIndicators());

        falsePositiveCounter.increment();
        metricsService.recordFalsePositive();

        log.info("Takeover detection marked as false positive: accountId={}", event.getAccountId());
    }

    private void resolveIncident(AccountTakeoverEvent event, String correlationId) {
        AccountTakeoverIncident incident = takeoverIncidentRepository.findByAccountIdAndCorrelationId(
            event.getAccountId(), correlationId)
            .orElseThrow(() -> new RuntimeException("Takeover incident not found"));

        incident.setStatus("RESOLVED");
        incident.setResolvedAt(LocalDateTime.now());
        incident.setResolvedBy(event.getResolvedBy());
        incident.setResolutionNotes(event.getResolutionNotes());
        takeoverIncidentRepository.save(incident);

        // Final security assessment
        securityService.performPostIncidentAssessment(event.getAccountId());

        metricsService.recordIncidentResolved();

        log.info("Takeover incident resolved: accountId={}, resolvedBy={}",
            event.getAccountId(), event.getResolvedBy());
    }

    private void processEmergencyLockdown(AccountTakeoverEvent event, String correlationId) {
        takeoverService.emergencyLockAccount(event.getAccountId());

        notificationService.sendEmergencyAlert(
            "Emergency Account Lockdown",
            String.format("Emergency lockdown triggered for account %s due to suspected takeover",
                event.getAccountId()),
            Map.of("accountId", event.getAccountId(), "correlationId", correlationId)
        );

        metricsService.recordEmergencyLockdown();

        log.error("Emergency lockdown triggered: accountId={}, reason={}",
            event.getAccountId(), event.getReason());
    }

    private void initiateAccountRecovery(AccountTakeoverEvent event, String correlationId) {
        takeoverService.initiateRecoveryProcess(event.getAccountId());

        // Send recovery instructions
        notificationService.sendSecurityNotification(event.getUserId(),
            "Account Recovery Process",
            "We've initiated the account recovery process. Please follow the instructions we've sent to your verified contact methods.",
            correlationId);

        kafkaTemplate.send("account-recovery-workflow", Map.of(
            "accountId", event.getAccountId(),
            "userId", event.getUserId(),
            "recoveryType", "TAKEOVER_RECOVERY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordRecoveryInitiated();

        log.info("Account recovery process initiated: accountId={}", event.getAccountId());
    }
}