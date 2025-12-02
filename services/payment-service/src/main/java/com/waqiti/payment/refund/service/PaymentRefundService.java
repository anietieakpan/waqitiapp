package com.waqiti.payment.refund.service;

import com.waqiti.payment.core.model.RefundRequest;
import com.waqiti.payment.refund.model.RefundResult;
import com.waqiti.payment.refund.model.RefundValidationResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise Payment Refund Service Interface
 * 
 * Comprehensive refund processing service that integrates with:
 * - UnifiedPaymentService from payment-commons
 * - Multi-provider refund processing
 * - Compliance and risk management
 * - Distributed locking and transaction safety
 * - Comprehensive audit and monitoring
 * - Event-driven architecture with Kafka
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
public interface PaymentRefundService {
    
    // =====================================
    // CORE REFUND OPERATIONS
    // =====================================
    
    /**
     * Process a refund request with comprehensive validation and provider integration
     * 
     * @param refundRequest the enterprise refund request
     * @return refund processing result with detailed status and audit information
     * @throws RefundProcessingException if refund processing fails
     * @throws RefundValidationException if validation fails
     * @throws ComplianceException if compliance checks fail
     */
    RefundResult processRefund(RefundRequest refundRequest);
    
    /**
     * Process refund asynchronously for high-volume operations
     * 
     * @param refundRequest the enterprise refund request
     * @return future containing refund processing result
     */
    CompletableFuture<RefundResult> processRefundAsync(RefundRequest refundRequest);
    
    /**
     * Process multiple refunds in batch for efficiency
     * 
     * @param refundRequests list of refund requests
     * @return list of refund results with batch processing information
     */
    List<RefundResult> processBatchRefunds(List<RefundRequest> refundRequests);
    
    /**
     * Process partial refund with amount validation
     * 
     * @param originalPaymentId the original payment ID
     * @param refundAmount the amount to refund
     * @param reason the refund reason
     * @param requestedBy user requesting the refund
     * @return refund processing result
     */
    RefundResult processPartialRefund(String originalPaymentId, 
                                     BigDecimal refundAmount, 
                                     String reason, 
                                     String requestedBy);
    
    /**
     * Process full refund of the original payment
     * 
     * @param originalPaymentId the original payment ID
     * @param reason the refund reason
     * @param requestedBy user requesting the refund
     * @return refund processing result
     */
    RefundResult processFullRefund(String originalPaymentId, 
                                  String reason, 
                                  String requestedBy);
    
    // =====================================
    // REFUND VALIDATION
    // =====================================
    
    /**
     * Validate refund request eligibility and compliance
     * 
     * @param refundRequest the refund request to validate
     * @return comprehensive validation result with policy and risk assessment
     */
    RefundValidationResult validateRefundRequest(RefundRequest refundRequest);
    
    /**
     * Check if a payment is eligible for refund
     * 
     * @param originalPaymentId the original payment ID
     * @return true if eligible for refund
     */
    boolean isEligibleForRefund(String originalPaymentId);
    
    /**
     * Check if payment is within refund window
     * 
     * @param originalPaymentId the original payment ID
     * @return true if within refund window
     */
    boolean isWithinRefundWindow(String originalPaymentId);
    
    /**
     * Get maximum refundable amount for a payment
     * 
     * @param originalPaymentId the original payment ID
     * @return maximum refundable amount considering partial refunds
     */
    BigDecimal getMaxRefundableAmount(String originalPaymentId);
    
    /**
     * Get total amount already refunded for a payment
     * 
     * @param originalPaymentId the original payment ID
     * @return total refunded amount
     */
    BigDecimal getTotalRefundedAmount(String originalPaymentId);
    
    // =====================================
    // REFUND STATUS MANAGEMENT
    // =====================================
    
    /**
     * Update refund status with audit trail
     * 
     * @param refundId the refund ID
     * @param status the new status
     * @param updatedBy user updating the status
     * @param reason reason for status update
     */
    void updateRefundStatus(String refundId, 
                           RefundResult.RefundStatus status, 
                           String updatedBy, 
                           String reason);
    
    /**
     * Mark refund as failed with detailed error information
     * 
     * @param refundId the refund ID
     * @param errorCode the error code
     * @param errorMessage the error message
     * @param failedBy user or system that marked as failed
     */
    void markRefundFailed(String refundId, 
                         String errorCode, 
                         String errorMessage, 
                         String failedBy);
    
    /**
     * Approve refund for manual review cases
     * 
     * @param refundId the refund ID
     * @param approvedBy user approving the refund
     * @param approvalNotes approval notes
     * @return updated refund result
     */
    RefundResult approveRefund(String refundId, String approvedBy, String approvalNotes);
    
    /**
     * Reject refund with reason
     * 
     * @param refundId the refund ID
     * @param rejectedBy user rejecting the refund
     * @param rejectionReason reason for rejection
     * @return updated refund result
     */
    RefundResult rejectRefund(String refundId, String rejectedBy, String rejectionReason);
    
