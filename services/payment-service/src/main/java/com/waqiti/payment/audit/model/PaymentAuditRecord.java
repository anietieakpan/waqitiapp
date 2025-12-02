package com.waqiti.payment.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise Payment Audit Record
 * 
 * Comprehensive audit record for payment operations including:
 * - Complete transaction context and history
 * - Actor identification and authentication details
 * - Business logic decisions and validations
 * - Performance metrics and timing data
 * - Compliance and regulatory metadata
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAuditRecord {
    
    // Record identification
    private String auditId;
    private LocalDateTime timestamp;
    private String eventType;
    private EventCategory category;
    private EventSeverity severity;
    
    // Transaction context
    private String transactionId;
    private String paymentId;
    private String refundId;
    private String reconciliationId;
    private String settlementId;
    private TransactionType transactionType;
    
    // Actor information
    private UUID userId;
    private String userName;
    private String userEmail;
    private String userRole;
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private AuthenticationMethod authMethod;
    
    // Payment details
    private BigDecimal amount;
    private String currency;
    private UUID recipientId;
    private String recipientName;
    private String paymentMethod;
    private String paymentProvider;
    private PaymentStatus paymentStatus;
    
    // Operation details
    private String operationName;
    private OperationResult operationResult;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> operationMetadata;
    
    // Performance metrics
    private Long processingTimeMs;
    private Long responseTimeMs;
    private Long queueTimeMs;
    private Integer retryCount;
    private String performanceClassification;
    
    // Validation and security
    private ValidationStatus validationStatus;
    private Map<String, Boolean> validationChecks;
    private SecurityCheckResult securityCheckResult;
    private String kycVerificationLevel;
    private boolean fraudCheckPassed;
    private Integer riskScore;
    
    // Compliance and regulatory
    private boolean pciCompliant;
    private boolean amlCheckPassed;
    private String regulatoryJurisdiction;
    private Map<String, String> complianceMetadata;
    private boolean requiresReporting;
    private String reportingStatus;
    
    // System context
    private String serviceVersion;
    private String serverHostname;
    private String dataCenter;
    private String environmentType;
    private String correlationId;
    private String traceId;
    
    // Business context
    private String businessUnit;
    private String productType;
    private String customerSegment;
    private String merchantCategory;
    private Map<String, Object> businessMetadata;
    
    // Audit metadata
    private String previousAuditId;
    private String nextAuditId;
    private boolean isDeleted;
    private LocalDateTime deletedAt;
    private String deletedBy;
    private Integer dataRetentionDays;
    
    // Enums
    public enum EventCategory {
        PAYMENT_OPERATION,
        REFUND_OPERATION,
        RECONCILIATION,
        SECURITY_EVENT,
        COMPLIANCE_EVENT,
        CUSTOMER_MANAGEMENT,
        SYSTEM_EVENT,
        CONFIGURATION_CHANGE,
        DATA_ACCESS,
        INVESTIGATION
    }
    
    public enum EventSeverity {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        CRITICAL,
        SECURITY_ALERT
    }
    
    public enum TransactionType {
        P2P_PAYMENT,
        GROUP_PAYMENT,
        MERCHANT_PAYMENT,
        REFUND,
        REVERSAL,
        SETTLEMENT,
        RECONCILIATION,
        FEE,
        ADJUSTMENT
    }
    
    public enum PaymentStatus {
        INITIATED,
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REVERSED,
        SETTLED,
        RECONCILED
    }
    
    public enum OperationResult {
        SUCCESS,
        FAILURE,
        PARTIAL_SUCCESS,
        PENDING,
        TIMEOUT,
        CANCELLED,
        RETRY_SCHEDULED
    }
    
    public enum ValidationStatus {
        PASSED,
        FAILED,
        WARNING,
        BYPASSED,
        NOT_APPLICABLE
    }
    
    public enum SecurityCheckResult {
        PASSED,
        FAILED,
        REQUIRES_REVIEW,
        ESCALATED,
        BLOCKED
    }
    
    public enum AuthenticationMethod {
        PASSWORD,
        TWO_FACTOR,
        BIOMETRIC,
        OAUTH,
        SSO,
        API_KEY,
        JWT,
        CERTIFICATE
    }
    
    // Helper methods
    public boolean isSuccessful() {
        return operationResult == OperationResult.SUCCESS;
    }
    
    public boolean isSecurityEvent() {
        return category == EventCategory.SECURITY_EVENT || 
               severity == EventSeverity.SECURITY_ALERT;
    }
    
    public boolean requiresInvestigation() {
        return severity == EventSeverity.CRITICAL || 
               severity == EventSeverity.SECURITY_ALERT ||
               (securityCheckResult != null && securityCheckResult == SecurityCheckResult.REQUIRES_REVIEW);
    }
    
    public boolean isHighRisk() {
        return riskScore != null && riskScore > 70;
    }
    
    public boolean isSlowOperation() {
        return processingTimeMs != null && processingTimeMs > 5000;
    }
    
    // Static factory methods
    public static PaymentAuditRecord paymentOperation(String paymentId, UUID userId, BigDecimal amount, 
                                                      String currency, OperationResult result) {
        return PaymentAuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .eventType("PAYMENT_OPERATION")
            .category(EventCategory.PAYMENT_OPERATION)
            .severity(result == OperationResult.SUCCESS ? EventSeverity.INFO : EventSeverity.WARNING)
            .paymentId(paymentId)
            .transactionType(TransactionType.P2P_PAYMENT)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .operationResult(result)
            .build();
    }
    
    public static PaymentAuditRecord securityViolation(String violationType, UUID userId, 
                                                       String description, Map<String, Object> context) {
        return PaymentAuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .eventType(violationType)
            .category(EventCategory.SECURITY_EVENT)
            .severity(EventSeverity.SECURITY_ALERT)
            .userId(userId)
            .operationResult(OperationResult.FAILURE)
            .securityCheckResult(SecurityCheckResult.FAILED)
            .operationMetadata(context)
            .requiresReporting(true)
            .build();
    }
    
    public static PaymentAuditRecord refundOperation(String refundId, String paymentId, 
                                                     BigDecimal amount, OperationResult result) {
        return PaymentAuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .eventType("REFUND_OPERATION")
            .category(EventCategory.REFUND_OPERATION)
            .severity(EventSeverity.INFO)
            .refundId(refundId)
            .paymentId(paymentId)
            .transactionType(TransactionType.REFUND)
            .amount(amount)
            .operationResult(result)
            .build();
    }
}