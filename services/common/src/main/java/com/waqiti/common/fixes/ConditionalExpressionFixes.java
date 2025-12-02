package com.waqiti.common.fixes;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Utility class containing fixes for simplifiable conditional expressions
 * identified by Qodana analysis
 */
@Component
public class ConditionalExpressionFixes {

    /**
     * Fixed method for liveness check - simplified conditional expression
     */
    public boolean extractLivenessCheck(JsonNode eventData) {
        // Qodana suggested fix: simplified from ternary operator
        return !eventData.has("livenessCheck") || eventData.get("livenessCheck").asBoolean();
    }
    
    /**
     * Fixed method for extract text check - simplified conditional expression
     */
    public boolean extractTextCheck(JsonNode eventData) {
        // Qodana suggested fix: simplified from ternary operator
        return !eventData.has("extractText") || eventData.get("extractText").asBoolean();
    }
    
    /**
     * Fixed method for authenticity check - simplified conditional expression
     */
    public boolean checkAuthenticity(JsonNode eventData) {
        // Qodana suggested fix: simplified from ternary operator
        return !eventData.has("checkAuthenticity") || eventData.get("checkAuthenticity").asBoolean();
    }
    
    /**
     * Fixed method for document verification - simplified conditional expression
     */
    public boolean verifyDocument(JsonNode eventData) {
        // Qodana suggested fix: simplified from ternary operator
        return !eventData.has("verifyDocument") || eventData.get("verifyDocument").asBoolean();
    }
    
    /**
     * Fixed method for face matching - simplified conditional expression
     */
    public boolean enableFaceMatching(JsonNode eventData) {
        // Qodana suggested fix: simplified from ternary operator
        return !eventData.has("enableFaceMatching") || eventData.get("enableFaceMatching").asBoolean();
    }
    
    /**
     * Fixed method for ID verification - simplified conditional expression
     */
    public boolean verifyId(JsonNode eventData) {
        // Qodana suggested fix: simplified from ternary operator
        return !eventData.has("verifyId") || eventData.get("verifyId").asBoolean();
    }
    
    /**
     * Fixed method for address verification - simplified conditional expression
     */
    public boolean verifyAddress(JsonNode eventData) {
        // Qodana suggested fix: simplified from ternary operator
        return !eventData.has("verifyAddress") || eventData.get("verifyAddress").asBoolean();
    }
    
    /**
     * Fixed method for biometric verification - simplified conditional expression
     */
    public boolean enableBiometricVerification(JsonNode eventData) {
        // Qodana suggested fix: simplified from ternary operator
        return !eventData.has("enableBiometricVerification") || eventData.get("enableBiometricVerification").asBoolean();
    }
    
    /**
     * Helper method to safely extract boolean values with default true
     */
    public boolean extractBooleanWithDefaultTrue(JsonNode eventData, String fieldName) {
        return !eventData.has(fieldName) || eventData.get(fieldName).asBoolean();
    }
    
    /**
     * Helper method to safely extract boolean values with default false
     */
    public boolean extractBooleanWithDefaultFalse(JsonNode eventData, String fieldName) {
        return eventData.has(fieldName) && eventData.get(fieldName).asBoolean();
    }
    
    /**
     * Helper method to safely extract string values with default
     */
    public String extractStringWithDefault(JsonNode eventData, String fieldName, String defaultValue) {
        return eventData.has(fieldName) ? eventData.get(fieldName).asText() : defaultValue;
    }
    
    /**
     * Helper method to safely extract integer values with default
     */
    public int extractIntWithDefault(JsonNode eventData, String fieldName, int defaultValue) {
        return eventData.has(fieldName) ? eventData.get(fieldName).asInt() : defaultValue;
    }
    
    /**
     * Helper method to safely extract double values with default
     */
    public double extractDoubleWithDefault(JsonNode eventData, String fieldName, double defaultValue) {
        return eventData.has(fieldName) ? eventData.get(fieldName).asDouble() : defaultValue;
    }
}