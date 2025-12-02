package com.waqiti.common.fraud.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Context information provided to rule actions during execution.
 * Contains transaction details, user information, and rule evaluation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionExecutionContext {
    
    /**
     * Transaction identifier
     */
    private String transactionId;
    
    /**
     * User identifier
     */
    private String userId;
    
    /**
     * Merchant identifier
     */
    private String merchantId;
    
    /**
     * Transaction amount
     */
    private double amount;
    
    /**
     * Currency code
     */
    private String currency;
    
    /**
     * Transaction type
     */
    private String transactionType;
    
    /**
     * Transaction timestamp
     */
    private LocalDateTime transactionTime;
    
    /**
     * Rule that triggered this action
     */
    private String ruleId;
    
    /**
     * Rule evaluation result that triggered this action
     */
    private RuleEvaluationResult ruleResult;
    
    /**
     * Overall fraud score for the transaction
     */
    private double fraudScore;
    
    /**
     * Risk level assessment
     */
    private String riskLevel;
    
    /**
     * Full transaction data
     */
    private Map<String, Object> transactionData;
    
    /**
     * User profile information
     */
    private Map<String, Object> userProfile;
    
    /**
     * Merchant profile information
     */
    private Map<String, Object> merchantProfile;
    
    /**
     * Session information
     */
    private Map<String, Object> sessionData;
    
    /**
     * Device information
     */
    private Map<String, Object> deviceData;
    
    /**
     * Location information
     */
    private Map<String, Object> locationData;
    
    /**
     * Additional context parameters
     */
    private Map<String, Object> parameters;
    
    /**
     * Execution environment (PRODUCTION, TESTING, etc.)
     */
    private String environment;
    
    /**
     * Business unit context
     */
    private String businessUnit;
    
    /**
     * Customer segment
     */
    private String customerSegment;
    
    /**
     * Action execution request ID for tracking
     */
    private String executionRequestId;
    
    /**
     * Timestamp when context was created
     */
    @Builder.Default
    private LocalDateTime contextCreatedAt = LocalDateTime.now();
    
    /**
     * Get parameter value with type conversion
     */
    public Object getParameter(String key) {
        if (parameters == null) {
            return null;
        }
        return parameters.get(key);
    }
    
    /**
     * Get parameter as string with default value
     */
    public String getParameterAsString(String key, String defaultValue) {
        Object value = getParameter(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Get parameter as double with default value
     */
    public double getParameterAsDouble(String key, double defaultValue) {
        Object value = getParameter(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * Get parameter as boolean with default value
     */
    public boolean getParameterAsBoolean(String key, boolean defaultValue) {
        Object value = getParameter(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    /**
     * Get transaction data field
     */
    public Object getTransactionField(String fieldName) {
        if (transactionData == null) {
            return null;
        }
        return transactionData.get(fieldName);
    }
    
    /**
     * Get user profile field
     */
    public Object getUserProfileField(String fieldName) {
        if (userProfile == null) {
            return null;
        }
        return userProfile.get(fieldName);
    }
    
    /**
     * Get merchant profile field
     */
    public Object getMerchantProfileField(String fieldName) {
        if (merchantProfile == null) {
            return null;
        }
        return merchantProfile.get(fieldName);
    }
    
    /**
     * Get session data field
     */
    public Object getSessionField(String fieldName) {
        if (sessionData == null) {
            return null;
        }
        return sessionData.get(fieldName);
    }
    
    /**
     * Get device data field
     */
    public Object getDeviceField(String fieldName) {
        if (deviceData == null) {
            return null;
        }
        return deviceData.get(fieldName);
    }
    
    /**
     * Get location data field
     */
    public Object getLocationField(String fieldName) {
        if (locationData == null) {
            return null;
        }
        return locationData.get(fieldName);
    }
    
    /**
     * Check if transaction is high value
     */
    public boolean isHighValueTransaction() {
        return amount >= getParameterAsDouble("highValueThreshold", 10000.0);
    }
    
    /**
     * Check if user is high risk
     */
    public boolean isHighRiskUser() {
        Object riskScore = getUserProfileField("riskScore");
        if (riskScore instanceof Number) {
            return ((Number) riskScore).doubleValue() >= 0.7;
        }
        return false;
    }
    
    /**
     * Check if transaction is cross-border
     */
    public boolean isCrossBorderTransaction() {
        String userCountry = (String) getUserProfileField("country");
        String merchantCountry = (String) getMerchantProfileField("country");
        
        return userCountry != null && merchantCountry != null && 
               !userCountry.equals(merchantCountry);
    }
    
    /**
     * Check if transaction is during off-hours
     */
    public boolean isOffHoursTransaction() {
        if (transactionTime == null) {
            return false;
        }
        
        int hour = transactionTime.getHour();
        return hour < 6 || hour > 22; // 10 PM to 6 AM considered off-hours
    }
    
    /**
     * Get user's time zone offset if available
     */
    public Integer getUserTimezoneOffset() {
        Object offset = getUserProfileField("timezoneOffset");
        if (offset instanceof Number) {
            return ((Number) offset).intValue();
        }
        return null;
    }
    
    /**
     * Check if this is a repeat transaction
     */
    public boolean isRepeatTransaction() {
        Object isRepeat = getTransactionField("isRepeat");
        if (isRepeat instanceof Boolean) {
            return (Boolean) isRepeat;
        }
        return false;
    }
    
    /**
     * Get merchant risk score
     */
    public double getMerchantRiskScore() {
        Object riskScore = getMerchantProfileField("riskScore");
        if (riskScore instanceof Number) {
            return ((Number) riskScore).doubleValue();
        }
        return 0.5; // Default neutral risk
    }
    
    /**
     * Get device risk score
     */
    public double getDeviceRiskScore() {
        Object riskScore = getDeviceField("riskScore");
        if (riskScore instanceof Number) {
            return ((Number) riskScore).doubleValue();
        }
        return 0.5; // Default neutral risk
    }
    
    /**
     * Check if device is new or unknown
     */
    public boolean isNewDevice() {
        Object isNew = getDeviceField("isNew");
        if (isNew instanceof Boolean) {
            return (Boolean) isNew;
        }
        return false;
    }
    
    /**
     * Get location risk score
     */
    public double getLocationRiskScore() {
        Object riskScore = getLocationField("riskScore");
        if (riskScore instanceof Number) {
            return ((Number) riskScore).doubleValue();
        }
        return 0.5; // Default neutral risk
    }
    
    /**
     * Create context summary for logging
     */
    public String getContextSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Transaction: %s, User: %s, Amount: %.2f %s", 
            transactionId, userId, amount, currency));
        
        if (ruleId != null) {
            summary.append(String.format(", Rule: %s", ruleId));
        }
        
        summary.append(String.format(", Fraud Score: %.2f, Risk Level: %s", 
            fraudScore, riskLevel));
        
        if (merchantId != null) {
            summary.append(String.format(", Merchant: %s", merchantId));
        }
        
        return summary.toString();
    }
    
    /**
     * Create a copy of this context with additional parameters
     */
    public ActionExecutionContext withAdditionalParameters(Map<String, Object> additionalParams) {
        ActionExecutionContext copy = ActionExecutionContext.builder()
                .transactionId(this.transactionId)
                .userId(this.userId)
                .merchantId(this.merchantId)
                .amount(this.amount)
                .currency(this.currency)
                .transactionType(this.transactionType)
                .transactionTime(this.transactionTime)
                .ruleId(this.ruleId)
                .ruleResult(this.ruleResult)
                .fraudScore(this.fraudScore)
                .riskLevel(this.riskLevel)
                .transactionData(this.transactionData)
                .userProfile(this.userProfile)
                .merchantProfile(this.merchantProfile)
                .sessionData(this.sessionData)
                .deviceData(this.deviceData)
                .locationData(this.locationData)
                .environment(this.environment)
                .businessUnit(this.businessUnit)
                .customerSegment(this.customerSegment)
                .executionRequestId(this.executionRequestId)
                .build();
        
        // Merge parameters
        if (this.parameters != null) {
            copy.parameters = new java.util.HashMap<>(this.parameters);
        } else {
            copy.parameters = new java.util.HashMap<>();
        }
        
        if (additionalParams != null) {
            copy.parameters.putAll(additionalParams);
        }
        
        return copy;
    }
    
    /**
     * Validate context has required fields
     */
    public boolean isValid() {
        return transactionId != null && 
               userId != null && 
               amount >= 0 && 
               currency != null;
    }
    
    /**
     * Get all available data as a single map for processing
     */
    public Map<String, Object> getAllData() {
        Map<String, Object> allData = new java.util.HashMap<>();
        
        // Basic transaction info
        allData.put("transactionId", transactionId);
        allData.put("userId", userId);
        allData.put("merchantId", merchantId);
        allData.put("amount", amount);
        allData.put("currency", currency);
        allData.put("transactionType", transactionType);
        allData.put("transactionTime", transactionTime);
        allData.put("fraudScore", fraudScore);
        allData.put("riskLevel", riskLevel);
        
        // Add nested data with prefixes
        if (transactionData != null) {
            transactionData.forEach((key, value) -> allData.put("transaction." + key, value));
        }
        
        if (userProfile != null) {
            userProfile.forEach((key, value) -> allData.put("user." + key, value));
        }
        
        if (merchantProfile != null) {
            merchantProfile.forEach((key, value) -> allData.put("merchant." + key, value));
        }
        
        if (sessionData != null) {
            sessionData.forEach((key, value) -> allData.put("session." + key, value));
        }
        
        if (deviceData != null) {
            deviceData.forEach((key, value) -> allData.put("device." + key, value));
        }
        
        if (locationData != null) {
            locationData.forEach((key, value) -> allData.put("location." + key, value));
        }
        
        if (parameters != null) {
            parameters.forEach((key, value) -> allData.put("param." + key, value));
        }
        
        return allData;
    }
}