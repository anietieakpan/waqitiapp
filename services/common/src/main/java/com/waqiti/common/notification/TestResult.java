package com.waqiti.common.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of testing a notification configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResult {
    
    /**
     * Test ID for tracking
     */
    private String testId;
    
    /**
     * Whether the test was successful
     */
    private boolean success;
    
    /**
     * Status of the test
     */
    private TestStatus status;
    
    /**
     * Channel that was tested
     */
    private NotificationChannel channel;
    
    /**
     * Test message if any
     */
    private String message;
    
    /**
     * Error message if test failed
     */
    private String error;
    
    /**
     * Response time in milliseconds
     */
    private Long responseTimeMs;
    
    /**
     * Additional test details
     */
    private Map<String, Object> details;
    
    /**
     * Timestamp of the test
     */
    private LocalDateTime testedAt;
    
    /**
     * Configuration that was tested
     */
    private Map<String, Object> testedConfiguration;
    
    /**
     * Recommendations if test failed
     */
    private String recommendations;
    
    /**
     * Test status enumeration
     */
    public enum TestStatus {
        SUCCESS,
        FAILED,
        PARTIAL,
        TIMEOUT,
        ERROR
    }
    
    /**
     * Check if test passed
     */
    public boolean isPassed() {
        return success && status == TestStatus.SUCCESS;
    }
    
    /**
     * Check if test failed
     */
    public boolean isFailed() {
        return !success || status == TestStatus.FAILED || status == TestStatus.ERROR;
    }
    
    /**
     * Get summary message
     */
    public String getSummary() {
        if (isPassed()) {
            return String.format("Test successful for %s channel. Response time: %dms", 
                channel != null ? channel.name() : "Unknown", 
                responseTimeMs != null ? responseTimeMs : 0);
        } else {
            return String.format("Test failed for %s channel: %s", 
                channel != null ? channel.name() : "Unknown",
                error != null ? error : "Unknown error");
        }
    }
}