package com.waqiti.payment.refund.provider;

import com.waqiti.payment.core.model.RefundRequest;
import com.waqiti.payment.core.model.ProviderType;
import com.waqiti.payment.refund.model.RefundCalculation;
import com.waqiti.payment.refund.model.ProviderRefundResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Refund Provider Service Interface
 * 
 * Defines the contract for provider-specific refund processing.
 * Each payment provider (Stripe, PayPal, etc.) implements this interface
 * to handle refunds according to their specific requirements and APIs.
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
public interface RefundProviderService {
    
    /**
     * Get the provider type this service handles
     * 
     * @return the provider type
     */
    ProviderType getProviderType();
    
    /**
     * Check if this provider supports refund processing
     * 
     * @return true if refunds are supported
     */
    boolean supportsRefunds();
    
    /**
     * Check if the provider is currently available for refund processing
     * 
     * @return true if available
     */
    boolean isAvailable();
    
    /**
     * Process a refund request with the provider
     * 
     * @param refundRequest the refund request
     * @param calculation the refund calculation
     * @return provider refund result
     */
    ProviderRefundResult processRefund(RefundRequest refundRequest, RefundCalculation calculation);
    
    /**
     * Check the status of a refund with the provider
     * 
     * @param providerRefundId the provider refund ID
     * @return current refund status
     */
    ProviderRefundResult checkRefundStatus(String providerRefundId);
    
    /**
     * Cancel a pending refund with the provider
     * 
     * @param providerRefundId the provider refund ID
     * @return cancellation result
     */
    ProviderRefundResult cancelRefund(String providerRefundId);
    
    /**
     * Get refund details from the provider
     * 
     * @param providerRefundId the provider refund ID
     * @return refund details
     */
    ProviderRefundResult getRefundDetails(String providerRefundId);
    
    /**
     * Calculate refund fees for this provider
     * 
     * @param originalAmount the original payment amount
     * @param refundAmount the requested refund amount
     * @param currency the currency
     * @return calculated fee
     */
    BigDecimal calculateRefundFee(BigDecimal originalAmount, BigDecimal refundAmount, String currency);
    
    /**
     * Get the maximum refundable amount for a payment
     * 
     * @param originalPaymentId the original payment ID
     * @return maximum refundable amount
     */
    BigDecimal getMaxRefundableAmount(String originalPaymentId);
    
    /**
     * Check if partial refunds are supported
     * 
     * @return true if partial refunds are supported
     */
    boolean supportsPartialRefunds();
    
    /**
     * Check if multiple refunds are supported for a single payment
     * 
     * @return true if multiple refunds are supported
     */
    boolean supportsMultipleRefunds();
    
    /**
     * Get the refund processing timeframe for this provider
     * 
     * @return expected processing duration in days
     */
    int getRefundProcessingDays();
    
    /**
     * Validate a refund request before processing
     * 
     * @param refundRequest the refund request
     * @return validation result
     */
    ProviderRefundResult validateRefundRequest(RefundRequest refundRequest);
    
    /**
     * Handle provider callback/webhook for refund status updates
     * 
     * @param callbackData the callback data from provider
     * @return processing result
     */
    ProviderRefundResult handleCallback(Map<String, Object> callbackData);
    
    /**
     * Get provider-specific refund limits
     * 
     * @return refund limits configuration
     */
    RefundLimits getRefundLimits();
    
    /**
     * Get provider health status for refund operations
     * 
     * @return health check result
     */
    ProviderHealthStatus getHealthStatus();
    
    /**
     * Reconcile refund with provider settlement data
     * 
     * @param providerRefundId the provider refund ID
     * @param settlementData the settlement data
     * @return reconciliation result
     */
    ProviderRefundResult reconcileRefund(String providerRefundId, Map<String, Object> settlementData);
    
    /**
     * Retry a failed refund
     * 
     * @param originalRefundRequest the original refund request
     * @param retryAttempt the retry attempt number
     * @return retry result
     */
    ProviderRefundResult retryRefund(RefundRequest originalRefundRequest, int retryAttempt);
    
