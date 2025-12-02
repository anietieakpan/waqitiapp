package com.waqiti.common.validation.service;

import com.waqiti.common.validation.model.ValidationModels.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Email Validation Service
 * Orchestrates email validation using various specialized services
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailValidationService {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
        Pattern.CASE_INSENSITIVE
    );
    
    private final EmailVerificationService emailVerificationService;
    private final DisposableEmailService disposableEmailService;
    private final DomainReputationService domainReputationService;
    
    /**
     * Perform comprehensive email validation
     */
    public EmailValidationResult validateEmail(String email) {
        log.debug("Validating email: {}", email);
        
        List<ValidationWarning> warnings = new ArrayList<>();
        List<ValidationError> errors = new ArrayList<>();
        
        // Basic format validation
        boolean isValidFormat = EMAIL_PATTERN.matcher(email).matches();
        if (!isValidFormat) {
            errors.add(ValidationError.builder()
                .field("email")
                .message("Invalid email format")
                .code("INVALID_FORMAT")
                .build());
        }
        
        String domain = extractDomain(email);
        boolean isDisposable = false;
        DomainReputationResult domainReputation = null;
        
        // Check if disposable
        if (disposableEmailService != null && domain != null) {
            try {
                isDisposable = disposableEmailService.isDisposable(domain);
                if (isDisposable) {
                    warnings.add(ValidationWarning.builder()
                        .field("domain")
                        .message("Email domain is disposable/temporary")
                        .code("DISPOSABLE_DOMAIN")
                        .build());
                }
            } catch (Exception e) {
                log.error("Error checking disposable email: {}", e.getMessage());
            }
        }
        
        // Check domain reputation
        if (domainReputationService != null && domain != null) {
            try {
                domainReputation = domainReputationService.checkDomainReputation(domain);
            } catch (Exception e) {
                log.error("Error checking domain reputation: {}", e.getMessage());
            }
        }
        
        // Calculate risk score
        double riskScore = calculateRiskScore(isValidFormat, isDisposable, domainReputation);
        
        return EmailValidationResult.builder()
            .email(email)
            .isValid(isValidFormat && !errors.isEmpty())
            .isDeliverable(isValidFormat)
            .isDisposable(isDisposable)
            .domain(domain)
            .riskScore(riskScore)
            .domainReputation(domainReputation)
            .warnings(warnings)
            .errors(errors)
            .validatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Check if email format is valid
     */
    public boolean isValidFormat(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Extract domain from email address
     */
    private String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        return email.substring(email.indexOf("@") + 1);
    }
    
    /**
     * Calculate risk score based on validation results
     */
    private double calculateRiskScore(boolean isValidFormat, boolean isDisposable, 
                                     DomainReputationResult domainReputation) {
        if (!isValidFormat) {
            return 1.0; // Maximum risk
        }
        
        double score = 0.0;
        
        if (isDisposable) {
            score += 0.4;
        }
        
        if (domainReputation != null) {
            score += (1.0 - domainReputation.getReputationScore()) * 0.6;
        } else {
            score += 0.3; // Unknown domain reputation
        }
        
        return Math.min(1.0, score);
    }
}