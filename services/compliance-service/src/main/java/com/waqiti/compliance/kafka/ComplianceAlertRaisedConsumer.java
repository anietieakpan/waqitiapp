package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.compliance.service.CaseManagementService;
import com.waqiti.compliance.service.RegulatoryCaseService;
import com.waqiti.compliance.entity.ComplianceCase;
import com.waqiti.compliance.entity.ComplianceAlert;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka consumer for ComplianceAlertRaised events
 * Creates compliance cases from alerts to ensure proper investigation and tracking
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceAlertRaisedConsumer extends BaseKafkaConsumer {

    private final CaseManagementService caseManagementService;
    private final RegulatoryCaseService regulatoryCaseService;
    private final UniversalDLQHandler universalDLQHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "compliance-alert-raised", groupId = "compliance-case-management-group")
    @CircuitBreaker(name = "compliance-alert-consumer")
    @Retry(name = "compliance-alert-consumer")
    @Transactional
    public void handleComplianceAlertRaised(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "compliance-alert-raised");
        
        try {
            log.info("Processing compliance alert raised event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            
            String alertId = eventData.path("alertId").asText();
            String alertType = eventData.path("alertType").asText();
            String severity = eventData.path("severity").asText();
            String userId = eventData.path("userId").asText();
            String transactionId = eventData.path("transactionId").asText();
            String alertDescription = eventData.path("description").asText();
            String riskScore = eventData.path("riskScore").asText("0");
            BigDecimal transactionAmount = eventData.path("transactionAmount").isMissingNode() ? 
                    BigDecimal.ZERO : new BigDecimal(eventData.path("transactionAmount").asText());
            String sourceSystem = eventData.path("sourceSystem").asText();
            LocalDateTime alertTime = LocalDateTime.parse(eventData.path("alertTime").asText());
            
            log.info("Creating compliance case for alert: alertId={}, type={}, severity={}, userId={}", 
                    alertId, alertType, severity, userId);
            
            // Create compliance case from alert
            createComplianceCase(alertId, alertType, severity, userId, transactionId, 
                    alertDescription, riskScore, transactionAmount, sourceSystem, alertTime);
            
            ack.acknowledge();
            log.info("Successfully processed compliance alert: alertId={}", alertId);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse compliance alert event: {}", e.getMessage(), e);

            // Send to DLQ for retry/parking
            try {
                universalDLQHandler.handleFailedMessage(record, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send compliance alert to DLQ", dlqEx);
            }

            handleProcessingError(record, e);
        } catch (Exception e) {
            log.error("Error processing compliance alert event: {}", e.getMessage(), e);

            // Send to DLQ for retry/parking
            try {
                universalDLQHandler.handleFailedMessage(record, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send compliance alert to DLQ", dlqEx);
            }

            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void createComplianceCase(String alertId, String alertType, String severity, String userId,
                                      String transactionId, String alertDescription, String riskScore,
                                      BigDecimal transactionAmount, String sourceSystem, LocalDateTime alertTime) {
        
        try {
            // Determine case priority and SLA based on alert severity and type
            String casePriority = determineCasePriority(alertType, severity, riskScore);
            int slaHours = determineSLAHours(alertType, severity, riskScore);
            
            // Create compliance case
            ComplianceCase complianceCase = ComplianceCase.builder()
                    .id(UUID.randomUUID())
                    .caseNumber(generateCaseNumber(alertType))
                    .alertId(alertId)
                    .caseType(mapAlertTypeToCase(alertType))
                    .priority(casePriority)
                    .status("OPEN")
                    .subjectUserId(userId)
                    .transactionId(transactionId)
                    .title(generateCaseTitle(alertType, userId))
                    .description(alertDescription)
                    .riskScore(Double.parseDouble(riskScore))
                    .transactionAmount(transactionAmount)
                    .sourceSystem(sourceSystem)
                    .createdAt(alertTime)
                    .slaDeadline(alertTime.plusHours(slaHours))
                    .assignedTo(autoAssignCaseOwner(alertType, severity))
                    .regulatoryReportingRequired(requiresRegulatoryReporting(alertType, severity, riskScore))
                    .build();
            
            // Save the case using case management service
            ComplianceCase createdCase = caseManagementService.createCase(complianceCase);
            
            // Create initial case activities
            createInitialCaseActivities(createdCase, alertType, severity, riskScore);
            
            // If high priority or regulatory reporting required, create immediate notifications
            if ("HIGH".equals(casePriority) || "CRITICAL".equals(casePriority)) {
                createUrgentCaseNotification(createdCase);
            }
            
            // If regulatory reporting required, initiate regulatory case workflow
            if (complianceCase.isRegulatoryReportingRequired()) {
                initiateRegulatoryReporting(createdCase, alertType);
            }
            
            log.info("Created compliance case: caseId={}, caseNumber={}, priority={}, slaHours={}", 
                    createdCase.getId(), createdCase.getCaseNumber(), casePriority, slaHours);
            
        } catch (Exception e) {
            log.error("Error creating compliance case for alert: alertId={}, error={}", 
                    alertId, e.getMessage(), e);
            throw e;
        }
    }
    
    private String determineCasePriority(String alertType, String severity, String riskScore) {
        double risk = Double.parseDouble(riskScore);
        
        // Critical priority cases
        if ("CRITICAL".equalsIgnoreCase(severity) || risk >= 0.9) {
            return "CRITICAL";
        }
        
        // High priority cases
        if ("HIGH".equalsIgnoreCase(severity) || risk >= 0.7 || 
            isHighPriorityAlertType(alertType)) {
            return "HIGH";
        }
        
        // Medium priority cases
        if ("MEDIUM".equalsIgnoreCase(severity) || risk >= 0.4) {
            return "MEDIUM";
        }
        
        return "LOW";
    }
    
    private boolean isHighPriorityAlertType(String alertType) {
        return alertType.contains("SANCTIONS") || 
               alertType.contains("PEP") || 
               alertType.contains("TERRORIST_FINANCING") ||
               alertType.contains("MONEY_LAUNDERING") ||
               alertType.contains("FRAUD");
    }
    
    private int determineSLAHours(String alertType, String severity, String riskScore) {
        double risk = Double.parseDouble(riskScore);
        
        // Critical alerts - 4 hours
        if ("CRITICAL".equalsIgnoreCase(severity) || risk >= 0.9) {
            return 4;
        }
        
        // High priority alerts - 24 hours
        if ("HIGH".equalsIgnoreCase(severity) || risk >= 0.7 || isHighPriorityAlertType(alertType)) {
            return 24;
        }
        
        // Medium priority alerts - 72 hours (3 days)
        if ("MEDIUM".equalsIgnoreCase(severity) || risk >= 0.4) {
            return 72;
        }
        
        // Low priority alerts - 168 hours (7 days)
        return 168;
    }
    
    private String mapAlertTypeToCase(String alertType) {
        if (alertType.contains("AML") || alertType.contains("MONEY_LAUNDERING")) {
            return "AML_INVESTIGATION";
        } else if (alertType.contains("SANCTIONS")) {
            return "SANCTIONS_SCREENING";
        } else if (alertType.contains("PEP")) {
            return "PEP_MONITORING";
        } else if (alertType.contains("FRAUD")) {
            return "FRAUD_INVESTIGATION";
        } else if (alertType.contains("KYC")) {
            return "KYC_REVIEW";
        } else if (alertType.contains("TRANSACTION")) {
            return "TRANSACTION_MONITORING";
        } else {
            return "GENERAL_COMPLIANCE";
        }
    }
    
    private String generateCaseNumber(String alertType) {
        String prefix;
        if (alertType.contains("AML")) {
            prefix = "AML";
        } else if (alertType.contains("SANCTIONS")) {
            prefix = "SAR";
        } else if (alertType.contains("FRAUD")) {
            prefix = "FRD";
        } else if (alertType.contains("KYC")) {
            prefix = "KYC";
        } else {
            prefix = "COM";
        }
        
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(7);
        return String.format("%s-%s-%s", prefix, 
                LocalDateTime.now().getYear(), timestamp);
    }
    
    private String generateCaseTitle(String alertType, String userId) {
        return String.format("%s Alert - User %s", 
                formatAlertType(alertType), 
                userId.substring(0, Math.min(8, userId.length())) + "...");
    }
    
    private String formatAlertType(String alertType) {
        return alertType.replace("_", " ").toLowerCase()
                .replaceAll("\\b\\w", m -> m.group().toUpperCase());
    }
    
    private String autoAssignCaseOwner(String alertType, String severity) {
        // In production, this would use a proper assignment algorithm
        // considering workload, expertise, and availability
        
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            return "senior.compliance.officer@example.com";
        } else if (alertType.contains("AML") || alertType.contains("SANCTIONS")) {
            return "aml.specialist@example.com";
        } else if (alertType.contains("FRAUD")) {
            return "fraud.investigator@example.com";
        } else {
            return "compliance.analyst@example.com";
        }
    }
    
    private boolean requiresRegulatoryReporting(String alertType, String severity, String riskScore) {
        double risk = Double.parseDouble(riskScore);
        
        // Always report critical alerts
        if ("CRITICAL".equalsIgnoreCase(severity) || risk >= 0.9) {
            return true;
        }
        
        // Report specific alert types
        return alertType.contains("SANCTIONS") ||
               alertType.contains("TERRORIST_FINANCING") ||
               alertType.contains("MONEY_LAUNDERING") ||
               (alertType.contains("TRANSACTION") && risk >= 0.7);
    }
    
    private void createInitialCaseActivities(ComplianceCase complianceCase, String alertType, 
                                             String severity, String riskScore) {
        
        try {
            // Create case opening activity
            caseManagementService.addCaseActivity(
                    complianceCase.getId(),
                    "CASE_OPENED",
                    String.format("Case opened from %s alert with severity %s and risk score %s", 
                            alertType, severity, riskScore),
                    "SYSTEM"
            );
            
            // Create assignment activity
            caseManagementService.addCaseActivity(
                    complianceCase.getId(),
                    "CASE_ASSIGNED",
                    String.format("Case automatically assigned to %s", complianceCase.getAssignedTo()),
                    "SYSTEM"
            );
            
            // Create initial investigation checklist
            createInvestigationChecklist(complianceCase);
            
        } catch (Exception e) {
            log.error("Error creating initial case activities: caseId={}, error={}", 
                    complianceCase.getId(), e.getMessage(), e);
        }
    }
    
    private void createInvestigationChecklist(ComplianceCase complianceCase) {
        String caseType = complianceCase.getCaseType();
        
        if ("AML_INVESTIGATION".equals(caseType)) {
            caseManagementService.addCaseTask(complianceCase.getId(), 
                    "Review customer profile and transaction history", "PENDING");
            caseManagementService.addCaseTask(complianceCase.getId(), 
                    "Analyze transaction patterns for suspicious activity", "PENDING");
            caseManagementService.addCaseTask(complianceCase.getId(), 
                    "Check against sanctions and watch lists", "PENDING");
            caseManagementService.addCaseTask(complianceCase.getId(), 
                    "Document findings and prepare SAR if required", "PENDING");
        } else if ("SANCTIONS_SCREENING".equals(caseType)) {
            caseManagementService.addCaseTask(complianceCase.getId(), 
                    "Verify sanctions hit against customer data", "PENDING");
            caseManagementService.addCaseTask(complianceCase.getId(), 
                    "Check for false positive indicators", "PENDING");
            caseManagementService.addCaseTask(complianceCase.getId(), 
                    "Escalate to regulatory if true positive", "PENDING");
        } else if ("FRAUD_INVESTIGATION".equals(caseType)) {
            caseManagementService.addCaseTask(complianceCase.getId(), 
                    "Analyze transaction for fraud indicators", "PENDING");
            caseManagementService.addCaseTask(complianceCase.getId(), 
                    "Contact customer for verification if needed", "PENDING");
            caseManagementService.addCaseTask(complianceCase.getId(), 
                    "Coordinate with fraud prevention team", "PENDING");
        }
    }
    
    private void createUrgentCaseNotification(ComplianceCase complianceCase) {
        try {
            caseManagementService.sendUrgentNotification(
                    complianceCase.getId(),
                    complianceCase.getAssignedTo(),
                    String.format("URGENT: High priority compliance case %s requires immediate attention", 
                            complianceCase.getCaseNumber()),
                    complianceCase.getDescription()
            );
        } catch (Exception e) {
            log.error("Error sending urgent case notification: caseId={}, error={}", 
                    complianceCase.getId(), e.getMessage(), e);
        }
    }
    
    private void initiateRegulatoryReporting(ComplianceCase complianceCase, String alertType) {
        try {
            regulatoryCaseService.initiateRegulatoryCase(
                    complianceCase.getId(),
                    determineRegulatoryType(alertType),
                    complianceCase.getTransactionAmount(),
                    complianceCase.getSubjectUserId()
            );
            
            log.info("Initiated regulatory reporting: caseId={}, type={}", 
                    complianceCase.getId(), alertType);
        } catch (Exception e) {
            log.error("Error initiating regulatory reporting: caseId={}, error={}", 
                    complianceCase.getId(), e.getMessage(), e);
        }
    }
    
    private String determineRegulatoryType(String alertType) {
        if (alertType.contains("SANCTIONS")) {
            return "OFAC_REPORT";
        } else if (alertType.contains("MONEY_LAUNDERING") || alertType.contains("AML")) {
            return "SAR_FILING";
        } else if (alertType.contains("TERRORIST_FINANCING")) {
            return "CTR_FILING";
        } else {
            return "GENERAL_REGULATORY";
        }
    }
}