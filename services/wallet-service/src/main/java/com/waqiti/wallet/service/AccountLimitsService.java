package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.AccountLimits;
import com.waqiti.wallet.repository.AccountLimitsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Account Limits Service
 *
 * Manages account limits for wallet users including transaction limits,
 * withdrawal limits, deposit limits, and velocity controls.
 *
 * Provides comprehensive limit management with audit trail and compliance tracking.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AccountLimitsService {

    private final AccountLimitsRepository accountLimitsRepository;

    /**
     * Update daily transaction limit for a user
     */
    public void updateDailyTransactionLimit(String userId, BigDecimal newLimit,
                                           String reason, String updatedBy) {
        log.info("Updating daily transaction limit for user {}: newLimit={}, reason={}, updatedBy={}",
            userId, newLimit, reason, updatedBy);

        AccountLimits limits = getOrCreateAccountLimits(userId);
        limits.setDailyTransactionLimit(newLimit);
        limits.setLastUpdated(LocalDateTime.now());
        limits.setUpdatedBy(updatedBy);
        limits.setUpdateReason(reason);

        accountLimitsRepository.save(limits);

        log.info("Daily transaction limit updated for user {}: {}", userId, newLimit);
    }

    /**
     * Update monthly transaction limit for a user
     */
    public void updateMonthlyTransactionLimit(String userId, BigDecimal newLimit,
                                             String reason, String updatedBy) {
        log.info("Updating monthly transaction limit for user {}: newLimit={}, reason={}, updatedBy={}",
            userId, newLimit, reason, updatedBy);

        AccountLimits limits = getOrCreateAccountLimits(userId);
        limits.setMonthlyTransactionLimit(newLimit);
        limits.setLastUpdated(LocalDateTime.now());
        limits.setUpdatedBy(updatedBy);
        limits.setUpdateReason(reason);

        accountLimitsRepository.save(limits);

        log.info("Monthly transaction limit updated for user {}: {}", userId, newLimit);
    }

    /**
     * Update single transaction limit for a user
     */
    public void updateSingleTransactionLimit(String userId, BigDecimal newLimit,
                                            String reason, String updatedBy) {
        log.info("Updating single transaction limit for user {}: newLimit={}, reason={}, updatedBy={}",
            userId, newLimit, reason, updatedBy);

        AccountLimits limits = getOrCreateAccountLimits(userId);
        limits.setSingleTransactionLimit(newLimit);
        limits.setLastUpdated(LocalDateTime.now());
        limits.setUpdatedBy(updatedBy);
        limits.setUpdateReason(reason);

        accountLimitsRepository.save(limits);

        log.info("Single transaction limit updated for user {}: {}", userId, newLimit);
    }

    /**
     * Update withdrawal limit for a user
     */
    public void updateWithdrawalLimit(String userId, BigDecimal newLimit,
                                     String reason, String updatedBy) {
        log.info("Updating withdrawal limit for user {}: newLimit={}, reason={}, updatedBy={}",
            userId, newLimit, reason, updatedBy);

        AccountLimits limits = getOrCreateAccountLimits(userId);
        limits.setWithdrawalLimit(newLimit);
        limits.setLastUpdated(LocalDateTime.now());
        limits.setUpdatedBy(updatedBy);
        limits.setUpdateReason(reason);

        accountLimitsRepository.save(limits);

        log.info("Withdrawal limit updated for user {}: {}", userId, newLimit);
    }

    /**
     * Update deposit limit for a user
     */
    public void updateDepositLimit(String userId, BigDecimal newLimit,
                                  String reason, String updatedBy) {
        log.info("Updating deposit limit for user {}: newLimit={}, reason={}, updatedBy={}",
            userId, newLimit, reason, updatedBy);

        AccountLimits limits = getOrCreateAccountLimits(userId);
        limits.setDepositLimit(newLimit);
        limits.setLastUpdated(LocalDateTime.now());
        limits.setUpdatedBy(updatedBy);
        limits.setUpdateReason(reason);

        accountLimitsRepository.save(limits);

        log.info("Deposit limit updated for user {}: {}", userId, newLimit);
    }

    /**
     * Update account balance limit for a user
     */
    public void updateAccountBalanceLimit(String userId, BigDecimal newLimit,
                                         String reason, String updatedBy) {
        log.info("Updating account balance limit for user {}: newLimit={}, reason={}, updatedBy={}",
            userId, newLimit, reason, updatedBy);

        AccountLimits limits = getOrCreateAccountLimits(userId);
        limits.setAccountBalanceLimit(newLimit);
        limits.setLastUpdated(LocalDateTime.now());
        limits.setUpdatedBy(updatedBy);
        limits.setUpdateReason(reason);

        accountLimitsRepository.save(limits);

        log.info("Account balance limit updated for user {}: {}", userId, newLimit);
    }

    /**
     * Update velocity limit (transactions per time window) for a user
     */
    public void updateVelocityLimit(String userId, Integer newLimit, String timeWindow,
                                   String reason, String updatedBy) {
        log.info("Updating velocity limit for user {}: newLimit={}, timeWindow={}, reason={}, updatedBy={}",
            userId, newLimit, timeWindow, reason, updatedBy);

        AccountLimits limits = getOrCreateAccountLimits(userId);
        limits.setVelocityLimit(newLimit);
        limits.setVelocityTimeWindow(timeWindow);
        limits.setLastUpdated(LocalDateTime.now());
        limits.setUpdatedBy(updatedBy);
        limits.setUpdateReason(reason);

        accountLimitsRepository.save(limits);

        log.info("Velocity limit updated for user {}: {} transactions per {}",
            userId, newLimit, timeWindow);
    }

    /**
     * Get account limits for a user, creating default limits if not found
     */
    private AccountLimits getOrCreateAccountLimits(String userId) {
        Optional<AccountLimits> existing = accountLimitsRepository.findByUserId(userId);

        if (existing.isPresent()) {
            return existing.get();
        }

        // Create default limits
        AccountLimits limits = AccountLimits.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .dailyTransactionLimit(new BigDecimal("10000.00"))
            .monthlyTransactionLimit(new BigDecimal("50000.00"))
            .singleTransactionLimit(new BigDecimal("5000.00"))
            .withdrawalLimit(new BigDecimal("5000.00"))
            .depositLimit(new BigDecimal("10000.00"))
            .accountBalanceLimit(new BigDecimal("100000.00"))
            .velocityLimit(10)
            .velocityTimeWindow("1H")
            .createdAt(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .build();

        return accountLimitsRepository.save(limits);
    }

    /**
     * Get account limits for a user
     */
    public AccountLimits getAccountLimits(String userId) {
        return accountLimitsRepository.findByUserId(userId)
            .orElse(getOrCreateAccountLimits(userId));
    }
}
