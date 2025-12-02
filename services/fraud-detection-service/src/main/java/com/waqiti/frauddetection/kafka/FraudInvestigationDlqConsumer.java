package com.waqiti.frauddetection.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.frauddetection.service.FraudInvestigationService;
import com.waqiti.frauddetection.service.FraudValidationService;
import com.waqiti.frauddetection.repository.FraudInvestigationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.Map;

/**
 * DLQ Consumer for fraud investigation failures.
 * Handles critical fraud investigation processing errors with immediate case management escalation.
 */
@Component
@Slf4j
public class FraudInvestigationDlqConsumer extends BaseDlqConsumer {

    private final FraudInvestigationService fraudInvestigationService;
    private final FraudValidationService fraudValidationService;
    private final FraudInvestigationRepository fraudInvestigationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FraudInvestigationDlqConsumer(DlqHandler dlqHandler,
                                       AuditService auditService,
                                       NotificationService notificationService,
                                       MeterRegistry meterRegistry,
                                       FraudInvestigationService fraudInvestigationService,
                                       FraudValidationService fraudValidationService,
                                       FraudInvestigationRepository fraudInvestigationRepository,
                                       KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.fraudInvestigationService = fraudInvestigationService;
        this.fraudValidationService = fraudValidationService;
        this.fraudInvestigationRepository = fraudInvestigationRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"fraud-investigation.DLQ"},
        groupId = "fraud-investigation-dlq-consumer-group",
        containerFactory = "criticalFraudKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "fraud-investigation-dlq", fallbackMethod = "handleFraudInvestigationDlqFallback")
    public void handleFraudInvestigationDlq(@Payload Object originalMessage,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                          @Header(KafkaHeaders.OFFSET) long offset,
                                          Acknowledgment acknowledgment,
                                          @Header Map<String, Object> headers) {

        log.info("Processing fraud investigation DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String investigationId = extractInvestigationId(originalMessage);
            String caseId = extractCaseId(originalMessage);
            String customerId = extractCustomerId(originalMessage);
            String investigationType = extractInvestigationType(originalMessage);
            String priority = extractPriority(originalMessage);
            String investigationStatus = extractInvestigationStatus(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing fraud investigation DLQ: investigationId={}, caseId={}, customerId={}, type={}, priority={}, messageId={}",
                investigationId, caseId, customerId, investigationType, priority, messageId);

            // Validate fraud investigation state and integrity
            if (investigationId != null || caseId != null) {
                validateFraudInvestigationState(investigationId, caseId, messageId);
                assessFraudInvestigationIntegrity(investigationId, originalMessage, messageId);
                handleFraudInvestigationRecovery(investigationId, caseId, customerId, originalMessage, exceptionMessage, messageId);
            }

            // Generate critical investigation alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for investigation timeline impact
            assessInvestigationTimelineImpact(investigationId, investigationType, priority, originalMessage, messageId);

            // Handle case management escalation
            handleCaseManagementEscalation(investigationId, caseId, customerId, investigationType, priority, originalMessage, messageId);

            // Trigger manual investigation review
            triggerManualInvestigationReview(investigationId, caseId, customerId, investigationType, priority, messageId);

        } catch (Exception e) {
            log.error("Error in fraud investigation DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "fraud-investigation-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FRAUD_INVESTIGATION";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String investigationType = extractInvestigationType(originalMessage);
        String priority = extractPriority(originalMessage);

        // All fraud investigations are critical for case management
        if (isHighPriorityInvestigation(priority)) {
            return true;
        }

        // Critical investigation types always escalated
        if (isCriticalInvestigationType(investigationType)) {
            return true;
        }

        // Regulatory investigations are critical
        return isRegulatoryInvestigation(investigationType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String investigationId = extractInvestigationId(originalMessage);
        String caseId = extractCaseId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        String investigationType = extractInvestigationType(originalMessage);
        String priority = extractPriority(originalMessage);
        String investigationStatus = extractInvestigationStatus(originalMessage);

        try {
            // CRITICAL escalation for fraud investigation failures
            String alertTitle = String.format("FRAUD INVESTIGATION CRITICAL: Investigation Processing Failed - %s",
                investigationType != null ? investigationType : "Unknown Investigation");
            String alertMessage = String.format(
                "🔍 FRAUD INVESTIGATION SYSTEM FAILURE 🔍\n\n" +
                "A fraud investigation operation has FAILED and requires IMMEDIATE attention:\n\n" +
                "Investigation ID: %s\n" +
                "Case ID: %s\n" +
                "Customer ID: %s\n" +
                "Investigation Type: %s\n" +
                "Priority: %s\n" +
                "Investigation Status: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: Investigation failures can cause:\n" +
                "- Delayed fraud case resolution\n" +
                "- Missed investigation deadlines\n" +
                "- Regulatory compliance violations\n" +
                "- Customer financial losses\n" +
                "- Evidence preservation issues\n\n" +
                "IMMEDIATE fraud investigation team and case management escalation required.",
                investigationId != null ? investigationId : "unknown",
                caseId != null ? caseId : "unknown",
                customerId != null ? customerId : "unknown",
                investigationType != null ? investigationType : "unknown",
                priority != null ? priority : "unknown",
                investigationStatus != null ? investigationStatus : "unknown",
                exceptionMessage
            );

            // Send fraud investigation alert
            notificationService.sendFraudInvestigationAlert(alertTitle, alertMessage, "CRITICAL");

            // Send case management alert
            notificationService.sendCaseManagementAlert(
                "URGENT: Fraud Investigation Case Failed",
                alertMessage,
                "CRITICAL"
            );

            // Send fraud operations alert
            notificationService.sendFraudAlert(
                "Fraud Investigation Processing Failed",
                String.format("Fraud investigation %s failed processing for case %s. " +
                    "Review investigation procedures and case management workflow.", investigationId, caseId),
                "HIGH"
            );

            // High-priority investigation specific alerts
            if (isHighPriorityInvestigation(priority)) {
                notificationService.sendHighPriorityAlert(
                    "HIGH PRIORITY INVESTIGATION FAILED",
                    String.format("High-priority fraud investigation %s (case: %s) failed for customer %s. " +
                        "IMMEDIATE investigation team response and case escalation required.", investigationId, caseId, customerId),
                    "CRITICAL"
                );
            }

            // Regulatory investigation specific alerts
            if (isRegulatoryInvestigation(investigationType)) {
                notificationService.sendRegulatoryAlert(
                    "Regulatory Investigation Failed",
                    String.format("Regulatory fraud investigation %s failed. " +
                        "Review compliance requirements and regulatory reporting deadlines.", investigationId),
                    "HIGH"
                );
            }

            // Criminal investigation specific alerts
            if (isCriminalInvestigation(investigationType)) {
                notificationService.sendLawEnforcementAlert(
                    "Criminal Investigation Failed",
                    String.format("Criminal fraud investigation %s failed. " +
                        "Review law enforcement coordination and evidence preservation.", investigationId),
                    "HIGH"
                );
            }

            // Complex fraud investigation alerts
            if (isComplexFraudInvestigation(investigationType)) {
                notificationService.sendComplexFraudAlert(
                    "Complex Fraud Investigation Failed",
                    String.format("Complex fraud investigation %s failed. " +
                        "Review specialized investigation procedures and expert assignment.", investigationId),
                    "HIGH"
                );
            }

            // Customer service alert for customer impact
            notificationService.sendCustomerServiceAlert(
                "Customer Investigation Issue",
                String.format("Customer %s fraud investigation failed. " +
                    "Monitor customer communications and provide investigation status updates.", customerId),
                "MEDIUM"
            );

            // Compliance alert for regulatory requirements
            notificationService.sendComplianceAlert(
                "Investigation Compliance Risk",
                String.format("Fraud investigation failure may impact compliance obligations. " +
                    "Review investigation reporting requirements and deadline management."),
                "MEDIUM"
            );

            // Legal alert for case proceedings
            notificationService.sendLegalAlert(
                "Investigation Legal Impact",
                String.format("Fraud investigation %s failure may impact legal proceedings. " +
                    "Review case documentation and legal timeline requirements.", investigationId),
                "MEDIUM"
            );

            // Technology escalation for system integrity
            notificationService.sendTechnologyAlert(
                "Investigation System Alert",
                String.format("Fraud investigation system failure may indicate infrastructure issues. " +
                    "Investigation: %s, Case: %s", investigationId, caseId),
                "HIGH"
            );

        } catch (Exception e) {
            log.error("Failed to send fraud investigation DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateFraudInvestigationState(String investigationId, String caseId, String messageId) {
        try {
            if (investigationId != null) {
                var investigation = fraudInvestigationRepository.findById(investigationId);
                if (investigation.isPresent()) {
                    String status = investigation.get().getStatus();
                    String state = investigation.get().getState();

                    log.info("Fraud investigation state validation: investigationId={}, status={}, state={}, messageId={}",
                        investigationId, status, state, messageId);

                    // Check for critical investigation states
                    if ("IN_PROGRESS".equals(status) || "UNDER_REVIEW".equals(status)) {
                        log.warn("Critical fraud investigation state in DLQ: investigationId={}, status={}", investigationId, status);

                        notificationService.sendFraudInvestigationAlert(
                            "CRITICAL: Investigation State Inconsistency",
                            String.format("Fraud investigation %s in critical state %s found in DLQ. " +
                                "Immediate investigation state reconciliation required.", investigationId, status),
                            "CRITICAL"
                        );
                    }

                    // Check for time-sensitive states
                    if ("PENDING_DEADLINE".equals(state) || "URGENT_REVIEW".equals(state)) {
                        notificationService.sendCaseManagementAlert(
                            "Time-Sensitive Investigation Failed",
                            String.format("Investigation %s with time-sensitive state %s failed. " +
                                "IMMEDIATE deadline management and case escalation required.", investigationId, state),
                            "CRITICAL"
                        );
                    }
                } else {
                    log.warn("Fraud investigation not found despite investigationId present: investigationId={}, messageId={}",
                        investigationId, messageId);
                }
            }

        } catch (Exception e) {
            log.error("Error validating fraud investigation state: investigationId={}, error={}",
                investigationId, e.getMessage());
        }
    }

    private void assessFraudInvestigationIntegrity(String investigationId, Object originalMessage, String messageId) {
        try {
            // Validate fraud investigation data integrity
            boolean integrityValid = fraudValidationService.validateFraudInvestigationIntegrity(investigationId);
            if (!integrityValid) {
                log.error("Fraud investigation integrity validation failed: investigationId={}", investigationId);

                notificationService.sendFraudInvestigationAlert(
                    "CRITICAL: Investigation Data Integrity Failure",
                    String.format("Fraud investigation %s failed data integrity validation in DLQ processing. " +
                        "Immediate investigation consistency review required.", investigationId),
                    "CRITICAL"
                );

                // Create investigation integrity incident
                kafkaTemplate.send("investigation-integrity-incidents", Map.of(
                    "investigationId", investigationId != null ? investigationId : "unknown",
                    "incidentType", "INVESTIGATION_INTEGRITY_FAILURE",
                    "severity", "CRITICAL",
                    "investigationImpact", true,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing fraud investigation integrity: investigationId={}, error={}",
                investigationId, e.getMessage());
        }
    }

    private void handleFraudInvestigationRecovery(String investigationId, String caseId, String customerId,
                                                 Object originalMessage, String exceptionMessage, String messageId) {
        try {
            // Attempt automatic fraud investigation recovery
            boolean recoveryAttempted = fraudInvestigationService.attemptInvestigationRecovery(
                investigationId, caseId, customerId, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic fraud investigation recovery attempted: investigationId={}, caseId={}, customerId={}",
                    investigationId, caseId, customerId);

                kafkaTemplate.send("fraud-investigation-recovery-queue", Map.of(
                    "investigationId", investigationId != null ? investigationId : "unknown",
                    "caseId", caseId != null ? caseId : "unknown",
                    "customerId", customerId != null ? customerId : "unknown",
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                log.warn("Automatic fraud investigation recovery not possible: investigationId={}", investigationId);

                notificationService.sendFraudInvestigationAlert(
                    "Manual Investigation Recovery Required",
                    String.format("Fraud investigation %s requires manual recovery intervention. " +
                        "Automatic recovery was not successful.", investigationId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling fraud investigation recovery: investigationId={}, error={}",
                investigationId, e.getMessage());
        }
    }

    private void assessInvestigationTimelineImpact(String investigationId, String investigationType, String priority,
                                                 Object originalMessage, String messageId) {
        try {
            if (isTimelinesCritical(investigationType, priority)) {
                log.warn("Timeline-critical fraud investigation failure: investigationId={}, type={}, priority={}",
                    investigationId, investigationType, priority);

                notificationService.sendCaseManagementAlert(
                    "CRITICAL: Investigation Timeline Risk",
                    String.format("Timeline-critical investigation (type: %s, priority: %s) failed for %s. " +
                        "This may impact investigation deadlines and compliance. " +
                        "IMMEDIATE case management and timeline review required.",
                        investigationType, priority, investigationId),
                    "CRITICAL"
                );

                // Create timeline impact incident
                kafkaTemplate.send("investigation-timeline-incidents", Map.of(
                    "investigationId", investigationId != null ? investigationId : "unknown",
                    "investigationType", investigationType,
                    "priority", priority,
                    "incidentType", "TIMELINE_CRITICAL_INVESTIGATION_FAILURE",
                    "timelineRisk", true,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing investigation timeline impact: investigationId={}, error={}",
                investigationId, e.getMessage());
        }
    }

    private void handleCaseManagementEscalation(String investigationId, String caseId, String customerId,
                                              String investigationType, String priority, Object originalMessage, String messageId) {
        try {
            if (requiresCaseEscalation(investigationType, priority)) {
                log.warn("Case management escalation required: investigationId={}, caseId={}, type={}, priority={}",
                    investigationId, caseId, investigationType, priority);

                // Trigger case management escalation
                kafkaTemplate.send("case-management-escalation-queue", Map.of(
                    "investigationId", investigationId != null ? investigationId : "unknown",
                    "caseId", caseId != null ? caseId : "unknown",
                    "customerId", customerId != null ? customerId : "unknown",
                    "investigationType", investigationType,
                    "priority", priority,
                    "escalationReason", "INVESTIGATION_DLQ_FAILURE",
                    "urgency", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));

                // Alert case management team
                notificationService.sendCaseManagementAlert(
                    "Case Escalation Required",
                    String.format("Investigation %s (case %s) requires immediate case management escalation " +
                        "due to processing failure. Review case priority and resource allocation.", investigationId, caseId),
                    "HIGH"
                );
            }
        } catch (Exception e) {
            log.error("Error handling case management escalation: investigationId={}, error={}",
                investigationId, e.getMessage());
        }
    }

    private void triggerManualInvestigationReview(String investigationId, String caseId, String customerId,
                                                 String investigationType, String priority, String messageId) {
        try {
            // All fraud investigation DLQ requires manual review due to case impact
            kafkaTemplate.send("manual-investigation-review-queue", Map.of(
                "investigationId", investigationId != null ? investigationId : "unknown",
                "caseId", caseId != null ? caseId : "unknown",
                "customerId", customerId != null ? customerId : "unknown",
                "investigationType", investigationType,
                "priority", priority,
                "reviewReason", "FRAUD_INVESTIGATION_DLQ_FAILURE",
                "reviewPriority", "HIGH",
                "messageId", messageId,
                "caseImpact", true,
                "requiresImmediateReview", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual investigation review for DLQ: investigationId={}, caseId={}", investigationId, caseId);
        } catch (Exception e) {
            log.error("Error triggering manual investigation review: investigationId={}, error={}",
                investigationId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleFraudInvestigationDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                   int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String investigationId = extractInvestigationId(originalMessage);
        String caseId = extractCaseId(originalMessage);
        String customerId = extractCustomerId(originalMessage);

        // EMERGENCY situation - fraud investigation circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "EMERGENCY: Fraud Investigation DLQ Circuit Breaker",
                String.format("CRITICAL INVESTIGATION FAILURE: Fraud investigation DLQ circuit breaker triggered " +
                    "for investigation %s (case %s, customer %s). " +
                    "This represents complete failure of fraud investigation systems. " +
                    "IMMEDIATE C-LEVEL, FRAUD INVESTIGATION, AND CASE MANAGEMENT ESCALATION REQUIRED.",
                    investigationId, caseId, customerId)
            );

            // Mark as emergency investigation issue
            fraudInvestigationService.markEmergencyInvestigationIssue(investigationId, caseId, "CIRCUIT_BREAKER_INVESTIGATION_DLQ");

        } catch (Exception e) {
            log.error("Error in fraud investigation DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for investigation classification
    private boolean isHighPriorityInvestigation(String priority) {
        return priority != null && (
            priority.contains("HIGH") || priority.contains("CRITICAL") ||
            priority.contains("URGENT")
        );
    }

    private boolean isCriticalInvestigationType(String investigationType) {
        return investigationType != null && (
            investigationType.contains("CRIMINAL") || investigationType.contains("REGULATORY") ||
            investigationType.contains("COMPLEX_FRAUD")
        );
    }

    private boolean isRegulatoryInvestigation(String investigationType) {
        return investigationType != null && (
            investigationType.contains("REGULATORY") || investigationType.contains("COMPLIANCE") ||
            investigationType.contains("SAR")
        );
    }

    private boolean isCriminalInvestigation(String investigationType) {
        return investigationType != null && (
            investigationType.contains("CRIMINAL") || investigationType.contains("LAW_ENFORCEMENT")
        );
    }

    private boolean isComplexFraudInvestigation(String investigationType) {
        return investigationType != null && (
            investigationType.contains("COMPLEX") || investigationType.contains("ORGANIZED") ||
            investigationType.contains("SOPHISTICATED")
        );
    }

    private boolean isTimelinesCritical(String investigationType, String priority) {
        return isRegulatoryInvestigation(investigationType) || isHighPriorityInvestigation(priority);
    }

    private boolean requiresCaseEscalation(String investigationType, String priority) {
        return isCriticalInvestigationType(investigationType) || isHighPriorityInvestigation(priority);
    }

    // Data extraction helper methods
    private String extractInvestigationId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object investigationId = messageMap.get("investigationId");
                if (investigationId == null) investigationId = messageMap.get("id");
                return investigationId != null ? investigationId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract investigationId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractCaseId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object caseId = messageMap.get("caseId");
                if (caseId == null) caseId = messageMap.get("fraudCaseId");
                return caseId != null ? caseId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract caseId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractCustomerId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object customerId = messageMap.get("customerId");
                if (customerId == null) customerId = messageMap.get("userId");
                return customerId != null ? customerId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract customerId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractInvestigationType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object type = messageMap.get("investigationType");
                if (type == null) type = messageMap.get("type");
                return type != null ? type.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract investigationType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractPriority(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object priority = messageMap.get("priority");
                if (priority == null) priority = messageMap.get("investigationPriority");
                return priority != null ? priority.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract priority from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractInvestigationStatus(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object status = messageMap.get("investigationStatus");
                if (status == null) status = messageMap.get("status");
                return status != null ? status.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract investigationStatus from message: {}", e.getMessage());
        }
        return null;
    }
}