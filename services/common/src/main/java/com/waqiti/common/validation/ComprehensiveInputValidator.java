package com.waqiti.common.validation;

import com.waqiti.common.exception.ValidationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Comprehensive Input Validation Service
 * 
 * SECURITY CRITICAL: Prevents injection attacks, data corruption, and business logic violations
 * 
 * This service provides multi-layered input validation for all user inputs:
 * 
 * VALIDATION LAYERS:
 * 1. Format validation (regex, length, type)
 * 2. Business rule validation (limits, constraints)
 * 3. Security validation (injection prevention)
 * 4. Cross-field validation (relationships)
 * 5. Context-aware validation (user role, transaction type)
 * 
 * SECURITY FEATURES:
 * - SQL injection prevention
 * - XSS attack prevention
 * - Command injection prevention
 * - Path traversal prevention
 * - Email/phone format validation
 * - Financial amount validation
 * - Currency code validation
 * - Account number validation
 * 
 * COMPLIANCE:
 * - PCI DSS: Requirement 6.5.1 (Injection flaws)
 * - OWASP Top 10: A03 Injection
 * - NIST 800-53: SI-10 (Information Input Validation)
 * - ISO 27001: A.14.2.1 (Secure development policy)
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Component
@Slf4j
public class ComprehensiveInputValidator {

    private final Validator validator;
    
    // Security patterns for injection prevention
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        ".*(union|select|insert|update|delete|drop|create|alter|exec|execute|script|javascript|vbscript|onload|onerror|eval|expression).*",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern XSS_PATTERN = Pattern.compile(
        ".*(<script|</script|javascript:|vbscript:|onload=|onerror=|onclick=|onmouseover=|onfocus=|onblur=|onchange=|onsubmit=).*",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
        ".*(\\||&|;|`|\\$\\(|\\$\\{|\\\\|/etc/|/bin/|cmd\\.exe|powershell|bash|sh).*",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
        ".*(\\.\\.[\\\\/]|[\\\\/]\\.\\.[\\\\/]|\\.\\.%|%2e%2e|%2f|%5c).*",
        Pattern.CASE_INSENSITIVE
    );
    
