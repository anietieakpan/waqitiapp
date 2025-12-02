package com.waqiti.dlq.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseConsumer;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.monitoring.CircuitBreaker;
import com.waqiti.dlq.dto.CriticalPaymentProcessingDlqEventDto;
import com.waqiti.dlq.service.CriticalPaymentProcessingDlqService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Critical DLQ Consumer for failed payment processing events.
 * Handles high-priority payment failures with immediate escalation, automated recovery,
 * real-time business impact assessment, and emergency response procedures.
 *
 * This consumer processes critical payment failures that could result in significant
 * business impact, regulatory violations, or customer dissatisfaction.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Component
public class CriticalPaymentProcessingDlqConsumer extends BaseConsumer<CriticalPaymentProcessingDlqEventDto> {

    private static final String TOPIC_NAME = "critical-payment-processing-dlq";
    private static final String CONSUMER_GROUP = "dlq-service-critical-payment";
    private static final String ESCALATION_TOPIC = "critical-payment-escalation";

    private final CriticalPaymentProcessingDlqService dlqService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    // Critical Payment Metrics
    private final Counter dlqEventsProcessedCounter;
    private final Counter dlqEventsFailedCounter;
    private final Counter criticalPaymentFailuresCounter;
    private final Counter emergencyEscalationsCounter;
    private final Counter automaticRecoveriesCounter;
    private final Counter manualInterventionsRequiredCounter;
    private final Timer dlqProcessingTimer;
    private final Timer paymentRecoveryTimer;

    @Autowired
    public CriticalPaymentProcessingDlqConsumer(
            CriticalPaymentProcessingDlqService dlqService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector,
            MeterRegistry meterRegistry) {

        this.dlqService = dlqService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = new CircuitBreaker("critical-payment-dlq", 3, Duration.ofMinutes(1));

        // Initialize critical payment metrics
        this.dlqEventsProcessedCounter = Counter.builder("critical_payment_dlq_events_processed_total")
                .description("Total number of critical payment DLQ events processed")
                .tag("service", "dlq")
                .tag("consumer", "critical-payment-processing")
                .register(meterRegistry);

        this.dlqEventsFailedCounter = Counter.builder("critical_payment_dlq_events_failed_total")
                .description("Total number of failed critical payment DLQ events")
                .tag("service", "dlq")
                .tag("consumer", "critical-payment-processing")
                .register(meterRegistry);

        this.criticalPaymentFailuresCounter = Counter.builder("critical_payment_failures_total")
                .description("Total number of critical payment failures")
                .tag("service", "dlq")
                .register(meterRegistry);

        this.emergencyEscalationsCounter = Counter.builder("emergency_escalations_total")
                .description("Total number of emergency escalations")
                .tag("service", "dlq")
                .tag("type", "critical-payment")
                .register(meterRegistry);

        this.automaticRecoveriesCounter = Counter.builder("automatic_recoveries_total")
                .description("Total number of automatic recoveries")
                .tag("service", "dlq")
                .tag("type", "critical-payment")
                .register(meterRegistry);

        this.manualInterventionsRequiredCounter = Counter.builder("manual_interventions_required_total")
                .description("Total number of manual interventions required")
                .tag("service", "dlq")
                .tag("type", "critical-payment")
                .register(meterRegistry);

        this.dlqProcessingTimer = Timer.builder("critical_payment_dlq_processing_duration")
                .description("Time taken to process critical payment DLQ events")
                .tag("service", "dlq")
                .register(meterRegistry);

        this.paymentRecoveryTimer = Timer.builder("payment_recovery_duration")
                .description("Time taken for payment recovery")
                .tag("service", "dlq")
                .register(meterRegistry);
    }

