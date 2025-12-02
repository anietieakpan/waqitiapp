package com.waqiti.payment.validation;

import com.waqiti.payment.client.dto.UserResponse;
import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.dto.ReconciliationRequest;
import com.waqiti.payment.dto.RefundRequest;
import com.waqiti.payment.validation.model.PaymentValidationResult;
import com.waqiti.payment.validation.model.ReconciliationValidationResult;
import com.waqiti.payment.validation.model.RefundValidationResult;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Enterprise Payment Validation Service Interface
 * 
 * Comprehensive validation service for all payment-related operations including:
 * - Payment request validation (amount, recipient, business rules)
 * - Refund request validation (eligibility, amounts, windows)  
 * - Reconciliation validation (settlement data, amounts)
 * - Security validation (IP addresses, fraud checks)
 * - Business rule validation (limits, compliance)
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
public interface PaymentValidationServiceInterface {
    
    // =====================================
    // PAYMENT VALIDATION
    // =====================================
    
    /**
     * Validate payment amount against business rules
     * 
     * @param amount the payment amount
     * @param currency the currency code
     * @return validation result
     */
    PaymentValidationResult validatePaymentAmount(BigDecimal amount, String currency);
    
    /**
     * Validate recipient exists and is eligible
     * 
     * @param recipientId the recipient user ID
     * @return recipient information if valid
     * @throws IllegalArgumentException if recipient invalid
     */
    UserResponse validateRecipientExists(UUID recipientId);
    
    // =====================================
    // REFUND VALIDATION
    // =====================================
    
    /**
     * Validate refund request eligibility and business rules
     * 
     * @param refundRequest the refund request
     * @return comprehensive refund validation result
     */
    RefundValidationResult validateRefundRequest(RefundRequest refundRequest);
    
    /**
     * Validate refund window timing
     * 
     * @param originalPaymentId the original payment ID
     * @param paymentMethod the original payment method
     * @return true if within refund window
     */
    boolean isWithinRefundWindow(String originalPaymentId, String paymentMethod);
    
    // =====================================
    // RECONCILIATION VALIDATION
    // =====================================
    
    /**
     * Validate reconciliation request
     * 
     * @param reconciliationRequest the reconciliation request
     * @return comprehensive reconciliation validation result
     */
    ReconciliationValidationResult validateReconciliationRequest(ReconciliationRequest reconciliationRequest);
    
    // =====================================
    // SECURITY VALIDATION
    // =====================================
    
    /**
     * Validate IP address format and security constraints
     * 
     * @param ipAddress the IP address to validate
     * @return true if valid and secure IP address
     */
    boolean isValidIPAddress(String ipAddress);
    
    /**
     * Validate if IP is from private network
     * 
     * @param ipAddress the IP address to check
     * @return true if private IP
     */
    boolean isPrivateIPAddress(String ipAddress);
}