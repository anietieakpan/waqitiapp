package com.waqiti.compliance.kafka;

import com.waqiti.common.events.ComplianceSarFilingEvent;
import com.waqiti.compliance.domain.SarFilingRecord;
import com.waqiti.compliance.repository.SarFilingRecordRepository;
import com.waqiti.compliance.service.SarFilingService;
import com.waqiti.compliance.service.ComplianceEscalationService;
import com.waqiti.compliance.metrics.ComplianceMetricsService;
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
public class ComplianceSarFilingFailedConsumer {

    private final SarFilingRecordRepository sarFilingRecordRepository;
    private final SarFilingService sarFilingService;
    private final ComplianceEscalationService escalationService;
    private final ComplianceMetricsService metricsService;
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
    private Counter sarFilingFailureCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("sar_filing_failed_processed_total")
            .description("Total number of successfully processed SAR filing failed events")
            .register(meterRegistry);
        errorCounter = Counter.builder("sar_filing_failed_errors_total")
            .description("Total number of SAR filing failed processing errors")
            .register(meterRegistry);
        sarFilingFailureCounter = Counter.builder("sar_filing_failures_critical_total")
            .description("Total number of critical SAR filing failures requiring compliance escalation")
            .register(meterRegistry);
        processingTimer = Timer.builder("sar_filing_failed_processing_duration")
            .description("Time taken to process SAR filing failed events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"compliance.sar.filing.failed", "sar-filing-failures", "suspicious-activity-filing-failed"},
        groupId = "sar-filing-failed-service-group",
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
    @CircuitBreaker(name = "sar-filing-failed", fallbackMethod = "handleSarFilingFailedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleSarFilingFailedEvent(
            @Payload ComplianceSarFilingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("sar-filing-failed-%s-p%d-o%d", event.getSarId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getSarId(), event.getFilingStatus(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("SAR filing failed event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL SAR filing failure: sarId={}, reason={}, amount={}",
                event.getSarId(), event.getFailureReason(), event.getTransactionAmount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // SAR filing failures are always critical for regulatory compliance
            sarFilingFailureCounter.increment();
            escalateCriticalSarFilingFailure(event, correlationId);

            switch (event.getFilingType()) {
                case SUSPICIOUS_ACTIVITY_REPORT:
                    processSuspiciousActivityReportFailure(event, correlationId);
                    break;

                case CURRENCY_TRANSACTION_REPORT:
                    processCurrencyTransactionReportFailure(event, correlationId);
                    break;

                case BANK_SECRECY_ACT_REPORT:
                    processBankSecrecyActReportFailure(event, correlationId);
                    break;

                case ANTI_MONEY_LAUNDERING_REPORT:
                    processAntiMoneyLaunderingReportFailure(event, correlationId);
                    break;

                case OFAC_SANCTIONS_REPORT:
                    processOfacSanctionsReportFailure(event, correlationId);
                    break;

                case FINRA_COMPLIANCE_REPORT:
                    processFinraComplianceReportFailure(event, correlationId);
                    break;

                default:
                    processGenericSarFilingFailure(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("SAR_FILING_FAILED_PROCESSED", event.getSarId(),
                Map.of("filingType", event.getFilingType(), "failureReason", event.getFailureReason(),
                    "transactionAmount", event.getTransactionAmount(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process SAR filing failed event: {}", e.getMessage(), e);

            // Send to compliance escalation
            sendComplianceEscalation(event, correlationId, e);

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleSarFilingFailedEventFallback(
            ComplianceSarFilingEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("sar-filing-failed-fallback-%s-p%d-o%d", event.getSarId(), partition, offset);

        log.error("Circuit breaker fallback triggered for SAR filing failed: sarId={}, error={}",
            event.getSarId(), ex.getMessage());

        sendComplianceEscalation(event, correlationId, ex);
        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltSarFilingFailedEvent(
            @Payload ComplianceSarFilingEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-sar-filing-%s-%d", event.getSarId(), System.currentTimeMillis());

        log.error("CRITICAL: SAR filing failed event permanently failed - sarId={}, topic={}, error={}",
            event.getSarId(), topic, exceptionMessage);

        auditService.logComplianceEvent("SAR_FILING_FAILED_DLT_EVENT", event.getSarId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "filingType", event.getFilingType(), "correlationId", correlationId,
                "requiresImmediateRegulatoryAction", true, "timestamp", Instant.now()));

        sendComplianceEscalation(event, correlationId, new RuntimeException(exceptionMessage));
    }

    private void processSuspiciousActivityReportFailure(ComplianceSarFilingEvent event, String correlationId) {
        SarFilingRecord record = SarFilingRecord.builder()
            .sarId(event.getSarId())
            .filingType("SUSPICIOUS_ACTIVITY_REPORT_FAILED")
            .status("FILING_FAILED")
            .failureReason(event.getFailureReason())
            .transactionAmount(event.getTransactionAmount())
            .accountId(event.getAccountId())
            .customerId(event.getCustomerId())
            .correlationId(correlationId)
            .filingDeadline(event.getFilingDeadline())
            .attemptedAt(LocalDateTime.now())
            .requiresImmediateRefiling(true)
            .build();
        sarFilingRecordRepository.save(record);

        sarFilingService.initiateManualSarFiling(event.getSarId(), "AUTOMATED_FILING_FAILED");
        escalationService.escalateSarFilingFailure(event, correlationId);

        // FinCEN notification requirement
        notificationService.sendRegulatoryAlert(
            "CRITICAL: SAR Filing Failed - Regulatory Deadline Risk",
            String.format("SAR filing %s failed - manual intervention required to meet FinCEN deadline", event.getSarId()),
            Map.of("sarId", event.getSarId(), "amount", event.getTransactionAmount(), "correlationId", correlationId)
        );

        // Executive notification for large amounts
        if (event.getTransactionAmount() > 100000.0) {
            notificationService.sendExecutiveAlert(
                "CRITICAL: High-Value SAR Filing Failed",
                String.format("High-value SAR filing failed: $%.2f - immediate regulatory attention required", event.getTransactionAmount()),
                Map.of("sarId", event.getSarId(), "amount", event.getTransactionAmount(), "correlationId", correlationId)
            );
        }

        log.error("Suspicious Activity Report filing failed: sarId={}, amount=${}", event.getSarId(), event.getTransactionAmount());
    }

    private void processCurrencyTransactionReportFailure(ComplianceSarFilingEvent event, String correlationId) {
        sarFilingService.recordCtrFilingFailure(event.getSarId(), event.getFailureReason());
        escalationService.escalateCtrFilingFailure(event, correlationId);

        // Treasury Department notification
        notificationService.sendTreasuryAlert(
            "CTR Filing Failed",
            String.format("Currency Transaction Report filing %s failed", event.getSarId()),
            Map.of("sarId", event.getSarId(), "correlationId", correlationId)
        );

        log.error("Currency Transaction Report filing failed: sarId={}", event.getSarId());
    }

    private void processBankSecrecyActReportFailure(ComplianceSarFilingEvent event, String correlationId) {
        sarFilingService.recordBsaFilingFailure(event.getSarId(), event.getFailureReason());
        escalationService.escalateBsaFilingFailure(event, correlationId);

        log.error("Bank Secrecy Act report filing failed: sarId={}", event.getSarId());
    }

    private void processAntiMoneyLaunderingReportFailure(ComplianceSarFilingEvent event, String correlationId) {
        sarFilingService.recordAmlFilingFailure(event.getSarId(), event.getFailureReason());
        escalationService.escalateAmlFilingFailure(event, correlationId);

        // AML team notification
        notificationService.sendAmlAlert(
            "AML Report Filing Failed",
            String.format("Anti-Money Laundering report filing %s failed", event.getSarId()),
            "HIGH"
        );

        log.error("Anti-Money Laundering report filing failed: sarId={}", event.getSarId());
    }

    private void processOfacSanctionsReportFailure(ComplianceSarFilingEvent event, String correlationId) {
        sarFilingService.recordOfacFilingFailure(event.getSarId(), event.getFailureReason());
        escalationService.escalateOfacFilingFailure(event, correlationId);

        // OFAC sanctions require immediate attention
        notificationService.sendSanctionsAlert(
            "CRITICAL: OFAC Sanctions Report Filing Failed",
            String.format("OFAC sanctions report filing %s failed - immediate compliance action required", event.getSarId()),
            "CRITICAL"
        );

        log.error("OFAC sanctions report filing failed: sarId={}", event.getSarId());
    }

    private void processFinraComplianceReportFailure(ComplianceSarFilingEvent event, String correlationId) {
        sarFilingService.recordFinraFilingFailure(event.getSarId(), event.getFailureReason());
        escalationService.escalateFinraFilingFailure(event, correlationId);

        log.error("FINRA compliance report filing failed: sarId={}", event.getSarId());
    }

    private void processGenericSarFilingFailure(ComplianceSarFilingEvent event, String correlationId) {
        sarFilingService.recordGenericFilingFailure(event.getSarId(), event.getFailureReason(), "GENERIC");
        escalationService.escalateGenericFilingFailure(event, correlationId);

        log.warn("Generic SAR filing failed: sarId={}, type={}", event.getSarId(), event.getFilingType());
    }

    private void escalateCriticalSarFilingFailure(ComplianceSarFilingEvent event, String correlationId) {
        try {
            notificationService.sendComplianceAlert(
                "CRITICAL: SAR Filing Failure Requires Immediate Action",
                String.format("SAR filing failure for %s requires immediate compliance intervention. " +
                    "Type: %s, Amount: $%.2f, Failure Reason: %s, Deadline: %s",
                    event.getSarId(), event.getFilingType(), event.getTransactionAmount(),
                    event.getFailureReason(), event.getFilingDeadline()),
                Map.of(
                    "sarId", event.getSarId(),
                    "correlationId", correlationId,
                    "filingType", event.getFilingType(),
                    "transactionAmount", event.getTransactionAmount(),
                    "failureReason", event.getFailureReason(),
                    "filingDeadline", event.getFilingDeadline(),
                    "priority", "CRITICAL_SAR_FILING_FAILURE"
                )
            );
        } catch (Exception ex) {
            log.error("Failed to send critical SAR filing failure escalation: {}", ex.getMessage());
        }
    }

    private void sendComplianceEscalation(ComplianceSarFilingEvent event, String correlationId, Exception ex) {
        try {
            notificationService.sendComplianceAlert(
                "SYSTEM CRITICAL: SAR Filing Processing Failure",
                String.format("CRITICAL SYSTEM FAILURE: Unable to process SAR filing failure for %s. " +
                    "This indicates a serious compliance system failure requiring immediate intervention. " +
                    "Error: %s", event.getSarId(), ex.getMessage()),
                Map.of(
                    "sarId", event.getSarId(),
                    "correlationId", correlationId,
                    "errorMessage", ex.getMessage(),
                    "priority", "COMPLIANCE_SYSTEM_CRITICAL_FAILURE"
                )
            );
        } catch (Exception notificationEx) {
            log.error("CRITICAL: Failed to send compliance escalation for SAR filing failure: {}", notificationEx.getMessage());
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