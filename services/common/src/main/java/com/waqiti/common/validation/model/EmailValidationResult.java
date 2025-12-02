package com.waqiti.common.validation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise-grade Email Validation Result model.
 * Contains comprehensive validation results including syntax validation,
 * domain verification, MX records, disposable email detection, and risk scoring.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailValidationResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * The email address that was validated
     */
    @NotNull(message = "Email address is required")
    @Email(message = "Invalid email format")
    private String email;
    
    /**
     * The domain part of the email
     */
    private String domain;
    
    /**
     * Whether the email syntax is valid
     */
    @Builder.Default
    private boolean syntaxValid = false;
    
    /**
     * Whether the domain exists and is resolvable
     */
    @Builder.Default
    private boolean domainExists = false;
    
    /**
     * Whether the domain has MX records
     */
    @Builder.Default
    private boolean hasMXRecords = false;
    
    /**
     * Whether the email is from a disposable/temporary email provider
     */
    @Builder.Default
    private boolean isDisposable = false;
    
    /**
     * Whether the email is a role-based account (admin@, info@, etc.)
     */
    @Builder.Default
    private boolean isRoleAccount = false;
    
    /**
     * Whether the email is from a free email provider
     */
    @Builder.Default
    private boolean isFreeProvider = false;
    
    /**
     * Whether the email is from a corporate domain
     */
    @Builder.Default
    private boolean isCorporate = false;
    
    /**
     * Overall validity of the email
     */
    @Builder.Default
    private boolean overallValid = false;
    
    /**
     * Risk score from 0.0 (low risk) to 1.0 (high risk)
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Builder.Default
    private double riskScore = 0.5;
    
    /**
     * Confidence level of the validation from 0.0 to 1.0
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Builder.Default
    private double confidence = 0.5;
    
    /**
     * Domain reputation score from 0.0 (poor) to 1.0 (excellent)
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Builder.Default
    private double domainReputation = 0.5;
    
    /**
     * List of validation messages/warnings
     */
    @Size(max = 50)
    @Builder.Default
    private List<String> validationMessages = new ArrayList<>();
    
    /**
     * When the validation was performed
     */
    @NotNull
    @Builder.Default
    private LocalDateTime validatedAt = LocalDateTime.now();
    
    /**
     * Time taken for validation in milliseconds
     */
    private Long validationDurationMs;
    
    /**
     * Whether SMTP check was performed
     */
    @Builder.Default
    private boolean smtpCheckPerformed = false;
    
    /**
     * Result of SMTP check if performed
     */
    private Boolean smtpCheckResult;
    
    /**
     * Whether the email is blacklisted
     */
    @Builder.Default
    private boolean isBlacklisted = false;
    
    /**
     * Whether the email is whitelisted
     */
    @Builder.Default
    private boolean isWhitelisted = false;
    
    /**
     * Catch-all status of the domain
     */
    private Boolean isCatchAll;
    
    /**
     * Whether deliverability was verified
     */
    @Builder.Default
    private boolean deliverabilityVerified = false;
    
    /**
     * Suggested action based on validation results
     */
    private SuggestedAction suggestedAction;
    
    /**
     * Additional metadata for the validation
     */
    private ValidationMetadata metadata;
    
    /**
     * Enum for suggested actions based on validation
     */
    public enum SuggestedAction {
        ACCEPT("Accept the email address"),
        REJECT("Reject the email address"),
        REVIEW("Manual review recommended"),
        VERIFY("Additional verification required"),
        MONITOR("Accept but monitor for suspicious activity");
        
        private final String description;
        
        SuggestedAction(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Additional validation metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationMetadata {
        private String mxHost;
        private String mxPriority;
        private String ispName;
        private String countryCode;
        private boolean hasSpfRecord;
        private boolean hasDmarcRecord;
        private boolean hasDkimRecord;
        private Integer domainAgeInDays;
        private LocalDateTime domainCreatedDate;
        private String validationProvider;
        private String validationMethod;
    }
    
    /**
     * Helper method to determine if the email should be accepted
     */
    public boolean shouldAccept() {
        return overallValid && !isDisposable && !isBlacklisted && riskScore < 0.7;
    }
    
    /**
     * Helper method to determine if additional verification is needed
     */
    public boolean needsVerification() {
        return riskScore >= 0.5 && riskScore < 0.7 && !isBlacklisted;
    }
    
    /**
     * Get a human-readable summary of the validation result
     */
    public String getSummary() {
        if (overallValid) {
            if (isDisposable) {
                return "Valid but disposable email address";
            } else if (isRoleAccount) {
                return "Valid role-based email account";
            } else if (isCorporate) {
                return "Valid corporate email address";
            } else if (isFreeProvider) {
                return "Valid free email provider address";
            } else {
                return "Valid email address";
            }
        } else {
            if (!syntaxValid) {
                return "Invalid email syntax";
            } else if (!domainExists) {
                return "Domain does not exist";
            } else if (!hasMXRecords) {
                return "No MX records found for domain";
            } else {
                return "Invalid email address";
            }
        }
    }
}