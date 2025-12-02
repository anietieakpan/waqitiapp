package com.waqiti.compliance.kafka;

import com.waqiti.common.events.PolicyComplianceEvent;
import com.waqiti.compliance.domain.PolicyViolation;
import com.waqiti.compliance.repository.PolicyViolationRepository;
import com.waqiti.compliance.service.PolicyComplianceService;
import com.waqiti.compliance.service.ComplianceService;
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
public class PolicyComplianceConsumer {

    private final PolicyViolationRepository violationRepository;
    private final PolicyComplianceService policyComplianceService;
    private final ComplianceService complianceService;
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
    private Counter violationsCounter;
    private Counter criticalViolationsCounter;
    private Counter policyBreachesCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("policy_compliance_processed_total")
            .description("Total number of successfully processed policy compliance events")
            .register(meterRegistry);
        errorCounter = Counter.builder("policy_compliance_errors_total")
            .description("Total number of policy compliance processing errors")
            .register(meterRegistry);
        violationsCounter = Counter.builder("policy_violations_total")
            .description("Total number of policy violations detected")
            .register(meterRegistry);
        criticalViolationsCounter = Counter.builder("policy_violations_critical_total")
            .description("Total number of critical policy violations")
            .register(meterRegistry);
        policyBreachesCounter = Counter.builder("policy_breaches_total")
            .description("Total number of policy breaches requiring remediation")
            .register(meterRegistry);
        processingTimer = Timer.builder("policy_compliance_processing_duration")
            .description("Time taken to process policy compliance events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"policy-compliance-events", "policy-violation-events", "compliance-critical-violations",
                 "internal-policy-breaches", "governance-violations"},
        groupId = "compliance-policy-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 12000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "policy-compliance", fallbackMethod = "handlePolicyComplianceFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 12000))
    public void handlePolicyComplianceEvent(
            @Payload PolicyComplianceEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("policy-%s-p%d-o%d", event.getEntityId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getEntityId(), event.getPolicyType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing policy compliance event: entityId={}, policyType={}, severity={}, violationType={}",
                event.getEntityId(), event.getPolicyType(), event.getSeverity(), event.getViolationType());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case POLICY_VIOLATION_DETECTED:
                    processPolicyViolationDetected(event, correlationId);
                    break;

                case COMPLIANCE_CHECK_FAILED:
                    processComplianceCheckFailed(event, correlationId);
                    break;

                case POLICY_BREACH_CONFIRMED:
                    processPolicyBreachConfirmed(event, correlationId);
                    break;

                case GOVERNANCE_VIOLATION:
                    processGovernanceViolation(event, correlationId);
                    break;

                case RISK_THRESHOLD_EXCEEDED:
                    processRiskThresholdExceeded(event, correlationId);
                    break;

                case APPROVAL_LIMIT_EXCEEDED:
                    processApprovalLimitExceeded(event, correlationId);
                    break;

                case UNAUTHORIZED_ACCESS:
                    processUnauthorizedAccess(event, correlationId);
                    break;

                case DATA_RETENTION_VIOLATION:
                    processDataRetentionViolation(event, correlationId);
                    break;

                case SEGREGATION_VIOLATION:
                    processSegregationViolation(event, correlationId);
                    break;

                case REMEDIATION_REQUIRED:
                    processRemediationRequired(event, correlationId);
                    break;

                default:
                    log.warn("Unknown policy compliance event type: {}", event.getEventType());
                    break;
            }

            // Check for regulatory impact
            if (hasRegulatoryImpact(event)) {
                createRegulatoryIncident(event, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("POLICY_COMPLIANCE_EVENT_PROCESSED", event.getEntityId(),
                Map.of("policyType", event.getPolicyType(), "violationType", event.getViolationType(),
                    "severity", event.getSeverity(), "correlationId", correlationId,
                    "eventType", event.getEventType(), "timestamp", Instant.now()));

            successCounter.increment();
            if ("VIOLATION".equals(event.getEventType().toString())) {
                violationsCounter.increment();
                if ("CRITICAL".equals(event.getSeverity())) {
                    criticalViolationsCounter.increment();
                }
            }
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process policy compliance event: {}", e.getMessage(), e);

            // Escalate critical policy violations immediately
            if ("CRITICAL".equals(event.getSeverity()) || isCriticalPolicyType(event.getPolicyType())) {
                escalatePolicyViolation(event, correlationId, e);
            }

            // Send fallback event
            kafkaTemplate.send("policy-compliance-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "requiresEscalation", "CRITICAL".equals(event.getSeverity())));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handlePolicyComplianceFallback(
            PolicyComplianceEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("policy-fallback-%s-p%d-o%d", event.getEntityId(), partition, offset);

        log.error("Circuit breaker fallback triggered for policy compliance: entityId={}, error={}",
            event.getEntityId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("policy-compliance-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for policy compliance failures
        try {
            notificationService.sendCriticalAlert(
                "Critical Policy Compliance System Failure",
                String.format("Policy compliance processing failed for entity %s (%s): %s",
                    event.getEntityId(), event.getPolicyType(), ex.getMessage()),
                "CRITICAL"
            );

            // Mandatory escalation for policy compliance failures
            escalatePolicyViolation(event, correlationId, ex);

        } catch (Exception notificationEx) {
            log.error("Failed to send critical policy compliance alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltPolicyComplianceEvent(
            @Payload PolicyComplianceEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-policy-%s-%d", event.getEntityId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Policy compliance permanently failed: entityId={}, topic={}, error={}",
            event.getEntityId(), topic, exceptionMessage);

        // Save to dead letter store for compliance investigation
        auditService.logComplianceEvent("POLICY_COMPLIANCE_DLT_EVENT", event.getEntityId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "policyType", event.getPolicyType(), "correlationId", correlationId,
                "requiresRegulatoryNotification", true, "priority", "EMERGENCY", "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Emergency: Policy Compliance in DLT",
                String.format("CRITICAL: Policy compliance sent to DLT - Entity: %s, Policy: %s, Error: %s",
                    event.getEntityId(), event.getPolicyType(), exceptionMessage),
                Map.of("entityId", event.getEntityId(), "policyType", event.getPolicyType(),
                    "topic", topic, "correlationId", correlationId,
                    "severity", "EMERGENCY", "requiresImmediateAction", true)
            );

            // Mandatory executive escalation for DLT events
            escalatePolicyViolation(event, correlationId, new Exception(exceptionMessage));

        } catch (Exception ex) {
            log.error("Failed to send emergency policy compliance DLT alert: {}", ex.getMessage());
        }
    }

    private void processPolicyViolationDetected(PolicyComplianceEvent event, String correlationId) {
        PolicyViolation violation = PolicyViolation.builder()
            .violationId(UUID.randomUUID().toString())
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .policyType(event.getPolicyType())
            .violationType(event.getViolationType())
            .severity(event.getSeverity())
            .status("DETECTED")
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .description(event.getDescription())
            .evidence(event.getEvidence())
            .build();

        violationRepository.save(violation);

        violationsCounter.increment();
        if ("CRITICAL".equals(event.getSeverity())) {
            criticalViolationsCounter.increment();
        }

        // Auto-assign for investigation
        String investigator = policyComplianceService.assignInvestigator(event.getSeverity(), event.getPolicyType());
        violation.setAssignedInvestigator(investigator);
        violationRepository.save(violation);

        // Immediate actions for critical violations
        if ("CRITICAL".equals(event.getSeverity())) {
            policyComplianceService.initiateImmediateRemediation(event.getEntityId(), event.getPolicyType(), correlationId);

            notificationService.sendCriticalAlert(
                "Critical Policy Violation Detected",
                String.format("Critical policy violation: Entity %s, Policy: %s, Type: %s",
                    event.getEntityId(), event.getPolicyType(), event.getViolationType()),
                "HIGH"
            );
        }

        // Create investigation case
        kafkaTemplate.send("policy-investigation-required", Map.of(
            "violationId", violation.getViolationId(),
            "entityId", event.getEntityId(),
            "policyType", event.getPolicyType(),
            "severity", event.getSeverity(),
            "investigator", investigator,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.warn("Policy violation detected: violationId={}, entityId={}, policy={}, severity={}",
            violation.getViolationId(), event.getEntityId(), event.getPolicyType(), event.getSeverity());
    }

    private void processComplianceCheckFailed(PolicyComplianceEvent event, String correlationId) {
        // Log compliance check failure
        auditService.logComplianceEvent("COMPLIANCE_CHECK_FAILED", event.getEntityId(),
            Map.of("policyType", event.getPolicyType(), "failureReason", event.getFailureReason(),
                "correlationId", correlationId, "timestamp", Instant.now()));

        // Determine if manual intervention is required
        if (requiresManualIntervention(event.getPolicyType(), event.getFailureReason())) {
            kafkaTemplate.send("manual-compliance-review", Map.of(
                "entityId", event.getEntityId(),
                "policyType", event.getPolicyType(),
                "failureReason", event.getFailureReason(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Retry compliance check if appropriate
        if (isRetryable(event.getFailureReason())) {
            policyComplianceService.scheduleComplianceCheckRetry(event.getEntityId(), event.getPolicyType(), correlationId);
        }

        log.error("Compliance check failed: entityId={}, policy={}, reason={}",
            event.getEntityId(), event.getPolicyType(), event.getFailureReason());
    }

    private void processPolicyBreachConfirmed(PolicyComplianceEvent event, String correlationId) {
        PolicyViolation violation = violationRepository.findByEntityIdAndPolicyType(
                event.getEntityId(), event.getPolicyType())
            .orElseThrow(() -> new RuntimeException("Policy violation not found"));

        violation.setStatus("CONFIRMED_BREACH");
        violation.setConfirmedAt(LocalDateTime.now());
        violation.setConfirmedBy(event.getConfirmedBy());
        violation.setBreachDetails(event.getBreachDetails());
        violationRepository.save(violation);

        policyBreachesCounter.increment();

        // Execute breach remediation protocol
        policyComplianceService.executeBreach Remediation(event.getEntityId(), event.getPolicyType(),
            event.getBreachDetails(), correlationId);

        // Notify regulatory bodies if required
        if (requiresRegulatoryNotification(event.getPolicyType())) {
            notifyRegulatoryAuthorities(event, correlationId);
        }

        // Executive escalation for confirmed breaches
        escalatePolicyBreach(event, correlationId);

        notificationService.sendEmergencyAlert(
            "Policy Breach Confirmed",
            String.format("CRITICAL: Policy breach confirmed - Entity: %s, Policy: %s",
                event.getEntityId(), event.getPolicyType()),
            Map.of("violationId", violation.getViolationId(), "severity", "EMERGENCY")
        );

        log.error("Policy breach confirmed: violationId={}, entityId={}, policy={}",
            violation.getViolationId(), event.getEntityId(), event.getPolicyType());
    }

    private void processGovernanceViolation(PolicyComplianceEvent event, String correlationId) {
        // Create governance incident
        complianceService.createGovernanceIncident(
            event.getPolicyType(),
            event.getDescription(),
            event.getSeverity(),
            correlationId
        );

        // Board notification for critical governance violations
        if ("CRITICAL".equals(event.getSeverity())) {
            notificationService.sendBoardNotification(
                "Critical Governance Violation",
                String.format("Critical governance violation detected: %s - %s",
                    event.getPolicyType(), event.getDescription()),
                correlationId
            );
        }

        log.error("Governance violation detected: entityId={}, policy={}, description={}",
            event.getEntityId(), event.getPolicyType(), event.getDescription());
    }

    private void processRiskThresholdExceeded(PolicyComplianceEvent event, String correlationId) {
        // Calculate risk exposure
        double riskExposure = policyComplianceService.calculateRiskExposure(event.getEntityId(), event.getRiskMetrics());

        // Immediate risk mitigation if exposure is excessive
        if (riskExposure > policyComplianceService.getCriticalRiskThreshold()) {
            policyComplianceService.executeRiskMitigation(event.getEntityId(), correlationId);

            notificationService.sendCriticalAlert(
                "Critical Risk Threshold Exceeded",
                String.format("Critical risk exposure detected: Entity %s, Exposure: %.2f%%",
                    event.getEntityId(), riskExposure),
                "HIGH"
            );
        }

        // Update risk monitoring
        policyComplianceService.updateRiskMonitoring(event.getEntityId(), riskExposure, correlationId);

        log.warn("Risk threshold exceeded: entityId={}, exposure={}", event.getEntityId(), riskExposure);
    }

    private void processApprovalLimitExceeded(PolicyComplianceEvent event, String correlationId) {
        // Block transaction if auto-approval limit exceeded
        if (event.getAmount().compareTo(policyComplianceService.getAutoApprovalLimit()) > 0) {
            policyComplianceService.blockTransactionForApproval(event.getTransactionId(), correlationId);
        }

        // Route to appropriate approver
        String approver = policyComplianceService.routeForApproval(event.getAmount(), event.getEntityType());

        // Send approval notification
        notificationService.sendApprovalRequest(approver,
            "Approval Required - Limit Exceeded",
            String.format("Transaction requires approval: %s %s for entity %s",
                event.getAmount(), event.getCurrency(), event.getEntityId()),
            correlationId);

        log.info("Approval limit exceeded: entityId={}, amount={}, approver={}",
            event.getEntityId(), event.getAmount(), approver);
    }

    private void processUnauthorizedAccess(PolicyComplianceEvent event, String correlationId) {
        // Immediate security response
        policyComplianceService.executeSecurityResponse(event.getEntityId(), event.getAccessDetails(), correlationId);

        // Create security incident
        complianceService.createSecurityIncident(
            "UNAUTHORIZED_ACCESS",
            event.getDescription(),
            "HIGH",
            correlationId
        );

        // Lock account if required
        if (event.getAccessDetails().isSuspiciousActivity()) {
            policyComplianceService.lockAccount(event.getEntityId(), "UNAUTHORIZED_ACCESS", correlationId);
        }

        notificationService.sendSecurityAlert(
            "Unauthorized Access Detected",
            String.format("Unauthorized access attempt: Entity %s, Details: %s",
                event.getEntityId(), event.getAccessDetails()),
            "HIGH"
        );

        log.error("Unauthorized access detected: entityId={}, details={}",
            event.getEntityId(), event.getAccessDetails());
    }

    private void processDataRetentionViolation(PolicyComplianceEvent event, String correlationId) {
        // Initiate data remediation
        policyComplianceService.initiateDataRemediation(event.getEntityId(), event.getDataDetails(), correlationId);

        // Schedule data review
        policyComplianceService.scheduleDataRetentionReview(event.getEntityId(), correlationId);

        // Create data governance incident
        complianceService.createDataGovernanceIncident(
            "DATA_RETENTION_VIOLATION",
            event.getDescription(),
            "MEDIUM",
            correlationId
        );

        log.warn("Data retention violation: entityId={}, details={}",
            event.getEntityId(), event.getDataDetails());
    }

    private void processSegregationViolation(PolicyComplianceEvent event, String correlationId) {
        // Immediate segregation enforcement
        policyComplianceService.enforceSegregation(event.getEntityId(), event.getSegregationDetails(), correlationId);

        // Executive notification for segregation violations
        notificationService.sendExecutiveAlert(
            "Segregation Violation Detected",
            String.format("Segregation policy violation: Entity %s, Details: %s",
                event.getEntityId(), event.getSegregationDetails()),
            "HIGH"
        );

        log.error("Segregation violation: entityId={}, details={}",
            event.getEntityId(), event.getSegregationDetails());
    }

    private void processRemediationRequired(PolicyComplianceEvent event, String correlationId) {
        // Create remediation plan
        policyComplianceService.createRemediationPlan(event.getEntityId(), event.getPolicyType(),
            event.getRemediationDetails(), correlationId);

        // Assign remediation owner
        String remediationOwner = policyComplianceService.assignRemediationOwner(event.getSeverity());

        // Set remediation deadline
        LocalDateTime deadline = calculateRemediationDeadline(event.getSeverity());

        // Send remediation assignment
        notificationService.sendAssignmentNotification(remediationOwner,
            "Remediation Assignment",
            String.format("Remediation required: Entity %s, Policy: %s, Deadline: %s",
                event.getEntityId(), event.getPolicyType(), deadline),
            correlationId);

        log.info("Remediation required: entityId={}, policy={}, owner={}, deadline={}",
            event.getEntityId(), event.getPolicyType(), remediationOwner, deadline);
    }

    // Helper methods

    private boolean hasRegulatoryImpact(PolicyComplianceEvent event) {
        return "FINANCIAL_REGULATION".equals(event.getPolicyType()) ||
               "DATA_PROTECTION".equals(event.getPolicyType()) ||
               "ANTI_MONEY_LAUNDERING".equals(event.getPolicyType()) ||
               "CONSUMER_PROTECTION".equals(event.getPolicyType());
    }

    private boolean isCriticalPolicyType(String policyType) {
        return "ANTI_MONEY_LAUNDERING".equals(policyType) ||
               "SANCTIONS".equals(policyType) ||
               "FRAUD_PREVENTION".equals(policyType) ||
               "DATA_PROTECTION".equals(policyType);
    }

    private boolean requiresManualIntervention(String policyType, String failureReason) {
        return failureReason.contains("SYSTEM_ERROR") ||
               failureReason.contains("DATA_INCONSISTENCY") ||
               failureReason.contains("EXTERNAL_SERVICE_FAILURE");
    }

    private boolean isRetryable(String failureReason) {
        return failureReason.contains("TIMEOUT") ||
               failureReason.contains("NETWORK_ERROR") ||
               failureReason.contains("SERVICE_UNAVAILABLE");
    }

    private boolean requiresRegulatoryNotification(String policyType) {
        return "ANTI_MONEY_LAUNDERING".equals(policyType) ||
               "SANCTIONS".equals(policyType) ||
               "CONSUMER_PROTECTION".equals(policyType) ||
               "DATA_PROTECTION".equals(policyType);
    }

    private LocalDateTime calculateRemediationDeadline(String severity) {
        switch (severity) {
            case "CRITICAL":
                return LocalDateTime.now().plusHours(24);
            case "HIGH":
                return LocalDateTime.now().plusDays(3);
            case "MEDIUM":
                return LocalDateTime.now().plusDays(7);
            default:
                return LocalDateTime.now().plusDays(14);
        }
    }

    private void createRegulatoryIncident(PolicyComplianceEvent event, String correlationId) {
        complianceService.createRegulatoryIncident(
            "POLICY_VIOLATION",
            String.format("Policy violation with regulatory impact: %s - %s",
                event.getPolicyType(), event.getDescription()),
            event.getSeverity(),
            correlationId
        );
    }

    private void escalatePolicyViolation(PolicyComplianceEvent event, String correlationId, Exception error) {
        try {
            notificationService.sendExecutiveEscalation(
                "Critical Policy Compliance Issue",
                String.format("URGENT: Policy compliance issue requiring executive attention.\n" +
                    "Entity ID: %s\n" +
                    "Policy Type: %s\n" +
                    "Violation Type: %s\n" +
                    "Severity: %s\n" +
                    "Error: %s\n" +
                    "Correlation ID: %s\n" +
                    "Time: %s",
                    event.getEntityId(), event.getPolicyType(), event.getViolationType(),
                    event.getSeverity(), error.getMessage(), correlationId, Instant.now()),
                Map.of(
                    "priority", "EMERGENCY",
                    "category", "POLICY_COMPLIANCE",
                    "entityId", event.getEntityId(),
                    "correlationId", correlationId,
                    "requiresImmediateAction", true
                )
            );
        } catch (Exception ex) {
            log.error("Failed to escalate policy violation to executive team: {}", ex.getMessage());
        }
    }

    private void escalatePolicyBreach(PolicyComplianceEvent event, String correlationId) {
        try {
            notificationService.sendExecutiveEscalation(
                "Confirmed Policy Breach",
                String.format("EMERGENCY: Confirmed policy breach requiring immediate action.\n" +
                    "Entity ID: %s\n" +
                    "Policy Type: %s\n" +
                    "Breach Details: %s\n" +
                    "Confirmed By: %s\n" +
                    "Correlation ID: %s\n" +
                    "Time: %s",
                    event.getEntityId(), event.getPolicyType(), event.getBreachDetails(),
                    event.getConfirmedBy(), correlationId, Instant.now()),
                Map.of(
                    "priority", "EMERGENCY",
                    "category", "POLICY_BREACH",
                    "entityId", event.getEntityId(),
                    "correlationId", correlationId,
                    "requiresImmediateAction", true
                )
            );
        } catch (Exception ex) {
            log.error("Failed to escalate policy breach to executive team: {}", ex.getMessage());
        }
    }

    private void notifyRegulatoryAuthorities(PolicyComplianceEvent event, String correlationId) {
        try {
            policyComplianceService.notifyRegulatoryAuthorities(
                event.getPolicyType(),
                event.getBreachDetails(),
                correlationId
            );
        } catch (Exception ex) {
            log.error("Failed to notify regulatory authorities: {}", ex.getMessage());
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