    /**
     * Get refund transaction history from provider
     * 
     * @param originalPaymentId the original payment ID
     * @param fromDate start date for history
     * @param toDate end date for history
     * @return list of refund transactions
     */
    java.util.List<ProviderRefundResult> getRefundHistory(String originalPaymentId, 
                                                         Instant fromDate, 
                                                         Instant toDate);
    
    // Supporting classes
    class RefundLimits {
        private BigDecimal minimumRefundAmount;
        private BigDecimal maximumRefundAmount;
        private BigDecimal dailyRefundLimit;
        private BigDecimal monthlyRefundLimit;
        private int maxRefundsPerPayment;
        private int refundWindowDays;
        
        // Constructors, getters, setters
        public RefundLimits() {}
        
        public RefundLimits(BigDecimal minimumRefundAmount, 
                          BigDecimal maximumRefundAmount,
                          BigDecimal dailyRefundLimit,
                          BigDecimal monthlyRefundLimit,
                          int maxRefundsPerPayment,
                          int refundWindowDays) {
            this.minimumRefundAmount = minimumRefundAmount;
            this.maximumRefundAmount = maximumRefundAmount;
            this.dailyRefundLimit = dailyRefundLimit;
            this.monthlyRefundLimit = monthlyRefundLimit;
            this.maxRefundsPerPayment = maxRefundsPerPayment;
            this.refundWindowDays = refundWindowDays;
        }
        
        // Getters and setters
        public BigDecimal getMinimumRefundAmount() { return minimumRefundAmount; }
        public void setMinimumRefundAmount(BigDecimal minimumRefundAmount) { this.minimumRefundAmount = minimumRefundAmount; }
        
        public BigDecimal getMaximumRefundAmount() { return maximumRefundAmount; }
        public void setMaximumRefundAmount(BigDecimal maximumRefundAmount) { this.maximumRefundAmount = maximumRefundAmount; }
        
        public BigDecimal getDailyRefundLimit() { return dailyRefundLimit; }
        public void setDailyRefundLimit(BigDecimal dailyRefundLimit) { this.dailyRefundLimit = dailyRefundLimit; }
        
        public BigDecimal getMonthlyRefundLimit() { return monthlyRefundLimit; }
        public void setMonthlyRefundLimit(BigDecimal monthlyRefundLimit) { this.monthlyRefundLimit = monthlyRefundLimit; }
        
        public int getMaxRefundsPerPayment() { return maxRefundsPerPayment; }
        public void setMaxRefundsPerPayment(int maxRefundsPerPayment) { this.maxRefundsPerPayment = maxRefundsPerPayment; }
        
        public int getRefundWindowDays() { return refundWindowDays; }
        public void setRefundWindowDays(int refundWindowDays) { this.refundWindowDays = refundWindowDays; }
    }
    
    class ProviderHealthStatus {
        private boolean healthy;
        private String status;
        private String message;
        private Instant lastChecked;
        private long responseTimeMillis;
        private Map<String, Object> details;
        
        // Constructors
        public ProviderHealthStatus() {}
        
        public ProviderHealthStatus(boolean healthy, String status, String message) {
            this.healthy = healthy;
            this.status = status;
            this.message = message;
            this.lastChecked = Instant.now();
        }
        
        // Getters and setters
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public Instant getLastChecked() { return lastChecked; }
        public void setLastChecked(Instant lastChecked) { this.lastChecked = lastChecked; }
        
        public long getResponseTimeMillis() { return responseTimeMillis; }
        public void setResponseTimeMillis(long responseTimeMillis) { this.responseTimeMillis = responseTimeMillis; }
        
        public Map<String, Object> getDetails() { return details; }
        public void setDetails(Map<String, Object> details) { this.details = details; }
        
        // Static factory methods
        public static ProviderHealthStatus healthy() {
            return new ProviderHealthStatus(true, "UP", "Provider is healthy");
        }
        
        public static ProviderHealthStatus unhealthy(String reason) {
            return new ProviderHealthStatus(false, "DOWN", reason);
        }
        
        public static ProviderHealthStatus degraded(String reason) {
            return new ProviderHealthStatus(true, "DEGRADED", reason);
        }
    }
}