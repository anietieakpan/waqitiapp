package com.waqiti.wallet.service.impl;

import com.waqiti.wallet.service.ComprehensiveTransactionAuditService;
import com.waqiti.wallet.domain.Transaction;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.dto.TransferRequest;
import com.waqiti.wallet.dto.DepositRequest;
import com.waqiti.wallet.dto.WithdrawalRequest;
import com.waqiti.common.audit.ComprehensiveAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive Transaction Audit Service Implementation
 * 
 * CRITICAL FINANCIAL COMPLIANCE: Complete audit trail for regulatory compliance
 * 
 * Provides exhaustive audit logging with:
 * - Full transaction lifecycle tracking
 * - Regulatory compliance data
 * - Security context capture
 * - Risk indicator logging
 * - Balance change tracking
 * - Suspicious activity detection
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComprehensiveTransactionAuditServiceImpl implements ComprehensiveTransactionAuditService {
    
    private final ComprehensiveAuditService auditService;
    
    @Override
    public void auditWalletCreation(UUID userId, Wallet wallet, String creationContext) {
        log.info("AUDIT: Wallet created - User: {}, Wallet: {}", userId, wallet.getId());
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("walletId", wallet.getId());
        auditData.put("walletType", wallet.getWalletType());
        auditData.put("currency", wallet.getCurrency());
        auditData.put("initialBalance", wallet.getBalance());
        auditData.put("status", wallet.getStatus());
        auditData.put("dailyLimit", wallet.getDailyLimit());
        auditData.put("monthlyLimit", wallet.getMonthlyLimit());
        auditData.put("creationContext", creationContext);
        auditData.put("securityContext", getCurrentSecurityContext());
        auditData.put("createdAt", LocalDateTime.now());
        
        auditService.auditFinancialTransaction(
            "WALLET_CREATED",
            userId.toString(),
            "Wallet created successfully",
            auditData
        );
    }
    
    @Override
    public void auditWalletStatusChange(Wallet wallet, String oldStatus, String newStatus, 
                                       String reason, String changedBy) {
        log.warn("AUDIT: Wallet status changed - Wallet: {}, {} -> {}", 
            wallet.getId(), oldStatus, newStatus);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("walletId", wallet.getId());
        auditData.put("userId", wallet.getUserId());
        auditData.put("oldStatus", oldStatus);
        auditData.put("newStatus", newStatus);
        auditData.put("reason", reason);
        auditData.put("changedBy", changedBy);
        auditData.put("currentBalance", wallet.getBalance());
        auditData.put("availableBalance", wallet.getAvailableBalance());
        auditData.put("securityContext", getCurrentSecurityContext());
        auditData.put("changedAt", LocalDateTime.now());
        
        String severity = "FROZEN".equals(newStatus) || "SUSPENDED".equals(newStatus) ? 
            "HIGH_RISK" : "STANDARD";
        
        auditService.auditHighRiskOperation(
            "WALLET_STATUS_CHANGED",
            wallet.getUserId().toString(),
            String.format("Wallet status changed from %s to %s: %s", oldStatus, newStatus, reason),
            auditData
        );
    }
    
    @Override
    public void auditTransactionInitiated(Transaction transaction, String initiationContext, 
                                         String securityContext) {
        log.info("AUDIT: Transaction initiated - ID: {}, Type: {}, Amount: {}", 
            transaction.getId(), transaction.getType(), transaction.getAmount());
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("transactionId", transaction.getId());
        auditData.put("walletId", transaction.getWalletId());
        auditData.put("userId", transaction.getUserId());
        auditData.put("transactionType", transaction.getType());
        auditData.put("amount", transaction.getAmount());
        auditData.put("currency", transaction.getCurrency());
        auditData.put("paymentMethod", transaction.getPaymentMethod());
        auditData.put("paymentMethodId", transaction.getPaymentMethodId());
        auditData.put("reference", transaction.getReference());
        auditData.put("description", transaction.getDescription());
        auditData.put("balanceBefore", transaction.getBalanceBefore());
        auditData.put("expectedBalanceAfter", transaction.getBalanceAfter());
        auditData.put("status", transaction.getStatus());
        auditData.put("initiationContext", initiationContext);
        auditData.put("securityContext", securityContext);
        auditData.put("metadata", transaction.getMetadata());
        auditData.put("initiatedAt", LocalDateTime.now());
        
        // Check for high-value transaction
        String eventType = isHighValueTransaction(transaction.getAmount()) ? 
            "HIGH_VALUE_TRANSACTION_INITIATED" : "TRANSACTION_INITIATED";
        
        auditService.auditFinancialTransaction(
            eventType,
            transaction.getUserId().toString(),
            String.format("Transaction initiated - Type: %s, Amount: %s %s", 
                transaction.getType(), transaction.getAmount(), transaction.getCurrency()),
            auditData
        );
    }
    
    @Override
    public void auditTransactionCompleted(Transaction transaction, BigDecimal balanceBefore, 
                                         BigDecimal balanceAfter, String completionContext) {
        log.info("AUDIT: Transaction completed - ID: {}, Final Balance: {}", 
            transaction.getId(), balanceAfter);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("transactionId", transaction.getId());
        auditData.put("walletId", transaction.getWalletId());
        auditData.put("userId", transaction.getUserId());
        auditData.put("transactionType", transaction.getType());
        auditData.put("amount", transaction.getAmount());
        auditData.put("currency", transaction.getCurrency());
        auditData.put("balanceBefore", balanceBefore);
        auditData.put("balanceAfter", balanceAfter);
        auditData.put("balanceChange", balanceAfter.subtract(balanceBefore));
        auditData.put("status", transaction.getStatus());
        auditData.put("paymentMethod", transaction.getPaymentMethod());
        auditData.put("providerTransactionId", transaction.getProviderTransactionId());
        auditData.put("completionContext", completionContext);
        auditData.put("processingDuration", calculateProcessingDuration(transaction));
        auditData.put("completedAt", LocalDateTime.now());
        
        // Additional data for regulatory compliance
        auditData.put("isInternational", isInternationalTransaction(transaction));
        auditData.put("riskIndicators", identifyRiskIndicators(transaction));
        auditData.put("regulatoryThresholds", checkRegulatoryThresholds(transaction));
        
        auditService.auditFinancialTransaction(
            "TRANSACTION_COMPLETED",
            transaction.getUserId().toString(),
            String.format("Transaction completed successfully - Amount: %s %s", 
                transaction.getAmount(), transaction.getCurrency()),
            auditData
        );
        
        // Additional audit for high-value transactions
        if (isHighValueTransaction(transaction.getAmount())) {
            auditHighValueTransactionCompleted(transaction, balanceBefore, balanceAfter);
        }
    }
    
    @Override
    public void auditTransactionFailed(String transactionId, UUID userId, String failureReason, 
                                      String errorCode, Exception error) {
        log.error("AUDIT: Transaction failed - ID: {}, User: {}, Reason: {}", 
            transactionId, userId, failureReason);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("transactionId", transactionId);
        auditData.put("userId", userId);
        auditData.put("failureReason", failureReason);
        auditData.put("errorCode", errorCode);
        auditData.put("errorMessage", error != null ? error.getMessage() : null);
        auditData.put("errorClass", error != null ? error.getClass().getSimpleName() : null);
        auditData.put("securityContext", getCurrentSecurityContext());
        auditData.put("failedAt", LocalDateTime.now());
        
        auditService.auditFinancialTransaction(
            "TRANSACTION_FAILED",
            userId.toString(),
            String.format("Transaction failed - Reason: %s, Error: %s", failureReason, errorCode),
            auditData
        );
    }
    
    @Override
    public void auditBalanceChange(UUID walletId, UUID userId, BigDecimal oldBalance, 
                                  BigDecimal newBalance, String changeReason, String transactionId) {
        log.info("AUDIT: Balance changed - Wallet: {}, {} -> {}", walletId, oldBalance, newBalance);
        
        BigDecimal changeAmount = newBalance.subtract(oldBalance);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("walletId", walletId);
        auditData.put("userId", userId);
        auditData.put("oldBalance", oldBalance);
        auditData.put("newBalance", newBalance);
        auditData.put("changeAmount", changeAmount);
        auditData.put("changeType", changeAmount.compareTo(BigDecimal.ZERO) > 0 ? "CREDIT" : "DEBIT");
        auditData.put("changeReason", changeReason);
        auditData.put("transactionId", transactionId);
        auditData.put("securityContext", getCurrentSecurityContext());
        auditData.put("changedAt", LocalDateTime.now());
        
        auditService.auditFinancialTransaction(
            "BALANCE_CHANGED",
            userId.toString(),
            String.format("Balance changed by %s - Reason: %s", changeAmount, changeReason),
            auditData
        );
        
        // Check for unusual balance changes
        if (isUnusualBalanceChange(changeAmount)) {
            auditUnusualBalanceChange(walletId, userId, changeAmount, changeReason, transactionId);
        }
    }
    
    @Override
    public void auditTransfer(TransferRequest request, String transactionId, 
                             Transaction debitTx, Transaction creditTx, String result) {
        log.info("AUDIT: Transfer operation - ID: {}, From: {} -> To: {}, Amount: {}", 
            transactionId, request.getFromUserId(), request.getToUserId(), request.getAmount());
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("transferId", transactionId);
        auditData.put("fromUserId", request.getFromUserId());
        auditData.put("toUserId", request.getToUserId());
        auditData.put("fromWalletId", request.getFromWalletId());
        auditData.put("toWalletId", request.getToWalletId());
        auditData.put("amount", request.getAmount());
        auditData.put("currency", request.getCurrency());
        auditData.put("reference", request.getReference());
        auditData.put("description", request.getDescription());
        auditData.put("result", result);
        auditData.put("debitTransactionId", debitTx != null ? debitTx.getId() : null);
        auditData.put("creditTransactionId", creditTx != null ? creditTx.getId() : null);
        auditData.put("metadata", request.getMetadata());
        auditData.put("securityContext", getCurrentSecurityContext());
        auditData.put("processedAt", LocalDateTime.now());
        
        // Additional transfer-specific data
        auditData.put("isInternalTransfer", request.getFromUserId().equals(request.getToUserId()));
        auditData.put("transferType", determineTransferType(request));
        
        auditService.auditFinancialTransaction(
            "WALLET_TRANSFER_PROCESSED",
            request.getFromUserId().toString(),
            String.format("Transfer processed - Amount: %s %s, Result: %s", 
                request.getAmount(), request.getCurrency(), result),
            auditData
        );
        
        // Audit for recipient as well
        if (!request.getFromUserId().equals(request.getToUserId())) {
            auditService.auditFinancialTransaction(
                "WALLET_TRANSFER_RECEIVED",
                request.getToUserId().toString(),
                String.format("Transfer received - Amount: %s %s from user %s", 
                    request.getAmount(), request.getCurrency(), request.getFromUserId()),
                auditData
            );
        }
    }
    
    @Override
    public void auditDeposit(DepositRequest request, String transactionId, 
                            Transaction transaction, String paymentProvider, String result) {
        log.info("AUDIT: Deposit operation - ID: {}, Wallet: {}, Amount: {}, Provider: {}", 
            transactionId, request.getWalletId(), request.getAmount(), paymentProvider);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("transactionId", transactionId);
        auditData.put("walletId", request.getWalletId());
        auditData.put("userId", transaction != null ? transaction.getUserId() : null);
        auditData.put("amount", request.getAmount());
        auditData.put("currency", request.getCurrency());
        auditData.put("paymentMethod", request.getPaymentMethod());
        auditData.put("paymentMethodId", request.getPaymentMethodId());
        auditData.put("paymentProvider", paymentProvider);
        auditData.put("providerTransactionId", transaction != null ? transaction.getProviderTransactionId() : null);
        auditData.put("reference", request.getReference());
        auditData.put("description", request.getDescription());
        auditData.put("result", result);
        auditData.put("balanceBefore", transaction != null ? transaction.getBalanceBefore() : null);
        auditData.put("balanceAfter", transaction != null ? transaction.getBalanceAfter() : null);
        auditData.put("metadata", request.getMetadata());
        auditData.put("securityContext", getCurrentSecurityContext());
        auditData.put("processedAt", LocalDateTime.now());
        
        auditService.auditFinancialTransaction(
            "WALLET_DEPOSIT_PROCESSED",
            transaction != null ? transaction.getUserId().toString() : "UNKNOWN",
            String.format("Deposit processed - Amount: %s %s, Result: %s", 
                request.getAmount(), request.getCurrency(), result),
            auditData
        );
    }
    
    @Override
    public void auditWithdrawal(WithdrawalRequest request, String transactionId, 
                               Transaction transaction, String paymentProvider, String result) {
        log.info("AUDIT: Withdrawal operation - ID: {}, Wallet: {}, Amount: {}, Provider: {}", 
            transactionId, request.getWalletId(), request.getAmount(), paymentProvider);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("transactionId", transactionId);
        auditData.put("walletId", request.getWalletId());
        auditData.put("userId", transaction != null ? transaction.getUserId() : null);
        auditData.put("amount", request.getAmount());
        auditData.put("currency", request.getCurrency());
        auditData.put("paymentMethod", request.getPaymentMethod());
        auditData.put("paymentMethodId", request.getPaymentMethodId());
        auditData.put("paymentProvider", paymentProvider);
        auditData.put("providerTransactionId", transaction != null ? transaction.getProviderTransactionId() : null);
        auditData.put("reference", request.getReference());
        auditData.put("description", request.getDescription());
        auditData.put("result", result);
        auditData.put("balanceBefore", transaction != null ? transaction.getBalanceBefore() : null);
        auditData.put("balanceAfter", transaction != null ? transaction.getBalanceAfter() : null);
        auditData.put("metadata", request.getMetadata());
        auditData.put("securityContext", getCurrentSecurityContext());
        auditData.put("processedAt", LocalDateTime.now());
        
        // Additional withdrawal risk indicators
        auditData.put("isHighRiskWithdrawal", isHighRiskWithdrawal(request));
        auditData.put("withdrawalRiskScore", calculateWithdrawalRiskScore(request));
        
        auditService.auditFinancialTransaction(
            "WALLET_WITHDRAWAL_PROCESSED",
            transaction != null ? transaction.getUserId().toString() : "UNKNOWN",
            String.format("Withdrawal processed - Amount: %s %s, Result: %s", 
                request.getAmount(), request.getCurrency(), result),
            auditData
        );
    }
    
    @Override
    public void auditSuspiciousActivity(UUID userId, String activityType, String description, 
                                       Map<String, Object> suspiciousIndicators) {
        log.warn("AUDIT: Suspicious activity detected - User: {}, Type: {}", userId, activityType);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("userId", userId);
        auditData.put("activityType", activityType);
        auditData.put("description", description);
        auditData.put("indicators", suspiciousIndicators);
        auditData.put("detectedAt", LocalDateTime.now());
        auditData.put("securityContext", getCurrentSecurityContext());
        
        auditService.auditCriticalSecurityEvent(
            "SUSPICIOUS_ACTIVITY_DETECTED",
            userId.toString(),
            String.format("Suspicious activity detected - Type: %s, Description: %s", 
                activityType, description),
            auditData
        );
    }
    
    @Override
    public void auditRegulatoryThreshold(UUID userId, String thresholdType, BigDecimal amount, 
                                        BigDecimal threshold, String period) {
        log.warn("AUDIT: Regulatory threshold exceeded - User: {}, Type: {}, Amount: {}", 
            userId, thresholdType, amount);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("userId", userId);
        auditData.put("thresholdType", thresholdType);
        auditData.put("amount", amount);
        auditData.put("threshold", threshold);
        auditData.put("period", period);
        auditData.put("exceedAmount", amount.subtract(threshold));
        auditData.put("exceedPercent", amount.divide(threshold, 4, RoundingMode.HALF_UP));
        auditData.put("detectedAt", LocalDateTime.now());
        
        auditService.auditCriticalComplianceEvent(
            "REGULATORY_THRESHOLD_EXCEEDED",
            userId.toString(),
            String.format("Regulatory threshold exceeded - Type: %s, Amount: %s (Threshold: %s)", 
                thresholdType, amount, threshold),
            auditData
        );
    }
    
    @Override
    public void auditAccountFreezeAction(UUID userId, String action, String reason, 
                                        String authorizedBy, String caseId) {
        log.error("AUDIT: Account freeze action - User: {}, Action: {}", userId, action);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("userId", userId);
        auditData.put("action", action);
        auditData.put("reason", reason);
        auditData.put("authorizedBy", authorizedBy);
        auditData.put("caseId", caseId);
        auditData.put("actionAt", LocalDateTime.now());
        auditData.put("securityContext", getCurrentSecurityContext());
        
        auditService.auditCriticalSecurityEvent(
            "ACCOUNT_FREEZE_ACTION",
            userId.toString(),
            String.format("Account freeze action - Action: %s, Reason: %s", action, reason),
            auditData
        );
    }
    
    @Override
    public void auditHighRiskPattern(UUID userId, String patternType, String description, 
                                    Double riskScore, Map<String, Object> patternData) {
        log.warn("AUDIT: High-risk pattern detected - User: {}, Pattern: {}, Score: {}", 
            userId, patternType, riskScore);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("userId", userId);
        auditData.put("patternType", patternType);
        auditData.put("description", description);
        auditData.put("riskScore", riskScore);
        auditData.put("patternData", patternData);
        auditData.put("detectedAt", LocalDateTime.now());
        
        auditService.auditHighRiskOperation(
            "HIGH_RISK_PATTERN_DETECTED",
            userId.toString(),
            String.format("High-risk pattern detected - Pattern: %s, Score: %.2f", 
                patternType, riskScore),
            auditData
        );
    }
    
    @Override
    public void auditComplianceViolation(UUID userId, String violationType, String description, 
                                        String severity, String action) {
        log.error("AUDIT: Compliance violation - User: {}, Type: {}, Severity: {}", 
            userId, violationType, severity);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("userId", userId);
        auditData.put("violationType", violationType);
        auditData.put("description", description);
        auditData.put("severity", severity);
        auditData.put("action", action);
        auditData.put("detectedAt", LocalDateTime.now());
        auditData.put("securityContext", getCurrentSecurityContext());
        
        auditService.auditCriticalComplianceEvent(
            "COMPLIANCE_VIOLATION",
            userId.toString(),
            String.format("Compliance violation - Type: %s, Severity: %s", violationType, severity),
            auditData
        );
    }
    
    @Override
    public void auditCurrencyExchange(UUID userId, String fromCurrency, String toCurrency, 
                                     BigDecimal fromAmount, BigDecimal toAmount, 
                                     BigDecimal exchangeRate, String provider) {
        log.info("AUDIT: Currency exchange - User: {}, {} {} -> {} {}", 
            userId, fromAmount, fromCurrency, toAmount, toCurrency);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("userId", userId);
        auditData.put("fromCurrency", fromCurrency);
        auditData.put("toCurrency", toCurrency);
        auditData.put("fromAmount", fromAmount);
        auditData.put("toAmount", toAmount);
        auditData.put("exchangeRate", exchangeRate);
        auditData.put("provider", provider);
        auditData.put("exchangedAt", LocalDateTime.now());
        
        auditService.auditFinancialTransaction(
            "CURRENCY_EXCHANGE",
            userId.toString(),
            String.format("Currency exchange - %s %s to %s %s at rate %s", 
                fromAmount, fromCurrency, toAmount, toCurrency, exchangeRate),
            auditData
        );
    }
    
    @Override
    public void auditLimitChange(UUID userId, String limitType, BigDecimal oldLimit, 
                                BigDecimal newLimit, String reason, String authorizedBy) {
        log.info("AUDIT: Limit changed - User: {}, Type: {}, {} -> {}", 
            userId, limitType, oldLimit, newLimit);
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("userId", userId);
        auditData.put("limitType", limitType);
        auditData.put("oldLimit", oldLimit);
        auditData.put("newLimit", newLimit);
        auditData.put("changeAmount", newLimit.subtract(oldLimit));
        auditData.put("reason", reason);
        auditData.put("authorizedBy", authorizedBy);
        auditData.put("changedAt", LocalDateTime.now());
        auditData.put("securityContext", getCurrentSecurityContext());
        
        auditService.auditHighRiskOperation(
            "WALLET_LIMIT_CHANGED",
            userId.toString(),
            String.format("Wallet limit changed - Type: %s, %s to %s", 
                limitType, oldLimit, newLimit),
            auditData
        );
    }
    
    // Private helper methods
    
    private String getCurrentSecurityContext() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                return String.format("Principal: %s, Authorities: %s", 
                    auth.getName(), auth.getAuthorities());
            }
        } catch (Exception e) {
            log.debug("Error getting security context", e);
        }
        return "Anonymous";
    }
    
    private boolean isHighValueTransaction(BigDecimal amount) {
        return amount.compareTo(new BigDecimal("10000")) > 0;
    }
    
    private boolean isInternationalTransaction(Transaction transaction) {
        // This would typically check if the transaction crosses borders
        // For now, we'll use a simple heuristic based on metadata
        Map<String, Object> metadata = transaction.getMetadata();
        if (metadata != null) {
            return metadata.containsKey("sourceCountry") && 
                   metadata.containsKey("destinationCountry") &&
                   !metadata.get("sourceCountry").equals(metadata.get("destinationCountry"));
        }
        return false;
    }
    
    private Map<String, Object> identifyRiskIndicators(Transaction transaction) {
        Map<String, Object> indicators = new HashMap<>();
        
        // Round amount indicator
        if (isRoundAmount(transaction.getAmount())) {
            indicators.put("roundAmount", true);
        }
        
        // Large amount indicator
        if (isHighValueTransaction(transaction.getAmount())) {
            indicators.put("highValue", true);
        }
        
        // International indicator
        if (isInternationalTransaction(transaction)) {
            indicators.put("international", true);
        }
        
        return indicators;
    }
    
    private Map<String, Object> checkRegulatoryThresholds(Transaction transaction) {
        Map<String, Object> thresholds = new HashMap<>();
        
        // CTR threshold (Cash Transaction Report)
        if (transaction.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            thresholds.put("ctrRequired", true);
        }
        
        // SAR threshold indicators
        if (transaction.getAmount().compareTo(new BigDecimal("5000")) > 0) {
            thresholds.put("sarReview", true);
        }
        
        return thresholds;
    }
    
    private boolean isRoundAmount(BigDecimal amount) {
        return amount.remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) == 0 ||
               amount.remainder(new BigDecimal("500")).compareTo(BigDecimal.ZERO) == 0;
    }
    
    private Long calculateProcessingDuration(Transaction transaction) {
        if (transaction.getCreatedAt() != null && transaction.getCompletedAt() != null) {
            return java.time.Duration.between(transaction.getCreatedAt(), transaction.getCompletedAt()).toMillis();
        }
        
        // Transaction still in progress or missing timestamps - log for audit purposes
        if (transaction.getCreatedAt() != null && transaction.getCompletedAt() == null) {
            log.debug("TRANSACTION_AUDIT: Transaction {} is still in progress, created at: {}", 
                     transaction.getId(), transaction.getCreatedAt());
            // Return duration from creation to now for in-progress transactions
            return java.time.Duration.between(transaction.getCreatedAt(), LocalDateTime.now()).toMillis();
        }
        
        if (transaction.getCreatedAt() == null) {
            log.warn("TRANSACTION_AUDIT_WARNING: Transaction {} missing creation timestamp", transaction.getId());
        }
        
        // Return 0 instead of null to indicate unknown/invalid duration
        return 0L;
    }
    
    private void auditHighValueTransactionCompleted(Transaction transaction, 
                                                   BigDecimal balanceBefore, BigDecimal balanceAfter) {
        Map<String, Object> highValueData = new HashMap<>();
        highValueData.put("transactionId", transaction.getId());
        highValueData.put("amount", transaction.getAmount());
        highValueData.put("currency", transaction.getCurrency());
        highValueData.put("balanceBefore", balanceBefore);
        highValueData.put("balanceAfter", balanceAfter);
        highValueData.put("requiresReporting", true);
        
        auditService.auditCriticalComplianceEvent(
            "HIGH_VALUE_TRANSACTION_COMPLETED",
            transaction.getUserId().toString(),
            String.format("High-value transaction completed - Amount: %s %s", 
                transaction.getAmount(), transaction.getCurrency()),
            highValueData
        );
    }
    
    private boolean isUnusualBalanceChange(BigDecimal changeAmount) {
        return changeAmount.abs().compareTo(new BigDecimal("50000")) > 0;
    }
    
    private void auditUnusualBalanceChange(UUID walletId, UUID userId, BigDecimal changeAmount, 
                                          String reason, String transactionId) {
        Map<String, Object> unusualData = new HashMap<>();
        unusualData.put("walletId", walletId);
        unusualData.put("changeAmount", changeAmount);
        unusualData.put("reason", reason);
        unusualData.put("transactionId", transactionId);
        
        auditService.auditHighRiskOperation(
            "UNUSUAL_BALANCE_CHANGE",
            userId.toString(),
            String.format("Unusual balance change detected - Amount: %s", changeAmount),
            unusualData
        );
    }
    
    private String determineTransferType(TransferRequest request) {
        if (request.getFromUserId().equals(request.getToUserId())) {
            return "INTERNAL";
        }
        // Could add more logic for P2P, business, international, etc.
        return "P2P";
    }
    
    private boolean isHighRiskWithdrawal(WithdrawalRequest request) {
        return request.getAmount().compareTo(new BigDecimal("10000")) > 0 ||
               "CRYPTO".equals(request.getPaymentMethod()) ||
               "INTERNATIONAL_WIRE".equals(request.getPaymentMethod());
    }
    
    private double calculateWithdrawalRiskScore(WithdrawalRequest request) {
        double score = 0.0;
        
        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            score += 0.3;
        }
        
        if ("CRYPTO".equals(request.getPaymentMethod())) {
            score += 0.4;
        }
        
        if ("INTERNATIONAL_WIRE".equals(request.getPaymentMethod())) {
            score += 0.3;
        }
        
        return Math.min(score, 1.0);
    }
}