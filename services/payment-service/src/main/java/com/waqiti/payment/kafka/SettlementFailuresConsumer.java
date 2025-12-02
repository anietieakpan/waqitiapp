package com.waqiti.payment.kafka;

import com.waqiti.common.events.SettlementFailureEvent;
import com.waqiti.payment.domain.Settlement;
import com.waqiti.payment.repository.SettlementRepository;
import com.waqiti.payment.service.SettlementService;
import com.waqiti.payment.service.SettlementRetryService;
import com.waqiti.payment.service.LiquidityService;
import com.waqiti.payment.metrics.SettlementMetricsService;
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
public class SettlementFailuresConsumer {

    private final SettlementRepository settlementRepository;
    private final SettlementService settlementService;
    private final SettlementRetryService retryService;
    private final LiquidityService liquidityService;
    private final SettlementMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Financial impact tracking
    private final AtomicLong totalFailedSettlementAmount = new AtomicLong(0);
    private final AtomicLong settlementFailureCount = new AtomicLong(0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter financialImpactCounter;
    private Timer processingTimer;
    private Gauge failedSettlementAmountGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("settlement_failures_processed_total")
            .description("Total number of successfully processed settlement failure events")
            .register(meterRegistry);
        errorCounter = Counter.builder("settlement_failures_errors_total")
            .description("Total number of settlement failure processing errors")
            .register(meterRegistry);
        financialImpactCounter = Counter.builder("settlement_failures_financial_impact")
            .description("Financial impact of settlement failures")
            .register(meterRegistry);
        processingTimer = Timer.builder("settlement_failures_processing_duration")
            .description("Time taken to process settlement failure events")
            .register(meterRegistry);
        failedSettlementAmountGauge = Gauge.builder("settlement_failed_amount_total")
            .description("Total amount of failed settlements")
            .register(meterRegistry, totalFailedSettlementAmount, AtomicLong::get);
    }

