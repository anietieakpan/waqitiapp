package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.VelocityCheckEvent;
import com.waqiti.frauddetection.domain.VelocityCheckViolation;
import com.waqiti.frauddetection.repository.VelocityCheckViolationRepository;
import com.waqiti.frauddetection.service.VelocityCheckService;
import com.waqiti.frauddetection.service.FraudEscalationService;
import com.waqiti.frauddetection.metrics.FraudMetricsService;
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
public class VelocityCheckEventsDlqConsumer {

    private final VelocityCheckViolationRepository velocityViolationRepository;
    private final VelocityCheckService velocityCheckService;
    private final FraudEscalationService escalationService;
    private final FraudMetricsService metricsService;
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
    private Counter velocityViolationCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("velocity_check_dlq_processed_total")
            .description("Total number of successfully processed velocity check DLQ events")
            .register(meterRegistry);
        errorCounter = Counter.builder("velocity_check_dlq_errors_total")
            .description("Total number of velocity check DLQ processing errors")
            .register(meterRegistry);
        velocityViolationCounter = Counter.builder("velocity_violations_critical_total")
            .description("Total number of critical velocity violations requiring escalation")
            .register(meterRegistry);
        processingTimer = Timer.builder("velocity_check_dlq_processing_duration")
            .description("Time taken to process velocity check DLQ events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"velocity-check-events-dlq", "velocity-limit-violations-dlq", "transaction-velocity-dlq"},
        groupId = "velocity-check-dlq-service-group",
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
    @CircuitBreaker(name = "velocity-check-dlq", fallbackMethod = "handleVelocityCheckDlqEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleVelocityCheckDlqEvent(
            @Payload VelocityCheckEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("velocity-dlq-%s-p%d-o%d", event.getAccountId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAccountId(), event.getVelocityType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Velocity check DLQ event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL velocity check violation from DLQ: accountId={}, type={}, amount={}, topic={}",
                event.getAccountId(), event.getVelocityType(), event.getTransactionAmount(), topic);

            // Clean expired entries periodically
            cleanExpiredEntries();

            // DLQ velocity check events indicate critical fraud prevention failures
            if (isCriticalVelocityViolation(event)) {
                velocityViolationCounter.increment();
                escalateCriticalVelocityViolation(event, correlationId, topic);
            }

            switch (event.getVelocityType()) {
                case TRANSACTION_COUNT:
                    processTransactionCountViolationDlq(event, correlationId, topic);
                    break;

                case TRANSACTION_AMOUNT:
                    processTransactionAmountViolationDlq(event, correlationId, topic);
                    break;

                case DAILY_LIMIT:
                    processDailyLimitViolationDlq(event, correlationId, topic);
                    break;

                case MONTHLY_LIMIT:
                    processMonthlyLimitViolationDlq(event, correlationId, topic);
                    break;

                case MERCHANT_VELOCITY:
                    processMerchantVelocityViolationDlq(event, correlationId, topic);
                    break;

                case CARD_VELOCITY:
                    processCardVelocityViolationDlq(event, correlationId, topic);
                    break;

                case CROSS_BORDER:
                    processCrossBorderVelocityViolationDlq(event, correlationId, topic);
                    break;

                case ATM_VELOCITY:
                    processAtmVelocityViolationDlq(event, correlationId, topic);
                    break;

                default:
                    processGenericVelocityViolationDlq(event, correlationId, topic);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logFraudEvent("VELOCITY_CHECK_DLQ_PROCESSED", event.getAccountId(),
                Map.of("velocityType", event.getVelocityType(), "transactionAmount", event.getTransactionAmount(),
                    "violationSeverity", event.getViolationSeverity(), "correlationId", correlationId,
                    "dlqTopic", topic, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process velocity check DLQ event: {}", e.getMessage(), e);

            // Send to fraud team escalation for velocity DLQ failures
            sendFraudTeamEscalation(event, correlationId, topic, e);

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleVelocityCheckDlqEventFallback(
            VelocityCheckEvent event,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("velocity-dlq-fallback-%s-p%d-o%d", event.getAccountId(), partition, offset);

        log.error("Circuit breaker fallback triggered for velocity check DLQ: accountId={}, topic={}, error={}",
            event.getAccountId(), topic, ex.getMessage());

        // Critical: velocity check DLQ circuit breaker means fraud prevention system failure
        sendFraudTeamEscalation(event, correlationId, topic, ex);

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltVelocityCheckEvent(
            @Payload VelocityCheckEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-velocity-%s-%d", event.getAccountId(), System.currentTimeMillis());

        log.error("CRITICAL: Velocity check DLQ permanently failed - accountId={}, topic={}, error={}",
            event.getAccountId(), topic, exceptionMessage);

        // Save to audit trail for fraud monitoring
        auditService.logFraudEvent("VELOCITY_CHECK_DLQ_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "velocityType", event.getVelocityType(), "correlationId", correlationId,
                "requiresManualFraudReview", true, "timestamp", Instant.now()));

        // Immediate fraud team escalation for DLT velocity failures
        sendFraudTeamEscalation(event, correlationId, topic, new RuntimeException(exceptionMessage));
    }

    private boolean isCriticalVelocityViolation(VelocityCheckEvent event) {
        return "CRITICAL".equals(event.getViolationSeverity()) ||
               event.getTransactionAmount() > 50000.0 ||
               event.getViolationCount() >= 5 ||
               Arrays.asList("CROSS_BORDER", "ATM_VELOCITY").contains(event.getVelocityType().toString());
    }

    private void processTransactionCountViolationDlq(VelocityCheckEvent event, String correlationId, String topic) {
        VelocityCheckViolation violation = VelocityCheckViolation.builder()
            .accountId(event.getAccountId())
            .violationType("TRANSACTION_COUNT_DLQ")
            .severity("CRITICAL")
            .description(String.format("Transaction count velocity violation from DLQ: %s", event.getDescription()))
            .transactionAmount(event.getTransactionAmount())
            .violationCount(event.getViolationCount())
            .timeWindow(event.getTimeWindow())
            .correlationId(correlationId)
            .source(topic)
            .status("REQUIRES_IMMEDIATE_ACTION")
            .detectedAt(LocalDateTime.now())
            .build();
        velocityViolationRepository.save(violation);

        velocityCheckService.blockTransactionVelocity(event.getAccountId(), "DLQ_COUNT_VIOLATION");
        escalationService.escalateTransactionCountViolation(event, correlationId);

        // Fraud team notification
        notificationService.sendFraudAlert(
            "CRITICAL: Transaction Count Velocity Violation from DLQ",
            String.format("Transaction count violation for account %s from DLQ - immediate action required", event.getAccountId()),
            "CRITICAL"
        );

        log.error("Transaction count velocity violation DLQ processed: accountId={}, count={}",
            event.getAccountId(), event.getViolationCount());
    }

    private void processTransactionAmountViolationDlq(VelocityCheckEvent event, String correlationId, String topic) {
        velocityCheckService.recordAmountVelocityViolation(event.getAccountId(), event.getTransactionAmount(), "DLQ_SOURCE");
        velocityCheckService.blockHighValueTransactions(event.getAccountId(), "DLQ_AMOUNT_VIOLATION");
        escalationService.escalateTransactionAmountViolation(event, correlationId);

        // High-value transaction alert
        if (event.getTransactionAmount() > 100000.0) {
            notificationService.sendExecutiveAlert(
                "CRITICAL: High-Value Velocity Violation from DLQ",
                String.format("High-value velocity violation of $%.2f for account %s from DLQ",
                    event.getTransactionAmount(), event.getAccountId()),
                Map.of("accountId", event.getAccountId(), "amount", event.getTransactionAmount(), "correlationId", correlationId)
            );
        }

        log.error("Transaction amount velocity violation DLQ processed: accountId={}, amount=${}",
            event.getAccountId(), event.getTransactionAmount());
    }

    private void processDailyLimitViolationDlq(VelocityCheckEvent event, String correlationId, String topic) {
        velocityCheckService.recordDailyLimitViolation(event.getAccountId(), event.getTransactionAmount());
        escalationService.escalateDailyLimitViolation(event, correlationId);

        log.error("Daily limit velocity violation DLQ processed: accountId={}", event.getAccountId());
    }

    private void processMonthlyLimitViolationDlq(VelocityCheckEvent event, String correlationId, String topic) {
        velocityCheckService.recordMonthlyLimitViolation(event.getAccountId(), event.getTransactionAmount());
        escalationService.escalateMonthlyLimitViolation(event, correlationId);

        log.error("Monthly limit velocity violation DLQ processed: accountId={}", event.getAccountId());
    }

    private void processMerchantVelocityViolationDlq(VelocityCheckEvent event, String correlationId, String topic) {
        velocityCheckService.recordMerchantVelocityViolation(event.getAccountId(), event.getMerchantId(), event.getTransactionAmount());
        escalationService.escalateMerchantVelocityViolation(event, correlationId);

        // Merchant risk notification
        notificationService.sendMerchantAlert(
            "Merchant Velocity Violation from DLQ",
            String.format("Merchant velocity violation for account %s and merchant %s from DLQ",
                event.getAccountId(), event.getMerchantId()),
            "HIGH"
        );

        log.error("Merchant velocity violation DLQ processed: accountId={}, merchantId={}",
            event.getAccountId(), event.getMerchantId());
    }

    private void processCardVelocityViolationDlq(VelocityCheckEvent event, String correlationId, String topic) {
        velocityCheckService.recordCardVelocityViolation(event.getAccountId(), event.getCardId(), event.getTransactionAmount());
        velocityCheckService.blockCard(event.getCardId(), "DLQ_VELOCITY_VIOLATION");
        escalationService.escalateCardVelocityViolation(event, correlationId);

        // Card security notification
        notificationService.sendCardSecurityAlert(
            "Card Velocity Violation from DLQ",
            String.format("Card velocity violation for card %s from DLQ - card blocked", event.getCardId()),
            "HIGH"
        );

        log.error("Card velocity violation DLQ processed: accountId={}, cardId={}",
            event.getAccountId(), event.getCardId());
    }

    private void processCrossBorderVelocityViolationDlq(VelocityCheckEvent event, String correlationId, String topic) {
        velocityCheckService.recordCrossBorderVelocityViolation(event.getAccountId(), event.getCountryCode(), event.getTransactionAmount());
        escalationService.escalateCrossBorderVelocityViolation(event, correlationId);

        // AML team notification for cross-border violations
        notificationService.sendAmlAlert(
            "CRITICAL: Cross-Border Velocity Violation from DLQ",
            String.format("Cross-border velocity violation for account %s to country %s from DLQ",
                event.getAccountId(), event.getCountryCode()),
            "CRITICAL"
        );

        log.error("Cross-border velocity violation DLQ processed: accountId={}, country={}",
            event.getAccountId(), event.getCountryCode());
    }

    private void processAtmVelocityViolationDlq(VelocityCheckEvent event, String correlationId, String topic) {
        velocityCheckService.recordAtmVelocityViolation(event.getAccountId(), event.getAtmId(), event.getTransactionAmount());
        escalationService.escalateAtmVelocityViolation(event, correlationId);

        log.error("ATM velocity violation DLQ processed: accountId={}, atmId={}",
            event.getAccountId(), event.getAtmId());
    }

    private void processGenericVelocityViolationDlq(VelocityCheckEvent event, String correlationId, String topic) {
        velocityCheckService.recordGenericVelocityViolation(event.getAccountId(), event.getDescription(), "DLQ_GENERIC");
        escalationService.escalateGenericVelocityViolation(event, correlationId);

        log.warn("Generic velocity violation DLQ processed: accountId={}, type={}",
            event.getAccountId(), event.getVelocityType());
    }

    private void escalateCriticalVelocityViolation(VelocityCheckEvent event, String correlationId, String topic) {
        try {
            notificationService.sendFraudAlert(
                "CRITICAL: Velocity Violation from DLQ Requires Immediate Action",
                String.format("Critical velocity violation for account %s from DLQ topic %s requires immediate fraud review. " +
                    "Type: %s, Amount: $%.2f, Violations: %d",
                    event.getAccountId(), topic, event.getVelocityType(), event.getTransactionAmount(), event.getViolationCount()),
                Map.of(
                    "accountId", event.getAccountId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "velocityType", event.getVelocityType(),
                    "transactionAmount", event.getTransactionAmount(),
                    "violationCount", event.getViolationCount(),
                    "priority", "CRITICAL_VELOCITY_VIOLATION"
                )
            );
        } catch (Exception ex) {
            log.error("Failed to send critical velocity violation escalation: {}", ex.getMessage());
        }
    }

    private void sendFraudTeamEscalation(VelocityCheckEvent event, String correlationId, String topic, Exception ex) {
        try {
            notificationService.sendFraudAlert(
                "SYSTEM CRITICAL: Velocity Check DLQ Processing Failure",
                String.format("CRITICAL SYSTEM FAILURE: Unable to process velocity check from DLQ for account %s. " +
                    "This indicates a serious fraud prevention system failure requiring immediate intervention. " +
                    "Topic: %s, Error: %s", event.getAccountId(), topic, ex.getMessage()),
                Map.of(
                    "accountId", event.getAccountId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "errorMessage", ex.getMessage(),
                    "priority", "FRAUD_SYSTEM_CRITICAL_FAILURE"
                )
            );
        } catch (Exception notificationEx) {
            log.error("CRITICAL: Failed to send fraud team escalation for velocity DLQ failure: {}", notificationEx.getMessage());
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