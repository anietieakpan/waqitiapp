package com.waqiti.common.security.validation;

import com.waqiti.common.security.audit.SecurityAuditLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise-grade input validation service to prevent SQL injection,
 * XSS, and other injection attacks.
 * 
 * Features:
 * - 77+ SQL injection patterns detection
 * - XSS prevention
 * - Path traversal protection
 * - Command injection prevention
 * - Input sanitization and normalization
 * - Rate limiting for validation requests
 * - Comprehensive audit logging
 */
@Slf4j
@Service
public class SecureInputValidator {
    
    private final SecurityAuditLogger auditLogger;
    
    public SecureInputValidator(SecurityAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }
    
    // SQL Injection patterns - comprehensive list
    private static final String[] SQL_INJECTION_PATTERNS = {
        // Basic SQL injection patterns
        "(?i).*('|(\\-\\-)|(;)|(\\||\\|)|(\\*|\\*)).*",
        "(?i).*(union|select|insert|delete|update|drop|create|alter|exec|execute).*",
        "(?i).*(script|javascript|vbscript|onload|onerror|onclick).*",
        "(?i).*(eval|expression|url|import).*",
        "(?i).*(information_schema|sysobjects|syscolumns).*",
        "(?i).*(concat|group_concat|string_agg).*",
        "(?i).*(cast|convert|substring|ascii|char).*",
        "(?i).*(waitfor|delay|benchmark|sleep).*",
        "(?i).*(load_file|into|outfile|dumpfile).*",
        "(?i).*(hex|unhex|md5|sha1|password).*",
        
        // Advanced SQL injection patterns
        "(?i).*('\\s*(or|and)\\s*'[\\d\\w]*'\\s*=\\s*'[\\d\\w]*').*",
        "(?i).*('\\s*(or|and)\\s*[\\d\\w]*\\s*=\\s*[\\d\\w]*).*",
        "(?i).*(1\\s*=\\s*1|1=1|'1'='1').*",
        "(?i).*(0\\s*=\\s*0|0=0|'0'='0').*",
        "(?i).*('\\s*(or|and)\\s*true).*",
        "(?i).*('\\s*(or|and)\\s*false).*",
        "(?i).*(\\b(sp_|xp_)\\w+).*",
        "(?i).*(@@version|@@servername|@@database).*",
        "(?i).*(user\\(\\)|database\\(\\)|version\\(\\)).*",
        "(?i).*(current_user|session_user|system_user).*",
        
        // Union-based injection
        "(?i).*(union\\s+all\\s+select).*",
        "(?i).*(union\\s+select).*",
        "(?i).*(union.*null).*",
        "(?i).*(order\\s+by\\s+\\d+).*",
        "(?i).*(group\\s+by\\s+\\d+).*",
        "(?i).*(having\\s+\\d+).*",
        
        // Boolean-based blind injection
        "(?i).*(and\\s+\\d+\\s*=\\s*\\d+).*",
        "(?i).*(or\\s+\\d+\\s*=\\s*\\d+).*",
        "(?i).*(and\\s+'\\w+'\\s*=\\s*'\\w+').*",
        "(?i).*(or\\s+'\\w+'\\s*=\\s*'\\w+').*",
        "(?i).*(and\\s+ascii\\s*\\().*",
        "(?i).*(and\\s+substring\\s*\\().*",
        "(?i).*(and\\s+length\\s*\\().*",
        
        // Time-based blind injection
        "(?i).*(and\\s+sleep\\s*\\().*",
        "(?i).*(or\\s+sleep\\s*\\().*",
        "(?i).*(waitfor\\s+delay).*",
        "(?i).*(benchmark\\s*\\().*",
        "(?i).*(pg_sleep\\s*\\().*",
        
        // Error-based injection
        "(?i).*(extractvalue\\s*\\().*",
        "(?i).*(updatexml\\s*\\().*",
        "(?i).*(exp\\s*\\().*",
        "(?i).*(floor\\s*\\().*",
        "(?i).*(rand\\s*\\().*",
        "(?i).*(count\\s*\\().*",
        "(?i).*(group\\s+by\\s+concat).*",
        
        // Function-based injection
        "(?i).*(load_file\\s*\\().*",
        "(?i).*(into\\s+outfile).*",
        "(?i).*(into\\s+dumpfile).*",
        "(?i).*(hex\\s*\\().*",
        "(?i).*(unhex\\s*\\().*",
        "(?i).*(compress\\s*\\().*",
        "(?i).*(uncompress\\s*\\().*",
        
        // Comment-based evasion
        "(?i).*(/\\*.*\\*/).*",
        "(?i).*(\\-\\-.*$).*",
        "(?i).*(#.*$).*",
        "(?i).*(/\\*!.*\\*/).*",
        
        // Encoding-based evasion
        "(?i).*(0x[0-9a-f]+).*",
        "(?i).*(char\\s*\\().*",
        "(?i).*(nchar\\s*\\().*",
        "(?i).*(varchar\\s*\\().*",
        "(?i).*(nvarchar\\s*\\().*",
        
        // Database-specific patterns
        "(?i).*(msdasql|sqloledb|msdaora).*",
        "(?i).*(master\\.dbo|tempdb|model|msdb).*",
        "(?i).*(sysdatabases|sysusers|systables).*",
        "(?i).*(information_schema\\.).*",
        "(?i).*(pg_database|pg_user|pg_tables).*",
        "(?i).*(mysql\\.|performance_schema\\.).*",
        
        // NoSQL injection patterns
        "(?i).*({\\s*\\$.*}).*",
        "(?i).*(\\$where|\\$regex|\\$gt|\\$lt).*",
        "(?i).*(\\$ne|\\$in|\\$nin|\\$exists).*",
        "(?i).*(\\$or|\\$and|\\$not|\\$nor).*",
        "(?i).*(this\\s*==|this\\s*!=).*",
        "(?i).*(eval\\s*\\(|function\\s*\\().*",
        
        // LDAP injection patterns
        "(?i).*((\\)|\\(|\\&|\\||\\!|\\=|\\~|\\>|\\<)\\s*\\w+).*",
        "(?i).*(objectclass\\s*=).*",
        "(?i).*(cn\\s*=|uid\\s*=|ou\\s*=).*",
        
        // XPath injection patterns
        "(?i).*(\\[.*\\]).*",
        "(?i).*(ancestor\\s*::|descendant\\s*::).*",
        "(?i).*(following\\s*::|preceding\\s*::).*",
        "(?i).*(normalize-space\\s*\\().*",
        "(?i).*(string-length\\s*\\().*",
        
        // Additional patterns for completeness
        "(?i).*(declare\\s+@|declare\\s+\\w+).*",
        "(?i).*(openrowset|opendatasource).*",
        "(?i).*(bulk\\s+insert|bcp).*",
        "(?i).*(grant|revoke|deny).*",
        "(?i).*(backup|restore|dbcc).*"
    };
    
