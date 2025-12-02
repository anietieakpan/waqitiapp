package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.SuspiciousTransactionEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.frauddetection.service.FraudDetectionService;
import com.waqiti.frauddetection.model.SuspiciousTransaction;
import com.waqiti.frauddetection.model.RiskScore;
import com.waqiti.frauddetection.repository.SuspiciousTransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Consumer for processing suspicious transaction events.
 * Analyzes transaction patterns and triggers fraud detection workflows.
 */
@Slf4j
@Component
public class SuspiciousTransactionConsumer extends BaseKafkaConsumer<SuspiciousTransactionEvent> {

    private static final String TOPIC = "suspicious-transaction-events";
    private static final Set<String> HIGH_RISK_PATTERNS = Set.of(
        "VELOCITY_BREACH", "LOCATION_ANOMALY", "AMOUNT_ANOMALY",
        "TIME_ANOMALY", "MERCHANT_RISK", "DEVICE_CHANGE"
    );

    private final FraudDetectionService fraudDetectionService;
    private final SuspiciousTransactionRepository suspiciousTransactionRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter fraudConfirmedCounter;
    private final Counter falsePositiveCounter;
    private final Timer processingTimer;

    @Autowired
    public SuspiciousTransactionConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            FraudDetectionService fraudDetectionService,
            SuspiciousTransactionRepository suspiciousTransactionRepository) {
        super(objectMapper, TOPIC);
        this.fraudDetectionService = fraudDetectionService;
        this.suspiciousTransactionRepository = suspiciousTransactionRepository;

        this.processedCounter = Counter.builder("suspicious_transaction_processed_total")
                .description("Total suspicious transactions processed")
                .register(meterRegistry);
        this.fraudConfirmedCounter = Counter.builder("fraud_confirmed_total")
                .description("Total confirmed fraud cases")
                .register(meterRegistry);
        this.falsePositiveCounter = Counter.builder("false_positive_total")
                .description("Total false positive cases")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("suspicious_transaction_processing_duration")
                .description("Time taken to process suspicious transactions")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "fraud-service-suspicious-transaction-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing suspicious transaction event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            SuspiciousTransactionEvent event = deserializeEvent(record.value(), SuspiciousTransactionEvent.class);

            // Validate required fields
            validateEvent(event);

            // Check for duplicate processing
            if (isAlreadyProcessed(event.getTransactionId(), event.getEventId())) {
                log.info("Suspicious transaction already processed: {}", event.getTransactionId());
                ack.acknowledge();
                return;
            }

            // Process the suspicious transaction
            processSuspiciousTransaction(event);

            processedCounter.increment();
            log.info("Successfully processed suspicious transaction: {}", event.getTransactionId());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing suspicious transaction event: {}", record.value(), e);
            throw new RuntimeException("Failed to process suspicious transaction event", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processSuspiciousTransaction(SuspiciousTransactionEvent event) {
        try {
            // Create suspicious transaction record
            SuspiciousTransaction suspiciousTransaction = createSuspiciousTransaction(event);

            // Calculate risk score
            RiskScore riskScore = fraudDetectionService.calculateRiskScore(
                event.getCustomerId(),
                event.getTransactionAmount(),
                event.getMerchantId(),
                event.getTransactionType(),
                event.getLocation(),
                event.getDeviceFingerprint()
            );

            suspiciousTransaction.setRiskScore(riskScore.getScore());
            suspiciousTransaction.setRiskLevel(riskScore.getLevel());
            suspiciousTransaction.setRiskFactors(riskScore.getFactors());

            // Determine if immediate action is required
            if (requiresImmediateAction(event, riskScore)) {
                handleHighRiskTransaction(event, suspiciousTransaction);
            } else {
                handleMediumRiskTransaction(event, suspiciousTransaction);
            }

            // Save the suspicious transaction
            suspiciousTransactionRepository.save(suspiciousTransaction);

            // Trigger ML model update if pattern is new
            if (isNewFraudPattern(event)) {
                fraudDetectionService.updateMlModel(event);
            }

            log.info("Processed suspicious transaction: {} with risk score: {}",
                    event.getTransactionId(), riskScore.getScore());

        } catch (Exception e) {
            log.error("Error processing suspicious transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to process suspicious transaction", e);
        }
    }

    private SuspiciousTransaction createSuspiciousTransaction(SuspiciousTransactionEvent event) {
        return SuspiciousTransaction.builder()
                .transactionId(event.getTransactionId())
                .customerId(event.getCustomerId())
                .amount(event.getTransactionAmount())
                .currency(event.getCurrency())
                .merchantId(event.getMerchantId())
                .merchantName(event.getMerchantName())
                .transactionType(event.getTransactionType())
                .suspicionReasons(event.getSuspicionReasons())
                .detectionMethod(event.getDetectionMethod())
                .location(event.getLocation())
                .deviceFingerprint(event.getDeviceFingerprint())
                .transactionTime(event.getTransactionTime())
                .detectedAt(LocalDateTime.now())
                .status("UNDER_REVIEW")
                .investigationPriority(determinePriority(event))
                .build();
    }

    private boolean requiresImmediateAction(SuspiciousTransactionEvent event, RiskScore riskScore) {
        return riskScore.getScore() > 80 ||
               event.getTransactionAmount().compareTo(BigDecimal.valueOf(10000)) > 0 ||
               HIGH_RISK_PATTERNS.stream().anyMatch(pattern ->
                   event.getSuspicionReasons().contains(pattern)) ||
               fraudDetectionService.isBlacklistedMerchant(event.getMerchantId()) ||
               fraudDetectionService.isCompromisedDevice(event.getDeviceFingerprint());
    }

    private void handleHighRiskTransaction(SuspiciousTransactionEvent event, SuspiciousTransaction transaction) {
        try {
            // Immediate account freeze for very high risk
            if (transaction.getRiskScore() > 95) {
                fraudDetectionService.freezeAccount(event.getCustomerId(),
                    "HIGH_RISK_FRAUD_DETECTION", event.getTransactionId());
            }

            // Block transaction if still pending
            fraudDetectionService.blockTransaction(event.getTransactionId(),
                "SUSPICIOUS_ACTIVITY_DETECTED");

            // Create high priority investigation case
            fraudDetectionService.createInvestigationCase(event.getCustomerId(),
                event.getTransactionId(), "HIGH", transaction.getRiskScore());

            // Send immediate alerts
            fraudDetectionService.sendFraudAlert(event.getCustomerId(),
                event.getTransactionId(), "IMMEDIATE_ATTENTION_REQUIRED");

            transaction.setStatus("BLOCKED");
            transaction.setInvestigationPriority("HIGH");

            log.warn("High risk transaction blocked: {} for customer: {}",
                    event.getTransactionId(), event.getCustomerId());

        } catch (Exception e) {
            log.error("Error handling high risk transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle high risk transaction", e);
        }
    }

    private void handleMediumRiskTransaction(SuspiciousTransactionEvent event, SuspiciousTransaction transaction) {
        try {
            // Enhanced monitoring for medium risk
            fraudDetectionService.enableEnhancedMonitoring(event.getCustomerId(),
                event.getTransactionId());

            // Create standard investigation case
            fraudDetectionService.createInvestigationCase(event.getCustomerId(),
                event.getTransactionId(), "MEDIUM", transaction.getRiskScore());

            // Send standard alert
            fraudDetectionService.sendFraudAlert(event.getCustomerId(),
                event.getTransactionId(), "REVIEW_REQUIRED");

            transaction.setStatus("UNDER_REVIEW");
            transaction.setInvestigationPriority("MEDIUM");

        } catch (Exception e) {
            log.error("Error handling medium risk transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle medium risk transaction", e);
        }
    }

    private String determinePriority(SuspiciousTransactionEvent event) {
        if (event.getTransactionAmount().compareTo(BigDecimal.valueOf(50000)) > 0) {
            return "CRITICAL";
        } else if (event.getTransactionAmount().compareTo(BigDecimal.valueOf(10000)) > 0) {
            return "HIGH";
        } else if (HIGH_RISK_PATTERNS.stream().anyMatch(pattern ->
                   event.getSuspicionReasons().contains(pattern))) {
            return "HIGH";
        } else {
            return "MEDIUM";
        }
    }

    private boolean isNewFraudPattern(SuspiciousTransactionEvent event) {
        // Check if this combination of suspicion reasons represents a new pattern
        return fraudDetectionService.isNewPattern(event.getSuspicionReasons(),
            event.getTransactionType(), event.getMerchantId());
    }

    private boolean isAlreadyProcessed(String transactionId, String eventId) {
        return suspiciousTransactionRepository.existsByTransactionIdAndEventId(transactionId, eventId);
    }

    private void validateEvent(SuspiciousTransactionEvent event) {
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getTransactionAmount() == null || event.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        if (event.getSuspicionReasons() == null || event.getSuspicionReasons().isEmpty()) {
            throw new IllegalArgumentException("Suspicion reasons cannot be null or empty");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Suspicious transaction processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed suspicious transaction event - Key: {}, Time: {}ms", key, processingTime);
    }
}