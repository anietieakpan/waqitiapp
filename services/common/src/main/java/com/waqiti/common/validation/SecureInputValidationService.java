package com.waqiti.common.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CRITICAL SECURITY SERVICE: Comprehensive Input Validation Framework
 * Prevents injection attacks and ensures data integrity across all services
 * 
 * Security features:
 * - SQL injection prevention
 * - XSS prevention
 * - Command injection prevention
 * - Path traversal prevention
 * - LDAP injection prevention
 * - XML injection prevention
 * - NoSQL injection prevention
 * - Header injection prevention
 * - Unicode attack prevention
 * - Business logic validation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureInputValidationService {
    
    private final ObjectMapper objectMapper;
    
    // Validation patterns
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)((\\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|EXECUTE|UNION|FROM|WHERE|" +
        "ORDER BY|GROUP BY|HAVING|INTO|VALUES|TRUNCATE|DECLARE|CAST|CONVERT|SCRIPT|JAVASCRIPT|" +
        "ONLOAD|ONERROR|ONCLICK|ALERT|CONFIRM|PROMPT)\\b)|(--)|(;)|(\\|\\|)|(\\*/)|(/\\*)|" +
        "(xp_)|(sp_)|(0x[0-9A-F]+))", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i)(<script[^>]*>.*?</script>|<iframe.*?>|javascript:|on\\w+\\s*=|<img[^>]*>|" +
        "<object.*?>|<embed.*?>|<applet.*?>|<meta.*?>|<link.*?>|<style.*?>.*?</style>|" +
        "expression\\s*\\(|import\\s+|@import|behavior\\s*:|binding\\s*:|vbscript:|" +
        "data:text/html|<svg.*?onload)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
        "(?i)([;&|`$(){}\\[\\]<>]|\\$\\(|\\$\\{|&&|\\|\\||>>|<<|\\n|\\r|\\t|\\x00|\\x1a)"
    );
    
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
        "(?i)(\\.\\./|\\.\\\\|%2e%2e%2f|%2e%2e%5c|%.{0,}2e%.{0,}2e%.{0,}2f|" +
        "%.{0,}2e%.{0,}2e%.{0,}5c|\\.{2,}[/\\\\]|\\\\\\.\\.)"
    );
    
    private static final Pattern LDAP_INJECTION_PATTERN = Pattern.compile(
        "(?i)(\\*|\\(|\\)|\\\\|,|;|\\||&|!|~|=|<|>|\\x00)"
    );
    
    private static final Pattern XML_INJECTION_PATTERN = Pattern.compile(
        "(?i)(<!DOCTYPE|<!ENTITY|SYSTEM|PUBLIC|<\\?xml|<\\!\\[CDATA\\[)"
    );
    
    private static final Pattern NOSQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(\\$ne|\\$eq|\\$gt|\\$gte|\\$lt|\\$lte|\\$in|\\$nin|\\$and|\\$or|\\$not|" +
        "\\$regex|\\$where|\\$exists|\\$type|\\$expr|\\$jsonSchema|\\$mod|\\$text|\\$geoNear)"
    );
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$" // E.164 format
    );
    
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern ALPHA_PATTERN = Pattern.compile("^[a-zA-Z]+$");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[0-9]+$");
    
    // Blacklisted keywords for various contexts
    private static final Set<String> SQL_KEYWORDS = ImmutableSet.of(
        "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", "TRUNCATE",
        "EXEC", "EXECUTE", "UNION", "DECLARE", "CAST", "CONVERT"
    );
    
    private static final Set<String> DANGEROUS_FILE_EXTENSIONS = ImmutableSet.of(
        ".exe", ".bat", ".cmd", ".com", ".pif", ".scr", ".vbs", ".js", ".jar",
        ".zip", ".rar", ".sh", ".ps1", ".psm1", ".msi", ".dll", ".app"
    );
    
    // Unicode normalization and homograph attack prevention
    private static final Map<Character, Character> HOMOGRAPH_MAP = new HashMap<>();
    
    // Rate limiting for validation attempts
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        // Initialize homograph detection map
        initializeHomographMap();
        log.info("Secure Input Validation Service initialized with comprehensive security rules");
    }
    
    /**
     * Comprehensive validation for user input
     */
    public ValidationResult validateInput(String input, InputType type, ValidationContext context) {
        if (input == null) {
            return ValidationResult.invalid("Input cannot be null");
        }
        
        // Check rate limiting
        if (!checkRateLimit(context.getUserId())) {
            log.warn("Rate limit exceeded for user: {}", context.getUserId());
            return ValidationResult.invalid("Rate limit exceeded");
        }
        
        // Normalize Unicode to prevent homograph attacks
        String normalized = normalizeUnicode(input);
        
        // Check length constraints
        if (!validateLength(normalized, type)) {
            return ValidationResult.invalid("Input length exceeds maximum allowed");
        }
        
        // Type-specific validation
        ValidationResult typeValidation = validateByType(normalized, type);
        if (!typeValidation.isValid()) {
            return typeValidation;
        }
        
        // Security checks
        ValidationResult securityValidation = performSecurityChecks(normalized, type, context);
        if (!securityValidation.isValid()) {
            logSecurityViolation(context, input, securityValidation.getError());
            return securityValidation;
        }
        
        // Business logic validation
        ValidationResult businessValidation = validateBusinessLogic(normalized, type, context);
        if (!businessValidation.isValid()) {
            return businessValidation;
        }
        
        // Sanitize and return
        String sanitized = sanitizeInput(normalized, type);
        return ValidationResult.valid(sanitized);
    }
    
    /**
     * Validate by input type
     */
    private ValidationResult validateByType(String input, InputType type) {
        switch (type) {
            case EMAIL:
                return validateEmail(input);
            case PHONE:
                return validatePhone(input);
            case USERNAME:
                return validateUsername(input);
            case PASSWORD:
                return validatePassword(input);
            case URL:
                return validateUrl(input);
            case UUID:
                return validateUuid(input);
            case AMOUNT:
                return validateAmount(input);
            case DATE:
                return validateDate(input);
            case ACCOUNT_NUMBER:
                return validateAccountNumber(input);
            case TRANSACTION_ID:
                return validateTransactionId(input);
            case SQL_PARAMETER:
                return validateSqlParameter(input);
            case JSON:
                return validateJson(input);
            case XML:
                return validateXml(input);
            case FILE_PATH:
                return validateFilePath(input);
            case COMMAND:
                return validateCommand(input);
            default:
                return validateGenericText(input);
        }
    }
    
    /**
     * Perform comprehensive security checks
     */
    private ValidationResult performSecurityChecks(String input, InputType type, ValidationContext context) {
        // SQL Injection check
        if (SQL_INJECTION_PATTERN.matcher(input).find()) {
            return ValidationResult.invalid("Potential SQL injection detected");
        }
        
        // XSS check
        if (type != InputType.HTML && XSS_PATTERN.matcher(input).find()) {
            return ValidationResult.invalid("Potential XSS attack detected");
        }
        
        // Command injection check
        if (type != InputType.COMMAND && COMMAND_INJECTION_PATTERN.matcher(input).find()) {
            return ValidationResult.invalid("Potential command injection detected");
        }
        
        // Path traversal check
        if (PATH_TRAVERSAL_PATTERN.matcher(input).find()) {
            return ValidationResult.invalid("Potential path traversal detected");
        }
        
        // LDAP injection check
        if (type == InputType.LDAP_QUERY && LDAP_INJECTION_PATTERN.matcher(input).find()) {
            return ValidationResult.invalid("Potential LDAP injection detected");
        }
        
        // XML injection check
        if (type == InputType.XML && XML_INJECTION_PATTERN.matcher(input).find()) {
            return ValidationResult.invalid("Potential XML injection detected");
        }
        
        // NoSQL injection check
        if (NOSQL_INJECTION_PATTERN.matcher(input).find()) {
            return ValidationResult.invalid("Potential NoSQL injection detected");
        }
        
        // Check for null bytes
        if (input.contains("\0")) {
            return ValidationResult.invalid("Null byte injection detected");
        }
        
        // Check for Unicode control characters
        if (containsControlCharacters(input)) {
            return ValidationResult.invalid("Control characters detected");
        }
        
        return ValidationResult.valid(input);
    }
    
    /**
     * Validate email addresses
     */
    private ValidationResult validateEmail(String email) {
        if (!EmailValidator.getInstance().isValid(email)) {
            return ValidationResult.invalid("Invalid email format");
        }
        
        // Additional checks for email header injection
        if (email.contains("\n") || email.contains("\r") || email.contains("%0a") || email.contains("%0d")) {
            return ValidationResult.invalid("Email header injection detected");
        }
        
        // Check for homograph attacks in domain
        String domain = email.substring(email.indexOf('@') + 1);
        if (containsHomographs(domain)) {
            return ValidationResult.invalid("Potential homograph attack in email domain");
        }
        
        return ValidationResult.valid(email.toLowerCase());
    }
    
    /**
     * Validate phone numbers
     */
    private ValidationResult validatePhone(String phone) {
        // Remove common formatting characters
        String cleaned = phone.replaceAll("[\\s\\-\\(\\)]", "");
        
        if (!PHONE_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.invalid("Invalid phone number format");
        }
        
        // Check for reasonable length (3-15 digits for E.164)
        if (cleaned.length() < 3 || cleaned.length() > 15) {
            return ValidationResult.invalid("Phone number length invalid");
        }
        
        return ValidationResult.valid(cleaned);
    }
    
    /**
     * Validate usernames
     */
    private ValidationResult validateUsername(String username) {
        // Length check
        if (username.length() < 3 || username.length() > 30) {
            return ValidationResult.invalid("Username must be between 3 and 30 characters");
        }
        
        // Pattern check - alphanumeric with underscores and hyphens
        if (!username.matches("^[a-zA-Z0-9_-]+$")) {
            return ValidationResult.invalid("Username contains invalid characters");
        }
        
        // Check for SQL keywords
        String upper = username.toUpperCase();
        for (String keyword : SQL_KEYWORDS) {
            if (upper.contains(keyword)) {
                return ValidationResult.invalid("Username contains restricted keywords");
            }
        }
        
        return ValidationResult.valid(username);
    }
    
    /**
     * Validate passwords with security requirements
     */
    private ValidationResult validatePassword(String password) {
        // Length check
        if (password.length() < 8 || password.length() > 128) {
            return ValidationResult.invalid("Password must be between 8 and 128 characters");
        }
        
        // Complexity requirements
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasDigit = password.matches(".*[0-9].*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
        
        if (!(hasUpper && hasLower && hasDigit && hasSpecial)) {
            return ValidationResult.invalid("Password must contain uppercase, lowercase, digit, and special character");
        }
        
        // Check for common patterns
        if (containsCommonPatterns(password)) {
            return ValidationResult.invalid("Password contains common patterns");
        }
        
        return ValidationResult.valid(password);
    }
    
    /**
     * Validate URLs
     */
    private ValidationResult validateUrl(String url) {
        UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
        
        if (!urlValidator.isValid(url)) {
            return ValidationResult.invalid("Invalid URL format");
        }
        
        // Check for javascript: and data: schemes
        String lower = url.toLowerCase();
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            return ValidationResult.invalid("Dangerous URL scheme detected");
        }
        
        // Check for SSRF attempts
        if (isInternalUrl(url)) {
            return ValidationResult.invalid("Internal URL not allowed");
        }
        
        return ValidationResult.valid(url);
    }
    
    /**
     * Validate UUIDs
     */
    private ValidationResult validateUuid(String uuid) {
        if (!UUID_PATTERN.matcher(uuid).matches()) {
            return ValidationResult.invalid("Invalid UUID format");
        }
        
        try {
            UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return ValidationResult.invalid("Invalid UUID");
        }
        
        return ValidationResult.valid(uuid.toLowerCase());
    }
    
    /**
     * Validate monetary amounts
     */
    private ValidationResult validateAmount(String amount) {
        try {
            BigDecimal value = new BigDecimal(amount);
            
            // Check for negative amounts
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                return ValidationResult.invalid("Amount cannot be negative");
            }
            
            // Check for reasonable maximum
            if (value.compareTo(new BigDecimal("1000000000")) > 0) {
                return ValidationResult.invalid("Amount exceeds maximum allowed");
            }
            
            // Check decimal places (max 2 for currency)
            if (value.scale() > 2) {
                return ValidationResult.invalid("Amount has too many decimal places");
            }
            
            return ValidationResult.valid(value.toString());
            
        } catch (NumberFormatException e) {
            return ValidationResult.invalid("Invalid amount format");
        }
    }
    
    /**
     * Validate account numbers
     */
    private ValidationResult validateAccountNumber(String accountNumber) {
        // Remove spaces and hyphens
        String cleaned = accountNumber.replaceAll("[\\s-]", "");

        // Check if numeric
        if (!NUMERIC_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.invalid("Account number must be numeric");
        }

        // Length check (typical range)
        if (cleaned.length() < 8 || cleaned.length() > 20) {
            return ValidationResult.invalid("Invalid account number length");
        }

        // Luhn algorithm check for credit card numbers
        if (cleaned.length() == 16 && !isValidLuhn(cleaned)) {
            return ValidationResult.invalid("Invalid account number checksum");
        }

        return ValidationResult.valid(cleaned);
    }

    /**
     * Validate card numbers (credit/debit cards)
     */
    public ValidationResult validateCardNumber(String cardNumber) {
        // Remove spaces and hyphens
        String cleaned = cardNumber.replaceAll("[\\s-]", "");

        // Check if numeric
        if (!NUMERIC_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.invalid("Card number must be numeric");
        }

        // Card numbers are typically 13-19 digits
        if (cleaned.length() < 13 || cleaned.length() > 19) {
            return ValidationResult.invalid("Invalid card number length");
        }

        // Validate using Luhn algorithm
        if (!isValidLuhn(cleaned)) {
            return ValidationResult.invalid("Invalid card number checksum");
        }

        // Check for known test card numbers (should be blocked in production)
        if (isTestCardNumber(cleaned)) {
            return ValidationResult.invalid("Test card number not allowed in production");
        }

        return ValidationResult.valid(cleaned);
    }

    /**
     * Check if card number is a known test card number
     */
    private boolean isTestCardNumber(String cardNumber) {
        // Common test card numbers
        Set<String> testCards = Set.of(
            "4111111111111111",  // Visa test
            "5555555555554444",  // Mastercard test
            "378282246310005",   // Amex test
            "6011111111111117",  // Discover test
            "3530111333300000",  // JCB test
            "4012888888881881"   // Visa test
        );

        return testCards.contains(cardNumber);
    }
    
    /**
     * Sanitize input based on type
     */
    private String sanitizeInput(String input, InputType type) {
        switch (type) {
            case HTML:
                return Jsoup.clean(input, Safelist.basic());
            case SQL_PARAMETER:
                // SQL escaping removed in newer versions - use parameterized queries instead
                // For safety, we'll sanitize by removing dangerous characters
                return input.replaceAll("[';\"\\--]", "");
            case XML:
                return StringEscapeUtils.escapeXml11(input);
            case JSON:
                return StringEscapeUtils.escapeJson(input);
            case JAVASCRIPT:
                return Encode.forJavaScript(input);
            case URL:
                return Encode.forUriComponent(input);
            case HTML_ATTRIBUTE:
                return Encode.forHtmlAttribute(input);
            default:
                return Encode.forHtml(input);
        }
    }
    
    /**
     * Check for control characters
     */
    private boolean containsControlCharacters(String input) {
        for (char c : input.toCharArray()) {
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check for homograph attacks
     */
    private boolean containsHomographs(String input) {
        for (char c : input.toCharArray()) {
            if (HOMOGRAPH_MAP.containsKey(c)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Initialize homograph detection map
     */
    private void initializeHomographMap() {
        // Cyrillic lookalikes
        HOMOGRAPH_MAP.put('а', 'a'); // Cyrillic
        HOMOGRAPH_MAP.put('е', 'e'); // Cyrillic
        HOMOGRAPH_MAP.put('о', 'o'); // Cyrillic
        HOMOGRAPH_MAP.put('р', 'p'); // Cyrillic
        HOMOGRAPH_MAP.put('с', 'c'); // Cyrillic
        HOMOGRAPH_MAP.put('у', 'y'); // Cyrillic
        HOMOGRAPH_MAP.put('х', 'x'); // Cyrillic
        
        // Greek lookalikes
        HOMOGRAPH_MAP.put('Α', 'A'); // Greek
        HOMOGRAPH_MAP.put('Β', 'B'); // Greek
        HOMOGRAPH_MAP.put('Ε', 'E'); // Greek
        HOMOGRAPH_MAP.put('Ζ', 'Z'); // Greek
        HOMOGRAPH_MAP.put('Η', 'H'); // Greek
        HOMOGRAPH_MAP.put('Ι', 'I'); // Greek
        HOMOGRAPH_MAP.put('Κ', 'K'); // Greek
        HOMOGRAPH_MAP.put('Μ', 'M'); // Greek
        HOMOGRAPH_MAP.put('Ν', 'N'); // Greek
        HOMOGRAPH_MAP.put('Ο', 'O'); // Greek
        HOMOGRAPH_MAP.put('Ρ', 'P'); // Greek
        HOMOGRAPH_MAP.put('Τ', 'T'); // Greek
        HOMOGRAPH_MAP.put('Υ', 'Y'); // Greek
        HOMOGRAPH_MAP.put('Χ', 'X'); // Greek
    }
    
    /**
     * Normalize Unicode to prevent attacks
     */
    private String normalizeUnicode(String input) {
        // Convert to ASCII-compatible encoding
        String normalized = IDN.toASCII(input, IDN.USE_STD3_ASCII_RULES);
        
        // Remove zero-width characters
        normalized = normalized.replaceAll("[\\u200B\\u200C\\u200D\\uFEFF]", "");
        
        // Remove directional override characters
        normalized = normalized.replaceAll("[\\u202A\\u202B\\u202C\\u202D\\u202E]", "");
        
        return normalized;
    }
    
    /**
     * Validate length based on input type
     */
    private boolean validateLength(String input, InputType type) {
        int maxLength = getMaxLength(type);
        return input.length() <= maxLength;
    }
    
    /**
     * Get maximum allowed length for input type
     */
    private int getMaxLength(InputType type) {
        switch (type) {
            case EMAIL:
                return 254; // RFC 5321
            case PHONE:
                return 15; // E.164
            case USERNAME:
                return 30;
            case PASSWORD:
                return 128;
            case URL:
                return 2048;
            case UUID:
                return 36;
            case AMOUNT:
                return 20;
            case ACCOUNT_NUMBER:
                return 20;
            case TRANSACTION_ID:
                return 50;
            default:
                return 1000;
        }
    }
    
    /**
     * Check for common password patterns
     */
    private boolean containsCommonPatterns(String password) {
        String lower = password.toLowerCase();
        
        // Check for keyboard patterns
        String[] keyboardPatterns = {"qwerty", "asdf", "zxcv", "1234", "4321", "1111"};
        for (String pattern : keyboardPatterns) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        
        // Check for repeated characters
        Pattern repeatedChars = Pattern.compile("(.)\\1{2,}");
        if (repeatedChars.matcher(password).find()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if URL points to internal network
     */
    private boolean isInternalUrl(String url) {
        String lower = url.toLowerCase();
        return lower.contains("localhost") ||
               lower.contains("127.0.0.1") ||
               lower.contains("::1") ||
               lower.contains("169.254") ||
               lower.contains("10.") ||
               lower.contains("172.16") ||
               lower.contains("192.168") ||
               lower.contains(".local");
    }
    
    /**
     * Validate date strings
     */
    private ValidationResult validateDate(String date) {
        try {
            // Try common date formats
            String[] formats = {
                "yyyy-MM-dd",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
            };
            
            for (String format : formats) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                    LocalDateTime.parse(date, formatter);
                    return ValidationResult.valid(date);
                } catch (Exception e) {
                    // Try next format
                }
            }
            
            return ValidationResult.invalid("Invalid date format");
            
        } catch (Exception e) {
            return ValidationResult.invalid("Invalid date");
        }
    }
    
    /**
     * Validate transaction IDs
     */
    private ValidationResult validateTransactionId(String transactionId) {
        // Check format (alphanumeric with hyphens)
        if (!transactionId.matches("^[A-Za-z0-9-]+$")) {
            return ValidationResult.invalid("Invalid transaction ID format");
        }
        
        // Length check
        if (transactionId.length() < 10 || transactionId.length() > 50) {
            return ValidationResult.invalid("Invalid transaction ID length");
        }
        
        return ValidationResult.valid(transactionId);
    }
    
    /**
     * Validate SQL parameters
     */
    private ValidationResult validateSqlParameter(String param) {
        // Check for SQL injection patterns
        if (SQL_INJECTION_PATTERN.matcher(param).find()) {
            return ValidationResult.invalid("SQL injection pattern detected");
        }
        
        // Check for comment sequences
        if (param.contains("--") || param.contains("/*") || param.contains("*/")) {
            return ValidationResult.invalid("SQL comment detected");
        }
        
        // SQL escaping removed - sanitize dangerous characters for safety
        return ValidationResult.valid(param.replaceAll("[';\"\\--]", ""));
    }
    
    /**
     * Validate JSON input
     */
    private ValidationResult validateJson(String json) {
        try {
            objectMapper.readTree(json);
            
            // Check for dangerous content
            if (json.contains("<script") || json.contains("javascript:")) {
                return ValidationResult.invalid("Dangerous content in JSON");
            }
            
            return ValidationResult.valid(json);
            
        } catch (Exception e) {
            return ValidationResult.invalid("Invalid JSON format");
        }
    }
    
    /**
     * Validate XML input
     */
    private ValidationResult validateXml(String xml) {
        // Check for XXE attack patterns
        if (XML_INJECTION_PATTERN.matcher(xml).find()) {
            return ValidationResult.invalid("XML injection pattern detected");
        }
        
        // Check for external entities
        if (xml.contains("<!ENTITY") || xml.contains("SYSTEM") || xml.contains("PUBLIC")) {
            return ValidationResult.invalid("External entities not allowed");
        }
        
        return ValidationResult.valid(StringEscapeUtils.escapeXml11(xml));
    }
    
    /**
     * Validate file paths
     */
    private ValidationResult validateFilePath(String path) {
        // Check for path traversal
        if (PATH_TRAVERSAL_PATTERN.matcher(path).find()) {
            return ValidationResult.invalid("Path traversal detected");
        }
        
        // Check for null bytes
        if (path.contains("\0") || path.contains("%00")) {
            return ValidationResult.invalid("Null byte in path");
        }
        
        // Check for dangerous extensions
        String lower = path.toLowerCase();
        for (String ext : DANGEROUS_FILE_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return ValidationResult.invalid("Dangerous file extension");
            }
        }
        
        return ValidationResult.valid(path);
    }
    
    /**
     * Validate command input
     */
    private ValidationResult validateCommand(String command) {
        // Very restrictive - only allow specific whitelisted commands
        Set<String> allowedCommands = ImmutableSet.of("status", "info", "list", "get", "set");
        
        if (!allowedCommands.contains(command.toLowerCase())) {
            return ValidationResult.invalid("Command not allowed");
        }
        
        return ValidationResult.valid(command);
    }
    
    /**
     * Generic text validation
     */
    private ValidationResult validateGenericText(String text) {
        // Remove control characters except newlines and tabs
        String cleaned = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        
        // Check for suspicious patterns
        if (SQL_INJECTION_PATTERN.matcher(cleaned).find() ||
            XSS_PATTERN.matcher(cleaned).find() ||
            COMMAND_INJECTION_PATTERN.matcher(cleaned).find()) {
            return ValidationResult.invalid("Suspicious patterns detected");
        }
        
        return ValidationResult.valid(cleaned);
    }
    
    /**
     * Validate business logic constraints
     */
    private ValidationResult validateBusinessLogic(String input, InputType type, ValidationContext context) {
        // Add business-specific validation rules here
        
        // Example: Check if email is from allowed domain
        if (type == InputType.EMAIL && context.isRestrictedDomain()) {
            String domain = input.substring(input.indexOf('@') + 1);
            if (!context.getAllowedDomains().contains(domain)) {
                return ValidationResult.invalid("Email domain not allowed");
            }
        }
        
        // Example: Check amount limits
        if (type == InputType.AMOUNT && context.hasAmountLimits()) {
            try {
                BigDecimal amount = new BigDecimal(input);
                if (amount.compareTo(context.getMaxAmount()) > 0) {
                    return ValidationResult.invalid("Amount exceeds maximum limit");
                }
                if (amount.compareTo(context.getMinAmount()) < 0) {
                    return ValidationResult.invalid("Amount below minimum limit");
                }
            } catch (NumberFormatException e) {
                return ValidationResult.invalid("Invalid amount");
            }
        }
        
        return ValidationResult.valid(input);
    }
    
    /**
     * Luhn algorithm validation
     */
    private boolean isValidLuhn(String number) {
        int sum = 0;
        boolean alternate = false;
        
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(number.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return (sum % 10 == 0);
    }
    
    /**
     * Rate limiting check
     */
    private boolean checkRateLimit(String userId) {
        RateLimiter limiter = rateLimiters.computeIfAbsent(userId, 
            k -> new RateLimiter(100, 60000)); // 100 requests per minute
        
        return limiter.tryAcquire();
    }
    
    /**
     * Log security violations for monitoring
     */
    private void logSecurityViolation(ValidationContext context, String input, String error) {
        log.error("SECURITY VIOLATION - User: {}, IP: {}, Input Type: {}, Error: {}, Input Sample: {}", 
            context.getUserId(),
            context.getIpAddress(),
            context.getInputType(),
            error,
            truncateForLogging(input));
        
        // Send alert for critical violations
        if (error.contains("injection") || error.contains("attack")) {
            sendSecurityAlert(context, error);
        }
    }
    
    /**
     * Truncate input for safe logging
     */
    private String truncateForLogging(String input) {
        if (input.length() <= 50) {
            return input.replaceAll("[\r\n]", " ");
        }
        return input.substring(0, 50).replaceAll("[\r\n]", " ") + "...";
    }
    
    /**
     * Send security alert
     */
    private void sendSecurityAlert(ValidationContext context, String error) {
        // Implementation for sending security alerts
        // This could be email, SMS, Slack, etc.
        log.error("CRITICAL SECURITY ALERT: Potential attack from user {} at IP {}: {}", 
            context.getUserId(), context.getIpAddress(), error);
    }
    
    /**
     * Input types enumeration
     */
    public enum InputType {
        EMAIL, PHONE, USERNAME, PASSWORD, URL, UUID,
        AMOUNT, DATE, ACCOUNT_NUMBER, TRANSACTION_ID,
        SQL_PARAMETER, JSON, XML, HTML, JAVASCRIPT,
        FILE_PATH, COMMAND, LDAP_QUERY, HTML_ATTRIBUTE,
        GENERIC_TEXT
    }
    
    /**
     * Validation result
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String value;
        private final String error;
        
        private ValidationResult(boolean valid, String value, String error) {
            this.valid = valid;
            this.value = value;
            this.error = error;
        }
        
        public static ValidationResult valid(String sanitizedValue) {
            return new ValidationResult(true, sanitizedValue, null);
        }
        
        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, null, error);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getValue() {
            return value;
        }
        
        public String getError() {
            return error;
        }
    }
    
    /**
     * Validation context
     */
    public static class ValidationContext {
        private final String userId;
        private final String ipAddress;
        private final InputType inputType;
        private final Map<String, Object> additionalParams;
        
        public ValidationContext(String userId, String ipAddress, InputType inputType) {
            this.userId = userId;
            this.ipAddress = ipAddress;
            this.inputType = inputType;
            this.additionalParams = new HashMap<>();
        }
        
        // Getters and additional context methods
        public String getUserId() { return userId; }
        public String getIpAddress() { return ipAddress; }
        public InputType getInputType() { return inputType; }
        
        public boolean isRestrictedDomain() {
            return additionalParams.containsKey("restrictedDomain");
        }
        
        public Set<String> getAllowedDomains() {
            return (Set<String>) additionalParams.getOrDefault("allowedDomains", new HashSet<>());
        }
        
        public boolean hasAmountLimits() {
            return additionalParams.containsKey("maxAmount");
        }
        
        public BigDecimal getMaxAmount() {
            return (BigDecimal) additionalParams.get("maxAmount");
        }
        
        public BigDecimal getMinAmount() {
            return (BigDecimal) additionalParams.getOrDefault("minAmount", BigDecimal.ZERO);
        }
    }
    
    /**
     * Simple rate limiter
     */
    private static class RateLimiter {
        private final int maxRequests;
        private final long windowMs;
        private final Queue<Long> requestTimes;
        
        public RateLimiter(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
            this.requestTimes = new LinkedList<>();
        }
        
        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            
            // Remove old requests outside window
            while (!requestTimes.isEmpty() && requestTimes.peek() < now - windowMs) {
                requestTimes.poll();
            }
            
            if (requestTimes.size() < maxRequests) {
                requestTimes.offer(now);
                return true;
            }
            
            return false;
        }
    }
}