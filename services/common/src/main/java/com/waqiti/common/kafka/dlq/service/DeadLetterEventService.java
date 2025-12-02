package com.waqiti.common.kafka.dlq.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.entity.DeadLetterEvent;
import com.waqiti.common.kafka.dlq.entity.DeadLetterEvent.DLQSeverity;
import com.waqiti.common.kafka.dlq.entity.DeadLetterEvent.DLQStatus;
import com.waqiti.common.kafka.dlq.repository.DeadLetterEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dead Letter Event Service
 *
 * Centralized service for managing failed Kafka events (DLQ/DLT).
 *
 * Features:
 * - Store failed events with full context
 * - Automatic retry with exponential backoff
 * - Manual replay capability
 * - Event expiration and cleanup
 * - Comprehensive metrics and alerting
 * - Severity-based categorization
 *
 * Retry Strategy:
 * - Attempt 1: Immediate (0 seconds)
 * - Attempt 2: After 1 minute
 * - Attempt 3: After 5 minutes
 * - Attempt 4: After 15 minutes
 * - Max Attempts: 3 by default
 *
 * Cleanup Policy:
 * - Expired events: Deleted immediately
 * - Resolved events: Retained for 90 days
 * - Failed events: Retained for 180 days
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-11
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeadLetterEventService {

    private final DeadLetterEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter dlqEventsCreated;
    private Counter dlqEventsRetried;
    private Counter dlqEventsResolved;
    private Counter dlqEventsFailed;
    private Counter dlqEventsExpired;

    // Retry delays (in minutes)
    private static final int[] RETRY_DELAYS = {1, 5, 15, 30, 60};
    private static final Duration DEFAULT_EXPIRATION = Duration.ofDays(180); // 6 months

    @PostConstruct
    public void initMetrics() {
        dlqEventsCreated = Counter.builder("dlq.events.created")
            .description("Total DLQ events created")
            .register(meterRegistry);

        dlqEventsRetried = Counter.builder("dlq.events.retried")
            .description("Total DLQ events retried")
            .register(meterRegistry);

        dlqEventsResolved = Counter.builder("dlq.events.resolved")
            .description("Total DLQ events successfully resolved")
            .register(meterRegistry);

        dlqEventsFailed = Counter.builder("dlq.events.failed")
            .description("Total DLQ events permanently failed")
            .register(meterRegistry);

        dlqEventsExpired = Counter.builder("dlq.events.expired")
            .description("Total DLQ events expired and deleted")
            .register(meterRegistry);
    }

    /**
     * Store a failed event in DLQ
     *
     * @param eventId Original event ID
     * @param eventType Event class name
     * @param serviceName Service that failed
     * @param consumerClass Consumer class name
     * @param topic Kafka topic
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param payload Event payload (JSON)
     * @param failureReason Exception message
     * @param throwable Original exception
     * @param severity Severity level
     * @return Stored DeadLetterEvent
     */
    @Transactional
    public DeadLetterEvent storeDLTEvent(
            String eventId,
            String eventType,
            String serviceName,
            String consumerClass,
            String topic,
            Integer partition,
            Long offset,
            String payload,
            String failureReason,
            Throwable throwable,
            DLQSeverity severity) {

        // Check for duplicates
        Optional<DeadLetterEvent> existing = repository.findByEventId(eventId);
        if (existing.isPresent()) {
            log.warn("DLQ event already exists for eventId: {}. Incrementing retry count.", eventId);
            DeadLetterEvent event = existing.get();
            event.incrementRetryCount();
            event.setFailureReason(failureReason);
            if (throwable != null) {
                event.setStackTrace(getStackTrace(throwable));
            }
            return repository.save(event);
        }

        // Create new DLQ event
        DeadLetterEvent event = DeadLetterEvent.builder()
            .eventId(eventId)
            .eventType(eventType)
            .serviceName(serviceName)
            .consumerClass(consumerClass)
            .topic(topic)
            .partition(partition)
            .offset(offset)
            .payload(payload)
            .failureReason(truncate(failureReason, 2000))
            .stackTrace(throwable != null ? truncate(getStackTrace(throwable), 10000) : null)
            .retryCount(0)
            .maxRetries(3)
            .status(DLQStatus.NEW)
            .severity(severity)
            .nextRetryAt(LocalDateTime.now().plusMinutes(RETRY_DELAYS[0]))
            .expiresAt(LocalDateTime.now().plus(DEFAULT_EXPIRATION))
            .build();

        event = repository.save(event);
        dlqEventsCreated.increment();

        log.error("📥 DLQ: Stored failed event | eventId={}, type={}, service={}, severity={}, reason={}",
            eventId, eventType, serviceName, severity, truncate(failureReason, 100));

        // Alert on critical events
        if (severity == DLQSeverity.CRITICAL) {
            sendCriticalAlert(event);
        }

        return event;
    }

    /**
     * Simplified store method for quick usage
     */
    @Transactional
    public DeadLetterEvent storeDLTEvent(
            String eventId,
            String eventType,
            String serviceName,
            String payload,
            String failureReason,
            Throwable throwable) {

        return storeDLTEvent(
            eventId,
            eventType,
            serviceName,
            null,
            "unknown-topic",
            null,
            null,
            payload,
            failureReason,
            throwable,
            DLQSeverity.MEDIUM
        );
    }

    /**
     * Retry a specific DLQ event
     *
     * @param eventId Event ID to retry
     * @return true if retry succeeded
     */
    @Transactional
    public boolean retryEvent(String eventId) {
        Optional<DeadLetterEvent> optional = repository.findByEventId(eventId);
        if (optional.isEmpty()) {
            log.warn("DLQ event not found for retry: {}", eventId);
            return false;
        }

        DeadLetterEvent event = optional.get();

        if (!event.canRetry()) {
            log.warn("DLQ event cannot be retried: eventId={}, status={}, retryCount={}/{}",
                eventId, event.getStatus(), event.getRetryCount(), event.getMaxRetries());
            return false;
        }

        try {
            // Update status
            event.setStatus(DLQStatus.RETRYING);
            event.incrementRetryCount();

            // Calculate next retry time with exponential backoff
            if (event.getRetryCount() < RETRY_DELAYS.length) {
                int delayMinutes = RETRY_DELAYS[event.getRetryCount()];
                event.setNextRetryAt(LocalDateTime.now().plusMinutes(delayMinutes));
            } else {
                // Max retries exceeded
                event.setStatus(DLQStatus.FAILED);
                event.markFailed("Maximum retry attempts exceeded");
                dlqEventsFailed.increment();
                log.error("🔴 DLQ: Event permanently failed after {} retries: eventId={}",
                    event.getRetryCount(), eventId);
                repository.save(event);
                return false;
            }

            repository.save(event);
            dlqEventsRetried.increment();

            // Republish to original topic
            republishEvent(event);

            log.info("🔄 DLQ: Event retried (attempt {}/{}): eventId={}",
                event.getRetryCount(), event.getMaxRetries(), eventId);

            return true;

        } catch (Exception e) {
            log.error("Failed to retry DLQ event: eventId={}", eventId, e);
            event.setFailureReason("Retry failed: " + e.getMessage());
            repository.save(event);
            return false;
        }
    }

    /**
     * Automatically retry eligible events
     * Scheduled every 5 minutes
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    @Transactional
    public void autoRetryEvents() {
        List<DeadLetterEvent> eligibleEvents = repository.findEventsEligibleForRetry(LocalDateTime.now());

        if (eligibleEvents.isEmpty()) {
            return;
        }

        log.info("🔄 DLQ: Auto-retry processing {} eligible events", eligibleEvents.size());

        int successCount = 0;
        int failureCount = 0;

        for (DeadLetterEvent event : eligibleEvents) {
            boolean success = retryEvent(event.getEventId());
            if (success) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        log.info("🔄 DLQ: Auto-retry completed - success: {}, failed: {}", successCount, failureCount);
    }

    /**
     * Clean up expired events
     * Scheduled daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredEvents() {
        LocalDateTime now = LocalDateTime.now();

        // Delete expired events
        int expiredCount = repository.deleteExpiredEvents(now);
        if (expiredCount > 0) {
            dlqEventsExpired.increment(expiredCount);
            log.info("🗑️ DLQ: Deleted {} expired events", expiredCount);
        }

        // Delete old resolved events (90 days retention)
        LocalDateTime resolvedRetention = now.minusDays(90);
        int resolvedCount = repository.deleteOldResolvedEvents(resolvedRetention);
        if (resolvedCount > 0) {
            log.info("🗑️ DLQ: Deleted {} old resolved events (>90 days)", resolvedCount);
        }
    }

    /**
     * Manually resolve a DLQ event
     *
     * @param eventId Event ID
     * @param resolvedBy User ID or system name
     * @param notes Resolution notes
     */
    @Transactional
    public void resolveEvent(String eventId, String resolvedBy, String notes) {
        Optional<DeadLetterEvent> optional = repository.findByEventId(eventId);
        if (optional.isEmpty()) {
            throw new IllegalArgumentException("DLQ event not found: " + eventId);
        }

        DeadLetterEvent event = optional.get();
        event.markResolved(resolvedBy, notes);
        repository.save(event);
        dlqEventsResolved.increment();

        log.info("✅ DLQ: Event manually resolved | eventId={}, resolvedBy={}, notes={}",
            eventId, resolvedBy, truncate(notes, 100));
    }

    /**
     * Skip a DLQ event (won't retry)
     *
     * @param eventId Event ID
     * @param reason Reason for skipping
     */
    @Transactional
    public void skipEvent(String eventId, String reason) {
        Optional<DeadLetterEvent> optional = repository.findByEventId(eventId);
        if (optional.isEmpty()) {
            throw new IllegalArgumentException("DLQ event not found: " + eventId);
        }

        DeadLetterEvent event = optional.get();
        event.markSkipped(reason);
        repository.save(event);

        log.info("⏭️ DLQ: Event skipped | eventId={}, reason={}", eventId, truncate(reason, 100));
    }

    /**
     * Get DLQ event by ID
     */
    public Optional<DeadLetterEvent> getEvent(String eventId) {
        return repository.findByEventId(eventId);
    }

    /**
     * Get events by status
     */
    public Page<DeadLetterEvent> getEventsByStatus(DLQStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable);
    }

    /**
     * Get events by service
     */
    public Page<DeadLetterEvent> getEventsByService(String serviceName, Pageable pageable) {
        return repository.findByServiceName(serviceName, pageable);
    }

    /**
     * Get DLQ statistics
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
            "new", repository.countByStatus(DLQStatus.NEW),
            "retrying", repository.countByStatus(DLQStatus.RETRYING),
            "resolved", repository.countByStatus(DLQStatus.RESOLVED),
            "failed", repository.countByStatus(DLQStatus.FAILED),
            "critical", repository.countBySeverity(DLQSeverity.CRITICAL),
            "high", repository.countBySeverity(DLQSeverity.HIGH)
        );
    }

    // Private helper methods

    private void republishEvent(DeadLetterEvent event) {
        try {
            // Parse payload back to object
            Object eventObject = objectMapper.readValue(event.getPayload(), Object.class);

            // Republish to original topic
            kafkaTemplate.send(event.getTopic(), event.getEventId(), eventObject);

            log.info("📤 DLQ: Event republished to topic: {} | eventId={}", event.getTopic(), event.getEventId());

        } catch (Exception e) {
            log.error("Failed to republish DLQ event: eventId={}", event.getEventId(), e);
            throw new RuntimeException("Failed to republish event", e);
        }
    }

    private void sendCriticalAlert(DeadLetterEvent event) {
        // Send to alerting channel (PagerDuty, Slack, etc.)
        log.error("🚨 CRITICAL DLQ EVENT | eventId={}, service={}, type={}, reason={}",
            event.getEventId(),
            event.getServiceName(),
            event.getEventType(),
            truncate(event.getFailureReason(), 200));

        // TODO: Integrate with AlertingService for PagerDuty/Slack notifications
        // alertingService.sendCriticalAlert("DLQ Critical Event", event.toString());
    }

    private String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        // PCI DSS FIX: Build stack trace without printStackTrace()
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getName()).append(": ").append(throwable.getMessage()).append("\n");

        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        Throwable cause = throwable.getCause();
        if (cause != null) {
            sb.append("Caused by: ").append(cause.getClass().getName())
              .append(": ").append(cause.getMessage()).append("\n");
        }

        return sb.toString();
    }

    private String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        return str.length() <= maxLength ? str : str.substring(0, maxLength) + "... (truncated)";
    }
}
