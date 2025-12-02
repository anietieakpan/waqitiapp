package com.waqiti.payment.service;

import com.waqiti.common.audit.annotation.AuditLogged;
import com.waqiti.common.audit.domain.AuditLog.EventCategory;
import com.waqiti.common.audit.domain.AuditLog.Severity;
import com.waqiti.payment.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Audited Payment Service with comprehensive audit logging
 * 
 * This service wraps payment operations with comprehensive audit logging
 * for compliance with SOX, PCI DSS, GDPR, and SOC 2 requirements.
 * 
 * AUDIT COVERAGE:
 * - Payment creation and processing
 * - Payment method changes
 * - Refunds and chargebacks
 * - Fee calculations
 * - Settlement operations
 * - Fraud detection events
 * - High-value transaction monitoring
 * 
 * COMPLIANCE MAPPING:
 * - PCI DSS: Payment card data processing events
 * - SOX: Financial transaction audit trails
 * - GDPR: Customer payment data access
 * - SOC 2: Security and operational controls
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditedPaymentService {
    
    private final PaymentService paymentService;
    
    /**
     * Create payment request with comprehensive audit logging
     */
    @AuditLogged(
        eventType = "PAYMENT_REQUEST_CREATED",
        category = EventCategory.FINANCIAL,
        severity = Severity.HIGH,
        description = "Payment request created for user #{requestorId} with amount #{request.amount} #{request.currency}",
        entityType = "Payment",
        entityIdExpression = "#result.paymentId",
        pciRelevant = true,
        soxRelevant = true,
        requiresNotification = false,
        riskScore = 30,
        metadata = {
            "amount: #request.amount",
            "currency: #request.currency", 
            "recipientId: #request.recipientId",
            "paymentMethod: #request.paymentMethod"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public PaymentRequestResponse createPaymentRequest(UUID requestorId, CreatePaymentRequestRequest request) {
        log.info("AUDIT: Creating payment request for user: {} amount: {} {}", 
                requestorId, request.getAmount(), request.getCurrency());
        
        return paymentService.createPaymentRequest(requestorId, request);
    }
    
    /**
     * Process payment with audit logging
     */
    @AuditLogged(
        eventType = "PAYMENT_PROCESSED",
        category = EventCategory.FINANCIAL,
        severity = Severity.HIGH,
        description = "Payment processed for request #{paymentRequestId} by user #{userId}",
        entityType = "Payment",
        entityIdExpression = "#paymentRequestId",
        pciRelevant = true,
        soxRelevant = true,
        requiresNotification = false,
        riskScore = 40,
        metadata = {
            "userId: #userId",
            "paymentRequestId: #paymentRequestId",
            "processingResult: #result.status"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public PaymentResponse processPayment(UUID userId, UUID paymentRequestId) {
        log.info("AUDIT: Processing payment request: {} for user: {}", paymentRequestId, userId);
        
        return paymentService.processPayment(userId, paymentRequestId);
    }
    
    /**
     * Cancel payment with audit logging
     */
    @AuditLogged(
        eventType = "PAYMENT_CANCELLED",
        category = EventCategory.FINANCIAL,
        severity = Severity.MEDIUM,
        description = "Payment cancelled for request #{paymentRequestId} by user #{userId} - reason: #{reason}",
        entityType = "Payment",
        entityIdExpression = "#paymentRequestId",
        pciRelevant = true,
        soxRelevant = true,
        metadata = {
            "userId: #userId",
            "paymentRequestId: #paymentRequestId",
            "reason: #reason"
        },
        captureParameters = true,
        sendToSiem = false
    )
    @Transactional
    public void cancelPaymentRequest(UUID userId, UUID paymentRequestId, String reason) {
        log.info("AUDIT: Cancelling payment request: {} for user: {} reason: {}", 
                paymentRequestId, userId, reason);
        
        paymentService.cancelPaymentRequest(userId, paymentRequestId, reason);
    }
    
    /**
     * Process refund with audit logging
     */
    @AuditLogged(
        eventType = "REFUND_PROCESSED",
        category = EventCategory.FINANCIAL,
        severity = Severity.HIGH,
        description = "Refund processed for payment #{paymentId} amount #{amount} #{currency}",
        entityType = "Refund",
        entityIdExpression = "#result.refundId",
        pciRelevant = true,
        soxRelevant = true,
        requiresNotification = true,
        riskScore = 50,
        metadata = {
            "paymentId: #paymentId",
            "amount: #amount",
            "currency: #currency",
            "reason: #reason"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public RefundResponse processRefund(UUID paymentId, BigDecimal amount, String currency, String reason) {
        log.info("AUDIT: Processing refund for payment: {} amount: {} {} reason: {}", 
                paymentId, amount, currency, reason);
        
        return paymentService.processRefund(paymentId, amount, currency, reason);
    }
    
    /**
     * Update payment method with audit logging
     */
    @AuditLogged(
        eventType = "PAYMENT_METHOD_UPDATED",
        category = EventCategory.FINANCIAL,
        severity = Severity.MEDIUM,
        description = "Payment method updated for user #{userId} from #{oldMethod} to #{newMethod}",
        entityType = "PaymentMethod",
        entityIdExpression = "#userId",
        pciRelevant = true,
        gdprRelevant = true,
        metadata = {
            "userId: #userId",
            "oldMethod: #oldMethod",
            "newMethod: #newMethod"
        },
        captureParameters = true,
        excludeFields = {"cardNumber", "cvv", "accountNumber"},
        sendToSiem = false
    )
    @Transactional
    public void updatePaymentMethod(UUID userId, String oldMethod, String newMethod) {
        log.info("AUDIT: Updating payment method for user: {} from: {} to: {}", 
                userId, oldMethod, newMethod);
        
        paymentService.updatePaymentMethod(userId, oldMethod, newMethod);
    }
    
    /**
     * Process high-value transaction with enhanced audit logging
     */
    @AuditLogged(
        eventType = "HIGH_VALUE_TRANSACTION",
        category = EventCategory.FINANCIAL,
        severity = Severity.CRITICAL,
        description = "High-value transaction processed - amount #{amount} #{currency} for user #{userId}",
        entityType = "HighValueTransaction",
        entityIdExpression = "#result.transactionId",
        pciRelevant = true,
        soxRelevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 80,
        metadata = {
            "userId: #userId",
            "amount: #amount",
            "currency: #currency",
            "thresholdExceeded: true",
            "reviewRequired: true"
        },
        captureParameters = true,
        captureReturnValue = true,
        captureExecutionTime = true,
        sendToSiem = true
    )
    @Transactional
    public TransactionResponse processHighValueTransaction(UUID userId, BigDecimal amount, String currency) {
        log.warn("AUDIT: Processing high-value transaction for user: {} amount: {} {}", 
                userId, amount, currency);
        
        return paymentService.processHighValueTransaction(userId, amount, currency);
    }
    
    /**
     * Detect and log fraudulent activity
     */
    @AuditLogged(
        eventType = "FRAUD_DETECTED",
        category = EventCategory.FRAUD,
        severity = Severity.CRITICAL,
        description = "Fraudulent activity detected for user #{userId} - type: #{fraudType}",
        entityType = "FraudEvent",
        entityIdExpression = "#userId",
        pciRelevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 95,
        metadata = {
            "userId: #userId",
            "fraudType: #fraudType",
            "confidence: #confidence",
            "immediateAction: #immediateAction"
        },
        captureParameters = true,
        sendToSiem = true
    )
    public void reportFraudulentActivity(UUID userId, String fraudType, double confidence, String immediateAction) {
        log.error("AUDIT: FRAUD DETECTED - User: {} Type: {} Confidence: {} Action: {}", 
                userId, fraudType, confidence, immediateAction);
        
        paymentService.reportFraudulentActivity(userId, fraudType, confidence, immediateAction);
    }
    
    /**
     * Process chargeback with audit logging
     */
    @AuditLogged(
        eventType = "CHARGEBACK_RECEIVED",
        category = EventCategory.FINANCIAL,
        severity = Severity.HIGH,
        description = "Chargeback received for payment #{paymentId} amount #{amount} #{currency}",
        entityType = "Chargeback",
        entityIdExpression = "#result.chargebackId",
        pciRelevant = true,
        soxRelevant = true,
        requiresNotification = true,
        riskScore = 70,
        metadata = {
            "paymentId: #paymentId",
            "amount: #amount",
            "currency: #currency",
            "reason: #reason",
            "merchantId: #merchantId"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public ChargebackResponse processChargeback(UUID paymentId, BigDecimal amount, String currency, 
                                               String reason, UUID merchantId) {
        log.warn("AUDIT: Processing chargeback for payment: {} amount: {} {} reason: {}", 
                paymentId, amount, currency, reason);
        
        return paymentService.processChargeback(paymentId, amount, currency, reason, merchantId);
    }
    
    /**
     * Calculate fees with audit logging
     */
    @AuditLogged(
        eventType = "FEE_CALCULATED",
        category = EventCategory.FINANCIAL,
        severity = Severity.INFO,
        description = "Fees calculated for transaction amount #{amount} #{currency}",
        entityType = "FeeCalculation",
        soxRelevant = true,
        metadata = {
            "amount: #amount",
            "currency: #currency",
            "transactionType: #transactionType",
            "calculatedFee: #result.feeAmount"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = false
    )
    public FeeCalculationResponse calculateFees(BigDecimal amount, String currency, String transactionType) {
        log.debug("AUDIT: Calculating fees for amount: {} {} type: {}", amount, currency, transactionType);
        
        return paymentService.calculateFees(amount, currency, transactionType);
    }
    
    /**
     * Process settlement with audit logging
     */
    @AuditLogged(
        eventType = "SETTLEMENT_PROCESSED",
        category = EventCategory.FINANCIAL,
        severity = Severity.HIGH,
        description = "Settlement processed for batch #{batchId} total amount #{totalAmount}",
        entityType = "Settlement",
        entityIdExpression = "#result.settlementId",
        soxRelevant = true,
        requiresNotification = true,
        metadata = {
            "batchId: #batchId",
            "totalAmount: #totalAmount",
            "transactionCount: #transactionCount",
            "settlementDate: #result.settlementDate"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public SettlementResponse processSettlement(UUID batchId, BigDecimal totalAmount, int transactionCount) {
        log.info("AUDIT: Processing settlement for batch: {} amount: {} transactions: {}", 
                batchId, totalAmount, transactionCount);
        
        return paymentService.processSettlement(batchId, totalAmount, transactionCount);
    }
    
    /**
     * Access payment card data with audit logging
     */
    @AuditLogged(
        eventType = "PAYMENT_CARD_DATA_ACCESSED",
        category = EventCategory.DATA_ACCESS,
        severity = Severity.HIGH,
        description = "Payment card data accessed for user #{userId} by operator #{currentUser}",
        entityType = "PaymentCardData",
        entityIdExpression = "#userId",
        pciRelevant = true,
        gdprRelevant = true,
        requiresNotification = true,
        metadata = {
            "userId: #userId",
            "accessReason: #accessReason",
            "dataFields: #dataFields"
        },
        captureParameters = true,
        excludeFields = {"cardNumber", "cvv", "expiryDate"},
        sendToSiem = true
    )
    public PaymentCardDataResponse accessPaymentCardData(UUID userId, String accessReason, String[] dataFields) {
        log.info("AUDIT: Accessing payment card data for user: {} reason: {}", userId, accessReason);
        
        return paymentService.accessPaymentCardData(userId, accessReason, dataFields);
    }
    
    /**
     * Export payment data with audit logging
     */
    @AuditLogged(
        eventType = "PAYMENT_DATA_EXPORTED",
        category = EventCategory.DATA_ACCESS,
        severity = Severity.HIGH,
        description = "Payment data exported for date range #{startDate} to #{endDate}",
        entityType = "DataExport",
        pciRelevant = true,
        gdprRelevant = true,
        soxRelevant = true,
        requiresNotification = true,
        metadata = {
            "startDate: #startDate",
            "endDate: #endDate",
            "exportFormat: #exportFormat",
            "recordCount: #result.recordCount"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    public DataExportResponse exportPaymentData(String startDate, String endDate, String exportFormat) {
        log.info("AUDIT: Exporting payment data for period: {} to {} format: {}", 
                startDate, endDate, exportFormat);
        
        return paymentService.exportPaymentData(startDate, endDate, exportFormat);
    }
    
    /**
     * Process international transfer with enhanced audit logging
     */
    @AuditLogged(
        eventType = "INTERNATIONAL_TRANSFER_PROCESSED",
        category = EventCategory.FINANCIAL,
        severity = Severity.CRITICAL,
        description = "International transfer processed from #{fromCountry} to #{toCountry} amount #{amount} #{currency}",
        entityType = "InternationalTransfer",
        entityIdExpression = "#result.transferId",
        pciRelevant = true,
        soxRelevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 85,
        metadata = {
            "fromCountry: #fromCountry",
            "toCountry: #toCountry",
            "amount: #amount",
            "currency: #currency",
            "userId: #userId",
            "complianceCheck: #result.complianceStatus"
        },
        captureParameters = true,
        captureReturnValue = true,
        captureExecutionTime = true,
        sendToSiem = true
    )
    @Transactional
    public InternationalTransferResponse processInternationalTransfer(UUID userId, String fromCountry, 
                                                                    String toCountry, BigDecimal amount, String currency) {
        log.warn("AUDIT: Processing international transfer - User: {} From: {} To: {} Amount: {} {}", 
                userId, fromCountry, toCountry, amount, currency);
        
        return paymentService.processInternationalTransfer(userId, fromCountry, toCountry, amount, currency);
    }
    
    /**
     * Process payment failure with audit logging
     */
    @AuditLogged(
        eventType = "PAYMENT_FAILED",
        category = EventCategory.FINANCIAL,
        severity = Severity.MEDIUM,
        description = "Payment failed for user #{userId} - reason: #{failureReason}",
        entityType = "PaymentFailure",
        entityIdExpression = "#paymentId",
        pciRelevant = true,
        soxRelevant = true,
        metadata = {
            "userId: #userId",
            "paymentId: #paymentId",
            "failureReason: #failureReason",
            "errorCode: #errorCode",
            "retryable: #retryable"
        },
        captureParameters = true,
        auditSuccessOnly = false,
        sendToSiem = true
    )
    public void recordPaymentFailure(UUID userId, UUID paymentId, String failureReason, 
                                   String errorCode, boolean retryable) {
        log.warn("AUDIT: Payment failure recorded - User: {} Payment: {} Reason: {} Error: {}", 
                userId, paymentId, failureReason, errorCode);
        
        paymentService.recordPaymentFailure(userId, paymentId, failureReason, errorCode, retryable);
    }
}