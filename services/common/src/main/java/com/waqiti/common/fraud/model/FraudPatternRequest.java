package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fraud pattern request
 */
@Data
@Builder
@Jacksonized
public class FraudPatternRequest {
    private String userId;
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private String merchantId;
    private Instant timestamp;
    private Map<String, Object> transactionData;
    private String patternType;
    private List<Map<String, Object>> dataPoints;
    private Duration timeWindow;
    private Map<String, Object> connectionData;
    private List<Map<String, Object>> transactionHistory;
    private Map<String, Object> currentTransaction;
    private boolean enableMlDetection;
    
    /**
     * Get data points for pattern analysis
     */
    public List<Map<String, Object>> getDataPoints() {
        return dataPoints;
    }
    
    /**
     * Get the time window for pattern analysis
     */
    public Duration getTimeWindow() {
        return timeWindow != null ? timeWindow : Duration.ofHours(24);
    }
    
    /**
     * Get connection data for analysis
     */
    public Map<String, Object> getConnectionData() {
        return connectionData;
    }
    
    /**
     * Get transaction history
     */
    public List<Map<String, Object>> getTransactionHistory() {
        return transactionHistory;
    }
    
    /**
     * Get current transaction data
     */
    public Map<String, Object> getCurrentTransaction() {
        return currentTransaction != null ? currentTransaction : transactionData;
    }
    
    /**
     * Check if ML detection is enabled
     */
    public boolean isEnableMlDetection() {
        return enableMlDetection;
    }
}