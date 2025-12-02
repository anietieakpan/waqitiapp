package com.waqiti.payment.kafka;

import com.waqiti.common.events.WireTransferFailureEvent;
import com.waqiti.payment.domain.WireTransferFailure;
import com.waqiti.payment.repository.WireTransferFailureRepository;
import com.waqiti.payment.service.WireTransferService;
import com.waqiti.payment.service.SWIFTService;
import com.waqiti.payment.service.CorrespondentBankService;
import com.waqiti.payment.metrics.WireTransferMetricsService;
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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import java.math.BigDecimal;

@Component
@Slf4j
@RequiredArgsConstructor
public class WireTransferFailuresConsumer {

    private final WireTransferFailureRepository failureRepository;
    private final WireTransferService wireTransferService;
    private final SWIFTService swiftService;
    private final CorrespondentBankService correspondentBankService;
    private final WireTransferMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Financial impact tracking
    private final AtomicLong totalFailedWireAmount = new AtomicLong(0);
    private final AtomicLong wireFailureCount = new AtomicLong(0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter financialImpactCounter;
    private Timer processingTimer;
    private Gauge failedWireAmountGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("wire_transfer_failures_processed_total")
            .description("Total number of successfully processed wire transfer failure events")
            .register(meterRegistry);
        errorCounter = Counter.builder("wire_transfer_failures_errors_total")
            .description("Total number of wire transfer failure processing errors")
            .register(meterRegistry);
        financialImpactCounter = Counter.builder("wire_transfer_failures_financial_impact")
            .description("Financial impact of wire transfer failures")
            .register(meterRegistry);
        processingTimer = Timer.builder("wire_transfer_failures_processing_duration")
            .description("Time taken to process wire transfer failure events")
            .register(meterRegistry);
        failedWireAmountGauge = Gauge.builder("wire_transfer_failed_amount_total")
            .description("Total amount of failed wire transfers")
            .register(meterRegistry, totalFailedWireAmount, AtomicLong::get);
    }

