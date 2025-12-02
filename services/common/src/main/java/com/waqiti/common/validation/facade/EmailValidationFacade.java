package com.waqiti.common.validation.facade;

import com.waqiti.common.validation.model.ValidationModels.EmailValidationResult;
import com.waqiti.common.validation.model.ValidationModels.DomainReputationResult;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise Email Validation Facade
 * 
 * Public interface for email validation services following enterprise patterns:
 * - Comprehensive email validation pipeline
 * - Disposable email detection
 * - Domain reputation analysis
 * - Real-time verification
 * - Batch processing capabilities
 * - Compliance tracking
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2024-01-18
 */
@Validated
public interface EmailValidationFacade {
    
    /**
     * Comprehensive email validation with all checks
     * 
     * @param emailAddress Email address to validate
     * @return Future containing comprehensive validation results
     */
    CompletableFuture<EmailValidationResult> validateEmail(
        @NotBlank @Email String emailAddress
    );
    
    /**
     * Check if email domain is disposable/temporary
     * 
     * @param emailAddress Email address to check
     * @return Future containing disposable email detection results
     */
    CompletableFuture<DisposableEmailResult> checkDisposableEmail(
        @NotBlank @Email String emailAddress
    );
    
    /**
     * Assess domain reputation and trustworthiness
     * 
     * @param domain Domain to assess
     * @return Future containing domain reputation analysis
     */
    CompletableFuture<DomainReputationResult> assessDomainReputation(
        @NotBlank String domain
    );
    
    /**
     * Batch email validation for high-volume processing
     * 
     * @param emailAddresses List of emails to validate
     * @return Future containing map of email to validation results
     */
    CompletableFuture<Map<String, EmailValidationResult>> batchValidateEmails(
        @jakarta.validation.Valid List<@Email String> emailAddresses
    );
    
    /**
     * Real-time email verification (SMTP check)
     * 
     * @param emailAddress Email to verify
     * @return Future containing verification results
     */
    CompletableFuture<EmailVerificationResult> verifyEmailRealtime(
        @NotBlank @Email String emailAddress
    );
    
    // Supporting model classes
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DisposableEmailResult {
        private boolean isDisposable;
        private String provider;
        private double confidence;
        private List<String> detectionMethods;
        private long checkedAt;
        private com.waqiti.common.validation.model.ValidationModels.ValidationError error;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EmailVerificationResult {
        private boolean isDeliverable;
        private boolean isValidMailbox;
        private boolean acceptsAll;
        private String smtpResponse;
        private double confidence;
        private long verifiedAt;
        private com.waqiti.common.validation.model.ValidationModels.ValidationError error;
    }
}