    // XSS patterns
    private static final String[] XSS_PATTERNS = {
        "(?i).*<script.*>.*</script>.*",
        "(?i).*javascript\\s*:.*",
        "(?i).*vbscript\\s*:.*",
        "(?i).*on(load|click|error|focus|blur|change|submit)\\s*=.*",
        "(?i).*expression\\s*\\(.*",
        "(?i).*<iframe.*>.*",
        "(?i).*<object.*>.*",
        "(?i).*<embed.*>.*",
        "(?i).*<applet.*>.*",
        "(?i).*<form.*>.*",
        "(?i).*<input.*>.*",
        "(?i).*<img.*onerror.*>.*",
        "(?i).*<svg.*onload.*>.*",
        "(?i).*data\\s*:.*text/html.*",
        "(?i).*document\\.cookie.*",
        "(?i).*document\\.write.*",
        "(?i).*eval\\s*\\(.*",
        "(?i).*setTimeout\\s*\\(.*",
        "(?i).*setInterval\\s*\\(.*"
    };
    
    // Path traversal patterns
    private static final String[] PATH_TRAVERSAL_PATTERNS = {
        ".*\\.\\.[\\\\/].*",
        ".*[\\\\/]\\.\\..*",
        ".*%2e%2e[\\\\/].*",
        ".*[\\\\/]%2e%2e.*",
        ".*%252e%252e[\\\\/].*",
        ".*[\\\\/]%252e%252e.*",
        ".*\\.\\.%2f.*",
        ".*%2f\\.\\..*",
        ".*\\.\\.%5c.*",
        ".*%5c\\.\\..*"
    };
    
