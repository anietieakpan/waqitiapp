package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.payment.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for high-value transaction events
 * Handles transactions above regulatory thresholds with enhanced monitoring,
 * compliance checks, and risk assessment procedures
 *
 * Critical for: AML compliance, regulatory reporting, risk management
 * SLA: Must process high-value transactions within 3 seconds for fraud prevention
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HighValueTransactionEventsConsumer {

    private final HighValueTransactionService highValueTransactionService;
    private final RiskAssessmentService riskAssessmentService;
    private final ComplianceMonitoringService complianceMonitoringService;
    private final FraudDetectionService fraudDetectionService;
    private final NotificationService notificationService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // High-value transaction thresholds
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal CRITICAL_VALUE_THRESHOLD = new BigDecimal("50000");
    private static final BigDecimal EXECUTIVE_ALERT_THRESHOLD = new BigDecimal("100000");

    // Metrics
    private final Counter highValueTransactionCounter = Counter.builder("high_value_transactions_processed_total")
            .description("Total number of high-value transactions processed")
            .register(metricsService.getMeterRegistry());

    private final Counter criticalValueTransactionCounter = Counter.builder("critical_value_transactions_total")
            .description("Total number of critical value transactions")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("high_value_transaction_processing_duration")
            .description("Time taken to process high-value transactions")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"high-value-transaction-events"},
        groupId = "payment-service-high-value-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "high-value-transaction-processor", fallbackMethod = "handleHighValueTransactionFailure")
    @Retry(name = "high-value-transaction-processor")
    public void processHighValueTransactionEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.info("PAYMENT: Processing high-value transaction: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("High-value transaction {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate transaction data
            HighValueTransactionData transactionData = extractHighValueTransactionData(event.getPayload());
            validateHighValueTransactionData(transactionData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process high-value transaction
            processHighValueTransaction(transactionData, event);

            // Record successful processing metrics
            highValueTransactionCounter.increment();

            if (transactionData.getAmount().compareTo(CRITICAL_VALUE_THRESHOLD) >= 0) {
                criticalValueTransactionCounter.increment();
            }

            // Audit the transaction processing
            auditHighValueTransactionProcessing(transactionData, event, "SUCCESS");

            log.info("PAYMENT: Successfully processed high-value transaction: {} for account: {} - amount: {} status: {}",
                    eventId, transactionData.getAccountId(), transactionData.getAmount(), transactionData.getStatus());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("PAYMENT: Invalid high-value transaction data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("PAYMENT: Failed to process high-value transaction: {}", eventId, e);
            auditHighValueTransactionProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("High-value transaction processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        Instant processedTime = processedEventIds.get(eventId);
        if (processedTime != null) {
            if (ChronoUnit.HOURS.between(processedTime, Instant.now()) < IDEMPOTENCY_TTL_HOURS) {
                return true;
            } else {
                processedEventIds.remove(eventId);
            }
        }
        return false;
    }

    private void markEventAsProcessed(String eventId) {
        processedEventIds.put(eventId, Instant.now());
    }

    private void cleanupIdempotencyCache() {
        Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
        processedEventIds.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private HighValueTransactionData extractHighValueTransactionData(Map<String, Object> payload) {
        return HighValueTransactionData.builder()
                .transactionId(extractString(payload, "transactionId"))
                .accountId(extractString(payload, "accountId"))
                .customerId(extractString(payload, "customerId"))
                .amount(extractBigDecimal(payload, "amount"))
                .currency(extractString(payload, "currency"))
                .transactionType(extractString(payload, "transactionType"))
                .status(extractString(payload, "status"))
                .fromAccount(extractString(payload, "fromAccount"))
                .toAccount(extractString(payload, "toAccount"))
                .beneficiaryName(extractString(payload, "beneficiaryName"))
                .beneficiaryBank(extractString(payload, "beneficiaryBank"))
                .purpose(extractString(payload, "purpose"))
                .transactionDate(extractInstant(payload, "transactionDate"))
                .valueDate(extractInstant(payload, "valueDate"))
                .exchangeRate(extractBigDecimal(payload, "exchangeRate"))
                .fees(extractBigDecimal(payload, "fees"))
                .priority(extractString(payload, "priority"))
                .riskScore(extractDouble(payload, "riskScore"))
                .complianceFlags(extractStringList(payload, "complianceFlags"))
                .crossBorder(extractBoolean(payload, "crossBorder"))
                .jurisdiction(extractString(payload, "jurisdiction"))
                .build();
    }

    private void validateHighValueTransactionData(HighValueTransactionData transactionData) {
        if (transactionData.getTransactionId() == null || transactionData.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is required");
        }

        if (transactionData.getAmount() == null || transactionData.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid transaction amount is required");
        }

        if (transactionData.getAmount().compareTo(HIGH_VALUE_THRESHOLD) < 0) {
            throw new IllegalArgumentException("Transaction amount below high-value threshold");
        }

        if (transactionData.getAccountId() == null || transactionData.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }

        if (transactionData.getTransactionDate() == null) {
            throw new IllegalArgumentException("Transaction date is required");
        }

        List<String> validStatuses = List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED", "SUSPENDED");
        if (!validStatuses.contains(transactionData.getStatus())) {
            throw new IllegalArgumentException("Invalid transaction status: " + transactionData.getStatus());
        }
    }

    private void processHighValueTransaction(HighValueTransactionData transactionData, GenericKafkaEvent event) {
        log.info("PAYMENT: Processing high-value transaction - ID: {}, Amount: {}, Type: {}, Priority: {}",
                transactionData.getTransactionId(), transactionData.getAmount(),
                transactionData.getTransactionType(), transactionData.getPriority());

        try {
            // Enhanced risk assessment for high-value transactions
            performEnhancedRiskAssessment(transactionData);

            // Compliance monitoring and checks
            performComplianceMonitoring(transactionData);

            // Fraud detection analysis
            performFraudDetectionAnalysis(transactionData);

            // Process based on transaction status
            processTransactionByStatus(transactionData);

            // Generate regulatory alerts if needed
            generateRegulatoryAlerts(transactionData);

            // Update monitoring dashboards
            updateMonitoringDashboards(transactionData);

            // Executive notifications for very high amounts
            processExecutiveNotifications(transactionData);

            log.info("PAYMENT: High-value transaction processed - ID: {}, Risk: {}, Compliance: {}, Action: {}",
                    transactionData.getTransactionId(),
                    getRiskLevel(transactionData.getRiskScore()),
                    getComplianceStatus(transactionData.getComplianceFlags()),
                    getTransactionAction(transactionData.getStatus()));

        } catch (Exception e) {
            log.error("PAYMENT: Failed to process high-value transaction for: {}", transactionData.getTransactionId(), e);

            // Emergency procedures for high-value transaction failures
            executeEmergencyHighValueProcedures(transactionData, e);

            throw new RuntimeException("High-value transaction processing failed", e);
        }
    }

    private void performEnhancedRiskAssessment(HighValueTransactionData transactionData) {
        // Enhanced risk scoring for high-value transactions
        double riskScore = riskAssessmentService.calculateHighValueTransactionRisk(
                transactionData.getTransactionId(),
                transactionData.getAmount(),
                transactionData.getCustomerId(),
                transactionData.getCrossBorder(),
                transactionData.getJurisdiction()
        );

        // Update transaction with calculated risk score
        highValueTransactionService.updateTransactionRiskScore(
                transactionData.getTransactionId(),
                riskScore
        );

        // High-risk transaction handling
        if (riskScore >= 0.8) {
            handleHighRiskTransaction(transactionData, riskScore);
        }

        // Medium-risk transaction monitoring
        if (riskScore >= 0.6 && riskScore < 0.8) {
            handleMediumRiskTransaction(transactionData, riskScore);
        }
    }

    private void handleHighRiskTransaction(HighValueTransactionData transactionData, double riskScore) {
        log.warn("HIGH RISK TRANSACTION DETECTED: {} with risk score {}",
                transactionData.getTransactionId(), riskScore);

        // Suspend transaction for manual review
        highValueTransactionService.suspendTransactionForReview(
                transactionData.getTransactionId(),
                "HIGH_RISK_SCORE"
        );

        // Immediate compliance team notification
        notificationService.sendCriticalAlert(
                "High-Risk High-Value Transaction",
                String.format("Transaction %s with amount %s has high risk score %.2f",
                        transactionData.getTransactionId(),
                        transactionData.getAmount(),
                        riskScore),
                "COMPLIANCE_TEAM"
        );

        // Escalate to senior management for very high amounts
        if (transactionData.getAmount().compareTo(EXECUTIVE_ALERT_THRESHOLD) >= 0) {
            notificationService.sendExecutiveAlert(
                    "Critical High-Value Transaction Alert",
                    transactionData,
                    riskScore
            );
        }
    }

    private void handleMediumRiskTransaction(HighValueTransactionData transactionData, double riskScore) {
        // Enhanced monitoring for medium-risk transactions
        complianceMonitoringService.enableEnhancedMonitoring(
                transactionData.getCustomerId(),
                "MEDIUM_RISK_HIGH_VALUE_TRANSACTION"
        );

        // Notify compliance team
        notificationService.sendComplianceAlert(
                "Medium-Risk High-Value Transaction",
                String.format("Transaction %s requires attention with risk score %.2f",
                        transactionData.getTransactionId(), riskScore)
        );
    }

    private void performComplianceMonitoring(HighValueTransactionData transactionData) {
        // AML compliance checks
        complianceMonitoringService.performAmlChecks(
                transactionData.getTransactionId(),
                transactionData.getCustomerId(),
                transactionData.getAmount(),
                transactionData.getBeneficiaryName()
        );

        // CTR threshold monitoring
        if (transactionData.getAmount().compareTo(new BigDecimal("10000")) >= 0) {
            complianceMonitoringService.flagForCtrReporting(
                    transactionData.getTransactionId(),
                    transactionData.getAmount()
            );
        }

        // Cross-border compliance checks
        if (transactionData.getCrossBorder()) {
            complianceMonitoringService.performCrossBorderCompliance(
                    transactionData.getTransactionId(),
                    transactionData.getJurisdiction(),
                    transactionData.getAmount()
            );
        }
    }

    private void performFraudDetectionAnalysis(HighValueTransactionData transactionData) {
        // Real-time fraud scoring
        double fraudScore = fraudDetectionService.calculateFraudScore(
                transactionData.getTransactionId(),
                transactionData.getCustomerId(),
                transactionData.getAmount(),
                transactionData.getTransactionType()
        );

        // Fraud alert handling
        if (fraudScore >= 0.7) {
            handlePotentialFraud(transactionData, fraudScore);
        }

        // Velocity checks for high-value transactions
        fraudDetectionService.performVelocityChecks(
                transactionData.getCustomerId(),
                transactionData.getAmount(),
                transactionData.getTransactionDate()
        );
    }

    private void handlePotentialFraud(HighValueTransactionData transactionData, double fraudScore) {
        log.error("POTENTIAL FRAUD DETECTED: High-value transaction {} with fraud score {}",
                transactionData.getTransactionId(), fraudScore);

        // Immediately suspend transaction
        highValueTransactionService.suspendTransactionForReview(
                transactionData.getTransactionId(),
                "POTENTIAL_FRAUD_DETECTED"
        );

        // Critical fraud alert
        notificationService.sendCriticalAlert(
                "FRAUD ALERT: High-Value Transaction",
                String.format("Potential fraud detected in transaction %s with fraud score %.2f",
                        transactionData.getTransactionId(), fraudScore),
                "FRAUD_TEAM"
        );

        // Freeze customer account if fraud score is very high
        if (fraudScore >= 0.9) {
            fraudDetectionService.freezeCustomerAccount(
                    transactionData.getCustomerId(),
                    "HIGH_FRAUD_SCORE_HIGH_VALUE_TRANSACTION"
            );
        }
    }

    private void processTransactionByStatus(HighValueTransactionData transactionData) {
        switch (transactionData.getStatus()) {
            case "PENDING":
                handlePendingTransaction(transactionData);
                break;
            case "PROCESSING":
                handleProcessingTransaction(transactionData);
                break;
            case "COMPLETED":
                handleCompletedTransaction(transactionData);
                break;
            case "FAILED":
                handleFailedTransaction(transactionData);
                break;
            case "SUSPENDED":
                handleSuspendedTransaction(transactionData);
                break;
            default:
                log.warn("Unknown transaction status: {}", transactionData.getStatus());
        }
    }

    private void handlePendingTransaction(HighValueTransactionData transactionData) {
        // Validate transaction limits
        highValueTransactionService.validateTransactionLimits(
                transactionData.getCustomerId(),
                transactionData.getAmount()
        );

        // Schedule for processing based on priority
        if ("HIGH".equals(transactionData.getPriority())) {
            highValueTransactionService.prioritizeTransaction(
                    transactionData.getTransactionId()
            );
        }
    }

    private void handleProcessingTransaction(HighValueTransactionData transactionData) {
        // Monitor processing progress
        highValueTransactionService.monitorTransactionProgress(
                transactionData.getTransactionId()
        );

        // Real-time status updates
        notificationService.sendTransactionStatusUpdate(
                transactionData.getCustomerId(),
                transactionData.getTransactionId(),
                "PROCESSING"
        );
    }

    private void handleCompletedTransaction(HighValueTransactionData transactionData) {
        // Post-transaction monitoring
        complianceMonitoringService.performPostTransactionMonitoring(
                transactionData.getTransactionId(),
                transactionData.getAmount()
        );

        // Generate completion reports
        regulatoryReportingService.generateTransactionCompletionReport(
                transactionData.getTransactionId()
        );

        // Customer notification
        notificationService.sendTransactionCompletionNotification(
                transactionData.getCustomerId(),
                transactionData.getTransactionId(),
                transactionData.getAmount()
        );
    }

    private void handleFailedTransaction(HighValueTransactionData transactionData) {
        // Analyze failure reasons
        highValueTransactionService.analyzeTransactionFailure(
                transactionData.getTransactionId()
        );

        // Customer notification
        notificationService.sendTransactionFailureNotification(
                transactionData.getCustomerId(),
                transactionData.getTransactionId(),
                "Transaction failed - please contact support"
        );

        // Operations team alert for high-value failures
        notificationService.sendOperationsAlert(
                "High-Value Transaction Failure",
                String.format("High-value transaction %s failed for amount %s",
                        transactionData.getTransactionId(),
                        transactionData.getAmount())
        );
    }

    private void handleSuspendedTransaction(HighValueTransactionData transactionData) {
        // Queue for manual review
        complianceMonitoringService.queueForManualReview(
                transactionData.getTransactionId(),
                "HIGH_VALUE_TRANSACTION_SUSPENDED"
        );

        // Compliance team notification
        notificationService.sendComplianceAlert(
                "High-Value Transaction Suspended",
                String.format("Transaction %s suspended for manual review",
                        transactionData.getTransactionId())
        );
    }

    private void generateRegulatoryAlerts(HighValueTransactionData transactionData) {
        // CTR alerts for amounts >= $10,000
        if (transactionData.getAmount().compareTo(new BigDecimal("10000")) >= 0) {
            regulatoryReportingService.generateCtrAlert(
                    transactionData.getTransactionId(),
                    transactionData.getAmount(),
                    transactionData.getCustomerId()
            );
        }

        // FBAR alerts for cross-border transactions
        if (transactionData.getCrossBorder() &&
            transactionData.getAmount().compareTo(new BigDecimal("10000")) >= 0) {
            regulatoryReportingService.generateFbarAlert(
                    transactionData.getTransactionId(),
                    transactionData.getJurisdiction(),
                    transactionData.getAmount()
            );
        }

        // SAR referrals for suspicious high-value transactions
        if (transactionData.getComplianceFlags().contains("SUSPICIOUS")) {
            regulatoryReportingService.generateSarReferral(
                    transactionData.getTransactionId(),
                    "HIGH_VALUE_SUSPICIOUS_TRANSACTION"
            );
        }
    }

    private void updateMonitoringDashboards(HighValueTransactionData transactionData) {
        // Update high-value transaction dashboard
        highValueTransactionService.updateDashboard(
                transactionData.getTransactionId(),
                transactionData.getAmount(),
                transactionData.getStatus()
        );

        // Update compliance monitoring dashboard
        complianceMonitoringService.updateComplianceDashboard(
                transactionData.getCustomerId(),
                transactionData.getAmount(),
                transactionData.getComplianceFlags()
        );

        // Update risk monitoring dashboard
        riskAssessmentService.updateRiskDashboard(
                transactionData.getCustomerId(),
                transactionData.getRiskScore()
        );
    }

    private void processExecutiveNotifications(HighValueTransactionData transactionData) {
        // Executive alerts for transactions >= $100,000
        if (transactionData.getAmount().compareTo(EXECUTIVE_ALERT_THRESHOLD) >= 0) {
            notificationService.sendExecutiveAlert(
                    "Executive Alert: High-Value Transaction",
                    String.format("High-value transaction of %s processed for customer %s",
                            transactionData.getAmount(),
                            transactionData.getCustomerId()),
                    transactionData
            );
        }

        // Board-level notifications for exceptional amounts
        if (transactionData.getAmount().compareTo(new BigDecimal("1000000")) >= 0) {
            notificationService.sendBoardAlert(
                    "Board Alert: Exceptional Transaction Amount",
                    transactionData
            );
        }
    }

    private String getRiskLevel(Double riskScore) {
        if (riskScore == null) return "UNKNOWN";
        if (riskScore >= 0.8) return "HIGH";
        if (riskScore >= 0.6) return "MEDIUM";
        if (riskScore >= 0.3) return "LOW";
        return "VERY_LOW";
    }

    private String getComplianceStatus(List<String> complianceFlags) {
        if (complianceFlags.contains("SUSPICIOUS")) return "SUSPICIOUS";
        if (complianceFlags.contains("HIGH_RISK")) return "HIGH_RISK";
        if (complianceFlags.contains("REVIEW_REQUIRED")) return "REVIEW_REQUIRED";
        return "COMPLIANT";
    }

    private String getTransactionAction(String status) {
        return switch (status) {
            case "PENDING" -> "AWAITING_PROCESSING";
            case "PROCESSING" -> "IN_PROGRESS";
            case "COMPLETED" -> "SUCCESSFULLY_COMPLETED";
            case "FAILED" -> "PROCESSING_FAILED";
            case "SUSPENDED" -> "SUSPENDED_FOR_REVIEW";
            default -> "UNKNOWN_ACTION";
        };
    }

    private void executeEmergencyHighValueProcedures(HighValueTransactionData transactionData, Exception error) {
        log.error("EMERGENCY: Executing emergency high-value transaction procedures due to processing failure");

        try {
            // Emergency suspension for high-value transactions
            highValueTransactionService.emergencySuspend(
                    transactionData.getTransactionId(),
                    "PROCESSING_FAILURE"
            );

            // Emergency notification
            notificationService.sendEmergencyAlert(
                    "CRITICAL: High-Value Transaction Processing Failed",
                    String.format("Failed to process high-value transaction %s with amount %s: %s",
                            transactionData.getTransactionId(),
                            transactionData.getAmount(),
                            error.getMessage())
            );

            // Manual intervention alert
            notificationService.escalateToManualIntervention(
                    transactionData.getTransactionId(),
                    "HIGH_VALUE_TRANSACTION_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency high-value transaction procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("PAYMENT: High-value transaction validation failed for event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "HIGH_VALUE_TRANSACTION_VALIDATION_ERROR",
                null,
                "High-value transaction validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditHighValueTransactionProcessing(HighValueTransactionData transactionData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "HIGH_VALUE_TRANSACTION_PROCESSED",
                    transactionData != null ? transactionData.getCustomerId() : null,
                    String.format("High-value transaction processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "transactionId", transactionData != null ? transactionData.getTransactionId() : "unknown",
                            "accountId", transactionData != null ? transactionData.getAccountId() : "unknown",
                            "amount", transactionData != null ? transactionData.getAmount() : BigDecimal.ZERO,
                            "currency", transactionData != null ? transactionData.getCurrency() : "unknown",
                            "transactionType", transactionData != null ? transactionData.getTransactionType() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit high-value transaction processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: High-value transaction event sent to DLT - EventId: {}", event.getEventId());

        try {
            HighValueTransactionData transactionData = extractHighValueTransactionData(event.getPayload());

            // Emergency procedures for DLT events
            highValueTransactionService.emergencySuspend(
                    transactionData.getTransactionId(),
                    "DLT_PROCESSING_FAILURE"
            );

            // Critical alert
            notificationService.sendEmergencyAlert(
                    "CRITICAL: High-Value Transaction in DLT",
                    "High-value transaction could not be processed - immediate manual intervention required"
            );

        } catch (Exception e) {
            log.error("Failed to handle high-value transaction DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleHighValueTransactionFailure(GenericKafkaEvent event, String topic, int partition,
                                                 long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for high-value transaction processing - EventId: {}",
                event.getEventId(), e);

        try {
            HighValueTransactionData transactionData = extractHighValueTransactionData(event.getPayload());

            // Emergency protection
            highValueTransactionService.emergencySuspend(
                    transactionData.getTransactionId(),
                    "CIRCUIT_BREAKER_ACTIVATED"
            );

            // Emergency alert
            notificationService.sendEmergencyAlert(
                    "High-Value Transaction Circuit Breaker Open",
                    "High-value transaction processing is failing - immediate attention required"
            );

        } catch (Exception ex) {
            log.error("Failed to handle high-value transaction circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Double extractDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    private Boolean extractBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of();
    }

    // Data class
    @lombok.Data
    @lombok.Builder
    public static class HighValueTransactionData {
        private String transactionId;
        private String accountId;
        private String customerId;
        private BigDecimal amount;
        private String currency;
        private String transactionType;
        private String status;
        private String fromAccount;
        private String toAccount;
        private String beneficiaryName;
        private String beneficiaryBank;
        private String purpose;
        private Instant transactionDate;
        private Instant valueDate;
        private BigDecimal exchangeRate;
        private BigDecimal fees;
        private String priority;
        private Double riskScore;
        private List<String> complianceFlags;
        private Boolean crossBorder;
        private String jurisdiction;
    }
}