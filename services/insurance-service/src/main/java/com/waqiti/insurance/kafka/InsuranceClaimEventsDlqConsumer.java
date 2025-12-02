package com.waqiti.insurance.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.insurance.service.InsuranceClaimService;
import com.waqiti.insurance.service.InsuranceComplianceService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class InsuranceClaimEventsDlqConsumer extends BaseDlqConsumer {

    private final InsuranceClaimService insuranceClaimService;
    private final InsuranceComplianceService insuranceComplianceService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public InsuranceClaimEventsDlqConsumer(InsuranceClaimService insuranceClaimService,
                                           InsuranceComplianceService insuranceComplianceService,
                                           MeterRegistry meterRegistry) {
        super("insurance-claim-events-dlq");
        this.insuranceClaimService = insuranceClaimService;
        this.insuranceComplianceService = insuranceComplianceService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("insurance_claim_events_dlq_processed_total")
                .description("Total insurance claim events DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("insurance_claim_events_dlq_errors_total")
                .description("Total insurance claim events DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("insurance_claim_events_dlq_duration")
                .description("Insurance claim events DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "insurance-claim-events-dlq",
        groupId = "insurance-service-insurance-claim-events-dlq-group",
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
        include = {Exception.class},
        traversingCauses = "true",
        retryTopicSuffix = "-retry",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "insurance-claim-events-dlq", fallbackMethod = "handleInsuranceClaimEventsDlqFallback")
    public void handleInsuranceClaimEventsDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-Claim-Id", required = false) String claimId,
            @Header(value = "X-Policy-Id", required = false) String policyId,
            @Header(value = "X-Claim-Type", required = false) String claimType,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Insurance claim event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing insurance claim DLQ event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, claimId={}, policyId={}, claimType={}",
                     topic, partition, offset, record.key(), correlationId, claimId, policyId, claimType);

            String claimData = record.value();
            validateInsuranceClaimData(claimData, eventId);

            // Process insurance claim DLQ with regulatory compliance
            InsuranceClaimRecoveryResult result = insuranceClaimService.processInsuranceClaimEventsDlq(
                claimData,
                record.key(),
                correlationId,
                claimId,
                policyId,
                claimType,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Handle recovery result based on claim complexity
            if (result.isProcessed()) {
                handleSuccessfulProcessing(result, correlationId);
            } else if (result.isFraudulent()) {
                handleFraudulentClaim(result, eventId, correlationId);
            } else if (result.requiresAdjusterReview()) {
                handleAdjusterReviewRequired(result, correlationId);
            } else if (result.isRegulatoryViolation()) {
                handleRegulatoryViolation(result, eventId, correlationId);
            } else {
                handleFailedProcessing(result, eventId, correlationId);
            }

            // Update insurance compliance metrics
            updateInsuranceComplianceMetrics(result, correlationId);

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed insurance claim DLQ: eventId={}, claimId={}, " +
                    "correlationId={}, processingStatus={}",
                    eventId, result.getClaimId(), correlationId, result.getProcessingStatus());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in insurance claim DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();
        } catch (InsuranceComplianceException e) {
            errorCounter.increment();
            log.error("Insurance compliance violation in claim DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleInsuranceComplianceException(record, e, correlationId);
            throw e; // Compliance violations must be retried
        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error in insurance claim DLQ: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in insurance claim DLQ: eventId={}, correlationId={}",
                     eventId, correlationId, e);
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
        log.error("Insurance claim event sent to DLT - CUSTOMER CLAIM AT RISK: " +
                 "topic={}, originalPartition={}, originalOffset={}, correlationId={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, exceptionMessage);

        // Execute customer claim protection protocol
        executeCustomerClaimProtectionProtocol(record, topic, exceptionMessage, correlationId);

        // Store for insurance operations review
        storeForInsuranceOperationsReview(record, topic, exceptionMessage, correlationId);

        // Send insurance operations alert
        sendInsuranceOperationsAlert(record, topic, exceptionMessage, correlationId);

        // Create customer protection incident
        createCustomerProtectionIncident(record, topic, exceptionMessage, correlationId);

        // Update DLT metrics
        Counter.builder("insurance_claim_events_dlt_events_total")
                .description("Total insurance claim events sent to DLT")
                .tag("topic", topic)
                .tag("customer_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    public void handleInsuranceClaimEventsDlqFallback(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String claimId, String policyId, String claimType,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for insurance claim DLQ: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        // Store in insurance operations priority queue
        storeInInsuranceOperationsPriorityQueue(record, correlationId);

        // Send insurance operations team alert
        sendInsuranceOperationsTeamAlert(correlationId, ex);

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("insurance_claim_events_dlq_circuit_breaker_activations_total")
                .tag("customer_impact", "medium")
                .register(meterRegistry)
                .increment();
    }

    private void validateInsuranceClaimData(String claimData, String eventId) {
        if (claimData == null || claimData.trim().isEmpty()) {
            throw new ValidationException("Insurance claim data is null or empty for eventId: " + eventId);
        }

        if (!claimData.contains("claimId")) {
            throw new ValidationException("Insurance claim data missing claimId for eventId: " + eventId);
        }

        if (!claimData.contains("policyId")) {
            throw new ValidationException("Insurance claim data missing policyId for eventId: " + eventId);
        }

        if (!claimData.contains("claimAmount")) {
            throw new ValidationException("Insurance claim data missing claimAmount for eventId: " + eventId);
        }

        // Validate insurance regulatory compliance
        validateInsuranceRegulatory(claimData, eventId);
    }

    private void validateInsuranceRegulatory(String claimData, String eventId) {
        try {
            JsonNode data = objectMapper.readTree(claimData);
            String claimType = data.get("claimType").asText();
            BigDecimal claimAmount = new BigDecimal(data.get("claimAmount").asText());

            // Validate claim amount
            if (claimAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Invalid claim amount (must be positive) for eventId: " + eventId);
            }

            // Validate large claim reporting requirements
            if (claimAmount.compareTo(new BigDecimal("100000")) > 0) {
                if (!data.has("regulatoryNotification")) {
                    log.warn("Large claim missing regulatory notification: claimAmount={}, eventId={}",
                            claimAmount, eventId);
                }
            }

            // Validate claim documentation
            if (!data.has("documentation")) {
                throw new InsuranceComplianceException("Claim missing required documentation for eventId: " + eventId);
            }

            // Validate fraud indicators
            if (data.has("fraudScore")) {
                double fraudScore = data.get("fraudScore").asDouble();
                if (fraudScore > 0.8) {
                    log.warn("High fraud score detected: {} for eventId: {}", fraudScore, eventId);
                }
            }

            // Validate state insurance requirements
            if (!data.has("stateRequirements")) {
                log.warn("Missing state insurance requirements validation: eventId={}", eventId);
            }

        } catch (Exception e) {
            throw new ValidationException("Failed to validate insurance regulatory requirements: " + e.getMessage());
        }
    }

    private void handleSuccessfulProcessing(InsuranceClaimRecoveryResult result, String correlationId) {
        log.info("Insurance claim successfully processed: claimId={}, policyId={}, claimAmount={}, correlationId={}",
                result.getClaimId(), result.getPolicyId(), result.getClaimAmount(), correlationId);

        // Update claim status
        insuranceClaimService.updateClaimStatus(
            result.getClaimId(),
            ClaimStatus.PROCESSED,
            result.getProcessingDetails(),
            correlationId
        );

        // Process claim payout if approved
        if (result.isApproved()) {
            claimPayoutService.processPayment(
                result.getClaimId(),
                result.getPolicyHolderId(),
                result.getApprovedAmount(),
                result.getPaymentMethod(),
                correlationId
            );
        }

        // Send claim resolution notification
        notificationService.sendClaimResolutionNotification(
            result.getPolicyHolderId(),
            result.getClaimId(),
            result.getResolutionType(),
            result.getApprovedAmount(),
            correlationId
        );

        // Update insurance metrics
        insuranceMetricsService.recordSuccessfulClaimProcessing(
            result.getClaimType(),
            result.getClaimAmount(),
            result.getProcessingTime(),
            correlationId
        );

        // Update actuarial data
        actuarialDataService.recordClaimEvent(
            result.getPolicyId(),
            result.getClaimType(),
            result.getClaimAmount(),
            result.getApprovedAmount(),
            correlationId
        );

        // File regulatory reports if required
        if (result.requiresRegulatoryReporting()) {
            insuranceRegulatoryService.fileClaimReport(
                result.getClaimId(),
                result.getClaimType(),
                result.getApprovedAmount(),
                correlationId
            );
        }
    }

    private void handleFraudulentClaim(InsuranceClaimRecoveryResult result, String eventId, String correlationId) {
        log.error("Fraudulent insurance claim detected: claimId={}, policyId={}, fraudIndicators={}, correlationId={}",
                result.getClaimId(), result.getPolicyId(), result.getFraudIndicators(), correlationId);

        // Update claim status to fraudulent
        insuranceClaimService.updateClaimStatus(
            result.getClaimId(),
            ClaimStatus.FRAUDULENT,
            String.format("Fraud detected: %s", result.getFraudIndicators()),
            correlationId
        );

        // Create fraud investigation case
        fraudInvestigationService.createCase(
            CaseType.INSURANCE_FRAUD,
            result.getClaimId(),
            result.getPolicyId(),
            result.getPolicyHolderId(),
            result.getFraudIndicators(),
            eventId,
            correlationId,
            Severity.HIGH
        );

        // Send fraud alert
        fraudAlertService.sendInsuranceFraudAlert(
            FraudAlertType.CLAIM_FRAUD,
            String.format("Fraudulent claim detected: %s", result.getFraudIndicators()),
            Map.of(
                "claimId", result.getClaimId(),
                "policyId", result.getPolicyId(),
                "policyHolderId", result.getPolicyHolderId(),
                "fraudIndicators", String.join(",", result.getFraudIndicators()),
                "correlationId", correlationId,
                "action", "Claim denied, investigation initiated"
            )
        );

        // Flag policy holder for review
        policyHolderReviewService.flagForFraudReview(
            result.getPolicyHolderId(),
            result.getClaimId(),
            result.getFraudIndicators(),
            correlationId
        );

        // File SIU report
        siuReportingService.fileFraudReport(
            result.getClaimId(),
            result.getPolicyId(),
            result.getFraudIndicators(),
            correlationId
        );

        // Potentially void policy if severe fraud
        if (result.isSevereFraud()) {
            policyVoidingService.initiateVoidingProcess(
                result.getPolicyId(),
                VoidReason.FRAUDULENT_CLAIM,
                correlationId
            );
        }
    }

    private void handleAdjusterReviewRequired(InsuranceClaimRecoveryResult result, String correlationId) {
        log.info("Insurance claim requires adjuster review: claimId={}, policyId={}, reason={}, correlationId={}",
                result.getClaimId(), result.getPolicyId(), result.getReviewReason(), correlationId);

        // Update claim status to pending adjuster review
        insuranceClaimService.updateClaimStatus(
            result.getClaimId(),
            ClaimStatus.PENDING_ADJUSTER_REVIEW,
            result.getReviewReason(),
            correlationId
        );

        // Assign to claims adjuster
        claimsAdjusterService.assignClaim(
            result.getClaimId(),
            result.getClaimComplexity(),
            result.getSpecialtyRequired(),
            correlationId
        );

        // Queue for adjuster review
        adjusterReviewQueue.add(
            AdjusterReviewRequest.builder()
                .claimId(result.getClaimId())
                .policyId(result.getPolicyId())
                .policyHolderId(result.getPolicyHolderId())
                .claimType(result.getClaimType())
                .claimAmount(result.getClaimAmount())
                .reviewReason(result.getReviewReason())
                .correlationId(correlationId)
                .priority(determineAdjusterPriority(result))
                .specialtyRequired(result.getSpecialtyRequired())
                .deadline(calculateAdjusterDeadline(result))
                .build()
        );

        // Send adjuster review notification
        notificationService.sendAdjusterReviewNotification(
            result.getPolicyHolderId(),
            result.getClaimId(),
            result.getReviewReason(),
            correlationId
        );

        // Update adjuster workload metrics
        adjusterWorkloadMetricsService.recordAssignment(
            result.getClaimComplexity(),
            result.getSpecialtyRequired(),
            correlationId
        );
    }

    private void handleRegulatoryViolation(InsuranceClaimRecoveryResult result, String eventId, String correlationId) {
        log.error("Insurance regulatory violation: claimId={}, policyId={}, violation={}, correlationId={}",
                result.getClaimId(), result.getPolicyId(), result.getViolationType(), correlationId);

        // Create regulatory violation incident
        insuranceRegulatoryViolationIncidentService.createIncident(
            IncidentType.INSURANCE_REGULATORY_VIOLATION,
            result.getClaimId(),
            result.getPolicyId(),
            result.getViolationType(),
            eventId,
            correlationId,
            Severity.CRITICAL
        );

        // Send immediate regulatory team alert
        insuranceRegulatoryAlertService.sendCriticalAlert(
            InsuranceRegulatoryAlertType.CLAIM_COMPLIANCE_VIOLATION,
            String.format("Insurance regulatory violation: %s", result.getViolationType()),
            Map.of(
                "claimId", result.getClaimId(),
                "policyId", result.getPolicyId(),
                "violationType", result.getViolationType().toString(),
                "correlationId", correlationId,
                "action", "Claim processing halted, regulatory review required"
            )
        );

        // Halt claim processing
        insuranceClaimService.haltClaimProcessing(
            result.getClaimId(),
            HaltReason.REGULATORY_VIOLATION,
            correlationId
        );

        // File regulatory breach notification if required
        if (result.requiresRegulatoryBreach()) {
            insuranceRegulatoryBreachService.fileBreachNotification(
                result.getClaimId(),
                result.getViolationType(),
                correlationId
            );
        }

        // Create compliance review case
        complianceReviewService.createCase(
            CaseType.INSURANCE_REGULATORY_VIOLATION,
            result.getClaimId(),
            result.getViolationType(),
            correlationId
        );
    }

    private void handleFailedProcessing(InsuranceClaimRecoveryResult result, String eventId, String correlationId) {
        log.error("Insurance claim processing failed: claimId={}, policyId={}, reason={}, correlationId={}",
                result.getClaimId(), result.getPolicyId(), result.getFailureReason(), correlationId);

        // Update claim status to failed
        insuranceClaimService.updateClaimStatus(
            result.getClaimId(),
            ClaimStatus.PROCESSING_FAILED,
            result.getFailureReason(),
            correlationId
        );

        // Escalate to insurance operations manager
        insuranceOperationsManagerEscalationService.escalateClaimFailure(
            result.getClaimId(),
            result.getPolicyId(),
            result.getClaimType(),
            result.getFailureReason(),
            eventId,
            correlationId,
            EscalationPriority.HIGH
        );

        // Create customer service ticket
        customerServiceTicketService.createTicket(
            TicketType.INSURANCE_CLAIM_PROCESSING_FAILURE,
            result.getPolicyHolderId(),
            String.format("Claim processing failed: %s", result.getFailureReason()),
            Priority.HIGH,
            correlationId
        );

        // Send failure notification to policy holder
        notificationService.sendClaimProcessingFailureNotification(
            result.getPolicyHolderId(),
            result.getClaimId(),
            result.getFailureReason(),
            correlationId
        );

        // Check claim deadline compliance
        if (result.isNearingClaimDeadline()) {
            claimDeadlineMonitoringService.flagNearingDeadline(
                result.getClaimId(),
                result.getClaimDeadline(),
                correlationId
            );
        }
    }

    private void updateInsuranceComplianceMetrics(InsuranceClaimRecoveryResult result, String correlationId) {
        // Record claim processing metrics
        insuranceClaimMetricsService.recordProcessing(
            result.getClaimType(),
            result.getProcessingStatus(),
            result.getClaimAmount(),
            result.getProcessingTime(),
            correlationId
        );

        // Update claim approval rate metrics
        claimApprovalRateMetricsService.recordDecision(
            result.getClaimType(),
            result.getProcessingStatus(),
            result.getClaimAmount(),
            result.getApprovedAmount(),
            correlationId
        );

        // Update fraud detection metrics
        if (result.hasFraudAnalysis()) {
            fraudDetectionMetricsService.recordFraudAnalysis(
                result.getClaimType(),
                result.getFraudScore(),
                result.isFraudulent(),
                correlationId
            );
        }

        // Update regulatory compliance metrics
        insuranceRegulatoryComplianceMetricsService.recordCompliance(
            result.getClaimType(),
            result.hasRegulatoryViolation(),
            correlationId
        );
    }

    private void handleInsuranceComplianceException(ConsumerRecord<String, String> record,
                                                    InsuranceComplianceException e, String correlationId) {
        // Create insurance compliance violation record
        insuranceComplianceViolationRepository.save(
            InsuranceComplianceViolation.builder()
                .claimId(extractClaimId(record.value()))
                .policyId(extractPolicyId(record.value()))
                .violationType(ViolationType.INSURANCE_REGULATORY_COMPLIANCE)
                .description(e.getMessage())
                .correlationId(correlationId)
                .severity(Severity.CRITICAL)
                .timestamp(Instant.now())
                .requiresRegulatoryReporting(true)
                .source("insurance-claim-events-dlq")
                .claimType(extractClaimType(record.value()))
                .build()
        );

        // Send immediate compliance team alert
        insuranceComplianceAlertService.sendCriticalAlert(
            InsuranceComplianceAlertType.CLAIM_COMPLIANCE_VIOLATION,
            e.getMessage(),
            correlationId
        );
    }

    private Priority determineAdjusterPriority(InsuranceClaimRecoveryResult result) {
        if (result.getClaimAmount().compareTo(new BigDecimal("50000")) > 0) {
            return Priority.HIGH;
        } else if (result.getClaimComplexity() == ClaimComplexity.COMPLEX) {
            return Priority.MEDIUM;
        } else {
            return Priority.LOW;
        }
    }

    private Instant calculateAdjusterDeadline(InsuranceClaimRecoveryResult result) {
        int days = switch (result.getClaimComplexity()) {
            case SIMPLE -> 5;
            case STANDARD -> 10;
            case COMPLEX -> 20;
            case CATASTROPHIC -> 30;
        };
        return Instant.now().plus(Duration.ofDays(days));
    }

    private void executeCustomerClaimProtectionProtocol(ConsumerRecord<String, String> record,
                                                        String topic, String exceptionMessage,
                                                        String correlationId) {
        try {
            // Execute comprehensive customer claim protection protocol
            CustomerClaimProtectionResult protection = customerClaimProtectionService.execute(
                record.key(),
                record.value(),
                topic,
                exceptionMessage,
                correlationId
            );

            if (protection.isCustomerProtected()) {
                log.info("Customer claim protected: correlationId={}, protectedClaims={}",
                        correlationId, protection.getProtectedClaims());

                // Apply customer protection measures
                customerProtectionService.applyMeasures(
                    protection.getAffectedClaims(),
                    protection.getProtectionMeasures(),
                    correlationId
                );
            }
        } catch (Exception e) {
            log.error("Customer claim protection protocol failed: correlationId={}", correlationId, e);
        }
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

    private String extractClaimId(String value) {
        try {
            return objectMapper.readTree(value).get("claimId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractPolicyId(String value) {
        try {
            return objectMapper.readTree(value).get("policyId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractClaimType(String value) {
        try {
            return objectMapper.readTree(value).get("claimType").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }
}