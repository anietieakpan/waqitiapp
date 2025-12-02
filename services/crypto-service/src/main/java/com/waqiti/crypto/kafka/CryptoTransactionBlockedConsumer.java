package com.waqiti.crypto.kafka;

import com.waqiti.crypto.events.model.CryptoEvent;
import com.waqiti.crypto.service.CryptoTransactionService;
import com.waqiti.crypto.service.CryptoWalletService;
import com.waqiti.crypto.compliance.ComplianceService;
import com.waqiti.crypto.security.FraudDetectionService;
import com.waqiti.crypto.repository.CryptoTransactionRepository;
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
public class CryptoTransactionBlockedConsumer {

    private final CryptoTransactionService cryptoTransactionService;
    private final CryptoWalletService cryptoWalletService;
    private final CryptoTransactionRepository transactionRepository;
    private final ComplianceService complianceService;
    private final FraudDetectionService fraudDetectionService;
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
        successCounter = Counter.builder("crypto_transaction_blocked_processed_total")
            .description("Total number of successfully processed crypto transaction blocked events")
            .register(meterRegistry);
        errorCounter = Counter.builder("crypto_transaction_blocked_errors_total")
            .description("Total number of crypto transaction blocked processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("crypto_transaction_blocked_processing_duration")
            .description("Time taken to process crypto transaction blocked events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"crypto-transaction-blocked", "crypto-transaction-rejected", "crypto-transaction-suspended"},
        groupId = "crypto-transaction-blocked-service-group",
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
    @CircuitBreaker(name = "crypto-transaction-blocked", fallbackMethod = "handleCryptoTransactionBlockedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCryptoTransactionBlockedEvent(
            @Payload CryptoEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("blocked-tx-%s-p%d-o%d", event.getTransactionId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getTransactionId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing crypto transaction blocked: txId={}, reason={}, blockType={}",
                event.getTransactionId(), event.getReason(), event.getEventType());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case "TRANSACTION_BLOCKED_FRAUD":
                    processTransactionBlockedFraud(event, correlationId);
                    break;

                case "TRANSACTION_BLOCKED_COMPLIANCE":
                    processTransactionBlockedCompliance(event, correlationId);
                    break;

                case "TRANSACTION_BLOCKED_AML":
                    processTransactionBlockedAml(event, correlationId);
                    break;

                case "TRANSACTION_BLOCKED_SANCTIONS":
                    processTransactionBlockedSanctions(event, correlationId);
                    break;

                case "TRANSACTION_SUSPENDED_VELOCITY":
                    processTransactionSuspendedVelocity(event, correlationId);
                    break;

                case "TRANSACTION_REJECTED_LIMITS":
                    processTransactionRejectedLimits(event, correlationId);
                    break;

                case "TRANSACTION_BLOCKED_HIGH_RISK":
                    processTransactionBlockedHighRisk(event, correlationId);
                    break;

                case "TRANSACTION_BLOCKED_MANUAL_REVIEW":
                    processTransactionBlockedManualReview(event, correlationId);
                    break;

                default:
                    log.warn("Unknown crypto transaction blocked event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("CRYPTO_TRANSACTION_BLOCKED_EVENT_PROCESSED", event.getUserId(),
                Map.of("eventType", event.getEventType(), "transactionId", event.getTransactionId(),
                    "reason", event.getReason(), "blockType", event.getEventType(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process crypto transaction blocked event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("crypto-transaction-blocked-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCryptoTransactionBlockedEventFallback(
            CryptoEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("blocked-tx-fallback-%s-p%d-o%d", event.getTransactionId(), partition, offset);

        log.error("Circuit breaker fallback triggered for crypto transaction blocked: txId={}, error={}",
            event.getTransactionId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("crypto-transaction-blocked-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical notification to security team
        try {
            notificationService.sendCriticalAlert(
                "Crypto Transaction Block Processing Failed",
                String.format("CRITICAL: Failed to process blocked transaction %s: %s", event.getTransactionId(), ex.getMessage()),
                Map.of("transactionId", event.getTransactionId(), "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCryptoTransactionBlockedEvent(
            @Payload CryptoEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-blocked-tx-%s-%d", event.getTransactionId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Crypto transaction blocked permanently failed: txId={}, topic={}, error={}",
            event.getTransactionId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("CRYPTO_TRANSACTION_BLOCKED_DLT_EVENT", event.getUserId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "transactionId", event.getTransactionId(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send emergency alert to security team
        try {
            notificationService.sendEmergencyAlert(
                "CRYPTO TRANSACTION BLOCK DLT EVENT",
                String.format("EMERGENCY: Transaction block event %s sent to DLT - immediate security review required: %s",
                    event.getTransactionId(), exceptionMessage),
                Map.of("transactionId", event.getTransactionId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
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

    private void processTransactionBlockedFraud(CryptoEvent event, String correlationId) {
        log.error("Processing fraud-blocked transaction: txId={}, reason={}", event.getTransactionId(), event.getReason());

        // Immediately block the transaction
        cryptoTransactionService.blockTransactionFraud(event.getTransactionId(), event.getReason(), correlationId);

        // Release any reserved funds back to available balance
        cryptoWalletService.releaseReservedFunds(event.getCryptoWalletId(), event.getTransactionId(), correlationId);

        // Flag wallet for enhanced fraud monitoring
        cryptoWalletService.enableFraudMonitoring(event.getCryptoWalletId(), correlationId);

        // Create fraud investigation case
        fraudDetectionService.createFraudCase(event.getTransactionId(), event.getUserId(),
            "BLOCKED_TRANSACTION", event.getReason(), correlationId);

        // Send critical alert to fraud team
        notificationService.sendCriticalAlert(
            "Crypto Transaction Blocked - Fraud",
            String.format("Transaction %s blocked for fraud: %s", event.getTransactionId(), event.getReason()),
            Map.of("transactionId", event.getTransactionId(), "userId", event.getUserId(),
                "walletId", event.getCryptoWalletId())
        );

        // Notify user (carefully worded to not reveal detection methods)
        notificationService.sendNotification(event.getUserId(),
            "Transaction Security Review",
            "Your transaction has been temporarily held for security review. Our team will contact you shortly.",
            correlationId);

        log.error("Fraud transaction blocked and investigation initiated: txId={}", event.getTransactionId());
    }

    private void processTransactionBlockedCompliance(CryptoEvent event, String correlationId) {
        log.warn("Processing compliance-blocked transaction: txId={}, reason={}", event.getTransactionId(), event.getReason());

        // Block transaction with compliance status
        cryptoTransactionService.blockTransactionCompliance(event.getTransactionId(), event.getReason(), correlationId);

        // Release reserved funds
        cryptoWalletService.releaseReservedFunds(event.getCryptoWalletId(), event.getTransactionId(), correlationId);

        // Create compliance review case
        complianceService.createComplianceCase(event.getTransactionId(), event.getUserId(),
            "COMPLIANCE_BLOCK", event.getReason(), correlationId);

        // Notify compliance team
        notificationService.sendOperationalAlert(
            "Crypto Transaction Compliance Block",
            String.format("Transaction %s blocked for compliance: %s", event.getTransactionId(), event.getReason()),
            "HIGH"
        );

        // Notify user with clear next steps
        notificationService.sendNotification(event.getUserId(),
            "Transaction Compliance Review Required",
            String.format("Your transaction requires additional compliance review: %s. Please contact support for assistance.", event.getReason()),
            correlationId);

        log.warn("Compliance transaction blocked: txId={}, reason={}", event.getTransactionId(), event.getReason());
    }

    private void processTransactionBlockedAml(CryptoEvent event, String correlationId) {
        log.warn("Processing AML-blocked transaction: txId={}, reason={}", event.getTransactionId(), event.getReason());

        // Block transaction with AML status
        cryptoTransactionService.blockTransactionAml(event.getTransactionId(), event.getReason(), correlationId);

        // Release reserved funds
        cryptoWalletService.releaseReservedFunds(event.getCryptoWalletId(), event.getTransactionId(), correlationId);

        // Flag wallet for AML review
        cryptoWalletService.flagForAmlReview(event.getCryptoWalletId(), event.getReason(), correlationId);

        // Create AML investigation
        complianceService.createAmlInvestigation(event.getTransactionId(), event.getUserId(),
            event.getReason(), correlationId);

        // Alert AML team
        notificationService.sendOperationalAlert(
            "Crypto Transaction AML Block",
            String.format("Transaction %s blocked for AML review: %s", event.getTransactionId(), event.getReason()),
            "HIGH"
        );

        // Notify user
        notificationService.sendNotification(event.getUserId(),
            "Transaction Under Review",
            "Your transaction is under review for regulatory compliance. We will contact you if additional information is needed.",
            correlationId);

        log.warn("AML transaction blocked: txId={}, reason={}", event.getTransactionId(), event.getReason());
    }

    private void processTransactionBlockedSanctions(CryptoEvent event, String correlationId) {
        log.error("Processing sanctions-blocked transaction: txId={}, reason={}", event.getTransactionId(), event.getReason());

        // Immediately block transaction - sanctions are zero tolerance
        cryptoTransactionService.blockTransactionSanctions(event.getTransactionId(), event.getReason(), correlationId);

        // Release reserved funds
        cryptoWalletService.releaseReservedFunds(event.getCryptoWalletId(), event.getTransactionId(), correlationId);

        // Immediately freeze entire wallet pending investigation
        cryptoWalletService.freezeWallet(event.getCryptoWalletId(), "SANCTIONS_SCREENING", correlationId);

        // Create emergency sanctions case
        complianceService.createEmergencySanctionsCase(event.getTransactionId(), event.getUserId(),
            event.getReason(), correlationId);

        // Send emergency alert
        notificationService.sendEmergencyAlert(
            "SANCTIONS SCREENING ALERT",
            String.format("URGENT: Transaction %s blocked for sanctions screening: %s - wallet frozen",
                event.getTransactionId(), event.getReason()),
            Map.of("transactionId", event.getTransactionId(), "userId", event.getUserId(),
                "walletId", event.getCryptoWalletId())
        );

        // Do NOT notify user - sanctions screening notifications are handled separately
        // by compliance team to avoid tipping off potential bad actors

        log.error("SANCTIONS BLOCK: Transaction and wallet frozen: txId={}", event.getTransactionId());
    }

    private void processTransactionSuspendedVelocity(CryptoEvent event, String correlationId) {
        log.info("Processing velocity-suspended transaction: txId={}, reason={}", event.getTransactionId(), event.getReason());

        // Suspend transaction with velocity reason
        cryptoTransactionService.suspendTransactionVelocity(event.getTransactionId(), event.getReason(), correlationId);

        // Apply temporary velocity limits
        cryptoWalletService.applyVelocityLimits(event.getCryptoWalletId(), correlationId);

        // Schedule automatic retry in cooling-off period
        kafkaTemplate.send("crypto-transaction-scheduler", Map.of(
            "transactionId", event.getTransactionId(),
            "eventType", "VELOCITY_RETRY_SCHEDULED",
            "scheduledTime", Instant.now().plusSeconds(3600), // 1 hour cooling off
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Notify user of temporary suspension
        notificationService.sendNotification(event.getUserId(),
            "Transaction Temporarily Suspended",
            "Your transaction has been temporarily suspended due to velocity limits. It will be automatically retried in 1 hour.",
            correlationId);

        log.info("Velocity transaction suspended with auto-retry scheduled: txId={}", event.getTransactionId());
    }

    private void processTransactionRejectedLimits(CryptoEvent event, String correlationId) {
        log.info("Processing limits-rejected transaction: txId={}, reason={}", event.getTransactionId(), event.getReason());

        // Reject transaction due to limits
        cryptoTransactionService.rejectTransactionLimits(event.getTransactionId(), event.getReason(), correlationId);

        // Release reserved funds
        cryptoWalletService.releaseReservedFunds(event.getCryptoWalletId(), event.getTransactionId(), correlationId);

        // Check if limits can be increased
        boolean canIncreaseLimits = cryptoWalletService.canIncreaseLimits(event.getCryptoWalletId());

        // Notify user with actionable information
        String message = canIncreaseLimits ?
            String.format("Your transaction exceeds current limits: %s. You can request a limit increase in your account settings.", event.getReason()) :
            String.format("Your transaction exceeds maximum allowable limits: %s.", event.getReason());

        notificationService.sendNotification(event.getUserId(),
            "Transaction Limit Exceeded",
            message,
            correlationId);

        log.info("Limits transaction rejected: txId={}, canIncrease={}", event.getTransactionId(), canIncreaseLimits);
    }

    private void processTransactionBlockedHighRisk(CryptoEvent event, String correlationId) {
        log.warn("Processing high-risk blocked transaction: txId={}, reason={}", event.getTransactionId(), event.getReason());

        // Block transaction with high-risk status
        cryptoTransactionService.blockTransactionHighRisk(event.getTransactionId(), event.getReason(), correlationId);

        // Release reserved funds
        cryptoWalletService.releaseReservedFunds(event.getCryptoWalletId(), event.getTransactionId(), correlationId);

        // Enable enhanced monitoring
        cryptoWalletService.enableEnhancedMonitoring(event.getCryptoWalletId(), correlationId);

        // Create risk assessment case
        fraudDetectionService.createRiskAssessmentCase(event.getTransactionId(), event.getUserId(),
            event.getReason(), correlationId);

        // Alert risk management team
        notificationService.sendOperationalAlert(
            "High-Risk Crypto Transaction Blocked",
            String.format("Transaction %s blocked for high risk: %s", event.getTransactionId(), event.getReason()),
            "MEDIUM"
        );

        // Notify user
        notificationService.sendNotification(event.getUserId(),
            "Transaction Risk Review",
            "Your transaction requires additional risk assessment. Our team will review and contact you within 24 hours.",
            correlationId);

        log.warn("High-risk transaction blocked: txId={}, reason={}", event.getTransactionId(), event.getReason());
    }

    private void processTransactionBlockedManualReview(CryptoEvent event, String correlationId) {
        log.info("Processing manual review blocked transaction: txId={}, reason={}", event.getTransactionId(), event.getReason());

        // Block transaction pending manual review
        cryptoTransactionService.blockTransactionManualReview(event.getTransactionId(), event.getReason(), correlationId);

        // Keep funds reserved during review period
        cryptoWalletService.extendReservationPeriod(event.getCryptoWalletId(), event.getTransactionId(), correlationId);

        // Create manual review case with priority
        complianceService.createManualReviewCase(event.getTransactionId(), event.getUserId(),
            event.getReason(), "STANDARD", correlationId);

        // Alert review team
        notificationService.sendOperationalAlert(
            "Crypto Transaction Manual Review Required",
            String.format("Transaction %s requires manual review: %s", event.getTransactionId(), event.getReason()),
            "MEDIUM"
        );

        // Set SLA for review completion
        kafkaTemplate.send("crypto-review-scheduler", Map.of(
            "transactionId", event.getTransactionId(),
            "eventType", "MANUAL_REVIEW_SLA_REMINDER",
            "scheduledTime", Instant.now().plusSeconds(172800), // 48 hour SLA
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Notify user of review status
        notificationService.sendNotification(event.getUserId(),
            "Transaction Under Review",
            "Your transaction is under manual review. We will complete the review within 2 business days.",
            correlationId);

        log.info("Manual review transaction blocked: txId={}, 48h SLA set", event.getTransactionId());
    }
}