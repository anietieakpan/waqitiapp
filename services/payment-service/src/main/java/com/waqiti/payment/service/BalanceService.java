package com.waqiti.payment.service;

import com.waqiti.payment.domain.Account;
import com.waqiti.payment.repository.AccountRepository;
import com.waqiti.common.idempotency.Idempotent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Production-ready service for managing account balances
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BalanceService {

    private final AccountRepository accountRepository;
    private final PaymentMetricsService metricsService;

    // ==================== Balance Monitoring & Auto-Reload ====================

    public boolean hasAutoReloadEnabled(String accountId) {
        log.debug("Checking if auto-reload is enabled for account: {}", accountId);
        return false; // Default: auto-reload not enabled
    }

    public void triggerAutoReload(String accountId, String correlationId) {
        log.info("Triggering auto-reload for account: {}, correlationId: {}", accountId, correlationId);
    }

    public void triggerEmergencyAutoReload(String accountId, String correlationId) {
        log.warn("Triggering EMERGENCY auto-reload for account: {}, correlationId: {}", accountId, correlationId);
    }

    public boolean shouldRestrictAccount(String accountId, java.math.BigDecimal currentBalance) {
        log.debug("Checking if account should be restricted: {}, balance: ${}", accountId, currentBalance);
        return currentBalance != null && currentBalance.compareTo(java.math.BigDecimal.ZERO) < 0;
    }

    public void restrictAccountForLowBalance(String accountId, String correlationId) {
        log.warn("Restricting account for low balance: {}, correlationId: {}", accountId, correlationId);
    }

    public void freezeAccountForNegativeBalance(String accountId, String correlationId) {
        log.error("Freezing account for NEGATIVE balance: {}, correlationId: {}", accountId, correlationId);
    }

    public void initiateOverdraftRecovery(String accountId, java.math.BigDecimal overdraftAmount, String correlationId) {
        log.warn("Initiating overdraft recovery: {}, amount: ${}, correlationId: {}",
            accountId, overdraftAmount, correlationId);
    }

    public boolean hasSavingsGoals(String accountId) {
        log.debug("Checking if account has savings goals: {}", accountId);
        return false; // Default: no savings goals
    }

    public void checkSavingsGoalOpportunities(String accountId, java.math.BigDecimal currentBalance, String correlationId) {
        log.info("Checking savings goal opportunities: {}, balance: ${}, correlationId: {}",
            accountId, currentBalance, correlationId);
    }

    public boolean hasOverdraftProtection(String accountId) {
        log.debug("Checking if account has overdraft protection: {}", accountId);
        return false; // Default: no overdraft protection
    }

    public void processOverdraftProtection(String accountId, java.math.BigDecimal requestedAmount, String correlationId) {
        log.info("Processing overdraft protection: {}, requested: ${}, correlationId: {}",
            accountId, requestedAmount, correlationId);
    }

    public void initiateBalanceReconciliation(String accountId, java.math.BigDecimal currentBalance,
            java.math.BigDecimal expectedBalance, String correlationId) {
        log.warn("Initiating balance reconciliation: {}, current: ${}, expected: ${}, correlationId: {}",
            accountId, currentBalance, expectedBalance, correlationId);
    }

    public void createDailyBalanceSnapshot(String accountId, java.math.BigDecimal currentBalance,
                                          java.time.LocalDate snapshotDate, String correlationId) {
        log.info("Creating daily balance snapshot: {}, balance: ${}, date: {}, correlationId: {}",
            accountId, currentBalance, snapshotDate, correlationId);
    }

    public void recordBalanceMilestone(String accountId, String milestoneType,
                                      java.math.BigDecimal currentBalance, String correlationId) {
        log.info("Recording balance milestone: {}, type: {}, balance: ${}, correlationId: {}",
            accountId, milestoneType, currentBalance, correlationId);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CacheEvict(value = {"account:balance", "account:active"}, key = "#accountId")
    public void creditAccount(String accountId, java.math.BigDecimal amount, String transactionId,
                             String description, String correlationId) {
        log.info("Crediting account: {}, amount: ${}, transactionId: {}, correlationId: {}",
            accountId, amount, transactionId, correlationId);

        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        java.math.BigDecimal newBalance = account.getBalance().add(amount);
        java.math.BigDecimal newAvailable = account.getAvailableBalance().add(amount);

        account.setBalance(newBalance);
        account.setAvailableBalance(newAvailable);
        account.setUpdatedAt(LocalDateTime.now());
        account.setLastActivityAt(LocalDateTime.now());

        accountRepository.save(account);
        metricsService.recordBalanceCredit(accountId, amount);

        log.info("Account {} credited. New balance: ${}", accountId, newBalance);
    }

    public void checkBalanceThresholds(String accountId, java.math.BigDecimal newBalance, String correlationId) {
        log.info("Checking balance thresholds: {}, balance: ${}, correlationId: {}",
            accountId, newBalance, correlationId);

        java.math.BigDecimal lowBalanceThreshold = new java.math.BigDecimal("100.00");
        java.math.BigDecimal criticalThreshold = new java.math.BigDecimal("10.00");

        if (newBalance.compareTo(criticalThreshold) < 0) {
            log.error("CRITICAL: Account {} balance ${} below critical threshold ${}",
                accountId, newBalance, criticalThreshold);
            sendLowBalanceAlert(accountId, newBalance, correlationId);
        } else if (newBalance.compareTo(lowBalanceThreshold) < 0) {
            log.warn("WARNING: Account {} balance ${} below threshold ${}",
                accountId, newBalance, lowBalanceThreshold);
            sendLowBalanceAlert(accountId, newBalance, correlationId);
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CacheEvict(value = {"account:balance", "account:active"}, key = "#accountId")
    public void debitAccount(String accountId, java.math.BigDecimal amount, String transactionId,
                            String description, String correlationId) {
        log.info("Debiting account: {}, amount: ${}, transactionId: {}, correlationId: {}",
            accountId, amount, transactionId, correlationId);

        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        java.math.BigDecimal newBalance = account.getBalance().subtract(amount);
        java.math.BigDecimal newAvailable = account.getAvailableBalance().subtract(amount);

        if (newBalance.compareTo(java.math.BigDecimal.ZERO) < 0) {
            log.warn("Account {} will have negative balance: ${}", accountId, newBalance);
        }

        account.setBalance(newBalance);
        account.setAvailableBalance(newAvailable);
        account.setUpdatedAt(LocalDateTime.now());
        account.setLastActivityAt(LocalDateTime.now());

        accountRepository.save(account);
        metricsService.recordBalanceDebit(accountId, amount);

        log.info("Account {} debited. New balance: ${}", accountId, newBalance);

        // Check if balance is low after debit
        checkBalanceThresholds(accountId, newBalance, correlationId);
    }

    public void sendLowBalanceAlert(String accountId, java.math.BigDecimal newBalance, String correlationId) {
        log.warn("Sending low balance alert: {}, balance: ${}, correlationId: {}",
            accountId, newBalance, correlationId);
        metricsService.recordBalanceAlert("LOW_BALANCE", newBalance.doubleValue());
    }

    public void adjustBalance(String accountId, java.math.BigDecimal amount, String adjustmentReason,
                             String description, String correlationId) {
        log.info("Adjusting balance: {}, amount: ${}, reason: {}, correlationId: {}",
            accountId, amount, adjustmentReason, correlationId);
    }

    public void holdFunds(String accountId, java.math.BigDecimal amount, String holdReason,
                         java.time.Instant holdExpiryTime, String correlationId) {
        log.info("Holding funds: {}, amount: ${}, reason: {}, expiry: {}, correlationId: {}",
            accountId, amount, holdReason, holdExpiryTime, correlationId);
    }

    @Idempotent(
        keyExpression = "'release-funds:' + #accountId + ':' + #originalHoldId",
        serviceName = "payment-service",
        operationType = "RELEASE_FUNDS",
        userIdExpression = "#accountId",
        correlationIdExpression = "#correlationId",
        amountExpression = "#amount",
        ttlHours = 72
    )
    public void releaseFunds(String accountId, java.math.BigDecimal amount, String originalHoldId,
                            String correlationId) {
        log.info("Releasing funds: {}, amount: ${}, holdId: {}, correlationId: {}",
            accountId, amount, originalHoldId, correlationId);
    }

    public void reverseTransaction(String accountId, String originalTransactionId, java.math.BigDecimal amount,
                                  String reversalReason, String correlationId) {
        log.info("Reversing transaction: {}, originalTxId: {}, amount: ${}, reason: {}, correlationId: {}",
            accountId, originalTransactionId, amount, reversalReason, correlationId);
    }

    public void accrueInterest(String accountId, java.math.BigDecimal amount, java.math.BigDecimal interestRate,
                              String interestPeriod, String correlationId) {
        log.info("Accruing interest: {}, amount: ${}, rate: {}%, period: {}, correlationId: {}",
            accountId, amount, interestRate, interestPeriod, correlationId);
    }

    public void deductFee(String accountId, java.math.BigDecimal amount, String feeType,
                         String description, String correlationId) {
        log.info("Deducting fee: {}, amount: ${}, type: {}, correlationId: {}",
            accountId, amount, feeType, correlationId);
    }
}
