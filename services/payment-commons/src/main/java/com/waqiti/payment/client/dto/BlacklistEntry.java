package com.waqiti.payment.client.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blacklist entry DTO
 * Represents an entry in a blacklist
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BlacklistEntry {
    
    private UUID entryId;
    
    private String value;
    
    private String originalValue; // Original before normalization
    
    private EntryType entryType;
    
    private String blacklistName;
    
    private BlacklistCategory category;
    
    private EntryStatus status;
    
    // Entry details
    private String reason;
    
    private String description;
    
    private RiskLevel riskLevel;
    
    // Source information
    private String sourceSystem;
    
    private String addedBy;
    
    private LocalDateTime addedAt;
    
    private String lastModifiedBy;
    
    private LocalDateTime lastModifiedAt;
    
    // Validity and expiration
    private LocalDateTime validFrom;
    
    private LocalDateTime validUntil;
    
    private boolean isActive;
    
    // Match configuration
    private MatchConfiguration matchConfig;
    
    // Context and metadata
    @Builder.Default
    private List<String> tags = List.of();
    
    private String jurisdiction;
    
    private String regulatorySource;
    
    private Map<String, Object> metadata;
    
    // Related information
    @Builder.Default
    private List<UUID> relatedEntryIds = List.of();
    
    private String caseNumber;
    
    private UUID investigationId;
    
    public enum EntryType {
        EMAIL,
        PHONE,
        IP_ADDRESS,
        DEVICE_ID,
        USER_ID,
        MERCHANT_ID,
        BANK_ACCOUNT,
        CREDIT_CARD,
        ADDRESS,
        NAME,
        COMPANY,
        DOMAIN,
        BIN, // Bank Identification Number
        CUSTOM
    }
    
    public enum BlacklistCategory {
        FRAUD,
        SANCTIONS,
        PEP, // Politically Exposed Person
        AML, // Anti-Money Laundering
        TERRORISM,
        INTERNAL_POLICY,
        REGULATORY,
        INDUSTRY_SHARED,
        BEHAVIORAL,
        VELOCITY,
        GEOGRAPHIC,
        DEVICE_BASED,
        TEMPORARY_BLOCK
    }
    
    public enum EntryStatus {
        ACTIVE,
        INACTIVE,
        PENDING_REVIEW,
        EXPIRED,
        REMOVED,
        SUSPENDED
    }
    
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchConfiguration {
        private MatchType matchType;
        private boolean caseSensitive;
        private Double fuzzyThreshold; // 0.0-1.0 for fuzzy matching
        private boolean wildcardEnabled;
        private boolean regexEnabled;
        private String regexPattern;
        private boolean normalizeValue;
        private Map<String, Object> customMatchRules;
        
        public enum MatchType {
            EXACT,
            FUZZY,
            STARTS_WITH,
            ENDS_WITH,
            CONTAINS,
            REGEX,
            WILDCARD,
            PHONETIC
        }
    }
    
    // Business logic methods
    public boolean isCurrentlyActive() {
        if (!isActive || status != EntryStatus.ACTIVE) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        
        if (validUntil != null && now.isAfter(validUntil)) {
            return false;
        }
        
        return true;
    }
    
    public boolean isExpired() {
        return status == EntryStatus.EXPIRED ||
               (validUntil != null && LocalDateTime.now().isAfter(validUntil));
    }
    
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }
    
    public boolean isRegulatory() {
        return category == BlacklistCategory.SANCTIONS ||
               category == BlacklistCategory.PEP ||
               category == BlacklistCategory.AML ||
               category == BlacklistCategory.TERRORISM ||
               category == BlacklistCategory.REGULATORY;
    }
    
    public boolean supportsFuzzyMatching() {
        return matchConfig != null && 
               matchConfig.getMatchType() == MatchConfiguration.MatchType.FUZZY &&
               matchConfig.getFuzzyThreshold() != null;
    }
    
    public boolean isTemporary() {
        return category == BlacklistCategory.TEMPORARY_BLOCK ||
               validUntil != null;
    }
    
    public Long getDaysUntilExpiry() {
        if (validUntil == null) {
            return null;
        }
        
        return java.time.temporal.ChronoUnit.DAYS.between(
            LocalDateTime.now().toLocalDate(),
            validUntil.toLocalDate()
        );
    }
    
    public boolean requiresReview() {
        return status == EntryStatus.PENDING_REVIEW ||
               (isExpired() && status == EntryStatus.ACTIVE);
    }
    
    public boolean matches(String testValue, Double confidenceThreshold) {
        if (!isCurrentlyActive() || testValue == null || value == null) {
            return false;
        }
        
        if (matchConfig == null) {
            // Default exact match
            return value.equals(testValue);
        }
        
        return switch (matchConfig.getMatchType()) {
            case EXACT -> performExactMatch(testValue);
            case FUZZY -> performFuzzyMatch(testValue, confidenceThreshold);
            case STARTS_WITH -> performStartsWithMatch(testValue);
            case ENDS_WITH -> performEndsWithMatch(testValue);
            case CONTAINS -> performContainsMatch(testValue);
            case REGEX -> performRegexMatch(testValue);
            case WILDCARD -> performWildcardMatch(testValue);
            case PHONETIC -> performPhoneticMatch(testValue);
            default -> false;
        };
    }
    
    private boolean performExactMatch(String testValue) {
        if (matchConfig.isCaseSensitive()) {
            return value.equals(testValue);
        }
        return value.equalsIgnoreCase(testValue);
    }
    
    private boolean performFuzzyMatch(String testValue, Double threshold) {
        if (matchConfig.getFuzzyThreshold() == null) {
            return false;
        }
        
        Double actualThreshold = threshold != null ? threshold : matchConfig.getFuzzyThreshold();
        
        // Simple Levenshtein distance-based fuzzy matching
        double similarity = calculateSimilarity(value, testValue);
        return similarity >= actualThreshold;
    }
    
    private boolean performStartsWithMatch(String testValue) {
        String compareValue = matchConfig.isCaseSensitive() ? testValue : testValue.toLowerCase();
        String baseValue = matchConfig.isCaseSensitive() ? value : value.toLowerCase();
        return compareValue.startsWith(baseValue);
    }
    
    private boolean performEndsWithMatch(String testValue) {
        String compareValue = matchConfig.isCaseSensitive() ? testValue : testValue.toLowerCase();
        String baseValue = matchConfig.isCaseSensitive() ? value : value.toLowerCase();
        return compareValue.endsWith(baseValue);
    }
    
    private boolean performContainsMatch(String testValue) {
        String compareValue = matchConfig.isCaseSensitive() ? testValue : testValue.toLowerCase();
        String baseValue = matchConfig.isCaseSensitive() ? value : value.toLowerCase();
        return compareValue.contains(baseValue);
    }
    
    private boolean performRegexMatch(String testValue) {
        String pattern = matchConfig.getRegexPattern() != null ? 
                        matchConfig.getRegexPattern() : value;
        
        try {
            return testValue.matches(pattern);
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean performWildcardMatch(String testValue) {
        // Convert wildcard pattern to regex
        String regexPattern = value.replace("*", ".*").replace("?", ".");
        
        try {
            return testValue.matches(regexPattern);
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean performPhoneticMatch(String testValue) {
        // Simplified phonetic matching - would use Soundex or similar in real implementation
        return value.equalsIgnoreCase(testValue);
    }
    
    private double calculateSimilarity(String s1, String s2) {
        // Simplified similarity calculation - would use proper algorithm in real implementation
        if (s1.equals(s2)) {
            return 1.0;
        }
        
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) {
            return 1.0;
        }
        
        int distance = calculateLevenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLength;
    }
    
    private int calculateLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
}