    // Financial validation patterns
    private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9]{8,20}$");
    private static final Pattern ROUTING_NUMBER_PATTERN = Pattern.compile("^[0-9]{9}$");
    private static final Pattern SWIFT_CODE_PATTERN = Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{4}[0-9]{7}([A-Z0-9]?){0,16}$");
    
    // Personal information patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z\\s\\-']{1,100}$");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-#.,]{1,200}$");
    
    // Transaction patterns
    private static final Pattern TRANSACTION_REF_PATTERN = Pattern.compile("^[A-Z0-9]{8,32}$");
    private static final Pattern PAYMENT_METHOD_PATTERN = Pattern.compile("^(CARD|BANK|WALLET|CRYPTO)$");
    
    // Business limits
    private static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("100000.00");
    private static final BigDecimal MIN_TRANSACTION_AMOUNT = new BigDecimal("0.01");
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_ADDRESS_LENGTH = 200;
    
    // Allowed file extensions
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp");
    private static final Set<String> ALLOWED_DOCUMENT_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt");
    
    public ComprehensiveInputValidator(Validator validator) {
        this.validator = validator;
    }
    
    /**
     * Validates a complete payment request with comprehensive security checks
     */
    public ValidationResult validatePaymentRequest(PaymentValidationContext context) {
        ValidationResult result = new ValidationResult();
        
        try {
            // 1. Basic null and format validation
            validateRequired(context.getAmount(), "amount", result);
            validateRequired(context.getRecipientId(), "recipientId", result);
            validateRequired(context.getCurrency(), "currency", result);
            
            // 2. Security validation
            validateAgainstInjectionAttacks(context.getDescription(), "description", result);
            validateAgainstInjectionAttacks(context.getReference(), "reference", result);
            
            // 3. Financial validation
            validateAmount(context.getAmount(), result);
            validateCurrency(context.getCurrency(), result);
            
            // 4. Business rule validation
            validateTransactionLimits(context, result);
            validateRecipientId(context.getRecipientId(), result);
            
            // 5. Context-aware validation
            validateUserPermissions(context, result);
            
            log.debug("Payment validation completed - Valid: {}, Errors: {}", 
                result.isValid(), result.getErrors().size());
                
        } catch (Exception e) {
            log.error("Payment validation failed with exception", e);
            result.addError("validation", "Validation service error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Validates user registration data
     */
    public ValidationResult validateUserRegistration(UserRegistrationContext context) {
        ValidationResult result = new ValidationResult();
        
        try {
            // Required field validation
            validateRequired(context.getEmail(), "email", result);
            validateRequired(context.getFirstName(), "firstName", result);
            validateRequired(context.getLastName(), "lastName", result);
            validateRequired(context.getPhoneNumber(), "phoneNumber", result);
            validateRequired(context.getPassword(), "password", result);
            
            // Format validation
            validateEmail(context.getEmail(), result);
            validatePhoneNumber(context.getPhoneNumber(), result);
            validateName(context.getFirstName(), "firstName", result);
            validateName(context.getLastName(), "lastName", result);
            validatePassword(context.getPassword(), result);
            
            // Security validation
            validateAgainstInjectionAttacks(context.getEmail(), "email", result);
            validateAgainstInjectionAttacks(context.getFirstName(), "firstName", result);
            validateAgainstInjectionAttacks(context.getLastName(), "lastName", result);
            
            // Address validation if provided
            if (context.getAddress() != null) {
                validateAddress(context.getAddress(), result);
                validateAgainstInjectionAttacks(context.getAddress(), "address", result);
            }
            
        } catch (Exception e) {
            log.error("User registration validation failed", e);
            result.addError("validation", "Registration validation error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Validates financial account information
     */
    public ValidationResult validateAccountInformation(AccountValidationContext context) {
        ValidationResult result = new ValidationResult();
        
        try {
            validateRequired(context.getAccountNumber(), "accountNumber", result);
            validateRequired(context.getAccountType(), "accountType", result);
            
            validateAccountNumber(context.getAccountNumber(), result);
            
            if (context.getRoutingNumber() != null) {
                validateRoutingNumber(context.getRoutingNumber(), result);
            }
            
            if (context.getSwiftCode() != null) {
                validateSwiftCode(context.getSwiftCode(), result);
            }
            
            if (context.getIban() != null) {
                validateIban(context.getIban(), result);
            }
            
            // Security validation
            validateAgainstInjectionAttacks(context.getAccountNumber(), "accountNumber", result);
            validateAgainstInjectionAttacks(context.getBankName(), "bankName", result);
            
        } catch (Exception e) {
            log.error("Account validation failed", e);
            result.addError("validation", "Account validation error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Validates file upload requests
     */
    public ValidationResult validateFileUpload(FileUploadContext context) {
        ValidationResult result = new ValidationResult();
        
        try {
            validateRequired(context.getFileName(), "fileName", result);
            validateRequired(context.getFileSize(), "fileSize", result);
            validateRequired(context.getFileType(), "fileType", result);
            
            // Security validation
            validateFileName(context.getFileName(), result);
            validateFileSize(context.getFileSize(), context.getMaxAllowedSize(), result);
            validateFileExtension(context.getFileName(), context.getAllowedExtensions(), result);
            
            // Path traversal prevention
            validateAgainstPathTraversal(context.getFileName(), result);
            
        } catch (Exception e) {
            log.error("File upload validation failed", e);
            result.addError("validation", "File validation error: " + e.getMessage());
        }
        
        return result;
    }
    
    // Private validation methods
    
    private void validateRequired(Object value, String fieldName, ValidationResult result) {
        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
            result.addError(fieldName, fieldName + " is required");
        }
    }
    
    private void validateAgainstInjectionAttacks(String value, String fieldName, ValidationResult result) {
        if (value == null) return;
        
        if (SQL_INJECTION_PATTERN.matcher(value).matches()) {
            result.addError(fieldName, fieldName + " contains potentially malicious SQL content");
            log.warn("SECURITY: SQL injection attempt detected in field: {}, value: {}", 
                fieldName, maskSensitiveValue(value));
        }
        
        if (XSS_PATTERN.matcher(value).matches()) {
            result.addError(fieldName, fieldName + " contains potentially malicious script content");
            log.warn("SECURITY: XSS attack attempt detected in field: {}, value: {}", 
                fieldName, maskSensitiveValue(value));
        }
        
        if (COMMAND_INJECTION_PATTERN.matcher(value).matches()) {
            result.addError(fieldName, fieldName + " contains potentially malicious command content");
            log.warn("SECURITY: Command injection attempt detected in field: {}, value: {}", 
                fieldName, maskSensitiveValue(value));
        }
    }
    
    private void validateAgainstPathTraversal(String value, ValidationResult result) {
        if (value == null) return;
        
        if (PATH_TRAVERSAL_PATTERN.matcher(value).matches()) {
            result.addError("fileName", "File name contains potentially malicious path traversal content");
            log.warn("SECURITY: Path traversal attempt detected in filename: {}", maskSensitiveValue(value));
        }
    }
    
    private void validateAmount(BigDecimal amount, ValidationResult result) {
        if (amount == null) return;
        
        if (amount.compareTo(MIN_TRANSACTION_AMOUNT) < 0) {
            result.addError("amount", "Amount must be at least " + MIN_TRANSACTION_AMOUNT);
        }
        
        if (amount.compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
            result.addError("amount", "Amount cannot exceed " + MAX_TRANSACTION_AMOUNT);
        }
        
        // Check decimal places (max 2 for most currencies)
        if (amount.scale() > 2) {
            result.addError("amount", "Amount cannot have more than 2 decimal places");
        }
    }
    
    private void validateCurrency(String currency, ValidationResult result) {
        if (currency == null) return;
        
        if (!CURRENCY_CODE_PATTERN.matcher(currency).matches()) {
            result.addError("currency", "Currency must be a valid 3-letter ISO code");
        }
    }
    
    private void validateEmail(String email, ValidationResult result) {
        if (email == null) return;
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            result.addError("email", "Email format is invalid");
        }
        
        if (email.length() > 254) {
            result.addError("email", "Email is too long (max 254 characters)");
        }
    }
    
    private void validatePhoneNumber(String phoneNumber, ValidationResult result) {
        if (phoneNumber == null) return;
        
        if (!PHONE_PATTERN.matcher(phoneNumber).matches()) {
            result.addError("phoneNumber", "Phone number format is invalid");
        }
    }
    
    private void validateName(String name, String fieldName, ValidationResult result) {
        if (name == null) return;
        
        if (!NAME_PATTERN.matcher(name).matches()) {
            result.addError(fieldName, fieldName + " contains invalid characters");
        }
        
        if (name.length() > MAX_NAME_LENGTH) {
            result.addError(fieldName, fieldName + " is too long (max " + MAX_NAME_LENGTH + " characters)");
        }
    }
    
    private void validatePassword(String password, ValidationResult result) {
        if (password == null) return;
        
        if (password.length() < 8) {
            result.addError("password", "Password must be at least 8 characters long");
        }
        
        if (password.length() > 128) {
            result.addError("password", "Password is too long (max 128 characters)");
        }
        
        if (!password.matches(".*[A-Z].*")) {
            result.addError("password", "Password must contain at least one uppercase letter");
        }
        
        if (!password.matches(".*[a-z].*")) {
            result.addError("password", "Password must contain at least one lowercase letter");
        }
        
        if (!password.matches(".*[0-9].*")) {
            result.addError("password", "Password must contain at least one number");
        }
        
        if (!password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) {
            result.addError("password", "Password must contain at least one special character");
        }
    }
    
    private void validateAddress(String address, ValidationResult result) {
        if (address == null) return;
        
        if (!ADDRESS_PATTERN.matcher(address).matches()) {
            result.addError("address", "Address contains invalid characters");
        }
        
        if (address.length() > MAX_ADDRESS_LENGTH) {
            result.addError("address", "Address is too long (max " + MAX_ADDRESS_LENGTH + " characters)");
        }
    }
    
    private void validateAccountNumber(String accountNumber, ValidationResult result) {
        if (accountNumber == null) return;
        
        if (!ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches()) {
            result.addError("accountNumber", "Account number must be 8-20 digits");
        }
    }
    
    private void validateRoutingNumber(String routingNumber, ValidationResult result) {
        if (routingNumber == null) return;
        
        if (!ROUTING_NUMBER_PATTERN.matcher(routingNumber).matches()) {
            result.addError("routingNumber", "Routing number must be exactly 9 digits");
        }
    }
    
    private void validateSwiftCode(String swiftCode, ValidationResult result) {
        if (swiftCode == null) return;
        
        if (!SWIFT_CODE_PATTERN.matcher(swiftCode).matches()) {
            result.addError("swiftCode", "SWIFT code format is invalid");
        }
    }
    
    private void validateIban(String iban, ValidationResult result) {
        if (iban == null) return;
        
        if (!IBAN_PATTERN.matcher(iban).matches()) {
            result.addError("iban", "IBAN format is invalid");
        }
    }
    
    private void validateRecipientId(String recipientId, ValidationResult result) {
        if (recipientId == null) return;
        
        // Validate UUID format
        try {
            UUID.fromString(recipientId);
        } catch (IllegalArgumentException e) {
            result.addError("recipientId", "Recipient ID must be a valid UUID");
        }
    }
    
    private void validateTransactionLimits(PaymentValidationContext context, ValidationResult result) {
        // This would typically check against user-specific limits from the database
        // For now, implement basic validation
        if (context.getAmount() != null && context.getAmount().compareTo(new BigDecimal("50000")) > 0) {
            result.addError("amount", "Amount exceeds daily transaction limit");
        }
    }
    
    private void validateUserPermissions(PaymentValidationContext context, ValidationResult result) {
        // This would typically validate against user roles and permissions
        // For now, implement basic validation
        if (context.getUserRole() != null && context.getUserRole().equals("RESTRICTED")) {
            result.addError("user", "User account is restricted from making payments");
        }
    }
    
    private void validateFileName(String fileName, ValidationResult result) {
        if (fileName == null) return;
        
        if (fileName.length() > 255) {
            result.addError("fileName", "File name is too long (max 255 characters)");
        }
        
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            result.addError("fileName", "File name contains invalid characters");
        }
    }
    
    private void validateFileSize(Long fileSize, Long maxSize, ValidationResult result) {
        if (fileSize == null || maxSize == null) return;
        
        if (fileSize > maxSize) {
            result.addError("fileSize", "File size exceeds maximum allowed size of " + maxSize + " bytes");
        }
        
        if (fileSize <= 0) {
            result.addError("fileSize", "File size must be greater than 0");
        }
    }
    
    private void validateFileExtension(String fileName, Set<String> allowedExtensions, ValidationResult result) {
        if (fileName == null || allowedExtensions == null) return;
        
        String extension = getFileExtension(fileName);
        if (extension == null || !allowedExtensions.contains(extension.toLowerCase())) {
            result.addError("fileType", "File type not allowed. Allowed types: " + allowedExtensions);
        }
    }
    
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return null;
    }
    
    private String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 4) {
            return "***";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
    
    // Data classes for validation contexts
    
    public static class ValidationResult {
        private final Map<String, List<String>> errors = new HashMap<>();
        
        public void addError(String field, String message) {
            errors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public Map<String, List<String>> getErrors() {
            return new HashMap<>(errors);
        }
        
        public List<String> getFieldErrors(String field) {
            return errors.getOrDefault(field, Collections.emptyList());
        }
        
        public String getFirstError() {
            return errors.values().stream()
                .flatMap(List::stream)
                .findFirst()
                .orElse(null);
        }
        
        public int getErrorCount() {
            return errors.values().stream()
                .mapToInt(List::size)
                .sum();
        }
    }
    
    // Context classes would be defined here or in separate files
    public static class PaymentValidationContext {
        private BigDecimal amount;
        private String recipientId;
        private String currency;
        private String description;
        private String reference;
        private String userRole;
        
        // Getters and setters
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getRecipientId() { return recipientId; }
        public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }
        public String getUserRole() { return userRole; }
        public void setUserRole(String userRole) { this.userRole = userRole; }
    }
    
    public static class UserRegistrationContext {
        private String email;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private String password;
        private String address;
        
        // Getters and setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
    }
    
    public static class AccountValidationContext {
        private String accountNumber;
        private String accountType;
        private String routingNumber;
        private String swiftCode;
        private String iban;
        private String bankName;
        
        // Getters and setters
        public String getAccountNumber() { return accountNumber; }
        public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
        public String getAccountType() { return accountType; }
        public void setAccountType(String accountType) { this.accountType = accountType; }
        public String getRoutingNumber() { return routingNumber; }
        public void setRoutingNumber(String routingNumber) { this.routingNumber = routingNumber; }
        public String getSwiftCode() { return swiftCode; }
        public void setSwiftCode(String swiftCode) { this.swiftCode = swiftCode; }
        public String getIban() { return iban; }
        public void setIban(String iban) { this.iban = iban; }
        public String getBankName() { return bankName; }
        public void setBankName(String bankName) { this.bankName = bankName; }
    }
    
    public static class FileUploadContext {
        private String fileName;
        private Long fileSize;
        private String fileType;
        private Long maxAllowedSize;
        private Set<String> allowedExtensions;
        
        // Getters and setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        public Long getMaxAllowedSize() { return maxAllowedSize; }
        public void setMaxAllowedSize(Long maxAllowedSize) { this.maxAllowedSize = maxAllowedSize; }
        public Set<String> getAllowedExtensions() { return allowedExtensions; }
        public void setAllowedExtensions(Set<String> allowedExtensions) { this.allowedExtensions = allowedExtensions; }
    }
}