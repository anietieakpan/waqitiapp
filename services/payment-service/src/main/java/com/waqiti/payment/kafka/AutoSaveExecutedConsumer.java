package com.waqiti.payment.kafka;

import com.waqiti.common.events.AutoSaveExecutedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.domain.AutoSaveConfiguration;
import com.waqiti.payment.repository.AutoSaveConfigurationRepository;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.AutoSaveService;
import com.waqiti.payment.service.PaymentProcessingService;
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
public class AutoSaveExecutedConsumer {

    private final AutoSaveConfigurationRepository autoSaveConfigRepository;
    private final PaymentRepository paymentRepository;
    private final AutoSaveService autoSaveService;
    private final PaymentProcessingService paymentProcessingService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("auto_save_executed_processed_total")
            .description("Total number of successfully processed auto-save executed events")
            .register(meterRegistry);
        errorCounter = Counter.builder("auto_save_executed_errors_total")
            .description("Total number of auto-save executed processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("auto_save_executed_processing_duration")
            .description("Time taken to process auto-save executed events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"auto-save-executed", "auto-save-completion", "scheduled-save-executed"},
        groupId = "auto-save-executed-service-group",
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
    @CircuitBreaker(name = "auto-save-executed", fallbackMethod = "handleAutoSaveExecutedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAutoSaveExecutedEvent(
            @Payload AutoSaveExecutedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("autosave-%s-p%d-o%d", event.getUserId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getUserId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing auto-save executed: userId={}, amount={}, sourceAccount={}",
                event.getUserId(), event.getSaveAmount(), event.getSourceAccountId());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case AUTO_SAVE_EXECUTED:
                    processAutoSaveExecution(event, correlationId);
                    break;

                case SCHEDULED_SAVE_EXECUTED:
                    processScheduledSaveExecution(event, correlationId);
                    break;

                case ROUND_UP_SAVE_EXECUTED:
                    processRoundUpSaveExecution(event, correlationId);
                    break;

                case GOAL_BASED_SAVE_EXECUTED:
                    processGoalBasedSaveExecution(event, correlationId);
                    break;

                case SPARE_CHANGE_SAVE_EXECUTED:
                    processSpareChangeSaveExecution(event, correlationId);
                    break;

                case AUTO_SAVE_FAILED:
                    processAutoSaveFailure(event, correlationId);
                    break;

                case AUTO_SAVE_INSUFFICIENT_FUNDS:
                    processInsufficientFundsForAutoSave(event, correlationId);
                    break;

                default:
                    log.warn("Unknown auto-save executed event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("AUTO_SAVE_EXECUTED_EVENT_PROCESSED", event.getUserId(),
                Map.of("eventType", event.getEventType(), "saveAmount", event.getSaveAmount(),
                    "sourceAccount", event.getSourceAccountId(), "targetAccount", event.getTargetAccountId(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process auto-save executed event: {}", e.getMessage(), e);

            // Send to DLQ with context
            dlqHandler.handleFailedMessage(
                "auto-save-executed",
                event,
                e,
                Map.of(
                    "userId", event.getUserId(),
                    "eventType", event.getEventType().toString(),
                    "saveAmount", event.getSaveAmount().toString(),
                    "autoSaveConfigId", event.getAutoSaveConfigId() != null ? event.getAutoSaveConfigId() : "unknown",
                    "correlationId", correlationId,
                    "partition", String.valueOf(partition),
                    "offset", String.valueOf(offset)
                )
            );

            // Send fallback event
            kafkaTemplate.send("auto-save-executed-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleAutoSaveExecutedEventFallback(
            AutoSaveExecutedEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("autosave-fallback-%s-p%d-o%d", event.getUserId(), partition, offset);

        log.error("Circuit breaker fallback triggered for auto-save executed: userId={}, error={}",
            event.getUserId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("auto-save-executed-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Auto-Save Executed Circuit Breaker Triggered",
                String.format("User %s auto-save execution failed: %s", event.getUserId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAutoSaveExecutedEvent(
            @Payload AutoSaveExecutedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-autosave-%s-%d", event.getUserId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Auto-save executed permanently failed: userId={}, topic={}, error={}",
            event.getUserId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("AUTO_SAVE_EXECUTED_DLT_EVENT", event.getUserId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Auto-Save Executed Dead Letter Event",
                String.format("User %s auto-save execution sent to DLT: %s", event.getUserId(), exceptionMessage),
                Map.of("userId", event.getUserId(), "topic", topic, "correlationId", correlationId)
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

    private void processAutoSaveExecution(AutoSaveExecutedEvent event, String correlationId) {
        // Update auto-save configuration last execution time
        autoSaveService.updateLastExecutionTime(event.getUserId(), event.getAutoSaveConfigId());

        // Record successful auto-save execution
        autoSaveService.recordSuccessfulExecution(
            event.getUserId(),
            event.getAutoSaveConfigId(),
            event.getSaveAmount(),
            event.getSourceAccountId(),
            event.getTargetAccountId(),
            correlationId
        );

        // Update savings goal progress if applicable
        if (event.getSavingsGoalId() != null) {
            autoSaveService.updateSavingsGoalProgress(event.getSavingsGoalId(), event.getSaveAmount());
        }

        // Send success notification to user
        notificationService.sendNotification(event.getUserId(), "Auto-Save Completed",
            String.format("Successfully saved $%.2f from your %s account to your savings.",
                event.getSaveAmount(), event.getSourceAccountId()),
            correlationId);

        // Record metrics
        metricsService.recordAutoSaveExecution(event.getSaveAmount(), event.getAutoSaveType());

        log.info("Auto-save executed successfully: userId={}, amount={}",
            event.getUserId(), event.getSaveAmount());
    }

    private void processScheduledSaveExecution(AutoSaveExecutedEvent event, String correlationId) {
        // Update scheduled save configuration
        autoSaveService.updateScheduledSaveExecution(event.getUserId(), event.getAutoSaveConfigId());

        // Process the scheduled transfer
        autoSaveService.processScheduledTransfer(
            event.getUserId(),
            event.getSaveAmount(),
            event.getSourceAccountId(),
            event.getTargetAccountId(),
            correlationId
        );

        // Schedule next occurrence
        autoSaveService.scheduleNextAutoSave(event.getUserId(), event.getAutoSaveConfigId());

        // Send notification
        notificationService.sendNotification(event.getUserId(), "Scheduled Save Completed",
            String.format("Your scheduled save of $%.2f has been processed successfully.",
                event.getSaveAmount()),
            correlationId);

        metricsService.recordScheduledSaveExecution(event.getSaveAmount());

        log.info("Scheduled save executed: userId={}, amount={}",
            event.getUserId(), event.getSaveAmount());
    }

    private void processRoundUpSaveExecution(AutoSaveExecutedEvent event, String correlationId) {
        // Record round-up save execution
        autoSaveService.recordRoundUpSave(
            event.getUserId(),
            event.getOriginalTransactionId(),
            event.getOriginalAmount(),
            event.getSaveAmount(),
            event.getTargetAccountId(),
            correlationId
        );

        // Update round-up configuration
        autoSaveService.updateRoundUpStats(event.getUserId(), event.getSaveAmount());

        // Update savings goal if applicable
        if (event.getSavingsGoalId() != null) {
            autoSaveService.updateSavingsGoalProgress(event.getSavingsGoalId(), event.getSaveAmount());
        }

        metricsService.recordRoundUpSaveExecution(event.getSaveAmount());

        log.info("Round-up save executed: userId={}, roundUpAmount={}",
            event.getUserId(), event.getSaveAmount());
    }

    private void processGoalBasedSaveExecution(AutoSaveExecutedEvent event, String correlationId) {
        // Update savings goal progress
        autoSaveService.updateSavingsGoalProgress(event.getSavingsGoalId(), event.getSaveAmount());

        // Check if goal is achieved
        boolean goalAchieved = autoSaveService.checkGoalAchievement(event.getSavingsGoalId());

        if (goalAchieved) {
            // Send goal achievement notification
            notificationService.sendNotification(event.getUserId(), "Savings Goal Achieved!",
                "Congratulations! You've reached your savings goal.",
                correlationId);

            // Send celebration event
            kafkaTemplate.send("savings-goal-achieved", Map.of(
                "userId", event.getUserId(),
                "goalId", event.getSavingsGoalId(),
                "totalSaved", event.getSaveAmount(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordGoalBasedSaveExecution(event.getSaveAmount());

        log.info("Goal-based save executed: userId={}, goalId={}, amount={}",
            event.getUserId(), event.getSavingsGoalId(), event.getSaveAmount());
    }

    private void processSpareChangeSaveExecution(AutoSaveExecutedEvent event, String correlationId) {
        // Record spare change save
        autoSaveService.recordSpareChangeSave(
            event.getUserId(),
            event.getOriginalTransactionId(),
            event.getSaveAmount(),
            event.getTargetAccountId(),
            correlationId
        );

        // Update spare change configuration stats
        autoSaveService.updateSpareChangeStats(event.getUserId(), event.getSaveAmount());

        metricsService.recordSpareChangeSaveExecution(event.getSaveAmount());

        log.info("Spare change save executed: userId={}, amount={}",
            event.getUserId(), event.getSaveAmount());
    }

    private void processAutoSaveFailure(AutoSaveExecutedEvent event, String correlationId) {
        // Record auto-save failure
        autoSaveService.recordAutoSaveFailure(
            event.getUserId(),
            event.getAutoSaveConfigId(),
            event.getFailureReason(),
            event.getSaveAmount(),
            correlationId
        );

        // Update failure metrics
        metricsService.recordAutoSaveFailure(event.getFailureReason());

        // Send failure notification
        notificationService.sendNotification(event.getUserId(), "Auto-Save Failed",
            String.format("Your auto-save of $%.2f could not be processed: %s",
                event.getSaveAmount(), event.getFailureReason()),
            correlationId);

        // Check if we need to disable auto-save due to repeated failures
        autoSaveService.checkAndHandleRepeatedFailures(event.getUserId(), event.getAutoSaveConfigId());

        log.warn("Auto-save failed: userId={}, reason={}",
            event.getUserId(), event.getFailureReason());
    }

    private void processInsufficientFundsForAutoSave(AutoSaveExecutedEvent event, String correlationId) {
        // Record insufficient funds failure
        autoSaveService.recordInsufficientFundsFailure(
            event.getUserId(),
            event.getAutoSaveConfigId(),
            event.getSaveAmount(),
            event.getAvailableBalance(),
            correlationId
        );

        // Update insufficient funds metrics
        metricsService.recordAutoSaveInsufficientFunds();

        // Send notification with suggestion
        notificationService.sendNotification(event.getUserId(), "Auto-Save Insufficient Funds",
            String.format("Your auto-save of $%.2f could not be processed due to insufficient funds. " +
                "Available balance: $%.2f. Consider adjusting your auto-save amount.",
                event.getSaveAmount(), event.getAvailableBalance()),
            correlationId);

        // Suggest auto-save amount adjustment
        autoSaveService.suggestAutoSaveAdjustment(event.getUserId(), event.getAutoSaveConfigId(),
            event.getAvailableBalance());

        log.warn("Auto-save insufficient funds: userId={}, requested={}, available={}",
            event.getUserId(), event.getSaveAmount(), event.getAvailableBalance());
    }
}