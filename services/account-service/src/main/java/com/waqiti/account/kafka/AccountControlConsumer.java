package com.waqiti.account.kafka;

import com.waqiti.common.events.AccountControlEvent;
import com.waqiti.account.domain.AccountControlAction;
import com.waqiti.account.repository.AccountControlActionRepository;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.service.AccountControlService;
import com.waqiti.account.service.AccountSecurityService;
import com.waqiti.account.metrics.AccountMetricsService;
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
public class AccountControlConsumer {

    private final AccountControlActionRepository controlActionRepository;
    private final AccountRepository accountRepository;
    private final AccountControlService controlService;
    private final AccountSecurityService securityService;
    private final AccountMetricsService metricsService;
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
        successCounter = Counter.builder("account_control_processed_total")
            .description("Total number of successfully processed account control events")
            .register(meterRegistry);
        errorCounter = Counter.builder("account_control_errors_total")
            .description("Total number of account control processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("account_control_processing_duration")
            .description("Time taken to process account control events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"account-control", "account-control-commands", "account-access-control"},
        groupId = "account-control-service-group",
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
    @CircuitBreaker(name = "account-control", fallbackMethod = "handleAccountControlEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAccountControlEvent(
            @Payload AccountControlEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("control-%s-p%d-o%d", event.getAccountId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAccountId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing account control event: accountId={}, action={}, reason={}",
                event.getAccountId(), event.getControlAction(), event.getReason());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case CONTROL_ACTION_REQUESTED:
                    processControlActionRequest(event, correlationId);
                    break;

                case CONTROL_ACTION_AUTHORIZED:
                    authorizeControlAction(event, correlationId);
                    break;

                case CONTROL_ACTION_EXECUTED:
                    executeControlAction(event, correlationId);
                    break;

                case CONTROL_ACTION_REVERSED:
                    reverseControlAction(event, correlationId);
                    break;

                case ACCESS_RESTRICTIONS_APPLIED:
                    applyAccessRestrictions(event, correlationId);
                    break;

                case ACCESS_RESTRICTIONS_REMOVED:
                    removeAccessRestrictions(event, correlationId);
                    break;

                case EMERGENCY_LOCKDOWN:
                    performEmergencyLockdown(event, correlationId);
                    break;

                case CONTROL_OVERRIDE:
                    processControlOverride(event, correlationId);
                    break;

                default:
                    log.warn("Unknown account control event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("ACCOUNT_CONTROL_EVENT_PROCESSED", event.getAccountId(),
                Map.of("eventType", event.getEventType(), "controlAction", event.getControlAction(),
                    "reason", event.getReason(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process account control event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("account-control-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAccountControlEventFallback(
            AccountControlEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("control-fallback-%s-p%d-o%d", event.getAccountId(), partition, offset);

        log.error("Circuit breaker fallback triggered for account control: accountId={}, error={}",
            event.getAccountId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("account-control-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Account Control Circuit Breaker Triggered",
                String.format("Account %s control action failed: %s", event.getAccountId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAccountControlEvent(
            @Payload AccountControlEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-control-%s-%d", event.getAccountId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Account control permanently failed: accountId={}, topic={}, error={}",
            event.getAccountId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("ACCOUNT_CONTROL_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Account Control Dead Letter Event",
                String.format("Account %s control action sent to DLT: %s", event.getAccountId(), exceptionMessage),
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

    private void processControlActionRequest(AccountControlEvent event, String correlationId) {
        AccountControlAction action = AccountControlAction.builder()
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .controlAction(event.getControlAction())
            .reason(event.getReason())
            .requestedBy(event.getRequestedBy())
            .status("PENDING_AUTHORIZATION")
            .requestedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        controlActionRepository.save(action);

        controlService.validateControlActionEligibility(event.getAccountId(), event.getControlAction());

        // Send for authorization if required
        if (controlService.requiresAuthorization(event.getControlAction())) {
            kafkaTemplate.send("account-control-authorization", Map.of(
                "accountId", event.getAccountId(),
                "controlAction", event.getControlAction(),
                "reason", event.getReason(),
                "requestedBy", event.getRequestedBy(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            // Execute immediately for low-risk actions
            kafkaTemplate.send("account-control-commands", Map.of(
                "accountId", event.getAccountId(),
                "eventType", "CONTROL_ACTION_EXECUTED",
                "controlAction", event.getControlAction(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordControlActionRequested(event.getControlAction());

        log.info("Account control action requested: accountId={}, action={}, reason={}",
            event.getAccountId(), event.getControlAction(), event.getReason());
    }

    private void authorizeControlAction(AccountControlEvent event, String correlationId) {
        AccountControlAction action = controlActionRepository.findByAccountIdAndCorrelationId(
            event.getAccountId(), correlationId)
            .orElseThrow(() -> new RuntimeException("Control action not found"));

        action.setStatus("AUTHORIZED");
        action.setAuthorizedAt(LocalDateTime.now());
        action.setAuthorizedBy(event.getAuthorizedBy());
        controlActionRepository.save(action);

        kafkaTemplate.send("account-control-commands", Map.of(
            "accountId", event.getAccountId(),
            "eventType", "CONTROL_ACTION_EXECUTED",
            "controlAction", event.getControlAction(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordControlActionAuthorized(event.getControlAction());

        log.info("Account control action authorized: accountId={}, action={}, authorizedBy={}",
            event.getAccountId(), event.getControlAction(), event.getAuthorizedBy());
    }

    private void executeControlAction(AccountControlEvent event, String correlationId) {
        AccountControlAction action = controlActionRepository.findByAccountIdAndCorrelationId(
            event.getAccountId(), correlationId)
            .orElseThrow(() -> new RuntimeException("Control action not found"));

        action.setStatus("EXECUTED");
        action.setExecutedAt(LocalDateTime.now());
        controlActionRepository.save(action);

        // Apply the control action
        switch (event.getControlAction()) {
            case "DISABLE_CARD":
                controlService.disableCard(event.getAccountId());
                break;
            case "ENABLE_CARD":
                controlService.enableCard(event.getAccountId());
                break;
            case "BLOCK_TRANSACTIONS":
                controlService.blockTransactions(event.getAccountId());
                break;
            case "UNBLOCK_TRANSACTIONS":
                controlService.unblockTransactions(event.getAccountId());
                break;
            case "LIMIT_DAILY_SPEND":
                controlService.limitDailySpend(event.getAccountId(), event.getLimitAmount());
                break;
            case "RESET_PIN":
                controlService.resetPin(event.getAccountId());
                break;
            case "LOCK_ONLINE_ACCESS":
                controlService.lockOnlineAccess(event.getAccountId());
                break;
            case "UNLOCK_ONLINE_ACCESS":
                controlService.unlockOnlineAccess(event.getAccountId());
                break;
            default:
                log.warn("Unknown control action: {}", event.getControlAction());
                break;
        }

        notificationService.sendNotification(event.getUserId(), "Account Control Action Applied",
            String.format("A control action has been applied to your account: %s", event.getControlAction()),
            correlationId);

        kafkaTemplate.send("account-control-events", Map.of(
            "accountId", event.getAccountId(),
            "userId", event.getUserId(),
            "eventType", "CONTROL_ACTION_COMPLETED",
            "controlAction", event.getControlAction(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordControlActionExecuted(event.getControlAction());

        log.info("Account control action executed: accountId={}, action={}",
            event.getAccountId(), event.getControlAction());
    }

    private void reverseControlAction(AccountControlEvent event, String correlationId) {
        AccountControlAction action = controlActionRepository.findByAccountIdAndControlAction(
            event.getAccountId(), event.getControlAction())
            .orElseThrow(() -> new RuntimeException("Control action to reverse not found"));

        action.setStatus("REVERSED");
        action.setReversedAt(LocalDateTime.now());
        action.setReversalReason(event.getReversalReason());
        controlActionRepository.save(action);

        controlService.reverseControlAction(event.getAccountId(), event.getControlAction());

        notificationService.sendNotification(event.getUserId(), "Account Control Action Reversed",
            String.format("A previous control action has been reversed: %s", event.getControlAction()),
            correlationId);

        metricsService.recordControlActionReversed(event.getControlAction());

        log.info("Account control action reversed: accountId={}, action={}, reason={}",
            event.getAccountId(), event.getControlAction(), event.getReversalReason());
    }

    private void applyAccessRestrictions(AccountControlEvent event, String correlationId) {
        securityService.applyAccessRestrictions(event.getAccountId(), event.getRestrictions());

        notificationService.sendNotification(event.getUserId(), "Access Restrictions Applied",
            "Additional access restrictions have been applied to your account for security purposes.",
            correlationId);

        metricsService.recordAccessRestrictionsApplied();

        log.info("Access restrictions applied: accountId={}, restrictions={}",
            event.getAccountId(), event.getRestrictions());
    }

    private void removeAccessRestrictions(AccountControlEvent event, String correlationId) {
        securityService.removeAccessRestrictions(event.getAccountId(), event.getRestrictions());

        notificationService.sendNotification(event.getUserId(), "Access Restrictions Removed",
            "Access restrictions have been removed from your account.",
            correlationId);

        metricsService.recordAccessRestrictionsRemoved();

        log.info("Access restrictions removed: accountId={}, restrictions={}",
            event.getAccountId(), event.getRestrictions());
    }

    private void performEmergencyLockdown(AccountControlEvent event, String correlationId) {
        controlService.performEmergencyLockdown(event.getAccountId());

        notificationService.sendCriticalAlert(
            "Emergency Account Lockdown",
            String.format("Emergency lockdown applied to account %s", event.getAccountId()),
            Map.of("accountId", event.getAccountId(), "reason", event.getReason(), "correlationId", correlationId)
        );

        kafkaTemplate.send("security-alerts", Map.of(
            "accountId", event.getAccountId(),
            "alertType", "EMERGENCY_LOCKDOWN",
            "reason", event.getReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordEmergencyLockdown();

        log.error("Emergency lockdown performed: accountId={}, reason={}",
            event.getAccountId(), event.getReason());
    }

    private void processControlOverride(AccountControlEvent event, String correlationId) {
        controlService.processControlOverride(event.getAccountId(), event.getOverrideDetails());

        auditService.logAccountEvent("CONTROL_OVERRIDE_APPLIED", event.getAccountId(),
            Map.of("overrideDetails", event.getOverrideDetails(), "authorizedBy", event.getAuthorizedBy(),
                "correlationId", correlationId, "timestamp", Instant.now()));

        metricsService.recordControlOverride();

        log.warn("Control override applied: accountId={}, details={}, authorizedBy={}",
            event.getAccountId(), event.getOverrideDetails(), event.getAuthorizedBy());
    }
}