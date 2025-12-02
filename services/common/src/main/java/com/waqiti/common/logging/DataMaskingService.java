/**
 * Data Masking Service for Log Privacy Protection
 * Implements comprehensive data masking to protect sensitive information in logs
 * Provides configurable masking strategies and compliance with privacy regulations
 */
package com.waqiti.common.logging;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced data masking service with multiple masking strategies
 * Supports PII, financial data, authentication tokens, and custom patterns
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "logging.data-masking.enabled", havingValue = "true", matchIfMissing = true)
public class DataMaskingService {

    // Masking configuration
    @Value("${logging.data-masking.default-strategy:PARTIAL}")
    private MaskingStrategy defaultStrategy;
    
    @Value("${logging.data-masking.preserve-format:true}")
    private boolean preserveFormat;
    
    @Value("${logging.data-masking.mask-character:*}")
    private String maskCharacter;
    
    @Value("${logging.data-masking.cache-enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${logging.data-masking.cache-size:10000}")
    private int cacheSize;

    // Thread-safe caching of masked values
    private final Map<String, String> maskingCache = new ConcurrentHashMap<>();
    
    // Compiled regex patterns for performance
    private final Map<DataType, Pattern> compiledPatterns = new HashMap<>();
    
    // Custom masking functions
    private final Map<String, Function<String, String>> customMaskingFunctions = new HashMap<>();

    /**
     * Data types that require masking
     */
    public enum DataType {
        // Personal Identifiable Information (PII)
        EMAIL("(?i)\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
        PHONE_NUMBER("(?:\\+?1[-. ]?)?\\(?([0-9]{3})\\)?[-. ]?([0-9]{3})[-. ]?([0-9]{4})"),
        SSN("\\b(?!000|666|9\\d{2})\\d{3}[-.]?(?!00)\\d{2}[-.]?(?!0000)\\d{4}\\b"),
        NATIONAL_ID("\\b[A-Z]{2}[0-9]{6,12}\\b"),
        
        // Financial Data
        CREDIT_CARD("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3[0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b"),
        BANK_ACCOUNT("\\b[0-9]{8,17}\\b"),
        IBAN("\\b[A-Z]{2}[0-9]{2}[A-Z0-9]{4}[0-9]{7}([A-Z0-9]?){0,16}\\b"),
        SORT_CODE("\\b[0-9]{2}-[0-9]{2}-[0-9]{2}\\b"),
        ROUTING_NUMBER("\\b[0-9]{9}\\b"),
        
        // Authentication & Authorization
        JWT_TOKEN("\\beyJ[A-Za-z0-9_/+-]*={0,2}\\.[A-Za-z0-9_/+-]*={0,2}\\.[A-Za-z0-9_/+-]*={0,2}\\b"),
        API_KEY("\\b[Aa][Pp][Ii]_?[Kk][Ee][Yy]\\s*[:=]\\s*['\"]?[A-Za-z0-9_-]{20,50}['\"]?"),
        ACCESS_TOKEN("\\b[Aa]ccess_?[Tt]oken\\s*[:=]\\s*['\"]?[A-Za-z0-9_-]{20,100}['\"]?"),
        SESSION_ID("\\b[Ss]ession_?[Ii]d\\s*[:=]\\s*['\"]?[A-Za-z0-9_-]{20,50}['\"]?"),
        
        // Passwords and Secrets
        PASSWORD("(?i)(?:password|passwd|pwd)\\s*[:=]\\s*['\"]?[^\\s'\"]{8,}['\"]?"),
        SECRET("(?i)(?:secret|key|token)\\s*[:=]\\s*['\"]?[A-Za-z0-9_/+=]{16,}['\"]?"),
        
        // IP Addresses and Network
        IP_ADDRESS("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"),
        IPV6_ADDRESS("\\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\b"),
        MAC_ADDRESS("\\b([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})\\b"),
        
        // Database and URLs
        DATABASE_URL("\\b(?:jdbc:|mongodb:|redis:)//[^\\s]+"),
        CONNECTION_STRING("\\b(?:Server|Host|Data Source)=[^;]+;(?:.*Password=[^;]+;?.*)?"),
        
        // Custom Business Data
        CUSTOMER_ID("\\b[Cc]ustomer_?[Ii]d\\s*[:=]\\s*['\"]?[A-Za-z0-9_-]{8,20}['\"]?"),
        TRANSACTION_ID("\\b[Tt](?:ransaction|xn)_?[Ii]d\\s*[:=]\\s*['\"]?[A-Za-z0-9_-]{10,30}['\"]?"),
        ACCOUNT_NUMBER("\\b[Aa]ccount_?[Nn](?:umber|o)\\s*[:=]\\s*['\"]?[0-9]{8,20}['\"]?");

        private final String pattern;
        
        DataType(String pattern) {
            this.pattern = pattern;
        }
        
        public String getPattern() {
            return pattern;
        }
    }

