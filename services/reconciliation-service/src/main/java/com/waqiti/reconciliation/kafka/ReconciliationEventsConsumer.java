package com.waqiti.reconciliation.kafka;

import com.waqiti.common.events.ReconciliationEvent;
import com.waqiti.reconciliation.domain.Reconciliation;
import com.waqiti.reconciliation.domain.ReconciliationEntry;
import com.waqiti.reconciliation.repository.ReconciliationRepository;
import com.waqiti.reconciliation.repository.ReconciliationEntryRepository;
import com.waqiti.reconciliation.service.ReconciliationService;
import com.waqiti.reconciliation.service.MatchingService;
import com.waqiti.reconciliation.service.DiscrepancyAnalysisService;
import com.waqiti.reconciliation.service.ReportingService;
import com.waqiti.reconciliation.metrics.ReconciliationMetricsService;
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
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReconciliationEventsConsumer {

    private final ReconciliationRepository reconciliationRepository;
    private final ReconciliationEntryRepository entryRepository;
    private final ReconciliationService reconciliationService;
    private final MatchingService matchingService;
    private final DiscrepancyAnalysisService discrepancyAnalysisService;
    private final ReportingService reportingService;
    private final ReconciliationMetricsService metricsService;
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

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("reconciliation_events_processed_total")
            .description("Total number of successfully processed reconciliation events")
            .register(meterRegistry);
        errorCounter = Counter.builder("reconciliation_events_errors_total")
            .description("Total number of reconciliation events processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("reconciliation_events_processing_duration")
            .description("Time taken to process reconciliation events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"reconciliation-events", "reconciliation.events.stream", "general-reconciliation-events"},
        groupId = "reconciliation-events-service-group",
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
    @CircuitBreaker(name = "reconciliation-events", fallbackMethod = "handleReconciliationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleReconciliationEvent(
            @Payload ReconciliationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("rec-event-%s-p%d-o%d", event.getReconciliationId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getReconciliationId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing reconciliation event: reconciliationId={}, eventType={}, status={}",
                event.getReconciliationId(), event.getEventType(), event.getStatus());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case RECONCILIATION_INITIATED:
                    processReconciliationInitiated(event, correlationId);
                    break;

                case RECONCILIATION_IN_PROGRESS:
                    processReconciliationInProgress(event, correlationId);
                    break;

                case MATCHING_STARTED:
                    processMatchingStarted(event, correlationId);
                    break;

                case ENTRIES_MATCHED:
                    processEntriesMatched(event, correlationId);
                    break;

                case DISCREPANCY_DETECTED:
                    processDiscrepancyDetected(event, correlationId);
                    break;

                case RECONCILIATION_PAUSED:
                    processReconciliationPaused(event, correlationId);
                    break;

                case RECONCILIATION_RESUMED:
                    processReconciliationResumed(event, correlationId);
                    break;

                case RECONCILIATION_CANCELLED:
                    processReconciliationCancelled(event, correlationId);
                    break;

                case RECONCILIATION_COMPLETED:
                    processReconciliationCompleted(event, correlationId);
                    break;

                case REPORT_GENERATED:
                    processReportGenerated(event, correlationId);
                    break;

                default:
                    log.warn("Unknown reconciliation event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logReconciliationEvent("RECONCILIATION_EVENT_PROCESSED", event.getReconciliationId(),
                Map.of("eventType", event.getEventType(), "status", event.getStatus(),
                    "source", event.getSource(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process reconciliation event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("reconciliation-events-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleReconciliationEventFallback(
            ReconciliationEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("rec-event-fallback-%s-p%d-o%d", event.getReconciliationId(), partition, offset);

        log.error("Circuit breaker fallback triggered for reconciliation event: reconciliationId={}, error={}",
            event.getReconciliationId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("reconciliation-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Reconciliation Events Circuit Breaker Triggered",
                String.format("Reconciliation %s event processing failed: %s", event.getReconciliationId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltReconciliationEvent(
            @Payload ReconciliationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-rec-event-%s-%d", event.getReconciliationId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Reconciliation event permanently failed: reconciliationId={}, topic={}, error={}",
            event.getReconciliationId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logReconciliationEvent("RECONCILIATION_EVENT_DLT", event.getReconciliationId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Reconciliation Event Dead Letter",
                String.format("Reconciliation %s event sent to DLT: %s", event.getReconciliationId(), exceptionMessage),
                Map.of("reconciliationId", event.getReconciliationId(), "topic", topic, "correlationId", correlationId)
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
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processReconciliationInitiated(ReconciliationEvent event, String correlationId) {
        Reconciliation reconciliation = Reconciliation.builder()
            .reconciliationId(event.getReconciliationId())
            .source(event.getSource())
            .target(event.getTarget())
            .status("INITIATED")
            .reconciliationDate(event.getReconciliationDate())
            .initiatedAt(LocalDateTime.now())
            .initiatedBy(event.getInitiatedBy())
            .correlationId(correlationId)
            .build();
        reconciliationRepository.save(reconciliation);

        reconciliationService.setupReconciliationJob(event.getReconciliationId());

        kafkaTemplate.send("reconciliation-workflow", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "eventType", "RECONCILIATION_SETUP_COMPLETE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReconciliationInitiated(event.getSource(), event.getTarget());

        log.info("Reconciliation initiated: reconciliationId={}, source={}, target={}",
            event.getReconciliationId(), event.getSource(), event.getTarget());
    }

    private void processReconciliationInProgress(ReconciliationEvent event, String correlationId) {
        Reconciliation reconciliation = reconciliationRepository.findByReconciliationId(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus("IN_PROGRESS");
        reconciliation.setStartedAt(LocalDateTime.now());
        reconciliation.setTotalEntries(event.getTotalEntries());
        reconciliationRepository.save(reconciliation);

        metricsService.recordReconciliationStarted();

        log.info("Reconciliation in progress: reconciliationId={}, totalEntries={}",
            event.getReconciliationId(), event.getTotalEntries());
    }

    private void processMatchingStarted(ReconciliationEvent event, String correlationId) {
        Reconciliation reconciliation = reconciliationRepository.findByReconciliationId(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setMatchingStartedAt(LocalDateTime.now());
        reconciliationRepository.save(reconciliation);

        matchingService.initializeMatching(event.getReconciliationId(), event.getMatchingCriteria());

        metricsService.recordMatchingStarted();

        log.info("Matching started: reconciliationId={}, criteria={}",
            event.getReconciliationId(), event.getMatchingCriteria());
    }

    private void processEntriesMatched(ReconciliationEvent event, String correlationId) {
        Reconciliation reconciliation = reconciliationRepository.findByReconciliationId(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setMatchedEntries(event.getMatchedCount());
        reconciliation.setUnmatchedEntries(event.getUnmatchedCount());
        reconciliationRepository.save(reconciliation);

        // Process matched entries
        for (String entryId : event.getMatchedEntryIds()) {
            ReconciliationEntry entry = entryRepository.findById(entryId).orElse(null);
            if (entry != null) {
                entry.setMatchStatus("MATCHED");
                entry.setMatchedAt(LocalDateTime.now());
                entryRepository.save(entry);
            }
        }

        metricsService.recordEntriesMatched(event.getMatchedCount(), event.getUnmatchedCount());

        log.info("Entries matched: reconciliationId={}, matched={}, unmatched={}",
            event.getReconciliationId(), event.getMatchedCount(), event.getUnmatchedCount());
    }

    private void processDiscrepancyDetected(ReconciliationEvent event, String correlationId) {
        Reconciliation reconciliation = reconciliationRepository.findByReconciliationId(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setDiscrepancyCount(event.getDiscrepancyCount());
        reconciliation.setDiscrepancyAmount(event.getDiscrepancyAmount());
        reconciliation.setDiscrepanciesDetectedAt(LocalDateTime.now());
        reconciliationRepository.save(reconciliation);

        discrepancyAnalysisService.analyzeDiscrepancies(event.getReconciliationId(), event.getDiscrepancies());

        kafkaTemplate.send("reconciliation-discrepancy-events", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "eventType", "DISCREPANCY_ANALYSIS_INITIATED",
            "discrepancyCount", event.getDiscrepancyCount(),
            "discrepancyAmount", event.getDiscrepancyAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send alert if discrepancies exceed threshold
        if (event.getDiscrepancyAmount().compareTo(new BigDecimal("1000.00")) > 0) {
            notificationService.sendOperationalAlert(
                "High Value Discrepancies Detected",
                String.format("Reconciliation %s has discrepancies totaling %s",
                    event.getReconciliationId(), event.getDiscrepancyAmount()),
                "HIGH"
            );
        }

        metricsService.recordDiscrepancyDetected(event.getDiscrepancyCount(), event.getDiscrepancyAmount());

        log.warn("Discrepancies detected: reconciliationId={}, count={}, amount={}",
            event.getReconciliationId(), event.getDiscrepancyCount(), event.getDiscrepancyAmount());
    }

    private void processReconciliationPaused(ReconciliationEvent event, String correlationId) {
        Reconciliation reconciliation = reconciliationRepository.findByReconciliationId(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus("PAUSED");
        reconciliation.setPausedAt(LocalDateTime.now());
        reconciliation.setPauseReason(event.getPauseReason());
        reconciliationRepository.save(reconciliation);

        reconciliationService.pauseReconciliation(event.getReconciliationId());

        notificationService.sendNotification("RECONCILIATION_TEAM", "Reconciliation Paused",
            String.format("Reconciliation %s paused: %s", event.getReconciliationId(), event.getPauseReason()),
            correlationId);

        metricsService.recordReconciliationPaused();

        log.info("Reconciliation paused: reconciliationId={}, reason={}",
            event.getReconciliationId(), event.getPauseReason());
    }

    private void processReconciliationResumed(ReconciliationEvent event, String correlationId) {
        Reconciliation reconciliation = reconciliationRepository.findByReconciliationId(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus("IN_PROGRESS");
        reconciliation.setResumedAt(LocalDateTime.now());
        reconciliation.setResumedBy(event.getResumedBy());
        reconciliationRepository.save(reconciliation);

        reconciliationService.resumeReconciliation(event.getReconciliationId());

        metricsService.recordReconciliationResumed();

        log.info("Reconciliation resumed: reconciliationId={}, resumedBy={}",
            event.getReconciliationId(), event.getResumedBy());
    }

    private void processReconciliationCancelled(ReconciliationEvent event, String correlationId) {
        Reconciliation reconciliation = reconciliationRepository.findByReconciliationId(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus("CANCELLED");
        reconciliation.setCancelledAt(LocalDateTime.now());
        reconciliation.setCancelledBy(event.getCancelledBy());
        reconciliation.setCancellationReason(event.getCancellationReason());
        reconciliationRepository.save(reconciliation);

        reconciliationService.cleanupCancelledReconciliation(event.getReconciliationId());

        notificationService.sendNotification("FINANCE_MANAGER", "Reconciliation Cancelled",
            String.format("Reconciliation %s cancelled by %s: %s",
                event.getReconciliationId(), event.getCancelledBy(), event.getCancellationReason()),
            correlationId);

        metricsService.recordReconciliationCancelled();

        log.info("Reconciliation cancelled: reconciliationId={}, cancelledBy={}, reason={}",
            event.getReconciliationId(), event.getCancelledBy(), event.getCancellationReason());
    }

    private void processReconciliationCompleted(ReconciliationEvent event, String correlationId) {
        Reconciliation reconciliation = reconciliationRepository.findByReconciliationId(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus("COMPLETED");
        reconciliation.setCompletedAt(LocalDateTime.now());
        reconciliation.setFinalMatchedCount(event.getFinalMatchedCount());
        reconciliation.setFinalUnmatchedCount(event.getFinalUnmatchedCount());
        reconciliation.setFinalDiscrepancyAmount(event.getFinalDiscrepancyAmount());
        reconciliationRepository.save(reconciliation);

        kafkaTemplate.send("reconciliation-completed-events", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "status", "COMPLETED",
            "finalMatchedCount", event.getFinalMatchedCount(),
            "finalUnmatchedCount", event.getFinalUnmatchedCount(),
            "finalDiscrepancyAmount", event.getFinalDiscrepancyAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        reportingService.scheduleReportGeneration(event.getReconciliationId());

        metricsService.recordReconciliationCompleted(
            event.getFinalMatchedCount(),
            event.getFinalUnmatchedCount(),
            event.getFinalDiscrepancyAmount()
        );

        log.info("Reconciliation completed: reconciliationId={}, matched={}, unmatched={}, discrepancy={}",
            event.getReconciliationId(), event.getFinalMatchedCount(),
            event.getFinalUnmatchedCount(), event.getFinalDiscrepancyAmount());
    }

    private void processReportGenerated(ReconciliationEvent event, String correlationId) {
        Reconciliation reconciliation = reconciliationRepository.findByReconciliationId(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setReportGeneratedAt(LocalDateTime.now());
        reconciliation.setReportPath(event.getReportPath());
        reconciliationRepository.save(reconciliation);

        notificationService.sendNotification("FINANCE_TEAM", "Reconciliation Report Generated",
            String.format("Report for reconciliation %s is available at %s",
                event.getReconciliationId(), event.getReportPath()),
            correlationId);

        metricsService.recordReportGenerated();

        log.info("Reconciliation report generated: reconciliationId={}, reportPath={}",
            event.getReconciliationId(), event.getReportPath());
    }
}