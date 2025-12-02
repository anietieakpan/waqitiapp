package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.config.OpenTelemetryTracingConfig.DlqTracingHelper;
import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.exception.RecoverableException;
import com.waqiti.common.exception.RegulatoryException;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.model.alert.CryptoComplianceRecoveryResult;
import com.waqiti.common.model.incident.Incident;
import com.waqiti.common.model.incident.IncidentPriority;
import com.waqiti.common.service.DlqEscalationService;
import com.waqiti.common.service.DlqNotificationAdapter;
import com.waqiti.common.service.IdempotencyService;
import com.waqiti.common.service.IncidentManagementService;
import com.waqiti.crypto.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Component
public class CryptoComplianceCompletedDlqConsumer extends BaseDlqConsumer {

    private final CryptoComplianceService cryptoComplianceService;
    private final IncidentManagementService incidentManagementService;
    private final DlqNotificationAdapter notificationAdapter;
    private final DlqEscalationService escalationService;
    private final IdempotencyService idempotencyService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Crypto compliance production services
    private final RegulatoryAlertService regulatoryAlertService;
    private final RegulatoryViolationIncidentService regulatoryViolationIncidentService;
    private final CustomerSecurityService customerSecurityService;
    private final ManualComplianceReviewQueue manualComplianceReviewQueue;
    private final SarFilingService sarFilingService;
    private final ChiefComplianceOfficerEscalationService ccoEscalationService;
    private final RegulatoryTeamAlertService regulatoryTeamAlertService;
    private final CryptoTransactionService cryptoTransactionService;
    private final CryptoRegulatoryService cryptoRegulatoryService;

    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Counter regulatoryViolationsCounter;
    private final Timer processingTimer;

    @Autowired(required = false)
    private DlqTracingHelper tracingHelper;

