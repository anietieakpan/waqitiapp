package com.waqiti.notification.service;

import com.waqiti.common.util.DataMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

/**
 * Service for validating email addresses
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailValidationService {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final Pattern DISPOSABLE_EMAIL_PATTERN = Pattern.compile(
        ".*@(tempmail\\.org|guerrillamail\\.com|mailinator\\.com|10minutemail\\.com|yopmail\\.com)$",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
        "example.com",
        "test.com",
        "localhost"
    );
    
    private static final Set<String> DISPOSABLE_DOMAINS = new HashSet<>(Set.of(
        "tempmail.org",
        "guerrillamail.com",
        "mailinator.com",
        "10minutemail.com",
        "yopmail.com",
        "temp-mail.org",
        "throwaway.email"
    ));
    
    public boolean validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        email = email.trim().toLowerCase();
        
        // Basic format validation
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            // GDPR COMPLIANCE: Mask email in logs per GDPR Article 32
            log.debug("Invalid email format: {}", DataMaskingUtil.maskEmail(email));
            return false;
        }

        // Check for blocked domains
        if (isBlockedDomain(email)) {
            // GDPR COMPLIANCE: Mask email in logs per GDPR Article 32
            log.debug("Email from blocked domain: {}", DataMaskingUtil.maskEmail(email));
            return false;
        }

        // Check for disposable email
        if (isDisposableEmail(email)) {
            // GDPR COMPLIANCE: Mask email in logs per GDPR Article 32
            log.debug("Disposable email detected: {}", DataMaskingUtil.maskEmail(email));
            return false;
        }
        
        return true;
    }
    
    public boolean isDisposableEmail(String email) {
        if (email == null) return false;
        
        email = email.toLowerCase();
        
        // Pattern-based check
        if (DISPOSABLE_EMAIL_PATTERN.matcher(email).matches()) {
            return true;
        }
        
        // Domain-based check
        String domain = extractDomain(email);
        return DISPOSABLE_DOMAINS.contains(domain);
    }
    
    public boolean isBlockedDomain(String email) {
        if (email == null) return false;
        
        String domain = extractDomain(email.toLowerCase());
        return BLOCKED_DOMAINS.contains(domain);
    }
    
    public boolean isValidDomain(String email) {
        if (email == null) return false;
        
        String domain = extractDomain(email);
        
        // Basic domain validation
        if (domain == null || domain.isEmpty()) {
            return false;
        }
        
        // Must contain at least one dot
        if (!domain.contains(".")) {
            return false;
        }
        
        // Must not start or end with dot
        if (domain.startsWith(".") || domain.endsWith(".")) {
            return false;
        }
        
        return true;
    }
    
    public String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        
        return email.substring(email.lastIndexOf("@") + 1);
    }
    
    public String normalizeEmail(String email) {
        if (email == null) return null;
        
        email = email.trim().toLowerCase();
        
        // Gmail-specific normalization
        if (email.contains("@gmail.com")) {
            String localPart = email.substring(0, email.indexOf("@"));
            
            // Remove dots from Gmail local part
            localPart = localPart.replace(".", "");
            
            // Remove everything after + (plus addressing)
            if (localPart.contains("+")) {
                localPart = localPart.substring(0, localPart.indexOf("+"));
            }
            
            email = localPart + "@gmail.com";
        }
        
        return email;
    }
    
    public EmailValidationResult validateEmailDetailed(String email) {
        EmailValidationResult result = new EmailValidationResult();
        result.setOriginalEmail(email);
        
        if (email == null || email.trim().isEmpty()) {
            result.setValid(false);
            result.addError("Email is null or empty");
            return result;
        }
        
        String normalizedEmail = normalizeEmail(email);
        result.setNormalizedEmail(normalizedEmail);
        
        // Format validation
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            result.setValid(false);
            result.addError("Invalid email format");
            return result;
        }
        
        // Domain validation
        if (!isValidDomain(normalizedEmail)) {
            result.setValid(false);
            result.addError("Invalid domain format");
            return result;
        }
        
        // Blocked domain check
        if (isBlockedDomain(normalizedEmail)) {
            result.setValid(false);
            result.addError("Domain is blocked");
            return result;
        }
        
        // Disposable email check
        if (isDisposableEmail(normalizedEmail)) {
            result.setValid(false);
            result.addWarning("Disposable email detected");
            // Don't return here - might still allow with warning
        }
        
        result.setValid(true);
        result.setDomain(extractDomain(normalizedEmail));
        
        return result;
    }
    
    public static class EmailValidationResult {
        private String originalEmail;
        private String normalizedEmail;
        private String domain;
        private boolean valid;
        private java.util.List<String> errors = new java.util.ArrayList<>();
        private java.util.List<String> warnings = new java.util.ArrayList<>();
        
        // Getters and setters
        public String getOriginalEmail() { return originalEmail; }
        public void setOriginalEmail(String originalEmail) { this.originalEmail = originalEmail; }
        
        public String getNormalizedEmail() { return normalizedEmail; }
        public void setNormalizedEmail(String normalizedEmail) { this.normalizedEmail = normalizedEmail; }
        
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
        
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public java.util.List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }
        
        public java.util.List<String> getWarnings() { return warnings; }
        public void addWarning(String warning) { this.warnings.add(warning); }
        
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
}