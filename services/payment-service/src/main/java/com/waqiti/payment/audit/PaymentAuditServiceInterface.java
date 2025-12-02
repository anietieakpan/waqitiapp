package com.waqiti.payment.audit;

import com.waqiti.payment.audit.model.*;
import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.dto.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise Payment Audit Service Interface
 * 
 * Comprehensive audit service for all payment-related operations including:
 * - Security event logging (violations, suspicious patterns, high-risk operations)
 * - Compliance audit trails (regulatory requirements, PCI, AML)
 * - Operational metrics tracking (performance, success rates, errors)
 * - Business intelligence data collection (trends, patterns, analytics)
 * - Forensic investigation support (detailed event reconstruction)
 * - Real-time alerting for critical events
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
public interface PaymentAuditServiceInterface {
    
    // =====================================
    // PAYMENT OPERATION AUDITING
    // =====================================
    
    /**
     * Audit successful payment request creation
     * 
     * @param paymentId the payment ID
     * @param requestorId the requestor user ID
     * @param amount the payment amount
     * @param currency the currency
     * @param recipientId the recipient ID
     * @param metadata additional metadata
     * @return audit record ID
     */
    String auditPaymentRequestCreated(String paymentId, UUID requestorId, BigDecimal amount, 
                                      String currency, UUID recipientId, Map<String, Object> metadata);
    
    /**
     * Audit failed payment request
     * 
     * @param requestorId the requestor user ID
     * @param amount the payment amount
     * @param errorType the error type
     * @param errorMessage the error message
     * @param metadata additional metadata
     * @return audit record ID
     */
    String auditPaymentRequestFailed(UUID requestorId, BigDecimal amount, 
                                     String errorType, String errorMessage, Map<String, Object> metadata);
    
    /**
     * Audit payment completion
     * 
     * @param paymentId the payment ID
     * @param status the completion status
     * @param processingTimeMs processing time in milliseconds
     * @param metadata additional metadata
     * @return audit record ID
     */
    String auditPaymentCompleted(String paymentId, String status, long processingTimeMs, Map<String, Object> metadata);
    
    // =====================================
    // REFUND OPERATION AUDITING
    // =====================================
    
    /**
     * Audit refund request
     * 
     * @param refundRequest the refund request
     * @param initiatedBy who initiated the refund
     * @return audit record ID
     */
    String auditRefundRequested(RefundRequest refundRequest, String initiatedBy);
    
    /**
     * Audit refund completion
     * 
     * @param refundId the refund ID
     * @param originalPaymentId the original payment ID
     * @param amount the refund amount
     * @param status the refund status
     * @param initiatedBy who initiated the refund
     * @param metadata additional metadata
     * @return audit record ID
     */
    String auditRefundCompleted(String refundId, String originalPaymentId, BigDecimal amount, 
                                String status, String initiatedBy, Map<String, Object> metadata);
    
    /**
     * Audit refund failure
     * 
     * @param refundId the refund ID
     * @param originalPaymentId the original payment ID
     * @param reason failure reason
     * @param initiatedBy who initiated the refund
     * @param metadata additional metadata
     * @return audit record ID
     */
    String auditRefundFailed(String refundId, String originalPaymentId, String reason, 
                            String initiatedBy, Map<String, Object> metadata);
    
    // =====================================
    // RECONCILIATION AUDITING
    // =====================================
    
    /**
     * Audit reconciliation process
     * 
     * @param reconciliationRequest the reconciliation request
     * @param discrepancies list of discrepancies found
     * @return audit record ID
     */
    String auditReconciliation(ReconciliationRequest reconciliationRequest, 
                              List<ReconciliationDiscrepancy> discrepancies);
    
    /**
     * Audit reconciliation completion
     * 
     * @param reconciliationId the reconciliation ID
     * @param settlementId the settlement ID
     * @param totalPayments total payments reconciled
     * @param variance total variance amount
     * @param initiatedBy who initiated the reconciliation
     * @param metadata additional metadata
     * @return audit record ID
     */
    String auditReconciliationCompleted(String reconciliationId, String settlementId, int totalPayments,
                                        BigDecimal variance, String initiatedBy, Map<String, Object> metadata);
    
    // =====================================
    // SECURITY EVENT AUDITING
    // =====================================
    
    /**
     * Audit security violation
     * 
     * @param violationType type of violation
     * @param userId user who triggered violation
     * @param description violation description
     * @param context additional context
     * @return audit record ID
     */
    String auditSecurityViolation(String violationType, String userId, String description, Map<String, Object> context);
    
    /**
     * Audit security event
     * 
     * @param eventType type of security event
     * @param userId user involved
     * @param description event description
     * @param context additional context
     * @return audit record ID
     */
    String auditSecurityEvent(String eventType, String userId, String description, Map<String, Object> context);
    
