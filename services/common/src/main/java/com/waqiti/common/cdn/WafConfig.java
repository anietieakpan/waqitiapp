package com.waqiti.common.cdn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Web Application Firewall (WAF) configuration for CDN
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WafConfig {
    
    /**
     * Whether WAF is enabled
     */
    @Builder.Default
    private boolean enabled = true;
    
    /**
     * WAF mode
     */
    @Builder.Default
    private WafMode mode = WafMode.BLOCK;
    
    /**
     * Managed rule groups to enable
     */
    private List<ManagedRuleGroup> managedRuleGroups;
    
    /**
     * Custom rules
     */
    private List<CustomRule> customRules;
    
    /**
     * IP whitelist
     */
    private List<String> ipWhitelist;
    
    /**
     * IP blacklist
     */
    private List<String> ipBlacklist;
    
    /**
     * Geo-blocking configuration
     */
    private GeoBlockingConfig geoBlocking;
    
    /**
     * Rate limiting configuration
     */
    private RateLimitConfig rateLimiting;
    
    /**
     * SQL injection protection
     */
    @Builder.Default
    private boolean sqlInjectionProtection = true;
    
    /**
     * XSS protection
     */
    @Builder.Default
    private boolean xssProtection = true;
    
    /**
     * Request size limits
     */
    private RequestSizeLimits sizeLimits;
    
    /**
     * Logging configuration
     */
    private LoggingConfig logging;
    
    /**
     * Get all rules (managed + custom)
     */
    public java.util.List<Object> getRules() {
        java.util.List<Object> allRules = new java.util.ArrayList<>();
        if (managedRuleGroups != null) {
            allRules.addAll(managedRuleGroups);
        }
        if (customRules != null) {
            allRules.addAll(customRules);
        }
        return allRules;
    }
    
    public enum WafMode {
        BLOCK,      // Block malicious requests
        COUNT,      // Count but don't block
        CHALLENGE   // Present CAPTCHA challenge
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManagedRuleGroup {
        private String vendorName;
        private String name;
        private String version;
        private Map<String, RuleOverride> ruleOverrides;
        private boolean enabled;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomRule {
        private String name;
        private String description;
        private int priority;
        private RuleAction action;
        private List<RuleCondition> conditions;
        private boolean enabled;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleCondition {
        private String field;
        private MatchOperator operator;
        private List<String> values;
        private boolean negated;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoBlockingConfig {
        private boolean enabled;
        private List<String> allowedCountries;
        private List<String> blockedCountries;
        private RuleAction defaultAction;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitConfig {
        private boolean enabled;
        private int requestsPerMinute;
        private int burstSize;
        private RuleAction limitExceededAction;
        private List<String> excludedPaths;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestSizeLimits {
        private long maxBodySize;
        private long maxHeaderSize;
        private int maxHeaders;
        private int maxQueryStringLength;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoggingConfig {
        private boolean enabled;
        private String s3Bucket;
        private String prefix;
        private boolean includeFullRequest;
        private List<String> redactedHeaders;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleOverride {
        private RuleAction action;
        private boolean enabled;
    }
    
    public enum RuleAction {
        ALLOW,
        BLOCK,
        COUNT,
        CHALLENGE
    }
    
    public enum MatchOperator {
        EQUALS,
        NOT_EQUALS,
        CONTAINS,
        NOT_CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        MATCHES_REGEX,
        GREATER_THAN,
        LESS_THAN
    }
}