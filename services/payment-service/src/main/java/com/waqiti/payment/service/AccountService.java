package com.waqiti.payment.service;

import com.waqiti.payment.domain.Account;
import com.waqiti.payment.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Production-ready service for managing account operations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Cacheable(value = "account:overdraft", key = "#accountId")
    public boolean hasOverdraftProtection(String accountId) {
        log.debug("Checking overdraft protection for account: {}", accountId);
        return accountRepository.findById(accountId)
            .map(account -> "PREMIUM".equals(account.getAccountCategory()) ||
                           "BUSINESS".equals(account.getAccountType()))
            .orElse(false);
    }

    @Cacheable(value = "account:active", key = "#accountId", unless = "#result == false")
    public boolean isAccountActive(String accountId) {
        log.debug("Checking if account is active: {}", accountId);
        return accountRepository.findById(accountId)
            .map(account -> account.isActive() &&
                           !account.isFrozen() &&
                           !account.isRestricted())
            .orElse(false);
    }

    @Cacheable(value = "account:balance", key = "#accountId")
    public BigDecimal getAvailableBalance(String accountId) {
        log.debug("Getting available balance for account: {}", accountId);
        return accountRepository.findById(accountId)
            .map(Account::getAvailableBalance)
            .orElse(BigDecimal.ZERO);
    }

    @Cacheable(value = "account:currency", key = "#accountId")
    public String getAccountCurrency(String accountId) {
        log.debug("Getting currency for account: {}", accountId);
        return accountRepository.findById(accountId)
            .map(account -> account.getCurrency() != null ? account.getCurrency() : "USD")
            .orElse("USD");
    }

    @Transactional
    public void updateBalanceAfterTransfer(String sourceAccountId, String destinationAccountId,
                                          BigDecimal amount) {
        log.info("Updating balances after transfer: from {} to {}, amount ${}",
            sourceAccountId, destinationAccountId, amount);

        // Update source account
        accountRepository.findById(sourceAccountId).ifPresent(sourceAccount -> {
            BigDecimal newBalance = sourceAccount.getBalance().subtract(amount);
            BigDecimal newAvailableBalance = sourceAccount.getAvailableBalance().subtract(amount);

            sourceAccount.setBalance(newBalance);
            sourceAccount.setAvailableBalance(newAvailableBalance);
            sourceAccount.setUpdatedAt(LocalDateTime.now());
            sourceAccount.setLastActivityAt(LocalDateTime.now());

            accountRepository.save(sourceAccount);
            log.debug("Updated source account {} balance to {}", sourceAccountId, newBalance);
        });

        // Update destination account
        accountRepository.findById(destinationAccountId).ifPresent(destAccount -> {
            BigDecimal newBalance = destAccount.getBalance().add(amount);
            BigDecimal newAvailableBalance = destAccount.getAvailableBalance().add(amount);

            destAccount.setBalance(newBalance);
            destAccount.setAvailableBalance(newAvailableBalance);
            destAccount.setUpdatedAt(LocalDateTime.now());
            destAccount.setLastActivityAt(LocalDateTime.now());

            accountRepository.save(destAccount);
            log.debug("Updated destination account {} balance to {}", destinationAccountId, newBalance);
        });
    }
}
