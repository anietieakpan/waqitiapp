package com.waqiti.compliance.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.compliance.service.ComplianceService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.repository.ComplianceAlertRepository;
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
 * DLQ Consumer for compliance alerts that failed to process.
 * Handles critical regulatory and compliance failures with escalation protocols.
 */
@Component
@Slf4j
public class ComplianceAlertsDlqConsumer extends BaseDlqConsumer {

    private final ComplianceService complianceService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ComplianceAlertRepository complianceAlertRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ComplianceAlertsDlqConsumer(DlqHandler dlqHandler,
                                      AuditService auditService,
                                      NotificationService notificationService,
                                      MeterRegistry meterRegistry,
                                      ComplianceService complianceService,
                                      RegulatoryReportingService regulatoryReportingService,
                                      ComplianceAlertRepository complianceAlertRepository,
                                      KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.complianceService = complianceService;
        this.regulatoryReportingService = regulatoryReportingService;
        this.complianceAlertRepository = complianceAlertRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"compliance-alerts.DLQ"},
        groupId = "compliance-alerts-dlq-consumer-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "compliance-alerts-dlq", fallbackMethod = "handleComplianceAlertsDlqFallback")
    public void handleComplianceAlertsDlq(@Payload Object originalMessage,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         Acknowledgment acknowledgment,
                                         @Header Map<String, Object> headers) {

        log.info("Processing compliance alerts DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String alertId = extractAlertId(originalMessage);
            String userId = extractUserId(originalMessage);
            String alertType = extractAlertType(originalMessage);
            String severity = extractSeverity(originalMessage);
            String regulatoryBody = extractRegulatoryBody(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing compliance alert DLQ: alertId={}, alertType={}, severity={}, messageId={}",
                alertId, alertType, severity, messageId);

            // Validate compliance alert status
            if (alertId != null) {
                validateComplianceAlertStatus(alertId, messageId);
                assessRegulatoryImpact(alertId, alertType, severity, regulatoryBody, originalMessage, messageId);
                handleComplianceReporting(alertId, alertType, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts with regulatory urgency
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for regulatory deadline impacts
            assessRegulatoryDeadlines(alertType, regulatoryBody, originalMessage, messageId);

            // Handle specific compliance alert failures
            handleSpecificComplianceFailure(alertType, alertId, originalMessage, messageId);

            // Trigger manual compliance review
            triggerManualComplianceReview(alertId, alertType, severity, messageId);

        } catch (Exception e) {
            log.error("Error in compliance alerts DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "compliance-alerts-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "REGULATORY_COMPLIANCE";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String severity = extractSeverity(originalMessage);
        String alertType = extractAlertType(originalMessage);
        String regulatoryBody = extractRegulatoryBody(originalMessage);

        // Critical if high severity or regulatory deadline involved
        if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
            return true;
        }

        // Critical regulatory bodies
        if (isHighPriorityRegulator(regulatoryBody)) {
            return true;
        }

        // Critical alert types
        return isCriticalAlertType(alertType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String alertId = extractAlertId(originalMessage);
        String userId = extractUserId(originalMessage);
        String alertType = extractAlertType(originalMessage);
        String severity = extractSeverity(originalMessage);
        String regulatoryBody = extractRegulatoryBody(originalMessage);

        try {
            // IMMEDIATE escalation for compliance failures - these have regulatory implications
            String alertTitle = String.format("REGULATORY CRITICAL: Compliance Alert Failed - %s", alertType);
            String alertMessage = String.format(
                "⚠️ REGULATORY COMPLIANCE FAILURE ⚠️\n\n" +
                "A compliance alert has failed processing and requires IMMEDIATE attention:\n\n" +
                "Alert ID: %s\n" +
                "Alert Type: %s\n" +
                "Severity: %s\n" +
                "Regulatory Body: %s\n" +
                "User ID: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: This failure may result in regulatory non-compliance.\n" +
                "Immediate manual intervention required to prevent regulatory violations.",
                alertId != null ? alertId : "unknown",
                alertType != null ? alertType : "unknown",
                severity != null ? severity : "unknown",
                regulatoryBody != null ? regulatoryBody : "unknown",
                userId != null ? userId : "unknown",
                exceptionMessage
            );

            // Send executive alert for all compliance DLQ issues
            notificationService.sendExecutiveAlert(alertTitle, alertMessage);

            // Send specific compliance team alert
            notificationService.sendComplianceAlert(
                "URGENT: Compliance Alert DLQ",
                alertMessage,
                "CRITICAL"
            );

            // Send regulatory affairs alert if specific regulator involved
            if (regulatoryBody != null && !regulatoryBody.isEmpty()) {
                notificationService.sendRegulatoryAlert(
                    String.format("Regulatory Alert Failed - %s", regulatoryBody),
                    String.format("Alert for %s has failed processing. Review regulatory obligations.", regulatoryBody),
                    "URGENT"
                );
            }

            // Send legal team notification for potential violations
            notificationService.sendLegalAlert(
                "Compliance Failure - Legal Review Required",
                String.format("Compliance alert %s failed. Assess legal implications and violation risks.", alertType),
                "HIGH"
            );

            // Customer notification if applicable (for customer-specific compliance issues)
            if (isCustomerSpecificAlert(alertType) && userId != null) {
                // Note: Customer notifications for compliance should be carefully worded
                notificationService.sendNotification(userId,
                    "Account Review in Progress",
                    "We're conducting a routine review of your account to ensure compliance with regulations. " +
                    "This may temporarily affect some services. We'll contact you if any action is needed.",
                    messageId);
            }

            // Risk management alert for business impact
            notificationService.sendRiskManagementAlert(
                "Compliance Risk Alert",
                String.format("Compliance alert failure for %s may expose the organization to regulatory risk. " +
                    "Immediate assessment required.", alertType),
                "CRITICAL"
            );

        } catch (Exception e) {
            log.error("Failed to send compliance alerts DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateComplianceAlertStatus(String alertId, String messageId) {
        try {
            var complianceAlert = complianceAlertRepository.findById(alertId);
            if (complianceAlert.isPresent()) {
                String status = complianceAlert.get().getStatus();
                String priority = complianceAlert.get().getPriority();

                log.info("Compliance alert status validation for DLQ: alertId={}, status={}, priority={}, messageId={}",
                    alertId, status, priority, messageId);

                // Check for time-sensitive alerts
                if ("PENDING_REGULATORY_RESPONSE".equals(status) || "DEADLINE_APPROACHING".equals(status)) {
                    log.warn("Time-sensitive compliance alert in DLQ: alertId={}, status={}", alertId, status);

                    notificationService.sendExecutiveAlert(
                        "URGENT: Time-Sensitive Compliance Alert Failed",
                        String.format("Time-sensitive compliance alert %s (status: %s) has failed processing. " +
                            "Regulatory deadlines may be at risk.", alertId, status)
                    );
                }

                // Check for high-priority alerts
                if ("CRITICAL".equals(priority) || "REGULATORY_DEADLINE".equals(priority)) {
                    notificationService.sendRegulatoryAlert(
                        "High-Priority Compliance Alert Failed",
                        String.format("High-priority compliance alert %s has failed. " +
                            "Review regulatory implications immediately.", alertId),
                        "URGENT"
                    );
                }
            } else {
                log.error("Compliance alert not found for DLQ: alertId={}, messageId={}", alertId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating compliance alert status for DLQ: alertId={}, error={}",
                alertId, e.getMessage());
        }
    }

    private void assessRegulatoryImpact(String alertId, String alertType, String severity, String regulatoryBody,
                                      Object originalMessage, String messageId) {
        try {
            log.info("Assessing regulatory impact: alertId={}, type={}, regulator={}", alertId, alertType, regulatoryBody);

            // Check for regulatory reporting requirements
            if (regulatoryBody != null) {
                boolean hasReportingDeadline = regulatoryReportingService.hasUpcomingDeadline(regulatoryBody, alertType);
                if (hasReportingDeadline) {
                    log.warn("DLQ for compliance alert with upcoming regulatory deadline: alertId={}, regulator={}",
                        alertId, regulatoryBody);

                    notificationService.sendExecutiveAlert(
                        "CRITICAL: Regulatory Deadline at Risk",
                        String.format("Compliance alert %s for %s has failed and may impact regulatory reporting deadline. " +
                            "Immediate escalation to legal and compliance teams required.", alertId, regulatoryBody)
                    );

                    // Create emergency compliance ticket
                    kafkaTemplate.send("emergency-compliance-tickets", Map.of(
                        "alertId", alertId,
                        "urgency", "REGULATORY_DEADLINE",
                        "regulatoryBody", regulatoryBody,
                        "alertType", alertType,
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }
            }

            // Assess violation risk
            String violationRisk = complianceService.assessViolationRisk(alertType, severity);
            if ("HIGH".equals(violationRisk) || "CRITICAL".equals(violationRisk)) {
                notificationService.sendRiskManagementAlert(
                    "High Violation Risk - Compliance Alert Failed",
                    String.format("Compliance alert %s with %s violation risk has failed. " +
                        "Regulatory violation potential is high.", alertId, violationRisk),
                    "CRITICAL"
                );
            }

            // Check for multi-jurisdictional impact
            boolean isMultiJurisdictional = complianceService.isMultiJurisdictionalAlert(alertType);
            if (isMultiJurisdictional) {
                notificationService.sendComplianceAlert(
                    "Multi-Jurisdictional Compliance Alert Failed",
                    String.format("Multi-jurisdictional compliance alert %s failed. " +
                        "Review impact across all operating jurisdictions.", alertId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error assessing regulatory impact: alertId={}, error={}", alertId, e.getMessage());
        }
    }

    private void handleComplianceReporting(String alertId, String alertType, Object originalMessage,
                                         String exceptionMessage, String messageId) {
        try {
            // Record compliance failure for regulatory audit trail
            complianceService.recordComplianceFailure(alertId, Map.of(
                "failureType", "COMPLIANCE_ALERT_DLQ",
                "alertType", alertType,
                "errorMessage", exceptionMessage,
                "messageId", messageId,
                "timestamp", Instant.now(),
                "requiresRegulatoryNotification", true,
                "auditTrailRequired", true
            ));

            // Check if this failure needs to be reported to regulators
            boolean requiresRegulatoryReporting = complianceService.requiresRegulatoryReporting(alertType);
            if (requiresRegulatoryReporting) {
                log.warn("Compliance alert DLQ requires regulatory reporting: alertId={}, alertType={}",
                    alertId, alertType);

                // Create regulatory reporting record
                kafkaTemplate.send("regulatory-reporting-queue", Map.of(
                    "reportType", "COMPLIANCE_SYSTEM_FAILURE",
                    "alertId", alertId,
                    "alertType", alertType,
                    "failureReason", "DLQ_PROCESSING_FAILURE",
                    "messageId", messageId,
                    "priority", "HIGH",
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error handling compliance reporting: alertId={}, error={}", alertId, e.getMessage());
        }
    }

    private void assessRegulatoryDeadlines(String alertType, String regulatoryBody, Object originalMessage, String messageId) {
        try {
            if (regulatoryBody != null && alertType != null) {
                // Check for upcoming deadlines that this alert may affect
                var upcomingDeadlines = regulatoryReportingService.getUpcomingDeadlines(regulatoryBody, alertType);
                if (!upcomingDeadlines.isEmpty()) {
                    log.warn("Compliance alert DLQ may affect regulatory deadlines: alertType={}, deadlineCount={}",
                        alertType, upcomingDeadlines.size());

                    for (var deadline : upcomingDeadlines) {
                        if (deadline.isWithinDays(7)) { // Within a week
                            notificationService.sendExecutiveAlert(
                                "URGENT: Regulatory Deadline at Risk",
                                String.format("Compliance alert failure may impact regulatory deadline for %s on %s. " +
                                    "Immediate action required.", regulatoryBody, deadline.getDeadlineDate())
                            );
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error assessing regulatory deadlines: error={}", e.getMessage());
        }
    }

    private void handleSpecificComplianceFailure(String alertType, String alertId, Object originalMessage, String messageId) {
        try {
            switch (alertType) {
                case "AML_SCREENING":
                    handleAmlScreeningFailure(alertId, originalMessage, messageId);
                    break;
                case "SAR_FILING":
                    handleSarFilingFailure(alertId, originalMessage, messageId);
                    break;
                case "KYC_VERIFICATION":
                    handleKycVerificationFailure(alertId, originalMessage, messageId);
                    break;
                case "SANCTIONS_SCREENING":
                    handleSanctionsScreeningFailure(alertId, originalMessage, messageId);
                    break;
                case "REGULATORY_REPORTING":
                    handleRegulatoryReportingFailure(alertId, originalMessage, messageId);
                    break;
                case "AUDIT_TRAIL":
                    handleAuditTrailFailure(alertId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for compliance alert type: {}", alertType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific compliance failure: alertType={}, alertId={}, error={}",
                alertType, alertId, e.getMessage());
        }
    }

    private void handleAmlScreeningFailure(String alertId, Object originalMessage, String messageId) {
        String userId = extractUserId(originalMessage);
        notificationService.sendComplianceAlert(
            "AML Screening Failed",
            String.format("AML screening alert %s failed for user %s. Manual AML review required immediately.", alertId, userId),
            "CRITICAL"
        );

        // Trigger manual AML review
        kafkaTemplate.send("manual-aml-review-queue", Map.of(
            "userId", userId,
            "alertId", alertId,
            "priority", "URGENT",
            "reason", "AML_SCREENING_DLQ",
            "messageId", messageId,
            "timestamp", Instant.now()
        ));
    }

    private void handleSarFilingFailure(String alertId, Object originalMessage, String messageId) {
        notificationService.sendExecutiveAlert(
            "SAR Filing Failed",
            String.format("SAR filing alert %s failed. This is a regulatory violation risk. " +
                "Immediate legal and compliance review required.", alertId)
        );
    }

    private void handleKycVerificationFailure(String alertId, Object originalMessage, String messageId) {
        String userId = extractUserId(originalMessage);
        notificationService.sendComplianceAlert(
            "KYC Verification Failed",
            String.format("KYC verification alert %s failed for user %s. Account may be non-compliant.", alertId, userId),
            "HIGH"
        );
    }

    private void handleSanctionsScreeningFailure(String alertId, Object originalMessage, String messageId) {
        notificationService.sendExecutiveAlert(
            "CRITICAL: Sanctions Screening Failed",
            String.format("Sanctions screening alert %s failed. This is a critical compliance violation risk. " +
                "Immediate executive and legal intervention required.", alertId)
        );
    }

    private void handleRegulatoryReportingFailure(String alertId, Object originalMessage, String messageId) {
        String regulatoryBody = extractRegulatoryBody(originalMessage);
        notificationService.sendRegulatoryAlert(
            "Regulatory Reporting Failed",
            String.format("Regulatory reporting alert %s for %s failed. " +
                "Review reporting obligations and deadlines.", alertId, regulatoryBody),
            "HIGH"
        );
    }

    private void handleAuditTrailFailure(String alertId, Object originalMessage, String messageId) {
        notificationService.sendComplianceAlert(
            "Audit Trail Alert Failed",
            String.format("Audit trail alert %s failed. This may affect regulatory examination readiness.", alertId),
            "MEDIUM"
        );
    }

    private void triggerManualComplianceReview(String alertId, String alertType, String severity, String messageId) {
        try {
            // All compliance DLQ messages require manual review due to regulatory implications
            kafkaTemplate.send("manual-compliance-review-queue", Map.of(
                "alertId", alertId,
                "alertType", alertType,
                "severity", severity,
                "reviewReason", "DLQ_PROCESSING_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "escalationRequired", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual compliance review for DLQ: alertId={}, alertType={}", alertId, alertType);
        } catch (Exception e) {
            log.error("Error triggering manual compliance review: alertId={}, error={}", alertId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleComplianceAlertsDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                 int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String alertId = extractAlertId(originalMessage);
        String alertType = extractAlertType(originalMessage);

        // This is a CRITICAL situation - compliance system circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "CRITICAL SYSTEM FAILURE: Compliance DLQ Circuit Breaker",
                String.format("EMERGENCY: Compliance DLQ circuit breaker triggered for alert %s (%s). " +
                    "This represents a complete failure of compliance processing systems. " +
                    "IMMEDIATE C-LEVEL ESCALATION REQUIRED.", alertId, alertType)
            );

            // Mark as emergency compliance issue
            complianceService.markEmergencyComplianceIssue(alertId, "CIRCUIT_BREAKER_COMPLIANCE_DLQ");

        } catch (Exception e) {
            log.error("Error in compliance alerts DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for classification
    private boolean isHighPriorityRegulator(String regulatoryBody) {
        return regulatoryBody != null && (
            regulatoryBody.contains("FinCEN") || regulatoryBody.contains("OCC") ||
            regulatoryBody.contains("FDIC") || regulatoryBody.contains("Fed") ||
            regulatoryBody.contains("CFPB") || regulatoryBody.contains("SEC")
        );
    }

    private boolean isCriticalAlertType(String alertType) {
        return alertType != null && (
            alertType.contains("AML") || alertType.contains("SAR") ||
            alertType.contains("SANCTIONS") || alertType.contains("FRAUD") ||
            alertType.contains("VIOLATION")
        );
    }

    private boolean isCustomerSpecificAlert(String alertType) {
        return alertType != null && (
            alertType.contains("KYC") || alertType.contains("AML") ||
            alertType.contains("CUSTOMER") || alertType.contains("ACCOUNT")
        );
    }

    // Data extraction helper methods
    private String extractAlertId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object alertId = messageMap.get("alertId");
                if (alertId == null) alertId = messageMap.get("complianceAlertId");
                return alertId != null ? alertId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract alertId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractUserId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object userId = messageMap.get("userId");
                if (userId == null) userId = messageMap.get("customerId");
                return userId != null ? userId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract userId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractAlertType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object alertType = messageMap.get("alertType");
                if (alertType == null) alertType = messageMap.get("type");
                return alertType != null ? alertType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract alertType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractSeverity(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object severity = messageMap.get("severity");
                if (severity == null) severity = messageMap.get("priority");
                return severity != null ? severity.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract severity from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractRegulatoryBody(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object regulatoryBody = messageMap.get("regulatoryBody");
                if (regulatoryBody == null) regulatoryBody = messageMap.get("regulator");
                return regulatoryBody != null ? regulatoryBody.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract regulatoryBody from message: {}", e.getMessage());
        }
        return null;
    }
}