    // Command injection patterns
    private static final String[] COMMAND_INJECTION_PATTERNS = {
        ".*[;&|`$()].*",
        ".*\\\\x[0-9a-f]{2}.*",
        ".*%[0-9a-f]{2}.*",
        ".*(cat|ls|dir|type|more|less)\\s+.*",
        ".*(rm|del|rmdir|rd)\\s+.*",
        ".*(chmod|chown|sudo)\\s+.*",
        ".*(wget|curl|nc|netcat)\\s+.*",
        ".*(echo|printf)\\s+.*",
        ".*(bash|sh|cmd|powershell)\\s+.*"
    };
    
    private final Set<Pattern> sqlInjectionPatterns = new HashSet<>();
    private final Set<Pattern> xssPatterns = new HashSet<>();
    private final Set<Pattern> pathTraversalPatterns = new HashSet<>();
    private final Set<Pattern> commandInjectionPatterns = new HashSet<>();
    
    // Rate limiting for validation requests
    private final Map<String, AtomicInteger> clientRequestCounts = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 1000;
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Secure Input Validator");
        
        // Compile SQL injection patterns
        for (String pattern : SQL_INJECTION_PATTERNS) {
            try {
                sqlInjectionPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                log.warn("Failed to compile SQL injection pattern: {}", pattern, e);
            }
        }
        
        // Compile XSS patterns
        for (String pattern : XSS_PATTERNS) {
            try {
                xssPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                log.warn("Failed to compile XSS pattern: {}", pattern, e);
            }
        }
        
        // Compile path traversal patterns
        for (String pattern : PATH_TRAVERSAL_PATTERNS) {
            try {
                pathTraversalPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                log.warn("Failed to compile path traversal pattern: {}", pattern, e);
            }
        }
        
        // Compile command injection patterns
        for (String pattern : COMMAND_INJECTION_PATTERNS) {
            try {
                commandInjectionPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                log.warn("Failed to compile command injection pattern: {}", pattern, e);
            }
        }
        
