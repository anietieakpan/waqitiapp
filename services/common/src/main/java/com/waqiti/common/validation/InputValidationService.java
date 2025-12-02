package com.waqiti.common.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * CRITICAL SECURITY SERVICE: Comprehensive Input Validation
 * Protects against injection attacks, data corruption, and security vulnerabilities
 * 
 * Features:
 * - SQL injection prevention
 * - XSS protection
 * - Command injection prevention
 * - Path traversal prevention
 * - Business logic validation
 * - Data sanitization
 */
@Service
@Slf4j
public class InputValidationService {

    // Security patterns for threat detection
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i).*(union|select|insert|delete|update|drop|alter|create|exec|execute|script|javascript|vbscript|onload|onerror).*"
    );
    
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i).*(<script|<iframe|<object|<embed|<form|javascript:|vbscript:|onload=|onerror=|onclick=).*"
    );
    
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
        "(?i).*(\\||;|&|\\$\\(|`|\\.\\./|\\.\\.\\\\|cmd|powershell|bash|sh|exec).*"
    );
    
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
        ".*(\\.\\.[\\\\/]|\\.\\.[\\\\]|%2e%2e%2f|%2e%2e%5c|\\.\\.%2f|\\.\\.%5c).*"
    );
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$"
    );
    
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile(
        "^[A-Z0-9]{8,20}$"
    );
    
    private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile(
        "^[A-Z]{3}$"
    );
    
    /**
     * Comprehensive validation for payment transfer requests
     */
    public ValidationResult validatePaymentTransfer(PaymentTransferRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Validate sender account
        if (!isValidAccountNumber(request.getSenderAccount())) {
            errors.add(ValidationError.builder()
                .field("senderAccount")
                .code("INVALID_ACCOUNT_NUMBER")
                .message("Invalid sender account number format")
                .severity(ValidationSeverity.ERROR)
                .build());
        }
        
        // Validate recipient account
        if (!isValidAccountNumber(request.getRecipientAccount())) {
            errors.add(ValidationError.builder()
                .field("recipientAccount")
                .code("INVALID_ACCOUNT_NUMBER")
                .message("Invalid recipient account number format")
                .severity(ValidationSeverity.ERROR)
                .build());
        }
        
        // Validate amount
        ValidationResult amountValidation = validateAmount(request.getAmount(), "amount");
        errors.addAll(amountValidation.getErrors());
        
        // Validate currency
        if (!isValidCurrencyCode(request.getCurrency())) {
            errors.add(ValidationError.builder()
                .field("currency")
                .code("INVALID_CURRENCY")
                .message("Invalid currency code")
                .severity(ValidationSeverity.ERROR)
                .build());
        }
        
        // Validate description for security threats
        if (request.getDescription() != null) {
            ValidationResult descValidation = validateSecureText(request.getDescription(), "description");
            errors.addAll(descValidation.getErrors());
        }
        
        // Business logic validation
        if (request.getSenderAccount() != null && request.getSenderAccount().equals(request.getRecipientAccount())) {
            errors.add(ValidationError.builder()
                .field("recipientAccount")
                .code("SELF_TRANSFER_NOT_ALLOWED")
                .message("Cannot transfer to the same account")
                .severity(ValidationSeverity.ERROR)
                .build());
        }
        
        return ValidationResult.builder()
            .valid(errors.isEmpty())
            .errors(errors)
            .build();
    }
    
    /**
     * Validate user registration request
     */
    public ValidationResult validateUserRegistration(UserRegistrationRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Validate email
        if (!isValidEmail(request.getEmail())) {
            errors.add(ValidationError.builder()
                .field("email")
                .code("INVALID_EMAIL")
                .message("Invalid email format")
                .severity(ValidationSeverity.ERROR)
                .build());
        }
        
        // Validate phone number
        if (request.getPhoneNumber() != null && !isValidPhoneNumber(request.getPhoneNumber())) {
            errors.add(ValidationError.builder()
                .field("phoneNumber")
                .code("INVALID_PHONE")
                .message("Invalid phone number format")
                .severity(ValidationSeverity.ERROR)
                .build());
        }
        
        // Validate password strength
        ValidationResult passwordValidation = validatePassword(request.getPassword());
        errors.addAll(passwordValidation.getErrors());
        
        // Validate personal information for security threats
        ValidationResult firstNameValidation = validateSecureText(request.getFirstName(), "firstName");
        errors.addAll(firstNameValidation.getErrors());
        
        ValidationResult lastNameValidation = validateSecureText(request.getLastName(), "lastName");
        errors.addAll(lastNameValidation.getErrors());
        
        return ValidationResult.builder()
            .valid(errors.isEmpty())
            .errors(errors)
            .build();
    }
    
    /**
     * Validate KYC verification request
     */
    public ValidationResult validateKycVerification(KycVerificationRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Validate document type
        if (!isValidDocumentType(request.getDocumentType())) {
            errors.add(ValidationError.builder()
                .field("documentType")
                .code("INVALID_DOCUMENT_TYPE")
                .message("Invalid document type")
                .severity(ValidationSeverity.ERROR)
                .build());
        }
        
        // Validate document number
        if (!isValidDocumentNumber(request.getDocumentNumber())) {
            errors.add(ValidationError.builder()
                .field("documentNumber")
                .code("INVALID_DOCUMENT_NUMBER")
                .message("Invalid document number format")
                .severity(ValidationSeverity.ERROR)
                .build());
        }
        
        // Validate date of birth
        if (!isValidDateOfBirth(request.getDateOfBirth())) {
            errors.add(ValidationError.builder()
                .field("dateOfBirth")
                .code("INVALID_DATE_OF_BIRTH")
                .message("Invalid date of birth")
                .severity(ValidationSeverity.ERROR)
                .build());
        }
        
        // Validate address for security threats
        if (request.getAddress() != null) {
            ValidationResult addressValidation = validateSecureText(request.getAddress(), "address");
            errors.addAll(addressValidation.getErrors());
        }
        
        return ValidationResult.builder()
            .valid(errors.isEmpty())
            .errors(errors)
            .build();
    }
    
    /**
     * Validate monetary amounts
     */
    public ValidationResult validateAmount(BigDecimal amount, String fieldName) {
        List<ValidationError> errors = new ArrayList<>();
        
        if (amount == null) {
            errors.add(ValidationError.builder()
                .field(fieldName)
                .code("AMOUNT_REQUIRED")
                .message("Amount is required")
                .severity(ValidationSeverity.ERROR)
                .build());
        } else {
            // Check for negative amounts
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(ValidationError.builder()
                    .field(fieldName)
                    .code("AMOUNT_MUST_BE_POSITIVE")
                    .message("Amount must be positive")
                    .severity(ValidationSeverity.ERROR)
                    .build());
            }
            
            // Check maximum amount (prevent overflow attacks)
            if (amount.compareTo(new BigDecimal("999999999.99")) > 0) {
                errors.add(ValidationError.builder()
                    .field(fieldName)
                    .code("AMOUNT_TOO_LARGE")
                    .message("Amount exceeds maximum allowed value")
                    .severity(ValidationSeverity.ERROR)
                    .build());
            }
            
            // Check decimal places (max 2 for most currencies)
            if (amount.scale() > 2) {
                errors.add(ValidationError.builder()
                    .field(fieldName)
                    .code("TOO_MANY_DECIMAL_PLACES")
                    .message("Amount cannot have more than 2 decimal places")
                    .severity(ValidationSeverity.WARNING)
                    .build());
            }
        }
        
        return ValidationResult.builder()
            .valid(errors.stream().noneMatch(e -> e.getSeverity() == ValidationSeverity.ERROR))
            .errors(errors)
            .build();
    }
    
    /**
     * Validate text input for security threats
     */
    public ValidationResult validateSecureText(String text, String fieldName) {
        List<ValidationError> errors = new ArrayList<>();
        
        if (text == null) {
            return ValidationResult.builder().valid(true).errors(errors).build();
        }
        
        // Check for SQL injection attempts
        if (SQL_INJECTION_PATTERN.matcher(text).matches()) {
            errors.add(ValidationError.builder()
                .field(fieldName)
                .code("POTENTIAL_SQL_INJECTION")
                .message("Input contains potentially malicious SQL patterns")
                .severity(ValidationSeverity.ERROR)
                .build());
            
            log.warn("SECURITY_ALERT: Potential SQL injection attempt in field '{}': {}", fieldName, text);
        }
        
        // Check for XSS attempts
        if (XSS_PATTERN.matcher(text).matches()) {
            errors.add(ValidationError.builder()
                .field(fieldName)
                .code("POTENTIAL_XSS")
                .message("Input contains potentially malicious script patterns")
                .severity(ValidationSeverity.ERROR)
                .build());
            
            log.warn("SECURITY_ALERT: Potential XSS attempt in field '{}': {}", fieldName, text);
        }
        
        // Check for command injection attempts
        if (COMMAND_INJECTION_PATTERN.matcher(text).matches()) {
            errors.add(ValidationError.builder()
                .field(fieldName)
                .code("POTENTIAL_COMMAND_INJECTION")
                .message("Input contains potentially malicious command patterns")
                .severity(ValidationSeverity.ERROR)
                .build());
            
            log.warn("SECURITY_ALERT: Potential command injection attempt in field '{}': {}", fieldName, text);
        }
        
        // Check for path traversal attempts
        if (PATH_TRAVERSAL_PATTERN.matcher(text).matches()) {
            errors.add(ValidationError.builder()
                .field(fieldName)
                .code("POTENTIAL_PATH_TRAVERSAL")
                .message("Input contains potentially malicious path patterns")
                .severity(ValidationSeverity.ERROR)
                .build());
            
            log.warn("SECURITY_ALERT: Potential path traversal attempt in field '{}': {}", fieldName, text);
        }
        
        return ValidationResult.builder()
            .valid(errors.isEmpty())
            .errors(errors)
            .build();
    }
    
    /**
     * Validate password strength
     */
    public ValidationResult validatePassword(String password) {
        List<ValidationError> errors = new ArrayList<>();
        
        if (password == null || password.length() < 8) {
            errors.add(ValidationError.builder()
                .field("password")
                .code("PASSWORD_TOO_SHORT")
                .message("Password must be at least 8 characters long")
                .severity(ValidationSeverity.ERROR)
                .build());
        }
        
        if (password != null) {
            // Check for at least one uppercase letter
            if (!password.matches(".*[A-Z].*")) {
                errors.add(ValidationError.builder()
                    .field("password")
                    .code("PASSWORD_NO_UPPERCASE")
                    .message("Password must contain at least one uppercase letter")
                    .severity(ValidationSeverity.WARNING)
                    .build());
            }
            
            // Check for at least one lowercase letter
            if (!password.matches(".*[a-z].*")) {
                errors.add(ValidationError.builder()
                    .field("password")
                    .code("PASSWORD_NO_LOWERCASE")
                    .message("Password must contain at least one lowercase letter")
                    .severity(ValidationSeverity.WARNING)
                    .build());
            }
            
            // Check for at least one digit
            if (!password.matches(".*\\d.*")) {
                errors.add(ValidationError.builder()
                    .field("password")
                    .code("PASSWORD_NO_DIGIT")
                    .message("Password must contain at least one digit")
                    .severity(ValidationSeverity.WARNING)
                    .build());
            }
            
            // Check for at least one special character
            if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
                errors.add(ValidationError.builder()
                    .field("password")
                    .code("PASSWORD_NO_SPECIAL")
                    .message("Password must contain at least one special character")
                    .severity(ValidationSeverity.WARNING)
                    .build());
            }
            
            // Check for common weak passwords
            if (isCommonPassword(password)) {
                errors.add(ValidationError.builder()
                    .field("password")
                    .code("PASSWORD_TOO_COMMON")
                    .message("Password is too common and easily guessable")
                    .severity(ValidationSeverity.ERROR)
                    .build());
            }
        }
        
        return ValidationResult.builder()
            .valid(errors.stream().noneMatch(e -> e.getSeverity() == ValidationSeverity.ERROR))
            .errors(errors)
            .build();
    }
    
    /**
     * Sanitize text input by removing/escaping dangerous characters
     */
    public String sanitizeText(String input) {
        if (input == null) {
            return null;
        }
        
        return input
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
            .replace("&", "&amp;");
    }
    
    // Helper validation methods
    
    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    private boolean isValidPhoneNumber(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }
    
    private boolean isValidAccountNumber(String accountNumber) {
        return accountNumber != null && ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches();
    }
    
    private boolean isValidCurrencyCode(String currency) {
        return currency != null && CURRENCY_CODE_PATTERN.matcher(currency).matches();
    }
    
    private boolean isValidDocumentType(String documentType) {
        return documentType != null && 
            (documentType.equals("PASSPORT") || 
             documentType.equals("DRIVERS_LICENSE") || 
             documentType.equals("NATIONAL_ID"));
    }
    
    private boolean isValidDocumentNumber(String documentNumber) {
        return documentNumber != null && 
            documentNumber.length() >= 5 && 
            documentNumber.length() <= 20 &&
            documentNumber.matches("^[A-Z0-9]+$");
    }
    
    private boolean isValidDateOfBirth(String dateOfBirth) {
        // Simple validation - in production would use proper date parsing
        return dateOfBirth != null && dateOfBirth.matches("^\\d{4}-\\d{2}-\\d{2}$");
    }
    
    private boolean isCommonPassword(String password) {
        String[] commonPasswords = {
            "password", "123456", "password123", "admin", "qwerty",
            "letmein", "welcome", "monkey", "dragon", "master"
        };
        
        String lowerPassword = password.toLowerCase();
        for (String common : commonPasswords) {
            if (lowerPassword.contains(common)) {
                return true;
            }
        }
        return false;
    }
}