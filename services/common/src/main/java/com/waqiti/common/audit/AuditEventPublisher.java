package com.waqiti.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publisher for audit events to central audit service via Kafka.
 *
 * This component publishes security and compliance audit events to a Kafka topic
 * for centralized audit logging, monitoring, and compliance reporting.
 *
 * Features:
 * - Asynchronous non-blocking publishing
 * - Automatic retry on failure
 * - Local fallback logging if Kafka unavailable
 * - Thread-safe and production-ready
 * - Structured event metadata
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventPublisher {

    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;

    private static final String AUDIT_TOPIC = "audit-events";

    /**
     * Publishes an audit event asynchronously to Kafka.
     *
     * This method is fire-and-forget - it will log errors but won't block
     * the calling thread. If Kafka is unavailable, events are logged locally.
     *
     * @param eventType Type of audit event
     * @param level Severity level
     * @param message Human-readable description
     * @param metadata Additional event metadata
     */
    @Async
    public void publishEvent(String eventType, AuditLevel level, String message, Map<String, Object> metadata) {
        try {
            AuditEvent event = buildAuditEvent(eventType, level, message, metadata);

            CompletableFuture<SendResult<String, AuditEvent>> future =
                kafkaTemplate.send(AUDIT_TOPIC, event.getEventId(), event);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish audit event to Kafka: type={}, level={}, message={}",
                             eventType, level, message, ex);
                    // Fallback: log locally
                    logLocalAuditEvent(event);
                } else {
                    log.debug("Audit event published successfully: type={}, id={}",
                             eventType, event.getEventId());
                }
            });

        } catch (Exception e) {
            log.error("Error building audit event: type={}, message={}", eventType, message, e);
            // Fallback: log locally
            logLocalAuditEvent(eventType, level, message, metadata);
        }
    }

    /**
     * Synchronous publish for critical events that must be guaranteed.
     *
     * Use this only when you need to ensure the event is published before
     * proceeding (e.g., security violations, compliance events).
     *
     * @param eventType Type of audit event
     * @param level Severity level
     * @param message Human-readable description
     * @param metadata Additional event metadata
     */
    public void publishEventSync(String eventType, AuditLevel level, String message, Map<String, Object> metadata) {
        try {
            AuditEvent event = buildAuditEvent(eventType, level, message, metadata);

            SendResult<String, AuditEvent> result =
                kafkaTemplate.send(AUDIT_TOPIC, event.getEventId(), event).get();

            log.debug("Audit event published synchronously: type={}, id={}",
                     eventType, event.getEventId());

        } catch (Exception e) {
            log.error("Failed to publish audit event synchronously: type={}, message={}",
                     eventType, message, e);
            // Fallback: log locally
            logLocalAuditEvent(eventType, level, message, metadata);
        }
    }

    /**
     * Builds an AuditEvent from the provided parameters.
     */
    private AuditEvent buildAuditEvent(String eventType, AuditLevel level, String message, Map<String, Object> metadata) {
        return AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .action(eventType)
            .details(message)
            .metadata(metadata)
            .riskScore(mapLevelToRiskScore(level))
            .build();
    }

    /**
     * Maps AuditLevel to risk score (0-10 scale).
     */
    private Integer mapLevelToRiskScore(AuditLevel level) {
        if (level == null) return 0;

        return switch (level) {
            case LOW -> 2;
            case MEDIUM -> 5;
            case HIGH -> 8;
            case CRITICAL -> 10;
        };
    }

    /**
     * Fallback: logs audit event locally when Kafka is unavailable.
     */
    private void logLocalAuditEvent(AuditEvent event) {
        log.warn("AUDIT_EVENT_FALLBACK: eventId={}, type={}, timestamp={}, riskScore={}, message={}",
                 event.getEventId(),
                 event.getAction(),
                 event.getTimestamp(),
                 event.getRiskScore(),
                 event.getDetails());
    }

    /**
     * Fallback: logs audit event locally when Kafka is unavailable.
     */
    private void logLocalAuditEvent(String eventType, AuditLevel level, String message, Map<String, Object> metadata) {
        log.warn("AUDIT_EVENT_FALLBACK: type={}, level={}, message={}, metadata={}",
                 eventType, level, message, metadata);
    }
}
