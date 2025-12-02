package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.compliance.service.AMLInvestigationService;
import com.waqiti.compliance.service.CaseManagementService;
import com.waqiti.compliance.service.ComplianceNotificationService;
import com.waqiti.compliance.service.SARProcessingService;
import com.waqiti.common.exception.ComplianceProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for AML Investigation Events
 * Handles anti-money laundering investigations, case management, and regulatory reporting
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AMLInvestigationEventsConsumer {
    
    private final AMLInvestigationService investigationService;
    private final CaseManagementService caseManagementService;
    private final ComplianceNotificationService notificationService;
    private final SARProcessingService sarProcessingService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"aml-investigation-events", "suspicious-activity-detected", "investigation-escalated", "sar-filing-required"},
        groupId = "compliance-service-aml-investigation-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000)
    )
    @Transactional
    public void handleAMLInvestigationEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID investigationId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            investigationId = UUID.fromString((String) event.get("investigationId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String alertType = (String) event.get("alertType"); // STRUCTURING, SMURFING, LAYERING, INTEGRATION
            String riskLevel = (String) event.get("riskLevel"); // LOW, MEDIUM, HIGH, CRITICAL
            BigDecimal totalAmount = new BigDecimal((String) event.get("totalAmount"));
            String currency = (String) event.get("currency");
            Integer transactionCount = (Integer) event.get("transactionCount");
            LocalDateTime detectionDate = LocalDateTime.parse((String) event.get("detectionDate"));
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            // Investigation details
            String suspiciousPattern = (String) event.get("suspiciousPattern");
            String geographicRisk = (String) event.get("geographicRisk");
            String timePatternRisk = (String) event.get("timePatternRisk");
            String counterpartyRisk = (String) event.get("counterpartyRisk");
            Boolean crossBorderActivity = (Boolean) event.getOrDefault("crossBorderActivity", false);
            Boolean highRiskCountryInvolved = (Boolean) event.getOrDefault("highRiskCountryInvolved", false);
            
            // Regulatory requirements
            Boolean sarRequired = (Boolean) event.getOrDefault("sarRequired", false);
            Boolean ctrRequired = (Boolean) event.getOrDefault("ctrRequired", false);
            String jurisdictionCode = (String) event.get("jurisdictionCode");
            String regulatoryDeadline = (String) event.get("regulatoryDeadline");
            
            // Case management
            String assignedInvestigator = (String) event.get("assignedInvestigator");
            String caseStatus = (String) event.get("caseStatus"); // OPEN, UNDER_INVESTIGATION, ESCALATED, CLOSED
            String investigationPriority = (String) event.get("investigationPriority"); // LOW, MEDIUM, HIGH, URGENT
            
            log.info("Processing AML investigation event - InvestigationId: {}, CustomerId: {}, Type: {}, Risk: {}", 
                    investigationId, customerId, eventType, riskLevel);
            
            // Step 1: Validate investigation data
            Map<String, Object> validationResult = investigationService.validateInvestigationData(
                    investigationId, customerId, alertType, totalAmount, currency, 
                    transactionCount, detectionDate, timestamp);
            
            if ("INVALID".equals(validationResult.get("status"))) {
                investigationService.logInvalidInvestigation(investigationId, 
                        (String) validationResult.get("reason"), timestamp);
                log.warn("Invalid AML investigation data: {}", validationResult.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Customer risk profile assessment
            Map<String, Object> customerRiskProfile = investigationService.assessCustomerRisk(
                    customerId, totalAmount, transactionCount, crossBorderActivity,
                    highRiskCountryInvolved, geographicRisk, timestamp);
            
            String customerRiskRating = (String) customerRiskProfile.get("riskRating");
            Boolean isPEP = (Boolean) customerRiskProfile.get("isPEP");
            Boolean isSanctioned = (Boolean) customerRiskProfile.get("isSanctioned");
            
            // Step 3: Process based on event type
            switch (eventType) {
                case "SUSPICIOUS_ACTIVITY_DETECTED":
                    investigationService.initiateSuspiciousActivityInvestigation(investigationId,
                            customerId, alertType, suspiciousPattern, totalAmount, currency,
                            transactionCount, riskLevel, customerRiskProfile, timestamp);
                    break;
                    
                case "INVESTIGATION_ESCALATED":
                    investigationService.escalateInvestigation(investigationId, customerId,
                            (String) event.get("escalationReason"), 
                            (String) event.get("escalatedTo"), timestamp);
                    break;
                    
                case "SAR_FILING_REQUIRED":
                    investigationService.processSARRequirement(investigationId, customerId,
                            suspiciousPattern, totalAmount, currency, jurisdictionCode, 
                            regulatoryDeadline, timestamp);
                    break;
                    
                default:
                    investigationService.processGenericInvestigationEvent(investigationId, 
                            eventType, event, timestamp);
            }
            
            // Step 4: Case management workflow
            UUID caseId = caseManagementService.createOrUpdateCase(investigationId, customerId,
                    alertType, caseStatus, investigationPriority, assignedInvestigator,
                    totalAmount, currency, riskLevel, timestamp);
            
            // Step 5: Enhanced due diligence for high-risk cases
            if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel) || isPEP || isSanctioned) {
                investigationService.performEnhancedDueDiligence(investigationId, customerId,
                        customerRiskProfile, crossBorderActivity, geographicRisk, timestamp);
            }
            
            // Step 6: Pattern analysis and ML detection
            Map<String, Object> patternAnalysis = investigationService.analyzeTransactionPatterns(
                    investigationId, customerId, suspiciousPattern, timePatternRisk,
                    counterpartyRisk, transactionCount, timestamp);
            
            Boolean additionalPatternsDetected = (Boolean) patternAnalysis.get("additionalPatternsDetected");
            if (additionalPatternsDetected) {
                investigationService.expandInvestigationScope(investigationId, patternAnalysis, timestamp);
            }
            
            // Step 7: Regulatory filing requirements
            if (sarRequired) {
                UUID sarId = sarProcessingService.initiateSARFiling(investigationId, customerId,
                        suspiciousPattern, totalAmount, currency, jurisdictionCode, 
                        regulatoryDeadline, timestamp);
                
                investigationService.linkSARToInvestigation(investigationId, sarId, timestamp);
            }
            
            if (ctrRequired) {
                investigationService.initiateCTRFiling(investigationId, customerId, totalAmount,
                        currency, jurisdictionCode, timestamp);
            }
            
            // Step 8: International cooperation requirements
            if (crossBorderActivity || highRiskCountryInvolved) {
                investigationService.assessInternationalCooperationRequirements(investigationId,
                        geographicRisk, jurisdictionCode, timestamp);
            }
            
            // Step 9: Timeline and deadline management
            if (regulatoryDeadline != null) {
                LocalDateTime deadline = LocalDateTime.parse(regulatoryDeadline);
                caseManagementService.setRegulatoryDeadline(caseId, deadline, timestamp);
                
                // Auto-escalate if approaching deadline
                investigationService.scheduleDeadlineMonitoring(investigationId, deadline, timestamp);
            }
            
            // Step 10: Send investigation notifications
            notificationService.sendInvestigationNotification(investigationId, customerId,
                    eventType, alertType, riskLevel, assignedInvestigator, sarRequired, timestamp);
            
            // Step 11: Update investigation metrics
            investigationService.updateInvestigationMetrics(investigationId, alertType, riskLevel,
                    totalAmount, currency, caseStatus, timestamp);
            
            // Step 12: Audit logging with enhanced security
            auditService.auditFinancialEvent(
                    "AML_INVESTIGATION_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("AML investigation event processed - Type: %s, Alert: %s, Risk: %s, Amount: %s %s", 
                            eventType, alertType, riskLevel, totalAmount, currency),
                    Map.of(
                            "investigationId", investigationId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "alertType", alertType,
                            "riskLevel", riskLevel,
                            "totalAmount", totalAmount.toString(),
                            "currency", currency,
                            "transactionCount", transactionCount.toString(),
                            "suspiciousPattern", suspiciousPattern,
                            "customerRiskRating", customerRiskRating,
                            "isPEP", isPEP.toString(),
                            "isSanctioned", isSanctioned.toString(),
                            "crossBorderActivity", crossBorderActivity.toString(),
                            "highRiskCountryInvolved", highRiskCountryInvolved.toString(),
                            "sarRequired", sarRequired.toString(),
                            "ctrRequired", ctrRequired.toString(),
                            "caseStatus", caseStatus,
                            "investigationPriority", investigationPriority,
                            "assignedInvestigator", assignedInvestigator != null ? assignedInvestigator : "UNASSIGNED"
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed AML investigation event - InvestigationId: {}, Risk: {}, SAR Required: {}", 
                    investigationId, riskLevel, sarRequired);
            
        } catch (Exception e) {
            log.error("AML investigation event processing failed - InvestigationId: {}, CustomerId: {}, Error: {}", 
                    investigationId, customerId, e.getMessage(), e);
            throw new ComplianceProcessingException("AML investigation event processing failed", e);
        }
    }
}