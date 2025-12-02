package com.waqiti.account.kafka;

import com.waqiti.common.events.AccountSuspensionEvent;
import com.waqiti.account.domain.AccountSuspension;
import com.waqiti.account.repository.AccountSuspensionRepository;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.service.AccountSuspensionService;
import com.waqiti.account.service.ComplianceService;
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
public class AccountSuspensionsConsumer {

    private final AccountSuspensionRepository suspensionRepository;
    private final AccountRepository accountRepository;
    private final AccountSuspensionService suspensionService;
    private final ComplianceService complianceService;
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
        successCounter = Counter.builder("account_suspensions_processed_total")
            .description("Total number of successfully processed account suspension events")
            .register(meterRegistry);
        errorCounter = Counter.builder("account_suspensions_errors_total")
            .description("Total number of account suspension processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("account_suspensions_processing_duration")
            .description("Time taken to process account suspension events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"account-suspensions", "account-suspension-events", "account-temporary-suspensions"},
        groupId = "account-suspensions-service-group",
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
    @CircuitBreaker(name = "account-suspensions", fallbackMethod = "handleAccountSuspensionEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAccountSuspensionEvent(
            @Payload AccountSuspensionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("suspension-%s-p%d-o%d", event.getAccountId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAccountId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing account suspension event: accountId={}, suspensionType={}, reason={}",
                event.getAccountId(), event.getSuspensionType(), event.getReason());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case SUSPENSION_INITIATED:
                    initiateSuspension(event, correlationId);
                    break;

                case SUSPENSION_APPROVED:
                    approveSuspension(event, correlationId);
                    break;

                case SUSPENSION_REJECTED:
                    rejectSuspension(event, correlationId);
                    break;

                case SUSPENSION_ACTIVATED:
                    activateSuspension(event, correlationId);
                    break;

                case SUSPENSION_EXTENDED:
                    extendSuspension(event, correlationId);
                    break;

                case SUSPENSION_LIFTED:
                    liftSuspension(event, correlationId);
                    break;

                case TEMPORARY_SUSPENSION_EXPIRED:
                    handleTemporarySuspensionExpiry(event, correlationId);
                    break;

                case EMERGENCY_SUSPENSION:
                    processEmergencySuspension(event, correlationId);
                    break;

                case SUSPENSION_APPEAL_SUBMITTED:
                    processSuspensionAppeal(event, correlationId);
                    break;

                case SUSPENSION_REVIEWED:
                    reviewSuspension(event, correlationId);
                    break;

                default:
                    log.warn("Unknown account suspension event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("ACCOUNT_SUSPENSION_EVENT_PROCESSED", event.getAccountId(),
                Map.of("eventType", event.getEventType(), "suspensionType", event.getSuspensionType(),
                    "reason", event.getReason(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process account suspension event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("account-suspension-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAccountSuspensionEventFallback(
            AccountSuspensionEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("suspension-fallback-%s-p%d-o%d", event.getAccountId(), partition, offset);

        log.error("Circuit breaker fallback triggered for account suspension: accountId={}, error={}",
            event.getAccountId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("account-suspensions-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Account Suspension Circuit Breaker Triggered",
                String.format("Account %s suspension processing failed: %s", event.getAccountId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAccountSuspensionEvent(
            @Payload AccountSuspensionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-suspension-%s-%d", event.getAccountId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Account suspension permanently failed: accountId={}, topic={}, error={}",
            event.getAccountId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("ACCOUNT_SUSPENSION_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Account Suspension Dead Letter Event",
                String.format("Account %s suspension processing sent to DLT: %s", event.getAccountId(), exceptionMessage),
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

    private void initiateSuspension(AccountSuspensionEvent event, String correlationId) {
        AccountSuspension suspension = AccountSuspension.builder()
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .suspensionType(event.getSuspensionType())
            .reason(event.getReason())
            .initiatedBy(event.getInitiatedBy())
            .initiatedAt(LocalDateTime.now())
            .status("PENDING_APPROVAL")
            .severity(event.getSeverity())
            .plannedDuration(event.getPlannedDuration())
            .correlationId(correlationId)
            .build();
        suspensionRepository.save(suspension);

        // Check if immediate suspension is required
        if (suspensionService.requiresImmediateSuspension(event.getSuspensionType(), event.getSeverity())) {
            kafkaTemplate.send("account-suspensions", Map.of(
                "accountId", event.getAccountId(),
                "eventType", "EMERGENCY_SUSPENSION",
                "suspensionType", event.getSuspensionType(),
                "reason", event.getReason(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            // Send for approval
            kafkaTemplate.send("suspension-approval-queue", Map.of(
                "accountId", event.getAccountId(),
                "suspensionType", event.getSuspensionType(),
                "reason", event.getReason(),
                "severity", event.getSeverity(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        notificationService.sendNotification(event.getUserId(), "Account Suspension Initiated",
            "A suspension request has been initiated for your account and is under review.",
            correlationId);

        metricsService.recordSuspensionInitiated(event.getSuspensionType(), event.getSeverity());

        log.info("Account suspension initiated: accountId={}, type={}, severity={}",
            event.getAccountId(), event.getSuspensionType(), event.getSeverity());
    }

    private void approveSuspension(AccountSuspensionEvent event, String correlationId) {
        AccountSuspension suspension = suspensionRepository.findByAccountIdAndCorrelationId(
            event.getAccountId(), correlationId)
            .orElseThrow(() -> new RuntimeException("Suspension request not found"));

        suspension.setStatus("APPROVED");
        suspension.setApprovedAt(LocalDateTime.now());
        suspension.setApprovedBy(event.getApprovedBy());
        suspension.setApprovalNotes(event.getApprovalNotes());
        suspensionRepository.save(suspension);

        kafkaTemplate.send("account-suspensions", Map.of(
            "accountId", event.getAccountId(),
            "eventType", "SUSPENSION_ACTIVATED",
            "suspensionType", event.getSuspensionType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordSuspensionApproved(event.getSuspensionType());

        log.info("Account suspension approved: accountId={}, approvedBy={}",
            event.getAccountId(), event.getApprovedBy());
    }

    private void rejectSuspension(AccountSuspensionEvent event, String correlationId) {
        AccountSuspension suspension = suspensionRepository.findByAccountIdAndCorrelationId(
            event.getAccountId(), correlationId)
            .orElseThrow(() -> new RuntimeException("Suspension request not found"));

        suspension.setStatus("REJECTED");
        suspension.setRejectedAt(LocalDateTime.now());
        suspension.setRejectedBy(event.getRejectedBy());
        suspension.setRejectionReason(event.getRejectionReason());
        suspensionRepository.save(suspension);

        notificationService.sendNotification(event.getUserId(), "Account Suspension Not Approved",
            String.format("The suspension request for your account has been rejected. Reason: %s",
                event.getRejectionReason()),
            correlationId);

        metricsService.recordSuspensionRejected(event.getSuspensionType());

        log.info("Account suspension rejected: accountId={}, reason={}",
            event.getAccountId(), event.getRejectionReason());
    }

    private void activateSuspension(AccountSuspensionEvent event, String correlationId) {
        AccountSuspension suspension = suspensionRepository.findByAccountIdAndCorrelationId(
            event.getAccountId(), correlationId)
            .orElseThrow(() -> new RuntimeException("Suspension request not found"));

        suspension.setStatus("ACTIVE");
        suspension.setActivatedAt(LocalDateTime.now());
        suspension.setExpiresAt(calculateExpiryDate(suspension.getPlannedDuration()));
        suspensionRepository.save(suspension);

        // Update account status
        var account = accountRepository.findById(event.getAccountId()).orElseThrow();
        account.setStatus("SUSPENDED");
        account.setSuspensionReason(event.getReason());
        account.setSuspendedAt(LocalDateTime.now());
        accountRepository.save(account);

        // Apply suspension restrictions
        suspensionService.applySuspensionRestrictions(event.getAccountId(), event.getSuspensionType());

        notificationService.sendNotification(event.getUserId(), "Account Suspended",
            String.format("Your account has been suspended. Reason: %s. Duration: %s",
                event.getReason(), formatDuration(suspension.getPlannedDuration())),
            correlationId);

        kafkaTemplate.send("account-status-changes", Map.of(
            "accountId", event.getAccountId(),
            "userId", event.getUserId(),
            "statusChange", "SUSPENDED",
            "suspensionType", event.getSuspensionType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule suspension expiry if temporary
        if (suspension.getExpiresAt() != null) {
            kafkaTemplate.send("scheduled-events", Map.of(
                "accountId", event.getAccountId(),
                "eventType", "SUSPENSION_EXPIRY",
                "scheduledTime", suspension.getExpiresAt(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordSuspensionActivated(event.getSuspensionType());

        log.warn("Account suspension activated: accountId={}, type={}, expiresAt={}",
            event.getAccountId(), event.getSuspensionType(), suspension.getExpiresAt());
    }

    private void extendSuspension(AccountSuspensionEvent event, String correlationId) {
        AccountSuspension suspension = suspensionRepository.findActiveSuspensionByAccountId(event.getAccountId())
            .orElseThrow(() -> new RuntimeException("Active suspension not found"));

        suspension.setExtendedAt(LocalDateTime.now());
        suspension.setExtensionReason(event.getExtensionReason());
        suspension.setExtendedBy(event.getExtendedBy());
        suspension.setExpiresAt(calculateExtendedExpiryDate(suspension.getExpiresAt(), event.getExtensionDuration()));
        suspensionRepository.save(suspension);

        notificationService.sendNotification(event.getUserId(), "Account Suspension Extended",
            String.format("Your account suspension has been extended. Reason: %s. New expiry: %s",
                event.getExtensionReason(), suspension.getExpiresAt()),
            correlationId);

        metricsService.recordSuspensionExtended(event.getSuspensionType());

        log.warn("Account suspension extended: accountId={}, newExpiryDate={}, reason={}",
            event.getAccountId(), suspension.getExpiresAt(), event.getExtensionReason());
    }

    private void liftSuspension(AccountSuspensionEvent event, String correlationId) {
        AccountSuspension suspension = suspensionRepository.findActiveSuspensionByAccountId(event.getAccountId())
            .orElseThrow(() -> new RuntimeException("Active suspension not found"));

        suspension.setStatus("LIFTED");
        suspension.setLiftedAt(LocalDateTime.now());
        suspension.setLiftedBy(event.getLiftedBy());
        suspension.setLiftReason(event.getLiftReason());
        suspensionRepository.save(suspension);

        // Restore account status
        var account = accountRepository.findById(event.getAccountId()).orElseThrow();
        account.setStatus("ACTIVE");
        account.setSuspensionReason(null);
        account.setSuspendedAt(null);
        accountRepository.save(account);

        // Remove suspension restrictions
        suspensionService.removeSuspensionRestrictions(event.getAccountId());

        notificationService.sendNotification(event.getUserId(), "Account Suspension Lifted",
            "Your account suspension has been lifted. You can now access all account features.",
            correlationId);

        kafkaTemplate.send("account-status-changes", Map.of(
            "accountId", event.getAccountId(),
            "userId", event.getUserId(),
            "statusChange", "SUSPENSION_LIFTED",
            "liftReason", event.getLiftReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordSuspensionLifted(suspension.getSuspensionType());

        log.info("Account suspension lifted: accountId={}, liftedBy={}, reason={}",
            event.getAccountId(), event.getLiftedBy(), event.getLiftReason());
    }

    private void handleTemporarySuspensionExpiry(AccountSuspensionEvent event, String correlationId) {
        AccountSuspension suspension = suspensionRepository.findActiveSuspensionByAccountId(event.getAccountId())
            .orElseThrow(() -> new RuntimeException("Active suspension not found"));

        suspension.setStatus("EXPIRED");
        suspension.setExpiredAt(LocalDateTime.now());
        suspensionRepository.save(suspension);

        // Restore account status
        var account = accountRepository.findById(event.getAccountId()).orElseThrow();
        account.setStatus("ACTIVE");
        account.setSuspensionReason(null);
        account.setSuspendedAt(null);
        accountRepository.save(account);

        // Remove suspension restrictions
        suspensionService.removeSuspensionRestrictions(event.getAccountId());

        notificationService.sendNotification(event.getUserId(), "Account Suspension Expired",
            "Your temporary account suspension has expired. You can now access all account features.",
            correlationId);

        metricsService.recordSuspensionExpired(suspension.getSuspensionType());

        log.info("Temporary suspension expired: accountId={}, type={}",
            event.getAccountId(), suspension.getSuspensionType());
    }

    private void processEmergencySuspension(AccountSuspensionEvent event, String correlationId) {
        AccountSuspension suspension = AccountSuspension.builder()
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .suspensionType("EMERGENCY")
            .reason(event.getReason())
            .initiatedBy(event.getInitiatedBy())
            .initiatedAt(LocalDateTime.now())
            .activatedAt(LocalDateTime.now())
            .status("ACTIVE")
            .severity("CRITICAL")
            .isEmergency(true)
            .correlationId(correlationId)
            .build();
        suspensionRepository.save(suspension);

        // Immediately suspend account
        var account = accountRepository.findById(event.getAccountId()).orElseThrow();
        account.setStatus("SUSPENDED");
        account.setSuspensionReason(event.getReason());
        account.setSuspendedAt(LocalDateTime.now());
        accountRepository.save(account);

        suspensionService.applySuspensionRestrictions(event.getAccountId(), "EMERGENCY");

        notificationService.sendCriticalAlert(
            "Emergency Account Suspension",
            String.format("Emergency suspension applied to account %s: %s", event.getAccountId(), event.getReason()),
            Map.of("accountId", event.getAccountId(), "reason", event.getReason(), "correlationId", correlationId)
        );

        kafkaTemplate.send("security-alerts", Map.of(
            "accountId", event.getAccountId(),
            "alertType", "EMERGENCY_SUSPENSION",
            "reason", event.getReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordEmergencySuspension();

        log.error("Emergency suspension activated: accountId={}, reason={}",
            event.getAccountId(), event.getReason());
    }

    private void processSuspensionAppeal(AccountSuspensionEvent event, String correlationId) {
        AccountSuspension suspension = suspensionRepository.findActiveSuspensionByAccountId(event.getAccountId())
            .orElseThrow(() -> new RuntimeException("Active suspension not found"));

        suspension.setAppealSubmittedAt(LocalDateTime.now());
        suspension.setAppealReason(event.getAppealReason());
        suspension.setAppealDetails(event.getAppealDetails());
        suspensionRepository.save(suspension);

        kafkaTemplate.send("suspension-appeals-queue", Map.of(
            "accountId", event.getAccountId(),
            "suspensionId", suspension.getId(),
            "appealReason", event.getAppealReason(),
            "appealDetails", event.getAppealDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendNotification(event.getUserId(), "Suspension Appeal Received",
            "We have received your appeal for the account suspension and will review it within 3 business days.",
            correlationId);

        metricsService.recordSuspensionAppealSubmitted();

        log.info("Suspension appeal submitted: accountId={}, reason={}",
            event.getAccountId(), event.getAppealReason());
    }

    private void reviewSuspension(AccountSuspensionEvent event, String correlationId) {
        AccountSuspension suspension = suspensionRepository.findActiveSuspensionByAccountId(event.getAccountId())
            .orElseThrow(() -> new RuntimeException("Active suspension not found"));

        suspension.setReviewedAt(LocalDateTime.now());
        suspension.setReviewedBy(event.getReviewedBy());
        suspension.setReviewNotes(event.getReviewNotes());
        suspension.setNextReviewDate(event.getNextReviewDate());
        suspensionRepository.save(suspension);

        // Schedule next review if needed
        if (event.getNextReviewDate() != null) {
            kafkaTemplate.send("scheduled-reviews", Map.of(
                "accountId", event.getAccountId(),
                "reviewType", "SUSPENSION_REVIEW",
                "suspensionId", suspension.getId(),
                "scheduledDate", event.getNextReviewDate(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordSuspensionReviewed();

        log.info("Suspension reviewed: accountId={}, reviewedBy={}, nextReview={}",
            event.getAccountId(), event.getReviewedBy(), event.getNextReviewDate());
    }

    private LocalDateTime calculateExpiryDate(String duration) {
        // Simple duration parsing - in production, use a proper duration parser
        if (duration == null) return null;

        LocalDateTime now = LocalDateTime.now();
        if (duration.contains("day")) {
            int days = Integer.parseInt(duration.replaceAll("[^0-9]", ""));
            return now.plusDays(days);
        } else if (duration.contains("hour")) {
            int hours = Integer.parseInt(duration.replaceAll("[^0-9]", ""));
            return now.plusHours(hours);
        }
        return null;
    }

    private LocalDateTime calculateExtendedExpiryDate(LocalDateTime currentExpiry, String extensionDuration) {
        if (extensionDuration == null || currentExpiry == null) return currentExpiry;

        if (extensionDuration.contains("day")) {
            int days = Integer.parseInt(extensionDuration.replaceAll("[^0-9]", ""));
            return currentExpiry.plusDays(days);
        } else if (extensionDuration.contains("hour")) {
            int hours = Integer.parseInt(extensionDuration.replaceAll("[^0-9]", ""));
            return currentExpiry.plusHours(hours);
        }
        return currentExpiry;
    }

    private String formatDuration(String duration) {
        return duration != null ? duration : "Indefinite";
    }
}