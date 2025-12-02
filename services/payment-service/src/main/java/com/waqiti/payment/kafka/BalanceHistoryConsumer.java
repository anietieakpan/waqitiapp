package com.waqiti.payment.kafka;

import com.waqiti.common.events.BalanceHistoryEvent;
import com.waqiti.payment.domain.BalanceHistory;
import com.waqiti.payment.repository.BalanceHistoryRepository;
import com.waqiti.payment.service.BalanceService;
import com.waqiti.payment.service.AnalyticsService;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class BalanceHistoryConsumer {

    private final BalanceHistoryRepository balanceHistoryRepository;
    private final BalanceService balanceService;
    private final AnalyticsService analyticsService;
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
        successCounter = Counter.builder("balance_history_processed_total")
            .description("Total number of successfully processed balance history events")
            .register(meterRegistry);
        errorCounter = Counter.builder("balance_history_errors_total")
            .description("Total number of balance history processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("balance_history_processing_duration")
            .description("Time taken to process balance history events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"balance-history", "account-balance-changes", "balance-snapshots"},
        groupId = "balance-history-service-group",
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
    @CircuitBreaker(name = "balance-history", fallbackMethod = "handleBalanceHistoryEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBalanceHistoryEvent(
            @Payload BalanceHistoryEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("balance-history-%s-p%d-o%d", event.getAccountId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAccountId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing balance history: accountId={}, balance={}, change={}",
                event.getAccountId(), event.getCurrentBalance(), event.getBalanceChange());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case BALANCE_UPDATED:
                    processBalanceUpdated(event, correlationId);
                    break;

                case DAILY_BALANCE_SNAPSHOT:
                    processDailyBalanceSnapshot(event, correlationId);
                    break;

                case MONTHLY_BALANCE_SUMMARY:
                    processMonthlyBalanceSummary(event, correlationId);
                    break;

                case BALANCE_TREND_ANALYSIS:
                    processBalanceTrendAnalysis(event, correlationId);
                    break;

                case BALANCE_ANOMALY_DETECTED:
                    processBalanceAnomalyDetected(event, correlationId);
                    break;

                case BALANCE_MILESTONE_REACHED:
                    processBalanceMilestoneReached(event, correlationId);
                    break;

                default:
                    log.warn("Unknown balance history event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("BALANCE_HISTORY_EVENT_PROCESSED", event.getAccountId(),
                Map.of("eventType", event.getEventType(), "currentBalance", event.getCurrentBalance(),
                    "balanceChange", event.getBalanceChange(), "transactionId", event.getTransactionId(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process balance history event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("balance-history-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleBalanceHistoryEventFallback(
            BalanceHistoryEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("balance-history-fallback-%s-p%d-o%d", event.getAccountId(), partition, offset);

        log.error("Circuit breaker fallback triggered for balance history: accountId={}, error={}",
            event.getAccountId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("balance-history-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBalanceHistoryEvent(
            @Payload BalanceHistoryEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-balance-history-%s-%d", event.getAccountId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Balance history permanently failed: accountId={}, topic={}, error={}",
            event.getAccountId(), topic, exceptionMessage);

        auditService.logPaymentEvent("BALANCE_HISTORY_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));
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

    private void processBalanceUpdated(BalanceHistoryEvent event, String correlationId) {
        BalanceHistory history = BalanceHistory.builder()
            .accountId(event.getAccountId())
            .transactionId(event.getTransactionId())
            .previousBalance(event.getPreviousBalance())
            .currentBalance(event.getCurrentBalance())
            .balanceChange(event.getBalanceChange())
            .changeReason(event.getChangeReason())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceHistoryRepository.save(history);

        // Update balance analytics
        analyticsService.updateBalanceAnalytics(
            event.getAccountId(),
            event.getCurrentBalance(),
            event.getBalanceChange(),
            correlationId
        );

        metricsService.recordBalanceChange(event.getAccountId(), event.getBalanceChange());

        log.debug("Balance updated: accountId={}, change={}",
            event.getAccountId(), event.getBalanceChange());
    }

    private void processDailyBalanceSnapshot(BalanceHistoryEvent event, String correlationId) {
        balanceService.createDailyBalanceSnapshot(
            event.getAccountId(),
            event.getCurrentBalance(),
            event.getSnapshotDate(),
            correlationId
        );

        analyticsService.updateDailyBalanceTrends(
            event.getAccountId(),
            event.getCurrentBalance(),
            event.getSnapshotDate(),
            correlationId
        );

        metricsService.recordDailyBalanceSnapshot(event.getAccountId(), event.getCurrentBalance());

        log.debug("Daily balance snapshot: accountId={}, balance={}",
            event.getAccountId(), event.getCurrentBalance());
    }

    private void processMonthlyBalanceSummary(BalanceHistoryEvent event, String correlationId) {
        analyticsService.generateMonthlyBalanceSummary(
            event.getAccountId(),
            event.getMonthlyData(),
            correlationId
        );

        metricsService.recordMonthlyBalanceSummary(event.getAccountId(), event.getMonthlyData());

        log.info("Monthly balance summary processed: accountId={}", event.getAccountId());
    }

    private void processBalanceTrendAnalysis(BalanceHistoryEvent event, String correlationId) {
        analyticsService.analyzeBalanceTrends(
            event.getAccountId(),
            event.getTrendData(),
            correlationId
        );

        if (event.getTrendData().isNegativeTrend()) {
            notificationService.sendNotification(event.getUserId(), "Balance Trend Alert",
                "We've noticed a declining balance trend in your account. Consider reviewing your spending.",
                correlationId);
        }

        log.info("Balance trend analysis: accountId={}, trend={}",
            event.getAccountId(), event.getTrendData().getTrendDirection());
    }

    private void processBalanceAnomalyDetected(BalanceHistoryEvent event, String correlationId) {
        analyticsService.recordBalanceAnomaly(
            event.getAccountId(),
            event.getAnomalyDetails(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Balance Anomaly Detected",
            String.format("Account %s balance anomaly: %s",
                event.getAccountId(), event.getAnomalyDetails().getDescription()),
            "MEDIUM"
        );

        metricsService.recordBalanceAnomaly(event.getAccountId(), event.getAnomalyDetails().getSeverity());

        log.warn("Balance anomaly detected: accountId={}, anomaly={}",
            event.getAccountId(), event.getAnomalyDetails().getType());
    }

    private void processBalanceMilestoneReached(BalanceHistoryEvent event, String correlationId) {
        balanceService.recordBalanceMilestone(
            event.getAccountId(),
            event.getMilestoneType(),
            event.getCurrentBalance(),
            correlationId
        );

        notificationService.sendNotification(event.getUserId(), "Balance Milestone Reached!",
            String.format("Congratulations! You've reached a new balance milestone: %s",
                event.getMilestoneType()),
            correlationId);

        metricsService.recordBalanceMilestone(event.getAccountId(), event.getMilestoneType());

        log.info("Balance milestone reached: accountId={}, milestone={}",
            event.getAccountId(), event.getMilestoneType());
    }
}