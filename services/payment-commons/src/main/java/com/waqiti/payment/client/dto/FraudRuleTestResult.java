package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fraud Rule Test Result
 * 
 * Result of testing a fraud detection rule against test data.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRuleTestResult {
    
    /**
     * Test execution ID
     */
    private String testId;
    
    /**
     * Rule ID that was tested
     */
    private String ruleId;
    
    /**
     * Whether the rule matched the test data
     */
    private Boolean matched;
    
    /**
     * Test execution status
     */
    private TestStatus status;
    
    /**
     * Score produced by the rule
     */
    private Double score;
    
    /**
     * Execution time in milliseconds
     */
    private Long executionTimeMs;
    
    /**
     * Test data used
     */
    private Map<String, Object> testData;
    
    /**
     * Values that were evaluated
     */
    private Map<String, Object> evaluatedValues;
    
    /**
     * Detailed explanation of the test result
     */
    private String explanation;
    
    /**
     * Any errors that occurred during testing
     */
    private String errorMessage;
    
    /**
     * Test execution timestamp
     */
    private LocalDateTime testedAt;
    
    /**
     * Rule condition that was evaluated
     */
    private String ruleCondition;
    
    /**
     * Expected result (if available)
     */
    private Boolean expectedResult;
    
    /**
     * Whether the test passed validation
     */
    private Boolean testPassed;
    
    public enum TestStatus {
        SUCCESS,
        FAILED,
        ERROR,
        TIMEOUT
    }
    
    /**
     * Check if the test was successful
     */
    public boolean isSuccessful() {
        return status == TestStatus.SUCCESS;
    }
    
    /**
     * Check if test result matches expectation
     */
    public boolean matchesExpected() {
        return expectedResult == null || expectedResult.equals(matched);
    }
}