        log.info("Secure Input Validator initialized with {} SQL injection patterns, {} XSS patterns", 
                sqlInjectionPatterns.size(), xssPatterns.size());
    }
    
    /**
     * Comprehensive input validation
     */
    public ValidationResult validateInput(String input, ValidationContext context) {
        if (input == null) {
            return ValidationResult.success();
        }
        
        // Check rate limiting
        if (!checkRateLimit(context.getClientId())) {
            auditLogger.logRateLimitExceeded("INPUT_VALIDATION", context.getClientId());
            return ValidationResult.failure("Rate limit exceeded", VulnerabilityType.RATE_LIMIT);
        }
        
        try {
            // Check for SQL injection
            ValidationResult sqlResult = checkSqlInjection(input, context);
            if (!sqlResult.isValid()) {
                return sqlResult;
            }
            
            // Check for XSS
            ValidationResult xssResult = checkXSS(input, context);
            if (!xssResult.isValid()) {
                return xssResult;
            }
            
            // Check for path traversal
            ValidationResult pathResult = checkPathTraversal(input, context);
            if (!pathResult.isValid()) {
                return pathResult;
            }
            
            // Check for command injection
            ValidationResult cmdResult = checkCommandInjection(input, context);
            if (!cmdResult.isValid()) {
                return cmdResult;
            }
            
            // Additional validations based on context
            ValidationResult contextResult = validateByContext(input, context);
            if (!contextResult.isValid()) {
                return contextResult;
            }
            
            auditLogger.logInputValidationSuccess(context.getField(), input.length());
            return ValidationResult.success();
            
        } catch (Exception e) {
            log.error("Input validation failed", e);
            auditLogger.logInputValidationError(context.getField(), e.getMessage());
            return ValidationResult.failure("Validation error", VulnerabilityType.UNKNOWN);
        }
    }
    
    /**
     * Check for SQL injection patterns
     */
    public ValidationResult checkSqlInjection(String input, ValidationContext context) {
        if (input == null || input.trim().isEmpty()) {
            return ValidationResult.success();
        }
        
        String normalizedInput = normalizeInput(input);
        
        for (Pattern pattern : sqlInjectionPatterns) {
            if (pattern.matcher(normalizedInput).matches()) {
                auditLogger.logSecurityThreat("SQL_INJECTION_ATTEMPT", context.getField(), 
                        context.getClientId(), sanitizeForLogging(input));
                return ValidationResult.failure("SQL injection detected", VulnerabilityType.SQL_INJECTION);
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Check for XSS patterns
     */
    public ValidationResult checkXSS(String input, ValidationContext context) {
        if (input == null || input.trim().isEmpty()) {
            return ValidationResult.success();
        }
        
        String normalizedInput = normalizeInput(input);
        
        for (Pattern pattern : xssPatterns) {
            if (pattern.matcher(normalizedInput).matches()) {
                auditLogger.logSecurityThreat("XSS_ATTEMPT", context.getField(), 
                        context.getClientId(), sanitizeForLogging(input));
                return ValidationResult.failure("XSS detected", VulnerabilityType.XSS);
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Check for path traversal patterns
     */
    public ValidationResult checkPathTraversal(String input, ValidationContext context) {
        if (input == null || input.trim().isEmpty()) {
            return ValidationResult.success();
        }
        
        String normalizedInput = normalizeInput(input);
        
        for (Pattern pattern : pathTraversalPatterns) {
            if (pattern.matcher(normalizedInput).matches()) {
                auditLogger.logSecurityThreat("PATH_TRAVERSAL_ATTEMPT", context.getField(), 
                        context.getClientId(), sanitizeForLogging(input));
                return ValidationResult.failure("Path traversal detected", VulnerabilityType.PATH_TRAVERSAL);
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Check for command injection patterns
     */
    public ValidationResult checkCommandInjection(String input, ValidationContext context) {
        if (input == null || input.trim().isEmpty()) {
            return ValidationResult.success();
        }
        
        String normalizedInput = normalizeInput(input);
        
        for (Pattern pattern : commandInjectionPatterns) {
            if (pattern.matcher(normalizedInput).matches()) {
                auditLogger.logSecurityThreat("COMMAND_INJECTION_ATTEMPT", context.getField(), 
                        context.getClientId(), sanitizeForLogging(input));
                return ValidationResult.failure("Command injection detected", VulnerabilityType.COMMAND_INJECTION);
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Context-specific validation
     */
    public ValidationResult validateByContext(String input, ValidationContext context) {
        switch (context.getType()) {
            case EMAIL:
                return validateEmail(input, context);
            case PHONE:
                return validatePhone(input, context);
            case CURRENCY:
                return validateCurrency(input, context);
            case AMOUNT:
                return validateAmount(input, context);
            case ALPHANUMERIC:
                return validateAlphanumeric(input, context);
            case NUMERIC:
                return validateNumeric(input, context);
            case DATE:
                return validateDate(input, context);
            case UUID:
                return validateUUID(input, context);
            case JSON:
                return validateJSON(input, context);
            case URL:
                return validateURL(input, context);
            default:
                return ValidationResult.success();
        }
    }
    
    /**
     * Sanitize input for safe storage/processing
     */
    public String sanitizeInput(String input, SanitizationMode mode) {
        if (input == null) {
            return null;
        }
        
        String sanitized = input;
        
        switch (mode) {
            case HTML_ENCODE:
                sanitized = htmlEncode(sanitized);
                break;
            case SQL_ESCAPE:
                sanitized = sqlEscape(sanitized);
                break;
            case REMOVE_SPECIAL_CHARS:
                sanitized = removeSpecialChars(sanitized);
                break;
            case ALPHANUMERIC_ONLY:
                sanitized = alphanumericOnly(sanitized);
                break;
            case TRIM_AND_NORMALIZE:
                sanitized = trimAndNormalize(sanitized);
                break;
            case FULL_SANITIZATION:
                sanitized = fullSanitization(sanitized);
                break;
        }
        
        return sanitized;
    }
    
    // Private helper methods
    
    private boolean checkRateLimit(String clientId) {
        AtomicInteger count = clientRequestCounts.computeIfAbsent(clientId, k -> new AtomicInteger(0));
        return count.incrementAndGet() <= MAX_REQUESTS_PER_MINUTE;
    }
    
    private String normalizeInput(String input) {
        return input.toLowerCase()
                   .replaceAll("\\s+", " ")
                   .replaceAll("%20", " ")
                   .replaceAll("%0a", "\n")
                   .replaceAll("%0d", "\r")
                   .replaceAll("\\+", " ");
    }
    
    private String sanitizeForLogging(String input) {
        if (input == null) return "null";
        
        // Truncate and mask sensitive patterns for logging
        String sanitized = input.length() > 100 ? input.substring(0, 100) + "..." : input;
        
        // Mask common sensitive patterns
        sanitized = sanitized.replaceAll("\\b\\d{4,}\\b", "****");  // Numbers
        sanitized = sanitized.replaceAll("'[^']*'", "'****'");      // Quoted strings
        sanitized = sanitized.replaceAll("\"[^\"]*\"", "\"****\""); // Double quoted strings
        
        return sanitized;
    }
    
    private ValidationResult validateEmail(String input, ValidationContext context) {
        Pattern emailPattern = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
        );
        
        if (!emailPattern.matcher(input).matches()) {
            return ValidationResult.failure("Invalid email format", VulnerabilityType.FORMAT_VIOLATION);
        }
        
        if (input.length() > 254) {
            return ValidationResult.failure("Email too long", VulnerabilityType.FORMAT_VIOLATION);
        }
        
        return ValidationResult.success();
    }
    
    private ValidationResult validatePhone(String input, ValidationContext context) {
        String cleanPhone = input.replaceAll("[^\\d+]", "");
        
        if (cleanPhone.length() < 10 || cleanPhone.length() > 15) {
            return ValidationResult.failure("Invalid phone number length", VulnerabilityType.FORMAT_VIOLATION);
        }
        
        Pattern phonePattern = Pattern.compile("^\\+?[1-9]\\d{1,14}$");
        if (!phonePattern.matcher(cleanPhone).matches()) {
            return ValidationResult.failure("Invalid phone format", VulnerabilityType.FORMAT_VIOLATION);
        }
        
        return ValidationResult.success();
    }
    
    private ValidationResult validateCurrency(String input, ValidationContext context) {
        Pattern currencyPattern = Pattern.compile("^[A-Z]{3}$");
        
        if (!currencyPattern.matcher(input).matches()) {
            return ValidationResult.failure("Invalid currency code", VulnerabilityType.FORMAT_VIOLATION);
        }
        
        // Check against known currency codes
        Set<String> validCurrencies = Set.of(
            "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "HKD", "SGD"
        );
        
        if (!validCurrencies.contains(input)) {
            return ValidationResult.failure("Unsupported currency", VulnerabilityType.FORMAT_VIOLATION);
        }
        
        return ValidationResult.success();
    }
    
    private ValidationResult validateAmount(String input, ValidationContext context) {
        try {
            BigDecimal amount = new BigDecimal(input);
            
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                return ValidationResult.failure("Amount cannot be negative", VulnerabilityType.FORMAT_VIOLATION);
            }
            
            if (amount.scale() > 2) {
                return ValidationResult.failure("Amount has too many decimal places", VulnerabilityType.FORMAT_VIOLATION);
            }
            
            BigDecimal maxAmount = new BigDecimal("999999999.99");
            if (amount.compareTo(maxAmount) > 0) {
                return ValidationResult.failure("Amount exceeds maximum", VulnerabilityType.FORMAT_VIOLATION);
            }
            
            return ValidationResult.success();
            
        } catch (NumberFormatException e) {
            return ValidationResult.failure("Invalid amount format", VulnerabilityType.FORMAT_VIOLATION);
        }
    }
    
    private ValidationResult validateAlphanumeric(String input, ValidationContext context) {
        Pattern pattern = Pattern.compile("^[a-zA-Z0-9\\s-_]*$");
        
        if (!pattern.matcher(input).matches()) {
            return ValidationResult.failure("Only alphanumeric characters allowed", VulnerabilityType.FORMAT_VIOLATION);
        }
        
        return ValidationResult.success();
    }
    
    private ValidationResult validateNumeric(String input, ValidationContext context) {
        Pattern pattern = Pattern.compile("^\\d+$");
        
        if (!pattern.matcher(input).matches()) {
            return ValidationResult.failure("Only numeric characters allowed", VulnerabilityType.FORMAT_VIOLATION);
        }
        
        return ValidationResult.success();
    }
    
    private ValidationResult validateDate(String input, ValidationContext context) {
        Pattern isoDatePattern = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z?)?$"
        );
        
        if (!isoDatePattern.matcher(input).matches()) {
            return ValidationResult.failure("Invalid date format", VulnerabilityType.FORMAT_VIOLATION);
        }
        
        return ValidationResult.success();
    }
    
    private ValidationResult validateUUID(String input, ValidationContext context) {
        Pattern uuidPattern = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        );
        
        if (!uuidPattern.matcher(input).matches()) {
            return ValidationResult.failure("Invalid UUID format", VulnerabilityType.FORMAT_VIOLATION);
        }
        
        return ValidationResult.success();
    }
    
    private ValidationResult validateJSON(String input, ValidationContext context) {
        try {
            // Basic JSON validation - would use Jackson in real implementation
            if (!input.trim().startsWith("{") && !input.trim().startsWith("[")) {
                return ValidationResult.failure("Invalid JSON format", VulnerabilityType.FORMAT_VIOLATION);
            }
            
            // Check for potentially dangerous JSON patterns
            if (input.contains("__proto__") || input.contains("constructor") || 
                input.contains("prototype")) {
                return ValidationResult.failure("Potentially dangerous JSON", VulnerabilityType.FORMAT_VIOLATION);
            }
            
            return ValidationResult.success();
            
        } catch (Exception e) {
            return ValidationResult.failure("Invalid JSON", VulnerabilityType.FORMAT_VIOLATION);
        }
    }
    
    private ValidationResult validateURL(String input, ValidationContext context) {
        try {
            java.net.URL url = new java.net.URL(input);
            
            // Check for allowed protocols
            String protocol = url.getProtocol().toLowerCase();
            if (!Set.of("http", "https").contains(protocol)) {
                return ValidationResult.failure("Invalid URL protocol", VulnerabilityType.FORMAT_VIOLATION);
            }
            
            // Check for local/private addresses
            String host = url.getHost().toLowerCase();
            if (host.equals("localhost") || host.equals("127.0.0.1") || 
                host.startsWith("192.168.") || host.startsWith("10.") ||
                host.startsWith("172.")) {
                return ValidationResult.failure("Local URLs not allowed", VulnerabilityType.FORMAT_VIOLATION);
            }
            
            return ValidationResult.success();
            
        } catch (Exception e) {
            return ValidationResult.failure("Invalid URL format", VulnerabilityType.FORMAT_VIOLATION);
        }
    }
    
    // Sanitization methods
    
    private String htmlEncode(String input) {
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;")
                   .replace("/", "&#x2F;");
    }
    
    private String sqlEscape(String input) {
        return input.replace("'", "''")
                   .replace("\\", "\\\\")
                   .replace("\0", "\\0")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\u001a", "\\Z");
    }
    
    private String removeSpecialChars(String input) {
        return input.replaceAll("[^a-zA-Z0-9\\s]", "");
    }
    
    private String alphanumericOnly(String input) {
        return input.replaceAll("[^a-zA-Z0-9]", "");
    }
    
    private String trimAndNormalize(String input) {
        return input.trim()
                   .replaceAll("\\s+", " ")
                   .replaceAll("[\\r\\n\\t]", " ");
    }
    
    private String fullSanitization(String input) {
        return htmlEncode(sqlEscape(trimAndNormalize(input)));
    }
    
    // Enums and inner classes
    
    public enum SanitizationMode {
        HTML_ENCODE,
        SQL_ESCAPE,
        REMOVE_SPECIAL_CHARS,
        ALPHANUMERIC_ONLY,
        TRIM_AND_NORMALIZE,
        FULL_SANITIZATION
    }
    
    public enum VulnerabilityType {
        SQL_INJECTION,
        XSS,
        PATH_TRAVERSAL,
        COMMAND_INJECTION,
        FORMAT_VIOLATION,
        RATE_LIMIT,
        UNKNOWN
    }
    
    public static class ValidationContext {
        private final String field;
        private final String clientId;
        private final InputType type;
        private final Map<String, Object> parameters;
        
        public ValidationContext(String field, String clientId, InputType type) {
            this.field = field;
            this.clientId = clientId;
            this.type = type;
            this.parameters = new HashMap<>();
        }
        
        // Getters
        public String getField() { return field; }
        public String getClientId() { return clientId; }
        public InputType getType() { return type; }
        public Map<String, Object> getParameters() { return parameters; }
        
        public ValidationContext withParameter(String key, Object value) {
            parameters.put(key, value);
            return this;
        }
    }
    
    public enum InputType {
        EMAIL,
        PHONE,
        CURRENCY,
        AMOUNT,
        ALPHANUMERIC,
        NUMERIC,
        DATE,
        UUID,
        JSON,
        URL,
        FREE_TEXT
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final VulnerabilityType vulnerabilityType;
        
        private ValidationResult(boolean valid, String errorMessage, VulnerabilityType vulnerabilityType) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.vulnerabilityType = vulnerabilityType;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }
        
        public static ValidationResult failure(String errorMessage, VulnerabilityType vulnerabilityType) {
            return new ValidationResult(false, errorMessage, vulnerabilityType);
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public VulnerabilityType getVulnerabilityType() { return vulnerabilityType; }
    }
}