package com.waqiti.compliance.service;

import com.waqiti.compliance.model.AMLMonitoringResult;
import com.waqiti.compliance.model.AMLRuleViolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AML Transaction Monitoring Service Interface
 * 
 * CRITICAL COMPLIANCE: Detects money laundering patterns in real-time
 * REGULATORY IMPACT: Prevents AML violations and regulatory penalties
 * 
 * Implements transaction monitoring rules required by FinCEN and other regulators
 */
public interface AMLTransactionMonitoringService {
    
    /**
     * Monitor a transaction for AML violations
     * 
     * @param transactionId Transaction to monitor
     * @param userId User performing transaction
     * @param amount Transaction amount
     * @param currency Currency code
     * @param transactionType Type of transaction
     * @param sourceAccount Source account
     * @param destinationAccount Destination account
     * @return Monitoring result with violations
     */
    AMLMonitoringResult monitorTransaction(UUID transactionId, UUID userId, BigDecimal amount,
                                          String currency, String transactionType,
                                          String sourceAccount, String destinationAccount);
    
    /**
     * Check for structuring (smurfing) patterns
     * 
     * @param userId User to check
     * @param amount Current transaction amount
     * @param timeWindow Time window to check (hours)
     * @return True if structuring detected
     */
    boolean detectStructuring(UUID userId, BigDecimal amount, int timeWindow);
    
    /**
     * Check for velocity violations
     * 
     * @param userId User to check
     * @param currentAmount Current transaction amount
     * @return List of velocity rule violations
     */
    List<AMLRuleViolation> checkVelocityRules(UUID userId, BigDecimal currentAmount);
    
    /**
     * Detect rapid movement of funds
     * 
     * @param userId User to check
     * @param timeWindow Time window in hours
     * @return True if rapid movement detected
     */
    boolean detectRapidMovement(UUID userId, int timeWindow);
    
    /**
     * Check for round-amount transactions (potential laundering indicator)
     * 
     * @param amount Transaction amount
     * @return True if suspicious round amount
     */
    boolean isRoundAmountSuspicious(BigDecimal amount);
    
    /**
     * Detect dormant account suddenly active
     * 
     * @param userId User to check
     * @param lastActivityDate Last activity date
     * @return True if dormant account reactivation is suspicious
     */
    boolean detectDormantAccountReactivation(UUID userId, LocalDateTime lastActivityDate);
    
    /**
     * Check cumulative transaction thresholds
     * 
     * @param userId User to check
     * @param period Period to check (DAILY, WEEKLY, MONTHLY)
     * @return True if threshold exceeded
     */
    boolean checkCumulativeThresholds(UUID userId, String period);
    
    /**
     * Detect unusual transaction patterns
     * 
     * @param userId User to check
     * @param transactionType Transaction type
     * @param amount Amount
     * @return Pattern anomaly score (0.0 to 1.0)
     */
    double detectUnusualPatterns(UUID userId, String transactionType, BigDecimal amount);
    
    /**
     * Check for geographic risk indicators
     * 
     * @param sourceCountry Source country
     * @param destinationCountry Destination country
     * @param amount Transaction amount
     * @return Risk score (0.0 to 1.0)
     */
    double assessGeographicRisk(String sourceCountry, String destinationCountry, BigDecimal amount);
    
    /**
     * Generate AML risk score for user
     * 
     * @param userId User to assess
     * @return Overall AML risk score (0.0 to 1.0)
     */
    double calculateUserAMLRiskScore(UUID userId);
    
    /**
     * Get user's transaction history for AML review
     *
     * @param userId User ID
     * @param startDate Start date
     * @param endDate End date
     * @return Transaction history with AML indicators
     */
    List<AMLMonitoringResult> getUserTransactionHistory(UUID userId, LocalDateTime startDate,
                                                       LocalDateTime endDate);

    /**
     * Validate AML reporting requirements
     */
    void validateAMLReportingRequirements(UUID reportId, String reportType, Map<String, Object> reportData);

    /**
     * Conduct comprehensive AML investigation
     */
    com.waqiti.compliance.domain.SuspiciousActivity conductComprehensiveAMLInvestigation(UUID reportId, String transactionId, Map<String, Object> data);

    /**
     * Analyze money laundering typologies
     */
    Map<String, Object> analyzeMLTypologies(UUID reportId, com.waqiti.compliance.domain.SuspiciousActivity investigation, LocalDateTime timestamp);

    /**
     * Determine BSA filing obligation
     */
    String determineBSAFilingObligation(UUID reportId, Map<String, Object> mlAnalysis, BigDecimal amount, String reportType);

    /**
     * Document filing decision
     */
    void documentFilingDecision(UUID reportId, String decision, LocalDateTime timestamp);

    /**
     * Generate AML regulator filing
     */
    com.waqiti.compliance.domain.SARFiling generateAMLRegulatorFiling(UUID reportId, com.waqiti.compliance.domain.SuspiciousActivity investigation, String reportType, LocalDateTime timestamp);

    /**
     * Validate filing completeness
     */
    void validateFilingCompleteness(com.waqiti.compliance.domain.SARFiling filing, String reportType, LocalDateTime timestamp);

    /**
     * Update filing status
     */
    void updateFilingStatus(UUID reportId, String status, String submissionId, LocalDateTime timestamp);

    /**
     * Send AML filing notifications
     */
    void sendAMLFilingNotifications(UUID reportId, String submissionId, String reportType, LocalDateTime timestamp);

    /**
     * Notify money laundering reporting officer
     */
    void notifyMoneyLaunderingReportingOfficer(UUID reportId, com.waqiti.compliance.domain.SARFiling filing, String submissionId);
}