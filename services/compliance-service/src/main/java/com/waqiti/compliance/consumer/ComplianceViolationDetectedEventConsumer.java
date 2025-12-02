package com.waqiti.compliance.consumer;

import com.waqiti.common.events.ComplianceViolationDetectedEvent;
import com.waqiti.compliance.service.ViolationProcessingService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.CustomerRestrictionService;
import com.waqiti.compliance.service.NotificationService;
import com.waqiti.compliance.service.AuditService;
import com.waqiti.compliance.repository.ProcessedEventRepository;
import com.waqiti.compliance.repository.ComplianceViolationRepository;
import com.waqiti.compliance.model.ProcessedEvent;
import com.waqiti.compliance.model.ComplianceViolation;
import com.waqiti.compliance.model.ViolationSeverity;
import com.waqiti.compliance.model.ViolationStatus;
import com.waqiti.compliance.model.RemediationAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Consumer for ComplianceViolationDetectedEvent - Critical for regulatory response
 * Handles violation assessment, remediation, regulatory reporting, and customer restrictions
 * ZERO TOLERANCE: All compliance violations must be addressed immediately with proper regulatory reporting
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ComplianceViolationDetectedEventConsumer {
    
    private final ViolationProcessingService violationProcessingService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final CustomerRestrictionService customerRestrictionService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ProcessedEventRepository processedEventRepository;
    private final ComplianceViolationRepository complianceViolationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final Map<String, ViolationSeverity> VIOLATION_SEVERITY_MAP = Map.of(
        "BSA_VIOLATION", ViolationSeverity.CRITICAL,
        "AML_VIOLATION", ViolationSeverity.CRITICAL,
        "OFAC_VIOLATION", ViolationSeverity.CRITICAL,
        "KYC_VIOLATION", ViolationSeverity.HIGH,
        "CDD_VIOLATION", ViolationSeverity.HIGH,
        "TRANSACTION_MONITORING_ALERT", ViolationSeverity.MEDIUM,
        "SUSPICIOUS_ACTIVITY", ViolationSeverity.HIGH,
        "WIRE_TRANSFER_VIOLATION", ViolationSeverity.HIGH
    );
    
    @KafkaListener(
        topics = "compliance.violation.detected",
        groupId = "compliance-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for compliance processing
    public void handleComplianceViolationDetected(ComplianceViolationDetectedEvent event) {
        log.warn("Processing compliance violation: {} - Type: {} - Customer: {} - Description: {}", 
            event.getViolationId(), event.getViolationType(), event.getCustomerId(), event.getDescription());
        
        // IDEMPOTENCY CHECK - Prevent duplicate violation processing
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Compliance violation already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // Create violation record
            ComplianceViolation violation = createViolationRecord(event);
            
            // STEP 1: Assess violation severity and classification
            assessViolationSeverityAndClassification(violation, event);
            
            // STEP 2: Apply immediate customer restrictions and holds
            applyImmediateRestrictions(violation, event);
            
            // STEP 3: Execute mandatory regulatory reporting (SAR, CTR, etc.)
            executeMandatoryRegulatoryReporting(violation, event);
            
            // STEP 4: Perform root cause analysis and pattern detection
            performRootCauseAnalysis(violation, event);
            
            // STEP 5: Initiate investigation and evidence collection
            initiateInvestigationAndEvidenceCollection(violation, event);
            
            // STEP 6: Implement remediation actions and controls
            implementRemediationActions(violation, event);
            
            // STEP 7: Update risk profiles and monitoring parameters
            updateRiskProfilesAndMonitoring(violation, event);
            
            // STEP 8: Generate regulatory examination documentation
            generateRegulatoryExaminationDocumentation(violation, event);
            
            // STEP 9: Notify regulatory authorities if required
            notifyRegulatoryAuthorities(violation, event);
            
            // STEP 10: Send internal alerts and escalations
            sendInternalAlertsAndEscalations(violation, event);
            
            // STEP 11: Create comprehensive audit trail
            createComprehensiveAuditTrail(violation, event);
            
            // STEP 12: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("ComplianceViolationDetectedEvent")
                .processedAt(Instant.now())
                .violationId(event.getViolationId())
                .customerId(event.getCustomerId())
                .violationType(event.getViolationType())
                .violationSeverity(violation.getSeverity())
                .violationStatus(violation.getStatus())
                .regulatoryReportsFiled(violation.getRegulatoryReportIds().size())
                .restrictionsApplied(violation.getRestrictionsApplied().size())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.warn("Successfully processed compliance violation: {} - Severity: {}, Status: {}, Reports: {}", 
                event.getViolationId(), violation.getSeverity(), violation.getStatus(), 
                violation.getRegulatoryReportIds().size());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process compliance violation: {}", 
                event.getViolationId(), e);
                
            // Apply emergency compliance controls
            applyEmergencyComplianceControls(event, e);
            
            throw new RuntimeException("Compliance violation processing failed", e);
        }
    }
    
    private ComplianceViolation createViolationRecord(ComplianceViolationDetectedEvent event) {
        ComplianceViolation violation = ComplianceViolation.builder()
            .id(event.getViolationId())
            .customerId(event.getCustomerId())
            .violationType(event.getViolationType())
            .description(event.getDescription())
            .detectedAt(event.getDetectedAt())
            .transactionId(event.getTransactionId())
            .amount(event.getAmount())
            .sourceSystem(event.getSourceSystem())
            .alertId(event.getAlertId())
            .status(ViolationStatus.DETECTED)
            .createdAt(LocalDateTime.now())
            .regulatoryReportIds(new ArrayList<>())
            .restrictionsApplied(new ArrayList<>())
            .remediationActions(new ArrayList<>())
            .investigationNotes(new ArrayList<>())
            .build();
        
        return complianceViolationRepository.save(violation);
    }
    
    private void assessViolationSeverityAndClassification(ComplianceViolation violation, ComplianceViolationDetectedEvent event) {
        // Determine severity based on violation type
        ViolationSeverity severity = VIOLATION_SEVERITY_MAP.getOrDefault(
            event.getViolationType(),
            ViolationSeverity.MEDIUM
        );
        
        // Adjust severity based on amount
        if (event.getAmount() != null) {
            if (event.getAmount().compareTo(new BigDecimal("100000")) > 0) {
                severity = ViolationSeverity.CRITICAL;
            } else if (event.getAmount().compareTo(new BigDecimal("10000")) > 0 && 
                       severity.ordinal() < ViolationSeverity.HIGH.ordinal()) {
                severity = ViolationSeverity.HIGH;
            }
        }
        
        violation.setSeverity(severity);
        
        // Classify violation by regulatory framework
        Set<String> regulatoryFrameworks = violationProcessingService.classifyByRegulatoryFramework(
            event.getViolationType(),
            event.getDescription()
        );
        
        violation.setRegulatoryFrameworks(regulatoryFrameworks);
        
        // Assess potential fine and penalty exposure
        BigDecimal potentialFine = violationProcessingService.calculatePotentialFine(
            event.getViolationType(),
            event.getAmount(),
            severity
        );
        
        violation.setPotentialFine(potentialFine);
        
        // Determine reporting requirements
        Map<String, Boolean> reportingRequirements = violationProcessingService.determineReportingRequirements(
            event.getViolationType(),
            severity,
            regulatoryFrameworks
        );
        
        violation.setReportingRequirements(reportingRequirements);
        
        complianceViolationRepository.save(violation);
        
        log.warn("Violation severity assessed for {}: Severity: {}, Frameworks: {}, Potential fine: ${}",
            event.getViolationId(), severity, regulatoryFrameworks, potentialFine);
    }
    
    private void applyImmediateRestrictions(ComplianceViolation violation, ComplianceViolationDetectedEvent event) {
        List<String> restrictionsApplied = new ArrayList<>();
        
        switch (violation.getSeverity()) {
            case CRITICAL -> {
                // Immediately freeze all customer accounts
                customerRestrictionService.freezeAllAccounts(
                    event.getCustomerId(),
                    "CRITICAL_COMPLIANCE_VIOLATION",
                    violation.getId()
                );
                restrictionsApplied.add("ALL_ACCOUNTS_FROZEN");
                
                // Block all transaction capabilities
                customerRestrictionService.blockAllTransactions(
                    event.getCustomerId(),
                    "CRITICAL_VIOLATION_BLOCK"
                );
                restrictionsApplied.add("ALL_TRANSACTIONS_BLOCKED");
                
                // Place on enhanced monitoring
                customerRestrictionService.placeOnEnhancedMonitoring(
                    event.getCustomerId(),
                    "IMMEDIATE",
                    violation.getId()
                );
                restrictionsApplied.add("ENHANCED_MONITORING_IMMEDIATE");
            }
            
            case HIGH -> {
                // Freeze specific transaction types
                customerRestrictionService.restrictTransactionTypes(
                    event.getCustomerId(),
                    Arrays.asList("WIRE_TRANSFER", "INTERNATIONAL_TRANSFER", "LARGE_CASH"),
                    violation.getId()
                );
                restrictionsApplied.add("HIGH_RISK_TRANSACTIONS_RESTRICTED");
                
                // Reduce transaction limits
                customerRestrictionService.reduceTransactionLimits(
                    event.getCustomerId(),
                    new BigDecimal("5000"), // Daily limit
                    new BigDecimal("25000"), // Monthly limit
                    violation.getId()
                );
                restrictionsApplied.add("TRANSACTION_LIMITS_REDUCED");
                
                // Require manager approval for transactions
                customerRestrictionService.requireManagerApproval(
                    event.getCustomerId(),
                    new BigDecimal("1000") // Threshold for approval
                );
                restrictionsApplied.add("MANAGER_APPROVAL_REQUIRED");
            }
            
            case MEDIUM -> {
                // Enhanced transaction monitoring
                customerRestrictionService.enableEnhancedTransactionMonitoring(
                    event.getCustomerId(),
                    violation.getId()
                );
                restrictionsApplied.add("ENHANCED_TRANSACTION_MONITORING");
                
                // Additional verification requirements
                customerRestrictionService.requireAdditionalVerification(
                    event.getCustomerId(),
                    Arrays.asList("PHONE_VERIFICATION", "EMAIL_CONFIRMATION")
                );
                restrictionsApplied.add("ADDITIONAL_VERIFICATION_REQUIRED");
            }
        }
        
        // Hold specific transaction if provided
        if (event.getTransactionId() != null) {
            customerRestrictionService.holdTransaction(
                event.getTransactionId(),
                "COMPLIANCE_VIOLATION_HOLD",
                violation.getId()
            );
            restrictionsApplied.add("TRANSACTION_HELD");
        }
        
        violation.setRestrictionsApplied(restrictionsApplied);
        violation.setRestrictionsAppliedAt(LocalDateTime.now());
        
        complianceViolationRepository.save(violation);
        
        log.warn("Immediate restrictions applied for violation {}: {}", 
            event.getViolationId(), restrictionsApplied);
    }
    
    private void executeMandatoryRegulatoryReporting(ComplianceViolation violation, ComplianceViolationDetectedEvent event) {
        List<String> reportIds = new ArrayList<>();
        
        // File SAR (Suspicious Activity Report) for suspicious activities
        if (violation.getReportingRequirements().getOrDefault("SAR_REQUIRED", false)) {
            String sarId = regulatoryReportingService.fileSAR(
                event.getCustomerId(),
                event.getTransactionId(),
                event.getAmount(),
                event.getViolationType(),
                violation.getDescription() + " - System detected compliance violation"
            );
            
            reportIds.add(sarId);
            violation.setSarId(sarId);
            
            log.warn("SAR filed for violation {}: SAR ID {}", event.getViolationId(), sarId);
        }
        
        // File CTR if large cash transaction involved
        if (event.getAmount() != null && 
            event.getAmount().compareTo(new BigDecimal("10000")) > 0 &&
            violation.getReportingRequirements().getOrDefault("CTR_REQUIRED", false)) {
            
            String ctrId = regulatoryReportingService.fileCTR(
                event.getCustomerId(),
                event.getTransactionId(),
                event.getAmount(),
                "USD",
                "COMPLIANCE_VIOLATION_CTR"
            );
            
            reportIds.add(ctrId);
            violation.setCtrId(ctrId);
            
            log.warn("CTR filed for violation {}: CTR ID {}", event.getViolationId(), ctrId);
        }
        
        // File OFAC blocking report if sanctions violation
        if ("OFAC_VIOLATION".equals(event.getViolationType())) {
            String ofacBlockingId = regulatoryReportingService.fileOFACBlocking(
                event.getCustomerId(),
                event.getTransactionId(),
                event.getAmount(),
                "OFAC sanctions match detected",
                violation.getId()
            );
            
            reportIds.add(ofacBlockingId);
            violation.setOfacBlockingId(ofacBlockingId);
            
            log.error("OFAC blocking report filed for violation {}: ID {}", 
                event.getViolationId(), ofacBlockingId);
        }
        
        // File Form 8300 for large cash transactions
        if (event.getAmount() != null && 
            event.getAmount().compareTo(new BigDecimal("10000")) > 0 &&
            "CASH_TRANSACTION".equals(event.getTransactionType())) {
            
            String form8300Id = regulatoryReportingService.fileForm8300(
                event.getCustomerId(),
                event.getAmount(),
                event.getTransactionId(),
                "COMPLIANCE_VIOLATION_8300"
            );
            
            reportIds.add(form8300Id);
            violation.setForm8300Id(form8300Id);
        }
        
        // File FinCEN 314(a) request if money laundering suspected
        if ("AML_VIOLATION".equals(event.getViolationType()) ||
            "MONEY_LAUNDERING_SUSPECTED".equals(event.getViolationType())) {
            
            String fincen314aId = regulatoryReportingService.submitFinCEN314a(
                event.getCustomerId(),
                violation.getDescription(),
                event.getAmount()
            );
            
            reportIds.add(fincen314aId);
            violation.setFincen314aId(fincen314aId);
        }
        
        violation.setRegulatoryReportIds(reportIds);
        violation.setRegulatoryReportsFiledAt(LocalDateTime.now());
        
        complianceViolationRepository.save(violation);
        
        log.warn("Regulatory reports filed for violation {}: {} reports", 
            event.getViolationId(), reportIds.size());
    }
    
    private void performRootCauseAnalysis(ComplianceViolation violation, ComplianceViolationDetectedEvent event) {
        // Analyze transaction patterns leading to violation
        Map<String, Object> patternAnalysis = violationProcessingService.analyzeTransactionPatterns(
            event.getCustomerId(),
            event.getTransactionId(),
            LocalDateTime.now().minusDays(90)
        );
        
        violation.setPatternAnalysisData(patternAnalysis);
        
        // Identify system controls that failed
        List<String> failedControls = violationProcessingService.identifyFailedControls(
            event.getViolationType(),
            event.getSourceSystem(),
            event.getAmount()
        );
        
        violation.setFailedControls(failedControls);
        
        // Check for similar violations across customer base
        List<String> relatedViolations = violationProcessingService.findRelatedViolations(
            event.getViolationType(),
            LocalDateTime.now().minusDays(30)
        );
        
        violation.setRelatedViolationIds(relatedViolations);
        
        // Analyze if this is part of a larger pattern
        boolean partOfLargerPattern = violationProcessingService.detectLargerPattern(
            event.getCustomerId(),
            event.getViolationType(),
            LocalDateTime.now().minusDays(180)
        );
        
        violation.setPartOfLargerPattern(partOfLargerPattern);
        
        if (partOfLargerPattern) {
            violation.addRemediationAction(RemediationAction.COMPREHENSIVE_ACCOUNT_REVIEW);
            violation.addRemediationAction(RemediationAction.ENHANCED_ONGOING_MONITORING);
        }
        
        complianceViolationRepository.save(violation);
        
        log.info("Root cause analysis completed for violation {}: Failed controls: {}, Related violations: {}",
            event.getViolationId(), failedControls.size(), relatedViolations.size());
    }
    
    private void initiateInvestigationAndEvidenceCollection(ComplianceViolation violation, ComplianceViolationDetectedEvent event) {
        // Create investigation case
        String investigationId = violationProcessingService.createInvestigationCase(
            event.getViolationId(),
            event.getCustomerId(),
            violation.getSeverity(),
            violation.getDescription()
        );
        
        violation.setInvestigationId(investigationId);
        
        // Assign investigator based on severity
        String investigatorId = violationProcessingService.assignInvestigator(
            violation.getSeverity(),
            event.getViolationType()
        );
        
        violation.setAssignedInvestigatorId(investigatorId);
        
        // Preserve evidence
        Map<String, Object> evidencePackage = violationProcessingService.collectEvidence(
            event.getCustomerId(),
            event.getTransactionId(),
            event.getAlertId(),
            LocalDateTime.now().minusDays(90)
        );
        
        violation.setEvidencePackage(evidencePackage);
        
        // Create legal hold if required
        if (violation.getSeverity() == ViolationSeverity.CRITICAL) {
            String legalHoldId = violationProcessingService.createLegalHold(
                event.getCustomerId(),
                investigationId,
                "Compliance violation investigation"
            );
            
            violation.setLegalHoldId(legalHoldId);
        }
        
        // Schedule investigation milestones
        List<LocalDateTime> investigationMilestones = violationProcessingService.scheduleInvestigationMilestones(
            investigationId,
            violation.getSeverity()
        );
        
        violation.setInvestigationMilestones(investigationMilestones);
        
        complianceViolationRepository.save(violation);
        
        log.info("Investigation initiated for violation {}: ID {}, Investigator: {}",
            event.getViolationId(), investigationId, investigatorId);
    }
    
    private void implementRemediationActions(ComplianceViolation violation, ComplianceViolationDetectedEvent event) {
        List<RemediationAction> actions = new ArrayList<>();
        
        // Determine required remediation based on violation type
        switch (event.getViolationType()) {
            case "BSA_VIOLATION" -> {
                actions.add(RemediationAction.BSA_TRAINING_REQUIRED);
                actions.add(RemediationAction.ENHANCED_MONITORING_90_DAYS);
                actions.add(RemediationAction.MANAGEMENT_REVIEW);
            }
            case "AML_VIOLATION" -> {
                actions.add(RemediationAction.AML_PROGRAM_REVIEW);
                actions.add(RemediationAction.CUSTOMER_DUE_DILIGENCE_UPDATE);
                actions.add(RemediationAction.ENHANCED_ONGOING_MONITORING);
            }
            case "OFAC_VIOLATION" -> {
                actions.add(RemediationAction.OFAC_COMPLIANCE_REVIEW);
                actions.add(RemediationAction.SANCTIONS_SCREENING_ENHANCEMENT);
                actions.add(RemediationAction.IMMEDIATE_ACCOUNT_CLOSURE);
            }
            case "KYC_VIOLATION" -> {
                actions.add(RemediationAction.KYC_REFRESH_REQUIRED);
                actions.add(RemediationAction.IDENTITY_REVERIFICATION);
                actions.add(RemediationAction.DOCUMENT_UPDATE_REQUIRED);
            }
        }
        
        // Execute immediate remediation actions
        for (RemediationAction action : actions) {
            switch (action) {
                case ENHANCED_MONITORING_90_DAYS -> {
                    customerRestrictionService.enableEnhancedMonitoring(
                        event.getCustomerId(),
                        90, // days
                        violation.getId()
                    );
                }
                case CUSTOMER_DUE_DILIGENCE_UPDATE -> {
                    violationProcessingService.initiateKYCRefresh(
                        event.getCustomerId(),
                        violation.getId()
                    );
                }
                case IMMEDIATE_ACCOUNT_CLOSURE -> {
                    customerRestrictionService.initiateAccountClosure(
                        event.getCustomerId(),
                        "COMPLIANCE_VIOLATION",
                        violation.getId()
                    );
                }
                case SANCTIONS_SCREENING_ENHANCEMENT -> {
                    violationProcessingService.enhanceSanctionsScreening(
                        event.getCustomerId(),
                        violation.getId()
                    );
                }
            }
        }
        
        violation.setRemediationActions(actions);
        violation.setRemediationInitiatedAt(LocalDateTime.now());
        
        // Calculate remediation completion timeline
        LocalDateTime expectedCompletion = violationProcessingService.calculateRemediationTimeline(
            actions,
            violation.getSeverity()
        );
        
        violation.setExpectedRemediationCompletion(expectedCompletion);
        
        complianceViolationRepository.save(violation);
        
        log.warn("Remediation actions implemented for violation {}: {} actions", 
            event.getViolationId(), actions.size());
    }
    
    private void updateRiskProfilesAndMonitoring(ComplianceViolation violation, ComplianceViolationDetectedEvent event) {
        // Update customer risk score
        double newRiskScore = violationProcessingService.updateCustomerRiskScore(
            event.getCustomerId(),
            event.getViolationType(),
            violation.getSeverity()
        );
        
        violation.setUpdatedCustomerRiskScore(newRiskScore);
        
        // Adjust monitoring parameters
        Map<String, Object> monitoringParameters = Map.of(
            "transactionAmountThreshold", new BigDecimal("1000"),
            "velocityLimits", Map.of(
                "daily", new BigDecimal("5000"),
                "monthly", new BigDecimal("25000")
            ),
            "alertSensitivity", "HIGH",
            "reviewFrequency", "WEEKLY"
        );
        
        violationProcessingService.updateMonitoringParameters(
            event.getCustomerId(),
            monitoringParameters,
            violation.getId()
        );
        
        // Update transaction monitoring rules
        violationProcessingService.updateTransactionMonitoringRules(
            event.getCustomerId(),
            event.getViolationType(),
            violation.getSeverity()
        );
        
        complianceViolationRepository.save(violation);
        
        log.info("Risk profiles updated for violation {}: New risk score: {}", 
            event.getViolationId(), newRiskScore);
    }
    
    private void generateRegulatoryExaminationDocumentation(ComplianceViolation violation, ComplianceViolationDetectedEvent event) {
        // Generate comprehensive examination package
        String examinationPackageId = regulatoryReportingService.generateExaminationPackage(
            violation.getId(),
            event.getCustomerId(),
            violation.getSeverity(),
            violation.getRegulatoryReportIds(),
            violation.getRemediationActions()
        );
        
        violation.setExaminationPackageId(examinationPackageId);
        
        // Create timeline of events
        Map<String, Object> eventTimeline = violationProcessingService.createEventTimeline(
            event.getCustomerId(),
            event.getTransactionId(),
            event.getDetectedAt(),
            LocalDateTime.now().minusDays(30)
        );
        
        violation.setEventTimeline(eventTimeline);
        
        // Document control failures and gaps
        List<String> controlGaps = violationProcessingService.documentControlGaps(
            violation.getFailedControls(),
            event.getSourceSystem()
        );
        
        violation.setIdentifiedControlGaps(controlGaps);
        
        complianceViolationRepository.save(violation);
        
        log.info("Regulatory examination documentation generated for violation {}: Package ID {}",
            event.getViolationId(), examinationPackageId);
    }
    
    private void notifyRegulatoryAuthorities(ComplianceViolation violation, ComplianceViolationDetectedEvent event) {
        // Notify FinCEN for BSA/AML violations
        if (violation.getRegulatoryFrameworks().contains("BSA") ||
            violation.getRegulatoryFrameworks().contains("AML")) {
            
            String fincenNotificationId = regulatoryReportingService.notifyFinCEN(
                violation.getId(),
                event.getViolationType(),
                violation.getSeverity(),
                violation.getSarId()
            );
            
            violation.setFincenNotificationId(fincenNotificationId);
        }
        
        // Notify OFAC for sanctions violations
        if ("OFAC_VIOLATION".equals(event.getViolationType())) {
            String ofacNotificationId = regulatoryReportingService.notifyOFAC(
                violation.getId(),
                event.getCustomerId(),
                event.getAmount(),
                violation.getOfacBlockingId()
            );
            
            violation.setOfacNotificationId(ofacNotificationId);
        }
        
        // Notify primary federal regulator
        String primaryRegulator = regulatoryReportingService.determinePrimaryRegulator();
        
        String regulatorNotificationId = regulatoryReportingService.notifyPrimaryRegulator(
            primaryRegulator,
            violation.getId(),
            violation.getSeverity(),
            violation.getExaminationPackageId()
        );
        
        violation.setRegulatorNotificationId(regulatorNotificationId);
        
        complianceViolationRepository.save(violation);
        
        log.warn("Regulatory authorities notified for violation {}: FinCEN: {}, OFAC: {}, Primary: {}",
            event.getViolationId(), violation.getFincenNotificationId(), 
            violation.getOfacNotificationId(), regulatorNotificationId);
    }
    
    private void sendInternalAlertsAndEscalations(ComplianceViolation violation, ComplianceViolationDetectedEvent event) {
        // Send immediate alert to compliance team
        notificationService.sendComplianceTeamAlert(
            violation.getId(),
            event.getViolationType(),
            violation.getSeverity(),
            event.getCustomerId(),
            event.getAmount()
        );
        
        // Escalate to senior management for critical violations
        if (violation.getSeverity() == ViolationSeverity.CRITICAL) {
            notificationService.sendSeniorManagementEscalation(
                violation.getId(),
                event.getViolationType(),
                event.getCustomerId(),
                violation.getPotentialFine(),
                violation.getRegulatoryReportIds()
            );
        }
        
        // Notify BSA Officer
        notificationService.notifyBSAOfficer(
            violation.getId(),
            event.getViolationType(),
            violation.getSeverity(),
            violation.getSarId()
        );
        
        // Alert AML team if applicable
        if (violation.getRegulatoryFrameworks().contains("AML")) {
            notificationService.alertAMLTeam(
                violation.getId(),
                event.getCustomerId(),
                event.getTransactionId(),
                violation.getInvestigationId()
            );
        }
        
        // Send 24-hour reminder for critical actions
        if (violation.getSeverity() == ViolationSeverity.CRITICAL) {
            notificationService.schedule24HourReminder(
                violation.getId(),
                violation.getAssignedInvestigatorId(),
                "Critical compliance violation requires immediate attention"
            );
        }
        
        log.warn("Internal alerts sent for violation {}: Severity {}", 
            event.getViolationId(), violation.getSeverity());
    }
    
    private void createComprehensiveAuditTrail(ComplianceViolation violation, ComplianceViolationDetectedEvent event) {
        // Create detailed audit record
        Map<String, Object> auditData = Map.of(
            "violationId", violation.getId(),
            "customerId", event.getCustomerId(),
            "violationType", event.getViolationType(),
            "severity", violation.getSeverity(),
            "detectedAt", event.getDetectedAt(),
            "processedAt", LocalDateTime.now(),
            "restrictionsApplied", violation.getRestrictionsApplied(),
            "regulatoryReports", violation.getRegulatoryReportIds(),
            "remediationActions", violation.getRemediationActions(),
            "investigationId", violation.getInvestigationId(),
            "potentialFine", violation.getPotentialFine()
        );
        
        String auditId = auditService.createComplianceAuditRecord(
            "COMPLIANCE_VIOLATION_PROCESSED",
            auditData,
            "SYSTEM"
        );
        
        violation.setAuditRecordId(auditId);
        
        // Create immutable evidence chain
        String evidenceChainId = auditService.createEvidenceChain(
            violation.getId(),
            violation.getEvidencePackage(),
            violation.getEventTimeline()
        );
        
        violation.setEvidenceChainId(evidenceChainId);
        
        complianceViolationRepository.save(violation);
        
        log.info("Comprehensive audit trail created for violation {}: Audit ID {}, Evidence chain: {}",
            event.getViolationId(), auditId, evidenceChainId);
    }
    
    private void applyEmergencyComplianceControls(ComplianceViolationDetectedEvent event, Exception originalException) {
        try {
            log.error("EMERGENCY: Applying emergency compliance controls for violation: {}", 
                event.getViolationId());
            
            // Emergency customer freeze
            customerRestrictionService.emergencyFreezeCustomer(
                event.getCustomerId(),
                "COMPLIANCE_PROCESSING_FAILURE"
            );
            
            // Emergency transaction hold
            if (event.getTransactionId() != null) {
                customerRestrictionService.emergencyHoldTransaction(
                    event.getTransactionId(),
                    "VIOLATION_PROCESSING_FAILURE"
                );
            }
            
            // Emergency compliance alert
            notificationService.sendEmergencyComplianceAlert(
                event.getViolationId(),
                event.getCustomerId(),
                event.getViolationType(),
                "Compliance violation processing failure - immediate manual intervention required"
            );
            
            // Emergency regulatory notification
            regulatoryReportingService.fileEmergencyNotification(
                event.getCustomerId(),
                event.getViolationType(),
                "System failure during compliance violation processing",
                originalException.getMessage()
            );
            
        } catch (Exception e) {
            log.error("Failed to apply emergency compliance controls", e);
        }
    }
}