    /**
     * Masking strategies
     */
    public enum MaskingStrategy {
        FULL,           // Replace entire value with asterisks
        PARTIAL,        // Show first and last characters, mask middle
        HASH,           // Replace with hash of the value
        TOKENIZE,       // Replace with consistent token
        REDACT,         // Replace with [REDACTED] text
        PRESERVE_FORMAT // Maintain format but mask characters
    }

    /**
     * Initialize the service and compile patterns
     */
    public void init() {
        log.info("Initializing Data Masking Service with strategy: {}", defaultStrategy);
        
        // Compile all regex patterns for performance
        for (DataType dataType : DataType.values()) {
            try {
                Pattern pattern = Pattern.compile(dataType.getPattern(), Pattern.MULTILINE | Pattern.DOTALL);
                compiledPatterns.put(dataType, pattern);
            } catch (Exception e) {
                log.error("Failed to compile pattern for {}: {}", dataType, e.getMessage());
            }
        }
        
        // Initialize custom masking functions
        initializeCustomMaskingFunctions();
        
        log.info("Data Masking Service initialized with {} patterns", compiledPatterns.size());
    }

    /**
     * Main masking method - masks all sensitive data in the input string
     */
    public String maskSensitiveData(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // Check cache first
        if (cacheEnabled && maskingCache.containsKey(input)) {
            return maskingCache.get(input);
        }
        
        String masked = input;
        
        // Apply masking for each data type
        for (Map.Entry<DataType, Pattern> entry : compiledPatterns.entrySet()) {
            DataType dataType = entry.getKey();
            Pattern pattern = entry.getValue();
            
            MaskingStrategy strategy = getStrategyForDataType(dataType);
            masked = maskPattern(masked, pattern, dataType, strategy);
        }
        
        // Apply custom masking functions
        for (Map.Entry<String, Function<String, String>> entry : customMaskingFunctions.entrySet()) {
            masked = entry.getValue().apply(masked);
        }
        
        // Cache the result if caching is enabled
        if (cacheEnabled && maskingCache.size() < cacheSize) {
            maskingCache.put(input, masked);
        }
        
        return masked;
    }

    /**
     * Mask specific data type with custom strategy
     */
    public String maskDataType(String input, DataType dataType, MaskingStrategy strategy) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        Pattern pattern = compiledPatterns.get(dataType);
        if (pattern == null) {
            log.warn("No pattern found for data type: {}", dataType);
            return input;
        }
        
        return maskPattern(input, pattern, dataType, strategy);
    }

