package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.SuspiciousPatternEvent;
import com.waqiti.frauddetection.domain.SuspiciousPatternAlert;
import com.waqiti.frauddetection.repository.SuspiciousPatternAlertRepository;
import com.waqiti.frauddetection.service.PatternAnalysisService;
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
public class SuspiciousPatternEventsDlqConsumer {

    private final SuspiciousPatternAlertRepository patternAlertRepository;
    private final PatternAnalysisService patternAnalysisService;
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
    private Counter criticalPatternCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("suspicious_pattern_dlq_processed_total")
            .description("Total number of successfully processed suspicious pattern DLQ events")
            .register(meterRegistry);
        errorCounter = Counter.builder("suspicious_pattern_dlq_errors_total")
            .description("Total number of suspicious pattern DLQ processing errors")
            .register(meterRegistry);
        criticalPatternCounter = Counter.builder("suspicious_patterns_critical_total")
            .description("Total number of critical suspicious patterns requiring escalation")
            .register(meterRegistry);
        processingTimer = Timer.builder("suspicious_pattern_dlq_processing_duration")
            .description("Time taken to process suspicious pattern DLQ events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"suspicious-pattern-events-dlq", "fraud-pattern-detection-dlq", "behavioral-anomaly-dlq"},
        groupId = "suspicious-pattern-dlq-service-group",
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
    @CircuitBreaker(name = "suspicious-pattern-dlq", fallbackMethod = "handleSuspiciousPatternDlqEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleSuspiciousPatternDlqEvent(
            @Payload SuspiciousPatternEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("pattern-dlq-%s-p%d-o%d", event.getPatternId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getPatternId(), event.getPatternType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Suspicious pattern DLQ event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL suspicious pattern from DLQ: patternId={}, type={}, riskScore={}, topic={}",
                event.getPatternId(), event.getPatternType(), event.getRiskScore(), topic);

            // Clean expired entries periodically
            cleanExpiredEntries();

            // DLQ pattern events indicate critical fraud detection failures
            if (isCriticalSuspiciousPattern(event)) {
                criticalPatternCounter.increment();
                escalateCriticalPattern(event, correlationId, topic);
            }

            switch (event.getPatternType()) {
                case MONEY_LAUNDERING:
                    processMoneyLaunderingPatternDlq(event, correlationId, topic);
                    break;

                case TRANSACTION_STRUCTURING:
                    processTransactionStructuringPatternDlq(event, correlationId, topic);
                    break;

                case ACCOUNT_TAKEOVER:
                    processAccountTakeoverPatternDlq(event, correlationId, topic);
                    break;

                case CARD_TESTING:
                    processCardTestingPatternDlq(event, correlationId, topic);
                    break;

                case VELOCITY_ABUSE:
                    processVelocityAbusePatternDlq(event, correlationId, topic);
                    break;

                case SOCIAL_ENGINEERING:
                    processSocialEngineeringPatternDlq(event, correlationId, topic);
                    break;

                case SYNTHETIC_IDENTITY:
                    processSyntheticIdentityPatternDlq(event, correlationId, topic);
                    break;

                case MULE_ACCOUNT:
                    processMuleAccountPatternDlq(event, correlationId, topic);
                    break;

                case COLLUSION_FRAUD:
                    processCollusionFraudPatternDlq(event, correlationId, topic);
                    break;

                default:
                    processGenericSuspiciousPatternDlq(event, correlationId, topic);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logFraudEvent("SUSPICIOUS_PATTERN_DLQ_PROCESSED", event.getPatternId(),
                Map.of("patternType", event.getPatternType(), "riskScore", event.getRiskScore(),
                    "confidence", event.getConfidence(), "correlationId", correlationId,
                    "dlqTopic", topic, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process suspicious pattern DLQ event: {}", e.getMessage(), e);

            // Send to fraud analysis escalation
            sendFraudAnalysisEscalation(event, correlationId, topic, e);

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleSuspiciousPatternDlqEventFallback(
            SuspiciousPatternEvent event,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("pattern-dlq-fallback-%s-p%d-o%d", event.getPatternId(), partition, offset);

        log.error("Circuit breaker fallback triggered for suspicious pattern DLQ: patternId={}, topic={}, error={}",
            event.getPatternId(), topic, ex.getMessage());

        sendFraudAnalysisEscalation(event, correlationId, topic, ex);
        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltSuspiciousPatternEvent(
            @Payload SuspiciousPatternEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-pattern-%s-%d", event.getPatternId(), System.currentTimeMillis());

        log.error("CRITICAL: Suspicious pattern DLQ permanently failed - patternId={}, topic={}, error={}",
            event.getPatternId(), topic, exceptionMessage);

        auditService.logFraudEvent("SUSPICIOUS_PATTERN_DLQ_DLT_EVENT", event.getPatternId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "patternType", event.getPatternType(), "correlationId", correlationId,
                "requiresManualAnalysis", true, "timestamp", Instant.now()));

        sendFraudAnalysisEscalation(event, correlationId, topic, new RuntimeException(exceptionMessage));
    }

    private boolean isCriticalSuspiciousPattern(SuspiciousPatternEvent event) {
        return event.getRiskScore() >= 85.0 ||
               event.getConfidence() >= 0.9 ||
               Arrays.asList("MONEY_LAUNDERING", "TRANSACTION_STRUCTURING", "ACCOUNT_TAKEOVER",
                           "SYNTHETIC_IDENTITY", "MULE_ACCOUNT").contains(event.getPatternType().toString()) ||
               event.getFinancialImpact() > 25000.0;
    }

    private void processMoneyLaunderingPatternDlq(SuspiciousPatternEvent event, String correlationId, String topic) {
        SuspiciousPatternAlert alert = SuspiciousPatternAlert.builder()
            .patternId(event.getPatternId())
            .patternType("MONEY_LAUNDERING_DLQ")
            .severity("CRITICAL")
            .description(String.format("Money laundering pattern from DLQ: %s", event.getDescription()))
            .riskScore(event.getRiskScore())
            .confidence(event.getConfidence())
            .financialImpact(event.getFinancialImpact())
            .correlationId(correlationId)
            .source(topic)
            .status("REQUIRES_AML_INVESTIGATION")
            .detectedAt(LocalDateTime.now())
            .build();
        patternAlertRepository.save(alert);

        patternAnalysisService.initiateAmlInvestigation(event.getPatternId(), "DLQ_MONEY_LAUNDERING");
        escalationService.escalateMoneyLaunderingPattern(event, correlationId);

        // AML team notification
        notificationService.sendAmlAlert(
            "CRITICAL: Money Laundering Pattern from DLQ",
            String.format("Money laundering pattern %s detected from DLQ - AML investigation required", event.getPatternId()),
            "CRITICAL"
        );

        // Executive notification for high-value ML patterns
        if (event.getFinancialImpact() > 100000.0) {
            notificationService.sendExecutiveAlert(
                "CRITICAL: High-Value Money Laundering Pattern",
                String.format("High-value money laundering pattern detected with impact of $%.2f", event.getFinancialImpact()),
                Map.of("patternId", event.getPatternId(), "amount", event.getFinancialImpact(), "correlationId", correlationId)
            );
        }

        log.error("Money laundering pattern DLQ processed: patternId={}, impact=${}", event.getPatternId(), event.getFinancialImpact());
    }

    private void processTransactionStructuringPatternDlq(SuspiciousPatternEvent event, String correlationId, String topic) {
        patternAnalysisService.recordStructuringPattern(event.getPatternId(), event.getDescription(), "DLQ_SOURCE");
        escalationService.escalateStructuringPattern(event, correlationId);

        // BSA reporting requirement
        notificationService.sendBsaAlert(
            "Transaction Structuring Pattern from DLQ",
            String.format("Structuring pattern %s detected from DLQ - BSA reporting required", event.getPatternId()),
            Map.of("patternId", event.getPatternId(), "correlationId", correlationId)
        );

        log.error("Transaction structuring pattern DLQ processed: patternId={}", event.getPatternId());
    }

    private void processAccountTakeoverPatternDlq(SuspiciousPatternEvent event, String correlationId, String topic) {
        patternAnalysisService.recordAccountTakeoverPattern(event.getPatternId(), event.getDescription());
        escalationService.escalateAccountTakeoverPattern(event, correlationId);

        log.error("Account takeover pattern DLQ processed: patternId={}", event.getPatternId());
    }

    private void processCardTestingPatternDlq(SuspiciousPatternEvent event, String correlationId, String topic) {
        patternAnalysisService.recordCardTestingPattern(event.getPatternId(), event.getDescription());
        escalationService.escalateCardTestingPattern(event, correlationId);

        log.error("Card testing pattern DLQ processed: patternId={}", event.getPatternId());
    }

    private void processVelocityAbusePatternDlq(SuspiciousPatternEvent event, String correlationId, String topic) {
        patternAnalysisService.recordVelocityAbusePattern(event.getPatternId(), event.getDescription());
        escalationService.escalateVelocityAbusePattern(event, correlationId);

        log.error("Velocity abuse pattern DLQ processed: patternId={}", event.getPatternId());
    }

    private void processSocialEngineeringPatternDlq(SuspiciousPatternEvent event, String correlationId, String topic) {
        patternAnalysisService.recordSocialEngineeringPattern(event.getPatternId(), event.getDescription());
        escalationService.escalateSocialEngineeringPattern(event, correlationId);

        log.error("Social engineering pattern DLQ processed: patternId={}", event.getPatternId());
    }

    private void processSyntheticIdentityPatternDlq(SuspiciousPatternEvent event, String correlationId, String topic) {
        patternAnalysisService.recordSyntheticIdentityPattern(event.getPatternId(), event.getDescription());
        escalationService.escalateSyntheticIdentityPattern(event, correlationId);

        // Identity verification team notification
        notificationService.sendIdentityAlert(
            "CRITICAL: Synthetic Identity Pattern from DLQ",
            String.format("Synthetic identity pattern %s detected from DLQ", event.getPatternId()),
            "CRITICAL"
        );

        log.error("Synthetic identity pattern DLQ processed: patternId={}", event.getPatternId());
    }

    private void processMuleAccountPatternDlq(SuspiciousPatternEvent event, String correlationId, String topic) {
        patternAnalysisService.recordMuleAccountPattern(event.getPatternId(), event.getDescription());
        escalationService.escalateMuleAccountPattern(event, correlationId);

        log.error("Mule account pattern DLQ processed: patternId={}", event.getPatternId());
    }

    private void processCollusionFraudPatternDlq(SuspiciousPatternEvent event, String correlationId, String topic) {
        patternAnalysisService.recordCollusionFraudPattern(event.getPatternId(), event.getDescription());
        escalationService.escalateCollusionFraudPattern(event, correlationId);

        log.error("Collusion fraud pattern DLQ processed: patternId={}", event.getPatternId());
    }

    private void processGenericSuspiciousPatternDlq(SuspiciousPatternEvent event, String correlationId, String topic) {
        patternAnalysisService.recordGenericSuspiciousPattern(event.getPatternId(), event.getDescription(), "DLQ_GENERIC");
        escalationService.escalateGenericSuspiciousPattern(event, correlationId);

        log.warn("Generic suspicious pattern DLQ processed: patternId={}, type={}",
            event.getPatternId(), event.getPatternType());
    }

    private void escalateCriticalPattern(SuspiciousPatternEvent event, String correlationId, String topic) {
        try {
            notificationService.sendFraudAlert(
                "CRITICAL: Suspicious Pattern from DLQ Requires Immediate Investigation",
                String.format("Critical suspicious pattern %s from DLQ topic %s requires immediate fraud investigation. " +
                    "Type: %s, Risk Score: %.2f, Confidence: %.2f, Financial Impact: $%.2f",
                    event.getPatternId(), topic, event.getPatternType(), event.getRiskScore(),
                    event.getConfidence(), event.getFinancialImpact()),
                Map.of(
                    "patternId", event.getPatternId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "patternType", event.getPatternType(),
                    "riskScore", event.getRiskScore(),
                    "confidence", event.getConfidence(),
                    "financialImpact", event.getFinancialImpact(),
                    "priority", "CRITICAL_PATTERN_INVESTIGATION"
                )
            );
        } catch (Exception ex) {
            log.error("Failed to send critical pattern escalation: {}", ex.getMessage());
        }
    }

    private void sendFraudAnalysisEscalation(SuspiciousPatternEvent event, String correlationId, String topic, Exception ex) {
        try {
            notificationService.sendFraudAlert(
                "SYSTEM CRITICAL: Suspicious Pattern DLQ Processing Failure",
                String.format("CRITICAL SYSTEM FAILURE: Unable to process suspicious pattern from DLQ for pattern %s. " +
                    "This indicates a serious fraud detection system failure requiring immediate intervention. " +
                    "Topic: %s, Error: %s", event.getPatternId(), topic, ex.getMessage()),
                Map.of(
                    "patternId", event.getPatternId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "errorMessage", ex.getMessage(),
                    "priority", "FRAUD_DETECTION_CRITICAL_FAILURE"
                )
            );
        } catch (Exception notificationEx) {
            log.error("CRITICAL: Failed to send fraud analysis escalation for pattern DLQ failure: {}", notificationEx.getMessage());
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