package com.waqiti.common.metrics.abstraction;

import lombok.Builder;
import lombok.Data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-Grade Tag Constraints
 * Defines rules and limits for metric tags to prevent cardinality explosion
 */
@Data
@Builder
public class TagConstraints {
    
    @Builder.Default
    private final int maxTags = 10;
    
    @Builder.Default
    private final int maxTagKeyLength = 50;
    
    @Builder.Default
    private final int maxTagValueLength = 100;
    
    @Builder.Default
    private final Set<String> allowedTags = new HashSet<>();
    
    @Builder.Default
    private final Set<String> requiredTags = new HashSet<>();
    
    @Builder.Default
    private final Map<String, Set<String>> allowedValues = new HashMap<>();
    
    @Builder.Default
    private final Map<String, ValueNormalizer> valueNormalizers = new HashMap<>();
    
    @Builder.Default
    private final boolean strict = false; // If true, throw exception on constraint violation
    
    @Builder.Default
    private final boolean allowUnknownTags = true;
    
    // Default constraints for general use
    public static final TagConstraints DEFAULT = TagConstraints.builder()
        .maxTags(10)
        .maxTagKeyLength(50)
        .maxTagValueLength(100)
        .allowUnknownTags(true)
        .strict(false)
        .build();
    
    // Strict constraints for critical metrics
    public static final TagConstraints STRICT = TagConstraints.builder()
        .maxTags(5)
        .maxTagKeyLength(30)
        .maxTagValueLength(50)
        .allowUnknownTags(false)
        .strict(true)
        .build();
    
    // High cardinality constraints
    public static final TagConstraints HIGH_CARDINALITY = TagConstraints.builder()
        .maxTags(15)
        .maxTagKeyLength(100)
        .maxTagValueLength(200)
        .allowUnknownTags(true)
        .strict(false)
        .build();
    
    /**
     * Check if a tag is allowed
     */
    public boolean isAllowedTag(String tag) {
        if (allowedTags.isEmpty()) {
            return allowUnknownTags;
        }
        return allowedTags.contains(tag) || allowUnknownTags;
    }
    
    /**
     * Check if a tag value is allowed
     */
    public boolean isAllowedValue(String tag, String value) {
        Set<String> allowed = allowedValues.get(tag);
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        return allowed.contains(value);
    }
    
    /**
     * Normalize a tag value
     */
    public String normalizeValue(String value) {
        // Apply custom normalizers
        for (Map.Entry<String, ValueNormalizer> entry : valueNormalizers.entrySet()) {
            if (value.matches(entry.getKey())) {
                return entry.getValue().normalize(value);
            }
        }
        return value;
    }
    
    /**
     * Validate all constraints
     */
    public void validate(Map<String, String> tags) {
        // Check required tags
        for (String required : requiredTags) {
            if (!tags.containsKey(required)) {
                if (strict) {
                    throw new IllegalArgumentException("Required tag missing: " + required);
                }
            }
        }
        
        // Check max tags
        if (tags.size() > maxTags) {
            if (strict) {
                throw new IllegalArgumentException("Too many tags: " + tags.size() + " > " + maxTags);
            }
        }
        
        // Check each tag
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            // Check key length
            if (key.length() > maxTagKeyLength) {
                if (strict) {
                    throw new IllegalArgumentException("Tag key too long: " + key);
                }
            }
            
            // Check value length
            if (value.length() > maxTagValueLength) {
                if (strict) {
                    throw new IllegalArgumentException("Tag value too long: " + value);
                }
            }
            
            // Check allowed values
            if (!isAllowedValue(key, value)) {
                if (strict) {
                    throw new IllegalArgumentException("Tag value not allowed: " + key + "=" + value);
                }
            }
        }
    }
    
    /**
     * Interface for custom value normalizers
     */
    @FunctionalInterface
    public interface ValueNormalizer {
        String normalize(String value);
    }
    
    /**
     * Common value normalizers
     */
    public static class Normalizers {
        
        public static final ValueNormalizer HTTP_STATUS = value -> {
            try {
                int status = Integer.parseInt(value);
                if (status < 200) return "1xx";
                if (status < 300) return "2xx";
                if (status < 400) return "3xx";
                if (status < 500) return "4xx";
                if (status < 600) return "5xx";
                return "unknown";
            } catch (NumberFormatException e) {
                return "unknown";
            }
        };
        
        public static final ValueNormalizer AMOUNT_RANGE = value -> {
            try {
                double amount = Double.parseDouble(value);
                if (amount < 10) return "micro";
                if (amount < 100) return "small";
                if (amount < 1000) return "medium";
                if (amount < 10000) return "large";
                return "very_large";
            } catch (NumberFormatException e) {
                return "unknown";
            }
        };
        
        public static final ValueNormalizer DURATION_CATEGORY = value -> {
            try {
                long millis = Long.parseLong(value);
                if (millis < 100) return "very_fast";
                if (millis < 500) return "fast";
                if (millis < 2000) return "normal";
                if (millis < 5000) return "slow";
                return "very_slow";
            } catch (NumberFormatException e) {
                return "unknown";
            }
        };
        
        public static final ValueNormalizer ERROR_CLASS = value -> {
            if (value == null || value.isEmpty()) return "none";
            if (value.contains("Timeout")) return "timeout";
            if (value.contains("Connection")) return "connection";
            if (value.contains("Auth")) return "auth";
            if (value.contains("Validation")) return "validation";
            if (value.contains("NotFound")) return "not_found";
            if (value.contains("Permission")) return "permission";
            return "other";
        };
    }
}