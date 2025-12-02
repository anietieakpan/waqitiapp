package com.waqiti.payment.kafka;

import com.waqiti.common.events.TransactionReversalFailedEvent;
import com.waqiti.payment.domain.TransactionReversal;
import com.waqiti.payment.repository.TransactionReversalRepository;
import com.waqiti.payment.service.ReversalService;
import com.waqiti.payment.service.TransactionRecoveryService;
import com.waqiti.payment.service.DisputeService;
import com.waqiti.payment.metrics.ReversalMetricsService;
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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import java.math.BigDecimal;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionReversalsFailedConsumer {

    private final TransactionReversalRepository reversalRepository;
    private final ReversalService reversalService;
    private final TransactionRecoveryService recoveryService;
    private final DisputeService disputeService;
    private final ReversalMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Financial impact tracking
    private final AtomicLong totalFailedReversalAmount = new AtomicLong(0);
    private final AtomicLong reversalFailureCount = new AtomicLong(0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter financialImpactCounter;
    private Timer processingTimer;
    private Gauge failedReversalAmountGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("transaction_reversals_failed_processed_total")
            .description("Total number of successfully processed failed transaction reversal events")
            .register(meterRegistry);
        errorCounter = Counter.builder("transaction_reversals_failed_errors_total")
            .description("Total number of failed transaction reversal processing errors")
            .register(meterRegistry);
        financialImpactCounter = Counter.builder("transaction_reversals_failed_financial_impact")
            .description("Financial impact of failed transaction reversals")
            .register(meterRegistry);
        processingTimer = Timer.builder("transaction_reversals_failed_processing_duration")
            .description("Time taken to process failed transaction reversal events")
            .register(meterRegistry);
        failedReversalAmountGauge = Gauge.builder("transaction_reversals_failed_amount_total")
            .description("Total amount of failed transaction reversals")
            .register(meterRegistry, totalFailedReversalAmount, AtomicLong::get);
    }

    @KafkaListener(
        topics = {"transaction-reversals-failed", "reversal-timeout-events", "reversal-rejection-events"},
        groupId = "transaction-reversals-failed-service-group",
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
    @CircuitBreaker(name = "transaction-reversals-failed", fallbackMethod = "handleReversalFailureFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleTransactionReversalFailedEvent(
            @Payload TransactionReversalFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("reversal-fail-%s-p%d-o%d", event.getReversalId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getReversalId(), event.getFailureReason(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing transaction reversal failure: reversalId={}, originalTransactionId={}, failureReason={}, amount={}",
                event.getReversalId(), event.getOriginalTransactionId(), event.getFailureReason(), event.getAmount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Financial impact assessment
            assessFinancialImpact(event.getAmount(), event.getFailureReason(), correlationId);

            switch (event.getFailureType()) {
                case INSUFFICIENT_FUNDS:
                    handleInsufficientFundsFailure(event, correlationId);
                    break;

                case ORIGINAL_TRANSACTION_NOT_FOUND:
                    handleOriginalTransactionNotFound(event, correlationId);
                    break;

                case REVERSAL_WINDOW_EXPIRED:
                    handleReversalWindowExpired(event, correlationId);
                    break;

                case ACCOUNT_FROZEN:
                    handleAccountFrozenFailure(event, correlationId);
                    break;

                case NETWORK_TIMEOUT:
                    handleNetworkTimeoutFailure(event, correlationId);
                    break;

                case BANK_REJECTION:
                    handleBankRejectionFailure(event, correlationId);
                    break;

                case DUPLICATE_REVERSAL:
                    handleDuplicateReversalFailure(event, correlationId);
                    break;

                case AUTHORIZATION_REQUIRED:
                    handleAuthorizationRequiredFailure(event, correlationId);
                    break;

                case SYSTEM_ERROR:
                    handleSystemErrorFailure(event, correlationId);
                    break;

                case COMPLIANCE_HOLD:
                    handleComplianceHoldFailure(event, correlationId);
                    break;

                default:
                    log.warn("Unknown reversal failure type: {}", event.getFailureType());
                    handleGenericReversalFailure(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logReversalEvent("TRANSACTION_REVERSAL_FAILURE_PROCESSED", event.getReversalId(),
                Map.of("failureType", event.getFailureType(), "failureReason", event.getFailureReason(),
                    "originalTransactionId", event.getOriginalTransactionId(), "amount", event.getAmount(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process transaction reversal failure event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("reversal-failure-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleReversalFailureFallback(
            TransactionReversalFailedEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("reversal-fail-fallback-%s-p%d-o%d", event.getReversalId(), partition, offset);

        log.error("Circuit breaker fallback triggered for reversal failure: reversalId={}, error={}",
            event.getReversalId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("transaction-reversals-failed-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for high-value reversals
        if (event.getAmount().compareTo(BigDecimal.valueOf(25000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Value Reversal Failure - Circuit Breaker Triggered",
                    String.format("Reversal %s (Amount: %s) failed: %s",
                        event.getReversalId(), event.getAmount(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltReversalFailureEvent(
            @Payload TransactionReversalFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-reversal-fail-%s-%d", event.getReversalId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Reversal failure permanently failed: reversalId={}, topic={}, error={}",
            event.getReversalId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logReversalEvent("REVERSAL_FAILURE_DLT_EVENT", event.getReversalId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "failureType", event.getFailureType(), "amount", event.getAmount(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Transaction Reversal Failure Dead Letter Event",
                String.format("Reversal %s (Amount: %s) sent to DLT: %s",
                    event.getReversalId(), event.getAmount(), exceptionMessage),
                Map.of("reversalId", event.getReversalId(), "topic", topic,
                    "correlationId", correlationId, "amount", event.getAmount())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessFinancialImpact(BigDecimal amount, String failureReason, String correlationId) {
        long amountCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
        totalFailedReversalAmount.addAndGet(amountCents);
        reversalFailureCount.incrementAndGet();
        financialImpactCounter.increment(amountCents);

        // Alert if cumulative failed reversal amount exceeds threshold
        if (totalFailedReversalAmount.get() > 500000000) { // $5M in cents
            try {
                notificationService.sendExecutiveAlert(
                    "CRITICAL: Failed Reversals Exceed $5M",
                    String.format("Cumulative failed reversal amount: $%,.2f. Risk management intervention required.",
                        totalFailedReversalAmount.get() / 100.0),
                    "CRITICAL"
                );
                // Reset counter after alert
                totalFailedReversalAmount.set(0);
            } catch (Exception ex) {
                log.error("Failed to send financial impact alert: {}", ex.getMessage());
            }
        }

        // High-value reversal failure alert
        if (amount.compareTo(BigDecimal.valueOf(25000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Value Reversal Failure",
                    String.format("Reversal amount: $%,.2f, Failure: %s", amount, failureReason),
                    "HIGH"
                );
            } catch (Exception ex) {
                log.error("Failed to send high-value reversal failure alert: {}", ex.getMessage());
            }
        }
    }

    private void handleInsufficientFundsFailure(TransactionReversalFailedEvent event, String correlationId) {
        updateReversalStatus(event.getReversalId(), "FAILED_INSUFFICIENT_FUNDS", event.getFailureReason());

        // Check liquidity position
        kafkaTemplate.send("liquidity-checks", Map.of(
            "accountId", event.getAccountId(),
            "checkType", "REVERSAL_LIQUIDITY",
            "requiredAmount", event.getAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule reversal retry when funds available
        kafkaTemplate.send("reversal-retry-schedules", Map.of(
            "reversalId", event.getReversalId(),
            "retryType", "INSUFFICIENT_FUNDS",
            "scheduleDelay", 3600000, // 1 hour
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Reversal Failed - Insufficient Funds",
            String.format("Reversal %s failed due to insufficient funds: $%,.2f",
                event.getReversalId(), event.getAmount()),
            "HIGH");

        metricsService.recordReversalFailure("INSUFFICIENT_FUNDS", event.getAmount());
    }

    private void handleOriginalTransactionNotFound(TransactionReversalFailedEvent event, String correlationId) {
        updateReversalStatus(event.getReversalId(), "FAILED_TRANSACTION_NOT_FOUND", event.getFailureReason());

        // Search for original transaction across all systems
        kafkaTemplate.send("transaction-search-requests", Map.of(
            "transactionId", event.getOriginalTransactionId(),
            "searchScope", "COMPREHENSIVE",
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check if transaction was already reversed
        kafkaTemplate.send("reversal-history-checks", Map.of(
            "originalTransactionId", event.getOriginalTransactionId(),
            "checkType", "PREVIOUS_REVERSALS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Reversal Failed - Original Transaction Not Found",
            String.format("Cannot find original transaction %s for reversal %s",
                event.getOriginalTransactionId(), event.getReversalId()),
            "HIGH");

        metricsService.recordReversalFailure("TRANSACTION_NOT_FOUND", event.getAmount());
    }

    private void handleReversalWindowExpired(TransactionReversalFailedEvent event, String correlationId) {
        updateReversalStatus(event.getReversalId(), "FAILED_WINDOW_EXPIRED", event.getFailureReason());

        // Check if manual authorization can override
        if (event.getAmount().compareTo(BigDecimal.valueOf(10000)) < 0) { // Under $10k
            kafkaTemplate.send("manual-authorization-requests", Map.of(
                "reversalId", event.getReversalId(),
                "authorizationType", "EXPIRED_WINDOW_OVERRIDE",
                "amount", event.getAmount(),
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            // Escalate to dispute process for high-value
            kafkaTemplate.send("dispute-case-creation", Map.of(
                "originalTransactionId", event.getOriginalTransactionId(),
                "disputeType", "EXPIRED_REVERSAL_WINDOW",
                "amount", event.getAmount(),
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        notificationService.sendOperationalAlert("Reversal Failed - Window Expired",
            String.format("Reversal window expired for transaction %s, amount: $%,.2f",
                event.getOriginalTransactionId(), event.getAmount()),
            "MEDIUM");

        metricsService.recordReversalFailure("WINDOW_EXPIRED", event.getAmount());
    }

    private void handleAccountFrozenFailure(TransactionReversalFailedEvent event, String correlationId) {
        updateReversalStatus(event.getReversalId(), "FAILED_ACCOUNT_FROZEN", event.getFailureReason());

        // Check freeze status and reason
        kafkaTemplate.send("account-freeze-status-checks", Map.of(
            "accountId", event.getAccountId(),
            "checkType", "FREEZE_DETAILS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Request freeze override for reversal
        kafkaTemplate.send("freeze-override-requests", Map.of(
            "accountId", event.getAccountId(),
            "overrideType", "REVERSAL_PROCESSING",
            "reversalId", event.getReversalId(),
            "amount", event.getAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Reversal Failed - Account Frozen",
            String.format("Account %s frozen, blocking reversal %s",
                event.getAccountId(), event.getReversalId()),
            "HIGH");

        metricsService.recordReversalFailure("ACCOUNT_FROZEN", event.getAmount());
    }

    private void handleNetworkTimeoutFailure(TransactionReversalFailedEvent event, String correlationId) {
        updateReversalStatus(event.getReversalId(), "FAILED_NETWORK_TIMEOUT", event.getFailureReason());

        // Check network connectivity
        kafkaTemplate.send("network-connectivity-checks", Map.of(
            "target", "REVERSAL_NETWORK",
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule automatic retry
        kafkaTemplate.send("reversal-retry-schedules", Map.of(
            "reversalId", event.getReversalId(),
            "retryType", "NETWORK_TIMEOUT",
            "scheduleDelay", 300000, // 5 minutes
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReversalFailure("NETWORK_TIMEOUT", event.getAmount());
    }

    private void handleBankRejectionFailure(TransactionReversalFailedEvent event, String correlationId) {
        updateReversalStatus(event.getReversalId(), "FAILED_BANK_REJECTION", event.getFailureReason());

        // Check if alternative reversal route available
        kafkaTemplate.send("alternative-reversal-routes", Map.of(
            "reversalId", event.getReversalId(),
            "originalBankCode", event.getBankCode(),
            "amount", event.getAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Escalate to manual processing if high value
        if (event.getAmount().compareTo(BigDecimal.valueOf(5000)) > 0) {
            kafkaTemplate.send("manual-reversal-requests", Map.of(
                "reversalId", event.getReversalId(),
                "processingType", "MANUAL_OVERRIDE",
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordReversalFailure("BANK_REJECTION", event.getAmount());
    }

    private void handleDuplicateReversalFailure(TransactionReversalFailedEvent event, String correlationId) {
        updateReversalStatus(event.getReversalId(), "FAILED_DUPLICATE_REVERSAL", event.getFailureReason());

        // Check for existing reversals
        kafkaTemplate.send("duplicate-reversal-checks", Map.of(
            "originalTransactionId", event.getOriginalTransactionId(),
            "checkType", "EXISTING_REVERSALS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Mark as duplicate and close
        kafkaTemplate.send("reversal-closure-requests", Map.of(
            "reversalId", event.getReversalId(),
            "closureReason", "DUPLICATE_DETECTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReversalFailure("DUPLICATE_REVERSAL", BigDecimal.ZERO);
    }

    private void handleAuthorizationRequiredFailure(TransactionReversalFailedEvent event, String correlationId) {
        updateReversalStatus(event.getReversalId(), "FAILED_AUTHORIZATION_REQUIRED", event.getFailureReason());

        // Route to authorization workflow
        kafkaTemplate.send("reversal-authorization-requests", Map.of(
            "reversalId", event.getReversalId(),
            "authorizationType", "REVERSAL_APPROVAL",
            "amount", event.getAmount(),
            "priority", event.getAmount().compareTo(BigDecimal.valueOf(10000)) > 0 ? "HIGH" : "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Reversal Requires Authorization",
            String.format("Reversal %s requires manual authorization: $%,.2f",
                event.getReversalId(), event.getAmount()),
            "MEDIUM");

        metricsService.recordReversalFailure("AUTHORIZATION_REQUIRED", event.getAmount());
    }

    private void handleSystemErrorFailure(TransactionReversalFailedEvent event, String correlationId) {
        updateReversalStatus(event.getReversalId(), "FAILED_SYSTEM_ERROR", event.getFailureReason());

        // Trigger system health check
        kafkaTemplate.send("system-health-alerts", Map.of(
            "component", "REVERSAL_PROCESSING",
            "severity", "HIGH",
            "error", event.getFailureReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule retry after system recovery
        kafkaTemplate.send("reversal-retry-schedules", Map.of(
            "reversalId", event.getReversalId(),
            "retryType", "SYSTEM_ERROR",
            "scheduleDelay", 900000, // 15 minutes
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Reversal System Error",
            String.format("System error in reversal processing: %s", event.getFailureReason()),
            "HIGH");

        metricsService.recordReversalFailure("SYSTEM_ERROR", event.getAmount());
    }

    private void handleComplianceHoldFailure(TransactionReversalFailedEvent event, String correlationId) {
        updateReversalStatus(event.getReversalId(), "FAILED_COMPLIANCE_HOLD", event.getFailureReason());

        // Create compliance review case
        kafkaTemplate.send("compliance-case-creation", Map.of(
            "caseType", "REVERSAL_COMPLIANCE_HOLD",
            "reversalId", event.getReversalId(),
            "amount", event.getAmount(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendComplianceAlert("Reversal Compliance Hold",
            String.format("Reversal %s held for compliance review: $%,.2f",
                event.getReversalId(), event.getAmount()),
            correlationId);

        metricsService.recordReversalFailure("COMPLIANCE_HOLD", event.getAmount());
    }

    private void handleGenericReversalFailure(TransactionReversalFailedEvent event, String correlationId) {
        updateReversalStatus(event.getReversalId(), "FAILED_UNKNOWN", event.getFailureReason());

        // Log for investigation
        auditService.logReversalEvent("UNKNOWN_REVERSAL_FAILURE", event.getReversalId(),
            Map.of("failureType", event.getFailureType(), "failureReason", event.getFailureReason(),
                "originalTransactionId", event.getOriginalTransactionId(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Reversal Failure",
            String.format("Unknown reversal failure for %s: %s",
                event.getReversalId(), event.getFailureReason()),
            "HIGH");

        metricsService.recordReversalFailure("UNKNOWN", event.getAmount());
    }

    private void updateReversalStatus(String reversalId, String status, String reason) {
        try {
            TransactionReversal reversal = reversalRepository.findById(reversalId).orElse(null);
            if (reversal != null) {
                reversal.setStatus(status);
                reversal.setFailureReason(reason);
                reversal.setUpdatedAt(LocalDateTime.now());
                reversalRepository.save(reversal);
            }
        } catch (Exception e) {
            log.error("Failed to update reversal status: {}", e.getMessage());
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
}