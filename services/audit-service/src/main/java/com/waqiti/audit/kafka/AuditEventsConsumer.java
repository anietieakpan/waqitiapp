package com.waqiti.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.audit.domain.AuditEvent;
import com.waqiti.audit.domain.UserActivity;
import com.waqiti.audit.domain.SecurityEvent;
import com.waqiti.audit.repository.AuditEventRepository;
import com.waqiti.audit.repository.UserActivityRepository;
import com.waqiti.audit.repository.SecurityEventRepository;
import com.waqiti.audit.service.AuditService;
import com.waqiti.audit.service.ComprehensiveAuditService;
import com.waqiti.audit.service.SuspiciousActivityDetectionEngine;
import com.waqiti.audit.service.AuditAnalyticsEngine;
import com.waqiti.audit.service.AuditNotificationService;
import com.waqiti.common.audit.AuditService as CommonAuditService;
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
public class AuditEventsConsumer {

    private final AuditEventRepository auditEventRepository;
    private final UserActivityRepository userActivityRepository;
    private final SecurityEventRepository securityEventRepository;
    private final AuditService auditService;
    private final ComprehensiveAuditService comprehensiveAuditService;
    private final SuspiciousActivityDetectionEngine suspiciousActivityDetectionEngine;
    private final AuditAnalyticsEngine auditAnalyticsEngine;
    private final AuditNotificationService auditNotificationService;
    private final CommonAuditService commonAuditService;
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
    private Counter userActivityCounter;
    private Counter securityEventCounter;
    private Counter transactionEventCounter;
    private Counter systemEventCounter;
    private Counter suspiciousActivityCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("audit_events_processed_total")
            .description("Total number of successfully processed audit events")
            .register(meterRegistry);
        errorCounter = Counter.builder("audit_events_errors_total")
            .description("Total number of audit event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("audit_events_processing_duration")
            .description("Time taken to process audit events")
            .register(meterRegistry);
        userActivityCounter = Counter.builder("audit_user_activity_events_total")
            .description("Total number of user activity audit events")
            .register(meterRegistry);
        securityEventCounter = Counter.builder("audit_security_events_total")
            .description("Total number of security audit events")
            .register(meterRegistry);
        transactionEventCounter = Counter.builder("audit_transaction_events_total")
            .description("Total number of transaction audit events")
            .register(meterRegistry);
        systemEventCounter = Counter.builder("audit_system_events_total")
            .description("Total number of system audit events")
            .register(meterRegistry);
        suspiciousActivityCounter = Counter.builder("audit_suspicious_activity_detected_total")
            .description("Total number of suspicious activities detected")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"audit-events"},
        groupId = "audit-events-processor-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "audit-events", fallbackMethod = "handleAuditEventsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuditEventsEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("audit-event-%d-p%d-o%d", System.currentTimeMillis(), partition, offset);
        String eventKey = String.format("event-%d-%d-%d", partition, offset, System.currentTimeMillis());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing audit event: partition={}, offset={}, correlationId={}",
                partition, offset, correlationId);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);

            String eventType = (String) eventData.get("eventType");
            String serviceName = (String) eventData.get("serviceName");
            String userId = (String) eventData.get("userId");
            String sessionId = (String) eventData.get("sessionId");
            String transactionId = (String) eventData.get("transactionId");
            String resourceId = (String) eventData.get("resourceId");
            String resourceType = (String) eventData.get("resourceType");
            String action = (String) eventData.get("action");
            String description = (String) eventData.get("description");
            String result = (String) eventData.get("result");
            String severity = (String) eventData.get("severity");
            String ipAddress = (String) eventData.get("ipAddress");
            String userAgent = (String) eventData.get("userAgent");
            Map<String, Object> metadata = (Map<String, Object>) eventData.get("metadata");
            String beforeState = (String) eventData.get("beforeState");
            String afterState = (String) eventData.get("afterState");
            Long durationMs = eventData.get("durationMs") != null ?
                Long.valueOf(eventData.get("durationMs").toString()) : null;

            // Process audit event based on type
            processAuditEvent(eventData, eventType, serviceName, userId, sessionId, transactionId,
                resourceId, resourceType, action, description, result, severity, ipAddress,
                userAgent, metadata, beforeState, afterState, durationMs, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            commonAuditService.logAuditEvent("AUDIT_EVENT_PROCESSED", correlationId,
                Map.of("eventType", eventType, "serviceName", serviceName, "userId", userId != null ? userId : "",
                    "action", action, "result", result, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process audit event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("audit-events-fallback-events", Map.of(
                "originalMessage", message, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuditEventsEventFallback(
            String message,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("audit-event-fallback-%d-p%d-o%d",
            System.currentTimeMillis(), partition, offset);

        log.error("Circuit breaker fallback triggered for audit events: partition={}, offset={}, error={}",
            partition, offset, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("audit-events-dlq", Map.of(
            "originalMessage", message,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Audit Events Circuit Breaker Triggered",
                String.format("Audit events processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuditEventsEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-audit-event-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Audit event permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        commonAuditService.logAuditEvent("AUDIT_EVENT_DLT_EVENT", correlationId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Audit Event Dead Letter Event",
                String.format("Audit event sent to DLT: %s", exceptionMessage),
                Map.of("topic", topic, "correlationId", correlationId)
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

    private void processAuditEvent(Map<String, Object> eventData, String eventType, String serviceName,
            String userId, String sessionId, String transactionId, String resourceId, String resourceType,
            String action, String description, String result, String severity, String ipAddress,
            String userAgent, Map<String, Object> metadata, String beforeState, String afterState,
            Long durationMs, String correlationId) {

        // Create main audit event
        AuditEvent auditEvent = AuditEvent.builder()
            .eventType(eventType)
            .serviceName(serviceName)
            .userId(userId)
            .sessionId(sessionId)
            .transactionId(transactionId)
            .resourceId(resourceId)
            .resourceType(resourceType)
            .action(action)
            .description(description)
            .result(mapAuditResult(result))
            .severity(mapAuditSeverity(severity))
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .correlationId(correlationId)
            .metadata(convertToStringMap(metadata))
            .beforeState(beforeState)
            .afterState(afterState)
            .durationMs(durationMs)
            .complianceTags(generateComplianceTags(eventType, resourceType))
            .build();

        auditEventRepository.save(auditEvent);

        // Process based on event type
        switch (eventType.toUpperCase()) {
            case "USER_ACTION":
                processUserAction(auditEvent, correlationId);
                userActivityCounter.increment();
                break;
            case "SECURITY":
                processSecurityEvent(auditEvent, correlationId);
                securityEventCounter.increment();
                break;
            case "TRANSACTION":
                processTransactionEvent(auditEvent, correlationId);
                transactionEventCounter.increment();
                break;
            case "SYSTEM":
                processSystemEvent(auditEvent, correlationId);
                systemEventCounter.increment();
                break;
            case "DATA_ACCESS":
                processDataAccessEvent(auditEvent, correlationId);
                break;
            case "COMPLIANCE":
                processComplianceEvent(auditEvent, correlationId);
                break;
            default:
                processGenericEvent(auditEvent, correlationId);
        }

        // Run suspicious activity detection
        if (userId != null && shouldCheckForSuspiciousActivity(eventType, result)) {
            checkForSuspiciousActivity(auditEvent, correlationId);
        }

        // Run analytics
        auditAnalyticsEngine.processAuditEvent(auditEvent, correlationId);

        // Send downstream events
        kafkaTemplate.send("audit-event-processed", Map.of(
            "eventId", auditEvent.getId(),
            "eventType", eventType,
            "serviceName", serviceName,
            "userId", userId != null ? userId : "",
            "action", action,
            "result", result,
            "severity", severity,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Processed audit event: type={}, service={}, user={}, action={}, result={}, correlationId={}",
            eventType, serviceName, userId, action, result, correlationId);
    }

    private void processUserAction(AuditEvent auditEvent, String correlationId) {
        // Create user activity record
        UserActivity userActivity = UserActivity.builder()
            .userId(auditEvent.getUserId())
            .sessionId(auditEvent.getSessionId())
            .action(auditEvent.getAction())
            .resourceId(auditEvent.getResourceId())
            .resourceType(auditEvent.getResourceType())
            .ipAddress(auditEvent.getIpAddress())
            .userAgent(auditEvent.getUserAgent())
            .result(auditEvent.getResult().toString())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        userActivityRepository.save(userActivity);

        // Send to user behavior analytics
        kafkaTemplate.send("user-behavior-analytics", Map.of(
            "userId", auditEvent.getUserId(),
            "action", auditEvent.getAction(),
            "result", auditEvent.getResult().toString(),
            "ipAddress", auditEvent.getIpAddress() != null ? auditEvent.getIpAddress() : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.debug("Processed user action: userId={}, action={}, correlationId={}",
            auditEvent.getUserId(), auditEvent.getAction(), correlationId);
    }

    private void processSecurityEvent(AuditEvent auditEvent, String correlationId) {
        // Create security event record
        SecurityEvent securityEvent = SecurityEvent.builder()
            .eventType(auditEvent.getAction())
            .severity(auditEvent.getSeverity().toString())
            .userId(auditEvent.getUserId())
            .ipAddress(auditEvent.getIpAddress())
            .userAgent(auditEvent.getUserAgent())
            .description(auditEvent.getDescription())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        securityEventRepository.save(securityEvent);

        // Send to security monitoring
        kafkaTemplate.send("security-monitoring", Map.of(
            "securityEventId", securityEvent.getId(),
            "eventType", auditEvent.getAction(),
            "severity", auditEvent.getSeverity().toString(),
            "userId", auditEvent.getUserId() != null ? auditEvent.getUserId() : "",
            "ipAddress", auditEvent.getIpAddress() != null ? auditEvent.getIpAddress() : "",
            "description", auditEvent.getDescription() != null ? auditEvent.getDescription() : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Escalate high severity security events
        if (auditEvent.getSeverity() == AuditEvent.AuditSeverity.HIGH ||
            auditEvent.getSeverity() == AuditEvent.AuditSeverity.CRITICAL) {
            escalateSecurityEvent(securityEvent, correlationId);
        }

        log.info("Processed security event: type={}, severity={}, user={}, correlationId={}",
            auditEvent.getAction(), auditEvent.getSeverity(), auditEvent.getUserId(), correlationId);
    }

    private void processTransactionEvent(AuditEvent auditEvent, String correlationId) {
        // Send to transaction monitoring
        kafkaTemplate.send("transaction-monitoring", Map.of(
            "transactionId", auditEvent.getTransactionId(),
            "userId", auditEvent.getUserId() != null ? auditEvent.getUserId() : "",
            "action", auditEvent.getAction(),
            "result", auditEvent.getResult().toString(),
            "amount", extractAmountFromMetadata(auditEvent.getMetadata()),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check for transaction anomalies
        if (auditEvent.getResult() == AuditEvent.AuditResult.FAILURE ||
            auditEvent.getResult() == AuditEvent.AuditResult.UNAUTHORIZED) {
            checkTransactionAnomaly(auditEvent, correlationId);
        }

        log.debug("Processed transaction event: transactionId={}, action={}, result={}, correlationId={}",
            auditEvent.getTransactionId(), auditEvent.getAction(), auditEvent.getResult(), correlationId);
    }

    private void processSystemEvent(AuditEvent auditEvent, String correlationId) {
        // Send to system monitoring
        kafkaTemplate.send("system-monitoring", Map.of(
            "serviceName", auditEvent.getServiceName(),
            "action", auditEvent.getAction(),
            "result", auditEvent.getResult().toString(),
            "severity", auditEvent.getSeverity().toString(),
            "description", auditEvent.getDescription() != null ? auditEvent.getDescription() : "",
            "durationMs", auditEvent.getDurationMs() != null ? auditEvent.getDurationMs() : 0,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Alert on system failures
        if (auditEvent.getResult() == AuditEvent.AuditResult.SYSTEM_ERROR) {
            handleSystemError(auditEvent, correlationId);
        }

        log.debug("Processed system event: service={}, action={}, result={}, correlationId={}",
            auditEvent.getServiceName(), auditEvent.getAction(), auditEvent.getResult(), correlationId);
    }

    private void processDataAccessEvent(AuditEvent auditEvent, String correlationId) {
        // Send to data access monitoring
        kafkaTemplate.send("data-access-monitoring", Map.of(
            "userId", auditEvent.getUserId() != null ? auditEvent.getUserId() : "",
            "resourceId", auditEvent.getResourceId() != null ? auditEvent.getResourceId() : "",
            "resourceType", auditEvent.getResourceType() != null ? auditEvent.getResourceType() : "",
            "action", auditEvent.getAction(),
            "result", auditEvent.getResult().toString(),
            "ipAddress", auditEvent.getIpAddress() != null ? auditEvent.getIpAddress() : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.debug("Processed data access event: user={}, resource={}, action={}, correlationId={}",
            auditEvent.getUserId(), auditEvent.getResourceId(), auditEvent.getAction(), correlationId);
    }

    private void processComplianceEvent(AuditEvent auditEvent, String correlationId) {
        // Send to compliance monitoring
        kafkaTemplate.send("compliance-monitoring", Map.of(
            "eventType", auditEvent.getEventType(),
            "action", auditEvent.getAction(),
            "userId", auditEvent.getUserId() != null ? auditEvent.getUserId() : "",
            "resourceId", auditEvent.getResourceId() != null ? auditEvent.getResourceId() : "",
            "result", auditEvent.getResult().toString(),
            "complianceTags", auditEvent.getComplianceTags() != null ? auditEvent.getComplianceTags() : "",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Processed compliance event: action={}, result={}, tags={}, correlationId={}",
            auditEvent.getAction(), auditEvent.getResult(), auditEvent.getComplianceTags(), correlationId);
    }

    private void processGenericEvent(AuditEvent auditEvent, String correlationId) {
        log.debug("Processed generic audit event: type={}, action={}, correlationId={}",
            auditEvent.getEventType(), auditEvent.getAction(), correlationId);
    }

    private void checkForSuspiciousActivity(AuditEvent auditEvent, String correlationId) {
        try {
            boolean isSuspicious = suspiciousActivityDetectionEngine.analyzeBehavior(auditEvent);

            if (isSuspicious) {
                suspiciousActivityCounter.increment();
                handleSuspiciousActivity(auditEvent, correlationId);
            }
        } catch (Exception e) {
            log.error("Error during suspicious activity detection: correlationId={}, error={}",
                correlationId, e.getMessage());
        }
    }

    private void handleSuspiciousActivity(AuditEvent auditEvent, String correlationId) {
        log.warn("SUSPICIOUS ACTIVITY DETECTED: user={}, action={}, correlationId={}",
            auditEvent.getUserId(), auditEvent.getAction(), correlationId);

        // Send suspicious activity alert
        kafkaTemplate.send("suspicious-activity-alerts", Map.of(
            "userId", auditEvent.getUserId(),
            "action", auditEvent.getAction(),
            "eventType", auditEvent.getEventType(),
            "ipAddress", auditEvent.getIpAddress() != null ? auditEvent.getIpAddress() : "",
            "description", "Suspicious activity pattern detected",
            "severity", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send notification
        try {
            auditNotificationService.sendSuspiciousActivityAlert(auditEvent, correlationId);
        } catch (Exception e) {
            log.error("Failed to send suspicious activity notification: {}", e.getMessage());
        }
    }

    private void escalateSecurityEvent(SecurityEvent securityEvent, String correlationId) {
        kafkaTemplate.send("security-escalations", Map.of(
            "securityEventId", securityEvent.getId(),
            "eventType", securityEvent.getEventType(),
            "severity", securityEvent.getSeverity(),
            "userId", securityEvent.getUserId() != null ? securityEvent.getUserId() : "",
            "description", securityEvent.getDescription() != null ? securityEvent.getDescription() : "",
            "requiresImmediateAction", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void checkTransactionAnomaly(AuditEvent auditEvent, String correlationId) {
        kafkaTemplate.send("transaction-anomalies", Map.of(
            "transactionId", auditEvent.getTransactionId(),
            "userId", auditEvent.getUserId() != null ? auditEvent.getUserId() : "",
            "action", auditEvent.getAction(),
            "result", auditEvent.getResult().toString(),
            "anomalyType", "TRANSACTION_FAILURE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleSystemError(AuditEvent auditEvent, String correlationId) {
        kafkaTemplate.send("system-errors", Map.of(
            "serviceName", auditEvent.getServiceName(),
            "action", auditEvent.getAction(),
            "description", auditEvent.getDescription() != null ? auditEvent.getDescription() : "",
            "errorType", "SYSTEM_ERROR",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private boolean shouldCheckForSuspiciousActivity(String eventType, String result) {
        return "USER_ACTION".equals(eventType) || "SECURITY".equals(eventType) ||
               "FAILURE".equals(result) || "UNAUTHORIZED".equals(result);
    }

    private String extractAmountFromMetadata(Map<String, String> metadata) {
        if (metadata != null) {
            return metadata.getOrDefault("amount", "0");
        }
        return "0";
    }

    private String generateComplianceTags(String eventType, String resourceType) {
        List<String> tags = new ArrayList<>();

        if ("TRANSACTION".equals(eventType)) {
            tags.add("PCI_DSS");
            tags.add("FINANCIAL");
        }
        if ("SECURITY".equals(eventType)) {
            tags.add("SOX");
            tags.add("SECURITY");
        }
        if ("DATA_ACCESS".equals(eventType)) {
            tags.add("GDPR");
            tags.add("DATA_ACCESS");
        }
        if (resourceType != null && resourceType.contains("PERSONAL")) {
            tags.add("GDPR");
            tags.add("PII");
        }

        return String.join(",", tags);
    }

    private AuditEvent.AuditResult mapAuditResult(String result) {
        if (result == null) return AuditEvent.AuditResult.SUCCESS;

        return switch (result.toUpperCase()) {
            case "SUCCESS" -> AuditEvent.AuditResult.SUCCESS;
            case "FAILURE" -> AuditEvent.AuditResult.FAILURE;
            case "PARTIAL_SUCCESS" -> AuditEvent.AuditResult.PARTIAL_SUCCESS;
            case "UNAUTHORIZED" -> AuditEvent.AuditResult.UNAUTHORIZED;
            case "FORBIDDEN" -> AuditEvent.AuditResult.FORBIDDEN;
            case "NOT_FOUND" -> AuditEvent.AuditResult.NOT_FOUND;
            case "VALIDATION_ERROR" -> AuditEvent.AuditResult.VALIDATION_ERROR;
            case "SYSTEM_ERROR" -> AuditEvent.AuditResult.SYSTEM_ERROR;
            default -> AuditEvent.AuditResult.SUCCESS;
        };
    }

    private AuditEvent.AuditSeverity mapAuditSeverity(String severity) {
        if (severity == null) return AuditEvent.AuditSeverity.MEDIUM;

        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> AuditEvent.AuditSeverity.CRITICAL;
            case "HIGH" -> AuditEvent.AuditSeverity.HIGH;
            case "MEDIUM" -> AuditEvent.AuditSeverity.MEDIUM;
            case "LOW" -> AuditEvent.AuditSeverity.LOW;
            default -> AuditEvent.AuditSeverity.MEDIUM;
        };
    }

    private Map<String, String> convertToStringMap(Map<String, Object> objectMap) {
        if (objectMap == null) return new HashMap<>();

        Map<String, String> stringMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : objectMap.entrySet()) {
            stringMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
        }
        return stringMap;
    }
}