    public CryptoComplianceCompletedDlqConsumer(
            CryptoComplianceService cryptoComplianceService,
            IncidentManagementService incidentManagementService,
            DlqNotificationAdapter notificationAdapter,
            DlqEscalationService escalationService,
            IdempotencyService idempotencyService,
            RegulatoryAlertService regulatoryAlertService,
            RegulatoryViolationIncidentService regulatoryViolationIncidentService,
            CustomerSecurityService customerSecurityService,
            ManualComplianceReviewQueue manualComplianceReviewQueue,
            SarFilingService sarFilingService,
            ChiefComplianceOfficerEscalationService ccoEscalationService,
            RegulatoryTeamAlertService regulatoryTeamAlertService,
            CryptoTransactionService cryptoTransactionService,
            CryptoRegulatoryService cryptoRegulatoryService,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {

        super(meterRegistry, objectMapper);
        this.cryptoComplianceService = cryptoComplianceService;
        this.incidentManagementService = incidentManagementService;
        this.notificationAdapter = notificationAdapter;
        this.escalationService = escalationService;
        this.idempotencyService = idempotencyService;
        this.regulatoryAlertService = regulatoryAlertService;
        this.regulatoryViolationIncidentService = regulatoryViolationIncidentService;
        this.customerSecurityService = customerSecurityService;
        this.manualComplianceReviewQueue = manualComplianceReviewQueue;
        this.sarFilingService = sarFilingService;
        this.ccoEscalationService = ccoEscalationService;
        this.regulatoryTeamAlertService = regulatoryTeamAlertService;
        this.cryptoTransactionService = cryptoTransactionService;
        this.cryptoRegulatoryService = cryptoRegulatoryService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;

        this.processedCounter = Counter.builder("crypto_compliance_dlq_processed_total")
                .description("Total crypto compliance DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("crypto_compliance_dlq_errors_total")
                .description("Total crypto compliance DLQ errors")
                .register(meterRegistry);
        this.regulatoryViolationsCounter = Counter.builder("crypto_compliance_regulatory_violations_total")
                .description("Total regulatory violations detected")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("crypto_compliance_dlq_duration")
                .description("Crypto compliance DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "crypto-compliance-completed-dlq",
        groupId = "crypto-service-crypto-compliance-completed-dlq-group",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation-level=read_committed",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.max-poll-interval-ms=600000",
            "spring.kafka.consumer.session-timeout-ms=30000"
        }
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 16000),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {RecoverableException.class, RegulatoryException.class},
        exclude = {ValidationException.class},
        traversingCauses = "true",
        retryTopicSuffix = "-retry",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "dlq-notifications", fallbackMethod = "handleCryptoComplianceCompletedDlqFallback")
    @Retry(name = "dlq-notifications")
    public void handleCryptoComplianceCompletedDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-Transaction-Id", required = false) String transactionId,
            @Header(value = "X-Crypto-Asset", required = false) String cryptoAsset,
            @Header(value = "X-Compliance-Type", required = false) String complianceType,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        // Start distributed trace span
        Span processingSpan = null;
        if (tracingHelper != null) {
            processingSpan = tracingHelper.startDlqProcessingSpan(topic, eventId, correlationId);
        }

        try {
            // Distributed idempotency check
            if (idempotencyService.isAlreadyProcessed(eventId, "CryptoComplianceDlq")) {
                log.debug("Crypto compliance event already processed (idempotency): eventId={}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing crypto compliance DLQ event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, transactionId={}, cryptoAsset={}, complianceType={}",
                     topic, partition, offset, record.key(), correlationId, transactionId, cryptoAsset, complianceType);

            String complianceData = record.value();
            validateCryptoComplianceData(complianceData, eventId);

            // Extract metadata
            JsonNode complianceNode = objectMapper.readTree(complianceData);
            String extractedTransactionId = transactionId != null ? transactionId : extractField(complianceNode, "transactionId", "UNKNOWN");
            String extractedCryptoAsset = cryptoAsset != null ? cryptoAsset : extractField(complianceNode, "cryptoAsset", "UNKNOWN");
            String extractedComplianceType = complianceType != null ? complianceType : extractField(complianceNode, "complianceType", "UNKNOWN");

            // Process crypto compliance with full recovery logic
            CryptoComplianceRecoveryResult result = cryptoComplianceService.processCryptoComplianceCompletedDlq(
                complianceData,
                record.key(),
                correlationId,
                extractedTransactionId,
                extractedCryptoAsset,
                extractedComplianceType,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Handle recovery result based on compliance criticality
            if (result.isRecovered() && result.isCompliancePassed()) {
                handleSuccessfulCompliance(result, correlationId);
            } else if (result.isCriticalViolation()) {
                handleRegulatoryViolation(result, eventId, correlationId, extractedTransactionId, extractedCryptoAsset, extractedComplianceType);
            } else if (result.isRequiresManualReview()) {
                handleManualReviewRequired(result, correlationId, extractedTransactionId, extractedCryptoAsset);
            } else {
                handleFailedCompliance(result, eventId, correlationId, extractedTransactionId, extractedCryptoAsset);
            }

            // Mark as processed in distributed idempotency store
            idempotencyService.markAsProcessed(eventId, "CryptoComplianceDlq", result);

            processedCounter.increment();
            acknowledgment.acknowledge();

            if (processingSpan != null) {
                tracingHelper.addAttribute(processingSpan, "compliance.transaction_id", extractedTransactionId);
                tracingHelper.addAttribute(processingSpan, "compliance.crypto_asset", extractedCryptoAsset);
                tracingHelper.addAttribute(processingSpan, "compliance.type", extractedComplianceType);
                tracingHelper.addAttribute(processingSpan, "compliance.passed", result.isCompliancePassed());
                tracingHelper.completeSpan(processingSpan);
            }

            log.info("Successfully processed crypto compliance DLQ: eventId={}, transactionId={}, " +
                    "correlationId={}, passed={}",
                    eventId, extractedTransactionId, correlationId, result.isCompliancePassed());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in crypto compliance DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());

            if (processingSpan != null) {
                tracingHelper.recordError(processingSpan, e);
                processingSpan.end();
            }

            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();

        } catch (RegulatoryException e) {
            errorCounter.increment();
            regulatoryViolationsCounter.increment();
            log.error("Regulatory violation in crypto compliance DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());

            if (processingSpan != null) {
                tracingHelper.recordError(processingSpan, e);
                processingSpan.end();
            }

            handleRegulatoryException(record, e, correlationId);
            throw e; // Regulatory violations must be retried

        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error in crypto compliance DLQ: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());

            if (processingSpan != null) {
                tracingHelper.recordError(processingSpan, e);
                processingSpan.end();
            }
            throw e;

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in crypto compliance DLQ: eventId={}, correlationId={}",
                     eventId, correlationId, e);

            if (processingSpan != null) {
                tracingHelper.recordError(processingSpan, e);
                processingSpan.end();
            }

            handleCriticalFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(KafkaHeaders.ORIGINAL_OFFSET) long originalOffset,
            @Header(KafkaHeaders.ORIGINAL_PARTITION) int originalPartition) {

        String correlationId = generateCorrelationId();
        log.error("Crypto compliance event sent to DLT - REGULATORY COMPLIANCE AT RISK: " +
                 "topic={}, originalPartition={}, originalOffset={}, correlationId={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, exceptionMessage);

        try {
            // Extract metadata
            JsonNode complianceNode = objectMapper.readTree(record.value());
            String transactionId = extractField(complianceNode, "transactionId", "UNKNOWN");
            String cryptoAsset = extractField(complianceNode, "cryptoAsset", "UNKNOWN");
            String complianceType = extractField(complianceNode, "complianceType", "UNKNOWN");

            // Create P0 incident for regulatory compliance failure
            Incident incident = incidentManagementService.createIncident(
                String.format("[DLT] Crypto Regulatory Compliance Failure: %s", transactionId),
                String.format("CRITICAL: Crypto compliance permanently failed after all retries.\nTopic: %s\nTransaction: %s\nAsset: %s\nCompliance Type: %s\nError: %s",
                             topic, transactionId, cryptoAsset, complianceType, exceptionMessage),
                IncidentPriority.P0,
                "crypto-service",
                "CRYPTO_REGULATORY_COMPLIANCE_FAILURE",
                correlationId
            );

            // Immediate P0 escalation to regulatory team
            escalationService.escalateP0Incident(incident);

            // Send critical regulatory alert
            notificationAdapter.sendCriticalAlert(
                String.format("[P0 DLT REGULATORY] Crypto Compliance Failure: %s", transactionId),
                String.format("CRITICAL REGULATORY RISK: Crypto compliance permanently failed.\nTransaction: %s\nAsset: %s\nCompliance: %s\nError: %s\nIncident: %s\n\nIMMEDIATE ACTION REQUIRED - REGULATORY VIOLATION RISK",
                             transactionId, cryptoAsset, complianceType, exceptionMessage, incident.getId())
            );

        } catch (Exception e) {
            log.error("Failed to process DLT handler: correlationId={}", correlationId, e);
        }

        // Update DLT metrics
        Counter.builder("crypto_compliance_dlt_critical_events_total")
                .description("Critical crypto compliance events sent to DLT")
                .tag("topic", topic)
                .tag("severity", "critical")
                .tag("regulatory_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    public void handleCryptoComplianceCompletedDlqFallback(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String transactionId, String cryptoAsset, String complianceType,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for crypto compliance DLQ: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        try {
            // Create P1 incident for circuit breaker activation
            incidentManagementService.createIncident(
                "[CIRCUIT BREAKER] Crypto Compliance DLQ",
                String.format("Circuit breaker activated for crypto compliance DLQ.\nTransaction: %s\nAsset: %s\nCompliance: %s\nError: %s",
                             transactionId, cryptoAsset, complianceType, ex.getMessage()),
                IncidentPriority.P1,
                "crypto-service",
                "CIRCUIT_BREAKER_OPEN",
                correlationId
            );

            // Send alert notification
            notificationAdapter.sendAlert(
                "[CIRCUIT BREAKER] Crypto Compliance DLQ",
                String.format("Circuit breaker activated. Transaction: %s, Asset: %s, Compliance: %s, Error: %s",
                             transactionId, cryptoAsset, complianceType, ex.getMessage())
            );

        } catch (Exception e) {
            log.error("Failed to process fallback: correlationId={}", correlationId, e);
        }

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("crypto_compliance_dlq_circuit_breaker_activations_total")
                .tag("regulatory_impact", "medium")
                .register(meterRegistry)
                .increment();
    }

    private void validateCryptoComplianceData(String complianceData, String eventId) {
        if (complianceData == null || complianceData.trim().isEmpty()) {
            throw new ValidationException("Crypto compliance data is null or empty for eventId: " + eventId);
        }

        if (!complianceData.contains("transactionId")) {
            throw new ValidationException("Crypto compliance data missing transactionId for eventId: " + eventId);
        }

        if (!complianceData.contains("cryptoAsset")) {
            throw new ValidationException("Crypto compliance data missing cryptoAsset for eventId: " + eventId);
        }

        if (!complianceData.contains("complianceType")) {
            throw new ValidationException("Crypto compliance data missing complianceType for eventId: " + eventId);
        }

        // Validate regulatory compliance requirements
        validateRegulatoryCompliance(complianceData, eventId);
    }

    private void validateRegulatoryCompliance(String complianceData, String eventId) {
        try {
            JsonNode data = objectMapper.readTree(complianceData);
            String complianceType = data.get("complianceType").asText();
            String cryptoAsset = data.get("cryptoAsset").asText();

            // Validate AML compliance requirements
            if ("AML".equals(complianceType)) {
                if (!data.has("amlVerification")) {
                    throw new RegulatoryException("AML compliance missing verification data for eventId: " + eventId);
                }

                if (!data.has("sourceOfFunds")) {
                    log.warn("AML compliance missing source of funds verification: eventId={}", eventId);
                }
            }

            // Validate KYC compliance requirements
            if ("KYC".equals(complianceType)) {
                if (!data.has("kycVerification")) {
                    throw new RegulatoryException("KYC compliance missing verification data for eventId: " + eventId);
                }
            }

            // Validate sanctions screening
            if (!data.has("sanctionsScreening")) {
                throw new RegulatoryException("Missing sanctions screening for crypto compliance: " + eventId);
            }

            // Validate transaction amount thresholds
            if (data.has("transactionAmount")) {
                BigDecimal amount = new BigDecimal(data.get("transactionAmount").asText());
                if (amount.compareTo(new BigDecimal("10000")) > 0) {
                    if (!data.has("ctrFiling")) {
                        log.warn("Large crypto transaction missing CTR filing requirements: eventId={}", eventId);
                    }
                }
            }

        } catch (Exception e) {
            throw new ValidationException("Failed to validate regulatory compliance: " + e.getMessage());
        }
    }

    private void handleSuccessfulCompliance(CryptoComplianceRecoveryResult result, String correlationId) {
        log.info("Crypto compliance successfully completed: transactionId={}, correlationId={}",
                result.getTransactionId(), correlationId);

        try {
            // Release transaction hold
            cryptoTransactionService.releaseTransactionHold(
                result.getTransactionId(),
                "Compliance verification completed - all checks passed",
                correlationId
            );

            // Record successful compliance metric
            Counter.builder("crypto_compliance_successful_total")
                    .tag("transaction_id", result.getTransactionId())
                    .register(meterRegistry)
                    .increment();

            log.info("Compliance hold released for transaction: {} correlationId: {}",
                    result.getTransactionId(), correlationId);

        } catch (Exception e) {
            log.error("Failed to handle successful compliance for transaction: {} correlationId: {}",
                    result.getTransactionId(), correlationId, e);
        }
    }

    private void handleRegulatoryViolation(CryptoComplianceRecoveryResult result, String eventId, String correlationId,
                                           String transactionId, String cryptoAsset, String complianceType) {
        log.error("CRITICAL: Regulatory violation detected - transaction={} correlationId={}",
                result.getTransactionId(), correlationId);

        try {
            // Immediately freeze transaction
            cryptoTransactionService.freezeTransaction(
                result.getTransactionId(),
                "Regulatory violation: " + String.join(", ", result.getViolationFlags()),
                correlationId
            );

            // Determine violation severity
            boolean isSanctionsHit = result.getViolationFlags().contains("SANCTIONS_HIT");
            boolean isCriticalAML = result.getViolationFlags().contains("AML_FAILURE") &&
                    "CRITICAL".equals(result.getRiskScore());

            // Create P0 incident for critical violations
            if (isSanctionsHit || isCriticalAML) {
                regulatoryViolationIncidentService.createP0RegulatoryViolation(
                    result.getTransactionId(),
                    result.getCustomerId() != null ? result.getCustomerId() : "UNKNOWN",
                    String.join(", ", result.getViolationFlags()),
                    "Critical regulatory violation detected in crypto compliance",
                    correlationId
                );

                // Escalate to CCO for sanctions hits
                if (isSanctionsHit) {
                    ccoEscalationService.escalateSanctionsHit(
                        result.getTransactionId(),
                        result.getCustomerId() != null ? result.getCustomerId() : "UNKNOWN",
                        "crypto-wallet-address",
                        "OFAC",
                        eventId,
                        correlationId
                    );

                    // Send critical sanctions alert
                    regulatoryAlertService.sendSanctionsHitAlert(
                        result.getTransactionId(),
                        result.getCustomerId() != null ? result.getCustomerId() : "UNKNOWN",
                        "crypto-wallet-address",
                        "OFAC",
                        correlationId
                    );

                    // Block customer permanently
                    if (result.getCustomerId() != null) {
                        customerSecurityService.blockCustomerForSanctionsHit(
                            result.getCustomerId(),
                            "crypto-wallet-address",
                            "OFAC",
                            correlationId
                        );
                    }
                }
            } else {
                // P1 incident for non-critical violations
                regulatoryViolationIncidentService.createP1RegulatoryViolation(
                    result.getTransactionId(),
                    result.getCustomerId() != null ? result.getCustomerId() : "UNKNOWN",
                    String.join(", ", result.getViolationFlags()),
                    "Regulatory violation requires review",
                    correlationId
                );
            }

            // File SAR for suspicious activity
            if (!result.getViolationFlags().isEmpty()) {
                sarFilingService.fileSar(
                    result.getTransactionId(),
                    result.getCustomerId() != null ? result.getCustomerId() : "UNKNOWN",
                    String.join(", ", result.getViolationFlags()),
                    correlationId
                );
            }

            // Send regulatory alert
            regulatoryAlertService.sendRegulatoryViolationAlert(
                result.getTransactionId(),
                result.getCustomerId() != null ? result.getCustomerId() : "UNKNOWN",
                String.join(", ", result.getViolationFlags()),
                "Regulatory violation detected",
                isSanctionsHit || isCriticalAML ? "CRITICAL" : "HIGH",
                correlationId
            );

            regulatoryViolationsCounter.increment();

        } catch (Exception e) {
            log.error("CRITICAL: Failed to handle regulatory violation for transaction: {} correlationId: {}",
                    result.getTransactionId(), correlationId, e);
        }
    }

    private void handleManualReviewRequired(CryptoComplianceRecoveryResult result, String correlationId,
                                            String transactionId, String cryptoAsset) {
        log.warn("Manual compliance review required: transaction={} risk={} correlationId={}",
                result.getTransactionId(), result.getRiskScore(), correlationId);

        try {
            // Queue transaction for manual review
            String priority = "CRITICAL".equals(result.getRiskScore()) || "HIGH".equals(result.getRiskScore())
                    ? "HIGH" : "MEDIUM";

            manualComplianceReviewQueue.queueForReview(
                result.getTransactionId(),
                result.getCustomerId() != null ? result.getCustomerId() : "UNKNOWN",
                "Manual review required - Risk: " + result.getRiskScore() +
                        ", Violations: " + String.join(", ", result.getViolationFlags()),
                result.getRiskScore(),
                priority,
                correlationId
            );

            // Send manual review alert
            regulatoryAlertService.sendManualReviewAlert(
                result.getTransactionId(),
                result.getCustomerId() != null ? result.getCustomerId() : "UNKNOWN",
                "Risk score: " + result.getRiskScore(),
                priority,
                correlationId
            );

            // Alert regulatory team
            regulatoryTeamAlertService.alertRegulatoryTeamForHighPriorityIssue(
                result.getTransactionId(),
                result.getCustomerId() != null ? result.getCustomerId() : "UNKNOWN",
                "MANUAL_REVIEW_REQUIRED",
                "Risk: " + result.getRiskScore() + ", Flags: " + String.join(", ", result.getViolationFlags()),
                correlationId
            );

            log.warn("Transaction queued for manual compliance review: {} priority: {} correlationId: {}",
                    result.getTransactionId(), priority, correlationId);

        } catch (Exception e) {
            log.error("Failed to queue transaction for manual review: {} correlationId: {}",
                    result.getTransactionId(), correlationId, e);
        }
    }

    private void handleFailedCompliance(CryptoComplianceRecoveryResult result, String eventId, String correlationId,
                                        String transactionId, String cryptoAsset) {
        log.error("Crypto compliance failed: transaction={} correlationId={}", result.getTransactionId(), correlationId);

        try {
            // Create P1 compliance failure incident
            regulatoryViolationIncidentService.createP2RegulatoryViolation(
                result.getTransactionId(),
                result.getCustomerId() != null ? result.getCustomerId() : "UNKNOWN",
                "COMPLIANCE_FAILURE",
                "Compliance processing failed - requires investigation",
                correlationId
            );

            // Escalate to compliance team
            ccoEscalationService.escalateToComplianceTeam(
                result.getTransactionId(),
                result.getCustomerId() != null ? result.getCustomerId() : "UNKNOWN",
                "COMPLIANCE_FAILURE",
                "Compliance check failed for transaction",
                eventId,
                correlationId
            );

            // Send failure alert
            regulatoryAlertService.sendRegulatoryViolationAlert(
                result.getTransactionId(),
                result.getCustomerId() != null ? result.getCustomerId() : "UNKNOWN",
                "COMPLIANCE_FAILURE",
                "Compliance processing failed",
                "MEDIUM",
                correlationId
            );

            log.error("Compliance failure handled: transaction={} escalated to compliance team, correlationId={}",
                    result.getTransactionId(), correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to handle compliance failure for transaction: {} correlationId: {}",
                    result.getTransactionId(), correlationId, e);
        }
    }

    private void updateCryptoComplianceMetrics(CryptoComplianceRecoveryResult result, String correlationId) {
        // Record compliance processing metrics using MeterRegistry directly
        Counter.builder("crypto_compliance_processed")
                .tag("risk_score", result.getRiskScore() != null ? result.getRiskScore() : "UNKNOWN")
                .tag("compliance_passed", String.valueOf(result.isCompliancePassed()))
                .register(meterRegistry)
                .increment();

        if (!result.isCompliancePassed()) {
            Counter.builder("crypto_compliance_violations")
                    .tag("violation_count", String.valueOf(result.getViolationFlags().size()))
                    .register(meterRegistry)
                    .increment();
        }
    }

    private void handleRegulatoryException(ConsumerRecord<String, String> record,
                                          RegulatoryException e, String correlationId) {
        log.error("CRITICAL: Regulatory exception in crypto compliance - correlationId: {}, error: {}",
                correlationId, e.getMessage());

        try {
            // Extract transaction details
            String transactionId = extractField(objectMapper.readTree(record.value()), "transactionId", "UNKNOWN");

            // Create P0 incident for regulatory exception
            regulatoryViolationIncidentService.createP0RegulatoryViolation(
                transactionId,
                "UNKNOWN",
                "REGULATORY_EXCEPTION",
                e.getMessage(),
                correlationId
            );

            // Send immediate regulatory team alert
            regulatoryTeamAlertService.alertRegulatoryTeamForHighPriorityIssue(
                transactionId,
                "UNKNOWN",
                "REGULATORY_EXCEPTION",
                e.getMessage(),
                correlationId
            );

            regulatoryViolationsCounter.increment();

        } catch (Exception ex) {
            log.error("Failed to handle regulatory exception: correlationId: {}", correlationId, ex);
        }
    }

    private void executeRegulatoryEmergencyProtocol(ConsumerRecord<String, String> record,
                                                    String topic, String exceptionMessage,
                                                    String correlationId) {
        try {
            // Execute comprehensive regulatory emergency protocol
            RegulatoryEmergencyResult emergency = regulatoryEmergencyService.execute(
                record.key(),
                record.value(),
                topic,
                exceptionMessage,
                correlationId
            );

            if (emergency.isRegulatoryRiskMitigated()) {
                log.info("Regulatory risk mitigated: correlationId={}, mitigatedTransactions={}",
                        correlationId, emergency.getMitigatedTransactions());

                // Apply risk mitigation measures
                riskMitigationService.applyMeasures(
                    emergency.getAffectedTransactions(),
                    emergency.getMitigationMeasures(),
                    correlationId
                );
            }
        } catch (Exception e) {
            log.error("Regulatory emergency protocol failed: correlationId={}", correlationId, e);
        }
    }

    private void storeForRegulatoryAudit(ConsumerRecord<String, String> record, String topic,
                                         String exceptionMessage, String correlationId) {
        regulatoryAuditRepository.save(
            RegulatoryAuditRecord.builder()
                .sourceTopic(topic)
                .transactionId(extractTransactionId(record.value()))
                .cryptoAsset(extractCryptoAsset(record.value()))
                .messageKey(record.key())
                .messageValue(record.value())
                .failureReason(exceptionMessage)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .status(AuditStatus.PENDING_REGULATORY_REVIEW)
                .regulatoryLevel(RegulatoryLevel.HIGH)
                .requiresReporting(true)
                .complianceType(extractComplianceType(record.value()))
                .build()
        );
    }

    private void sendChiefComplianceOfficerAlert(ConsumerRecord<String, String> record, String topic,
                                                 String exceptionMessage, String correlationId) {
        chiefComplianceOfficerAlertService.sendCriticalAlert(
            CCOAlertLevel.CRITICAL,
            "Crypto compliance permanently failed - regulatory violation risk",
            Map.of(
                "topic", topic,
                "transactionId", extractTransactionId(record.value()),
                "cryptoAsset", extractCryptoAsset(record.value()),
                "complianceType", extractComplianceType(record.value()),
                "error", exceptionMessage,
                "correlationId", correlationId,
                "regulatoryImpact", "HIGH",
                "requiredAction", "Immediate CCO review for regulatory compliance",
                "violationRisk", "Potential AML/KYC violation"
            )
        );
    }

    private void freezeRelatedCryptoTransactions(ConsumerRecord<String, String> record, String correlationId) {
        String transactionId = extractTransactionId(record.value());
        String customerId = extractCustomerId(record.value());

        // Freeze all pending crypto transactions for this customer
        cryptoTransactionService.freezeCustomerTransactions(
            customerId,
            FreezeReason.COMPLIANCE_FAILURE,
            correlationId
        );

        // Create compliance freeze record
        complianceFreezeRepository.save(
            ComplianceFreeze.builder()
                .customerId(customerId)
                .transactionId(transactionId)
                .freezeReason(FreezeReason.COMPLIANCE_FAILURE)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .status(FreezeStatus.ACTIVE)
                .requiresManualReview(true)
                .build()
        );
    }

    private boolean isAlreadyProcessed(String eventId) {
        Long processTime = processedEvents.get(eventId);
        if (processTime != null) {
            return System.currentTimeMillis() - processTime < Duration.ofHours(24).toMillis();
        }
        return false;
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
        if (processedEvents.size() > 10000) {
            cleanupOldProcessedEvents();
        }
    }

    private void cleanupOldProcessedEvents() {
        long cutoffTime = System.currentTimeMillis() - Duration.ofHours(24).toMillis();
        processedEvents.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }

    private String extractTransactionId(String value) {
        try {
            return objectMapper.readTree(value).get("transactionId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractCryptoAsset(String value) {
        try {
            return objectMapper.readTree(value).get("cryptoAsset").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractComplianceType(String value) {
        try {
            return objectMapper.readTree(value).get("complianceType").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractCustomerId(String value) {
        try {
            return objectMapper.readTree(value).get("customerId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }
}