    /**
     * Audit suspicious payment pattern
     * 
     * @param userId user with suspicious activity
     * @param patternType type of suspicious pattern
     * @param details pattern details
     * @return audit record ID
     */
    String auditSuspiciousPattern(UUID userId, String patternType, Map<String, Object> details);
    
    /**
     * Audit high-value payment attempt
     * 
     * @param userId user attempting payment
     * @param amount payment amount
     * @param currency payment currency
     * @param requiresManualReview whether manual review is required
     * @return audit record ID
     */
    String auditHighValuePayment(UUID userId, BigDecimal amount, String currency, boolean requiresManualReview);
    
    /**
     * Audit self-payment attempt
     * 
     * @param userId user attempting self-payment
     * @param amount payment amount
     * @param ipAddress client IP address
     * @return audit record ID
     */
    String auditSelfPaymentAttempt(UUID userId, BigDecimal amount, String ipAddress);
    
    /**
     * Audit insufficient KYC verification
     * 
     * @param userId user with insufficient KYC
     * @param requestedAmount requested payment amount
     * @param verificationLevel current verification level
     * @return audit record ID
     */
    String auditInsufficientKYC(UUID userId, BigDecimal requestedAmount, String verificationLevel);
    
    // =====================================
    // CUSTOMER ACCOUNT AUDITING
    // =====================================
    
    /**
     * Audit customer activation
     * 
     * @param customerId customer ID
     * @param activatedBy who activated the account
     * @return audit record ID
     */
    String auditCustomerActivation(String customerId, String activatedBy);
    
    /**
     * Audit customer suspension
     * 
     * @param customerId customer ID
     * @param reason suspension reason
     * @param suspendedBy who suspended the account
     * @return audit record ID
     */
    String auditCustomerSuspension(String customerId, String reason, String suspendedBy);
    
    /**
     * Audit ineligible customer activation attempt
     * 
     * @param customerId customer ID
     * @param reason ineligibility reason
     * @return audit record ID
     */
    String auditIneligibleActivation(String customerId, String reason);
    
    // =====================================
    // METRICS AND PERFORMANCE TRACKING
    // =====================================
    
    /**
     * Track payment operation metrics
     * 
     * @param operationType type of operation
     * @param success whether operation succeeded
     * @param processingTimeMs processing time in milliseconds
     * @param metadata additional metrics
     */
    void trackOperationMetrics(String operationType, boolean success, long processingTimeMs, Map<String, Object> metadata);
    
    /**
     * Update payment metrics
     * 
     * @param metricName metric name
     * @param value metric value
     * @param tags metric tags
     */
    void updateMetric(String metricName, double value, Map<String, String> tags);
    
    /**
     * Increment counter metric
     * 
     * @param counterName counter name
     * @param tags counter tags
     */
    void incrementCounter(String counterName, Map<String, String> tags);
    
    /**
     * Record gauge metric
     * 
     * @param gaugeName gauge name
     * @param value gauge value
     * @param tags gauge tags
     */
    void recordGauge(String gaugeName, double value, Map<String, String> tags);
    
    // =====================================
    // AUDIT TRAIL QUERIES
    // =====================================
    
    /**
     * Get audit trail for payment
     * 
     * @param paymentId payment ID
     * @return list of audit records
     */
    List<PaymentAuditRecord> getPaymentAuditTrail(String paymentId);
    
    /**
     * Get audit trail for user
     * 
     * @param userId user ID
     * @param startTime start time
     * @param endTime end time
     * @return list of audit records
     */
    List<PaymentAuditRecord> getUserAuditTrail(UUID userId, LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Get security violations for user
     * 
     * @param userId user ID
     * @param limit max records to return
     * @return list of security audit records
     */
    List<SecurityAuditRecord> getUserSecurityViolations(UUID userId, int limit);
    
    /**
     * Get suspicious activity report
     * 
     * @param startTime start time
     * @param endTime end time
     * @return suspicious activity report
     */
    SuspiciousActivityReport getSuspiciousActivityReport(LocalDateTime startTime, LocalDateTime endTime);
    
    // =====================================
    // COMPLIANCE AND REPORTING
    // =====================================
    
    /**
     * Generate compliance report
     * 
     * @param reportType type of compliance report
     * @param startTime start time
     * @param endTime end time
     * @return compliance report
     */
    ComplianceReport generateComplianceReport(String reportType, LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Export audit logs for external systems
     * 
     * @param format export format (JSON, CSV, etc.)
     * @param startTime start time
     * @param endTime end time
     * @return exported audit data
     */
    String exportAuditLogs(String format, LocalDateTime startTime, LocalDateTime endTime);
    
    // =====================================
    // SYSTEM INITIALIZATION AND HEALTH
    // =====================================
    
    /**
     * Initialize audit service security logging
     */
    void initializeSecurityLogging();
    
    /**
     * Check audit service health
     * 
     * @return true if healthy
     */
    boolean isHealthy();
    
    /**
     * Get audit service statistics
     * 
     * @return service statistics
     */
    AuditServiceStatistics getStatistics();
}