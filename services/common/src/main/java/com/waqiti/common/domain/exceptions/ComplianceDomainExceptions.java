package com.waqiti.common.domain.exceptions;

import com.waqiti.common.domain.Money;
import com.waqiti.common.domain.valueobjects.UserId;

/**
 * Compliance Domain Exceptions
 * Domain-specific exceptions for compliance and regulatory requirements
 */
public class ComplianceDomainExceptions {
    
    private static final String COMPLIANCE_DOMAIN = "COMPLIANCE";
    
    /**
     * AML Threshold Exceeded Exception
     */
    public static class AmlThresholdExceededException extends DomainException {
        
        private final UserId userId;
        private final Money amount;
        private final Money threshold;
        private final String thresholdType;
        
        public AmlThresholdExceededException(UserId userId, Money amount, Money threshold, String thresholdType) {
            super(String.format("AML %s threshold exceeded for user %s: amount %s exceeds %s", 
                    thresholdType, userId, amount, threshold),
                    "AML_THRESHOLD_EXCEEDED",
                    COMPLIANCE_DOMAIN);
            this.userId = userId;
            this.amount = amount;
            this.threshold = threshold;
            this.thresholdType = thresholdType;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public Money getAmount() {
            return amount;
        }
        
        public Money getThreshold() {
            return threshold;
        }
        
        public String getThresholdType() {
            return thresholdType;
        }
    }
    
    /**
     * Sanctions Screening Failed Exception
     */
    public static class SanctionsScreeningFailedException extends DomainException {
        
        private final UserId userId;
        private final String matchedList;
        private final double matchScore;
        
        public SanctionsScreeningFailedException(UserId userId, String matchedList, double matchScore) {
            super(String.format("Sanctions screening failed for user %s: matched on %s list with score %.2f", 
                    userId, matchedList, matchScore),
                    "SANCTIONS_SCREENING_FAILED",
                    COMPLIANCE_DOMAIN);
            this.userId = userId;
            this.matchedList = matchedList;
            this.matchScore = matchScore;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public String getMatchedList() {
            return matchedList;
        }
        
        public double getMatchScore() {
            return matchScore;
        }
    }
    
    /**
     * PEP Detection Exception
     */
    public static class PepDetectionException extends DomainException {
        
        private final UserId userId;
        private final String pepType;
        private final String jurisdiction;
        
        public PepDetectionException(UserId userId, String pepType, String jurisdiction) {
            super(String.format("PEP detected for user %s: type %s in jurisdiction %s", 
                    userId, pepType, jurisdiction),
                    "PEP_DETECTED",
                    COMPLIANCE_DOMAIN);
            this.userId = userId;
            this.pepType = pepType;
            this.jurisdiction = jurisdiction;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public String getPepType() {
            return pepType;
        }
        
        public String getJurisdiction() {
            return jurisdiction;
        }
    }
    
    /**
     * High Risk Transaction Exception
     */
    public static class HighRiskTransactionException extends DomainException {
        
        private final String transactionId;
        private final String riskLevel;
        private final String[] riskFactors;
        
        public HighRiskTransactionException(String transactionId, String riskLevel, String... riskFactors) {
            super(String.format("High risk transaction detected: %s with risk level %s", 
                    transactionId, riskLevel),
                    "HIGH_RISK_TRANSACTION",
                    COMPLIANCE_DOMAIN);
            this.transactionId = transactionId;
            this.riskLevel = riskLevel;
            this.riskFactors = riskFactors;
        }
        
        public String getTransactionId() {
            return transactionId;
        }
        
        public String getRiskLevel() {
            return riskLevel;
        }
        
        public String[] getRiskFactors() {
            return riskFactors;
        }
    }
    
    /**
     * Suspicious Activity Exception
     */
    public static class SuspiciousActivityException extends DomainException {
        
        private final UserId userId;
        private final String activityType;
        private final String pattern;
        
        public SuspiciousActivityException(UserId userId, String activityType, String pattern) {
            super(String.format("Suspicious activity detected for user %s: %s pattern '%s'", 
                    userId, activityType, pattern),
                    "SUSPICIOUS_ACTIVITY",
                    COMPLIANCE_DOMAIN);
            this.userId = userId;
            this.activityType = activityType;
            this.pattern = pattern;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public String getActivityType() {
            return activityType;
        }
        
        public String getPattern() {
            return pattern;
        }
    }
    
