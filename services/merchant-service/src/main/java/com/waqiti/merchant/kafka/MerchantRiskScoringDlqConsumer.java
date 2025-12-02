package com.waqiti.merchant.kafka;

import com.waqiti.common.events.MerchantRiskScoringEvent;
import com.waqiti.merchant.domain.MerchantRiskAssessment;
import com.waqiti.merchant.repository.MerchantRiskAssessmentRepository;
import com.waqiti.merchant.service.MerchantRiskService;
import com.waqiti.merchant.service.MerchantEscalationService;
import com.waqiti.merchant.metrics.MerchantMetricsService;
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
public class MerchantRiskScoringDlqConsumer {

    private final MerchantRiskAssessmentRepository riskAssessmentRepository;
    private final MerchantRiskService merchantRiskService;
    private final MerchantEscalationService escalationService;
    private final MerchantMetricsService metricsService;
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
    private Counter highRiskMerchantCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("merchant_risk_scoring_dlq_processed_total")
            .description("Total number of successfully processed merchant risk scoring DLQ events")
            .register(meterRegistry);
        errorCounter = Counter.builder("merchant_risk_scoring_dlq_errors_total")
            .description("Total number of merchant risk scoring DLQ processing errors")
            .register(meterRegistry);
        highRiskMerchantCounter = Counter.builder("merchant_high_risk_total")
            .description("Total number of high-risk merchants requiring escalation")
            .register(meterRegistry);
        processingTimer = Timer.builder("merchant_risk_scoring_dlq_processing_duration")
            .description("Time taken to process merchant risk scoring DLQ events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"merchant-risk-scoring.DLQ", "merchant-risk-alerts-dlq", "merchant-compliance-risk-dlq"},
        groupId = "merchant-risk-scoring-dlq-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "merchant-risk-scoring-dlq", fallbackMethod = "handleMerchantRiskScoringDlqEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleMerchantRiskScoringDlqEvent(
            @Payload MerchantRiskScoringEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("merchant-risk-dlq-%s-p%d-o%d", event.getMerchantId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getMerchantId(), event.getRiskScore(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Merchant risk scoring DLQ event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL merchant risk scoring from DLQ: merchantId={}, riskScore={}, riskLevel={}, topic={}",
                event.getMerchantId(), event.getRiskScore(), event.getRiskLevel(), topic);

            // Clean expired entries periodically
            cleanExpiredEntries();

            // DLQ risk scoring events indicate critical merchant risk assessment failures
            if (isHighRiskMerchant(event)) {
                highRiskMerchantCounter.increment();
                escalateHighRiskMerchant(event, correlationId, topic);
            }

            switch (event.getRiskLevel()) {
                case HIGH:
                    processHighRiskMerchantDlq(event, correlationId, topic);
                    break;

                case CRITICAL:
                    processCriticalRiskMerchantDlq(event, correlationId, topic);
                    break;

                case MEDIUM:
                    processMediumRiskMerchantDlq(event, correlationId, topic);
                    break;

                case LOW:
                    processLowRiskMerchantDlq(event, correlationId, topic);
                    break;

                case UNKNOWN:
                    processUnknownRiskMerchantDlq(event, correlationId, topic);
                    break;

                default:
                    processGenericRiskMerchantDlq(event, correlationId, topic);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logMerchantEvent("MERCHANT_RISK_SCORING_DLQ_PROCESSED", event.getMerchantId(),
                Map.of("riskScore", event.getRiskScore(), "riskLevel", event.getRiskLevel(),
                    "riskFactors", event.getRiskFactors(), "correlationId", correlationId,
                    "dlqTopic", topic, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process merchant risk scoring DLQ event: {}", e.getMessage(), e);

            // Send to risk management escalation for DLQ failures
            sendRiskManagementEscalation(event, correlationId, topic, e);

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleMerchantRiskScoringDlqEventFallback(
            MerchantRiskScoringEvent event,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("merchant-risk-dlq-fallback-%s-p%d-o%d", event.getMerchantId(), partition, offset);

        log.error("Circuit breaker fallback triggered for merchant risk scoring DLQ: merchantId={}, topic={}, error={}",
            event.getMerchantId(), topic, ex.getMessage());

        // Critical: risk scoring DLQ circuit breaker means risk management system failure
        sendRiskManagementEscalation(event, correlationId, topic, ex);

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltMerchantRiskScoringEvent(
            @Payload MerchantRiskScoringEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-merchant-risk-%s-%d", event.getMerchantId(), System.currentTimeMillis());

        log.error("CRITICAL: Merchant risk scoring DLQ permanently failed - merchantId={}, topic={}, error={}",
            event.getMerchantId(), topic, exceptionMessage);

        // Save to audit trail for risk management
        auditService.logMerchantEvent("MERCHANT_RISK_SCORING_DLQ_DLT_EVENT", event.getMerchantId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "riskLevel", event.getRiskLevel(), "correlationId", correlationId,
                "requiresManualRiskAssessment", true, "timestamp", Instant.now()));

        // Immediate risk management escalation for DLT failures
        sendRiskManagementEscalation(event, correlationId, topic, new RuntimeException(exceptionMessage));
    }

    private boolean isHighRiskMerchant(MerchantRiskScoringEvent event) {
        return event.getRiskScore() >= 80.0 ||
               Arrays.asList("HIGH", "CRITICAL").contains(event.getRiskLevel().toString()) ||
               event.getRiskFactors().contains("MONEY_LAUNDERING") ||
               event.getRiskFactors().contains("SANCTIONS_VIOLATION") ||
               event.getRiskFactors().contains("HIGH_CHARGEBACK_RATE");
    }

    private void processHighRiskMerchantDlq(MerchantRiskScoringEvent event, String correlationId, String topic) {
        MerchantRiskAssessment assessment = MerchantRiskAssessment.builder()
            .merchantId(event.getMerchantId())
            .riskScore(event.getRiskScore())
            .riskLevel("HIGH")
            .riskFactors(String.join(",", event.getRiskFactors()))
            .assessmentType("DLQ_HIGH_RISK")
            .description(String.format("High-risk merchant from DLQ: %s", event.getDescription()))
            .correlationId(correlationId)
            .source(topic)
            .status("REQUIRES_ENHANCED_MONITORING")
            .assessedAt(LocalDateTime.now())
            .build();
        riskAssessmentRepository.save(assessment);

        merchantRiskService.initiateEnhancedMonitoring(event.getMerchantId(), "DLQ_HIGH_RISK");
        escalationService.escalateHighRiskMerchant(event, correlationId);

        // Risk management team notification
        notificationService.sendRiskAlert(
            "HIGH-RISK Merchant from DLQ",
            String.format("High-risk merchant %s identified from DLQ - enhanced monitoring initiated", event.getMerchantId()),
            "HIGH"
        );

        log.error("High-risk merchant DLQ processed: merchantId={}, score={}", event.getMerchantId(), event.getRiskScore());
    }

    private void processCriticalRiskMerchantDlq(MerchantRiskScoringEvent event, String correlationId, String topic) {
        merchantRiskService.recordCriticalRiskMerchant(event.getMerchantId(), event.getRiskFactors(), "DLQ_SOURCE");
        merchantRiskService.initiateImmediateSuspension(event.getMerchantId(), "DLQ_CRITICAL_RISK");
        escalationService.escalateCriticalRiskMerchant(event, correlationId);

        // Immediate compliance team notification
        notificationService.sendComplianceAlert(
            "CRITICAL: Critical Risk Merchant from DLQ",
            String.format("Critical risk merchant %s from DLQ - immediate suspension initiated", event.getMerchantId()),
            "CRITICAL"
        );

        // Executive notification for critical risk
        notificationService.sendExecutiveAlert(
            "CRITICAL: High-Risk Merchant Detected from DLQ",
            String.format("Critical risk merchant %s detected from DLQ with score %.2f. Risk factors: %s",
                event.getMerchantId(), event.getRiskScore(), String.join(", ", event.getRiskFactors())),
            Map.of("merchantId", event.getMerchantId(), "riskScore", event.getRiskScore(), "correlationId", correlationId)
        );

        log.error("Critical risk merchant DLQ processed: merchantId={}", event.getMerchantId());
    }

    private void processMediumRiskMerchantDlq(MerchantRiskScoringEvent event, String correlationId, String topic) {
        merchantRiskService.recordMediumRiskMerchant(event.getMerchantId(), event.getRiskFactors());
        escalationService.escalateMediumRiskMerchant(event, correlationId);

        // Risk team notification
        notificationService.sendRiskAlert(
            "Medium Risk Merchant from DLQ",
            String.format("Medium risk merchant %s from DLQ requires review", event.getMerchantId()),
            "MEDIUM"
        );

        log.warn("Medium risk merchant DLQ processed: merchantId={}", event.getMerchantId());
    }

    private void processLowRiskMerchantDlq(MerchantRiskScoringEvent event, String correlationId, String topic) {
        merchantRiskService.recordLowRiskMerchant(event.getMerchantId(), event.getRiskFactors());
        escalationService.escalateLowRiskMerchant(event, correlationId);

        log.info("Low risk merchant DLQ processed: merchantId={}", event.getMerchantId());
    }

    private void processUnknownRiskMerchantDlq(MerchantRiskScoringEvent event, String correlationId, String topic) {
        merchantRiskService.recordUnknownRiskMerchant(event.getMerchantId(), event.getDescription());
        escalationService.escalateUnknownRiskMerchant(event, correlationId);

        // Risk team notification for unknown risk
        notificationService.sendRiskAlert(
            "Unknown Risk Merchant from DLQ",
            String.format("Unknown risk merchant %s from DLQ requires manual assessment", event.getMerchantId()),
            "HIGH"
        );

        log.warn("Unknown risk merchant DLQ processed: merchantId={}", event.getMerchantId());
    }

    private void processGenericRiskMerchantDlq(MerchantRiskScoringEvent event, String correlationId, String topic) {
        merchantRiskService.recordGenericRiskMerchant(event.getMerchantId(), event.getDescription(), "DLQ_GENERIC");
        escalationService.escalateGenericRiskMerchant(event, correlationId);

        log.warn("Generic risk merchant DLQ processed: merchantId={}, level={}",
            event.getMerchantId(), event.getRiskLevel());
    }

    private void escalateHighRiskMerchant(MerchantRiskScoringEvent event, String correlationId, String topic) {
        try {
            notificationService.sendRiskAlert(
                "CRITICAL: High-Risk Merchant from DLQ Requires Immediate Action",
                String.format("High-risk merchant %s from DLQ topic %s requires immediate risk assessment. " +
                    "Risk Score: %.2f, Level: %s, Factors: %s",
                    event.getMerchantId(), topic, event.getRiskScore(), event.getRiskLevel(),
                    String.join(", ", event.getRiskFactors())),
                Map.of(
                    "merchantId", event.getMerchantId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "riskScore", event.getRiskScore(),
                    "riskLevel", event.getRiskLevel(),
                    "priority", "HIGH_RISK_MERCHANT_ALERT"
                )
            );
        } catch (Exception ex) {
            log.error("Failed to send high-risk merchant escalation: {}", ex.getMessage());
        }
    }

    private void sendRiskManagementEscalation(MerchantRiskScoringEvent event, String correlationId, String topic, Exception ex) {
        try {
            notificationService.sendRiskAlert(
                "SYSTEM CRITICAL: Merchant Risk Scoring DLQ Processing Failure",
                String.format("CRITICAL SYSTEM FAILURE: Unable to process merchant risk scoring from DLQ for merchant %s. " +
                    "This indicates a serious risk management system failure requiring immediate intervention. " +
                    "Topic: %s, Error: %s", event.getMerchantId(), topic, ex.getMessage()),
                Map.of(
                    "merchantId", event.getMerchantId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "errorMessage", ex.getMessage(),
                    "priority", "RISK_SYSTEM_CRITICAL_FAILURE"
                )
            );
        } catch (Exception notificationEx) {
            log.error("CRITICAL: Failed to send risk management escalation for merchant DLQ failure: {}", notificationEx.getMessage());
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