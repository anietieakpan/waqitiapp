package com.waqiti.auth.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.auth.service.SessionService;
import com.waqiti.auth.service.SecurityService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionEventsDlqConsumer extends BaseDlqConsumer {

    private final SessionService sessionService;
    private final SecurityService securityService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public SessionEventsDlqConsumer(SessionService sessionService,
                                    SecurityService securityService,
                                    MeterRegistry meterRegistry) {
        super("session-events-dlq");
        this.sessionService = sessionService;
        this.securityService = securityService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("session_events_dlq_processed_total")
                .description("Total session events DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("session_events_dlq_errors_total")
                .description("Total session events DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("session_events_dlq_duration")
                .description("Session events DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "session-events-dlq",
        groupId = "auth-service-session-events-dlq-group",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation-level=read_committed",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.max-poll-interval-ms=300000",
            "spring.kafka.consumer.session-timeout-ms=30000",
            "spring.kafka.consumer.heartbeat-interval-ms=10000"
        }
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class},
        traversingCauses = "true",
        retryTopicSuffix = "-retry",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "session-events-dlq", fallbackMethod = "handleSessionEventsDlqFallback")
    public void handleSessionEventsDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-Session-Id", required = false) String sessionId,
            @Header(value = "X-User-Id", required = false) String userId,
            @Header(value = "X-Event-Type", required = false) String eventType,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Session event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing session DLQ event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, sessionId={}, userId={}, eventType={}",
                     topic, partition, offset, record.key(), correlationId, sessionId, userId, eventType);

            String sessionData = record.value();
            validateSessionData(sessionData, eventId);

            // Process session DLQ with security validation
            SessionRecoveryResult result = sessionService.processSessionEventsDlq(
                sessionData,
                record.key(),
                correlationId,
                sessionId,
                userId,
                eventType,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Perform security checks
            performSecurityValidation(result, correlationId);

            // Handle recovery result
            if (result.isRecovered()) {
                handleSuccessfulRecovery(result, correlationId);
            } else if (result.isSecurityViolation()) {
                handleSecurityViolation(result, eventId, correlationId);
            } else {
                handleFailedRecovery(result, eventId, correlationId);
            }

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed session DLQ: eventId={}, sessionId={}, " +
                    "correlationId={}, recoveryStatus={}",
                    eventId, result.getSessionId(), correlationId, result.getRecoveryStatus());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in session DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();
        } catch (SecurityException e) {
            errorCounter.increment();
            log.error("Security violation in session DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleSecurityException(record, e, correlationId);
            throw e; // Security violations must be retried
        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error in session DLQ: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in session DLQ: eventId={}, correlationId={}",
                     eventId, correlationId, e);
            handleCriticalFailure(record, e, correlationId);
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(KafkaHeaders.ORIGINAL_OFFSET) long originalOffset,
            @Header(KafkaHeaders.ORIGINAL_PARTITION) int originalPartition) {

        String correlationId = generateCorrelationId();
        log.error("Session event sent to DLT - SECURITY CONCERN: topic={}, originalPartition={}, " +
                 "originalOffset={}, correlationId={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, exceptionMessage);

        // Execute security incident protocol
        executeSecurityIncidentProtocol(record, topic, exceptionMessage, correlationId);

        // Store for security audit
        storeForSecurityAudit(record, topic, exceptionMessage, correlationId);

        // Send security alert
        sendSecurityAlert(record, topic, exceptionMessage, correlationId);

        // Update DLT metrics
        Counter.builder("session_events_dlt_critical_events_total")
                .description("Critical session events sent to DLT")
                .tag("topic", topic)
                .tag("severity", "critical")
                .tag("security_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    public void handleSessionEventsDlqFallback(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String sessionId, String userId, String eventType,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for session DLQ: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        // Store in security-sensitive recovery queue
        storeInSecurityRecoveryQueue(record, correlationId);

        // Send CISO alert
        sendCisoAlert(correlationId, ex);

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("session_events_dlq_circuit_breaker_activations_total")
                .tag("severity", "critical")
                .tag("security_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    private void validateSessionData(String sessionData, String eventId) {
        if (sessionData == null || sessionData.trim().isEmpty()) {
            throw new ValidationException("Session data is null or empty for eventId: " + eventId);
        }

        if (!sessionData.contains("sessionId")) {
            throw new ValidationException("Session data missing sessionId for eventId: " + eventId);
        }

        if (!sessionData.contains("userId")) {
            throw new ValidationException("Session data missing userId for eventId: " + eventId);
        }

        if (!sessionData.contains("timestamp")) {
            throw new ValidationException("Session data missing timestamp for eventId: " + eventId);
        }

        // Validate security-sensitive fields
        validateSecurityFields(sessionData, eventId);
    }

    private void validateSecurityFields(String sessionData, String eventId) {
        if (!sessionData.contains("ipAddress")) {
            log.warn("Session data missing IP address for security audit: eventId={}", eventId);
        }

        if (!sessionData.contains("userAgent")) {
            log.warn("Session data missing user agent for security audit: eventId={}", eventId);
        }

        // Check for potentially malicious patterns
        if (sessionData.contains("script") || sessionData.contains("eval") || sessionData.contains("javascript:")) {
            throw new SecurityException("Potentially malicious content detected in session data: " + eventId);
        }
    }

    private void performSecurityValidation(SessionRecoveryResult result, String correlationId) {
        try {
            SecurityValidationResult securityCheck = securityService.validateSession(
                result.getSessionId(),
                result.getUserId(),
                result.getIpAddress(),
                result.getUserAgent(),
                correlationId
            );

            if (!securityCheck.isValid()) {
                throw new SecurityException("Security validation failed: " + securityCheck.getFailureReason());
            }

            if (securityCheck.requiresAdditionalVerification()) {
                log.warn("Session requires additional verification: sessionId={}, correlationId={}",
                        result.getSessionId(), correlationId);
                // Flag for additional verification without failing
                result.setRequiresAdditionalVerification(true);
            }

        } catch (Exception e) {
            log.error("Security validation error: sessionId={}, correlationId={}",
                     result.getSessionId(), correlationId, e);
            throw new SecurityException("Security validation failed: " + e.getMessage(), e);
        }
    }

    private void handleSuccessfulRecovery(SessionRecoveryResult result, String correlationId) {
        log.info("Session successfully recovered: sessionId={}, userId={}, correlationId={}",
                result.getSessionId(), result.getUserId(), correlationId);

        // Update session state
        sessionService.updateSessionState(
            result.getSessionId(),
            SessionState.ACTIVE,
            correlationId
        );

        // Update user activity
        userActivityService.recordActivity(
            result.getUserId(),
            ActivityType.SESSION_RECOVERED,
            correlationId
        );

        // Send recovery notification if required
        if (result.requiresNotification()) {
            notificationService.sendSessionRecoveryNotification(
                result.getUserId(),
                result.getSessionId(),
                correlationId
            );
        }
    }

    private void handleSecurityViolation(SessionRecoveryResult result, String eventId, String correlationId) {
        log.error("Security violation detected: sessionId={}, userId={}, violation={}, correlationId={}",
                result.getSessionId(), result.getUserId(), result.getViolationType(), correlationId);

        // Immediately revoke session
        sessionService.revokeSession(
            result.getSessionId(),
            RevocationReason.SECURITY_VIOLATION,
            correlationId
        );

        // Block user temporarily
        userSecurityService.temporaryBlock(
            result.getUserId(),
            BlockReason.SECURITY_VIOLATION,
            Duration.ofHours(1),
            correlationId
        );

        // Create security incident
        securityIncidentService.createIncident(
            IncidentType.SESSION_SECURITY_VIOLATION,
            result.getSessionId(),
            result.getUserId(),
            result.getViolationType(),
            eventId,
            correlationId,
            Severity.HIGH
        );

        // Send immediate security alert
        securityAlertService.sendCriticalAlert(
            SecurityAlertType.SESSION_VIOLATION,
            String.format("Security violation in session recovery: %s", result.getViolationType()),
            Map.of(
                "sessionId", result.getSessionId(),
                "userId", result.getUserId(),
                "violationType", result.getViolationType().toString(),
                "correlationId", correlationId,
                "action", "Session revoked, user temporarily blocked"
            )
        );
    }

    private void handleFailedRecovery(SessionRecoveryResult result, String eventId, String correlationId) {
        log.error("Session recovery failed: sessionId={}, userId={}, reason={}, correlationId={}",
                result.getSessionId(), result.getUserId(), result.getFailureReason(), correlationId);

        // Queue for manual security review
        securityReviewQueue.add(
            SecurityReviewItem.builder()
                .sessionId(result.getSessionId())
                .userId(result.getUserId())
                .failureReason(result.getFailureReason())
                .eventId(eventId)
                .correlationId(correlationId)
                .priority(Priority.HIGH)
                .reviewType(ReviewType.SESSION_RECOVERY_FAILURE)
                .assignedTo("SECURITY_TEAM")
                .build()
        );
    }

    private void handleSecurityException(ConsumerRecord<String, String> record,
                                        SecurityException e, String correlationId) {
        // Create security violation record
        securityViolationRepository.save(
            SecurityViolation.builder()
                .sessionId(extractSessionId(record.value()))
                .userId(extractUserId(record.value()))
                .violationType(ViolationType.SESSION_SECURITY)
                .description(e.getMessage())
                .correlationId(correlationId)
                .severity(Severity.HIGH)
                .timestamp(Instant.now())
                .requiresInvestigation(true)
                .source("session-events-dlq")
                .build()
        );

        // Send immediate security team alert
        securityTeamAlertService.sendCriticalAlert(
            SecurityAlertType.DLQ_SECURITY_VIOLATION,
            e.getMessage(),
            correlationId
        );
    }

    private void executeSecurityIncidentProtocol(ConsumerRecord<String, String> record,
                                                 String topic, String exceptionMessage,
                                                 String correlationId) {
        try {
            // Execute comprehensive security incident response
            SecurityIncidentResult incident = securityIncidentService.executeEmergencyProtocol(
                record.key(),
                record.value(),
                topic,
                exceptionMessage,
                correlationId
            );

            if (incident.requiresUserNotification()) {
                log.info("User notification required for security incident: correlationId={}, userId={}",
                        correlationId, incident.getAffectedUserId());

                // Send security incident notification
                securityNotificationService.sendSecurityIncidentNotification(
                    incident.getAffectedUserId(),
                    incident.getIncidentType(),
                    correlationId
                );
            }
        } catch (Exception e) {
            log.error("Security incident protocol failed: correlationId={}", correlationId, e);
        }
    }

    private void storeForSecurityAudit(ConsumerRecord<String, String> record, String topic,
                                       String exceptionMessage, String correlationId) {
        securityAuditRepository.save(
            SecurityAuditRecord.builder()
                .sourceTopic(topic)
                .sessionId(extractSessionId(record.value()))
                .userId(extractUserId(record.value()))
                .messageKey(record.key())
                .messageValue(record.value())
                .failureReason(exceptionMessage)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .status(AuditStatus.PENDING_INVESTIGATION)
                .securityLevel(SecurityLevel.HIGH)
                .requiresReporting(true)
                .build()
        );
    }

    private void sendSecurityAlert(ConsumerRecord<String, String> record, String topic,
                                   String exceptionMessage, String correlationId) {
        cisoAlertService.sendCriticalAlert(
            CisoAlertLevel.CRITICAL,
            "Session event permanently failed - potential security breach",
            Map.of(
                "topic", topic,
                "sessionId", extractSessionId(record.value()),
                "userId", extractUserId(record.value()),
                "error", exceptionMessage,
                "correlationId", correlationId,
                "securityImpact", "HIGH",
                "requiredAction", "Immediate CISO review required",
                "complianceRisk", "Data protection violation risk"
            )
        );
    }

    private boolean isAlreadyProcessed(String eventId) {
        Long processTime = processedEvents.get(eventId);
        if (processTime != null) {
            return System.currentTimeMillis() - processTime < Duration.ofHours(24).toMillis();
        }
        return false;
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
        if (processedEvents.size() > 10000) {
            cleanupOldProcessedEvents();
        }
    }

    private void cleanupOldProcessedEvents() {
        long cutoffTime = System.currentTimeMillis() - Duration.ofHours(24).toMillis();
        processedEvents.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }

    private String extractSessionId(String value) {
        try {
            return objectMapper.readTree(value).get("sessionId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractUserId(String value) {
        try {
            return objectMapper.readTree(value).get("userId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }
}