    /**
     * Cancel pending refund
     * 
     * @param refundId the refund ID
     * @param cancelledBy user cancelling the refund
     * @param cancellationReason reason for cancellation
     * @return updated refund result
     */
    RefundResult cancelRefund(String refundId, String cancelledBy, String cancellationReason);
    
    // =====================================
    // REFUND RETRIEVAL
    // =====================================
    
    /**
     * Get refund result by refund ID
     * 
     * @param refundId the refund ID
     * @return refund result or null if not found
     */
    RefundResult getRefundById(String refundId);
    
    /**
     * Get all refunds for a payment
     * 
     * @param originalPaymentId the original payment ID
     * @return list of refund results
     */
    List<RefundResult> getRefundsByPaymentId(String originalPaymentId);
    
    /**
     * Get refunds by status
     * 
     * @param status the refund status
     * @param limit maximum number of results
     * @return list of refund results
     */
    List<RefundResult> getRefundsByStatus(RefundResult.RefundStatus status, int limit);
    
    /**
     * Get refunds requiring manual review
     * 
     * @param limit maximum number of results
     * @return list of refund results requiring review
     */
    List<RefundResult> getRefundsRequiringReview(int limit);
    
    /**
     * Get failed refunds for retry processing
     * 
     * @param limit maximum number of results
     * @return list of failed refund results
     */
    List<RefundResult> getFailedRefundsForRetry(int limit);
    
    // =====================================
    // RETRY AND RECOVERY
    // =====================================
    
    /**
     * Retry failed refund with exponential backoff
     * 
     * @param refundId the refund ID to retry
     * @return updated refund result
     */
    RefundResult retryFailedRefund(String refundId);
    
    /**
     * Retry refund with custom provider
     * 
     * @param refundId the refund ID
     * @param alternativeProvider the alternative provider to use
     * @return updated refund result
     */
    RefundResult retryWithAlternativeProvider(String refundId, String alternativeProvider);
    
    /**
     * Process refunds stuck in processing state
     * 
     * @param olderThan process refunds older than this time
     * @return number of refunds processed
     */
    int processStuckRefunds(Instant olderThan);
    
    /**
     * Handle provider callback for refund status update
     * 
     * @param providerRefundId the provider refund ID
     * @param providerStatus the provider status
     * @param providerMessage the provider message
     */
    void handleProviderCallback(String providerRefundId, 
                               String providerStatus, 
                               String providerMessage);
    
    // =====================================
    // FINANCIAL RECONCILIATION
    // =====================================
    
    /**
     * Calculate refund fees based on payment method and amount
     * 
     * @param originalPaymentId the original payment ID
     * @param refundAmount the refund amount
     * @return calculated refund fee
     */
    BigDecimal calculateRefundFee(String originalPaymentId, BigDecimal refundAmount);
    
    /**
     * Reconcile refund with provider settlement
     * 
     * @param refundId the refund ID
     * @param settlementData the settlement data from provider
     * @return reconciliation result
     */
    RefundResult reconcileRefundSettlement(String refundId, Object settlementData);
    
    /**
     * Generate refund reconciliation report
     * 
     * @param fromDate start date for report
     * @param toDate end date for report
     * @return reconciliation report data
     */
    Object generateReconciliationReport(Instant fromDate, Instant toDate);
    
    // =====================================
    // COMPLIANCE AND RISK
    // =====================================
    
    /**
     * Perform enhanced fraud check on refund request
     * 
     * @param refundRequest the refund request
     * @return fraud analysis result
     */
    Object performFraudCheck(RefundRequest refundRequest);
    
    /**
     * Perform AML check on refund request
     * 
     * @param refundRequest the refund request
     * @return AML check result
     */
    Object performAMLCheck(RefundRequest refundRequest);
    
    /**
     * Flag suspicious refund pattern
     * 
     * @param userId the user ID
     * @param refundPattern the suspicious pattern details
     */
    void flagSuspiciousRefundPattern(String userId, String refundPattern);
    
    // =====================================
    // MONITORING AND ANALYTICS
    // =====================================
    
    /**
     * Get refund processing metrics
     * 
     * @param fromDate start date for metrics
     * @param toDate end date for metrics
     * @return refund metrics data
     */
    Object getRefundMetrics(Instant fromDate, Instant toDate);
    
    /**
     * Get refund success rate by provider
     * 
     * @return provider success rate map
     */
    Object getProviderSuccessRates();
    
    /**
     * Get average refund processing time
     * 
     * @param fromDate start date for calculation
     * @param toDate end date for calculation
     * @return average processing time in milliseconds
     */
    Long getAverageProcessingTime(Instant fromDate, Instant toDate);
    
    /**
     * Check refund service health
     * 
     * @return health status information
     */
    Object getServiceHealth();
}