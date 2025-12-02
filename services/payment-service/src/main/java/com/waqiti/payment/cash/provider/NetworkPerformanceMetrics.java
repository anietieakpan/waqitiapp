package com.waqiti.payment.cash.provider;

import java.math.BigDecimal;

public class NetworkPerformanceMetrics {
    private long successCount = 0;
    private long failureCount = 0;
    private double totalProcessingTime = 0.0;
    private double totalAmount = 0.0;
    private Exception lastException;
    
    public void recordSuccess(long processingTime, BigDecimal amount) {
        successCount++;
        totalProcessingTime += processingTime;
        totalAmount += amount.doubleValue();
    }
    
    public void recordFailure(long processingTime, Exception exception) {
        failureCount++;
        totalProcessingTime += processingTime;
        lastException = exception;
    }
}
