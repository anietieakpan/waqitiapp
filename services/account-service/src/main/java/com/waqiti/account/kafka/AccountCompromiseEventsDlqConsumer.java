package com.waqiti.account.kafka;

import com.waqiti.common.events.AccountCompromiseEvent;
import com.waqiti.account.domain.AccountCompromiseIncident;
import com.waqiti.account.repository.AccountCompromiseIncidentRepository;
import com.waqiti.account.service.AccountSecurityService;
import com.waqiti.account.service.AccountEscalationService;
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
public class AccountCompromiseEventsDlqConsumer {

    private final AccountCompromiseIncidentRepository compromiseIncidentRepository;
    private final AccountSecurityService accountSecurityService;
    private final AccountEscalationService escalationService;
    private final AccountMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter criticalCompromiseCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("account_compromise_dlq_processed_total")
            .description("Total number of successfully processed account compromise DLQ events")
            .register(meterRegistry);
        errorCounter = Counter.builder("account_compromise_dlq_errors_total")
            .description("Total number of account compromise DLQ processing errors")
            .register(meterRegistry);
        criticalCompromiseCounter = Counter.builder("account_compromise_critical_total")
            .description("Total number of critical account compromises requiring executive escalation")
            .register(meterRegistry);
        processingTimer = Timer.builder("account_compromise_dlq_processing_duration")
            .description("Time taken to process account compromise DLQ events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"account-compromise-events-dlq", "account-takeover-dlq", "account-security-breach-dlq"},
        groupId = "account-compromise-dlq-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "account-compromise-dlq", fallbackMethod = "handleAccountCompromiseDlqEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAccountCompromiseDlqEvent(
            @Payload AccountCompromiseEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("compromise-dlq-%s-p%d-o%d", event.getAccountId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAccountId(), event.getCompromiseType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Account compromise DLQ event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL account compromise from DLQ: accountId={}, type={}, severity={}, topic={}",
                event.getAccountId(), event.getCompromiseType(), event.getSeverity(), topic);

            // Clean expired entries periodically
            cleanExpiredEntries();

            // DLQ compromise events are critical by nature - require immediate security response
            if (isCriticalAccountCompromise(event)) {
                criticalCompromiseCounter.increment();
                initiateEmergencySecurityResponse(event, correlationId, topic);
            }

            switch (event.getCompromiseType()) {
                case ACCOUNT_TAKEOVER:
                    processAccountTakeoverDlq(event, correlationId, topic);
                    break;

                case CREDENTIAL_COMPROMISE:
                    processCredentialCompromiseDlq(event, correlationId, topic);
                    break;

                case UNAUTHORIZED_ACCESS:
                    processUnauthorizedAccessDlq(event, correlationId, topic);
                    break;

                case IDENTITY_THEFT:
                    processIdentityTheftDlq(event, correlationId, topic);
                    break;

                case DEVICE_COMPROMISE:
                    processDeviceCompromiseDlq(event, correlationId, topic);
                    break;

                case SESSION_HIJACKING:
                    processSessionHijackingDlq(event, correlationId, topic);
                    break;

                case FRAUDULENT_TRANSACTION:
                    processFraudulentTransactionDlq(event, correlationId, topic);
                    break;

                case SOCIAL_ENGINEERING:
                    processSocialEngineeringDlq(event, correlationId, topic);
                    break;

                default:
                    processGenericCompromiseDlq(event, correlationId, topic);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("ACCOUNT_COMPROMISE_DLQ_PROCESSED", event.getAccountId(),
                Map.of("compromiseType", event.getCompromiseType(), "severity", event.getSeverity(),
                    "sourceIp", event.getSourceIp(), "correlationId", correlationId,
                    "dlqTopic", topic, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process account compromise DLQ event: {}", e.getMessage(), e);

            // Send to security escalation for compromise DLQ failures
            sendSecurityEscalation(event, correlationId, topic, e);

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAccountCompromiseDlqEventFallback(
            AccountCompromiseEvent event,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("compromise-dlq-fallback-%s-p%d-o%d", event.getAccountId(), partition, offset);

        log.error("Circuit breaker fallback triggered for account compromise DLQ: accountId={}, topic={}, error={}",
            event.getAccountId(), topic, ex.getMessage());

        // Critical: compromise DLQ circuit breaker means security system failure
        sendSecurityEscalation(event, correlationId, topic, ex);

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAccountCompromiseEvent(
            @Payload AccountCompromiseEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-compromise-%s-%d", event.getAccountId(), System.currentTimeMillis());

        log.error("CRITICAL: Account compromise DLQ permanently failed - accountId={}, topic={}, error={}",
            event.getAccountId(), topic, exceptionMessage);

        // Save to security audit trail
        auditService.logSecurityEvent("ACCOUNT_COMPROMISE_DLQ_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "compromiseType", event.getCompromiseType(), "correlationId", correlationId,
                "requiresEmergencyResponse", true, "timestamp", Instant.now()));

        // Immediate security escalation for DLT compromise failures
        sendSecurityEscalation(event, correlationId, topic, new RuntimeException(exceptionMessage));
    }

    private boolean isCriticalAccountCompromise(AccountCompromiseEvent event) {
        return "CRITICAL".equals(event.getSeverity()) ||
               Arrays.asList("ACCOUNT_TAKEOVER", "IDENTITY_THEFT", "FRAUDULENT_TRANSACTION").contains(event.getCompromiseType().toString()) ||
               event.getFinancialImpact() > 10000.0;
    }

    private void processAccountTakeoverDlq(AccountCompromiseEvent event, String correlationId, String topic) {
        AccountCompromiseIncident incident = AccountCompromiseIncident.builder()
            .accountId(event.getAccountId())
            .incidentType("ACCOUNT_TAKEOVER_DLQ")
            .severity("CRITICAL")
            .description(String.format("Account takeover from DLQ: %s", event.getDescription()))
            .sourceIp(event.getSourceIp())
            .userAgent(event.getUserAgent())
            .correlationId(correlationId)
            .source(topic)
            .status("EMERGENCY_RESPONSE_REQUIRED")
            .detectedAt(LocalDateTime.now())
            .financialImpact(event.getFinancialImpact())
            .build();
        compromiseIncidentRepository.save(incident);

        // Immediate account lockdown
        accountSecurityService.emergencyAccountLockdown(event.getAccountId(), "DLQ_ACCOUNT_TAKEOVER");
        escalationService.escalateAccountTakeover(event, correlationId);

        // Immediate security team notification
        notificationService.sendSecurityAlert(
            "CRITICAL: Account Takeover from DLQ",
            String.format("Account %s takeover detected from DLQ - immediate response required", event.getAccountId()),
            "CRITICAL"
        );

        log.error("Account takeover DLQ processed: accountId={}", event.getAccountId());
    }

    private void processCredentialCompromiseDlq(AccountCompromiseEvent event, String correlationId, String topic) {
        accountSecurityService.recordCredentialCompromise(event.getAccountId(), event.getDescription(), "DLQ_SOURCE");
        accountSecurityService.forcePasswordReset(event.getAccountId(), "DLQ_CREDENTIAL_COMPROMISE");
        escalationService.escalateCredentialCompromise(event, correlationId);

        // Security team notification
        notificationService.sendSecurityAlert(
            "Credential Compromise from DLQ",
            String.format("Account %s credentials compromised - password reset forced", event.getAccountId()),
            "HIGH"
        );

        log.error("Credential compromise DLQ processed: accountId={}", event.getAccountId());
    }

    private void processUnauthorizedAccessDlq(AccountCompromiseEvent event, String correlationId, String topic) {
        accountSecurityService.recordUnauthorizedAccess(event.getAccountId(), event.getSourceIp(), event.getDescription());
        escalationService.escalateUnauthorizedAccess(event, correlationId);

        // Security team notification
        notificationService.sendSecurityAlert(
            "Unauthorized Access from DLQ",
            String.format("Unauthorized access to account %s from IP %s", event.getAccountId(), event.getSourceIp()),
            "HIGH"
        );

        log.error("Unauthorized access DLQ processed: accountId={}, sourceIp={}", event.getAccountId(), event.getSourceIp());
    }

    private void processIdentityTheftDlq(AccountCompromiseEvent event, String correlationId, String topic) {
        accountSecurityService.recordIdentityTheft(event.getAccountId(), event.getDescription());
        accountSecurityService.emergencyAccountLockdown(event.getAccountId(), "DLQ_IDENTITY_THEFT");
        escalationService.escalateIdentityTheft(event, correlationId);

        // Critical: identity theft requires law enforcement notification
        notificationService.sendCriticalAlert(
            "CRITICAL: Identity Theft from DLQ",
            String.format("Identity theft detected for account %s - law enforcement notification required", event.getAccountId()),
            Map.of("accountId", event.getAccountId(), "correlationId", correlationId)
        );

        log.error("Identity theft DLQ processed: accountId={}", event.getAccountId());
    }

    private void processDeviceCompromiseDlq(AccountCompromiseEvent event, String correlationId, String topic) {
        accountSecurityService.recordDeviceCompromise(event.getAccountId(), event.getDeviceId(), event.getDescription());
        escalationService.escalateDeviceCompromise(event, correlationId);

        log.error("Device compromise DLQ processed: accountId={}, deviceId={}", event.getAccountId(), event.getDeviceId());
    }

    private void processSessionHijackingDlq(AccountCompromiseEvent event, String correlationId, String topic) {
        accountSecurityService.recordSessionHijacking(event.getAccountId(), event.getSessionId(), event.getDescription());
        accountSecurityService.terminateAllUserSessions(event.getAccountId(), "DLQ_SESSION_HIJACKING");
        escalationService.escalateSessionHijacking(event, correlationId);

        log.error("Session hijacking DLQ processed: accountId={}, sessionId={}", event.getAccountId(), event.getSessionId());
    }

    private void processFraudulentTransactionDlq(AccountCompromiseEvent event, String correlationId, String topic) {
        accountSecurityService.recordFraudulentTransaction(event.getAccountId(), event.getTransactionId(), event.getFinancialImpact());
        escalationService.escalateFraudulentTransaction(event, correlationId);

        // Financial crimes notification for high-value fraud
        if (event.getFinancialImpact() > 5000.0) {
            notificationService.sendFinancialCrimesAlert(
                "High-Value Fraudulent Transaction from DLQ",
                String.format("Fraudulent transaction of $%.2f detected for account %s", event.getFinancialImpact(), event.getAccountId()),
                Map.of("accountId", event.getAccountId(), "amount", event.getFinancialImpact(), "correlationId", correlationId)
            );
        }

        log.error("Fraudulent transaction DLQ processed: accountId={}, amount=${}", event.getAccountId(), event.getFinancialImpact());
    }

    private void processSocialEngineeringDlq(AccountCompromiseEvent event, String correlationId, String topic) {
        accountSecurityService.recordSocialEngineeringAttempt(event.getAccountId(), event.getDescription());
        escalationService.escalateSocialEngineering(event, correlationId);

        log.error("Social engineering DLQ processed: accountId={}", event.getAccountId());
    }

    private void processGenericCompromiseDlq(AccountCompromiseEvent event, String correlationId, String topic) {
        accountSecurityService.recordGenericCompromise(event.getAccountId(), event.getDescription(), "DLQ_GENERIC");
        escalationService.escalateGenericCompromise(event, correlationId);

        log.warn("Generic compromise DLQ processed: accountId={}, type={}",
            event.getAccountId(), event.getCompromiseType());
    }

    private void initiateEmergencySecurityResponse(AccountCompromiseEvent event, String correlationId, String topic) {
        try {
            notificationService.sendExecutiveAlert(
                "CRITICAL: Account Compromise Emergency Response Required",
                String.format("Critical account compromise for %s from DLQ topic %s requires emergency security response. " +
                    "Type: %s, Severity: %s, Financial Impact: $%.2f",
                    event.getAccountId(), topic, event.getCompromiseType(), event.getSeverity(), event.getFinancialImpact()),
                Map.of(
                    "accountId", event.getAccountId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "compromiseType", event.getCompromiseType(),
                    "severity", event.getSeverity(),
                    "financialImpact", event.getFinancialImpact(),
                    "priority", "EMERGENCY_SECURITY_RESPONSE"
                )
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency security response: {}", ex.getMessage());
        }
    }

    private void sendSecurityEscalation(AccountCompromiseEvent event, String correlationId, String topic, Exception ex) {
        try {
            notificationService.sendSecurityAlert(
                "SYSTEM CRITICAL: Account Compromise DLQ Processing Failure",
                String.format("CRITICAL SECURITY FAILURE: Unable to process account compromise from DLQ for account %s. " +
                    "This indicates a serious security system failure requiring immediate intervention. " +
                    "Topic: %s, Error: %s", event.getAccountId(), topic, ex.getMessage()),
                Map.of(
                    "accountId", event.getAccountId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "errorMessage", ex.getMessage(),
                    "priority", "SECURITY_SYSTEM_CRITICAL_FAILURE"
                )
            );
        } catch (Exception notificationEx) {
            log.error("CRITICAL: Failed to send security escalation for compromise DLQ failure: {}", notificationEx.getMessage());
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