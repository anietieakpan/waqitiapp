package com.waqiti.account.kafka;

import com.waqiti.common.events.AccountRestrictionsEvent;
import com.waqiti.account.domain.AccountRestriction;
import com.waqiti.account.repository.AccountRestrictionRepository;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.service.AccountRestrictionService;
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
public class AccountRestrictionsAppliedConsumer {

    private final AccountRestrictionRepository restrictionRepository;
    private final AccountRepository accountRepository;
    private final AccountRestrictionService restrictionService;
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
        successCounter = Counter.builder("account_restrictions_processed_total")
            .description("Total number of successfully processed account restriction events")
            .register(meterRegistry);
        errorCounter = Counter.builder("account_restrictions_errors_total")
            .description("Total number of account restriction processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("account_restrictions_processing_duration")
            .description("Time taken to process account restriction events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"account-restrictions-applied", "account-restrictions-events", "compliance-restrictions"},
        groupId = "account-restrictions-service-group",
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
    @CircuitBreaker(name = "account-restrictions", fallbackMethod = "handleAccountRestrictionsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAccountRestrictionsEvent(
            @Payload AccountRestrictionsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("restrictions-%s-p%d-o%d", event.getAccountId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAccountId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing account restrictions event: accountId={}, restrictionType={}, reason={}",
                event.getAccountId(), event.getRestrictionType(), event.getReason());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case RESTRICTION_APPLIED:
                    applyAccountRestriction(event, correlationId);
                    break;

                case RESTRICTION_MODIFIED:
                    modifyAccountRestriction(event, correlationId);
                    break;

                case RESTRICTION_ESCALATED:
                    escalateAccountRestriction(event, correlationId);
                    break;

                case RESTRICTION_REVIEWED:
                    reviewAccountRestriction(event, correlationId);
                    break;

                case RESTRICTION_LIFTED:
                    liftAccountRestriction(event, correlationId);
                    break;

                case TEMPORARY_RESTRICTION_EXPIRED:
                    handleTemporaryRestrictionExpiry(event, correlationId);
                    break;

                case COMPLIANCE_RESTRICTION_MANDATED:
                    applyComplianceRestriction(event, correlationId);
                    break;

                case REGULATORY_RESTRICTION_IMPOSED:
                    applyRegulatoryRestriction(event, correlationId);
                    break;

                default:
                    log.warn("Unknown account restrictions event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("ACCOUNT_RESTRICTIONS_EVENT_PROCESSED", event.getAccountId(),
                Map.of("eventType", event.getEventType(), "restrictionType", event.getRestrictionType(),
                    "reason", event.getReason(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process account restrictions event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("account-restrictions-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAccountRestrictionsEventFallback(
            AccountRestrictionsEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("restrictions-fallback-%s-p%d-o%d", event.getAccountId(), partition, offset);

        log.error("Circuit breaker fallback triggered for account restrictions: accountId={}, error={}",
            event.getAccountId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("account-restrictions-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Account Restrictions Circuit Breaker Triggered",
                String.format("Account %s restrictions processing failed: %s", event.getAccountId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAccountRestrictionsEvent(
            @Payload AccountRestrictionsEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-restrictions-%s-%d", event.getAccountId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Account restrictions permanently failed: accountId={}, topic={}, error={}",
            event.getAccountId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("ACCOUNT_RESTRICTIONS_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Account Restrictions Dead Letter Event",
                String.format("Account %s restrictions processing sent to DLT: %s", event.getAccountId(), exceptionMessage),
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

    private void applyAccountRestriction(AccountRestrictionsEvent event, String correlationId) {
        AccountRestriction restriction = AccountRestriction.builder()
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .restrictionType(event.getRestrictionType())
            .reason(event.getReason())
            .severity(event.getSeverity())
            .appliedBy(event.getAppliedBy())
            .appliedAt(LocalDateTime.now())
            .status("ACTIVE")
            .expiresAt(event.getExpiryDate())
            .correlationId(correlationId)
            .build();
        restrictionRepository.save(restriction);

        restrictionService.applyRestriction(event.getAccountId(), event.getRestrictionType(), event.getRestrictionDetails());

        // Update account status if necessary
        var account = accountRepository.findById(event.getAccountId()).orElseThrow();
        account.setStatus("RESTRICTED");
        account.setRestrictionLevel(event.getSeverity());
        accountRepository.save(account);

        notificationService.sendNotification(event.getUserId(), "Account Restriction Applied",
            String.format("A restriction has been applied to your account: %s. Reason: %s",
                event.getRestrictionType(), event.getReason()),
            correlationId);

        kafkaTemplate.send("account-status-changes", Map.of(
            "accountId", event.getAccountId(),
            "userId", event.getUserId(),
            "statusChange", "RESTRICTION_APPLIED",
            "restrictionType", event.getRestrictionType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordRestrictionApplied(event.getRestrictionType(), event.getSeverity());

        log.info("Account restriction applied: accountId={}, type={}, severity={}",
            event.getAccountId(), event.getRestrictionType(), event.getSeverity());
    }

    private void modifyAccountRestriction(AccountRestrictionsEvent event, String correlationId) {
        AccountRestriction restriction = restrictionRepository.findActiveByAccountIdAndType(
            event.getAccountId(), event.getRestrictionType())
            .orElseThrow(() -> new RuntimeException("Active restriction not found"));

        restriction.setSeverity(event.getSeverity());
        restriction.setReason(event.getReason());
        restriction.setModifiedAt(LocalDateTime.now());
        restriction.setModifiedBy(event.getAppliedBy());
        restrictionRepository.save(restriction);

        restrictionService.modifyRestriction(event.getAccountId(), event.getRestrictionType(), event.getRestrictionDetails());

        notificationService.sendNotification(event.getUserId(), "Account Restriction Modified",
            String.format("Your account restriction has been modified: %s", event.getRestrictionType()),
            correlationId);

        metricsService.recordRestrictionModified(event.getRestrictionType());

        log.info("Account restriction modified: accountId={}, type={}, newSeverity={}",
            event.getAccountId(), event.getRestrictionType(), event.getSeverity());
    }

    private void escalateAccountRestriction(AccountRestrictionsEvent event, String correlationId) {
        AccountRestriction restriction = restrictionRepository.findActiveByAccountIdAndType(
            event.getAccountId(), event.getRestrictionType())
            .orElseThrow(() -> new RuntimeException("Active restriction not found"));

        restriction.setSeverity("HIGH");
        restriction.setEscalatedAt(LocalDateTime.now());
        restriction.setEscalationReason(event.getEscalationReason());
        restrictionRepository.save(restriction);

        restrictionService.escalateRestriction(event.getAccountId(), event.getRestrictionType());

        notificationService.sendCriticalAlert(
            "Account Restriction Escalated",
            String.format("Restriction escalated for account %s: %s", event.getAccountId(), event.getEscalationReason()),
            Map.of("accountId", event.getAccountId(), "restrictionType", event.getRestrictionType(),
                "correlationId", correlationId)
        );

        kafkaTemplate.send("compliance-alerts", Map.of(
            "accountId", event.getAccountId(),
            "alertType", "RESTRICTION_ESCALATED",
            "restrictionType", event.getRestrictionType(),
            "escalationReason", event.getEscalationReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordRestrictionEscalated(event.getRestrictionType());

        log.warn("Account restriction escalated: accountId={}, type={}, reason={}",
            event.getAccountId(), event.getRestrictionType(), event.getEscalationReason());
    }

    private void reviewAccountRestriction(AccountRestrictionsEvent event, String correlationId) {
        AccountRestriction restriction = restrictionRepository.findActiveByAccountIdAndType(
            event.getAccountId(), event.getRestrictionType())
            .orElseThrow(() -> new RuntimeException("Active restriction not found"));

        restriction.setReviewedAt(LocalDateTime.now());
        restriction.setReviewedBy(event.getReviewedBy());
        restriction.setReviewNotes(event.getReviewNotes());
        restriction.setNextReviewDate(event.getNextReviewDate());
        restrictionRepository.save(restriction);

        // Schedule next review if needed
        if (event.getNextReviewDate() != null) {
            kafkaTemplate.send("scheduled-reviews", Map.of(
                "accountId", event.getAccountId(),
                "reviewType", "RESTRICTION_REVIEW",
                "restrictionType", event.getRestrictionType(),
                "scheduledDate", event.getNextReviewDate(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRestrictionReviewed(event.getRestrictionType());

        log.info("Account restriction reviewed: accountId={}, type={}, reviewedBy={}",
            event.getAccountId(), event.getRestrictionType(), event.getReviewedBy());
    }

    private void liftAccountRestriction(AccountRestrictionsEvent event, String correlationId) {
        AccountRestriction restriction = restrictionRepository.findActiveByAccountIdAndType(
            event.getAccountId(), event.getRestrictionType())
            .orElseThrow(() -> new RuntimeException("Active restriction not found"));

        restriction.setStatus("LIFTED");
        restriction.setLiftedAt(LocalDateTime.now());
        restriction.setLiftedBy(event.getLiftedBy());
        restriction.setLiftReason(event.getLiftReason());
        restrictionRepository.save(restriction);

        restrictionService.liftRestriction(event.getAccountId(), event.getRestrictionType());

        // Check if account should return to normal status
        if (!restrictionRepository.hasActiveRestrictions(event.getAccountId())) {
            var account = accountRepository.findById(event.getAccountId()).orElseThrow();
            account.setStatus("ACTIVE");
            account.setRestrictionLevel(null);
            accountRepository.save(account);
        }

        notificationService.sendNotification(event.getUserId(), "Account Restriction Lifted",
            String.format("A restriction has been removed from your account: %s", event.getRestrictionType()),
            correlationId);

        kafkaTemplate.send("account-status-changes", Map.of(
            "accountId", event.getAccountId(),
            "userId", event.getUserId(),
            "statusChange", "RESTRICTION_LIFTED",
            "restrictionType", event.getRestrictionType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordRestrictionLifted(event.getRestrictionType());

        log.info("Account restriction lifted: accountId={}, type={}, liftedBy={}",
            event.getAccountId(), event.getRestrictionType(), event.getLiftedBy());
    }

    private void handleTemporaryRestrictionExpiry(AccountRestrictionsEvent event, String correlationId) {
        AccountRestriction restriction = restrictionRepository.findActiveByAccountIdAndType(
            event.getAccountId(), event.getRestrictionType())
            .orElseThrow(() -> new RuntimeException("Active restriction not found"));

        restriction.setStatus("EXPIRED");
        restriction.setExpiredAt(LocalDateTime.now());
        restrictionRepository.save(restriction);

        restrictionService.liftRestriction(event.getAccountId(), event.getRestrictionType());

        notificationService.sendNotification(event.getUserId(), "Temporary Restriction Expired",
            String.format("A temporary restriction on your account has expired: %s", event.getRestrictionType()),
            correlationId);

        metricsService.recordRestrictionExpired(event.getRestrictionType());

        log.info("Temporary restriction expired: accountId={}, type={}",
            event.getAccountId(), event.getRestrictionType());
    }

    private void applyComplianceRestriction(AccountRestrictionsEvent event, String correlationId) {
        AccountRestriction restriction = AccountRestriction.builder()
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .restrictionType(event.getRestrictionType())
            .reason(event.getReason())
            .severity("HIGH")
            .appliedBy("COMPLIANCE_SYSTEM")
            .appliedAt(LocalDateTime.now())
            .status("ACTIVE")
            .isComplianceRestriction(true)
            .regulatoryReference(event.getRegulatoryReference())
            .correlationId(correlationId)
            .build();
        restrictionRepository.save(restriction);

        complianceService.applyComplianceRestriction(event.getAccountId(), event.getRestrictionType(), event.getComplianceDetails());

        notificationService.sendCriticalAlert(
            "Compliance Restriction Applied",
            String.format("Compliance restriction applied to account %s: %s", event.getAccountId(), event.getReason()),
            Map.of("accountId", event.getAccountId(), "restrictionType", event.getRestrictionType(),
                "regulatoryReference", event.getRegulatoryReference(), "correlationId", correlationId)
        );

        metricsService.recordComplianceRestrictionApplied(event.getRestrictionType());

        log.warn("Compliance restriction applied: accountId={}, type={}, reference={}",
            event.getAccountId(), event.getRestrictionType(), event.getRegulatoryReference());
    }

    private void applyRegulatoryRestriction(AccountRestrictionsEvent event, String correlationId) {
        AccountRestriction restriction = AccountRestriction.builder()
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .restrictionType(event.getRestrictionType())
            .reason(event.getReason())
            .severity("CRITICAL")
            .appliedBy("REGULATORY_AUTHORITY")
            .appliedAt(LocalDateTime.now())
            .status("ACTIVE")
            .isRegulatoryRestriction(true)
            .regulatoryReference(event.getRegulatoryReference())
            .correlationId(correlationId)
            .build();
        restrictionRepository.save(restriction);

        complianceService.applyRegulatoryRestriction(event.getAccountId(), event.getRestrictionType(), event.getRegulatoryDetails());

        notificationService.sendCriticalAlert(
            "Regulatory Restriction Imposed",
            String.format("Regulatory restriction imposed on account %s: %s", event.getAccountId(), event.getReason()),
            Map.of("accountId", event.getAccountId(), "restrictionType", event.getRestrictionType(),
                "regulatoryReference", event.getRegulatoryReference(), "correlationId", correlationId)
        );

        kafkaTemplate.send("regulatory-compliance-events", Map.of(
            "accountId", event.getAccountId(),
            "eventType", "REGULATORY_RESTRICTION_IMPOSED",
            "restrictionType", event.getRestrictionType(),
            "regulatoryReference", event.getRegulatoryReference(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordRegulatoryRestrictionApplied(event.getRestrictionType());

        log.error("Regulatory restriction imposed: accountId={}, type={}, reference={}",
            event.getAccountId(), event.getRestrictionType(), event.getRegulatoryReference());
    }
}