    /**
     * CTR Filing Required Exception
     */
    public static class CtrFilingRequiredException extends DomainException {
        
        private final String transactionId;
        private final Money amount;
        private final String reportType;
        
        public CtrFilingRequiredException(String transactionId, Money amount, String reportType) {
            super(String.format("CTR filing required for transaction %s: amount %s requires %s report", 
                    transactionId, amount, reportType),
                    "CTR_FILING_REQUIRED",
                    COMPLIANCE_DOMAIN);
            this.transactionId = transactionId;
            this.amount = amount;
            this.reportType = reportType;
        }
        
        public String getTransactionId() {
            return transactionId;
        }
        
        public Money getAmount() {
            return amount;
        }
        
        public String getReportType() {
            return reportType;
        }
    }
    
    /**
     * Cross Border Restriction Exception
     */
    public static class CrossBorderRestrictionException extends DomainException {
        
        private final String fromCountry;
        private final String toCountry;
        private final String restrictionType;
        
        public CrossBorderRestrictionException(String fromCountry, String toCountry, String restrictionType) {
            super(String.format("Cross-border transaction restricted from %s to %s: %s", 
                    fromCountry, toCountry, restrictionType),
                    "CROSS_BORDER_RESTRICTION",
                    COMPLIANCE_DOMAIN);
            this.fromCountry = fromCountry;
            this.toCountry = toCountry;
            this.restrictionType = restrictionType;
        }
        
        public String getFromCountry() {
            return fromCountry;
        }
        
        public String getToCountry() {
            return toCountry;
        }
        
        public String getRestrictionType() {
            return restrictionType;
        }
    }
    
    /**
     * Enhanced Due Diligence Required Exception
     */
    public static class EnhancedDueDiligenceRequiredException extends DomainException {
        
        private final UserId userId;
        private final String reason;
        private final String[] requiredDocuments;
        
        public EnhancedDueDiligenceRequiredException(UserId userId, String reason, String... requiredDocuments) {
            super(String.format("Enhanced due diligence required for user %s: %s", userId, reason),
                    "EDD_REQUIRED",
                    COMPLIANCE_DOMAIN);
            this.userId = userId;
            this.reason = reason;
            this.requiredDocuments = requiredDocuments;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public String getReason() {
            return reason;
        }
        
        public String[] getRequiredDocuments() {
            return requiredDocuments;
        }
    }
    
    /**
     * Regulatory Block Exception
     */
    public static class RegulatoryBlockException extends DomainException {
        
        private final String regulation;
        private final String jurisdiction;
        private final String blockReason;
        
        public RegulatoryBlockException(String regulation, String jurisdiction, String blockReason) {
            super(String.format("Transaction blocked by %s regulation in %s: %s", 
                    regulation, jurisdiction, blockReason),
                    "REGULATORY_BLOCK",
                    COMPLIANCE_DOMAIN);
            this.regulation = regulation;
            this.jurisdiction = jurisdiction;
            this.blockReason = blockReason;
        }
        
        public String getRegulation() {
            return regulation;
        }
        
        public String getJurisdiction() {
            return jurisdiction;
        }
        
        public String getBlockReason() {
            return blockReason;
        }
    }
    
    /**
     * Source of Funds Verification Required Exception
     */
    public static class SourceOfFundsVerificationRequiredException extends DomainException {
        
        private final UserId userId;
        private final Money amount;
        private final String triggerReason;
        
        public SourceOfFundsVerificationRequiredException(UserId userId, Money amount, String triggerReason) {
            super(String.format("Source of funds verification required for user %s: amount %s triggered %s", 
                    userId, amount, triggerReason),
                    "SOF_VERIFICATION_REQUIRED",
                    COMPLIANCE_DOMAIN);
            this.userId = userId;
            this.amount = amount;
            this.triggerReason = triggerReason;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public Money getAmount() {
            return amount;
        }
        
        public String getTriggerReason() {
            return triggerReason;
        }
    }
}