package com.waqiti.payment.kafka;

import com.waqiti.common.events.BatchPaymentFraudAlertEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.domain.FraudAlert;
import com.waqiti.payment.repository.FraudAlertRepository;
import com.waqiti.payment.service.FraudDetectionService;
import com.waqiti.payment.service.BatchProcessingService;
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
public class BatchPaymentFraudAlertsConsumer {

    private final FraudAlertRepository fraudAlertRepository;
    private final FraudDetectionService fraudDetectionService;
    private final BatchProcessingService batchProcessingService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("batch_payment_fraud_alerts_processed_total")
            .description("Total number of successfully processed batch payment fraud alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("batch_payment_fraud_alerts_errors_total")
            .description("Total number of batch payment fraud alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("batch_payment_fraud_alerts_processing_duration")
            .description("Time taken to process batch payment fraud alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"batch-payment-fraud-alerts", "bulk-fraud-detection-alerts", "batch-suspicious-activity"},
        groupId = "batch-payment-fraud-alerts-service-group",
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
    @CircuitBreaker(name = "batch-payment-fraud-alerts", fallbackMethod = "handleBatchPaymentFraudAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBatchPaymentFraudAlertEvent(
            @Payload BatchPaymentFraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("batch-fraud-alert-%s-p%d-o%d", event.getBatchId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getBatchId(), event.getFraudType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing batch payment fraud alert: batchId={}, type={}, severity={}",
                event.getBatchId(), event.getFraudType(), event.getSeverity());

            cleanExpiredEntries();

            switch (event.getFraudType()) {
                case SUSPICIOUS_PATTERN_DETECTED:
                    processSuspiciousPatternDetected(event, correlationId);
                    break;

                case VELOCITY_FRAUD:
                    processVelocityFraud(event, correlationId);
                    break;

                case AMOUNT_ANOMALY:
                    processAmountAnomaly(event, correlationId);
                    break;

                case GEOGRAPHIC_ANOMALY:
                    processGeographicAnomaly(event, correlationId);
                    break;

                case BEHAVIORAL_ANOMALY:
                    processBehavioralAnomaly(event, correlationId);
                    break;

                case BLACKLIST_MATCH:
                    processBlacklistMatch(event, correlationId);
                    break;

                case ML_MODEL_ALERT:
                    processMlModelAlert(event, correlationId);
                    break;

                case RULE_ENGINE_ALERT:
                    processRuleEngineAlert(event, correlationId);
                    break;

                case COORDINATED_ATTACK:
                    processCoordinatedAttack(event, correlationId);
                    break;

                case ACCOUNT_TAKEOVER:
                    processAccountTakeover(event, correlationId);
                    break;

                default:
                    log.warn("Unknown batch payment fraud alert type: {}", event.getFraudType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("BATCH_PAYMENT_FRAUD_ALERT_PROCESSED", event.getBatchId(),
                Map.of("fraudType", event.getFraudType(), "severity", event.getSeverity(),
                    "suspiciousTransactions", event.getSuspiciousTransactionCount(),
                    "riskScore", event.getRiskScore(), "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process batch payment fraud alert: {}", e.getMessage(), e);

            // Send to DLQ with context
            dlqHandler.handleFailedMessage(
                "batch-payment-fraud-alerts",
                event,
                e,
                Map.of(
                    "batchId", event.getBatchId(),
                    "fraudType", event.getFraudType().toString(),
                    "severity", event.getSeverity(),
                    "riskScore", String.valueOf(event.getRiskScore()),
                    "correlationId", correlationId,
                    "partition", String.valueOf(partition),
                    "offset", String.valueOf(offset)
                )
            );

            kafkaTemplate.send("batch-payment-fraud-alerts-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBatchPaymentFraudAlertEventFallback(
            BatchPaymentFraudAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("batch-fraud-alert-fallback-%s-p%d-o%d", event.getBatchId(), partition, offset);

        log.error("Circuit breaker fallback triggered for batch payment fraud alert: batchId={}, error={}",
            event.getBatchId(), ex.getMessage());

        kafkaTemplate.send("batch-payment-fraud-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBatchPaymentFraudAlertEvent(
            @Payload BatchPaymentFraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-batch-fraud-alert-%s-%d", event.getBatchId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Batch payment fraud alert permanently failed: batchId={}, error={}",
            event.getBatchId(), exceptionMessage);

        auditService.logPaymentEvent("BATCH_PAYMENT_FRAUD_ALERT_DLT_EVENT", event.getBatchId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "fraudType", event.getFraudType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) return false;
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

    private void processSuspiciousPatternDetected(BatchPaymentFraudAlertEvent event, String correlationId) {
        FraudAlert alert = FraudAlert.builder()
            .batchId(event.getBatchId())
            .fraudType("SUSPICIOUS_PATTERN_DETECTED")
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .suspiciousTransactionCount(event.getSuspiciousTransactionCount())
            .patternDetails(event.getPatternDetails())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        fraudAlertRepository.save(alert);

        // Analyze suspicious pattern
        fraudDetectionService.analyzeSuspiciousPattern(
            event.getBatchId(),
            event.getPatternDetails(),
            event.getSuspiciousTransactionIds(),
            correlationId
        );

        // Pause batch if risk is too high
        if (event.getRiskScore() > 80.0) {
            batchProcessingService.pauseBatch(event.getBatchId(), "High fraud risk detected", correlationId);
        }

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Suspicious Pattern Detected in Batch",
            String.format("Batch %s: Suspicious pattern detected affecting %d transactions (Risk: %.1f)",
                event.getBatchId(), event.getSuspiciousTransactionCount(), event.getRiskScore()),
            Map.of("batchId", event.getBatchId(), "riskScore", event.getRiskScore(), "correlationId", correlationId)
        );

        metricsService.recordBatchFraudAlert(event.getBatchId(), "SUSPICIOUS_PATTERN", event.getRiskScore());
    }

    private void processVelocityFraud(BatchPaymentFraudAlertEvent event, String correlationId) {
        FraudAlert alert = FraudAlert.builder()
            .batchId(event.getBatchId())
            .fraudType("VELOCITY_FRAUD")
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .velocityThreshold(event.getVelocityThreshold())
            .actualVelocity(event.getActualVelocity())
            .timeWindow(event.getTimeWindow())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        fraudAlertRepository.save(alert);

        // Handle velocity fraud
        fraudDetectionService.handleVelocityFraud(
            event.getBatchId(),
            event.getActualVelocity(),
            event.getVelocityThreshold(),
            event.getTimeWindow(),
            correlationId
        );

        // Implement velocity controls
        batchProcessingService.implementVelocityControls(
            event.getBatchId(),
            event.getVelocityThreshold(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Velocity Fraud Detected in Batch",
            String.format("Batch %s: Velocity fraud detected - %d transactions in %d minutes (threshold: %d)",
                event.getBatchId(), event.getActualVelocity(), event.getTimeWindow(), event.getVelocityThreshold()),
            "HIGH"
        );

        metricsService.recordBatchFraudAlert(event.getBatchId(), "VELOCITY_FRAUD", event.getRiskScore());
    }

    private void processAmountAnomaly(BatchPaymentFraudAlertEvent event, String correlationId) {
        FraudAlert alert = FraudAlert.builder()
            .batchId(event.getBatchId())
            .fraudType("AMOUNT_ANOMALY")
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .anomalousAmounts(event.getAnomalousAmounts())
            .expectedAmountRange(event.getExpectedAmountRange())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        fraudAlertRepository.save(alert);

        // Analyze amount anomalies
        fraudDetectionService.analyzeAmountAnomalies(
            event.getBatchId(),
            event.getAnomalousAmounts(),
            event.getExpectedAmountRange(),
            correlationId
        );

        // Flag anomalous transactions for review
        fraudDetectionService.flagTransactionsForReview(
            event.getSuspiciousTransactionIds(),
            "Amount anomaly detected",
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Amount Anomaly Detected in Batch",
            String.format("Batch %s: Amount anomalies detected in %d transactions",
                event.getBatchId(), event.getAnomalousAmounts().size()),
            "MEDIUM"
        );

        metricsService.recordBatchFraudAlert(event.getBatchId(), "AMOUNT_ANOMALY", event.getRiskScore());
    }

    private void processGeographicAnomaly(BatchPaymentFraudAlertEvent event, String correlationId) {
        FraudAlert alert = FraudAlert.builder()
            .batchId(event.getBatchId())
            .fraudType("GEOGRAPHIC_ANOMALY")
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .suspiciousLocations(event.getSuspiciousLocations())
            .geographicRiskFactors(event.getGeographicRiskFactors())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        fraudAlertRepository.save(alert);

        // Analyze geographic anomalies
        fraudDetectionService.analyzeGeographicAnomalies(
            event.getBatchId(),
            event.getSuspiciousLocations(),
            event.getGeographicRiskFactors(),
            correlationId
        );

        // Apply geographic risk controls
        fraudDetectionService.applyGeographicRiskControls(
            event.getSuspiciousTransactionIds(),
            event.getSuspiciousLocations(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Geographic Anomaly Detected in Batch",
            String.format("Batch %s: Geographic anomalies detected from %d suspicious locations",
                event.getBatchId(), event.getSuspiciousLocations().size()),
            "MEDIUM"
        );

        metricsService.recordBatchFraudAlert(event.getBatchId(), "GEOGRAPHIC_ANOMALY", event.getRiskScore());
    }

    private void processBehavioralAnomaly(BatchPaymentFraudAlertEvent event, String correlationId) {
        FraudAlert alert = FraudAlert.builder()
            .batchId(event.getBatchId())
            .fraudType("BEHAVIORAL_ANOMALY")
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .behavioralIndicators(event.getBehavioralIndicators())
            .deviationScore(event.getDeviationScore())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        fraudAlertRepository.save(alert);

        // Analyze behavioral anomalies
        fraudDetectionService.analyzeBehavioralAnomalies(
            event.getBatchId(),
            event.getBehavioralIndicators(),
            event.getDeviationScore(),
            correlationId
        );

        // Update user behavior profiles
        fraudDetectionService.updateBehaviorProfiles(
            event.getAffectedUserIds(),
            event.getBehavioralIndicators(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Behavioral Anomaly Detected in Batch",
            String.format("Batch %s: Behavioral anomalies detected (deviation score: %.2f)",
                event.getBatchId(), event.getDeviationScore()),
            "MEDIUM"
        );

        metricsService.recordBatchFraudAlert(event.getBatchId(), "BEHAVIORAL_ANOMALY", event.getRiskScore());
    }

    private void processBlacklistMatch(BatchPaymentFraudAlertEvent event, String correlationId) {
        FraudAlert alert = FraudAlert.builder()
            .batchId(event.getBatchId())
            .fraudType("BLACKLIST_MATCH")
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .blacklistMatches(event.getBlacklistMatches())
            .matchedEntities(event.getMatchedEntities())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        fraudAlertRepository.save(alert);

        // Handle blacklist matches
        fraudDetectionService.handleBlacklistMatches(
            event.getBatchId(),
            event.getBlacklistMatches(),
            event.getMatchedEntities(),
            correlationId
        );

        // Immediately block matched transactions
        fraudDetectionService.blockTransactions(
            event.getSuspiciousTransactionIds(),
            "Blacklist match detected",
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Blacklist Match Detected in Batch",
            String.format("Batch %s: %d blacklist matches detected",
                event.getBatchId(), event.getBlacklistMatches().size()),
            Map.of("batchId", event.getBatchId(), "matchCount", event.getBlacklistMatches().size(), "correlationId", correlationId)
        );

        metricsService.recordBatchFraudAlert(event.getBatchId(), "BLACKLIST_MATCH", event.getRiskScore());
    }

    private void processMlModelAlert(BatchPaymentFraudAlertEvent event, String correlationId) {
        FraudAlert alert = FraudAlert.builder()
            .batchId(event.getBatchId())
            .fraudType("ML_MODEL_ALERT")
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .modelName(event.getModelName())
            .modelVersion(event.getModelVersion())
            .confidence(event.getConfidence())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        fraudAlertRepository.save(alert);

        // Handle ML model alert
        fraudDetectionService.handleMlModelAlert(
            event.getBatchId(),
            event.getModelName(),
            event.getModelVersion(),
            event.getConfidence(),
            event.getSuspiciousTransactionIds(),
            correlationId
        );

        // Update model feedback
        fraudDetectionService.updateModelFeedback(
            event.getModelName(),
            event.getSuspiciousTransactionIds(),
            event.getRiskScore(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "ML Model Alert in Batch",
            String.format("Batch %s: ML model %s detected fraud (confidence: %.2f)",
                event.getBatchId(), event.getModelName(), event.getConfidence()),
            "HIGH"
        );

        metricsService.recordBatchFraudAlert(event.getBatchId(), "ML_MODEL_ALERT", event.getRiskScore());
    }

    private void processRuleEngineAlert(BatchPaymentFraudAlertEvent event, String correlationId) {
        FraudAlert alert = FraudAlert.builder()
            .batchId(event.getBatchId())
            .fraudType("RULE_ENGINE_ALERT")
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .triggeredRules(event.getTriggeredRules())
            .ruleCategories(event.getRuleCategories())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        fraudAlertRepository.save(alert);

        // Handle rule engine alerts
        fraudDetectionService.handleRuleEngineAlerts(
            event.getBatchId(),
            event.getTriggeredRules(),
            event.getRuleCategories(),
            correlationId
        );

        // Update rule effectiveness metrics
        fraudDetectionService.updateRuleEffectiveness(
            event.getTriggeredRules(),
            event.getSuspiciousTransactionIds(),
            correlationId
        );

        notificationService.sendOperationalAlert(
            "Rule Engine Alert in Batch",
            String.format("Batch %s: %d fraud rules triggered",
                event.getBatchId(), event.getTriggeredRules().size()),
            "MEDIUM"
        );

        metricsService.recordBatchFraudAlert(event.getBatchId(), "RULE_ENGINE_ALERT", event.getRiskScore());
    }

    private void processCoordinatedAttack(BatchPaymentFraudAlertEvent event, String correlationId) {
        FraudAlert alert = FraudAlert.builder()
            .batchId(event.getBatchId())
            .fraudType("COORDINATED_ATTACK")
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .attackPattern(event.getAttackPattern())
            .coordinationIndicators(event.getCoordinationIndicators())
            .attackScope(event.getAttackScope())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        fraudAlertRepository.save(alert);

        // Handle coordinated attack
        fraudDetectionService.handleCoordinatedAttack(
            event.getBatchId(),
            event.getAttackPattern(),
            event.getCoordinationIndicators(),
            event.getAttackScope(),
            correlationId
        );

        // Implement emergency countermeasures
        fraudDetectionService.implementEmergencyCountermeasures(
            event.getBatchId(),
            event.getAttackPattern(),
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Coordinated Attack Detected in Batch",
            String.format("Batch %s: Coordinated attack detected - %s (scope: %s)",
                event.getBatchId(), event.getAttackPattern(), event.getAttackScope()),
            Map.of("batchId", event.getBatchId(), "attackPattern", event.getAttackPattern(),
                "attackScope", event.getAttackScope(), "correlationId", correlationId)
        );

        metricsService.recordBatchFraudAlert(event.getBatchId(), "COORDINATED_ATTACK", event.getRiskScore());
    }

    private void processAccountTakeover(BatchPaymentFraudAlertEvent event, String correlationId) {
        FraudAlert alert = FraudAlert.builder()
            .batchId(event.getBatchId())
            .fraudType("ACCOUNT_TAKEOVER")
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .compromisedAccounts(event.getCompromisedAccounts())
            .takeoverIndicators(event.getTakeoverIndicators())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        fraudAlertRepository.save(alert);

        // Handle account takeover
        fraudDetectionService.handleAccountTakeover(
            event.getBatchId(),
            event.getCompromisedAccounts(),
            event.getTakeoverIndicators(),
            correlationId
        );

        // Immediately freeze compromised accounts
        fraudDetectionService.freezeCompromisedAccounts(
            event.getCompromisedAccounts(),
            "Account takeover detected",
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Account Takeover Detected in Batch",
            String.format("Batch %s: Account takeover detected affecting %d accounts",
                event.getBatchId(), event.getCompromisedAccounts().size()),
            Map.of("batchId", event.getBatchId(), "compromisedAccounts", event.getCompromisedAccounts().size(),
                "correlationId", correlationId)
        );

        metricsService.recordBatchFraudAlert(event.getBatchId(), "ACCOUNT_TAKEOVER", event.getRiskScore());
    }
}