    /**
     * Processes critical payment processing DLQ events with immediate priority and automated recovery.
     *
     * @param eventJson The JSON representation of the critical payment DLQ event
     * @param key The message key
     * @param partition The partition number
     * @param offset The message offset
     * @param timestamp The message timestamp
     * @param acknowledgment The acknowledgment for manual commit
     */
    @KafkaListener(
        topics = TOPIC_NAME,
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @Retryable(
        value = {Exception.class},
        maxAttempts = 2, // Reduced retries for immediate escalation
        backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void processCriticalPaymentDlqEvent(
            String eventJson,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();
        String correlationId = UUID.randomUUID().toString();

        try {
            log.error("CRITICAL: Processing failed payment event - CorrelationId: {}, Key: {}, Partition: {}, Offset: {}",
                    correlationId, key, partition, timestamp);

            // Deserialize DLQ event
            CriticalPaymentProcessingDlqEventDto dlqEvent = deserializeEvent(eventJson, correlationId);
            if (dlqEvent == null) {
                return;
            }

            // Validate DLQ event
            validateEvent(dlqEvent, correlationId);

            // Process with circuit breaker (reduced tolerance for critical payments)
            circuitBreaker.executeSupplier(() -> {
                processCriticalPaymentFailure(dlqEvent, correlationId);
                return null;
            });

            // Track metrics
            dlqEventsProcessedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "success");

            // Acknowledge message
            acknowledgment.acknowledge();

            log.warn("Critical payment DLQ event processed - CorrelationId: {}, PaymentId: {}, RecoveryAction: {}",
                    correlationId, dlqEvent.getPaymentId(), dlqEvent.getRecoveryAction());

        } catch (Exception e) {
            handleProcessingError(eventJson, key, correlationId, e, acknowledgment);
        } finally {
            sample.stop(dlqProcessingTimer);
        }
    }

    /**
     * Processes critical payment failures with comprehensive business impact analysis and recovery.
     */
    private void processCriticalPaymentFailure(CriticalPaymentProcessingDlqEventDto dlqEvent, String correlationId) {
        Timer.Sample recoveryTimer = Timer.start();

        try {
            log.error("Processing critical payment failure - PaymentId: {}, FailureType: {} - CorrelationId: {}",
                    dlqEvent.getPaymentId(), dlqEvent.getFailureType(), correlationId);

            criticalPaymentFailuresCounter.increment();

            // Immediate business impact assessment
            var businessImpactAssessment = dlqService.assessBusinessImpact(dlqEvent, correlationId);

            // Process based on failure type and severity
            switch (dlqEvent.getFailureType()) {
                case "PAYMENT_GATEWAY_FAILURE":
                    processPaymentGatewayFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "CARD_PROCESSING_FAILURE":
                    processCardProcessingFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "BANK_TRANSFER_FAILURE":
                    processBankTransferFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "CRYPTOCURRENCY_FAILURE":
                    processCryptocurrencyFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "WIRE_TRANSFER_FAILURE":
                    processWireTransferFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "REGULATORY_COMPLIANCE_FAILURE":
                    processRegulatoryComplianceFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "FRAUD_DETECTION_FAILURE":
                    processFraudDetectionFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "SETTLEMENT_FAILURE":
                    processSettlementFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "LIQUIDITY_CONSTRAINT_FAILURE":
                    processLiquidityConstraintFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                default:
                    processGenericPaymentFailure(dlqEvent, businessImpactAssessment, correlationId);
            }

            // Emergency escalation for critical business impact
            if (businessImpactAssessment.requiresEmergencyEscalation()) {
                initiateEmergencyEscalation(dlqEvent, businessImpactAssessment, correlationId);
            }

            // Customer communication for customer-facing failures
            if (businessImpactAssessment.affectsCustomerExperience()) {
                initiateCustomerCommunication(dlqEvent, businessImpactAssessment, correlationId);
            }

            // Update business intelligence and dashboards
            dlqService.updateCriticalPaymentDashboards(dlqEvent, businessImpactAssessment, correlationId);

        } finally {
            recoveryTimer.stop(paymentRecoveryTimer);
        }
    }

    /**
     * Processes payment gateway failures with multi-gateway failover.
     */
    private void processPaymentGatewayFailure(CriticalPaymentProcessingDlqEventDto dlqEvent,
                                            var businessImpactAssessment, String correlationId) {
        log.error("CRITICAL: Payment gateway failure - CorrelationId: {}, Gateway: {}",
                correlationId, dlqEvent.getPaymentGateway());

        // Immediate gateway health assessment
        var gatewayHealthAssessment = dlqService.assessPaymentGatewayHealth(dlqEvent.getPaymentGateway(), correlationId);

        if (gatewayHealthAssessment.isSystemWideOutage()) {
            log.error("System-wide gateway outage detected - CorrelationId: {}", correlationId);

            // Activate backup payment gateways
            var failoverResult = dlqService.activateBackupPaymentGateways(dlqEvent, correlationId);

            if (failoverResult.isSuccessful()) {
                // Retry payment with backup gateway
                var retryResult = dlqService.retryPaymentWithBackupGateway(dlqEvent, failoverResult, correlationId);

                if (retryResult.isSuccessful()) {
                    log.info("Payment successfully recovered using backup gateway - CorrelationId: {}, BackupGateway: {}",
                            correlationId, retryResult.getBackupGateway());
                    automaticRecoveriesCounter.increment();
                } else {
                    // Manual intervention required
                    handleGatewayFailoverFailure(dlqEvent, retryResult, correlationId);
                }
            } else {
                // Critical - all gateways down
                initiateGatewayEmergencyProtocol(dlqEvent, correlationId);
                emergencyEscalationsCounter.increment();
            }
        } else {
            // Isolated gateway issue - attempt direct recovery
            var recoveryResult = dlqService.attemptGatewayRecovery(dlqEvent, gatewayHealthAssessment, correlationId);

            if (recoveryResult.isSuccessful()) {
                automaticRecoveriesCounter.increment();
            } else {
                manualInterventionsRequiredCounter.increment();
                dlqService.escalateToPaymentEngineering(dlqEvent, recoveryResult, correlationId);
            }
        }
    }

    /**
     * Processes card processing failures with card-specific recovery strategies.
     */
    private void processCardProcessingFailure(CriticalPaymentProcessingDlqEventDto dlqEvent,
                                            var businessImpactAssessment, String correlationId) {
        log.error("Critical card processing failure - CorrelationId: {}, CardType: {}",
                correlationId, dlqEvent.getCardType());

        // Analyze card failure reasons
        var cardFailureAnalysis = dlqService.analyzeCardFailure(dlqEvent, correlationId);

        switch (cardFailureAnalysis.getFailureReason()) {
            case "INSUFFICIENT_FUNDS":
                handleInsufficientFundsFailure(dlqEvent, cardFailureAnalysis, correlationId);
                break;
            case "CARD_EXPIRED":
                handleExpiredCardFailure(dlqEvent, cardFailureAnalysis, correlationId);
                break;
            case "CARD_BLOCKED":
                handleBlockedCardFailure(dlqEvent, cardFailureAnalysis, correlationId);
                break;
            case "INVALID_CVV":
                handleInvalidCvvFailure(dlqEvent, cardFailureAnalysis, correlationId);
                break;
            case "NETWORK_TIMEOUT":
                handleCardNetworkTimeout(dlqEvent, cardFailureAnalysis, correlationId);
                break;
            case "FRAUD_SUSPECTED":
                handleFraudSuspectedFailure(dlqEvent, cardFailureAnalysis, correlationId);
                break;
            default:
                handleGenericCardFailure(dlqEvent, cardFailureAnalysis, correlationId);
        }

        // Update card processing intelligence
        dlqService.updateCardProcessingIntelligence(dlqEvent, cardFailureAnalysis, correlationId);
    }

    /**
     * Processes bank transfer failures with banking system integration recovery.
     */
    private void processBankTransferFailure(CriticalPaymentProcessingDlqEventDto dlqEvent,
                                          var businessImpactAssessment, String correlationId) {
        log.error("Critical bank transfer failure - CorrelationId: {}, BankCode: {}",
                correlationId, dlqEvent.getBankCode());

        // Bank system availability check
        var bankSystemStatus = dlqService.checkBankSystemAvailability(dlqEvent.getBankCode(), correlationId);

        if (bankSystemStatus.isUnavailable()) {
            // Wait for bank system recovery with exponential backoff
            var bankRecoveryResult = dlqService.waitForBankSystemRecovery(dlqEvent, bankSystemStatus, correlationId);

            if (bankRecoveryResult.isRecovered()) {
                // Retry transfer
                var retryResult = dlqService.retryBankTransfer(dlqEvent, correlationId);
                if (retryResult.isSuccessful()) {
                    automaticRecoveriesCounter.increment();
                }
            } else {
                // Escalate to banking relationships team
                dlqService.escalateToBankingRelationships(dlqEvent, bankRecoveryResult, correlationId);
                manualInterventionsRequiredCounter.increment();
            }
        } else {
            // Transfer-specific issue
            var transferAnalysis = dlqService.analyzeBankTransferFailure(dlqEvent, correlationId);
            dlqService.handleSpecificTransferIssue(dlqEvent, transferAnalysis, correlationId);
        }
    }

    /**
     * Processes cryptocurrency payment failures with blockchain recovery.
     */
    private void processCryptocurrencyFailure(CriticalPaymentProcessingDlqEventDto dlqEvent,
                                            var businessImpactAssessment, String correlationId) {
        log.error("Critical cryptocurrency failure - CorrelationId: {}, Cryptocurrency: {}",
                correlationId, dlqEvent.getCryptocurrency());

        // Blockchain network status assessment
        var blockchainStatus = dlqService.assessBlockchainNetworkStatus(dlqEvent.getCryptocurrency(), correlationId);

        if (blockchainStatus.hasNetworkCongestion()) {
            // Implement gas optimization and retry strategies
            var gasOptimizationResult = dlqService.optimizeGasAndRetry(dlqEvent, blockchainStatus, correlationId);

            if (gasOptimizationResult.isSuccessful()) {
                automaticRecoveriesCounter.increment();
            } else {
                // Consider alternative cryptocurrency or manual processing
                dlqService.considerAlternativeCryptocurrency(dlqEvent, gasOptimizationResult, correlationId);
                manualInterventionsRequiredCounter.increment();
            }
        } else if (blockchainStatus.hasSecurityConcerns()) {
            // Enhanced security verification
            var securityVerification = dlqService.performEnhancedSecurityVerification(dlqEvent, correlationId);
            dlqService.handleCryptocurrencySecurityConcerns(dlqEvent, securityVerification, correlationId);
        } else {
            // Standard cryptocurrency retry with blockchain optimization
            var retryResult = dlqService.retryCryptocurrencyPayment(dlqEvent, correlationId);
            if (retryResult.isSuccessful()) {
                automaticRecoveriesCounter.increment();
            }
        }
    }

    /**
     * Processes regulatory compliance failures with immediate compliance remediation.
     */
    private void processRegulatoryComplianceFailure(CriticalPaymentProcessingDlqEventDto dlqEvent,
                                                  var businessImpactAssessment, String correlationId) {
        log.error("CRITICAL: Regulatory compliance failure - CorrelationId: {}, Regulation: {}",
                correlationId, dlqEvent.getFailedRegulation());

        // Immediate compliance assessment
        var complianceAssessment = dlqService.assessRegulatoryComplianceFailure(dlqEvent, correlationId);

        if (complianceAssessment.requiresImmediateRemediation()) {
            // Emergency compliance remediation
            var remediationResult = dlqService.performEmergencyComplianceRemediation(dlqEvent, complianceAssessment, correlationId);

            if (remediationResult.isSuccessful()) {
                // Retry payment with compliance fixes
                var retryResult = dlqService.retryPaymentWithComplianceFixes(dlqEvent, remediationResult, correlationId);
                if (retryResult.isSuccessful()) {
                    automaticRecoveriesCounter.increment();
                }
            } else {
                // Escalate to compliance team
                dlqService.escalateToComplianceTeam(dlqEvent, remediationResult, correlationId);
                emergencyEscalationsCounter.increment();
            }
        }

        // Regulatory reporting if required
        if (complianceAssessment.requiresRegulatoryReporting()) {
            dlqService.initiateRegulatoryReporting(dlqEvent, complianceAssessment, correlationId);
        }
    }

    /**
     * Processes settlement failures with clearing house coordination.
     */
    private void processSettlementFailure(CriticalPaymentProcessingDlqEventDto dlqEvent,
                                        var businessImpactAssessment, String correlationId) {
        log.error("Critical settlement failure - CorrelationId: {}, ClearingHouse: {}",
                correlationId, dlqEvent.getClearingHouse());

        // Settlement system health check
        var settlementSystemStatus = dlqService.checkSettlementSystemHealth(dlqEvent.getClearingHouse(), correlationId);

        if (settlementSystemStatus.isOperational()) {
            // Settlement-specific issue - investigate and retry
            var settlementAnalysis = dlqService.analyzeSettlementFailure(dlqEvent, correlationId);
            var retryResult = dlqService.retrySettlement(dlqEvent, settlementAnalysis, correlationId);

            if (retryResult.isSuccessful()) {
                automaticRecoveriesCounter.increment();
            } else {
                manualInterventionsRequiredCounter.increment();
                dlqService.escalateToSettlementTeam(dlqEvent, retryResult, correlationId);
            }
        } else {
            // System-wide settlement issues
            dlqService.coordinateWithClearingHouse(dlqEvent, settlementSystemStatus, correlationId);
            emergencyEscalationsCounter.increment();
        }
    }

    /**
     * Initiates emergency escalation procedures for critical payment failures.
     */
    private void initiateEmergencyEscalation(CriticalPaymentProcessingDlqEventDto dlqEvent,
                                           var businessImpactAssessment, String correlationId) {
        log.error("EMERGENCY ESCALATION: Critical payment failure - CorrelationId: {}, BusinessImpact: {}",
                correlationId, businessImpactAssessment.getImpactLevel());

        emergencyEscalationsCounter.increment();

        // Immediate notification to executive team
        dlqService.notifyExecutiveTeam(dlqEvent, businessImpactAssessment, correlationId);

        // Activate incident response team
        dlqService.activateIncidentResponseTeam(dlqEvent, correlationId);

        // Create emergency bridge
        dlqService.createEmergencyBridge(dlqEvent, correlationId);

        // Prepare business continuity measures
        dlqService.prepareBusiness ContinuityMeasures(dlqEvent, businessImpactAssessment, correlationId);

        // Send escalation event
        try {
            kafkaTemplate.send(ESCALATION_TOPIC, dlqEvent.getPaymentId(), Map.of(
                "escalationType", "EMERGENCY",
                "paymentId", dlqEvent.getPaymentId(),
                "businessImpact", businessImpactAssessment.getImpactLevel(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "requiresImmediateAttention", true
            ));
        } catch (Exception e) {
            log.error("Failed to send emergency escalation - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage());
        }
    }

    /**
     * Initiates customer communication for customer-facing payment failures.
     */
    private void initiateCustomerCommunication(CriticalPaymentProcessingDlqEventDto dlqEvent,
                                             var businessImpactAssessment, String correlationId) {
        // Determine communication urgency and channel
        var communicationStrategy = dlqService.determineCommunicationStrategy(dlqEvent, businessImpactAssessment, correlationId);

        // Send immediate customer notification
        dlqService.sendCustomerNotification(dlqEvent, communicationStrategy, correlationId);

        // Prepare customer service briefing
        dlqService.prepareCustomerServiceBriefing(dlqEvent, correlationId);

        // Update customer account with failure details
        dlqService.updateCustomerAccountWithFailureDetails(dlqEvent, correlationId);
    }

    /**
     * Deserializes the DLQ event JSON into a CriticalPaymentProcessingDlqEventDto.
     */
    private CriticalPaymentProcessingDlqEventDto deserializeEvent(String eventJson, String correlationId) {
        try {
            return objectMapper.readValue(eventJson, CriticalPaymentProcessingDlqEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize critical payment DLQ event - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage(), e);
            dlqEventsFailedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "deserialization_error");
            return null;
        }
    }

    /**
     * Validates the critical payment DLQ event.
     */
    private void validateEvent(CriticalPaymentProcessingDlqEventDto dlqEvent, String correlationId) {
        if (dlqEvent.getPaymentId() == null || dlqEvent.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required for critical payment DLQ events");
        }

        if (dlqEvent.getFailureType() == null || dlqEvent.getFailureType().trim().isEmpty()) {
            throw new IllegalArgumentException("Failure type is required");
        }

        if (dlqEvent.getOriginalTimestamp() == null) {
            throw new IllegalArgumentException("Original timestamp is required");
        }

        if (dlqEvent.getFailureTimestamp() == null) {
            throw new IllegalArgumentException("Failure timestamp is required");
        }

        // Validate business criticality
        if (dlqEvent.getBusinessCriticality() == null) {
            throw new IllegalArgumentException("Business criticality assessment is required");
        }

        // Validate SLA breach potential
        Duration timeSinceFailure = Duration.between(dlqEvent.getFailureTimestamp(), Instant.now());
        if (timeSinceFailure.toMinutes() > 15) {
            log.warn("Critical payment DLQ event is older than 15 minutes - CorrelationId: {}, Age: {} minutes",
                    correlationId, timeSinceFailure.toMinutes());
        }
    }

    /**
     * Handles processing errors with emergency escalation procedures.
     */
    private void handleProcessingError(String eventJson, String key, String correlationId,
                                     Exception error, Acknowledgment acknowledgment) {
        log.error("CRITICAL: Failed to process critical payment DLQ event - CorrelationId: {}, Key: {}, Error: {}",
                 correlationId, key, error.getMessage(), error);

        dlqEventsFailedCounter.increment();
        metricsCollector.recordEventProcessed(TOPIC_NAME, "error");

        // Emergency notification for processing failures
        try {
            dlqService.notifyEmergencyTeamOfProcessingFailure(correlationId, error);

            // Create high-priority ticket
            dlqService.createHighPriorityIncidentTicket(correlationId, eventJson, error);

        } catch (Exception notificationError) {
            log.error("CRITICAL: Failed to notify emergency team - CorrelationId: {}, Error: {}",
                     correlationId, notificationError.getMessage());
        }

        // Acknowledge to prevent infinite retry loops in critical systems
        acknowledgment.acknowledge();
    }

    @Override
    protected String getConsumerName() {
        return "CriticalPaymentProcessingDlqConsumer";
    }

    @Override
    protected String getTopicName() {
        return TOPIC_NAME;
    }
}