package com.waqiti.compliance.events;

import com.waqiti.common.events.AmlAlertEvent;
import com.waqiti.common.events.SarFilingRequestEvent;
import com.waqiti.compliance.service.AMLComplianceService;
import com.waqiti.compliance.service.CaseManagementService;
import com.waqiti.compliance.service.InvestigationService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.common.audit.ComprehensiveAuditService;
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

import java.time.LocalDateTime;
import java.util.*;

/**
 * Critical Event Consumer for AML Alerts
 * 
 * REGULATORY IMPACT: Ensures timely investigation of suspicious activities
 * COMPLIANCE IMPACT: Prevents money laundering and terrorist financing
 * 
 * This consumer processes AML alerts from transaction monitoring systems
 * and initiates appropriate compliance workflows
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AmlAlertEventConsumer {
    
    private final AMLComplianceService amlComplianceService;
    private final CaseManagementService caseManagementService;
    private final InvestigationService investigationService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ComprehensiveAuditService auditService;
    
    /**
     * CRITICAL: Process AML alerts for compliance investigation
     * 
     * This consumer handles AML alerts from various detection systems
     * and ensures proper investigation and reporting
     */
    @KafkaListener(
        topics = "aml-alerts",
        groupId = "compliance-service-aml-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @Transactional
    public void handleAmlAlert(
            @Payload AmlAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.error("AML ALERT: Processing alert {} for user {} with severity {} from partition {}, offset {}", 
            event.getAlertId(), event.getUserId(), event.getSeverity(), partition, offset);
        
        try {
            // Audit the AML alert reception
            auditService.auditCriticalComplianceEvent(
                "AML_ALERT_RECEIVED",
                event.getUserId().toString(),
                "AML alert received: " + event.getAlertType(),
                Map.of(
                    "alertId", event.getAlertId(),
                    "alertType", event.getAlertType(),
                    "severity", event.getSeverity(),
                    "riskScore", event.getRiskScore(),
                    "transactionAmount", event.getTransactionAmount() != null ? event.getTransactionAmount() : "N/A"
                )
            );
            
            // Process based on severity and type
            processAmlAlert(event);
            
            // Handle automated actions
            executeAutomatedActions(event);
            
            // Create or update investigation case
            handleInvestigationCase(event);
            
            // Check if SAR filing is required
            if (event.isRequiresSarFiling()) {
                initiateSarFiling(event);
            }
            
            // Handle regulatory reporting requirements
            handleRegulatoryReporting(event);
            
            // Schedule follow-up actions
            scheduleFollowUpActions(event);
            
            // Log successful processing
            log.error("AML ALERT: Successfully processed alert {} - Type: {}, Severity: {}", 
                event.getAlertId(), event.getAlertType(), event.getSeverity());
            
            // Acknowledge the message after successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process AML alert {} for user {}", 
                event.getAlertId(), event.getUserId(), e);
            
            // Audit the failure - AML alert processing failure is critical
            auditService.auditCriticalComplianceEvent(
                "AML_ALERT_PROCESSING_FAILED",
                event.getUserId().toString(),
                "CRITICAL: Failed to process AML alert: " + e.getMessage(),
                Map.of(
                    "alertId", event.getAlertId(),
                    "error", e.getMessage(),
                    "alertType", event.getAlertType()
                )
            );
            
            // Don't acknowledge - let the message be retried or sent to DLQ
            throw new RuntimeException("AML alert processing failed", e);
        }
    }
    
    /**
     * Process AML alert based on severity and type
     */
    private void processAmlAlert(AmlAlertEvent event) {
        switch (event.getSeverity()) {
            case CRITICAL:
                handleCriticalAlert(event);
                break;
            case HIGH:
                handleHighSeverityAlert(event);
                break;
            case MEDIUM:
                handleMediumSeverityAlert(event);
                break;
            case LOW:
                handleLowSeverityAlert(event);
                break;
            case INFO:
                handleInfoAlert(event);
                break;
            default:
                log.warn("Unknown AML severity: {}", event.getSeverity());
                handleMediumSeverityAlert(event); // Default to medium
        }
    }
    
    /**
     * Handle critical severity alerts - immediate action required
     */
    private void handleCriticalAlert(AmlAlertEvent event) {
        log.error("CRITICAL AML: Processing CRITICAL alert {} for user {} - {}", 
            event.getAlertId(), event.getUserId(), event.getAlertType());
        
        try {
            // Immediately freeze account if required
            if (event.isRequiresAccountFreeze()) {
                amlComplianceService.freezeAccountForInvestigation(
                    event.getUserId(),
                    event.getAlertId(),
                    "CRITICAL AML ALERT: " + event.getAlertType(),
                    event.getCaseId()
                );
            }
            
            // Block transaction if still pending
            if (event.getTransactionId() != null && !event.isTransactionBlocked()) {
                amlComplianceService.blockTransaction(
                    event.getTransactionId(),
                    "AML Alert: " + event.getDescription()
                );
            }
            
            // Create high-priority investigation case
            String caseId = caseManagementService.createCriticalInvestigationCase(
                event.getUserId(),
                event.getAlertId(),
                event.getAlertType().toString(),
                event.getDescription(),
                event.getRiskScore()
            );
            
            // Notify compliance team immediately
            investigationService.notifyComplianceTeamUrgent(
                event.getAlertId(),
                caseId,
                event.getSeverity(),
                event.getDescription()
            );
            
            // Send executive notification for critical alerts
            if (isExecutiveNotificationRequired(event)) {
                investigationService.sendExecutiveNotification(
                    event.getAlertId(),
                    event.getUserId(),
                    event.getAlertType(),
                    event.getTransactionAmount()
                );
            }
            
            log.error("CRITICAL AML: Immediate actions taken for alert {}", event.getAlertId());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to handle critical AML alert {}", event.getAlertId(), e);
            throw e;
        }
    }
    
    /**
     * Handle high severity alerts - investigation within 24 hours
     */
    private void handleHighSeverityAlert(AmlAlertEvent event) {
        log.error("HIGH AML: Processing high severity alert {} for user {} - {}", 
            event.getAlertId(), event.getUserId(), event.getAlertType());
        
        try {
            // Create investigation case with high priority
            String caseId = caseManagementService.createHighPriorityCase(
                event.getUserId(),
                event.getAlertId(),
                event.getAlertType().toString(),
                event.getDescription(),
                event.getRiskScore()
            );
            
            // Apply transaction limits
            if (shouldApplyTransactionLimits(event)) {
                amlComplianceService.applyTransactionLimits(
                    event.getUserId(),
                    event.getDailyTotal(),
                    LocalDateTime.now().plusDays(7) // 7-day review period
                );
            }
            
            // Enable enhanced monitoring
            amlComplianceService.enableEnhancedMonitoring(
                event.getUserId(),
                event.getAlertType().toString(),
                LocalDateTime.now().plusMonths(3) // 3-month monitoring
            );
            
            // Notify compliance team
            investigationService.notifyComplianceTeam(
                event.getAlertId(),
                caseId,
                event.getSeverity(),
                event.getDescription()
            );
            
            log.error("HIGH AML: High priority case created: {}", caseId);
            
        } catch (Exception e) {
            log.error("Failed to handle high severity AML alert {}", event.getAlertId(), e);
            throw e;
        }
    }
    
    /**
     * Handle medium severity alerts - investigation within 72 hours
     */
    private void handleMediumSeverityAlert(AmlAlertEvent event) {
        log.warn("MEDIUM AML: Processing medium severity alert {} for user {} - {}", 
            event.getAlertId(), event.getUserId(), event.getAlertType());
        
        try {
            // Create standard investigation case
            String caseId = caseManagementService.createStandardCase(
                event.getUserId(),
                event.getAlertId(),
                event.getAlertType().toString(),
                event.getDescription(),
                event.getRiskScore()
            );
            
            // Schedule investigation
            investigationService.scheduleInvestigation(
                caseId,
                event.getAlertId(),
                LocalDateTime.now().plusHours(72), // 72-hour deadline
                "MEDIUM"
            );
            
            // Update risk profile
            amlComplianceService.updateRiskProfile(
                event.getUserId(),
                event.getRiskScore(),
                event.getDetectedPatterns()
            );
            
            log.warn("MEDIUM AML: Standard case created: {}", caseId);
            
        } catch (Exception e) {
            log.error("Failed to handle medium severity AML alert {}", event.getAlertId(), e);
            // Don't rethrow for medium severity
        }
    }
    
    /**
     * Handle low severity alerts - routine review
     */
    private void handleLowSeverityAlert(AmlAlertEvent event) {
        log.info("LOW AML: Processing low severity alert {} for user {} - {}", 
            event.getAlertId(), event.getUserId(), event.getAlertType());
        
        try {
            // Log for pattern analysis
            amlComplianceService.logForPatternAnalysis(
                event.getUserId(),
                event.getAlertId(),
                event.getAlertType(),
                event.getDetectedPatterns()
            );
            
            // Update user monitoring score
            amlComplianceService.updateMonitoringScore(
                event.getUserId(),
                event.getRiskScore()
            );
            
            // Create low priority case if threshold exceeded
            if (shouldCreateLowPriorityCase(event)) {
                caseManagementService.createLowPriorityCase(
                    event.getUserId(),
                    event.getAlertId(),
                    event.getAlertType().toString(),
                    event.getDescription()
                );
            }
            
            log.info("LOW AML: Alert logged for pattern analysis");
            
        } catch (Exception e) {
            log.error("Failed to handle low severity AML alert {}", event.getAlertId(), e);
            // Don't rethrow for low severity
        }
    }
    
    /**
     * Handle informational alerts
     */
    private void handleInfoAlert(AmlAlertEvent event) {
        log.info("INFO AML: Processing informational alert {} for user {}", 
            event.getAlertId(), event.getUserId());
        
        // Simply log for audit trail
        amlComplianceService.logInformationalAlert(
            event.getUserId(),
            event.getAlertId(),
            event.getAlertType(),
            event.getDescription()
        );
    }
    
    /**
     * Execute automated actions based on alert configuration
     */
    private void executeAutomatedActions(AmlAlertEvent event) {
        if (event.getAutomatedActions() == null || event.getAutomatedActions().isEmpty()) {
            return;
        }
        
        for (String action : event.getAutomatedActions()) {
            try {
                switch (action) {
                    case "FREEZE_ACCOUNT":
                        amlComplianceService.freezeAccountForInvestigation(
                            event.getUserId(),
                            event.getAlertId(),
                            event.getDescription(),
                            event.getCaseId()
                        );
                        break;
                    case "BLOCK_TRANSACTION":
                        if (event.getTransactionId() != null) {
                            amlComplianceService.blockTransaction(
                                event.getTransactionId(),
                                event.getDescription()
                            );
                        }
                        break;
                    case "REQUIRE_ENHANCED_KYC":
                        amlComplianceService.requireEnhancedKyc(
                            event.getUserId(),
                            event.getAlertType().toString()
                        );
                        break;
                    case "NOTIFY_AUTHORITIES":
                        regulatoryReportingService.notifyAuthorities(
                            event.getUserId(),
                            event.getAlertId(),
                            event.getAlertType().toString()
                        );
                        break;
                    default:
                        log.warn("Unknown automated action: {}", action);
                }
            } catch (Exception e) {
                log.error("Failed to execute automated action: {}", action, e);
            }
        }
    }
    
    /**
     * Handle investigation case creation/update
     */
    private void handleInvestigationCase(AmlAlertEvent event) {
        try {
            String caseId = event.getCaseId();
            
            if (caseId == null) {
                // Create new investigation case
                caseId = investigationService.createInvestigationCase(
                    event.getUserId(),
                    event.getAlertId(),
                    event.getAlertType(),
                    event.getSeverity(),
                    event.getDescription(),
                    event.getRiskScore(),
                    event.getInvestigationDeadline()
                );
            } else {
                // Update existing case with new alert
                investigationService.addAlertToCase(
                    caseId,
                    event.getAlertId(),
                    event.getAlertType(),
                    event.getSeverity(),
                    event.getDescription()
                );
            }
            
            // Assign investigator if specified
            if (event.getAssignedInvestigator() != null) {
                investigationService.assignInvestigator(
                    caseId,
                    event.getAssignedInvestigator()
                );
            }
            
        } catch (Exception e) {
            log.error("Failed to handle investigation case for alert {}", event.getAlertId(), e);
        }
    }
    
    /**
     * Initiate SAR filing when required
     */
    private void initiateSarFiling(AmlAlertEvent event) {
        log.error("SAR FILING: Initiating SAR filing for AML alert {}", event.getAlertId());
        
        try {
            // Determine SAR category based on alert type
            SarFilingRequestEvent.SarCategory category = determineSarCategory(event.getAlertType());
            
            // Determine SAR priority based on severity
            SarFilingRequestEvent.SarPriority priority = determineSarPriority(event.getSeverity());
            
            // Create SAR filing request
            SarFilingRequestEvent sarRequest = SarFilingRequestEvent.builder()
                .userId(event.getUserId())
                .accountId(event.getAccountId())
                .customerName(event.getCustomerName())
                .category(category)
                .priority(priority)
                .suspiciousActivity(event.getDescription())
                .totalSuspiciousAmount(event.getTransactionAmount())
                .currency(event.getCurrency())
                .detectionMethod("AML_ALERT_SYSTEM")
                .detectionRuleId(event.getAlertType().toString())
                .riskScore(event.getRiskScore() != null ? event.getRiskScore().doubleValue() : 0.0)
                .redFlags(event.getRedFlags())
                .suspiciousPatterns(event.getDetectedPatterns())
                .suspiciousTransactionIds(
                    event.getTransactionId() != null ? 
                    List.of(event.getTransactionId()) : 
                    event.getRelatedTransactionIds()
                )
                .caseId(event.getCaseId())
                .filingDeadline(calculateSarFilingDeadline(priority))
                .regulatoryBodies(determineRegulatoryBodies(event))
                .requiresImmediateFiling(event.getSeverity() == AmlAlertEvent.AmlSeverity.CRITICAL)
                .requiresLawEnforcementNotification(
                    event.getAlertType() == AmlAlertEvent.AmlAlertType.STRUCTURING ||
                    event.getAlertType() == AmlAlertEvent.AmlAlertType.SANCTIONED_COUNTRY
                )
                .requestingSystem("AML_MONITORING")
                .requestedAt(LocalDateTime.now())
                .metadata(Map.of(
                    "amlAlertId", event.getAlertId(),
                    "alertType", event.getAlertType(),
                    "severity", event.getSeverity()
                ))
                .build();
            
            // Publish SAR filing request
            regulatoryReportingService.publishSarFilingRequest(sarRequest);
            
            log.error("SAR FILING: SAR filing request published for alert {}", event.getAlertId());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to initiate SAR filing for alert {}", event.getAlertId(), e);
            // Don't rethrow - SAR filing failure shouldn't block alert processing
        }
    }
    
    /**
     * Handle regulatory reporting requirements
     */
    private void handleRegulatoryReporting(AmlAlertEvent event) {
        try {
            // Report based on alert type
            switch (event.getAlertType()) {
                case LARGE_CASH_TRANSACTION:
                case LARGE_WIRE_TRANSFER:
                    regulatoryReportingService.fileCurrencyTransactionReport(
                        event.getUserId(),
                        event.getTransactionId(),
                        event.getTransactionAmount(),
                        event.getCurrency()
                    );
                    break;
                    
                case SANCTIONED_COUNTRY:
                case HIGH_RISK_JURISDICTION:
                    regulatoryReportingService.fileGeographicRiskReport(
                        event.getUserId(),
                        event.getSourceCountry(),
                        event.getDestinationCountry(),
                        event.getTransactionAmount()
                    );
                    break;
                    
                case PEP_TRANSACTION:
                    regulatoryReportingService.filePepTransactionReport(
                        event.getUserId(),
                        event.getTransactionId(),
                        event.getDescription()
                    );
                    break;
                    
                case WATCHLIST_MATCH:
                    regulatoryReportingService.fileWatchlistMatchReport(
                        event.getUserId(),
                        event.getAlertId(),
                        event.getDescription()
                    );
                    break;
            }
            
            // File jurisdiction-specific reports
            if (event.getRequiredReports() != null) {
                for (String report : event.getRequiredReports()) {
                    regulatoryReportingService.fileRequiredReport(
                        report,
                        event.getUserId(),
                        event.getAlertId(),
                        event.getMetadata()
                    );
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to handle regulatory reporting for alert {}", event.getAlertId(), e);
        }
    }
    
    /**
     * Schedule follow-up actions for the alert
     */
    private void scheduleFollowUpActions(AmlAlertEvent event) {
        try {
            // Schedule investigation review
            investigationService.scheduleInvestigationReview(
                event.getAlertId(),
                event.getCaseId(),
                event.getInvestigationDeadline()
            );
            
            // Schedule risk reassessment
            amlComplianceService.scheduleRiskReassessment(
                event.getUserId(),
                LocalDateTime.now().plusDays(30) // 30-day reassessment
            );
            
            // Schedule enhanced due diligence if required
            if (event.isRequiresEnhancedDueDiligence()) {
                amlComplianceService.scheduleEnhancedDueDiligence(
                    event.getUserId(),
                    event.getAlertType().toString(),
                    LocalDateTime.now().plusDays(7) // 7-day deadline
                );
            }
            
        } catch (Exception e) {
            log.error("Failed to schedule follow-up actions for alert {}", event.getAlertId(), e);
        }
    }
    
    // Helper methods
    
    private boolean isExecutiveNotificationRequired(AmlAlertEvent event) {
        return event.getSeverity() == AmlAlertEvent.AmlSeverity.CRITICAL &&
               (event.getAlertType() == AmlAlertEvent.AmlAlertType.STRUCTURING ||
                event.getAlertType() == AmlAlertEvent.AmlAlertType.SANCTIONED_COUNTRY ||
                event.getAlertType() == AmlAlertEvent.AmlAlertType.PEP_TRANSACTION ||
                (event.getTransactionAmount() != null && 
                 event.getTransactionAmount().compareTo(new java.math.BigDecimal("100000")) > 0));
    }
    
    private boolean shouldApplyTransactionLimits(AmlAlertEvent event) {
        return event.getAlertType() == AmlAlertEvent.AmlAlertType.STRUCTURING ||
               event.getAlertType() == AmlAlertEvent.AmlAlertType.RAPID_MOVEMENT ||
               event.getAlertType() == AmlAlertEvent.AmlAlertType.HIGH_VELOCITY ||
               event.getDailyTransactionCount() > 20;
    }
    
    private boolean shouldCreateLowPriorityCase(AmlAlertEvent event) {
        return event.getRiskScore() > 30 ||
               event.getDetectedPatterns().size() > 3 ||
               event.getRelatedAlertIds().size() > 2;
    }
    
    private SarFilingRequestEvent.SarCategory determineSarCategory(AmlAlertEvent.AmlAlertType alertType) {
        switch (alertType) {
            case STRUCTURING:
            case LAYERING:
            case INTEGRATION:
                return SarFilingRequestEvent.SarCategory.STRUCTURING;
            case SANCTIONED_COUNTRY:
            case HIGH_RISK_JURISDICTION:
                return SarFilingRequestEvent.SarCategory.SANCTIONS_VIOLATION;
            case PEP_TRANSACTION:
                return SarFilingRequestEvent.SarCategory.BRIBERY_CORRUPTION;
            case WATCHLIST_MATCH:
                return SarFilingRequestEvent.SarCategory.TERRORIST_FINANCING;
            default:
                return SarFilingRequestEvent.SarCategory.MONEY_LAUNDERING;
        }
    }
    
    private SarFilingRequestEvent.SarPriority determineSarPriority(AmlAlertEvent.AmlSeverity severity) {
        switch (severity) {
            case CRITICAL:
                return SarFilingRequestEvent.SarPriority.IMMEDIATE;
            case HIGH:
                return SarFilingRequestEvent.SarPriority.URGENT;
            case MEDIUM:
                return SarFilingRequestEvent.SarPriority.HIGH;
            case LOW:
                return SarFilingRequestEvent.SarPriority.STANDARD;
            case INFO:
            default:
                return SarFilingRequestEvent.SarPriority.LOW;
        }
    }
    
    private LocalDateTime calculateSarFilingDeadline(SarFilingRequestEvent.SarPriority priority) {
        LocalDateTime now = LocalDateTime.now();
        switch (priority) {
            case IMMEDIATE:
                return now.plusHours(24);
            case URGENT:
                return now.plusDays(2);
            case HIGH:
                return now.plusDays(5);
            case STANDARD:
                return now.plusDays(30);
            case LOW:
            default:
                return now.plusDays(60);
        }
    }
    
    private List<String> determineRegulatoryBodies(AmlAlertEvent event) {
        List<String> bodies = new ArrayList<>();
        bodies.add("FINCEN"); // Financial Crimes Enforcement Network (US)
        
        if (event.getJurisdictionCode() != null) {
            switch (event.getJurisdictionCode()) {
                case "US":
                    bodies.add("OCC"); // Office of the Comptroller of the Currency
                    bodies.add("FDIC"); // Federal Deposit Insurance Corporation
                    break;
                case "GB":
                    bodies.add("FCA"); // Financial Conduct Authority
                    bodies.add("NCA"); // National Crime Agency
                    break;
                case "EU":
                    bodies.add("EBA"); // European Banking Authority
                    break;
            }
        }
        
        return bodies;
    }
}