    @KafkaListener(
        topics = {"wire-transfer-failures", "swift-message-failures", "correspondent-bank-failures"},
        groupId = "wire-transfer-failures-service-group",
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
    @CircuitBreaker(name = "wire-transfer-failures", fallbackMethod = "handleWireTransferFailureFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleWireTransferFailureEvent(
            @Payload WireTransferFailureEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("wire-fail-%s-p%d-o%d", event.getWireTransferId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getWireTransferId(), event.getFailureCode(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing wire transfer failure: wireTransferId={}, failureCode={}, amount={}, beneficiaryBank={}",
                event.getWireTransferId(), event.getFailureCode(), event.getAmount(), event.getBeneficiaryBankCode());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Financial impact assessment
            assessFinancialImpact(event.getAmount(), event.getFailureCode(), correlationId);

            switch (event.getFailureType()) {
                case SWIFT_MESSAGE_ERROR:
                    handleSWIFTMessageError(event, correlationId);
                    break;

                case CORRESPONDENT_BANK_REJECTION:
                    handleCorrespondentBankRejection(event, correlationId);
                    break;

                case BENEFICIARY_BANK_NOT_FOUND:
                    handleBeneficiaryBankNotFound(event, correlationId);
                    break;

                case INVALID_ACCOUNT_NUMBER:
                    handleInvalidAccountNumber(event, correlationId);
                    break;

                case SANCTIONS_SCREENING_FAILURE:
                    handleSanctionsScreeningFailure(event, correlationId);
                    break;

                case INSUFFICIENT_FUNDS:
                    handleInsufficientFunds(event, correlationId);
                    break;

                case CURRENCY_NOT_SUPPORTED:
                    handleCurrencyNotSupported(event, correlationId);
                    break;

                case REGULATORY_COMPLIANCE_FAILURE:
                    handleRegulatoryComplianceFailure(event, correlationId);
                    break;

                case CUTOFF_TIME_EXCEEDED:
                    handleCutoffTimeExceeded(event, correlationId);
                    break;

                case NETWORK_CONNECTIVITY_ERROR:
                    handleNetworkConnectivityError(event, correlationId);
                    break;

                default:
                    log.warn("Unknown wire transfer failure type: {}", event.getFailureType());
                    handleGenericWireTransferFailure(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logWireTransferEvent("WIRE_TRANSFER_FAILURE_PROCESSED", event.getWireTransferId(),
                Map.of("failureType", event.getFailureType(), "failureCode", event.getFailureCode(),
                    "amount", event.getAmount(), "beneficiaryBankCode", event.getBeneficiaryBankCode(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process wire transfer failure event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("wire-transfer-failure-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleWireTransferFailureFallback(
            WireTransferFailureEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("wire-fail-fallback-%s-p%d-o%d", event.getWireTransferId(), partition, offset);

        log.error("Circuit breaker fallback triggered for wire transfer failure: wireTransferId={}, error={}",
            event.getWireTransferId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("wire-transfer-failures-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for high-value wire transfers
        if (event.getAmount().compareTo(BigDecimal.valueOf(100000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Value Wire Transfer Failure - Circuit Breaker Triggered",
                    String.format("Wire transfer %s (Amount: %s) failed: %s",
                        event.getWireTransferId(), event.getAmount(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltWireTransferFailureEvent(
            @Payload WireTransferFailureEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-wire-fail-%s-%d", event.getWireTransferId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Wire transfer failure permanently failed: wireTransferId={}, topic={}, error={}",
            event.getWireTransferId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logWireTransferEvent("WIRE_TRANSFER_FAILURE_DLT_EVENT", event.getWireTransferId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "failureType", event.getFailureType(), "amount", event.getAmount(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Wire Transfer Failure Dead Letter Event",
                String.format("Wire transfer %s (Amount: %s) sent to DLT: %s",
                    event.getWireTransferId(), event.getAmount(), exceptionMessage),
                Map.of("wireTransferId", event.getWireTransferId(), "topic", topic,
                    "correlationId", correlationId, "amount", event.getAmount())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessFinancialImpact(BigDecimal amount, String failureCode, String correlationId) {
        long amountCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
        totalFailedWireAmount.addAndGet(amountCents);
        wireFailureCount.incrementAndGet();
        financialImpactCounter.increment(amountCents);

        // Alert if cumulative failed wire amount exceeds threshold
        if (totalFailedWireAmount.get() > 1000000000L) { // $10M in cents
            try {
                notificationService.sendExecutiveAlert(
                    "CRITICAL: Wire Transfer Failures Exceed $10M",
                    String.format("Cumulative failed wire transfer amount: $%,.2f. Treasury intervention required.",
                        totalFailedWireAmount.get() / 100.0),
                    "CRITICAL"
                );
                // Reset counter after alert
                totalFailedWireAmount.set(0);
            } catch (Exception ex) {
                log.error("Failed to send financial impact alert: {}", ex.getMessage());
            }
        }

        // High-value wire transfer failure alert
        if (amount.compareTo(BigDecimal.valueOf(100000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Value Wire Transfer Failure",
                    String.format("Wire transfer amount: $%,.2f, Failure: %s", amount, failureCode),
                    "HIGH"
                );
            } catch (Exception ex) {
                log.error("Failed to send high-value wire failure alert: {}", ex.getMessage());
            }
        }
    }

    private void handleSWIFTMessageError(WireTransferFailureEvent event, String correlationId) {
        createFailureRecord(event, "SWIFT_MESSAGE_ERROR", correlationId);

        // Validate SWIFT message format
        kafkaTemplate.send("swift-message-validation", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "swiftMessage", event.getSwiftMessage(),
            "validationType", "MT103_FORMAT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Regenerate SWIFT message
        kafkaTemplate.send("swift-message-regeneration", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "regenerationType", "FORMAT_CORRECTION",
            "originalMessage", event.getSwiftMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("SWIFT Message Error",
            String.format("SWIFT message error for wire transfer %s, regenerating message",
                event.getWireTransferId()),
            "HIGH");

        metricsService.recordWireTransferFailure("SWIFT_MESSAGE_ERROR", event.getAmount());
    }

    private void handleCorrespondentBankRejection(WireTransferFailureEvent event, String correlationId) {
        createFailureRecord(event, "CORRESPONDENT_BANK_REJECTION", correlationId);

        // Check correspondent bank relationship
        kafkaTemplate.send("correspondent-bank-relationship-checks", Map.of(
            "correspondentBankCode", event.getCorrespondentBankCode(),
            "checkType", "RELATIONSHIP_STATUS",
            "wireTransferId", event.getWireTransferId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Find alternative correspondent bank
        kafkaTemplate.send("alternative-correspondent-bank-search", Map.of(
            "targetCountry", event.getBeneficiaryCountry(),
            "currency", event.getCurrency(),
            "amount", event.getAmount(),
            "wireTransferId", event.getWireTransferId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Correspondent Bank Rejection",
            String.format("Wire transfer %s rejected by correspondent bank %s",
                event.getWireTransferId(), event.getCorrespondentBankCode()),
            "HIGH");

        metricsService.recordWireTransferFailure("CORRESPONDENT_BANK_REJECTION", event.getAmount());
    }

    private void handleBeneficiaryBankNotFound(WireTransferFailureEvent event, String correlationId) {
        createFailureRecord(event, "BENEFICIARY_BANK_NOT_FOUND", correlationId);

        // Validate beneficiary bank code
        kafkaTemplate.send("bank-code-validation", Map.of(
            "bankCode", event.getBeneficiaryBankCode(),
            "country", event.getBeneficiaryCountry(),
            "validationType", "SWIFT_BIC_LOOKUP",
            "wireTransferId", event.getWireTransferId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Search for correct bank code
        kafkaTemplate.send("bank-code-search", Map.of(
            "bankName", event.getBeneficiaryBankName(),
            "country", event.getBeneficiaryCountry(),
            "city", event.getBeneficiaryBankCity(),
            "searchType", "FUZZY_MATCH",
            "wireTransferId", event.getWireTransferId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Beneficiary Bank Not Found",
            String.format("Beneficiary bank not found for wire transfer %s: %s",
                event.getWireTransferId(), event.getBeneficiaryBankCode()),
            "HIGH");

        metricsService.recordWireTransferFailure("BENEFICIARY_BANK_NOT_FOUND", event.getAmount());
    }

    private void handleInvalidAccountNumber(WireTransferFailureEvent event, String correlationId) {
        createFailureRecord(event, "INVALID_ACCOUNT_NUMBER", correlationId);

        // Validate account number format
        kafkaTemplate.send("account-number-validation", Map.of(
            "accountNumber", event.getBeneficiaryAccountNumber(),
            "bankCode", event.getBeneficiaryBankCode(),
            "country", event.getBeneficiaryCountry(),
            "validationType", "FORMAT_CHECK",
            "wireTransferId", event.getWireTransferId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Request account verification
        kafkaTemplate.send("account-verification-requests", Map.of(
            "accountNumber", event.getBeneficiaryAccountNumber(),
            "bankCode", event.getBeneficiaryBankCode(),
            "beneficiaryName", event.getBeneficiaryName(),
            "verificationType", "NAME_MATCH",
            "wireTransferId", event.getWireTransferId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Invalid Account Number",
            String.format("Invalid account number for wire transfer %s: %s",
                event.getWireTransferId(), event.getBeneficiaryAccountNumber()),
            "HIGH");

        metricsService.recordWireTransferFailure("INVALID_ACCOUNT_NUMBER", event.getAmount());
    }

    private void handleSanctionsScreeningFailure(WireTransferFailureEvent event, String correlationId) {
        createFailureRecord(event, "SANCTIONS_SCREENING_FAILURE", correlationId);

        // Enhanced sanctions screening
        kafkaTemplate.send("enhanced-sanctions-screening", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "beneficiaryName", event.getBeneficiaryName(),
            "beneficiaryAddress", event.getBeneficiaryAddress(),
            "screeningType", "ENHANCED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Create compliance case
        kafkaTemplate.send("compliance-case-creation", Map.of(
            "caseType", "SANCTIONS_SCREENING_FAILURE",
            "wireTransferId", event.getWireTransferId(),
            "amount", event.getAmount(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendComplianceAlert("Sanctions Screening Failure",
            String.format("Sanctions screening failed for wire transfer %s",
                event.getWireTransferId()),
            correlationId);

        metricsService.recordWireTransferFailure("SANCTIONS_SCREENING_FAILURE", event.getAmount());
    }

    private void handleInsufficientFunds(WireTransferFailureEvent event, String correlationId) {
        createFailureRecord(event, "INSUFFICIENT_FUNDS", correlationId);

        // Check account balance
        kafkaTemplate.send("account-balance-checks", Map.of(
            "accountId", event.getOriginatorAccountId(),
            "requiredAmount", event.getAmount().add(event.getTransferFee()),
            "checkType", "AVAILABLE_BALANCE",
            "wireTransferId", event.getWireTransferId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule retry when funds available
        kafkaTemplate.send("wire-transfer-retry-schedules", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "retryType", "INSUFFICIENT_FUNDS",
            "scheduleDelay", 7200000, // 2 hours
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCustomerAlert(event.getOriginatorId(),
            "Wire Transfer Failed - Insufficient Funds",
            String.format("Your wire transfer of $%,.2f failed due to insufficient funds", event.getAmount()),
            correlationId);

        metricsService.recordWireTransferFailure("INSUFFICIENT_FUNDS", event.getAmount());
    }

    private void handleCurrencyNotSupported(WireTransferFailureEvent event, String correlationId) {
        createFailureRecord(event, "CURRENCY_NOT_SUPPORTED", correlationId);

        // Check supported currencies
        kafkaTemplate.send("supported-currency-checks", Map.of(
            "bankCode", event.getBeneficiaryBankCode(),
            "currency", event.getCurrency(),
            "checkType", "CURRENCY_SUPPORT",
            "wireTransferId", event.getWireTransferId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Suggest currency conversion
        kafkaTemplate.send("currency-conversion-suggestions", Map.of(
            "originalCurrency", event.getCurrency(),
            "targetBankCode", event.getBeneficiaryBankCode(),
            "amount", event.getAmount(),
            "wireTransferId", event.getWireTransferId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCustomerAlert(event.getOriginatorId(),
            "Currency Not Supported",
            String.format("Currency %s not supported by beneficiary bank", event.getCurrency()),
            correlationId);

        metricsService.recordWireTransferFailure("CURRENCY_NOT_SUPPORTED", event.getAmount());
    }

    private void handleRegulatoryComplianceFailure(WireTransferFailureEvent event, String correlationId) {
        createFailureRecord(event, "REGULATORY_COMPLIANCE_FAILURE", correlationId);

        // Enhanced compliance screening
        kafkaTemplate.send("enhanced-compliance-screening", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "complianceFailureReason", event.getComplianceFailureReason(),
            "screeningType", "REGULATORY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Create regulatory compliance case
        kafkaTemplate.send("compliance-case-creation", Map.of(
            "caseType", "REGULATORY_COMPLIANCE_FAILURE",
            "wireTransferId", event.getWireTransferId(),
            "failureReason", event.getComplianceFailureReason(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendComplianceAlert("Regulatory Compliance Failure",
            String.format("Regulatory compliance failure for wire transfer %s: %s",
                event.getWireTransferId(), event.getComplianceFailureReason()),
            correlationId);

        metricsService.recordWireTransferFailure("REGULATORY_COMPLIANCE_FAILURE", event.getAmount());
    }

    private void handleCutoffTimeExceeded(WireTransferFailureEvent event, String correlationId) {
        createFailureRecord(event, "CUTOFF_TIME_EXCEEDED", correlationId);

        // Schedule for next business day
        kafkaTemplate.send("next-business-day-scheduling", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "originalCutoffTime", event.getCutoffTime(),
            "nextAvailableTime", event.getNextCutoffTime(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check for same-day processing availability
        kafkaTemplate.send("same-day-processing-checks", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "amount", event.getAmount(),
            "urgencyLevel", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCustomerAlert(event.getOriginatorId(),
            "Wire Transfer Rescheduled",
            "Your wire transfer missed the cutoff time and has been scheduled for the next business day",
            correlationId);

        metricsService.recordWireTransferFailure("CUTOFF_TIME_EXCEEDED", event.getAmount());
    }

    private void handleNetworkConnectivityError(WireTransferFailureEvent event, String correlationId) {
        createFailureRecord(event, "NETWORK_CONNECTIVITY_ERROR", correlationId);

        // Test network connectivity
        kafkaTemplate.send("network-connectivity-tests", Map.of(
            "target", "SWIFT_NETWORK",
            "testType", "CONNECTIVITY_DIAGNOSTIC",
            "priority", "HIGH",
            "wireTransferId", event.getWireTransferId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule retry
        kafkaTemplate.send("wire-transfer-retry-schedules", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "retryType", "NETWORK_CONNECTIVITY",
            "scheduleDelay", 900000, // 15 minutes
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordWireTransferFailure("NETWORK_CONNECTIVITY_ERROR", event.getAmount());
    }

    private void handleGenericWireTransferFailure(WireTransferFailureEvent event, String correlationId) {
        createFailureRecord(event, "UNKNOWN_FAILURE", correlationId);

        // Log for investigation
        auditService.logWireTransferEvent("UNKNOWN_WIRE_TRANSFER_FAILURE", event.getWireTransferId(),
            Map.of("failureType", event.getFailureType(), "failureCode", event.getFailureCode(),
                "failureReason", event.getFailureReason(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Wire Transfer Failure",
            String.format("Unknown wire transfer failure for %s: %s",
                event.getWireTransferId(), event.getFailureReason()),
            "HIGH");

        metricsService.recordWireTransferFailure("UNKNOWN", event.getAmount());
    }

    private void createFailureRecord(WireTransferFailureEvent event, String failureType, String correlationId) {
        try {
            WireTransferFailure failure = WireTransferFailure.builder()
                .wireTransferId(event.getWireTransferId())
                .failureType(failureType)
                .failureCode(event.getFailureCode())
                .failureReason(event.getFailureReason())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .beneficiaryBankCode(event.getBeneficiaryBankCode())
                .failureTime(LocalDateTime.now())
                .status("OPEN")
                .correlationId(correlationId)
                .build();

            failureRepository.save(failure);
        } catch (Exception e) {
            log.error("Failed to create wire transfer failure record: {}", e.getMessage());
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