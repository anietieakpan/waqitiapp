package com.waqiti.config.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Feature flag evaluation rule
 * Defines complex conditions for determining if a feature flag should be enabled
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagRule {

    /**
     * Rule type: USER, GROUP, ENVIRONMENT, PERCENTAGE, CUSTOM, TIME_WINDOW, GEOGRAPHIC, DEVICE
     */
    private RuleType type;

    /**
     * Operator: EQUALS, NOT_EQUALS, IN, NOT_IN, CONTAINS, GREATER_THAN, LESS_THAN, MATCHES_REGEX
     */
    private RuleOperator operator;

    /**
     * Attribute to evaluate (e.g., "userId", "userGroup", "environment", "country", "deviceType")
     */
    private String attribute;

    /**
     * Expected value(s) for comparison
     */
    private List<String> values;

    /**
     * For percentage-based rules: 0-100
     */
    private Integer percentage;

    /**
     * For time-window rules: start time (ISO 8601)
     */
    private String startTime;

    /**
     * For time-window rules: end time (ISO 8601)
     */
    private String endTime;

    /**
     * For custom rules: script or expression to evaluate
     */
    private String expression;

    /**
     * Additional metadata for the rule
     */
    private Map<String, Object> metadata;

    /**
     * Child rules for AND/OR logic
     */
    private List<FeatureFlagRule> childRules;

    /**
     * Logic operator for child rules: AND, OR
     */
    private LogicOperator logicOperator;

    /**
     * Rule priority (higher = evaluated first)
     */
    @Builder.Default
    private Integer priority = 0;

    /**
     * Whether this rule is active
     */
    @Builder.Default
    private Boolean active = true;

    /**
     * Rule types
     */
    public enum RuleType {
        USER,           // Target specific users by ID
        GROUP,          // Target user groups/roles
        ENVIRONMENT,    // Target specific environments (dev, staging, prod)
        PERCENTAGE,     // Percentage rollout
        CUSTOM,         // Custom expression/script
        TIME_WINDOW,    // Active during specific time window
        GEOGRAPHIC,     // Target specific countries/regions
        DEVICE,         // Target specific device types (mobile, web, ios, android)
        ATTRIBUTE,      // Generic attribute-based rule
        COMPOSITE       // Combination of multiple rules
    }

    /**
     * Comparison operators
     */
    public enum RuleOperator {
        EQUALS,
        NOT_EQUALS,
        IN,
        NOT_IN,
        CONTAINS,
        NOT_CONTAINS,
        GREATER_THAN,
        LESS_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN_OR_EQUAL,
        MATCHES_REGEX,
        STARTS_WITH,
        ENDS_WITH
    }

    /**
     * Logic operators for combining rules
     */
    public enum LogicOperator {
        AND,    // All child rules must pass
        OR,     // At least one child rule must pass
        NOT     // Invert the result
    }

    /**
     * Evaluate this rule against provided context
     *
     * @param context Evaluation context containing user info, environment, etc.
     * @return true if rule passes, false otherwise
     */
    public boolean evaluate(Map<String, Object> context) {
        if (!active) {
            return false;
        }

        // Evaluate based on rule type
        return switch (type) {
            case USER -> evaluateUserRule(context);
            case GROUP -> evaluateGroupRule(context);
            case ENVIRONMENT -> evaluateEnvironmentRule(context);
            case PERCENTAGE -> evaluatePercentageRule(context);
            case TIME_WINDOW -> evaluateTimeWindowRule(context);
            case GEOGRAPHIC -> evaluateGeographicRule(context);
            case DEVICE -> evaluateDeviceRule(context);
            case ATTRIBUTE -> evaluateAttributeRule(context);
            case COMPOSITE -> evaluateCompositeRule(context);
            case CUSTOM -> evaluateCustomRule(context);
        };
    }

    private boolean evaluateUserRule(Map<String, Object> context) {
        String userId = (String) context.get("userId");
        if (userId == null) return false;

        return switch (operator) {
            case EQUALS -> values != null && values.contains(userId);
            case NOT_EQUALS -> values == null || !values.contains(userId);
            case IN -> values != null && values.contains(userId);
            case NOT_IN -> values == null || !values.contains(userId);
            default -> false;
        };
    }

    private boolean evaluateGroupRule(Map<String, Object> context) {
        @SuppressWarnings("unchecked")
        List<String> userGroups = (List<String>) context.get("userGroups");
        if (userGroups == null || userGroups.isEmpty()) return false;

        if (values == null || values.isEmpty()) return false;

        return userGroups.stream().anyMatch(values::contains);
    }

    private boolean evaluateEnvironmentRule(Map<String, Object> context) {
        String environment = (String) context.get("environment");
        if (environment == null) return false;

        return values != null && values.contains(environment);
    }

    private boolean evaluatePercentageRule(Map<String, Object> context) {
        String userId = (String) context.get("userId");
        if (userId == null || percentage == null) return false;

        // Consistent hash-based rollout
        int hash = Math.abs(userId.hashCode());
        int bucket = hash % 100;
        return bucket < percentage;
    }

    private boolean evaluateTimeWindowRule(Map<String, Object> context) {
        if (startTime == null || endTime == null) return false;

        java.time.Instant now = java.time.Instant.now();
        java.time.Instant start = java.time.Instant.parse(startTime);
        java.time.Instant end = java.time.Instant.parse(endTime);

        return now.isAfter(start) && now.isBefore(end);
    }

    private boolean evaluateGeographicRule(Map<String, Object> context) {
        String country = (String) context.get("country");
        if (country == null) return false;

        return values != null && values.contains(country);
    }

    private boolean evaluateDeviceRule(Map<String, Object> context) {
        String deviceType = (String) context.get("deviceType");
        if (deviceType == null) return false;

        return values != null && values.contains(deviceType);
    }

    private boolean evaluateAttributeRule(Map<String, Object> context) {
        if (attribute == null) return false;

        Object attributeValue = context.get(attribute);
        if (attributeValue == null) return false;

        String stringValue = attributeValue.toString();

        return switch (operator) {
            case EQUALS -> values != null && values.contains(stringValue);
            case NOT_EQUALS -> values == null || !values.contains(stringValue);
            case IN -> values != null && values.contains(stringValue);
            case NOT_IN -> values == null || !values.contains(stringValue);
            case CONTAINS -> values != null && values.stream().anyMatch(stringValue::contains);
            case NOT_CONTAINS -> values == null || values.stream().noneMatch(stringValue::contains);
            case STARTS_WITH -> values != null && values.stream().anyMatch(stringValue::startsWith);
            case ENDS_WITH -> values != null && values.stream().anyMatch(stringValue::endsWith);
            case MATCHES_REGEX -> values != null && values.stream().anyMatch(stringValue::matches);
            default -> false;
        };
    }

    private boolean evaluateCompositeRule(Map<String, Object> context) {
        if (childRules == null || childRules.isEmpty()) return false;

        return switch (logicOperator) {
            case AND -> childRules.stream().allMatch(rule -> rule.evaluate(context));
            case OR -> childRules.stream().anyMatch(rule -> rule.evaluate(context));
            case NOT -> childRules.stream().noneMatch(rule -> rule.evaluate(context));
        };
    }

    private boolean evaluateCustomRule(Map<String, Object> context) {
        // For custom expression evaluation
        // This would typically use a script engine (e.g., SpEL, Groovy, JavaScript)
        // For now, return false as placeholder
        // TODO: Implement custom expression evaluation
        return false;
    }
}
