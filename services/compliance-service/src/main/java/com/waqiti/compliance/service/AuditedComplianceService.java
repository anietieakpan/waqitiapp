package com.waqiti.compliance.service;

import com.waqiti.common.audit.annotation.AuditLogged;
import com.waqiti.common.audit.domain.AuditLog.EventCategory;
import com.waqiti.common.audit.domain.AuditLog.Severity;
import com.waqiti.compliance.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Audited Compliance Service with comprehensive audit logging
 * 
 * This service wraps compliance operations with comprehensive audit logging
 * for regulatory compliance with AML, KYC, sanctions screening, and reporting requirements.
 * 
 * AUDIT COVERAGE:
 * - AML checks and monitoring
 * - KYC verification processes
 * - Sanctions screening
 * - SAR (Suspicious Activity Report) filing
 * - CTR (Currency Transaction Report) filing
 * - Enhanced Due Diligence (EDD)
 * - Risk assessment and scoring
 * - Compliance investigations
 * - Regulatory reporting
 * 
 * COMPLIANCE MAPPING:
 * - AML: Anti-Money Laundering monitoring and reporting
 * - KYC: Know Your Customer verification and updates
 * - OFAC: Sanctions screening and monitoring
 * - BSA: Bank Secrecy Act reporting requirements
 * - FinCEN: Financial Crimes Enforcement Network reporting
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditedComplianceService {
    
    private final ComplianceService complianceService;
    
    /**
     * Perform AML check with audit logging
     */
    @AuditLogged(
        eventType = "AML_CHECK_PERFORMED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.HIGH,
        description = "AML check performed for user #{userId} transaction #{transactionId}",
        entityType = "AMLCheck",
        entityIdExpression = "#result.checkId",
        soxRelevant = true,
        requiresNotification = false,
        riskScore = 40,
        metadata = {
            "userId: #userId",
            "transactionId: #transactionId",
            "amount: #amount",
            "currency: #currency",
            "checkType: #checkType",
            "riskScore: #result.riskScore",
            "flagged: #result.flagged",
            "reason: #result.reason"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    public AMLCheckResponse performAMLCheck(UUID userId, UUID transactionId, BigDecimal amount, 
                                           String currency, String checkType) {
        log.info("AUDIT: Performing AML check for user: {} transaction: {} amount: {} {}", 
                userId, transactionId, amount, currency);
        
        return complianceService.performAMLCheck(userId, transactionId, amount, currency, checkType);
    }
    
    /**
     * Flag suspicious activity with audit logging
     */
    @AuditLogged(
        eventType = "SUSPICIOUS_ACTIVITY_FLAGGED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.CRITICAL,
        description = "Suspicious activity flagged for user #{userId} - type: #{activityType}",
        entityType = "SuspiciousActivity",
        entityIdExpression = "#result.activityId",
        soxRelevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 95,
        metadata = {
            "userId: #userId",
            "activityType: #activityType",
            "amount: #amount",
            "currency: #currency",
            "confidence: #confidence",
            "indicators: #indicators",
            "sarRequired: #result.sarRequired",
            "immediateAction: #immediateAction"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public SuspiciousActivityResponse flagSuspiciousActivity(UUID userId, String activityType, BigDecimal amount, 
                                                           String currency, double confidence, String indicators, 
                                                           String immediateAction) {
        log.error("AUDIT: FLAGGING SUSPICIOUS ACTIVITY - User: {} Type: {} Amount: {} {} Confidence: {}", 
                 userId, activityType, amount, currency, confidence);
        
        return complianceService.flagSuspiciousActivity(userId, activityType, amount, currency, 
                                                       confidence, indicators, immediateAction);
    }
    
    /**
     * File SAR (Suspicious Activity Report) with audit logging
     */
    @AuditLogged(
        eventType = "SAR_FILED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.CRITICAL,
        description = "SAR filed for user #{userId} - activity type: #{activityType}",
        entityType = "SAR",
        entityIdExpression = "#result.sarId",
        soxRelevant = true,
        requiresNotification = true,
        metadata = {
            "userId: #userId",
            "sarId: #result.sarId",
            "activityType: #activityType",
            "suspiciousAmount: #suspiciousAmount",
            "currency: #currency",
            "filingReason: #filingReason",
            "filedBy: #filedBy",
            "regulatoryReference: #result.regulatoryReference"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public SARFilingResponse fileSAR(UUID userId, String activityType, BigDecimal suspiciousAmount, 
                                    String currency, String filingReason, UUID filedBy) {
        log.warn("AUDIT: Filing SAR for user: {} type: {} amount: {} {} reason: {}", 
                userId, activityType, suspiciousAmount, currency, filingReason);
        
        return complianceService.fileSAR(userId, activityType, suspiciousAmount, currency, filingReason, filedBy);
    }
    
    /**
     * File CTR (Currency Transaction Report) with audit logging
     */
    @AuditLogged(
        eventType = "CTR_FILED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.HIGH,
        description = "CTR filed for transaction #{transactionId} amount #{amount} #{currency}",
        entityType = "CTR",
        entityIdExpression = "#result.ctrId",
        soxRelevant = true,
        requiresNotification = true,
        metadata = {
            "transactionId: #transactionId",
            "ctrId: #result.ctrId",
            "amount: #amount",
            "currency: #currency",
            "userId: #userId",
            "transactionType: #transactionType",
            "filedBy: #filedBy",
            "regulatoryReference: #result.regulatoryReference"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public CTRFilingResponse fileCTR(UUID transactionId, BigDecimal amount, String currency, 
                                    UUID userId, String transactionType, UUID filedBy) {
        log.info("AUDIT: Filing CTR for transaction: {} amount: {} {} user: {}", 
                transactionId, amount, currency, userId);
        
        return complianceService.fileCTR(transactionId, amount, currency, userId, transactionType, filedBy);
    }
    
    /**
     * Perform KYC verification with audit logging
     */
    @AuditLogged(
        eventType = "KYC_VERIFICATION_PERFORMED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.HIGH,
        description = "KYC verification performed for user #{userId} level #{verificationLevel}",
        entityType = "KYCVerification",
        entityIdExpression = "#result.verificationId",
        gdprRelevant = true,
        soxRelevant = true,
        metadata = {
            "userId: #userId",
            "verificationLevel: #verificationLevel",
            "verificationMethod: #verificationMethod",
            "documentTypes: #documentTypes",
            "verificationResult: #result.status",
            "riskScore: #result.riskScore",
            "verifiedBy: #verifiedBy"
        },
        captureParameters = true,
        captureReturnValue = true,
        excludeFields = {"documentData", "personalIdentifiers"},
        sendToSiem = true
    )
    @Transactional
    public KYCVerificationResponse performKYCVerification(UUID userId, String verificationLevel, 
                                                         String verificationMethod, String[] documentTypes, 
                                                         UUID verifiedBy) {
        log.info("AUDIT: Performing KYC verification for user: {} level: {} method: {}", 
                userId, verificationLevel, verificationMethod);
        
        return complianceService.performKYCVerification(userId, verificationLevel, verificationMethod, 
                                                       documentTypes, verifiedBy);
    }
    
    /**
     * Update KYC status with audit logging
     */
    @AuditLogged(
        eventType = "KYC_STATUS_UPDATED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.MEDIUM,
        description = "KYC status updated for user #{userId} from #{oldStatus} to #{newStatus}",
        entityType = "KYCStatusUpdate",
        entityIdExpression = "#userId",
        gdprRelevant = true,
        soxRelevant = true,
        metadata = {
            "userId: #userId",
            "oldStatus: #oldStatus",
            "newStatus: #newStatus",
            "updateReason: #updateReason",
            "updatedBy: #updatedBy",
            "reviewRequired: #reviewRequired",
            "complianceLevel: #complianceLevel"
        },
        captureParameters = true,
        sendToSiem = false
    )
    @Transactional
    public void updateKYCStatus(UUID userId, String oldStatus, String newStatus, String updateReason, 
                               UUID updatedBy, boolean reviewRequired, String complianceLevel) {
        log.info("AUDIT: Updating KYC status for user: {} from: {} to: {} reason: {}", 
                userId, oldStatus, newStatus, updateReason);
        
        complianceService.updateKYCStatus(userId, oldStatus, newStatus, updateReason, updatedBy, 
                                         reviewRequired, complianceLevel);
    }
    
    /**
     * Perform sanctions screening with audit logging
     */
    @AuditLogged(
        eventType = "SANCTIONS_SCREENING_PERFORMED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.HIGH,
        description = "Sanctions screening performed for user #{userId}",
        entityType = "SanctionsScreening",
        entityIdExpression = "#result.screeningId",
        soxRelevant = true,
        requiresNotification = false,
        riskScore = 60,
        metadata = {
            "userId: #userId",
            "screeningType: #screeningType",
            "screeningLists: #screeningLists",
            "matchFound: #result.matchFound",
            "matchScore: #result.matchScore",
            "matchDetails: #result.matchDetails",
            "actionRequired: #result.actionRequired"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    public SanctionsScreeningResponse performSanctionsScreening(UUID userId, String screeningType, 
                                                               String[] screeningLists) {
        log.info("AUDIT: Performing sanctions screening for user: {} type: {} lists: {}", 
                userId, screeningType, String.join(",", screeningLists));
        
        return complianceService.performSanctionsScreening(userId, screeningType, screeningLists);
    }
    
    /**
     * Flag sanctions match with audit logging
     */
    @AuditLogged(
        eventType = "SANCTIONS_MATCH_FLAGGED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.CRITICAL,
        description = "Sanctions match flagged for user #{userId} - list: #{sanctionsList}",
        entityType = "SanctionsMatch",
        entityIdExpression = "#result.matchId",
        soxRelevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 100,
        metadata = {
            "userId: #userId",
            "matchId: #result.matchId",
            "sanctionsList: #sanctionsList",
            "matchScore: #matchScore",
            "matchType: #matchType",
            "matchDetails: #matchDetails",
            "immediateAction: #immediateAction",
            "falsePositive: #result.falsePositive"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public SanctionsMatchResponse flagSanctionsMatch(UUID userId, String sanctionsList, double matchScore, 
                                                    String matchType, String matchDetails, String immediateAction) {
        log.error("AUDIT: SANCTIONS MATCH FLAGGED - User: {} List: {} Score: {} Type: {}", 
                 userId, sanctionsList, matchScore, matchType);
        
        return complianceService.flagSanctionsMatch(userId, sanctionsList, matchScore, matchType, 
                                                   matchDetails, immediateAction);
    }
    
    /**
     * Perform Enhanced Due Diligence with audit logging
     */
    @AuditLogged(
        eventType = "EDD_PERFORMED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.CRITICAL,
        description = "Enhanced Due Diligence performed for user #{userId}",
        entityType = "EDD",
        entityIdExpression = "#result.eddId",
        soxRelevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 80,
        metadata = {
            "userId: #userId",
            "eddReason: #eddReason",
            "eddLevel: #eddLevel",
            "investigationScope: #investigationScope",
            "eddResult: #result.status",
            "riskAssessment: #result.riskAssessment",
            "performedBy: #performedBy",
            "reviewRequired: #result.reviewRequired"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public EDDResponse performEnhancedDueDiligence(UUID userId, String eddReason, String eddLevel, 
                                                  String investigationScope, UUID performedBy) {
        log.warn("AUDIT: Performing Enhanced Due Diligence for user: {} reason: {} level: {}", 
                userId, eddReason, eddLevel);
        
        return complianceService.performEnhancedDueDiligence(userId, eddReason, eddLevel, 
                                                            investigationScope, performedBy);
    }
    
    /**
     * Calculate risk score with audit logging
     */
    @AuditLogged(
        eventType = "RISK_SCORE_CALCULATED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.MEDIUM,
        description = "Risk score calculated for user #{userId}",
        entityType = "RiskAssessment",
        entityIdExpression = "#userId",
        soxRelevant = true,
        metadata = {
            "userId: #userId",
            "assessmentType: #assessmentType",
            "riskFactors: #riskFactors",
            "calculatedScore: #result.riskScore",
            "riskLevel: #result.riskLevel",
            "recommendations: #result.recommendations",
            "calculatedBy: #calculatedBy"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = false
    )
    public RiskAssessmentResponse calculateRiskScore(UUID userId, String assessmentType, String[] riskFactors, 
                                                    UUID calculatedBy) {
        log.info("AUDIT: Calculating risk score for user: {} type: {} factors: {}", 
                userId, assessmentType, String.join(",", riskFactors));
        
        return complianceService.calculateRiskScore(userId, assessmentType, riskFactors, calculatedBy);
    }
    
    /**
     * Monitor transaction patterns with audit logging
     */
    @AuditLogged(
        eventType = "TRANSACTION_PATTERN_ANALYZED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.MEDIUM,
        description = "Transaction pattern analysis for user #{userId}",
        entityType = "PatternAnalysis",
        entityIdExpression = "#result.analysisId",
        soxRelevant = true,
        riskScore = 30,
        metadata = {
            "userId: #userId",
            "analysisType: #analysisType",
            "timeFrame: #timeFrame",
            "transactionCount: #transactionCount",
            "totalAmount: #totalAmount",
            "anomaliesDetected: #result.anomaliesDetected",
            "suspiciousPatterns: #result.suspiciousPatterns"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    public PatternAnalysisResponse monitorTransactionPatterns(UUID userId, String analysisType, String timeFrame, 
                                                             int transactionCount, BigDecimal totalAmount) {
        log.info("AUDIT: Analyzing transaction patterns for user: {} type: {} timeframe: {} count: {}", 
                userId, analysisType, timeFrame, transactionCount);
        
        return complianceService.monitorTransactionPatterns(userId, analysisType, timeFrame, 
                                                           transactionCount, totalAmount);
    }
    
    /**
     * Generate compliance report with audit logging
     */
    @AuditLogged(
        eventType = "COMPLIANCE_REPORT_GENERATED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.MEDIUM,
        description = "Compliance report generated - type: #{reportType}",
        entityType = "ComplianceReport",
        entityIdExpression = "#result.reportId",
        soxRelevant = true,
        metadata = {
            "reportType: #reportType",
            "reportPeriod: #reportPeriod",
            "reportScope: #reportScope",
            "generatedBy: #generatedBy",
            "reportId: #result.reportId",
            "recordCount: #result.recordCount",
            "regulatoryDeadline: #regulatoryDeadline"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    public ComplianceReportResponse generateComplianceReport(String reportType, String reportPeriod, 
                                                           String reportScope, UUID generatedBy, 
                                                           String regulatoryDeadline) {
        log.info("AUDIT: Generating compliance report - Type: {} Period: {} Scope: {}", 
                reportType, reportPeriod, reportScope);
        
        return complianceService.generateComplianceReport(reportType, reportPeriod, reportScope, 
                                                         generatedBy, regulatoryDeadline);
    }
    
    /**
     * Submit regulatory filing with audit logging
     */
    @AuditLogged(
        eventType = "REGULATORY_FILING_SUBMITTED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.HIGH,
        description = "Regulatory filing submitted - type: #{filingType}",
        entityType = "RegulatoryFiling",
        entityIdExpression = "#result.filingId",
        soxRelevant = true,
        requiresNotification = true,
        metadata = {
            "filingType: #filingType",
            "filingId: #result.filingId",
            "regulator: #regulator",
            "submittedBy: #submittedBy",
            "submissionMethod: #submissionMethod",
            "regulatoryReference: #result.regulatoryReference",
            "deadline: #deadline",
            "submissionStatus: #result.status"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public RegulatoryFilingResponse submitRegulatoryFiling(String filingType, String regulator, 
                                                          UUID submittedBy, String submissionMethod, 
                                                          String deadline) {
        log.info("AUDIT: Submitting regulatory filing - Type: {} Regulator: {} By: {}", 
                filingType, regulator, submittedBy);
        
        return complianceService.submitRegulatoryFiling(filingType, regulator, submittedBy, 
                                                       submissionMethod, deadline);
    }
    
    /**
     * Access compliance data with audit logging
     */
    @AuditLogged(
        eventType = "COMPLIANCE_DATA_ACCESSED",
        category = EventCategory.DATA_ACCESS,
        severity = Severity.HIGH,
        description = "Compliance data accessed for user #{userId}",
        entityType = "ComplianceDataAccess",
        entityIdExpression = "#userId",
        gdprRelevant = true,
        soxRelevant = true,
        requiresNotification = true,
        metadata = {
            "userId: #userId",
            "dataType: #dataType",
            "accessReason: #accessReason",
            "accessLevel: #accessLevel",
            "legalBasis: #legalBasis",
            "dataFields: #dataFields"
        },
        captureParameters = true,
        excludeFields = {"sensitiveData", "personalIdentifiers"},
        sendToSiem = true
    )
    public ComplianceDataResponse accessComplianceData(UUID userId, String dataType, String accessReason, 
                                                      String accessLevel, String legalBasis, String[] dataFields) {
        log.info("AUDIT: Accessing compliance data for user: {} type: {} reason: {}", 
                userId, dataType, accessReason);
        
        return complianceService.accessComplianceData(userId, dataType, accessReason, accessLevel, 
                                                     legalBasis, dataFields);
    }
    
    /**
     * Update compliance configuration with audit logging
     */
    @AuditLogged(
        eventType = "COMPLIANCE_CONFIG_UPDATED",
        category = EventCategory.CONFIGURATION,
        severity = Severity.HIGH,
        description = "Compliance configuration updated - type: #{configType}",
        entityType = "ComplianceConfig",
        soxRelevant = true,
        requiresNotification = true,
        metadata = {
            "configType: #configType",
            "oldValue: #oldValue",
            "newValue: #newValue",
            "updatedBy: #updatedBy",
            "updateReason: #updateReason",
            "effectiveDate: #effectiveDate"
        },
        captureParameters = true,
        sendToSiem = true
    )
    @Transactional
    public void updateComplianceConfiguration(String configType, String oldValue, String newValue, 
                                             UUID updatedBy, String updateReason, String effectiveDate) {
        log.info("AUDIT: Updating compliance configuration - Type: {} From: {} To: {} By: {}", 
                configType, oldValue, newValue, updatedBy);
        
        complianceService.updateComplianceConfiguration(configType, oldValue, newValue, updatedBy, 
                                                       updateReason, effectiveDate);
    }
    
    /**
     * Escalate compliance issue with audit logging
     */
    @AuditLogged(
        eventType = "COMPLIANCE_ISSUE_ESCALATED",
        category = EventCategory.COMPLIANCE,
        severity = Severity.CRITICAL,
        description = "Compliance issue escalated - type: #{issueType}",
        entityType = "ComplianceEscalation",
        entityIdExpression = "#result.escalationId",
        soxRelevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 90,
        metadata = {
            "issueType: #issueType",
            "issueDescription: #issueDescription",
            "severity: #severity",
            "affectedUsers: #affectedUsers",
            "escalatedBy: #escalatedBy",
            "escalatedTo: #escalatedTo",
            "immediateAction: #immediateAction"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public ComplianceEscalationResponse escalateComplianceIssue(String issueType, String issueDescription, 
                                                               String severity, String[] affectedUsers, 
                                                               UUID escalatedBy, UUID escalatedTo, 
                                                               String immediateAction) {
        log.error("AUDIT: ESCALATING COMPLIANCE ISSUE - Type: {} Severity: {} Affected: {} By: {}", 
                 issueType, severity, affectedUsers.length, escalatedBy);
        
        return complianceService.escalateComplianceIssue(issueType, issueDescription, severity, 
                                                        affectedUsers, escalatedBy, escalatedTo, immediateAction);
    }
}