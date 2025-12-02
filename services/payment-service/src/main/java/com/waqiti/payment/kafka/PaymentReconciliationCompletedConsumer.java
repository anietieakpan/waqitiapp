package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentReconciliationCompletedEvent;
import com.waqiti.payment.domain.PaymentReconciliation;
import com.waqiti.payment.repository.PaymentReconciliationRepository;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.ReconciliationService;
import com.waqiti.payment.service.SettlementService;
import com.waqiti.payment.service.DiscrepancyResolutionService;
import com.waqiti.payment.metrics.PaymentMetricsService;
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
public class PaymentReconciliationCompletedConsumer {

    private final PaymentReconciliationRepository reconciliationRepository;
    private final PaymentRepository paymentRepository;
    private final ReconciliationService reconciliationService;
    private final SettlementService settlementService;
    private final DiscrepancyResolutionService discrepancyService;
    private final PaymentMetricsService metricsService;
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
        successCounter = Counter.builder("payment_reconciliation_completed_processed_total")
            .description("Total number of successfully processed payment reconciliation completed events")
            .register(meterRegistry);
        errorCounter = Counter.builder("payment_reconciliation_completed_errors_total")
            .description("Total number of payment reconciliation completed processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("payment_reconciliation_completed_processing_duration")
            .description("Time taken to process payment reconciliation completed events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"payment.reconciliation.completed", "payment-reconciliation-completed", "reconciliation-completed-events"},
        groupId = "payment-reconciliation-completed-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "payment-reconciliation-completed", fallbackMethod = "handlePaymentReconciliationCompletedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handlePaymentReconciliationCompletedEvent(
            @Payload PaymentReconciliationCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("rec-completed-%s-p%d-o%d", event.getReconciliationId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getReconciliationId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing payment reconciliation completed: reconciliationId={}, status={}, amount={}",
                event.getReconciliationId(), event.getStatus(), event.getTotalAmount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case RECONCILIATION_MATCHED:
                    processMatchedReconciliation(event, correlationId);
                    break;

                case RECONCILIATION_DISCREPANCY_RESOLVED:
                    processDiscrepancyResolved(event, correlationId);
                    break;

                case RECONCILIATION_AUTO_BALANCED:
                    processAutoBalanced(event, correlationId);
                    break;

                case RECONCILIATION_MANUAL_ADJUSTMENT:
                    processManualAdjustment(event, correlationId);
                    break;

                case RECONCILIATION_VERIFIED:
                    processReconciliationVerified(event, correlationId);
                    break;

                case RECONCILIATION_FINALIZED:
                    finalizeReconciliation(event, correlationId);
                    break;

                case RECONCILIATION_ARCHIVED:
                    archiveReconciliation(event, correlationId);
                    break;

                default:
                    log.warn("Unknown payment reconciliation completed event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("PAYMENT_RECONCILIATION_COMPLETED_EVENT_PROCESSED", event.getReconciliationId(),
                Map.of("eventType", event.getEventType(), "status", event.getStatus(),
                    "totalAmount", event.getTotalAmount(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process payment reconciliation completed event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("payment-reconciliation-completed-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handlePaymentReconciliationCompletedEventFallback(
            PaymentReconciliationCompletedEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("rec-completed-fallback-%s-p%d-o%d", event.getReconciliationId(), partition, offset);

        log.error("Circuit breaker fallback triggered for payment reconciliation completed: reconciliationId={}, error={}",
            event.getReconciliationId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("payment-reconciliation-completed-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Payment Reconciliation Completed Circuit Breaker Triggered",
                String.format("Reconciliation %s completion failed: %s", event.getReconciliationId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltPaymentReconciliationCompletedEvent(
            @Payload PaymentReconciliationCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-rec-completed-%s-%d", event.getReconciliationId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Payment reconciliation completed permanently failed: reconciliationId={}, topic={}, error={}",
            event.getReconciliationId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("PAYMENT_RECONCILIATION_COMPLETED_DLT_EVENT", event.getReconciliationId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Payment Reconciliation Completed Dead Letter Event",
                String.format("Reconciliation %s completion sent to DLT: %s", event.getReconciliationId(), exceptionMessage),
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

    private void processMatchedReconciliation(PaymentReconciliationCompletedEvent event, String correlationId) {
        PaymentReconciliation reconciliation = reconciliationRepository.findById(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus("MATCHED");
        reconciliation.setMatchedAt(LocalDateTime.now());
        reconciliation.setTotalAmount(event.getTotalAmount());
        reconciliation.setDiscrepancyAmount(BigDecimal.ZERO);
        reconciliation.setMatchedTransactions(event.getMatchedTransactionCount());
        reconciliationRepository.save(reconciliation);

        reconciliationService.updatePaymentStatuses(event.getReconciliationId(), "RECONCILED");

        kafkaTemplate.send("payment-settlement-events", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "eventType", "READY_FOR_SETTLEMENT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReconciliationMatched(event.getTotalAmount());

        log.info("Reconciliation matched successfully: reconciliationId={}, amount={}",
            event.getReconciliationId(), event.getTotalAmount());
    }

    private void processDiscrepancyResolved(PaymentReconciliationCompletedEvent event, String correlationId) {
        PaymentReconciliation reconciliation = reconciliationRepository.findById(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus("DISCREPANCY_RESOLVED");
        reconciliation.setDiscrepancyResolvedAt(LocalDateTime.now());
        reconciliation.setResolutionMethod(event.getResolutionMethod());
        reconciliation.setResolutionNotes(event.getResolutionNotes());
        reconciliationRepository.save(reconciliation);

        discrepancyService.closeDiscrepancyCase(event.getReconciliationId());

        notificationService.sendNotification("FINANCE_TEAM", "Discrepancy Resolved",
            String.format("Reconciliation %s discrepancy resolved via %s",
                event.getReconciliationId(), event.getResolutionMethod()),
            correlationId);

        metricsService.recordDiscrepancyResolved(event.getTotalAmount());

        log.info("Reconciliation discrepancy resolved: reconciliationId={}, method={}",
            event.getReconciliationId(), event.getResolutionMethod());
    }

    private void processAutoBalanced(PaymentReconciliationCompletedEvent event, String correlationId) {
        PaymentReconciliation reconciliation = reconciliationRepository.findById(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus("AUTO_BALANCED");
        reconciliation.setAutoBalancedAt(LocalDateTime.now());
        reconciliation.setBalancingAdjustment(event.getBalancingAdjustment());
        reconciliationRepository.save(reconciliation);

        reconciliationService.applyAutoBalancingEntries(event.getReconciliationId(), event.getBalancingAdjustment());

        kafkaTemplate.send("accounting-events", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "eventType", "AUTO_BALANCING_ENTRY",
            "amount", event.getBalancingAdjustment(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordAutoBalancing(event.getBalancingAdjustment());

        log.info("Reconciliation auto-balanced: reconciliationId={}, adjustment={}",
            event.getReconciliationId(), event.getBalancingAdjustment());
    }

    private void processManualAdjustment(PaymentReconciliationCompletedEvent event, String correlationId) {
        PaymentReconciliation reconciliation = reconciliationRepository.findById(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus("MANUALLY_ADJUSTED");
        reconciliation.setManualAdjustmentAt(LocalDateTime.now());
        reconciliation.setAdjustedBy(event.getAdjustedBy());
        reconciliation.setManualAdjustment(event.getManualAdjustment());
        reconciliation.setAdjustmentReason(event.getAdjustmentReason());
        reconciliationRepository.save(reconciliation);

        auditService.logPaymentEvent("MANUAL_RECONCILIATION_ADJUSTMENT", event.getReconciliationId(),
            Map.of("adjustedBy", event.getAdjustedBy(), "amount", event.getManualAdjustment(),
                "reason", event.getAdjustmentReason(), "correlationId", correlationId));

        notificationService.sendNotification("FINANCE_MANAGER", "Manual Adjustment Applied",
            String.format("Reconciliation %s manually adjusted by %s: %s",
                event.getReconciliationId(), event.getAdjustedBy(), event.getManualAdjustment()),
            correlationId);

        metricsService.recordManualAdjustment(event.getManualAdjustment());

        log.info("Reconciliation manually adjusted: reconciliationId={}, adjustedBy={}, amount={}",
            event.getReconciliationId(), event.getAdjustedBy(), event.getManualAdjustment());
    }

    private void processReconciliationVerified(PaymentReconciliationCompletedEvent event, String correlationId) {
        PaymentReconciliation reconciliation = reconciliationRepository.findById(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus("VERIFIED");
        reconciliation.setVerifiedAt(LocalDateTime.now());
        reconciliation.setVerifiedBy(event.getVerifiedBy());
        reconciliation.setVerificationNotes(event.getVerificationNotes());
        reconciliationRepository.save(reconciliation);

        kafkaTemplate.send("compliance-events", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "eventType", "RECONCILIATION_VERIFIED",
            "verifiedBy", event.getVerifiedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReconciliationVerified();

        log.info("Reconciliation verified: reconciliationId={}, verifiedBy={}",
            event.getReconciliationId(), event.getVerifiedBy());
    }

    private void finalizeReconciliation(PaymentReconciliationCompletedEvent event, String correlationId) {
        PaymentReconciliation reconciliation = reconciliationRepository.findById(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus("FINALIZED");
        reconciliation.setFinalizedAt(LocalDateTime.now());
        reconciliation.setFinalizedBy(event.getFinalizedBy());
        reconciliationRepository.save(reconciliation);

        reconciliationService.generateReconciliationReport(event.getReconciliationId());

        kafkaTemplate.send("reporting-events", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "eventType", "RECONCILIATION_REPORT_GENERATED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReconciliationFinalized();

        log.info("Reconciliation finalized: reconciliationId={}", event.getReconciliationId());
    }

    private void archiveReconciliation(PaymentReconciliationCompletedEvent event, String correlationId) {
        PaymentReconciliation reconciliation = reconciliationRepository.findById(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus("ARCHIVED");
        reconciliation.setArchivedAt(LocalDateTime.now());
        reconciliationRepository.save(reconciliation);

        reconciliationService.archiveReconciliationData(event.getReconciliationId());

        metricsService.recordReconciliationArchived();

        log.info("Reconciliation archived: reconciliationId={}", event.getReconciliationId());
    }
}