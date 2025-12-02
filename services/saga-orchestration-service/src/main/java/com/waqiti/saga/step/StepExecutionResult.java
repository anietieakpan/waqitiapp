package com.waqiti.saga.step;

import java.util.HashMap;
import java.util.Map;

/**
 * Result of saga step execution
 */
public class StepExecutionResult {
    
    private final boolean success;
    private final String errorMessage;
    private final String errorCode;
    private final Map<String, Object> stepData;
    
    private StepExecutionResult(boolean success, String errorMessage, String errorCode, Map<String, Object> stepData) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
        this.stepData = stepData != null ? stepData : new HashMap<>();
    }
    
    public static StepExecutionResult success() {
        return new StepExecutionResult(true, null, null, null);
    }
    
    public static StepExecutionResult success(Map<String, Object> stepData) {
        return new StepExecutionResult(true, null, null, stepData);
    }
    
    public static StepExecutionResult failure(String errorMessage) {
        return new StepExecutionResult(false, errorMessage, null, null);
    }
    
    public static StepExecutionResult failure(String errorMessage, String errorCode) {
        return new StepExecutionResult(false, errorMessage, errorCode, null);
    }
    
    public static StepExecutionResult failure(String errorMessage, String errorCode, Map<String, Object> stepData) {
        return new StepExecutionResult(false, errorMessage, errorCode, stepData);
    }
    
    // Getters
    public boolean isSuccess() {
        return success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public Map<String, Object> getStepData() {
        return stepData;
    }
    
    public Object getData(String key) {
        return stepData.get(key);
    }
    
    public void addData(String key, Object value) {
        stepData.put(key, value);
    }
}