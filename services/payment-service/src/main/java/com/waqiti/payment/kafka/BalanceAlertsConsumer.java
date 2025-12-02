package com.waqiti.payment.kafka;

import com.waqiti.common.events.BalanceAlertEvent;
import com.waqiti.payment.domain.BalanceAlert;
import com.waqiti.payment.repository.BalanceAlertRepository;
import com.waqiti.payment.service.BalanceService;
import com.waqiti.payment.service.AlertService;
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
import jakarta.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class BalanceAlertsConsumer {

    private final BalanceAlertRepository balanceAlertRepository;
    private final BalanceService balanceService;
    private final AlertService alertService;
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
        successCounter = Counter.builder("balance_alerts_processed_total")
            .description("Total number of successfully processed balance alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("balance_alerts_errors_total")
            .description("Total number of balance alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("balance_alerts_processing_duration")
            .description("Time taken to process balance alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"balance-alerts", "low-balance-alerts", "negative-balance-alerts"},
        groupId = "balance-alerts-service-group",
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
    @CircuitBreaker(name = "balance-alerts", fallbackMethod = "handleBalanceAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBalanceAlertEvent(
            @Payload BalanceAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("balance-alert-%s-p%d-o%d", event.getAccountId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAccountId(), event.getAlertType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing balance alert: accountId={}, type={}, balance={}",
                event.getAccountId(), event.getAlertType(), event.getCurrentBalance());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getAlertType()) {
                case LOW_BALANCE_WARNING:
                    processLowBalanceWarning(event, correlationId);
                    break;

                case LOW_BALANCE_CRITICAL:
                    processLowBalanceCritical(event, correlationId);
                    break;

                case NEGATIVE_BALANCE:
                    processNegativeBalance(event, correlationId);
                    break;

                case BALANCE_THRESHOLD_EXCEEDED:
                    processBalanceThresholdExceeded(event, correlationId);
                    break;

                case INSUFFICIENT_FUNDS:
                    processInsufficientFunds(event, correlationId);
                    break;

                case BALANCE_RECONCILIATION_MISMATCH:
                    processBalanceReconciliationMismatch(event, correlationId);
                    break;

                case UNUSUAL_BALANCE_CHANGE:
                    processUnusualBalanceChange(event, correlationId);
                    break;

                case BALANCE_FREEZE_REQUIRED:
                    processBalanceFreezeRequired(event, correlationId);
                    break;

                case BALANCE_RESTORED:
                    processBalanceRestored(event, correlationId);
                    break;

                default:
                    log.warn("Unknown balance alert type: {}", event.getAlertType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("BALANCE_ALERT_EVENT_PROCESSED", event.getAccountId(),
                Map.of("alertType", event.getAlertType(), "currentBalance", event.getCurrentBalance(),
                    "threshold", event.getThreshold(), "severity", event.getSeverity(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process balance alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("balance-alerts-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBalanceAlertEventFallback(
            BalanceAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("balance-alert-fallback-%s-p%d-o%d", event.getAccountId(), partition, offset);

        log.error("Circuit breaker fallback triggered for balance alert: accountId={}, error={}",
            event.getAccountId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("balance-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Balance Alert Circuit Breaker Triggered",
                String.format("Account %s balance alert processing failed: %s", event.getAccountId(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBalanceAlertEvent(
            @Payload BalanceAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-balance-alert-%s-%d", event.getAccountId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Balance alert permanently failed: accountId={}, topic={}, error={}",
            event.getAccountId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("BALANCE_ALERT_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "alertType", event.getAlertType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Balance Alert Dead Letter Event",
                String.format("Account %s balance alert sent to DLT: %s", event.getAccountId(), exceptionMessage),
                Map.of("accountId", event.getAccountId(), "topic", topic, "correlationId", correlationId)
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

    private void processLowBalanceWarning(BalanceAlertEvent event, String correlationId) {
        // Create balance alert record
        BalanceAlert alert = BalanceAlert.builder()
            .accountId(event.getAccountId())
            .alertType("LOW_BALANCE_WARNING")
            .currentBalance(event.getCurrentBalance())
            .threshold(event.getThreshold())
            .severity("MEDIUM")
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceAlertRepository.save(alert);

        // Send low balance notification to user
        notificationService.sendNotification(event.getUserId(), "Low Balance Warning",
            String.format("Your account balance is running low: $%.2f. Please consider adding funds.",
                event.getCurrentBalance()),
            correlationId);

        // Check if user has auto-reload enabled
        if (balanceService.hasAutoReloadEnabled(event.getAccountId())) {
            balanceService.triggerAutoReload(event.getAccountId(), correlationId);
        }

        // Record metrics
        metricsService.recordBalanceAlert("LOW_BALANCE_WARNING", event.getCurrentBalance());

        log.warn("Low balance warning: accountId={}, balance=${}, threshold=${}",
            event.getAccountId(), event.getCurrentBalance(), event.getThreshold());
    }

    private void processLowBalanceCritical(BalanceAlertEvent event, String correlationId) {
        // Create critical balance alert
        BalanceAlert alert = BalanceAlert.builder()
            .accountId(event.getAccountId())
            .alertType("LOW_BALANCE_CRITICAL")
            .currentBalance(event.getCurrentBalance())
            .threshold(event.getThreshold())
            .severity("HIGH")
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceAlertRepository.save(alert);

        // Send critical balance notification
        notificationService.sendUrgentNotification(event.getUserId(), "Critical Low Balance",
            String.format("URGENT: Your account balance is critically low: $%.2f. " +
                "Immediate action required to avoid service interruption.",
                event.getCurrentBalance()),
            correlationId);

        // Trigger immediate auto-reload if available
        if (balanceService.hasAutoReloadEnabled(event.getAccountId())) {
            balanceService.triggerEmergencyAutoReload(event.getAccountId(), correlationId);
        }

        // Check if account should be restricted
        boolean shouldRestrict = balanceService.shouldRestrictAccount(event.getAccountId(), event.getCurrentBalance());
        if (shouldRestrict) {
            balanceService.restrictAccountForLowBalance(event.getAccountId(), correlationId);
        }

        // Record metrics
        metricsService.recordBalanceAlert("LOW_BALANCE_CRITICAL", event.getCurrentBalance());

        log.error("Critical low balance: accountId={}, balance=${}, restricted={}",
            event.getAccountId(), event.getCurrentBalance(), shouldRestrict);
    }

    private void processNegativeBalance(BalanceAlertEvent event, String correlationId) {
        // Create negative balance alert
        BalanceAlert alert = BalanceAlert.builder()
            .accountId(event.getAccountId())
            .alertType("NEGATIVE_BALANCE")
            .currentBalance(event.getCurrentBalance())
            .threshold(0.0)
            .severity("CRITICAL")
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceAlertRepository.save(alert);

        // Immediately freeze account for negative balance
        balanceService.freezeAccountForNegativeBalance(event.getAccountId(), correlationId);

        // Send critical notification
        notificationService.sendCriticalNotification(event.getUserId(), "Account Suspended - Negative Balance",
            String.format("Your account has been suspended due to negative balance: $%.2f. " +
                "Please contact support immediately.",
                event.getCurrentBalance()),
            correlationId);

        // Send operational alert
        notificationService.sendOperationalAlert(
            "Negative Balance Detected",
            String.format("Account %s has negative balance: $%.2f", event.getAccountId(), event.getCurrentBalance()),
            "CRITICAL"
        );

        // Start overdraft recovery process
        balanceService.initiateOverdraftRecovery(event.getAccountId(), Math.abs(event.getCurrentBalance()), correlationId);

        // Record metrics
        metricsService.recordBalanceAlert("NEGATIVE_BALANCE", event.getCurrentBalance());

        log.error("Negative balance detected: accountId={}, balance=${}",
            event.getAccountId(), event.getCurrentBalance());
    }

    private void processBalanceThresholdExceeded(BalanceAlertEvent event, String correlationId) {
        // Create threshold exceeded alert
        BalanceAlert alert = BalanceAlert.builder()
            .accountId(event.getAccountId())
            .alertType("BALANCE_THRESHOLD_EXCEEDED")
            .currentBalance(event.getCurrentBalance())
            .threshold(event.getThreshold())
            .severity("INFO")
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceAlertRepository.save(alert);

        // Send positive balance notification
        notificationService.sendNotification(event.getUserId(), "Balance Threshold Reached",
            String.format("Your account balance has reached your target threshold: $%.2f",
                event.getCurrentBalance()),
            correlationId);

        // Check for savings goals
        if (balanceService.hasSavingsGoals(event.getAccountId())) {
            balanceService.checkSavingsGoalOpportunities(event.getAccountId(), event.getCurrentBalance(), correlationId);
        }

        // Record metrics
        metricsService.recordBalanceAlert("BALANCE_THRESHOLD_EXCEEDED", event.getCurrentBalance());

        log.info("Balance threshold exceeded: accountId={}, balance=${}, threshold=${}",
            event.getAccountId(), event.getCurrentBalance(), event.getThreshold());
    }

    private void processInsufficientFunds(BalanceAlertEvent event, String correlationId) {
        // Create insufficient funds alert
        BalanceAlert alert = BalanceAlert.builder()
            .accountId(event.getAccountId())
            .alertType("INSUFFICIENT_FUNDS")
            .currentBalance(event.getCurrentBalance())
            .threshold(event.getRequestedAmount())
            .severity("HIGH")
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceAlertRepository.save(alert);

        // Send insufficient funds notification
        notificationService.sendNotification(event.getUserId(), "Transaction Declined - Insufficient Funds",
            String.format("Transaction declined. Available balance: $%.2f, Requested: $%.2f",
                event.getCurrentBalance(), event.getRequestedAmount()),
            correlationId);

        // Check for overdraft protection
        if (balanceService.hasOverdraftProtection(event.getAccountId())) {
            balanceService.processOverdraftProtection(event.getAccountId(), event.getRequestedAmount(), correlationId);
        }

        // Suggest auto-reload setup
        alertService.suggestAutoReloadSetup(event.getUserId(), event.getAccountId(), correlationId);

        // Record metrics
        metricsService.recordInsufficientFundsAlert(event.getCurrentBalance(), event.getRequestedAmount());

        log.warn("Insufficient funds: accountId={}, balance=${}, requested=${}",
            event.getAccountId(), event.getCurrentBalance(), event.getRequestedAmount());
    }

    private void processBalanceReconciliationMismatch(BalanceAlertEvent event, String correlationId) {
        // Create reconciliation mismatch alert
        BalanceAlert alert = BalanceAlert.builder()
            .accountId(event.getAccountId())
            .alertType("BALANCE_RECONCILIATION_MISMATCH")
            .currentBalance(event.getCurrentBalance())
            .threshold(event.getExpectedBalance())
            .severity("CRITICAL")
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceAlertRepository.save(alert);

        // Initiate reconciliation investigation
        balanceService.initiateBalanceReconciliation(
            event.getAccountId(),
            event.getCurrentBalance(),
            event.getExpectedBalance(),
            correlationId
        );

        // Send critical operational alert
        notificationService.sendCriticalAlert(
            "Balance Reconciliation Mismatch",
            String.format("Account %s balance mismatch: Current=$%.2f, Expected=$%.2f",
                event.getAccountId(), event.getCurrentBalance(), event.getExpectedBalance()),
            Map.of("accountId", event.getAccountId(), "variance",
                event.getCurrentBalance() - event.getExpectedBalance(), "correlationId", correlationId)
        );

        // Freeze account pending investigation
        balanceService.freezeAccountForInvestigation(event.getAccountId(), "Balance reconciliation mismatch", correlationId);

        // Record metrics
        metricsService.recordReconciliationMismatch(event.getCurrentBalance(), event.getExpectedBalance());

        log.error("Balance reconciliation mismatch: accountId={}, current=${}, expected=${}",
            event.getAccountId(), event.getCurrentBalance(), event.getExpectedBalance());
    }

    private void processUnusualBalanceChange(BalanceAlertEvent event, String correlationId) {
        // Create unusual balance change alert
        BalanceAlert alert = BalanceAlert.builder()
            .accountId(event.getAccountId())
            .alertType("UNUSUAL_BALANCE_CHANGE")
            .currentBalance(event.getCurrentBalance())
            .threshold(event.getPreviousBalance())
            .severity("MEDIUM")
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceAlertRepository.save(alert);

        // Analyze the unusual change
        double changeAmount = event.getCurrentBalance() - event.getPreviousBalance();
        double changePercentage = Math.abs(changeAmount) / Math.abs(event.getPreviousBalance()) * 100;

        // Send notification for significant changes
        if (changePercentage > 50) {
            notificationService.sendNotification(event.getUserId(), "Unusual Account Activity",
                String.format("Large balance change detected: %+$.2f (%.1f%%). " +
                    "If this wasn't expected, please contact support.",
                    changeAmount, changePercentage),
                correlationId);
        }

        // Trigger fraud detection if change is suspicious
        if (changePercentage > 90 || Math.abs(changeAmount) > 10000) {
            alertService.triggerFraudInvestigation(event.getAccountId(), changeAmount, correlationId);
        }

        // Record metrics
        metricsService.recordUnusualBalanceChange(event.getAccountId(), changeAmount, changePercentage);

        log.warn("Unusual balance change: accountId={}, change=${} ({}%)",
            event.getAccountId(), changeAmount, changePercentage);
    }

    private void processBalanceFreezeRequired(BalanceAlertEvent event, String correlationId) {
        // Create balance freeze alert
        BalanceAlert alert = BalanceAlert.builder()
            .accountId(event.getAccountId())
            .alertType("BALANCE_FREEZE_REQUIRED")
            .currentBalance(event.getCurrentBalance())
            .threshold(0.0)
            .severity("CRITICAL")
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceAlertRepository.save(alert);

        // Immediately freeze the account
        balanceService.freezeAccount(event.getAccountId(), event.getFreezeReason(), correlationId);

        // Send freeze notification to user
        notificationService.sendCriticalNotification(event.getUserId(), "Account Temporarily Frozen",
            String.format("Your account has been temporarily frozen for security reasons: %s. " +
                "Please contact support for assistance.",
                event.getFreezeReason()),
            correlationId);

        // Send operational alert
        notificationService.sendOperationalAlert(
            "Account Frozen",
            String.format("Account %s has been frozen: %s", event.getAccountId(), event.getFreezeReason()),
            "HIGH"
        );

        // Record metrics
        metricsService.recordAccountFrozen(event.getAccountId(), event.getFreezeReason());

        log.error("Account frozen: accountId={}, reason={}",
            event.getAccountId(), event.getFreezeReason());
    }

    private void processBalanceRestored(BalanceAlertEvent event, String correlationId) {
        // Create balance restored alert
        BalanceAlert alert = BalanceAlert.builder()
            .accountId(event.getAccountId())
            .alertType("BALANCE_RESTORED")
            .currentBalance(event.getCurrentBalance())
            .threshold(event.getThreshold())
            .severity("INFO")
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceAlertRepository.save(alert);

        // Unfreeze account if it was frozen for low balance
        if (balanceService.isFrozenForLowBalance(event.getAccountId())) {
            balanceService.unfreezeAccount(event.getAccountId(), "Balance restored", correlationId);
        }

        // Send positive notification
        notificationService.sendNotification(event.getUserId(), "Balance Restored",
            String.format("Your account balance has been restored to $%.2f. Normal service has resumed.",
                event.getCurrentBalance()),
            correlationId);

        // Resume any suspended services
        balanceService.resumeSuspendedServices(event.getAccountId(), correlationId);

        // Record metrics
        metricsService.recordBalanceRestored(event.getAccountId(), event.getCurrentBalance());

        log.info("Balance restored: accountId={}, balance=${}",
            event.getAccountId(), event.getCurrentBalance());
    }
}