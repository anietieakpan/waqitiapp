package com.waqiti.dispute.kafka;

import com.waqiti.payment.dto.PaymentChargebackEvent;
import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.service.TransactionDisputeService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.idempotency.IdempotencyService;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import jakarta.annotation.PostConstruct;

/**
 * CRITICAL FINANCIAL CONSUMER - Chargeback Initiation Handler
 *
 * IDEMPOTENCY PROTECTION:
 * - Replaced in-memory ConcurrentHashMap with Redis-backed IdempotencyService
 * - 30-day TTL for chargeback idempotency tracking (longer for financial records)
 * - CRITICAL: Prevents duplicate chargeback processing
 * - Distributed idempotency survives service restarts
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChargebackInitiatedConsumer {

    private final TransactionDisputeService disputeService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("chargeback_initiated_processed_total")
            .description("Total number of successfully processed chargeback initiated events")
            .register(meterRegistry);
        errorCounter = Counter.builder("chargeback_initiated_errors_total")
            .description("Total number of chargeback initiated processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("chargeback_initiated_processing_duration")
            .description("Time taken to process chargeback initiated events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"chargeback-initiated"},
        groupId = "dispute-chargeback-initiated-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "chargeback-initiated", fallbackMethod = "handleChargebackInitiatedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleChargebackInitiatedEvent(
            @Payload PaymentChargebackEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("chargeback-initiated-%s-p%d-o%d", event.getChargebackId(), partition, offset);

        // CRITICAL IDEMPOTENCY CHECK using distributed Redis-backed service
        String idempotencyKey = "chargeback:" + event.getChargebackId() + ":" + event.getEventId();
        UUID operationId = UUID.randomUUID();
        Duration ttl = Duration.ofDays(30); // 30-day retention for chargeback tracking

        try {
            if (!idempotencyService.startOperation(idempotencyKey, operationId, ttl)) {
                log.warn("⚠️ DUPLICATE CHARGEBACK PREVENTED - Already processed: chargebackId={}, eventId={}, amount={}",
                        event.getChargebackId(), event.getEventId(), event.getChargebackAmount());
                meterRegistry.counter("chargeback_duplicate_prevented_total").increment();
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing chargeback initiated: chargebackId={}, transactionId={}, amount={}, network={}",
                event.getChargebackId(), event.getTransactionId(), event.getChargebackAmount(), event.getCardNetwork());

            // Process chargeback initiation
            processChargebackInitiation(event, correlationId);

            auditService.logDisputeEvent("CHARGEBACK_INITIATED_PROCESSED", event.getChargebackId(),
                Map.of("chargebackId", event.getChargebackId(), "transactionId", event.getTransactionId(),
                    "customerId", event.getCustomerId(), "merchantId", event.getMerchantId(),
                    "amount", event.getChargebackAmount(), "currency", event.getCurrency(),
                    "reasonCode", event.getReasonCode(), "network", event.getCardNetwork(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            // Mark idempotency operation as complete
            idempotencyService.completeOperation(idempotencyKey, operationId);

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process chargeback initiated event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("chargeback-initiated-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleChargebackInitiatedEventFallback(
            PaymentChargebackEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("chargeback-initiated-fallback-%s-p%d-o%d", event.getChargebackId(), partition, offset);

        log.error("Circuit breaker fallback triggered for chargeback initiated: chargebackId={}, error={}",
            event.getChargebackId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("chargeback-initiated-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Chargeback Initiated Circuit Breaker Triggered",
                String.format("Chargeback %s initiation processing failed: %s", event.getChargebackId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltChargebackInitiatedEvent(
            @Payload PaymentChargebackEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-chargeback-initiated-%s-%d", event.getChargebackId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Chargeback initiated permanently failed: chargebackId={}, topic={}, error={}",
            event.getChargebackId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logDisputeEvent("CHARGEBACK_INITIATED_DLT_EVENT", event.getChargebackId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "chargebackId", event.getChargebackId(), "transactionId", event.getTransactionId(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Chargeback Initiated Dead Letter Event",
                String.format("Chargeback %s initiation sent to DLT: %s", event.getChargebackId(), exceptionMessage),
                Map.of("chargebackId", event.getChargebackId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    // REMOVED: Old in-memory idempotency methods
    // Replaced with Redis-backed IdempotencyService for distributed, persistent idempotency
    // Old methods: isEventProcessed(), markEventAsProcessed(), cleanExpiredEntries()

    private void processChargebackInitiation(PaymentChargebackEvent event, String correlationId) {
        log.info("Processing chargeback initiation: chargebackId={}, amount={}, reasonCode={}",
            event.getChargebackId(), event.getChargebackAmount(), event.getReasonCode());

        // Create dispute record for chargeback
        createChargebackDispute(event, correlationId);

        // Process immediate financial impact
        processFinancialImpact(event, correlationId);

        // Evaluate response strategy
        evaluateResponseStrategy(event, correlationId);

        // Notify stakeholders
        notifyStakeholders(event, correlationId);

        // Update merchant risk profile
        updateMerchantRiskProfile(event, correlationId);

        // Trigger prevention analysis
        triggerPreventionAnalysis(event, correlationId);

        // Update metrics
        updateChargebackMetrics(event);

        log.info("Chargeback initiation processed: chargebackId={}, status={}",
            event.getChargebackId(), event.getStatus());
    }

    private void createChargebackDispute(PaymentChargebackEvent event, String correlationId) {
        // Create or update dispute record
        disputeService.createChargebackDispute(
            event.getChargebackId(),
            event.getTransactionId(),
            event.getChargebackAmount(),
            event.getCurrency(),
            event.getReasonCode(),
            event.getReasonDescription(),
            event.getCustomerId(),
            event.getMerchantId(),
            correlationId
        );

        // Set initial status and priority
        disputeService.setChargebackStatus(event.getChargebackId(), "RECEIVED", correlationId);
        disputeService.setChargebackPriority(event.getChargebackId(), event.getPriority(), correlationId);

        log.info("Chargeback dispute created: chargebackId={}, priority={}",
            event.getChargebackId(), event.getPriority());
    }

    private void processFinancialImpact(PaymentChargebackEvent event, String correlationId) {
        // Calculate total financial impact
        var totalImpact = event.calculateTotalLoss();

        // Initiate financial holds and reserves
        kafkaTemplate.send("financial-holds", Map.of(
            "type", "CHARGEBACK_RESERVE",
            "chargebackId", event.getChargebackId(),
            "merchantId", event.getMerchantId(),
            "amount", event.getChargebackAmount(),
            "fee", event.getChargebackFee(),
            "totalImpact", totalImpact,
            "currency", event.getCurrency(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Update merchant account balance
        kafkaTemplate.send("merchant-account-adjustments", Map.of(
            "merchantId", event.getMerchantId(),
            "type", "CHARGEBACK_DEBIT",
            "amount", totalImpact,
            "currency", event.getCurrency(),
            "reference", event.getChargebackId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Financial impact processed: chargebackId={}, totalImpact={}",
            event.getChargebackId(), totalImpact);
    }

    private void evaluateResponseStrategy(PaymentChargebackEvent event, String correlationId) {
        if (!Boolean.TRUE.equals(event.getResponseRequired())) {
            log.info("Chargeback does not require response: {}", event.getChargebackId());
            return;
        }

        // Analyze if chargeback should be contested
        boolean shouldContest = analyzeContestability(event);

        if (shouldContest && event.canContest()) {
            // Send to investigation queue
            kafkaTemplate.send("chargeback-investigations", Map.of(
                "chargebackId", event.getChargebackId(),
                "transactionId", event.getTransactionId(),
                "priority", event.getPriority(),
                "deadline", event.getResponseDeadline(),
                "reasonCode", event.getReasonCode(),
                "autoContestable", isAutoContestable(event),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Chargeback queued for investigation: chargebackId={}, deadline={}",
                event.getChargebackId(), event.getResponseDeadline());
        } else {
            // Accept chargeback without contest
            acceptChargeback(event, correlationId);
        }
    }

    private void notifyStakeholders(PaymentChargebackEvent event, String correlationId) {
        // Notify customer
        notificationService.sendNotification(event.getCustomerId(), "Chargeback Initiated",
            String.format("A chargeback has been initiated for your transaction %s for amount %s %s",
                event.getTransactionId(), event.getChargebackAmount(), event.getCurrency()),
            correlationId);

        // Notify merchant
        notificationService.sendNotification(event.getMerchantId(), "Chargeback Alert",
            String.format("Chargeback initiated for transaction %s. Amount: %s %s. Reason: %s",
                event.getTransactionId(), event.getChargebackAmount(), event.getCurrency(),
                event.getReasonDescription()),
            correlationId);

        // High-value or critical chargebacks need management attention
        if (event.isHighValue() || event.getPriority() == PaymentChargebackEvent.Priority.CRITICAL) {
            notificationService.sendHighPriorityAlert(
                "High-Value Chargeback Initiated",
                String.format("High-value chargeback %s initiated for amount %s %s",
                    event.getChargebackId(), event.getChargebackAmount(), event.getCurrency()),
                Map.of("chargebackId", event.getChargebackId(), "amount", event.getChargebackAmount(),
                       "merchantId", event.getMerchantId(), "priority", "HIGH")
            );
        }
    }

    private void updateMerchantRiskProfile(PaymentChargebackEvent event, String correlationId) {
        // Send merchant risk update
        kafkaTemplate.send("merchant-risk-updates", Map.of(
            "merchantId", event.getMerchantId(),
            "eventType", "CHARGEBACK_INITIATED",
            "chargebackId", event.getChargebackId(),
            "amount", event.getChargebackAmount(),
            "reasonCode", event.getReasonCode(),
            "cardNetwork", event.getCardNetwork(),
            "riskImpact", calculateRiskImpact(event),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check for chargeback thresholds
        checkChargebackThresholds(event, correlationId);
    }

    private void triggerPreventionAnalysis(PaymentChargebackEvent event, String correlationId) {
        // Send to prevention analysis
        kafkaTemplate.send("chargeback-prevention-events", Map.of(
            "chargebackId", event.getChargebackId(),
            "transactionId", event.getTransactionId(),
            "merchantId", event.getMerchantId(),
            "reasonCode", event.getReasonCode(),
            "analysisType", "POST_CHARGEBACK",
            "preventionGoal", "REDUCE_FUTURE_CHARGEBACKS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void acceptChargeback(PaymentChargebackEvent event, String correlationId) {
        // Update chargeback status to accepted
        disputeService.setChargebackStatus(event.getChargebackId(), "ACCEPTED", correlationId);

        // Finalize financial impact
        kafkaTemplate.send("financial-finalizations", Map.of(
            "type", "CHARGEBACK_ACCEPTED",
            "chargebackId", event.getChargebackId(),
            "merchantId", event.getMerchantId(),
            "finalAmount", event.calculateTotalLoss(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Chargeback accepted without contest: chargebackId={}", event.getChargebackId());
    }

    private boolean analyzeContestability(PaymentChargebackEvent event) {
        // Basic contestability rules
        if (event.getChargebackAmount().compareTo(new java.math.BigDecimal("25")) <= 0) {
            return false; // Not worth contesting small amounts
        }

        if (event.isFinalStage()) {
            return false; // Final stages typically not contestable
        }

        // Consider fraud indicators
        if (Boolean.TRUE.equals(event.getFraudRelated()) && event.getFraudScore() != null && event.getFraudScore() > 80.0) {
            return false; // High fraud score makes contest unlikely to succeed
        }

        return true;
    }

    private boolean isAutoContestable(PaymentChargebackEvent event) {
        // Auto-contestable if standard reason codes and good merchant history
        return Set.of("4855", "4541", "4554", "83", "85").contains(event.getReasonCode()) &&
               !Boolean.TRUE.equals(event.getHighRiskMerchant());
    }

    private String calculateRiskImpact(PaymentChargebackEvent event) {
        if (event.isHighValue()) return "HIGH";
        if (event.getPriority() == PaymentChargebackEvent.Priority.CRITICAL) return "CRITICAL";
        return "MEDIUM";
    }

    private void checkChargebackThresholds(PaymentChargebackEvent event, String correlationId) {
        // Check if merchant exceeds chargeback thresholds
        kafkaTemplate.send("chargeback-threshold-monitoring", Map.of(
            "merchantId", event.getMerchantId(),
            "chargebackId", event.getChargebackId(),
            "currentCount", event.getMerchantChargebackCount(),
            "currentRatio", event.getMerchantChargebackRatio(),
            "checkType", "THRESHOLD_MONITORING",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void updateChargebackMetrics(PaymentChargebackEvent event) {
        meterRegistry.counter("chargebacks_initiated_total",
            "network", event.getCardNetwork().name(),
            "reason", event.getReasonCode(),
            "priority", event.getPriority().name()).increment();

        meterRegistry.gauge("chargeback_amount_usd",
            event.getChargebackAmount().doubleValue());

        if (event.isHighValue()) {
            meterRegistry.counter("high_value_chargebacks_total").increment();
        }
    }
}