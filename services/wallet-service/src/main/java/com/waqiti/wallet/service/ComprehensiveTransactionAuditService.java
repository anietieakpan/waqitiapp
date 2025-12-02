package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.Transaction;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.dto.TransferRequest;
import com.waqiti.wallet.dto.DepositRequest;
import com.waqiti.wallet.dto.WithdrawalRequest;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive Transaction Audit Service Interface
 * 
 * CRITICAL FINANCIAL COMPLIANCE: Complete audit trail for all wallet transactions
 * REGULATORY IMPACT: Ensures compliance with financial reporting requirements
 * 
 * Provides detailed audit logging for:
 * - Transaction creation and completion
 * - Balance changes
 * - Security context
 * - Regulatory compliance data
 * - Risk indicators
 */
public interface ComprehensiveTransactionAuditService {
    
    /**
     * Audit wallet creation
     */
    void auditWalletCreation(UUID userId, Wallet wallet, String creationContext);
    
    /**
     * Audit wallet status changes
     */
    void auditWalletStatusChange(Wallet wallet, String oldStatus, String newStatus, 
                                String reason, String changedBy);
    
    /**
     * Audit transaction initiation
     */
    void auditTransactionInitiated(Transaction transaction, String initiationContext, 
                                  String securityContext);
    
    /**
     * Audit transaction completion
     */
    void auditTransactionCompleted(Transaction transaction, BigDecimal balanceBefore, 
                                  BigDecimal balanceAfter, String completionContext);
    
    /**
     * Audit transaction failure
     */
    void auditTransactionFailed(String transactionId, UUID userId, String failureReason, 
                               String errorCode, Exception error);
    
    /**
     * Audit balance changes
     */
    void auditBalanceChange(UUID walletId, UUID userId, BigDecimal oldBalance, 
                           BigDecimal newBalance, String changeReason, String transactionId);
    
    /**
     * Audit transfer operations
     */
    void auditTransfer(TransferRequest request, String transactionId, 
                      Transaction debitTx, Transaction creditTx, String result);
    
    /**
     * Audit deposit operations
     */
    void auditDeposit(DepositRequest request, String transactionId, 
                     Transaction transaction, String paymentProvider, String result);
    
    /**
     * Audit withdrawal operations
     */
    void auditWithdrawal(WithdrawalRequest request, String transactionId, 
                        Transaction transaction, String paymentProvider, String result);
    
    /**
     * Audit suspicious activity
     */
    void auditSuspiciousActivity(UUID userId, String activityType, String description, 
                                Map<String, Object> suspiciousIndicators);
    
    /**
     * Audit regulatory threshold events
     */
    void auditRegulatoryThreshold(UUID userId, String thresholdType, BigDecimal amount, 
                                 BigDecimal threshold, String period);
    
    /**
     * Audit account freeze/unfreeze
     */
    void auditAccountFreezeAction(UUID userId, String action, String reason, 
                                 String authorizedBy, String caseId);
    
    /**
     * Audit high-risk transaction patterns
     */
    void auditHighRiskPattern(UUID userId, String patternType, String description, 
                             Double riskScore, Map<String, Object> patternData);
    
    /**
     * Audit compliance violations
     */
    void auditComplianceViolation(UUID userId, String violationType, String description, 
                                 String severity, String action);
    
    /**
     * Audit currency exchange operations
     */
    void auditCurrencyExchange(UUID userId, String fromCurrency, String toCurrency, 
                              BigDecimal fromAmount, BigDecimal toAmount, 
                              BigDecimal exchangeRate, String provider);
    
    /**
     * Audit limit changes
     */
    void auditLimitChange(UUID userId, String limitType, BigDecimal oldLimit, 
                         BigDecimal newLimit, String reason, String authorizedBy);
}