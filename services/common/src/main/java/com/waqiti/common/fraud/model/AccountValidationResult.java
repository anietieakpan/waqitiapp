package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of account validation for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountValidationResult {
    
    private String accountNumber;
    private String accountType;
    private ValidationStatus validationStatus;
    private double validationScore;
    
    // Core validation checks
    private boolean isValid; // Overall validation status
    private boolean isValidFormat;
    private boolean passesChecksumValidation;
    private boolean isValidChecksum;
    private boolean existsInBankingSystem;
    private boolean isActiveAccount;
    private boolean matchesExpectedPattern;
    
    // Advanced validation
    private boolean passesLuhnCheck;
    private boolean passesIbanValidation;
    private boolean passesRoutingValidation;
    private boolean passesBankSpecificValidation;
    
    // Account properties
    private String bankCode;
    private String branchCode;
    private String countryCode;
    private String currencyCode;
    private boolean isBusinessAccount;
    private boolean isJointAccount;
    
    // Risk assessment
    private boolean isHighRiskAccount;
    private boolean hasSecurityFlags;
    private boolean isOnWatchlist;
    private boolean hasRestrictions;
    private List<String> riskIndicators;
    
    // Validation errors and warnings
    private List<String> validationErrors;
    private List<String> validationWarnings;
    private String primaryFailureReason;
    private String detailedErrorMessage;
    
    // External validation
    private boolean validatedWithBank;
    private boolean confirmedByThirdParty;
    private LocalDateTime lastValidationDate;
    private String validationSource;
    
    // Analysis metadata
    private LocalDateTime validatedAt;
    private String validationMethod;
    private Map<String, Object> validationDetails;
    private double confidence;
    private long validationTimeMs;
    
    /**
     * Account validation status
     */
    public enum ValidationStatus {
        VALID("Account validation passed"),
        INVALID("Account validation failed"),
        UNCERTAIN("Account validation uncertain"),
        ERROR("Validation error occurred"),
        TIMEOUT("Validation timed out"),
        NOT_FOUND("Account not found");
        
        private final String description;
        
        ValidationStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * Check if account validation passed all checks
     */
    public boolean isFullyValid() {
        return validationStatus == ValidationStatus.VALID &&
               isValidFormat &&
               passesChecksumValidation &&
               existsInBankingSystem;
    }

    /**
     * Get checksum validation status
     */
    public boolean isValidChecksum() {
        return isValidChecksum || passesChecksumValidation;
    }
    
    /**
     * Check if account validation failed critically
     */
    public boolean hasCriticalFailure() {
        return validationStatus == ValidationStatus.INVALID || 
               !isValidFormat || 
               !passesChecksumValidation;
    }
    
    /**
     * Check if account requires manual verification
     */
    public boolean requiresManualVerification() {
        return validationStatus == ValidationStatus.UNCERTAIN || 
               isHighRiskAccount || 
               hasSecurityFlags ||
               !validationErrors.isEmpty();
    }
    
    /**
     * Check if account should be rejected
     */
    public boolean shouldBeRejected() {
        return validationStatus == ValidationStatus.INVALID || 
               isOnWatchlist || 
               hasRestrictions;
    }
    
    /**
     * Get overall validation confidence
     */
    public double getOverallConfidence() {
        double baseConfidence = confidence;
        
        // Adjust based on validation completeness
        if (validatedWithBank) baseConfidence += 0.2;
        if (confirmedByThirdParty) baseConfidence += 0.1;
        if (passesAllChecks()) baseConfidence += 0.1;
        
        // Reduce confidence for risk factors
        if (isHighRiskAccount) baseConfidence -= 0.2;
        if (hasSecurityFlags) baseConfidence -= 0.1;
        
        return Math.max(0.0, Math.min(1.0, baseConfidence));
    }
    
    /**
     * Check if all validation checks passed
     */
    public boolean passesAllChecks() {
        return isValidFormat && 
               passesChecksumValidation && 
               existsInBankingSystem && 
               isActiveAccount;
    }
    
    /**
     * Get validation quality score
     */
    public double getValidationQuality() {
        double quality = 0.0;
        int checkCount = 0;
        
        if (isValidFormat) { quality += 1.0; checkCount++; }
        if (passesChecksumValidation) { quality += 1.0; checkCount++; }
        if (existsInBankingSystem) { quality += 1.0; checkCount++; }
        if (isActiveAccount) { quality += 1.0; checkCount++; }
        if (validatedWithBank) { quality += 1.0; checkCount++; }
        
        return checkCount > 0 ? quality / checkCount : 0.0;
    }
    
    /**
     * Generate validation summary
     */
    public String getValidationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Account Validation Results\n");
        summary.append("Account: ").append(maskAccount(accountNumber)).append("\n");
        summary.append("Status: ").append(validationStatus.getDescription()).append("\n");
        summary.append("Score: ").append(String.format("%.2f", validationScore)).append("\n");
        summary.append("Format Valid: ").append(isValidFormat).append("\n");
        summary.append("Checksum Valid: ").append(passesChecksumValidation).append("\n");
        summary.append("Bank Confirmed: ").append(existsInBankingSystem).append("\n");
        
        if (!validationErrors.isEmpty()) {
            summary.append("Errors: ").append(String.join(", ", validationErrors)).append("\n");
        }
        
        if (!validationWarnings.isEmpty()) {
            summary.append("Warnings: ").append(String.join(", ", validationWarnings)).append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * Mask account number for security
     */
    private String maskAccount(String account) {
        if (account == null || account.length() < 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}