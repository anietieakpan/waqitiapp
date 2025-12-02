package com.waqiti.crypto.kafka;

import com.waqiti.crypto.events.model.CryptoEvent;
import com.waqiti.crypto.service.CryptoTransactionService;
import com.waqiti.crypto.service.CryptoWalletService;
import com.waqiti.crypto.compliance.ComplianceService;
import com.waqiti.crypto.compliance.SanctionsScreeningService;
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
public class CryptoComplianceCompletedConsumer {

    private final CryptoTransactionService cryptoTransactionService;
    private final CryptoWalletService cryptoWalletService;
    private final ComplianceService complianceService;
    private final SanctionsScreeningService sanctionsScreeningService;
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
        successCounter = Counter.builder("crypto_compliance_completed_processed_total")
            .description("Total number of successfully processed crypto compliance completed events")
            .register(meterRegistry);
        errorCounter = Counter.builder("crypto_compliance_completed_errors_total")
            .description("Total number of crypto compliance completed processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("crypto_compliance_completed_processing_duration")
            .description("Time taken to process crypto compliance completed events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"crypto-compliance-completed", "crypto-compliance-workflow", "crypto-regulatory-approvals"},
        groupId = "crypto-compliance-completed-service-group",
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
    @CircuitBreaker(name = "crypto-compliance-completed", fallbackMethod = "handleCryptoComplianceCompletedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCryptoComplianceCompletedEvent(
            @Payload CryptoEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("compliance-%s-p%d-o%d", event.getCryptoWalletId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getCryptoWalletId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing crypto compliance completed: walletId={}, status={}, complianceType={}",
                event.getCryptoWalletId(), event.getStatus(), event.getEventType());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case "COMPLIANCE_VERIFICATION_COMPLETED":
                    processComplianceVerification(event, correlationId);
                    break;

                case "KYC_VERIFICATION_COMPLETED":
                    processKycVerificationCompleted(event, correlationId);
                    break;

                case "AML_SCREENING_COMPLETED":
                    processAmlScreeningCompleted(event, correlationId);
                    break;

                case "SANCTIONS_SCREENING_COMPLETED":
                    processSanctionsScreeningCompleted(event, correlationId);
                    break;

                case "REGULATORY_APPROVAL_COMPLETED":
                    processRegulatoryApprovalCompleted(event, correlationId);
                    break;

                case "TRANSACTION_COMPLIANCE_APPROVED":
                    processTransactionComplianceApproved(event, correlationId);
                    break;

                case "WALLET_COMPLIANCE_VERIFIED":
                    processWalletComplianceVerified(event, correlationId);
                    break;

                default:
                    log.warn("Unknown crypto compliance event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("CRYPTO_COMPLIANCE_EVENT_PROCESSED", event.getUserId(),
                Map.of("eventType", event.getEventType(), "status", event.getStatus(),
                    "walletId", event.getCryptoWalletId(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process crypto compliance completed event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("crypto-compliance-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCryptoComplianceCompletedEventFallback(
            CryptoEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("compliance-fallback-%s-p%d-o%d", event.getCryptoWalletId(), partition, offset);

        log.error("Circuit breaker fallback triggered for crypto compliance: walletId={}, error={}",
            event.getCryptoWalletId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("crypto-compliance-completed-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Crypto Compliance Circuit Breaker Triggered",
                String.format("Crypto wallet %s compliance failed: %s", event.getCryptoWalletId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCryptoComplianceCompletedEvent(
            @Payload CryptoEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-compliance-%s-%d", event.getCryptoWalletId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Crypto compliance permanently failed: walletId={}, topic={}, error={}",
            event.getCryptoWalletId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("CRYPTO_COMPLIANCE_DLT_EVENT", event.getUserId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Crypto Compliance Dead Letter Event",
                String.format("Crypto wallet %s compliance sent to DLT: %s", event.getCryptoWalletId(), exceptionMessage),
                Map.of("walletId", event.getCryptoWalletId(), "topic", topic, "correlationId", correlationId)
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

    private void processComplianceVerification(CryptoEvent event, String correlationId) {
        log.info("Processing compliance verification completed: walletId={}", event.getCryptoWalletId());

        // Update wallet compliance status
        cryptoWalletService.updateComplianceStatus(event.getCryptoWalletId(), "VERIFIED", correlationId);

        // Notify user of completion
        notificationService.sendNotification(event.getUserId(), "Crypto Compliance Verified",
            "Your cryptocurrency wallet compliance verification has been completed successfully.",
            correlationId);

        // Trigger next workflow step
        kafkaTemplate.send("crypto-wallet-activation", Map.of(
            "walletId", event.getCryptoWalletId(),
            "eventType", "ACTIVATION_READY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Compliance verification processed: walletId={}", event.getCryptoWalletId());
    }

    private void processKycVerificationCompleted(CryptoEvent event, String correlationId) {
        log.info("Processing KYC verification completed: walletId={}", event.getCryptoWalletId());

        // Update KYC status
        complianceService.updateKycStatus(event.getCryptoWalletId(), "VERIFIED");

        // Check if full compliance is now complete
        if (complianceService.isFullyCompliant(event.getCryptoWalletId())) {
            kafkaTemplate.send("crypto-compliance-workflow", Map.of(
                "walletId", event.getCryptoWalletId(),
                "eventType", "FULL_COMPLIANCE_COMPLETED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("KYC verification processed: walletId={}", event.getCryptoWalletId());
    }

    private void processAmlScreeningCompleted(CryptoEvent event, String correlationId) {
        log.info("Processing AML screening completed: walletId={}", event.getCryptoWalletId());

        // Update AML screening status
        complianceService.updateAmlStatus(event.getCryptoWalletId(), event.getStatus());

        // If high-risk detected, notify compliance team
        if ("HIGH_RISK".equals(event.getStatus())) {
            notificationService.sendOperationalAlert(
                "High-Risk AML Alert",
                String.format("Wallet %s flagged for high AML risk", event.getCryptoWalletId()),
                "HIGH"
            );

            // Freeze wallet pending manual review
            cryptoWalletService.freezeWallet(event.getCryptoWalletId(), "AML_HIGH_RISK", correlationId);
        }

        log.info("AML screening processed: walletId={}, status={}", event.getCryptoWalletId(), event.getStatus());
    }

    private void processSanctionsScreeningCompleted(CryptoEvent event, String correlationId) {
        log.info("Processing sanctions screening completed: walletId={}", event.getCryptoWalletId());

        // Update sanctions screening status
        sanctionsScreeningService.updateScreeningStatus(event.getCryptoWalletId(), event.getStatus());

        // If sanctions match found, immediate escalation
        if ("MATCH_FOUND".equals(event.getStatus())) {
            notificationService.sendCriticalAlert(
                "SANCTIONS MATCH DETECTED",
                String.format("URGENT: Wallet %s has sanctions match - immediate action required", event.getCryptoWalletId()),
                Map.of("walletId", event.getCryptoWalletId(), "correlationId", correlationId)
            );

            // Immediately block all wallet operations
            cryptoWalletService.blockWallet(event.getCryptoWalletId(), "SANCTIONS_MATCH", correlationId);
        }

        log.info("Sanctions screening processed: walletId={}, status={}", event.getCryptoWalletId(), event.getStatus());
    }

    private void processRegulatoryApprovalCompleted(CryptoEvent event, String correlationId) {
        log.info("Processing regulatory approval completed: walletId={}", event.getCryptoWalletId());

        // Update regulatory approval status
        complianceService.updateRegulatoryStatus(event.getCryptoWalletId(), event.getStatus());

        // If approved, enable enhanced features
        if ("APPROVED".equals(event.getStatus())) {
            cryptoWalletService.enableEnhancedFeatures(event.getCryptoWalletId(), correlationId);
        }

        log.info("Regulatory approval processed: walletId={}, status={}", event.getCryptoWalletId(), event.getStatus());
    }

    private void processTransactionComplianceApproved(CryptoEvent event, String correlationId) {
        log.info("Processing transaction compliance approved: transactionId={}", event.getTransactionId());

        // Approve pending transaction
        cryptoTransactionService.approveTransaction(event.getTransactionId(), correlationId);

        // Notify user
        notificationService.sendNotification(event.getUserId(), "Transaction Approved",
            "Your cryptocurrency transaction has been approved and is being processed.",
            correlationId);

        log.info("Transaction compliance approved: transactionId={}", event.getTransactionId());
    }

    private void processWalletComplianceVerified(CryptoEvent event, String correlationId) {
        log.info("Processing wallet compliance verified: walletId={}", event.getCryptoWalletId());

        // Mark wallet as fully compliant and active
        cryptoWalletService.activateWallet(event.getCryptoWalletId(), correlationId);

        // Send welcome message
        notificationService.sendNotification(event.getUserId(), "Crypto Wallet Activated",
            "Your cryptocurrency wallet is now fully activated and ready for trading.",
            correlationId);

        // Trigger wallet activation events
        kafkaTemplate.send("crypto-wallet-lifecycle", Map.of(
            "walletId", event.getCryptoWalletId(),
            "userId", event.getUserId(),
            "eventType", "WALLET_ACTIVATED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Wallet compliance verified: walletId={}", event.getCryptoWalletId());
    }
}