    @KafkaListener(
        topics = {"settlement-failures", "settlement-rejected-events", "settlement-timeout-events"},
        groupId = "settlement-failures-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "settlement-failures", fallbackMethod = "handleSettlementFailureFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleSettlementFailureEvent(
            @Payload SettlementFailureEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("settlement-fail-%s-p%d-o%d", event.getSettlementId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getSettlementId(), event.getFailureCode(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing settlement failure: settlementId={}, failureCode={}, amount={}, bankCode={}",
                event.getSettlementId(), event.getFailureCode(), event.getAmount(), event.getBankCode());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Financial impact assessment
            assessFinancialImpact(event.getAmount(), event.getFailureCode(), correlationId);

            switch (event.getFailureType()) {
                case INSUFFICIENT_FUNDS:
                    handleInsufficientFundsFailure(event, correlationId);
                    break;

                case BANK_REJECTION:
                    handleBankRejectionFailure(event, correlationId);
                    break;

                case NETWORK_TIMEOUT:
                    handleNetworkTimeoutFailure(event, correlationId);
                    break;

                case INVALID_ACCOUNT:
                    handleInvalidAccountFailure(event, correlationId);
                    break;

                case CLEARING_HOUSE_ERROR:
                    handleClearingHouseErrorFailure(event, correlationId);
                    break;

                case REGULATORY_HOLD:
                    handleRegulatoryHoldFailure(event, correlationId);
                    break;

                case LIMIT_EXCEEDED:
                    handleLimitExceededFailure(event, correlationId);
                    break;

                case TECHNICAL_ERROR:
                    handleTechnicalErrorFailure(event, correlationId);
                    break;

                case FRAUD_SUSPECTED:
                    handleFraudSuspectedFailure(event, correlationId);
                    break;

                case SYSTEM_MAINTENANCE:
                    handleSystemMaintenanceFailure(event, correlationId);
                    break;

                default:
                    log.warn("Unknown settlement failure type: {}", event.getFailureType());
                    handleGenericSettlementFailure(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSettlementEvent("SETTLEMENT_FAILURE_PROCESSED", event.getSettlementId(),
                Map.of("failureType", event.getFailureType(), "failureCode", event.getFailureCode(),
                    "amount", event.getAmount(), "bankCode", event.getBankCode(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process settlement failure event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("settlement-failure-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleSettlementFailureFallback(
            SettlementFailureEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("settlement-fail-fallback-%s-p%d-o%d", event.getSettlementId(), partition, offset);

        log.error("Circuit breaker fallback triggered for settlement failure: settlementId={}, error={}",
            event.getSettlementId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("settlement-failures-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for high-value settlements
        if (event.getAmount().compareTo(BigDecimal.valueOf(100000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Value Settlement Failure - Circuit Breaker Triggered",
                    String.format("Settlement %s (Amount: %s) failed: %s",
                        event.getSettlementId(), event.getAmount(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltSettlementFailureEvent(
            @Payload SettlementFailureEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-settlement-fail-%s-%d", event.getSettlementId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Settlement failure permanently failed: settlementId={}, topic={}, error={}",
            event.getSettlementId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSettlementEvent("SETTLEMENT_FAILURE_DLT_EVENT", event.getSettlementId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "failureType", event.getFailureType(), "amount", event.getAmount(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Settlement Failure Dead Letter Event",
                String.format("Settlement %s (Amount: %s) sent to DLT: %s",
                    event.getSettlementId(), event.getAmount(), exceptionMessage),
                Map.of("settlementId", event.getSettlementId(), "topic", topic,
                    "correlationId", correlationId, "amount", event.getAmount())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessFinancialImpact(BigDecimal amount, String failureCode, String correlationId) {
        long amountCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
        totalFailedSettlementAmount.addAndGet(amountCents);
        settlementFailureCount.incrementAndGet();
        financialImpactCounter.increment(amountCents);

        // Alert if cumulative failed amount exceeds threshold
        if (totalFailedSettlementAmount.get() > 500000000) { // $5M in cents
            try {
                notificationService.sendExecutiveAlert(
                    "CRITICAL: Settlement Failures Exceed $5M",
                    String.format("Cumulative failed settlement amount: $%,.2f. Immediate intervention required.",
                        totalFailedSettlementAmount.get() / 100.0),
                    "CRITICAL"
                );
                // Reset counter after alert
                totalFailedSettlementAmount.set(0);
            } catch (Exception ex) {
                log.error("Failed to send financial impact alert: {}", ex.getMessage());
            }
        }

        // High-value settlement alert
        if (amount.compareTo(BigDecimal.valueOf(100000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Value Settlement Failure",
                    String.format("Settlement amount: $%,.2f, Failure: %s", amount, failureCode),
                    "HIGH"
                );
            } catch (Exception ex) {
                log.error("Failed to send high-value failure alert: {}", ex.getMessage());
            }
        }
    }

    private void handleInsufficientFundsFailure(SettlementFailureEvent event, String correlationId) {
        updateSettlementStatus(event.getSettlementId(), "FAILED_INSUFFICIENT_FUNDS", event.getFailureReason());

        // Check liquidity status
        liquidityService.checkLiquidityPosition();

        // Trigger liquidity alert
        kafkaTemplate.send("liquidity-alerts", Map.of(
            "alertType", "INSUFFICIENT_FUNDS_SETTLEMENT",
            "settlementId", event.getSettlementId(),
            "amount", event.getAmount(),
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule retry after liquidity is available
        retryService.scheduleSettlementRetry(event.getSettlementId(), "INSUFFICIENT_FUNDS", 3600000); // 1 hour

        notificationService.sendOperationalAlert("Settlement Failed - Insufficient Funds",
            String.format("Settlement %s failed due to insufficient funds: $%,.2f",
                event.getSettlementId(), event.getAmount()),
            "CRITICAL");

        metricsService.recordSettlementFailure("INSUFFICIENT_FUNDS", event.getAmount());
    }

    private void handleBankRejectionFailure(SettlementFailureEvent event, String correlationId) {
        updateSettlementStatus(event.getSettlementId(), "FAILED_BANK_REJECTION", event.getFailureReason());

        // Check if this is a systemic issue with the bank
        if (metricsService.getBankRejectionRate(event.getBankCode()) > 0.1) { // 10% rejection rate
            kafkaTemplate.send("bank-connectivity-alerts", Map.of(
                "bankCode", event.getBankCode(),
                "alertType", "HIGH_REJECTION_RATE",
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Try alternative settlement route
        kafkaTemplate.send("settlement-route-alternatives", Map.of(
            "settlementId", event.getSettlementId(),
            "originalBankCode", event.getBankCode(),
            "amount", event.getAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordSettlementFailure("BANK_REJECTION", event.getAmount());
    }

    private void handleNetworkTimeoutFailure(SettlementFailureEvent event, String correlationId) {
        updateSettlementStatus(event.getSettlementId(), "FAILED_NETWORK_TIMEOUT", event.getFailureReason());

        // Check network connectivity
        kafkaTemplate.send("network-connectivity-checks", Map.of(
            "target", "SETTLEMENT_NETWORK",
            "bankCode", event.getBankCode(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule automatic retry
        retryService.scheduleSettlementRetry(event.getSettlementId(), "NETWORK_TIMEOUT", 300000); // 5 minutes

        metricsService.recordSettlementFailure("NETWORK_TIMEOUT", event.getAmount());
    }

    private void handleInvalidAccountFailure(SettlementFailureEvent event, String correlationId) {
        updateSettlementStatus(event.getSettlementId(), "FAILED_INVALID_ACCOUNT", event.getFailureReason());

        // Validate account information
        kafkaTemplate.send("account-validation-requests", Map.of(
            "accountNumber", event.getAccountNumber(),
            "bankCode", event.getBankCode(),
            "settlementId", event.getSettlementId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Notify treasury team for manual review
        notificationService.sendOperationalAlert("Settlement Failed - Invalid Account",
            String.format("Settlement %s failed due to invalid account: %s",
                event.getSettlementId(), event.getAccountNumber()),
            "HIGH");

        metricsService.recordSettlementFailure("INVALID_ACCOUNT", event.getAmount());
    }

    private void handleClearingHouseErrorFailure(SettlementFailureEvent event, String correlationId) {
        updateSettlementStatus(event.getSettlementId(), "FAILED_CLEARING_HOUSE_ERROR", event.getFailureReason());

        // Check clearing house status
        kafkaTemplate.send("clearing-house-status-checks", Map.of(
            "clearingHouse", event.getClearingHouse(),
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule retry after clearing house issue resolution
        retryService.scheduleSettlementRetry(event.getSettlementId(), "CLEARING_HOUSE_ERROR", 1800000); // 30 minutes

        notificationService.sendOperationalAlert("Clearing House Error",
            String.format("Settlement %s failed due to clearing house error: %s",
                event.getSettlementId(), event.getFailureReason()),
            "HIGH");

        metricsService.recordSettlementFailure("CLEARING_HOUSE_ERROR", event.getAmount());
    }

    private void handleRegulatoryHoldFailure(SettlementFailureEvent event, String correlationId) {
        updateSettlementStatus(event.getSettlementId(), "FAILED_REGULATORY_HOLD", event.getFailureReason());

        // Notify compliance team
        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "REGULATORY_HOLD_SETTLEMENT",
            "settlementId", event.getSettlementId(),
            "amount", event.getAmount(),
            "reason", event.getFailureReason(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Create compliance review case
        kafkaTemplate.send("compliance-case-creation", Map.of(
            "caseType", "SETTLEMENT_REGULATORY_HOLD",
            "settlementId", event.getSettlementId(),
            "amount", event.getAmount(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendComplianceAlert("Settlement Regulatory Hold",
            String.format("Settlement %s placed on regulatory hold: $%,.2f",
                event.getSettlementId(), event.getAmount()),
            correlationId);

        metricsService.recordSettlementFailure("REGULATORY_HOLD", event.getAmount());
    }

    private void handleLimitExceededFailure(SettlementFailureEvent event, String correlationId) {
        updateSettlementStatus(event.getSettlementId(), "FAILED_LIMIT_EXCEEDED", event.getFailureReason());

        // Check if limit can be temporarily increased
        kafkaTemplate.send("settlement-limit-review", Map.of(
            "bankCode", event.getBankCode(),
            "currentLimit", event.getCurrentLimit(),
            "requestedAmount", event.getAmount(),
            "settlementId", event.getSettlementId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Split settlement if possible
        if (event.getAmount().compareTo(event.getCurrentLimit()) > 0) {
            kafkaTemplate.send("settlement-split-requests", Map.of(
                "originalSettlementId", event.getSettlementId(),
                "amount", event.getAmount(),
                "maxSplitAmount", event.getCurrentLimit(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordSettlementFailure("LIMIT_EXCEEDED", event.getAmount());
    }

    private void handleTechnicalErrorFailure(SettlementFailureEvent event, String correlationId) {
        updateSettlementStatus(event.getSettlementId(), "FAILED_TECHNICAL_ERROR", event.getFailureReason());

        // Trigger system health check
        kafkaTemplate.send("system-health-alerts", Map.of(
            "component", "SETTLEMENT_PROCESSING",
            "severity", "HIGH",
            "error", event.getFailureReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule retry after technical issue resolution
        retryService.scheduleSettlementRetry(event.getSettlementId(), "TECHNICAL_ERROR", 900000); // 15 minutes

        notificationService.sendOperationalAlert("Settlement Technical Error",
            String.format("Technical error in settlement %s: %s",
                event.getSettlementId(), event.getFailureReason()),
            "HIGH");

        metricsService.recordSettlementFailure("TECHNICAL_ERROR", event.getAmount());
    }

    private void handleFraudSuspectedFailure(SettlementFailureEvent event, String correlationId) {
        updateSettlementStatus(event.getSettlementId(), "FAILED_FRAUD_SUSPECTED", event.getFailureReason());

        // Trigger fraud investigation
        kafkaTemplate.send("fraud-investigation-requests", Map.of(
            "settlementId", event.getSettlementId(),
            "amount", event.getAmount(),
            "bankCode", event.getBankCode(),
            "suspicionLevel", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Freeze related accounts temporarily
        kafkaTemplate.send("account-freeze-requests", Map.of(
            "accountNumber", event.getAccountNumber(),
            "freezeType", "FRAUD_SUSPECTED",
            "duration", "24_HOURS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendSecurityAlert("Settlement Fraud Suspected",
            String.format("Suspected fraudulent settlement %s: $%,.2f",
                event.getSettlementId(), event.getAmount()),
            correlationId);

        metricsService.recordSettlementFailure("FRAUD_SUSPECTED", event.getAmount());
    }

    private void handleSystemMaintenanceFailure(SettlementFailureEvent event, String correlationId) {
        updateSettlementStatus(event.getSettlementId(), "FAILED_SYSTEM_MAINTENANCE", event.getFailureReason());

        // Check maintenance schedule
        kafkaTemplate.send("maintenance-schedule-checks", Map.of(
            "system", "SETTLEMENT_PROCESSING",
            "bankCode", event.getBankCode(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule retry after maintenance window
        retryService.scheduleSettlementRetry(event.getSettlementId(), "SYSTEM_MAINTENANCE", 7200000); // 2 hours

        metricsService.recordSettlementFailure("SYSTEM_MAINTENANCE", event.getAmount());
    }

    private void handleGenericSettlementFailure(SettlementFailureEvent event, String correlationId) {
        updateSettlementStatus(event.getSettlementId(), "FAILED_UNKNOWN", event.getFailureReason());

        // Log for investigation
        auditService.logSettlementEvent("UNKNOWN_SETTLEMENT_FAILURE", event.getSettlementId(),
            Map.of("failureType", event.getFailureType(), "failureCode", event.getFailureCode(),
                "failureReason", event.getFailureReason(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Settlement Failure",
            String.format("Unknown settlement failure %s: %s",
                event.getSettlementId(), event.getFailureReason()),
            "HIGH");

        metricsService.recordSettlementFailure("UNKNOWN", event.getAmount());
    }

    private void updateSettlementStatus(String settlementId, String status, String reason) {
        try {
            Settlement settlement = settlementRepository.findById(settlementId).orElse(null);
            if (settlement != null) {
                settlement.setStatus(status);
                settlement.setFailureReason(reason);
                settlement.setUpdatedAt(LocalDateTime.now());
                settlementRepository.save(settlement);
            }
        } catch (Exception e) {
            log.error("Failed to update settlement status: {}", e.getMessage());
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