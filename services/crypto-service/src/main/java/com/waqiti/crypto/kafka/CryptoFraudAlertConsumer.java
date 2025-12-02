package com.waqiti.crypto.kafka;

import com.waqiti.crypto.events.model.CryptoEvent;
import com.waqiti.crypto.service.CryptoTransactionService;
import com.waqiti.crypto.service.CryptoWalletService;
import com.waqiti.crypto.repository.CryptoFraudEventRepository;
import com.waqiti.crypto.entity.CryptoFraudEvent;
import com.waqiti.crypto.security.FraudDetectionService;
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
public class CryptoFraudAlertConsumer {

    private final CryptoTransactionService cryptoTransactionService;
    private final CryptoWalletService cryptoWalletService;
    private final CryptoFraudEventRepository fraudEventRepository;
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
        successCounter = Counter.builder("crypto_fraud_alert_processed_total")
            .description("Total number of successfully processed crypto fraud alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("crypto_fraud_alert_errors_total")
            .description("Total number of crypto fraud alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("crypto_fraud_alert_processing_duration")
            .description("Time taken to process crypto fraud alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"crypto-fraud-alert", "crypto-fraud-detection", "crypto-security-alerts"},
        groupId = "crypto-fraud-alert-service-group",
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
    @CircuitBreaker(name = "crypto-fraud-alert", fallbackMethod = "handleCryptoFraudAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCryptoFraudAlertEvent(
            @Payload CryptoEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("fraud-%s-p%d-o%d", event.getTransactionId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getTransactionId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing crypto fraud alert: transactionId={}, alertType={}, riskLevel={}",
                event.getTransactionId(), event.getAlertType(), event.getStatus());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case "FRAUD_ALERT_GENERATED":
                    processFraudAlertGenerated(event, correlationId);
                    break;

                case "SUSPICIOUS_ACTIVITY_DETECTED":
                    processSuspiciousActivityDetected(event, correlationId);
                    break;

                case "VELOCITY_THRESHOLD_EXCEEDED":
                    processVelocityThresholdExceeded(event, correlationId);
                    break;

                case "PATTERN_ANOMALY_DETECTED":
                    processPatternAnomalyDetected(event, correlationId);
                    break;

                case "HIGH_RISK_ADDRESS_INTERACTION":
                    processHighRiskAddressInteraction(event, correlationId);
                    break;

                case "MIXER_SERVICE_DETECTED":
                    processMixerServiceDetected(event, correlationId);
                    break;

                case "LARGE_TRANSACTION_ALERT":
                    processLargeTransactionAlert(event, correlationId);
                    break;

                case "UNUSUAL_GEOGRAPHICAL_ACTIVITY":
                    processUnusualGeographicalActivity(event, correlationId);
                    break;

                default:
                    log.warn("Unknown crypto fraud alert event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("CRYPTO_FRAUD_ALERT_PROCESSED", event.getUserId(),
                Map.of("eventType", event.getEventType(), "alertType", event.getAlertType(),
                    "transactionId", event.getTransactionId(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process crypto fraud alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("crypto-fraud-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCryptoFraudAlertEventFallback(
            CryptoEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("fraud-fallback-%s-p%d-o%d", event.getTransactionId(), partition, offset);

        log.error("Circuit breaker fallback triggered for crypto fraud alert: transactionId={}, error={}",
            event.getTransactionId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("crypto-fraud-alert-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to security team
        try {
            notificationService.sendCriticalAlert(
                "Crypto Fraud Alert Circuit Breaker Triggered",
                String.format("CRITICAL: Fraud alert processing failed for transaction %s: %s", event.getTransactionId(), ex.getMessage()),
                Map.of("transactionId", event.getTransactionId(), "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCryptoFraudAlertEvent(
            @Payload CryptoEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-fraud-%s-%d", event.getTransactionId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Crypto fraud alert permanently failed: transactionId={}, topic={}, error={}",
            event.getTransactionId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("CRYPTO_FRAUD_ALERT_DLT_EVENT", event.getUserId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert to security team
        try {
            notificationService.sendEmergencyAlert(
                "CRYPTO FRAUD ALERT DLT EVENT",
                String.format("EMERGENCY: Fraud alert for transaction %s sent to DLT - immediate manual review required: %s",
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

    private void processFraudAlertGenerated(CryptoEvent event, String correlationId) {
        log.info("Processing fraud alert generated: transactionId={}, alertType={}",
            event.getTransactionId(), event.getAlertType());

        // Create fraud event record
        CryptoFraudEvent fraudEvent = CryptoFraudEvent.builder()
            .transactionId(event.getTransactionId())
            .userId(event.getUserId())
            .walletId(event.getCryptoWalletId())
            .alertType(event.getAlertType())
            .riskLevel(event.getStatus())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        fraudEventRepository.save(fraudEvent);

        // Immediately halt transaction if high risk
        if ("HIGH".equals(event.getStatus()) || "CRITICAL".equals(event.getStatus())) {
            cryptoTransactionService.holdTransaction(event.getTransactionId(), "FRAUD_ALERT", correlationId);

            // Send critical alert
            notificationService.sendCriticalAlert(
                "High Risk Crypto Transaction Detected",
                String.format("Transaction %s flagged for high fraud risk - automatically held for review", event.getTransactionId()),
                Map.of("transactionId", event.getTransactionId(), "alertType", event.getAlertType())
            );
        }

        log.info("Fraud alert processed: transactionId={}, alertType={}", event.getTransactionId(), event.getAlertType());
    }

    private void processSuspiciousActivityDetected(CryptoEvent event, String correlationId) {
        log.info("Processing suspicious activity detected: walletId={}", event.getCryptoWalletId());

        // Flag wallet for enhanced monitoring
        cryptoWalletService.enableEnhancedMonitoring(event.getCryptoWalletId(), correlationId);

        // Create investigation case
        fraudDetectionService.createInvestigationCase(event.getCryptoWalletId(),
            "SUSPICIOUS_ACTIVITY", event.getDescription(), correlationId);

        // Notify compliance team
        notificationService.sendOperationalAlert(
            "Suspicious Crypto Activity Detected",
            String.format("Wallet %s flagged for suspicious activity - enhanced monitoring enabled", event.getCryptoWalletId()),
            "MEDIUM"
        );

        log.info("Suspicious activity processed: walletId={}", event.getCryptoWalletId());
    }

    private void processVelocityThresholdExceeded(CryptoEvent event, String correlationId) {
        log.info("Processing velocity threshold exceeded: walletId={}", event.getCryptoWalletId());

        // Temporarily reduce transaction limits
        cryptoWalletService.applyVelocityLimits(event.getCryptoWalletId(), correlationId);

        // Alert user of temporary restrictions
        notificationService.sendNotification(event.getUserId(),
            "Transaction Velocity Limit Reached",
            "Your account has reached daily transaction limits. Normal limits will resume in 24 hours.",
            correlationId);

        // Schedule automatic limit reset
        kafkaTemplate.send("crypto-velocity-scheduler", Map.of(
            "walletId", event.getCryptoWalletId(),
            "eventType", "VELOCITY_LIMIT_RESET",
            "scheduledTime", Instant.now().plusSeconds(86400), // 24 hours
            "correlationId", correlationId
        ));

        log.info("Velocity threshold processed: walletId={}", event.getCryptoWalletId());
    }

    private void processPatternAnomalyDetected(CryptoEvent event, String correlationId) {
        log.info("Processing pattern anomaly detected: transactionId={}", event.getTransactionId());

        // Score the anomaly
        double anomalyScore = fraudDetectionService.calculateAnomalyScore(event.getTransactionId());

        if (anomalyScore > 0.8) {
            // High anomaly score - block transaction
            cryptoTransactionService.blockTransaction(event.getTransactionId(), "PATTERN_ANOMALY_HIGH", correlationId);

            notificationService.sendOperationalAlert(
                "High Anomaly Score Transaction",
                String.format("Transaction %s blocked due to high pattern anomaly score: %.2f",
                    event.getTransactionId(), anomalyScore),
                "HIGH"
            );
        } else if (anomalyScore > 0.5) {
            // Medium anomaly score - require additional verification
            cryptoTransactionService.requireAdditionalVerification(event.getTransactionId(), correlationId);
        }

        log.info("Pattern anomaly processed: transactionId={}, score={}", event.getTransactionId(), anomalyScore);
    }

    private void processHighRiskAddressInteraction(CryptoEvent event, String correlationId) {
        log.info("Processing high-risk address interaction: transactionId={}", event.getTransactionId());

        // Immediately block the transaction
        cryptoTransactionService.blockTransaction(event.getTransactionId(), "HIGH_RISK_ADDRESS", correlationId);

        // Flag the wallet for investigation
        cryptoWalletService.flagForInvestigation(event.getCryptoWalletId(), "HIGH_RISK_ADDRESS_INTERACTION", correlationId);

        // Send critical alert
        notificationService.sendCriticalAlert(
            "High-Risk Address Interaction Blocked",
            String.format("Transaction %s blocked - interaction with known high-risk address", event.getTransactionId()),
            Map.of("transactionId", event.getTransactionId(), "walletId", event.getCryptoWalletId())
        );

        log.info("High-risk address interaction processed: transactionId={}", event.getTransactionId());
    }

    private void processMixerServiceDetected(CryptoEvent event, String correlationId) {
        log.info("Processing mixer service detected: transactionId={}", event.getTransactionId());

        // Block transaction immediately - mixer services indicate potential money laundering
        cryptoTransactionService.blockTransaction(event.getTransactionId(), "MIXER_SERVICE_DETECTED", correlationId);

        // Freeze wallet pending investigation
        cryptoWalletService.freezeWallet(event.getCryptoWalletId(), "MIXER_SERVICE_INTERACTION", correlationId);

        // Send emergency alert to compliance team
        notificationService.sendEmergencyAlert(
            "MIXER SERVICE INTERACTION DETECTED",
            String.format("URGENT: Transaction %s involves mixer service - wallet frozen pending investigation",
                event.getTransactionId()),
            Map.of("transactionId", event.getTransactionId(), "walletId", event.getCryptoWalletId())
        );

        log.error("Mixer service detected - emergency protocols activated: transactionId={}", event.getTransactionId());
    }

    private void processLargeTransactionAlert(CryptoEvent event, String correlationId) {
        log.info("Processing large transaction alert: transactionId={}, amount={}",
            event.getTransactionId(), event.getAmount());

        // For very large transactions, require manual approval
        if (event.getAmount().compareTo(event.getTotalValue()) > 0) {
            cryptoTransactionService.requireManualApproval(event.getTransactionId(), correlationId);

            notificationService.sendOperationalAlert(
                "Large Transaction Requires Approval",
                String.format("Transaction %s for %s %s requires manual approval",
                    event.getTransactionId(), event.getAmount(), event.getCryptoSymbol()),
                "MEDIUM"
            );
        }

        log.info("Large transaction alert processed: transactionId={}", event.getTransactionId());
    }

    private void processUnusualGeographicalActivity(CryptoEvent event, String correlationId) {
        log.info("Processing unusual geographical activity: walletId={}", event.getCryptoWalletId());

        // Require additional authentication for next 24 hours
        cryptoWalletService.requireEnhancedAuth(event.getCryptoWalletId(), 86400, correlationId); // 24 hours

        // Notify user of security measure
        notificationService.sendNotification(event.getUserId(),
            "Security Alert: Unusual Location Activity",
            "We've detected activity from an unusual location. Enhanced security is now active for 24 hours.",
            correlationId);

        log.info("Unusual geographical activity processed: walletId={}", event.getCryptoWalletId());
    }
}