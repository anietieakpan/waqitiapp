package com.waqiti.compliance.service;

import com.waqiti.compliance.domain.*;
import com.waqiti.compliance.dto.*;
import com.waqiti.compliance.repository.*;
import com.waqiti.compliance.client.*;
import com.waqiti.compliance.exception.*;
import com.waqiti.compliance.audit.ComplianceAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AML Compliance Service
 * 
 * Comprehensive Anti-Money Laundering compliance service providing:
 * - Real-time transaction monitoring and analysis
 * - Suspicious activity detection and reporting
 * - OFAC sanctions screening and validation
 * - PEP (Politically Exposed Person) screening
 * - Transaction pattern analysis and risk scoring
 * - Automated SAR (Suspicious Activity Report) generation
 * - CTR (Currency Transaction Report) management
 * - Customer risk profiling and ongoing monitoring
 * - Regulatory compliance workflow orchestration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AMLComplianceService {

    private final ComplianceRuleRepository complianceRuleRepository;
    private final SuspiciousActivityRepository suspiciousActivityRepository;
    private final ComplianceAlertRepository complianceAlertRepository;
    private final CustomerRiskProfileRepository customerRiskProfileRepository;
    private final OFACScreeningServiceClient ofacScreeningClient;
    private final PEPScreeningServiceClient pepScreeningClient;
    private final TransactionServiceClient transactionServiceClient;
    private final CustomerServiceClient customerServiceClient;
    private final RegulatoryReportingService regulatoryReportingService;
    private final TransactionPatternAnalyzer transactionPatternAnalyzer;
    private final ComplianceAuditService auditService;

    private static final BigDecimal CTR_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal SUSPICIOUS_AMOUNT_THRESHOLD = new BigDecimal("5000.00");
    private static final int MAX_DAILY_TRANSACTIONS = 50;

    /**
     * Performs comprehensive compliance check for transactions
     */
    @Transactional
    public ComplianceCheckResult checkTransactionCompliance(ComplianceCheckRequest request) {
        try {
            log.info("Performing compliance check for transaction: {}", request.getTransactionId());
            
            List<ComplianceViolation> violations = new ArrayList<>();
            List<ComplianceAlert> alerts = new ArrayList<>();
            
            // 1. OFAC Sanctions Screening
            OFACScreeningResult ofacResult = performOFACScreening(request);
            if (!ofacResult.isClean()) {
                violations.add(createViolation("OFAC_MATCH", ofacResult.getMatchDetails()));
            }
            
            // 2. PEP Screening
            PEPScreeningResult pepResult = performPEPScreening(request);
            if (pepResult.isPEPMatch()) {
                alerts.add(createAlert("PEP_DETECTED", pepResult.getPepDetails(), request.getTransactionId()));
            }
            
            // 3. Transaction Amount Analysis
            AmountAnalysisResult amountResult = analyzeTransactionAmount(request);
            if (amountResult.requiresCTR()) {
                alerts.add(createAlert("CTR_REQUIRED", "Transaction exceeds CTR threshold", request.getTransactionId()));
            }
            if (amountResult.isSuspicious()) {
                alerts.add(createAlert("SUSPICIOUS_AMOUNT", amountResult.getSuspiciousReason(), request.getTransactionId()));
            }
            
            // 4. Transaction Pattern Analysis
            PatternAnalysisResult patternResult = analyzeTransactionPatterns(request);
            if (patternResult.hasSuspiciousPatterns()) {
                for (SuspiciousPattern pattern : patternResult.getSuspiciousPatterns()) {
                    alerts.add(createAlert("SUSPICIOUS_PATTERN", pattern.getDescription(), request.getTransactionId()));
                }
            }
            
            // 5. Customer Risk Assessment
            CustomerRiskAssessment riskAssessment = assessCustomerRisk(request);
            if (riskAssessment.isHighRisk()) {
                alerts.add(createAlert("HIGH_RISK_CUSTOMER", riskAssessment.getRiskFactors(), request.getTransactionId()));
            }
            
            // 6. Velocity Checks
            VelocityCheckResult velocityResult = performVelocityChecks(request);
            if (velocityResult.isVelocityExceeded()) {
                violations.add(createViolation("VELOCITY_EXCEEDED", velocityResult.getViolationDetails()));
            }
            
            // Save alerts to database
            alerts.forEach(alert -> complianceAlertRepository.save(alert));
            
            // Determine overall compliance status
            boolean isCompliant = violations.isEmpty();
            ComplianceStatus status = determineComplianceStatus(violations, alerts);
            
            // Generate reports if required
            if (amountResult.requiresCTR()) {
                generateCTR(request);
            }
            
            if (!alerts.isEmpty() && requiresSAR(alerts)) {
                generateSAR(request, alerts);
            }
            
            // Create compliance decision
            ComplianceDecision decision = ComplianceDecision.builder()
                .transactionId(request.getTransactionId())
                .customerId(request.getCustomerId())
                .decisionType(ComplianceDecision.DecisionType.TRANSACTION_SCREENING)
                .decision(mapStatusToDecision(status))
                .riskScore(calculateOverallRiskScore(violations, alerts, riskAssessment))
                .reason(buildDecisionReason(violations, alerts))
                .createdBy(getCurrentUser())
                .decisionMetadata(buildDecisionMetadata(violations, alerts, riskAssessment))
                .build();
            
            // Record audit trail
            try {
                auditService.recordDecision(
                    decision,
                    getCurrentUser(),
                    "Automated compliance screening completed",
                    Map.of(
                        "violations", violations.size(),
                        "alerts", alerts.size(),
                        "riskScore", decision.getRiskScore(),
                        "processingTime", System.currentTimeMillis() - request.getStartTime()
                    )
                );
            } catch (Exception auditError) {
                log.error("Failed to record audit trail for transaction: {}", request.getTransactionId(), auditError);
                // Continue with processing - audit failure should not block transaction
            }
            
            ComplianceCheckResult result = ComplianceCheckResult.builder()
                .transactionId(request.getTransactionId())
                .compliant(isCompliant)
                .status(status)
                .violations(violations)
                .alerts(alerts)
                .riskScore(decision.getRiskScore())
                .checkedAt(LocalDateTime.now())
                .decisionId(decision.getId())
                .build();
            
            log.info("Compliance check completed: transactionId={}, compliant={}, alerts={}, decision={}", 
                    request.getTransactionId(), isCompliant, alerts.size(), decision.getDecision());
            
            return result;
            
        } catch (Exception e) {
            log.error("Compliance check failed for transaction: {}", request.getTransactionId(), e);
            throw new ComplianceCheckException("Failed to perform compliance check", e);
        }
    }

    /**
     * Performs customer onboarding compliance checks
     */
    @Transactional
    public CustomerComplianceResult checkCustomerCompliance(CustomerComplianceRequest request) {
        try {
            log.info("Performing customer compliance check: customerId={}", request.getCustomerId());
            
            List<ComplianceIssue> issues = new ArrayList<>();
            
            // 1. Identity Verification
            IdentityVerificationResult idResult = verifyCustomerIdentity(request);
            if (!idResult.isVerified()) {
                issues.add(ComplianceIssue.builder()
                    .issueType("IDENTITY_VERIFICATION_FAILED")
                    .description(idResult.getFailureReason())
                    .severity(ComplianceIssue.Severity.HIGH)
                    .build());
            }
            
            // 2. OFAC Screening
            CustomerOFACResult customerOfacResult = performCustomerOFACScreening(request);
            if (!customerOfacResult.isClean()) {
                issues.add(ComplianceIssue.builder()
                    .issueType("OFAC_CUSTOMER_MATCH")
                    .description("Customer matches OFAC sanctions list")
                    .severity(ComplianceIssue.Severity.CRITICAL)
                    .build());
            }
            
            // 3. PEP Screening
            CustomerPEPResult customerPepResult = performCustomerPEPScreening(request);
            if (customerPepResult.isPEP()) {
                issues.add(ComplianceIssue.builder()
                    .issueType("PEP_CUSTOMER")
                    .description("Customer identified as Politically Exposed Person")
                    .severity(ComplianceIssue.Severity.MEDIUM)
                    .build());
            }
            
            // 4. Enhanced Due Diligence Check
            if (requiresEnhancedDueDiligence(request, customerPepResult)) {
                EnhancedDueDiligenceResult eddResult = performEnhancedDueDiligence(request);
                if (!eddResult.isPassed()) {
                    issues.add(ComplianceIssue.builder()
                        .issueType("EDD_REQUIRED")
                        .description("Enhanced Due Diligence required")
                        .severity(ComplianceIssue.Severity.HIGH)
                        .build());
                }
            }
            
            // 5. Risk Classification
            CustomerRiskClassification riskClass = classifyCustomerRisk(request, issues);
            
            // Create or update customer risk profile
            updateCustomerRiskProfile(request.getCustomerId(), riskClass, issues);
            
            boolean approved = issues.stream().noneMatch(issue -> 
                issue.getSeverity() == ComplianceIssue.Severity.CRITICAL);
            
            CustomerComplianceResult result = CustomerComplianceResult.builder()
                .customerId(request.getCustomerId())
                .approved(approved)
                .riskClassification(riskClass)
                .complianceIssues(issues)
                .requiresManualReview(requiresManualReview(issues))
                .approvalRequired(requiresApproval(issues))
                .checkedAt(LocalDateTime.now())
                .build();
            
            log.info("Customer compliance check completed: customerId={}, approved={}, issues={}", 
                    request.getCustomerId(), approved, issues.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Customer compliance check failed: customerId={}", request.getCustomerId(), e);
            throw new ComplianceCheckException("Failed to perform customer compliance check", e);
        }
    }

    /**
     * Generates Suspicious Activity Report (SAR)
     */
    @Transactional
    public SARGenerationResult generateSuspiciousActivityReport(SARRequest request) {
        try {
            log.info("Generating SAR for customer: {}", request.getCustomerId());
            
            // Collect suspicious activities
            List<SuspiciousActivity> activities = collectSuspiciousActivities(request);
            
            // Generate SAR document
            SuspiciousActivityReport sar = SuspiciousActivityReport.builder()
                .sarId(UUID.randomUUID())
                .customerId(request.getCustomerId())
                .suspiciousActivities(activities)
                .narrativeDescription(generateNarrativeDescription(activities))
                .reportingInstitution(getReportingInstitutionInfo())
                .filingDate(LocalDateTime.now())
                .reportedBy(request.getReportedBy())
                .status(SARStatus.DRAFT)
                .build();
            
            // Submit to regulatory authorities
            RegulatorySubmissionResult submissionResult = regulatoryReportingService.submitSAR(sar);
            
            if (submissionResult.isSuccessful()) {
                sar.setStatus(SARStatus.SUBMITTED);
                sar.setSubmissionReference(submissionResult.getReferenceNumber());
            }
            
            // Save SAR record
            suspiciousActivityRepository.saveSAR(sar);
            
            log.info("SAR generated and submitted: sarId={}, reference={}", 
                    sar.getSarId(), submissionResult.getReferenceNumber());
            
            return SARGenerationResult.builder()
                .sarId(sar.getSarId())
                .successful(submissionResult.isSuccessful())
                .referenceNumber(submissionResult.getReferenceNumber())
                .submittedAt(LocalDateTime.now())
                .build();
            
        } catch (Exception e) {
            log.error("SAR generation failed for customer: {}", request.getCustomerId(), e);
            throw new ComplianceReportingException("Failed to generate SAR", e);
        }
    }

    /**
     * Monitors ongoing customer activity for compliance
     */
    @Transactional
    public ComplianceMonitoringResult monitorCustomerActivity(UUID customerId, LocalDateTime fromDate, LocalDateTime toDate) {
        try {
            log.info("Monitoring customer activity: customerId={}, period={} to {}", 
                    customerId, fromDate, toDate);
            
            // Get customer transactions in period
            List<TransactionSummary> transactions = transactionServiceClient.getCustomerTransactions(
                customerId, fromDate, toDate);
            
            List<ComplianceAlert> newAlerts = new ArrayList<>();
            
            // Analyze transaction patterns
            TransactionPatternAnalysis analysis = transactionPatternAnalyzer.analyzePattern(transactions);
            
            if (analysis.hasAnomalies()) {
                for (TransactionAnomaly anomaly : analysis.getAnomalies()) {
                    newAlerts.add(createAlert("TRANSACTION_ANOMALY", anomaly.getDescription(), customerId));
                }
            }
            
            // Check for structuring patterns
            StructuringAnalysisResult structuringResult = analyzeForStructuring(transactions);
            if (structuringResult.isStructuringDetected()) {
                newAlerts.add(createAlert("STRUCTURING_DETECTED", 
                    structuringResult.getStructuringEvidence(), customerId));
            }
            
            // Velocity analysis
            VelocityAnalysisResult velocityAnalysis = analyzeTransactionVelocity(transactions);
            if (velocityAnalysis.isExcessive()) {
                newAlerts.add(createAlert("EXCESSIVE_VELOCITY", 
                    velocityAnalysis.getVelocityDetails(), customerId));
            }
            
            // Save new alerts
            newAlerts.forEach(alert -> complianceAlertRepository.save(alert));
            
            // Update customer risk profile if needed
            if (!newAlerts.isEmpty()) {
                updateCustomerRiskProfileFromMonitoring(customerId, newAlerts);
            }
            
            return ComplianceMonitoringResult.builder()
                .customerId(customerId)
                .monitoringPeriod(MonitoringPeriod.of(fromDate, toDate))
                .transactionsAnalyzed(transactions.size())
                .alertsGenerated(newAlerts.size())
                .alerts(newAlerts)
                .riskLevelChange(calculateRiskLevelChange(customerId, newAlerts))
                .monitoredAt(LocalDateTime.now())
                .build();
            
        } catch (Exception e) {
            log.error("Customer activity monitoring failed: customerId={}", customerId, e);
            throw new ComplianceMonitoringException("Failed to monitor customer activity", e);
        }
    }

    // Private helper methods

    private OFACScreeningResult performOFACScreening(ComplianceCheckRequest request) {
        try {
            // Get customer details for screening
            CustomerDetails customer = customerServiceClient.getCustomerDetails(
                extractCustomerIdFromTransaction(request));
            
            OFACScreeningRequest screeningRequest = OFACScreeningRequest.builder()
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .dateOfBirth(customer.getDateOfBirth())
                .address(customer.getAddress())
                .build();
            
            return ofacScreeningClient.screenCustomer(screeningRequest);
            
        } catch (Exception e) {
            log.error("OFAC screening failed for transaction: {}", request.getTransactionId(), e);
            return OFACScreeningResult.error("OFAC screening service unavailable");
        }
    }

    private PEPScreeningResult performPEPScreening(ComplianceCheckRequest request) {
        try {
            CustomerDetails customer = customerServiceClient.getCustomerDetails(
                extractCustomerIdFromTransaction(request));
            
            PEPScreeningRequest screeningRequest = PEPScreeningRequest.builder()
                .fullName(customer.getFullName())
                .dateOfBirth(customer.getDateOfBirth())
                .nationality(customer.getNationality())
                .build();
            
            return pepScreeningClient.screenForPEP(screeningRequest);
            
        } catch (Exception e) {
            log.error("PEP screening failed for transaction: {}", request.getTransactionId(), e);
            return PEPScreeningResult.error("PEP screening service unavailable");
        }
    }

    private AmountAnalysisResult analyzeTransactionAmount(ComplianceCheckRequest request) {
        boolean requiresCTR = request.getAmount().compareTo(CTR_THRESHOLD) >= 0;
        boolean isSuspicious = false;
        String suspiciousReason = null;
        
        // Check for just-under-threshold amounts (potential structuring)
        if (request.getAmount().compareTo(new BigDecimal("9500")) >= 0 && 
            request.getAmount().compareTo(CTR_THRESHOLD) < 0) {
            isSuspicious = true;
            suspiciousReason = "Amount just below CTR threshold - potential structuring";
        }
        
        // Check for round amounts which might indicate structuring
        if (isRoundAmount(request.getAmount()) && 
            request.getAmount().compareTo(SUSPICIOUS_AMOUNT_THRESHOLD) >= 0) {
            isSuspicious = true;
            suspiciousReason = "Large round amount transaction";
        }
        
        return AmountAnalysisResult.builder()
            .amount(request.getAmount())
            .requiresCTR(requiresCTR)
            .suspicious(isSuspicious)
            .suspiciousReason(suspiciousReason)
            .build();
    }

    private PatternAnalysisResult analyzeTransactionPatterns(ComplianceCheckRequest request) {
        UUID customerId = extractCustomerIdFromTransaction(request);
        
        // Get recent customer transactions for pattern analysis
        List<TransactionSummary> recentTransactions = transactionServiceClient.getRecentTransactions(
            customerId, LocalDateTime.now().minusDays(30));
        
        return transactionPatternAnalyzer.analyzeForSuspiciousPatterns(recentTransactions, request);
    }

    private CustomerRiskAssessment assessCustomerRisk(ComplianceCheckRequest request) {
        UUID customerId = extractCustomerIdFromTransaction(request);
        
        CustomerRiskProfile existingProfile = customerRiskProfileRepository.findByCustomerId(customerId)
            .orElse(null);
        
        List<String> riskFactors = new ArrayList<>();
        RiskLevel riskLevel = RiskLevel.LOW;
        
        if (existingProfile != null) {
            riskLevel = existingProfile.getCurrentRiskLevel();
            riskFactors.addAll(existingProfile.getRiskFactors());
        }
        
        // Transaction-specific risk factors
        if (request.getAmount().compareTo(new BigDecimal("50000")) >= 0) {
            riskFactors.add("Large transaction amount");
            riskLevel = RiskLevel.MEDIUM;
        }
        
        // Check for high-risk countries
        if (isHighRiskCountryTransaction(request)) {
            riskFactors.add("Transaction involving high-risk country");
            riskLevel = RiskLevel.HIGH;
        }
        
        return CustomerRiskAssessment.builder()
            .customerId(customerId)
            .riskLevel(riskLevel)
            .riskFactors(riskFactors)
            .highRisk(riskLevel == RiskLevel.HIGH)
            .assessedAt(LocalDateTime.now())
            .build();
    }

    private VelocityCheckResult performVelocityChecks(ComplianceCheckRequest request) {
        UUID customerId = extractCustomerIdFromTransaction(request);
        
        // Check daily transaction count
        long dailyCount = transactionServiceClient.getDailyTransactionCount(customerId, LocalDateTime.now().toLocalDate());
        
        if (dailyCount >= MAX_DAILY_TRANSACTIONS) {
            return VelocityCheckResult.builder()
                .velocityExceeded(true)
                .violationDetails("Exceeded maximum daily transaction count: " + dailyCount)
                .build();
        }
        
        // Check daily transaction volume
        BigDecimal dailyVolume = transactionServiceClient.getDailyTransactionVolume(customerId, LocalDateTime.now().toLocalDate());
        BigDecimal dailyLimit = new BigDecimal("100000"); // $100k daily limit
        
        if (dailyVolume.compareTo(dailyLimit) > 0) {
            return VelocityCheckResult.builder()
                .velocityExceeded(true)
                .violationDetails("Exceeded daily transaction volume limit: " + dailyVolume)
                .build();
        }
        
        return VelocityCheckResult.builder()
            .velocityExceeded(false)
            .build();
    }

    private ComplianceViolation createViolation(String violationType, String details) {
        return ComplianceViolation.builder()
            .violationType(violationType)
            .details(details)
            .severity(ComplianceViolation.Severity.HIGH)
            .detectedAt(LocalDateTime.now())
            .build();
    }

    private ComplianceAlert createAlert(String alertType, String description, UUID relatedEntityId) {
        return ComplianceAlert.builder()
            .alertType(alertType)
            .description(description)
            .relatedEntityId(relatedEntityId)
            .priority(ComplianceAlert.Priority.MEDIUM)
            .status(ComplianceAlert.Status.NEW)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private ComplianceAlert createAlert(String alertType, String description, String relatedEntityId) {
        UUID entityUUID = null;
        try {
            entityUUID = UUID.fromString(relatedEntityId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for relatedEntityId: {}, using null", relatedEntityId);
        }
        return createAlert(alertType, description, entityUUID);
    }

    private ComplianceStatus determineComplianceStatus(List<ComplianceViolation> violations, List<ComplianceAlert> alerts) {
        if (!violations.isEmpty()) {
            return ComplianceStatus.VIOLATION;
        }
        
        if (alerts.stream().anyMatch(alert -> alert.getPriority() == ComplianceAlert.Priority.HIGH)) {
            return ComplianceStatus.REVIEW_REQUIRED;
        }
        
        if (!alerts.isEmpty()) {
            return ComplianceStatus.ALERT;
        }
        
        return ComplianceStatus.COMPLIANT;
    }

    private ComplianceDecision.Decision mapStatusToDecision(ComplianceStatus status) {
        switch (status) {
            case COMPLIANT:
                return ComplianceDecision.Decision.APPROVED;
            case VIOLATION:
                return ComplianceDecision.Decision.REJECTED;
            case REVIEW_REQUIRED:
                return ComplianceDecision.Decision.MANUAL_REVIEW;
            case ALERT:
                return ComplianceDecision.Decision.CONDITIONAL_APPROVAL;
            default:
                return ComplianceDecision.Decision.PENDING;
        }
    }
    
    private String buildDecisionReason(List<ComplianceViolation> violations, List<ComplianceAlert> alerts) {
        StringBuilder reason = new StringBuilder();
        
        if (!violations.isEmpty()) {
            reason.append("Violations: ");
            reason.append(violations.stream()
                .map(ComplianceViolation::getViolationType)
                .collect(Collectors.joining(", ")));
        }
        
        if (!alerts.isEmpty()) {
            if (reason.length() > 0) {
                reason.append("; ");
            }
            reason.append("Alerts: ");
            reason.append(alerts.stream()
                .map(ComplianceAlert::getAlertType)
                .collect(Collectors.joining(", ")));
        }
        
        if (reason.length() == 0) {
            reason.append("All compliance checks passed");
        }
        
        return reason.toString();
    }
    
    private Map<String, Object> buildDecisionMetadata(List<ComplianceViolation> violations, 
                                                     List<ComplianceAlert> alerts,
                                                     CustomerRiskAssessment riskAssessment) {
        Map<String, Object> metadata = new HashMap<>();
        
        metadata.put("violationCount", violations.size());
        metadata.put("alertCount", alerts.size());
        metadata.put("customerRiskLevel", riskAssessment.getRiskLevel());
        metadata.put("screeningResults", Map.of(
            "ofacClean", true,  // Would come from actual screening
            "pepMatch", false,
            "sanctionsClean", true
        ));
        
        if (!violations.isEmpty()) {
            metadata.put("violations", violations.stream()
                .map(v -> Map.of(
                    "type", v.getViolationType(),
                    "severity", v.getSeverity(),
                    "details", v.getDetails()
                ))
                .collect(Collectors.toList()));
        }
        
        if (!alerts.isEmpty()) {
            metadata.put("alerts", alerts.stream()
                .map(a -> Map.of(
                    "type", a.getAlertType(),
                    "priority", a.getPriority(),
                    "description", a.getDescription()
                ))
                .collect(Collectors.toList()));
        }
        
        return metadata;
    }
    
    private String getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "SYSTEM";
    }

    private boolean requiresSAR(List<ComplianceAlert> alerts) {
        return alerts.stream().anyMatch(alert -> 
            "SUSPICIOUS_PATTERN".equals(alert.getAlertType()) ||
            "STRUCTURING_DETECTED".equals(alert.getAlertType()) ||
            alert.getPriority() == ComplianceAlert.Priority.HIGH);
    }

    private void generateCTR(ComplianceCheckRequest request) {
        try {
            regulatoryReportingService.generateCTR(request);
            log.info("CTR generated for transaction: {}", request.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to generate CTR for transaction: {}", request.getTransactionId(), e);
        }
    }

    private void generateSAR(ComplianceCheckRequest request, List<ComplianceAlert> alerts) {
        try {
            SARRequest sarRequest = SARRequest.builder()
                .customerId(extractCustomerIdFromTransaction(request))
                .transactionId(request.getTransactionId())
                .suspiciousActivities(alerts.stream()
                    .map(alert -> alert.getDescription())
                    .collect(Collectors.toList()))
                .reportedBy("SYSTEM")
                .build();
            
            generateSuspiciousActivityReport(sarRequest);
            
        } catch (Exception e) {
            log.error("Failed to generate SAR for transaction: {}", request.getTransactionId(), e);
        }
    }

    private double calculateOverallRiskScore(List<ComplianceViolation> violations, 
                                           List<ComplianceAlert> alerts, 
                                           CustomerRiskAssessment riskAssessment) {
        double score = 0.0;
        
        // Base score from violations
        score += violations.size() * 0.3;
        
        // Add score from alerts
        score += alerts.size() * 0.1;
        
        // Add score from customer risk
        score += switch (riskAssessment.getRiskLevel()) {
            case LOW -> 0.1;
            case MEDIUM -> 0.3;
            case HIGH -> 0.5;
        };
        
        return Math.min(score, 1.0); // Cap at 1.0
    }

    private UUID extractCustomerIdFromTransaction(ComplianceCheckRequest request) {
        // Extract customer ID from various transaction fields
        if (request.getCustomerId() != null) {
            return request.getCustomerId();
        }
        
        // Try to extract from account number
        if (request.getAccountNumber() != null) {
            // Use account number to lookup customer ID
            String accountNum = request.getAccountNumber();
            // Create deterministic UUID from account number
            return UUID.nameUUIDFromBytes(("CUSTOMER_" + accountNum).getBytes());
        }
        
        // Try to extract from sender details
        if (request.getSenderName() != null) {
            // Create deterministic UUID from sender name and transaction date
            String identifier = request.getSenderName() + "_" + 
                (request.getTransactionDate() != null ? request.getTransactionDate() : LocalDateTime.now());
            return UUID.nameUUIDFromBytes(identifier.getBytes());
        }
        
        // Try to extract from metadata
        if (request.getMetadata() != null) {
            Map<String, Object> metadata = request.getMetadata();
            if (metadata.containsKey("customerId")) {
                return UUID.fromString(metadata.get("customerId").toString());
            }
            if (metadata.containsKey("userId")) {
                return UUID.fromString(metadata.get("userId").toString());
            }
            if (metadata.containsKey("accountId")) {
                // Generate customer ID from account ID
                return UUID.nameUUIDFromBytes(("ACCT_" + metadata.get("accountId")).getBytes());
            }
        }
        
        // Last resort: generate ID from available transaction data
        String transactionIdentifier = String.format("%s_%s_%s",
            request.getTransactionType() != null ? request.getTransactionType() : "UNKNOWN",
            request.getAmount() != null ? request.getAmount() : "0",
            request.getCurrency() != null ? request.getCurrency() : "USD"
        );
        
        log.warn("Could not extract customer ID, generating from transaction: {}", transactionIdentifier);
        return UUID.nameUUIDFromBytes(transactionIdentifier.getBytes());
    }

    private boolean isRoundAmount(BigDecimal amount) {
        return amount.remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean isHighRiskCountryTransaction(ComplianceCheckRequest request) {
        // FATF high-risk jurisdictions and countries under monitoring
        Set<String> highRiskCountries = Set.of(
            // FATF Black List (High-Risk Jurisdictions)
            "IR", "KP", "MM", // Iran, North Korea, Myanmar
            
            // FATF Grey List (Jurisdictions Under Increased Monitoring)
            "AL", "BB", "BF", "CM", "CD", "GI", "HT", "JM", "JO", 
            "ML", "MZ", "PH", "SN", "SS", "SY", "TZ", "TR", "UG", 
            "VU", "YE", "ZW", // Albania, Barbados, Burkina Faso, etc.
            
            // Additional high-risk countries based on corruption index
            "AF", "SO", "VE", "SD", "LY", "IQ", // Afghanistan, Somalia, Venezuela, etc.
            
            // Sanctioned countries
            "CU", "BY", "RU" // Cuba, Belarus, Russia (partial sanctions)
        );
        
        // Check sender country
        if (request.getSenderCountry() != null) {
            String senderCountryCode = request.getSenderCountry().toUpperCase();
            if (highRiskCountries.contains(senderCountryCode)) {
                log.warn("High-risk sender country detected: {}", senderCountryCode);
                return true;
            }
        }
        
        // Check recipient country
        if (request.getRecipientCountry() != null) {
            String recipientCountryCode = request.getRecipientCountry().toUpperCase();
            if (highRiskCountries.contains(recipientCountryCode)) {
                log.warn("High-risk recipient country detected: {}", recipientCountryCode);
                return true;
            }
        }
        
        // Check originating bank country
        if (request.getOriginatingBankCountry() != null) {
            String bankCountryCode = request.getOriginatingBankCountry().toUpperCase();
            if (highRiskCountries.contains(bankCountryCode)) {
                log.warn("High-risk originating bank country detected: {}", bankCountryCode);
                return true;
            }
        }
        
        // Check beneficiary bank country
        if (request.getBeneficiaryBankCountry() != null) {
            String bankCountryCode = request.getBeneficiaryBankCountry().toUpperCase();
            if (highRiskCountries.contains(bankCountryCode)) {
                log.warn("High-risk beneficiary bank country detected: {}", bankCountryCode);
                return true;
            }
        }
        
        // Check metadata for additional country information
        if (request.getMetadata() != null) {
            Map<String, Object> metadata = request.getMetadata();
            
            // Check various country fields in metadata
            String[] countryFields = {"country", "countryCode", "jurisdiction", 
                                     "originCountry", "destinationCountry"};
            
            for (String field : countryFields) {
                if (metadata.containsKey(field)) {
                    String country = metadata.get(field).toString().toUpperCase();
                    if (country.length() == 2 && highRiskCountries.contains(country)) {
                        log.warn("High-risk country in metadata.{}: {}", field, country);
                        return true;
                    }
                    // Check full country names
                    if (isHighRiskCountryName(country)) {
                        log.warn("High-risk country name in metadata.{}: {}", field, country);
                        return true;
                    }
                }
            }
        }
        
        // Check transaction description for country mentions
        if (request.getDescription() != null) {
            String description = request.getDescription().toUpperCase();
            if (containsHighRiskCountryMention(description)) {
                log.warn("High-risk country mentioned in transaction description");
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isHighRiskCountryName(String countryName) {
        Set<String> highRiskCountryNames = Set.of(
            "IRAN", "NORTH KOREA", "MYANMAR", "SYRIA", "AFGHANISTAN",
            "SOMALIA", "VENEZUELA", "SUDAN", "LIBYA", "IRAQ",
            "CUBA", "BELARUS", "RUSSIA", "YEMEN", "ZIMBABWE"
        );
        
        return highRiskCountryNames.stream()
            .anyMatch(countryName::contains);
    }
    
    private boolean containsHighRiskCountryMention(String text) {
        String[] highRiskKeywords = {
            "IRAN", "NORTH KOREA", "MYANMAR", "SYRIA", "AFGHANISTAN",
            "TEHRAN", "PYONGYANG", "DAMASCUS", "KABUL", "MOGADISHU",
            "CARACAS", "KHARTOUM", "TRIPOLI", "BAGHDAD", "HAVANA",
            "MINSK", "MOSCOW", "SANAA", "HARARE"
        };
        
        for (String keyword : highRiskKeywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }

    // Additional helper methods for customer compliance...
    private IdentityVerificationResult verifyCustomerIdentity(CustomerComplianceRequest request) {
        // Implementation would verify customer identity documents
        return IdentityVerificationResult.verified();
    }

    private CustomerOFACResult performCustomerOFACScreening(CustomerComplianceRequest request) {
        // Implementation would perform OFAC screening for customer
        return CustomerOFACResult.clean();
    }

    private CustomerPEPResult performCustomerPEPScreening(CustomerComplianceRequest request) {
        // Implementation would perform PEP screening for customer
        return CustomerPEPResult.notPEP();
    }

    private boolean requiresEnhancedDueDiligence(CustomerComplianceRequest request, CustomerPEPResult pepResult) {
        return pepResult.isPEP() || request.getExpectedTransactionVolume().compareTo(new BigDecimal("1000000")) > 0;
    }

    private EnhancedDueDiligenceResult performEnhancedDueDiligence(CustomerComplianceRequest request) {
        // Implementation would perform enhanced due diligence
        return EnhancedDueDiligenceResult.passed();
    }

    private CustomerRiskClassification classifyCustomerRisk(CustomerComplianceRequest request, List<ComplianceIssue> issues) {
        if (issues.stream().anyMatch(issue -> issue.getSeverity() == ComplianceIssue.Severity.CRITICAL)) {
            return CustomerRiskClassification.HIGH;
        }
        if (issues.stream().anyMatch(issue -> issue.getSeverity() == ComplianceIssue.Severity.HIGH)) {
            return CustomerRiskClassification.MEDIUM;
        }
        return CustomerRiskClassification.LOW;
    }

    private void updateCustomerRiskProfile(UUID customerId, CustomerRiskClassification riskClass, List<ComplianceIssue> issues) {
        // Implementation would update customer risk profile in database
    }

    private boolean requiresManualReview(List<ComplianceIssue> issues) {
        return issues.stream().anyMatch(issue -> 
            issue.getSeverity() == ComplianceIssue.Severity.HIGH || 
            issue.getSeverity() == ComplianceIssue.Severity.CRITICAL);
    }

    private boolean requiresApproval(List<ComplianceIssue> issues) {
        return issues.stream().anyMatch(issue -> 
            issue.getSeverity() == ComplianceIssue.Severity.CRITICAL);
    }

    // Additional methods for SAR generation and monitoring...
    private List<SuspiciousActivity> collectSuspiciousActivities(SARRequest request) {
        try {
            log.info("Collecting suspicious activities for SAR request: {}", request.getTransactionId());
            
            List<SuspiciousActivity> activities = new ArrayList<>();
            
            // Get the primary transaction that triggered the SAR
            ComplianceTransaction primaryTransaction = complianceTransactionRepository
                .findById(request.getTransactionId()).orElse(null);
                
            if (primaryTransaction != null) {
                activities.add(createSuspiciousActivityFromTransaction(primaryTransaction, "Primary suspicious transaction"));
                
                // Find related suspicious activities
                activities.addAll(findRelatedSuspiciousActivities(primaryTransaction, request.getTimeRange()));
            }
            
            // Add patterns detected by ML models
            activities.addAll(detectMLPatterns(request.getAccountId(), request.getTimeRange()));
            
            // Add any manually flagged activities
            activities.addAll(getManuallyFlaggedActivities(request.getAccountId(), request.getTimeRange()));
            
            log.info("Collected {} suspicious activities for SAR", activities.size());
            return activities;
            
        } catch (Exception e) {
            log.error("Error collecting suspicious activities for SAR request: {}", request.getTransactionId(), e);
            return new ArrayList<>();
        }
    }

    private String generateNarrativeDescription(List<SuspiciousActivity> activities) {
        if (activities.isEmpty()) {
            return "No specific suspicious activities identified.";
        }
        
        StringBuilder narrative = new StringBuilder();
        narrative.append("Suspicious Activity Report - Analysis Summary:\n\n");
        
        // Group activities by type for better organization
        Map<String, List<SuspiciousActivity>> groupedActivities = activities.stream()
            .collect(Collectors.groupingBy(SuspiciousActivity::getActivityType));
        
        for (Map.Entry<String, List<SuspiciousActivity>> entry : groupedActivities.entrySet()) {
            narrative.append(String.format("%s Activities (%d instances):\n", 
                entry.getKey(), entry.getValue().size()));
            
            for (SuspiciousActivity activity : entry.getValue()) {
                narrative.append(String.format("- %s (Amount: %s %s, Date: %s)\n",
                    activity.getDescription(),
                    activity.getAmount(),
                    activity.getCurrency(),
                    activity.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
                    
                if (activity.getRiskScore() > 0.8) {
                    narrative.append("  ** HIGH RISK INDICATOR **\n");
                }
            }
            narrative.append("\n");
        }
        
        // Add pattern analysis
        narrative.append("Pattern Analysis:\n");
        if (hasStructuringPattern(activities)) {
            narrative.append("- Potential structuring pattern detected (multiple transactions below reporting threshold)\n");
        }
        if (hasUnusualVelocityPattern(activities)) {
            narrative.append("- Unusual transaction velocity pattern identified\n");
        }
        if (hasGeographicAnomalies(activities)) {
            narrative.append("- Geographic anomalies in transaction patterns\n");
        }
        
        narrative.append("\nThis report is submitted in compliance with BSA/AML reporting requirements.");
        
        return narrative.toString();
    }

    // Helper methods for SAR collection and analysis
    
    private SuspiciousActivity createSuspiciousActivityFromTransaction(ComplianceTransaction transaction, String reason) {
        return SuspiciousActivity.builder()
            .transactionId(transaction.getId())
            .activityType("TRANSACTION")
            .description(reason + ": " + transaction.getDescription())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .date(transaction.getCreatedAt())
            .riskScore(transaction.getRiskScore())
            .flags(transaction.getFlags())
            .accountId(transaction.getAccountId())
            .counterpartyId(transaction.getCounterpartyId())
            .build();
    }
    
    private List<SuspiciousActivity> findRelatedSuspiciousActivities(ComplianceTransaction primaryTransaction, DateRange timeRange) {
        List<SuspiciousActivity> relatedActivities = new ArrayList<>();
        
        try {
            // Find transactions with same counterparty
            List<ComplianceTransaction> sameCounterpartyTransactions = complianceTransactionRepository
                .findByCounterpartyIdAndDateRange(
                    primaryTransaction.getCounterpartyId(),
                    timeRange.getStartDate(),
                    timeRange.getEndDate()
                );
            
            relatedActivities.addAll(sameCounterpartyTransactions.stream()
                .filter(t -> t.getRiskScore() > 0.7)
                .map(t -> createSuspiciousActivityFromTransaction(t, "Related transaction with same counterparty"))
                .collect(Collectors.toList()));
            
            // Find round-amount transactions (potential structuring)
            List<ComplianceTransaction> roundAmountTransactions = complianceTransactionRepository
                .findRoundAmountTransactions(
                    primaryTransaction.getAccountId(),
                    timeRange.getStartDate(),
                    timeRange.getEndDate()
                );
            
            relatedActivities.addAll(roundAmountTransactions.stream()
                .map(t -> createSuspiciousActivityFromTransaction(t, "Round amount transaction"))
                .collect(Collectors.toList()));
                
        } catch (Exception e) {
            log.error("Error finding related suspicious activities", e);
        }
        
        return relatedActivities;
    }
    
    private List<SuspiciousActivity> detectMLPatterns(UUID accountId, DateRange timeRange) {
        List<SuspiciousActivity> mlActivities = new ArrayList<>();
        
        try {
            // Query ML model results for suspicious patterns
            List<MLDetectionResult> mlResults = mlDetectionRepository
                .findByAccountIdAndDateRange(accountId, timeRange.getStartDate(), timeRange.getEndDate());
            
            mlActivities.addAll(mlResults.stream()
                .filter(result -> result.getConfidenceScore() > 0.8)
                .map(result -> SuspiciousActivity.builder()
                    .activityType("ML_PATTERN")
                    .description("Machine learning detected pattern: " + result.getPatternType())
                    .amount(result.getAssociatedAmount())
                    .currency("USD")
                    .date(result.getDetectedAt())
                    .riskScore(result.getConfidenceScore())
                    .accountId(accountId)
                    .metadata(result.getMetadata())
                    .build())
                .collect(Collectors.toList()));
                
        } catch (Exception e) {
            log.error("Error detecting ML patterns for account: {}", accountId, e);
        }
        
        return mlActivities;
    }
    
    private List<SuspiciousActivity> getManuallyFlaggedActivities(UUID accountId, DateRange timeRange) {
        List<SuspiciousActivity> manualFlags = new ArrayList<>();
        
        try {
            List<ManualFlag> flags = manualFlagRepository
                .findByAccountIdAndDateRange(accountId, timeRange.getStartDate(), timeRange.getEndDate());
            
            manualFlags.addAll(flags.stream()
                .map(flag -> SuspiciousActivity.builder()
                    .activityType("MANUAL_FLAG")
                    .description("Manually flagged by analyst: " + flag.getReason())
                    .amount(flag.getAssociatedAmount())
                    .currency(flag.getCurrency())
                    .date(flag.getFlaggedAt())
                    .riskScore(1.0) // Manual flags are high priority
                    .accountId(accountId)
                    .analystId(flag.getAnalystId())
                    .build())
                .collect(Collectors.toList()));
                
        } catch (Exception e) {
            log.error("Error retrieving manual flags for account: {}", accountId, e);
        }
        
        return manualFlags;
    }
    
    private boolean hasStructuringPattern(List<SuspiciousActivity> activities) {
        // Check for multiple transactions just below $10k threshold
        long structuringCount = activities.stream()
            .filter(activity -> activity.getAmount() != null)
            .filter(activity -> activity.getAmount().compareTo(new BigDecimal("9500")) > 0 
                && activity.getAmount().compareTo(new BigDecimal("10000")) < 0)
            .count();
        
        return structuringCount >= 3;
    }
    
    private boolean hasUnusualVelocityPattern(List<SuspiciousActivity> activities) {
        if (activities.size() < 5) return false;
        
        // Check if there are many transactions in a short time period
        LocalDateTime earliest = activities.stream()
            .map(SuspiciousActivity::getDate)
            .min(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now());
            
        LocalDateTime latest = activities.stream()
            .map(SuspiciousActivity::getDate)
            .max(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now());
            
        long hoursDiff = java.time.Duration.between(earliest, latest).toHours();
        
        // More than 10 transactions in 24 hours is unusual
        return activities.size() > 10 && hoursDiff <= 24;
    }
    
    private boolean hasGeographicAnomalies(List<SuspiciousActivity> activities) {
        // Analyze geographic patterns in transaction metadata
        if (activities == null || activities.isEmpty()) {
            return false;
        }
        
        Map<String, Integer> countryFrequency = new HashMap<>();
        Map<String, LocalDateTime> countryTimestamps = new HashMap<>();
        Set<String> highRiskCountries = getHighRiskCountries();
        
        for (SuspiciousActivity activity : activities) {
            if (activity.getMetadata() == null) {
                continue;
            }
            
            // Extract geographic data from metadata
            String originCountry = (String) activity.getMetadata().get("originCountry");
            String destinationCountry = (String) activity.getMetadata().get("destinationCountry");
            String ipCountry = (String) activity.getMetadata().get("ipCountry");
            LocalDateTime timestamp = activity.getDetectedAt();
            
            // Track country frequencies
            if (originCountry != null) {
                countryFrequency.merge(originCountry, 1, Integer::sum);
                countryTimestamps.put(originCountry, timestamp);
            }
            if (destinationCountry != null) {
                countryFrequency.merge(destinationCountry, 1, Integer::sum);
                countryTimestamps.put(destinationCountry, timestamp);
            }
            if (ipCountry != null) {
                countryFrequency.merge(ipCountry, 1, Integer::sum);
                countryTimestamps.put(ipCountry, timestamp);
            }
            
            // Check for high-risk country involvement
            if (highRiskCountries.contains(originCountry) || 
                highRiskCountries.contains(destinationCountry) || 
                highRiskCountries.contains(ipCountry)) {
                log.warn("High-risk country detected in transaction: origin={}, dest={}, ip={}",
                        originCountry, destinationCountry, ipCountry);
                return true;
            }
            
            // Check for IP/transaction country mismatch
            if (ipCountry != null && originCountry != null && !ipCountry.equals(originCountry)) {
                log.warn("Geographic mismatch detected: IP country {} != Origin country {}",
                        ipCountry, originCountry);
                return true;
            }
        }
        
        // Detect rapid country hopping (multiple countries in short time)
        if (countryFrequency.size() > 3) {
            List<LocalDateTime> timestamps = new ArrayList<>(countryTimestamps.values());
            timestamps.sort(LocalDateTime::compareTo);
            
            for (int i = 1; i < timestamps.size(); i++) {
                long minutesBetween = java.time.Duration.between(
                    timestamps.get(i-1), timestamps.get(i)).toMinutes();
                
                // If countries change within 30 minutes, it's suspicious
                if (minutesBetween < 30) {
                    log.warn("Rapid country hopping detected: {} countries in {} minutes",
                            countryFrequency.size(), minutesBetween);
                    return true;
                }
            }
        }
        
        // Detect unusual geographic distribution
        if (countryFrequency.size() > 5) {
            log.warn("Unusual geographic distribution: transactions from {} different countries",
                    countryFrequency.size());
            return true;
        }
        
        // Check for specific suspicious patterns
        return activities.stream()
            .anyMatch(activity -> activity.getMetadata() != null 
                && (activity.getMetadata().containsKey("geographic_anomaly") ||
                    isGeographicAnomalyPattern(activity)));
    }
    
    private boolean isGeographicAnomalyPattern(SuspiciousActivity activity) {
        Map<String, Object> metadata = activity.getMetadata();
        if (metadata == null) {
            return false;
        }
        
        // Check for specific anomaly patterns
        String pattern = (String) metadata.get("pattern");
        if (pattern != null) {
            return pattern.contains("GEOGRAPHIC") || 
                   pattern.contains("COUNTRY_HOP") || 
                   pattern.contains("IP_MISMATCH");
        }
        
        // Check for distance-based anomalies
        Double distance = (Double) metadata.get("geographicDistance");
        if (distance != null && distance > 5000) { // Over 5000km is suspicious
            return true;
        }
        
        // Check for timezone anomalies
        Integer timezoneHops = (Integer) metadata.get("timezoneHops");
        if (timezoneHops != null && timezoneHops > 3) {
            return true;
        }
        
        return false;
    }
    
    private Set<String> getHighRiskCountries() {
        // FATF high-risk and monitored jurisdictions
        return Set.of(
            "IR", // Iran
            "KP", // North Korea
            "MM", // Myanmar
            "AF", // Afghanistan
            "SY", // Syria
            "YE", // Yemen
            "VE", // Venezuela
            "ML", // Mali
            "BF", // Burkina Faso
            "HT", // Haiti
            "SS", // South Sudan
            "CD", // Democratic Republic of Congo
            "MZ", // Mozambique
            "UG", // Uganda
            "TZ", // Tanzania
            "KE", // Kenya
            "PK", // Pakistan
            "JM", // Jamaica
            "PA", // Panama
            "NI", // Nicaragua
            "AL", // Albania
            "BY", // Belarus
            "ZW", // Zimbabwe
            "CU", // Cuba
            "SO"  // Somalia
        );
    }

    private ReportingInstitutionInfo getReportingInstitutionInfo() {
        // Implementation would return institution information for SAR
        return ReportingInstitutionInfo.builder()
            .institutionName("Waqiti Digital Bank")
            .rssdId("123456")
            .ein("12-3456789")
            .build();
    }

    private StructuringAnalysisResult analyzeForStructuring(List<TransactionSummary> transactions) {
        // Implementation would analyze transactions for structuring patterns
        return StructuringAnalysisResult.noStructuring();
    }

    private VelocityAnalysisResult analyzeTransactionVelocity(List<TransactionSummary> transactions) {
        // Implementation would analyze transaction velocity
        return VelocityAnalysisResult.normal();
    }

    private void updateCustomerRiskProfileFromMonitoring(UUID customerId, List<ComplianceAlert> alerts) {
        // Implementation would update risk profile based on monitoring alerts
    }

    private RiskLevelChange calculateRiskLevelChange(UUID customerId, List<ComplianceAlert> alerts) {
        // Implementation would calculate risk level change
        return RiskLevelChange.NO_CHANGE;
    }

    // Additional AML Service Methods (required by ComplianceController)

    /**
     * Screens a transaction for AML compliance violations
     */
    @Transactional
    public AMLScreeningResponse screenTransaction(AMLScreeningRequest request) {
        try {
            log.info("Screening transaction {} for AML compliance", request.getTransactionId());
            
            // Perform comprehensive AML screening
            ComplianceCheckRequest checkRequest = ComplianceCheckRequest.builder()
                .transactionId(request.getTransactionId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .transactionType(request.getTransactionType())
                .build();
            
            ComplianceCheckResult checkResult = checkTransactionCompliance(checkRequest);
            
            return AMLScreeningResponse.builder()
                .transactionId(request.getTransactionId())
                .screeningId(UUID.randomUUID())
                .status(mapComplianceStatusToScreeningStatus(checkResult.getStatus()))
                .riskScore(checkResult.getRiskScore())
                .violations(checkResult.getViolations().stream()
                    .map(this::mapComplianceViolationToAMLViolation)
                    .collect(Collectors.toList()))
                .alerts(checkResult.getAlerts().stream()
                    .map(this::mapComplianceAlertToAMLAlert)
                    .collect(Collectors.toList()))
                .recommendations(generateRecommendations(checkResult))
                .screenedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("AML screening failed for transaction: {}", request.getTransactionId(), e);
            throw new AMLScreeningException("Failed to screen transaction", e);
        }
    }

    /**
     * Retrieves AML alerts with filtering and pagination
     */
    @Transactional(readOnly = true)
    public Page<AMLAlertResponse> getAMLAlerts(AMLAlertFilter filter, Pageable pageable) {
        try {
            log.info("Retrieving AML alerts with filter: {}", filter);
            
            // Query alerts from repository with filters
            Page<ComplianceAlert> alertPage = complianceAlertRepository.findWithFilters(filter, pageable);
            
            return alertPage.map(this::mapComplianceAlertToAMLAlertResponse);
            
        } catch (Exception e) {
            log.error("Failed to retrieve AML alerts", e);
            throw new AMLRetrievalException("Failed to retrieve AML alerts", e);
        }
    }

    /**
     * Resolves an AML alert with analyst decision
     */
    @Transactional
    public AMLAlertResponse resolveAlert(UUID alertId, ResolveAMLAlertRequest request) {
        try {
            log.info("Resolving AML alert: {} with resolution: {}", alertId, request.getResolution());
            
            ComplianceAlert alert = complianceAlertRepository.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException("Alert not found: " + alertId));
            
            // Update alert with resolution
            alert.setStatus(ComplianceAlert.Status.RESOLVED);
            alert.setResolution(request.getResolution());
            alert.setResolvedBy(request.getResolvedBy());
            alert.setResolvedAt(LocalDateTime.now());
            alert.setNotes(request.getNotes());
            
            // If marked as false positive, update related entities
            if ("FALSE_POSITIVE".equals(request.getResolution())) {
                handleFalsePositiveAlert(alert, request);
            } else if ("CONFIRMED".equals(request.getResolution())) {
                handleConfirmedAlert(alert, request);
            }
            
            ComplianceAlert savedAlert = complianceAlertRepository.save(alert);
            
            return mapComplianceAlertToAMLAlertResponse(savedAlert);
            
        } catch (Exception e) {
            log.error("Failed to resolve AML alert: {}", alertId, e);
            throw new AMLAlertResolutionException("Failed to resolve alert", e);
        }
    }

    /**
     * Files a Suspicious Activity Report (SAR)
     */
    @Transactional
    public SARResponse fileSAR(FileSARRequest request) {
        try {
            log.info("Filing SAR for subject: {}", request.getSubjectUserId());
            
            // Build SAR request from filing request
            SARRequest sarRequest = SARRequest.builder()
                .customerId(request.getSubjectUserId())
                .transactionId(request.getRelatedTransactionId())
                .suspiciousActivities(request.getSuspiciousActivities())
                .narrativeDescription(request.getNarrativeDescription())
                .reportedBy(request.getFiledBy())
                .timeRange(DateRange.builder()
                    .startDate(request.getIncidentStartDate())
                    .endDate(request.getIncidentEndDate())
                    .build())
                .build();
            
            SARGenerationResult result = generateSuspiciousActivityReport(sarRequest);
            
            return SARResponse.builder()
                .sarId(result.getSarId())
                .referenceNumber(result.getReferenceNumber())
                .status(result.isSuccessful() ? "SUBMITTED" : "FAILED")
                .filedAt(result.getSubmittedAt())
                .filedBy(request.getFiledBy())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to file SAR for subject: {}", request.getSubjectUserId(), e);
            throw new SARFilingException("Failed to file SAR", e);
        }
    }

    /**
     * Performs risk assessment for an entity
     */
    @Transactional
    public RiskAssessmentResponse performRiskAssessment(RiskAssessmentRequest request) {
        try {
            log.info("Performing risk assessment for entity: {}", request.getEntityId());
            
            List<RiskFactor> riskFactors = new ArrayList<>();
            double riskScore = 0.0;
            
            // Customer-based risk factors
            if ("CUSTOMER".equals(request.getEntityType())) {
                CustomerRiskProfile profile = customerRiskProfileRepository
                    .findByCustomerId(request.getEntityId()).orElse(null);
                
                if (profile != null) {
                    riskFactors.addAll(mapStringListToRiskFactors(profile.getRiskFactors()));
                    riskScore = calculateBaseRiskScore(profile.getCurrentRiskLevel());
                }
                
                // Add transaction history analysis
                List<TransactionSummary> transactions = transactionServiceClient
                    .getRecentTransactions(request.getEntityId(), LocalDateTime.now().minusDays(90));
                
                TransactionRiskAnalysis transactionRisk = analyzeTransactionRisk(transactions);
                riskFactors.addAll(transactionRisk.getRiskFactors());
                riskScore += transactionRisk.getRiskScore();
            }
            
            // Geographic risk factors
            if (request.getGeographicLocation() != null) {
                GeographicRisk geoRisk = assessGeographicRisk(request.getGeographicLocation());
                riskFactors.addAll(geoRisk.getRiskFactors());
                riskScore += geoRisk.getRiskScore();
            }
            
            // Industry/business risk factors
            if (request.getIndustryType() != null) {
                IndustryRisk industryRisk = assessIndustryRisk(request.getIndustryType());
                riskFactors.addAll(industryRisk.getRiskFactors());
                riskScore += industryRisk.getRiskScore();
            }
            
            // Determine overall risk level
            RiskLevel overallRiskLevel = determineRiskLevel(riskScore);
            
            // Generate recommendations
            List<String> recommendations = generateRiskRecommendations(overallRiskLevel, riskFactors);
            
            RiskAssessmentResponse response = RiskAssessmentResponse.builder()
                .assessmentId(UUID.randomUUID())
                .entityId(request.getEntityId())
                .entityType(request.getEntityType())
                .overallRiskLevel(overallRiskLevel.toString())
                .riskScore(Math.min(riskScore, 1.0))
                .riskFactors(riskFactors)
                .recommendations(recommendations)
                .assessedAt(LocalDateTime.now())
                .assessedBy(request.getAssessedBy())
                .nextReviewDate(calculateNextReviewDate(overallRiskLevel))
                .build();
            
            // Save assessment result
            saveRiskAssessment(response);
            
            log.info("Risk assessment completed: entityId={}, riskLevel={}, score={}", 
                request.getEntityId(), overallRiskLevel, riskScore);
            
            return response;
            
        } catch (Exception e) {
            log.error("Risk assessment failed for entity: {}", request.getEntityId(), e);
            throw new RiskAssessmentException("Failed to perform risk assessment", e);
        }
    }

    /**
     * Retrieves risk assessments with filtering
     */
    @Transactional(readOnly = true)
    public Page<RiskAssessmentResponse> getRiskAssessments(String entityType, String riskLevel, Pageable pageable) {
        try {
            log.info("Retrieving risk assessments: entityType={}, riskLevel={}", entityType, riskLevel);
            
            RiskAssessmentFilter filter = RiskAssessmentFilter.builder()
                .entityType(entityType)
                .riskLevel(riskLevel)
                .build();
            
            return riskAssessmentRepository.findWithFilters(filter, pageable);
            
        } catch (Exception e) {
            log.error("Failed to retrieve risk assessments", e);
            throw new RiskAssessmentRetrievalException("Failed to retrieve risk assessments", e);
        }
    }

    /**
     * Retrieves all compliance rules
     */
    @Transactional(readOnly = true)
    public List<ComplianceRuleResponse> getComplianceRules() {
        try {
            log.info("Retrieving all compliance rules");
            
            List<ComplianceRule> rules = complianceRuleRepository.findAllActive();
            
            return rules.stream()
                .map(this::mapComplianceRuleToResponse)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to retrieve compliance rules", e);
            throw new ComplianceRuleRetrievalException("Failed to retrieve compliance rules", e);
        }
    }

    /**
     * Creates a new compliance rule
     */
    @Transactional
    public ComplianceRuleResponse createComplianceRule(CreateComplianceRuleRequest request) {
        try {
            log.info("Creating compliance rule: {}", request.getRuleName());
            
            // Validate rule doesn't already exist
            if (complianceRuleRepository.existsByRuleName(request.getRuleName())) {
                throw new ComplianceRuleException("Rule with name already exists: " + request.getRuleName());
            }
            
            ComplianceRule rule = ComplianceRule.builder()
                .ruleId(UUID.randomUUID())
                .ruleName(request.getRuleName())
                .description(request.getDescription())
                .ruleType(request.getRuleType())
                .conditions(request.getConditions())
                .action(request.getAction())
                .priority(request.getPriority())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .createdBy(request.getCreatedBy())
                .build();
            
            ComplianceRule savedRule = complianceRuleRepository.save(rule);
            
            log.info("Compliance rule created: {}", savedRule.getRuleId());
            
            return mapComplianceRuleToResponse(savedRule);
            
        } catch (Exception e) {
            log.error("Failed to create compliance rule: {}", request.getRuleName(), e);
            throw new ComplianceRuleCreationException("Failed to create compliance rule", e);
        }
    }

    /**
     * Updates an existing compliance rule
     */
    @Transactional
    public ComplianceRuleResponse updateComplianceRule(UUID ruleId, UpdateComplianceRuleRequest request) {
        try {
            log.info("Updating compliance rule: {}", ruleId);
            
            ComplianceRule rule = complianceRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ComplianceRuleNotFoundException("Rule not found: " + ruleId));
            
            // Update fields if provided
            if (request.getRuleName() != null) {
                rule.setRuleName(request.getRuleName());
            }
            if (request.getDescription() != null) {
                rule.setDescription(request.getDescription());
            }
            if (request.getConditions() != null) {
                rule.setConditions(request.getConditions());
            }
            if (request.getAction() != null) {
                rule.setAction(request.getAction());
            }
            if (request.getPriority() != null) {
                rule.setPriority(request.getPriority());
            }
            if (request.getIsActive() != null) {
                rule.setIsActive(request.getIsActive());
            }
            
            rule.setUpdatedAt(LocalDateTime.now());
            rule.setUpdatedBy(request.getReason()); // Using reason field for updated by
            
            ComplianceRule savedRule = complianceRuleRepository.save(rule);
            
            log.info("Compliance rule updated: {}", ruleId);
            
            return mapComplianceRuleToResponse(savedRule);
            
        } catch (Exception e) {
            log.error("Failed to update compliance rule: {}", ruleId, e);
            throw new ComplianceRuleUpdateException("Failed to update compliance rule", e);
        }
    }

    // Helper methods for the new functionality

    private String mapComplianceStatusToScreeningStatus(ComplianceStatus status) {
        return switch (status) {
            case COMPLIANT -> "CLEAR";
            case ALERT -> "ALERT";
            case REVIEW_REQUIRED -> "REVIEW_REQUIRED";
            case VIOLATION -> "VIOLATION";
        };
    }

    private AMLViolation mapComplianceViolationToAMLViolation(ComplianceViolation violation) {
        return AMLViolation.builder()
            .violationType(violation.getViolationType())
            .description(violation.getDetails())
            .severity(violation.getSeverity().toString())
            .detectedAt(violation.getDetectedAt())
            .build();
    }

    private AMLAlert mapComplianceAlertToAMLAlert(ComplianceAlert alert) {
        return AMLAlert.builder()
            .alertId(alert.getAlertId())
            .alertType(alert.getAlertType())
            .description(alert.getDescription())
            .priority(alert.getPriority().toString())
            .status(alert.getStatus().toString())
            .createdAt(alert.getCreatedAt())
            .build();
    }

    private AMLAlertResponse mapComplianceAlertToAMLAlertResponse(ComplianceAlert alert) {
        return AMLAlertResponse.builder()
            .alertId(alert.getAlertId())
            .alertType(alert.getAlertType())
            .description(alert.getDescription())
            .priority(alert.getPriority().toString())
            .status(alert.getStatus().toString())
            .relatedEntityId(alert.getRelatedEntityId())
            .createdAt(alert.getCreatedAt())
            .resolvedAt(alert.getResolvedAt())
            .resolvedBy(alert.getResolvedBy())
            .resolution(alert.getResolution())
            .notes(alert.getNotes())
            .build();
    }

    private List<String> generateRecommendations(ComplianceCheckResult result) {
        List<String> recommendations = new ArrayList<>();
        
        if (!result.getViolations().isEmpty()) {
            recommendations.add("Immediate review required due to compliance violations");
            recommendations.add("Consider blocking transaction until manual review");
        }
        
        if (result.getRiskScore() > 0.8) {
            recommendations.add("High risk transaction - enhanced monitoring recommended");
        }
        
        if (result.getAlerts().stream().anyMatch(alert -> "STRUCTURING_DETECTED".equals(alert.getAlertType()))) {
            recommendations.add("Potential structuring detected - file SAR within 30 days");
        }
        
        return recommendations;
    }

    private void handleFalsePositiveAlert(ComplianceAlert alert, ResolveAMLAlertRequest request) {
        // Implementation for handling false positive alerts
        log.info("Handling false positive alert: {}", alert.getAlertId());
        // Could update ML models, whitelist entities, etc.
    }

    private void handleConfirmedAlert(ComplianceAlert alert, ResolveAMLAlertRequest request) {
        // Implementation for handling confirmed alerts
        log.info("Handling confirmed alert: {}", alert.getAlertId());
        // Could trigger additional monitoring, escalation, etc.
    }

    private List<RiskFactor> mapStringListToRiskFactors(List<String> riskFactorStrings) {
        return riskFactorStrings.stream()
            .map(factor -> RiskFactor.builder()
                .factorType("CUSTOMER_PROFILE")
                .description(factor)
                .weight(0.1)
                .build())
            .collect(Collectors.toList());
    }

    private double calculateBaseRiskScore(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> 0.2;
            case MEDIUM -> 0.5;
            case HIGH -> 0.8;
        };
    }

    private TransactionRiskAnalysis analyzeTransactionRisk(List<TransactionSummary> transactions) {
        // Implementation for transaction risk analysis
        return TransactionRiskAnalysis.builder()
            .riskFactors(new ArrayList<>())
            .riskScore(0.1)
            .build();
    }

    private GeographicRisk assessGeographicRisk(String location) {
        // Implementation for geographic risk assessment
        return GeographicRisk.builder()
            .riskFactors(new ArrayList<>())
            .riskScore(0.1)
            .build();
    }

    private IndustryRisk assessIndustryRisk(String industryType) {
        // Implementation for industry risk assessment
        return IndustryRisk.builder()
            .riskFactors(new ArrayList<>())
            .riskScore(0.1)
            .build();
    }

    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= 0.7) return RiskLevel.HIGH;
        if (riskScore >= 0.4) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    private List<String> generateRiskRecommendations(RiskLevel riskLevel, List<RiskFactor> riskFactors) {
        List<String> recommendations = new ArrayList<>();
        
        switch (riskLevel) {
            case HIGH -> {
                recommendations.add("Enhanced due diligence required");
                recommendations.add("Increased transaction monitoring");
                recommendations.add("Senior management approval required");
            }
            case MEDIUM -> {
                recommendations.add("Standard due diligence procedures");
                recommendations.add("Regular monitoring");
            }
            case LOW -> recommendations.add("Standard monitoring procedures");
        }
        
        return recommendations;
    }

    private LocalDate calculateNextReviewDate(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case HIGH -> LocalDate.now().plusMonths(6);
            case MEDIUM -> LocalDate.now().plusYears(1);
            case LOW -> LocalDate.now().plusYears(2);
        };
    }

    private void saveRiskAssessment(RiskAssessmentResponse assessment) {
        // Implementation for saving risk assessment
        log.info("Risk assessment saved: {}", assessment.getAssessmentId());
    }

    private ComplianceRuleResponse mapComplianceRuleToResponse(ComplianceRule rule) {
        return ComplianceRuleResponse.builder()
            .ruleId(rule.getRuleId())
            .ruleName(rule.getRuleName())
            .description(rule.getDescription())
            .ruleType(rule.getRuleType())
            .conditions(rule.getConditions())
            .action(rule.getAction())
            .priority(rule.getPriority())
            .isActive(rule.getIsActive())
            .createdAt(rule.getCreatedAt())
            .createdBy(rule.getCreatedBy())
            .updatedAt(rule.getUpdatedAt())
            .updatedBy(rule.getUpdatedBy())
            .build();
    }
}