    /**
     * Mask based on custom pattern
     */
    public String maskCustomPattern(String input, String patternString, MaskingStrategy strategy) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        try {
            Pattern pattern = Pattern.compile(patternString);
            return maskPattern(input, pattern, null, strategy);
        } catch (Exception e) {
            log.error("Invalid custom pattern: {}", patternString, e);
            return input;
        }
    }

    /**
     * Core pattern masking logic
     */
    private String maskPattern(String input, Pattern pattern, DataType dataType, MaskingStrategy strategy) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer masked = new StringBuffer();
        
        while (matcher.find()) {
            String originalValue = matcher.group();
            String maskedValue = applyMaskingStrategy(originalValue, dataType, strategy);
            matcher.appendReplacement(masked, Matcher.quoteReplacement(maskedValue));
        }
        matcher.appendTail(masked);
        
        return masked.toString();
    }

    /**
     * Apply specific masking strategy to a value
     */
    private String applyMaskingStrategy(String value, DataType dataType, MaskingStrategy strategy) {
        switch (strategy) {
            case FULL:
                return maskFull(value);
            case PARTIAL:
                return maskPartial(value, dataType);
            case HASH:
                return maskHash(value);
            case TOKENIZE:
                return maskTokenize(value, dataType);
            case REDACT:
                return "[REDACTED]";
            case PRESERVE_FORMAT:
                return maskPreserveFormat(value, dataType);
            default:
                return maskPartial(value, dataType);
        }
    }

    /**
     * Full masking - replace entire value with mask characters
     */
    private String maskFull(String value) {
        return maskCharacter.repeat(Math.min(value.length(), 8));
    }

    /**
     * Partial masking - show first and last characters
     */
    private String maskPartial(String value, DataType dataType) {
        if (value.length() <= 4) {
            return maskFull(value);
        }
        
        int showChars = getShowCharactersForDataType(dataType);
        int maskLength = value.length() - (2 * showChars);
        
        if (maskLength <= 0) {
            return maskFull(value);
        }
        
        return value.substring(0, showChars) + 
               maskCharacter.repeat(maskLength) + 
               value.substring(value.length() - showChars);
    }

    /**
     * Hash masking - replace with hash
     */
    private String maskHash(String value) {
        return "HASH:" + Integer.toHexString(value.hashCode()).toUpperCase();
    }

    /**
     * Tokenize masking - consistent token for same value
     */
    private String maskTokenize(String value, DataType dataType) {
        String prefix = dataType != null ? dataType.name().substring(0, 3) : "TOK";
        return prefix + ":" + String.format("%08X", value.hashCode());
    }

    /**
     * Format-preserving masking
     */
    private String maskPreserveFormat(String value, DataType dataType) {
        StringBuilder masked = new StringBuilder();
        
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                masked.append('X');
            } else if (Character.isLetter(c)) {
                masked.append(Character.isUpperCase(c) ? 'A' : 'a');
            } else {
                masked.append(c); // Preserve special characters and formatting
            }
        }
        
        return masked.toString();
    }

    /**
     * Get appropriate masking strategy for data type
     */
    private MaskingStrategy getStrategyForDataType(DataType dataType) {
        // Define specific strategies for different data types
        switch (dataType) {
            case PASSWORD:
            case SECRET:
            case API_KEY:
            case ACCESS_TOKEN:
                return MaskingStrategy.FULL;
            
            case JWT_TOKEN:
            case SESSION_ID:
                return MaskingStrategy.TOKENIZE;
            
            case CREDIT_CARD:
            case SSN:
            case BANK_ACCOUNT:
                return MaskingStrategy.PARTIAL;
            
            case EMAIL:
            case PHONE_NUMBER:
                return MaskingStrategy.PRESERVE_FORMAT;
            
            default:
                return defaultStrategy;
        }
    }

    /**
     * Get number of characters to show for partial masking
     */
    private int getShowCharactersForDataType(DataType dataType) {
        switch (dataType) {
            case CREDIT_CARD:
                return 4; // Show last 4 digits
            case EMAIL:
                return 2; // Show first 2 characters
            case PHONE_NUMBER:
                return 3; // Show first 3 digits
            case SSN:
                return 0; // Don't show any characters
            default:
                return 2;
        }
    }

    /**
     * Initialize custom masking functions
     */
    private void initializeCustomMaskingFunctions() {
        // Custom function for amount values
        customMaskingFunctions.put("amount", input -> {
            Pattern amountPattern = Pattern.compile("\\b(?:amount|value|price|cost)\\s*[:=]\\s*([0-9]+(?:\\.[0-9]{2})?)");
            return amountPattern.matcher(input).replaceAll(match -> {
                String key = match.group().split("[:=]")[0];
                return key + "=***.**";
            });
        });
        
        // Custom function for balance information
        customMaskingFunctions.put("balance", input -> {
            Pattern balancePattern = Pattern.compile("\\b(?:balance|total|sum)\\s*[:=]\\s*([0-9]+(?:\\.[0-9]{2})?)");
            return balancePattern.matcher(input).replaceAll(match -> {
                String key = match.group().split("[:=]")[0];
                return key + "=XXXXX";
            });
        });
        
        // Custom function for user IDs in specific format
        customMaskingFunctions.put("userId", input -> {
            Pattern userIdPattern = Pattern.compile("\\buser(?:_?id|_?name)\\s*[:=]\\s*['\"]?([A-Za-z0-9_-]+)['\"]?");
            return userIdPattern.matcher(input).replaceAll(match -> {
                String key = match.group().split("[:=]")[0];
                return key + "=USER_***";
            });
        });
    }

    /**
     * Add custom masking function
     */
    public void addCustomMaskingFunction(String name, Function<String, String> function) {
        customMaskingFunctions.put(name, function);
        log.debug("Added custom masking function: {}", name);
    }

    /**
     * Remove custom masking function
     */
    public void removeCustomMaskingFunction(String name) {
        customMaskingFunctions.remove(name);
        log.debug("Removed custom masking function: {}", name);
    }

    /**
     * Validate if string contains sensitive data
     */
    public ValidationResult validateForSensitiveData(String input) {
        if (input == null || input.isEmpty()) {
            return ValidationResult.builder()
                .containsSensitiveData(false)
                .detectedTypes(Collections.emptyList())
                .build();
        }
        
        List<DataType> detectedTypes = new ArrayList<>();
        
        for (Map.Entry<DataType, Pattern> entry : compiledPatterns.entrySet()) {
            DataType dataType = entry.getKey();
            Pattern pattern = entry.getValue();
            
            if (pattern.matcher(input).find()) {
                detectedTypes.add(dataType);
            }
        }
        
        return ValidationResult.builder()
            .containsSensitiveData(!detectedTypes.isEmpty())
            .detectedTypes(detectedTypes)
            .riskLevel(calculateRiskLevel(detectedTypes))
            .build();
    }

    /**
     * Calculate risk level based on detected data types
     */
    private RiskLevel calculateRiskLevel(List<DataType> detectedTypes) {
        if (detectedTypes.isEmpty()) {
            return RiskLevel.NONE;
        }
        
        // High-risk data types
        Set<DataType> highRisk = Set.of(
            DataType.CREDIT_CARD, DataType.SSN, DataType.PASSWORD, 
            DataType.SECRET, DataType.API_KEY, DataType.ACCESS_TOKEN
        );
        
        // Medium-risk data types
        Set<DataType> mediumRisk = Set.of(
            DataType.EMAIL, DataType.PHONE_NUMBER, DataType.BANK_ACCOUNT,
            DataType.JWT_TOKEN, DataType.SESSION_ID
        );
        
        boolean hasHighRisk = detectedTypes.stream().anyMatch(highRisk::contains);
        boolean hasMediumRisk = detectedTypes.stream().anyMatch(mediumRisk::contains);
        
        if (hasHighRisk) {
            return RiskLevel.HIGH;
        } else if (hasMediumRisk) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }

    /**
     * Get masking statistics
     */
    public MaskingStatistics getStatistics() {
        return MaskingStatistics.builder()
            .cacheSize(maskingCache.size())
            .supportedDataTypes(compiledPatterns.keySet())
            .customFunctions(customMaskingFunctions.keySet())
            .defaultStrategy(defaultStrategy)
            .build();
    }

    /**
     * Clear masking cache
     */
    public void clearCache() {
        maskingCache.clear();
        log.info("Data masking cache cleared");
    }

    // Data classes
    @Data
    @Builder
    public static class ValidationResult {
        private boolean containsSensitiveData;
        private List<DataType> detectedTypes;
        private RiskLevel riskLevel;
    }

    @Data
    @Builder
    public static class MaskingStatistics {
        private int cacheSize;
        private Set<DataType> supportedDataTypes;
        private Set<String> customFunctions;
        private MaskingStrategy defaultStrategy;
    }

    public enum RiskLevel {
        NONE, LOW, MEDIUM, HIGH, CRITICAL
    }
}