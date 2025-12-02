package com.waqiti.common.fraud.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Represents a single condition within a fraud detection rule.
 * Conditions are evaluated against transaction data to determine if they are met.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleCondition {
    
    /**
     * Unique condition identifier within the rule
     */
    private String name;
    
    /**
     * Field name in transaction data to evaluate
     */
    private String fieldName;
    
    /**
     * Operator for comparison
     */
    private ConditionOperator operator;
    
    /**
     * Value(s) to compare against
     */
    private Object value;
    
    /**
     * List of values for operators like IN, NOT_IN
     */
    private List<Object> values;
    
    /**
     * Data type of the field being evaluated
     */
    private FieldType fieldType;
    
    /**
     * Weight of this condition in overall rule evaluation
     */
    @Builder.Default
    private double weight = 1.0;
    
    /**
     * Whether this condition is required or optional
     */
    @Builder.Default
    private boolean required = true;
    
    /**
     * Confidence threshold for this specific condition
     */
    @Builder.Default
    private double confidenceThreshold = 0.7;
    
    /**
     * Tolerance for numeric comparisons
     */
    @Builder.Default
    private double tolerance = 0.0;
    
    /**
     * Case sensitivity for string comparisons
     */
    @Builder.Default
    private boolean caseSensitive = true;
    
    /**
     * Regex pattern for pattern matching
     */
    private String regexPattern;
    
    /**
     * Compiled regex pattern
     */
    private transient Pattern compiledPattern;
    
    /**
     * Time window for time-based conditions (in minutes)
     */
    private Integer timeWindowMinutes;
    
    /**
     * Aggregation function for multi-value fields
     */
    private AggregationFunction aggregationFunction;
    
    /**
     * Custom validation function name
     */
    private String customValidator;
    
    /**
     * Parameters for custom validators
     */
    private Map<String, Object> customParameters;
    
    /**
     * Evaluate this condition against transaction data
     */
    public boolean evaluate(Map<String, Object> transactionData) {
        try {
            Object fieldValue = extractFieldValue(transactionData);
            
            if (fieldValue == null) {
                return !required; // If field is missing, pass only if not required
            }
            
            return performComparison(fieldValue);
            
        } catch (Exception e) {
            // Log error and return false for safety
            return false;
        }
    }
    
    /**
     * Get confidence score for this condition evaluation
     */
    public double getConfidence(Map<String, Object> transactionData) {
        try {
            Object fieldValue = extractFieldValue(transactionData);
            
            if (fieldValue == null) {
                return required ? 0.0 : 1.0;
            }
            
            // Base confidence from successful evaluation
            double baseConfidence = performComparison(fieldValue) ? 1.0 : 0.0;
            
            // Adjust confidence based on data quality and certainty
            return adjustConfidenceBasedOnValue(baseConfidence, fieldValue);
            
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * Extract field value from transaction data with path navigation
     */
    private Object extractFieldValue(Map<String, Object> transactionData) {
        if (fieldName == null || transactionData == null) {
            return null;
        }
        
        // Handle nested field paths (e.g., "user.profile.riskScore")
        String[] pathParts = fieldName.split("\\.");
        Object currentValue = transactionData;
        
        for (String part : pathParts) {
            if (currentValue instanceof Map) {
                currentValue = ((Map<?, ?>) currentValue).get(part);
            } else {
                return null;
            }
        }
        
        // Apply aggregation if specified
        if (aggregationFunction != null && currentValue instanceof List) {
            return applyAggregation((List<?>) currentValue);
        }
        
        return currentValue;
    }
    
    /**
     * Perform the actual comparison based on operator
     */
    private boolean performComparison(Object fieldValue) {
        switch (operator) {
            case EQUALS:
                return compareEquals(fieldValue, value);
            case NOT_EQUALS:
                return !compareEquals(fieldValue, value);
            case GREATER_THAN:
                return compareNumeric(fieldValue, value) > 0;
            case GREATER_THAN_EQUALS:
                return compareNumeric(fieldValue, value) >= 0;
            case LESS_THAN:
                return compareNumeric(fieldValue, value) < 0;
            case LESS_THAN_EQUALS:
                return compareNumeric(fieldValue, value) <= 0;
            case IN:
                return values != null && values.stream().anyMatch(v -> compareEquals(fieldValue, v));
            case NOT_IN:
                return values == null || values.stream().noneMatch(v -> compareEquals(fieldValue, v));
            case CONTAINS:
                return containsCheck(fieldValue, value);
            case NOT_CONTAINS:
                return !containsCheck(fieldValue, value);
            case STARTS_WITH:
                return startsWithCheck(fieldValue, value);
            case ENDS_WITH:
                return endsWithCheck(fieldValue, value);
            case REGEX_MATCH:
                return regexMatch(fieldValue);
            case IS_NULL:
                return fieldValue == null;
            case IS_NOT_NULL:
                return fieldValue != null;
            case BETWEEN:
                return betweenCheck(fieldValue);
            case TIME_WINDOW:
                return timeWindowCheck(fieldValue);
            case CUSTOM:
                return customValidation(fieldValue);
            default:
                return false;
        }
    }
    
    /**
     * Compare two values for equality with type conversion
     */
    private boolean compareEquals(Object fieldValue, Object compareValue) {
        if (fieldValue == null && compareValue == null) {
            return true;
        }
        if (fieldValue == null || compareValue == null) {
            return false;
        }
        
        // Convert types if necessary
        if (fieldType == FieldType.NUMERIC) {
            double field = convertToDouble(fieldValue);
            double compare = convertToDouble(compareValue);
            return Math.abs(field - compare) <= tolerance;
        }
        
        if (fieldType == FieldType.STRING) {
            String fieldStr = fieldValue.toString();
            String compareStr = compareValue.toString();
            return caseSensitive ? fieldStr.equals(compareStr) : fieldStr.equalsIgnoreCase(compareStr);
        }
        
        return fieldValue.equals(compareValue);
    }
    
    /**
     * Compare numeric values
     */
    private int compareNumeric(Object fieldValue, Object compareValue) {
        double field = convertToDouble(fieldValue);
        double compare = convertToDouble(compareValue);
        
        if (Math.abs(field - compare) <= tolerance) {
            return 0;
        }
        return Double.compare(field, compare);
    }
    
    /**
     * Check if field contains value
     */
    private boolean containsCheck(Object fieldValue, Object searchValue) {
        if (fieldValue == null || searchValue == null) {
            return false;
        }
        
        String fieldStr = fieldValue.toString();
        String searchStr = searchValue.toString();
        
        return caseSensitive ? fieldStr.contains(searchStr) : 
               fieldStr.toLowerCase().contains(searchStr.toLowerCase());
    }
    
    /**
     * Check if field starts with value
     */
    private boolean startsWithCheck(Object fieldValue, Object prefixValue) {
        if (fieldValue == null || prefixValue == null) {
            return false;
        }
        
        String fieldStr = fieldValue.toString();
        String prefixStr = prefixValue.toString();
        
        return caseSensitive ? fieldStr.startsWith(prefixStr) : 
               fieldStr.toLowerCase().startsWith(prefixStr.toLowerCase());
    }
    
    /**
     * Check if field ends with value
     */
    private boolean endsWithCheck(Object fieldValue, Object suffixValue) {
        if (fieldValue == null || suffixValue == null) {
            return false;
        }
        
        String fieldStr = fieldValue.toString();
        String suffixStr = suffixValue.toString();
        
        return caseSensitive ? fieldStr.endsWith(suffixStr) : 
               fieldStr.toLowerCase().endsWith(suffixStr.toLowerCase());
    }
    
    /**
     * Check regex pattern match
     */
    private boolean regexMatch(Object fieldValue) {
        if (fieldValue == null || regexPattern == null) {
            return false;
        }
        
        if (compiledPattern == null) {
            try {
                compiledPattern = Pattern.compile(regexPattern, 
                    caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                return false;
            }
        }
        
        return compiledPattern.matcher(fieldValue.toString()).matches();
    }
    
    /**
     * Check if value is between two bounds
     */
    private boolean betweenCheck(Object fieldValue) {
        if (values == null || values.size() < 2) {
            return false;
        }
        
        double field = convertToDouble(fieldValue);
        double lower = convertToDouble(values.get(0));
        double upper = convertToDouble(values.get(1));
        
        return field >= lower && field <= upper;
    }
    
    /**
     * Check if timestamp is within time window
     */
    private boolean timeWindowCheck(Object fieldValue) {
        if (timeWindowMinutes == null || fieldValue == null) {
            return false;
        }
        
        try {
            LocalDateTime timestamp;
            if (fieldValue instanceof LocalDateTime) {
                timestamp = (LocalDateTime) fieldValue;
            } else {
                // Parse string timestamp
                timestamp = LocalDateTime.parse(fieldValue.toString());
            }
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowStart = now.minusMinutes(timeWindowMinutes);
            
            return timestamp.isAfter(windowStart) && timestamp.isBefore(now);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Custom validation using configured validator
     */
    private boolean customValidation(Object fieldValue) {
        // Placeholder for custom validation logic
        // In production, this would use a registry of custom validators
        return false;
    }
    
    /**
     * Apply aggregation function to list values
     */
    private Object applyAggregation(List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        
        switch (aggregationFunction) {
            case COUNT:
                return values.size();
            case SUM:
                return values.stream().mapToDouble(this::convertToDouble).sum();
            case AVERAGE:
                return values.stream().mapToDouble(this::convertToDouble).average().orElse(0.0);
            case MIN:
                return values.stream().mapToDouble(this::convertToDouble).min().orElse(0.0);
            case MAX:
                return values.stream().mapToDouble(this::convertToDouble).max().orElse(0.0);
            case FIRST:
                return values.get(0);
            case LAST:
                return values.get(values.size() - 1);
            default:
                return values;
        }
    }
    
    /**
     * Adjust confidence based on value characteristics
     */
    private double adjustConfidenceBasedOnValue(double baseConfidence, Object fieldValue) {
        if (baseConfidence == 0.0) {
            return 0.0;
        }
        
        // Reduce confidence for edge cases
        if (fieldType == FieldType.NUMERIC) {
            double numValue = convertToDouble(fieldValue);
            if (Double.isInfinite(numValue) || Double.isNaN(numValue)) {
                return baseConfidence * 0.5;
            }
        }
        
        // Full confidence for exact matches
        return baseConfidence;
    }
    
    /**
     * Convert value to double for numeric operations
     */
    private double convertToDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    /**
     * Validate condition configuration
     */
    public boolean isValid() {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return false;
        }
        
        if (operator == null) {
            return false;
        }
        
        // Validate operator-specific requirements
        switch (operator) {
            case IN:
            case NOT_IN:
                return values != null && !values.isEmpty();
            case BETWEEN:
                return values != null && values.size() >= 2;
            case REGEX_MATCH:
                return regexPattern != null && !regexPattern.trim().isEmpty();
            case TIME_WINDOW:
                return timeWindowMinutes != null && timeWindowMinutes > 0;
            case CUSTOM:
                return customValidator != null && !customValidator.trim().isEmpty();
            default:
                return value != null || operator == ConditionOperator.IS_NULL;
        }
    }
    
    /**
     * Get condition summary for display
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(fieldName).append(" ").append(operator.name().toLowerCase());
        
        if (value != null) {
            summary.append(" ").append(value);
        }
        
        if (values != null && !values.isEmpty()) {
            summary.append(" [").append(String.join(", ", 
                values.stream().map(Object::toString).toArray(String[]::new))).append("]");
        }
        
        return summary.toString();
    }
    
    // Supporting enums
    
    public enum ConditionOperator {
        EQUALS,
        NOT_EQUALS,
        GREATER_THAN,
        GREATER_THAN_EQUALS,
        LESS_THAN,
        LESS_THAN_EQUALS,
        IN,
        NOT_IN,
        CONTAINS,
        NOT_CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        REGEX_MATCH,
        IS_NULL,
        IS_NOT_NULL,
        BETWEEN,
        TIME_WINDOW,
        CUSTOM
    }
    
    public enum FieldType {
        STRING,
        NUMERIC,
        BOOLEAN,
        DATE_TIME,
        LIST,
        OBJECT
    }
    
    public enum AggregationFunction {
        COUNT,
        SUM,
        AVERAGE,
        MIN,
        MAX,
        FIRST,
        LAST
    }
}