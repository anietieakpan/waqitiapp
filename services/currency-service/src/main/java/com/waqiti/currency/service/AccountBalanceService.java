package com.waqiti.currency.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Account Balance Service
 *
 * Manages account balance updates for currency conversions.
 * Handles:
 * - Balance debits for conversion source currency
 * - Balance credits for conversion target currency
 * - Balance reconciliation
 * - Transaction history tracking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountBalanceService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AccountBalance> balanceCache = new ConcurrentHashMap<>();

    /**
     * Update account balance for currency conversion
     */
    @Transactional
    public void updateBalanceForConversion(String accountId, String fromCurrency, String toCurrency,
                                          BigDecimal originalAmount, BigDecimal convertedAmount,
                                          String correlationId) {

        log.info("Updating account balance for conversion: accountId={} debit={} {} credit={} {} correlationId={}",
                accountId, originalAmount, fromCurrency, convertedAmount, toCurrency, correlationId);

        try {
            // Debit source currency
            debitBalance(accountId, fromCurrency, originalAmount, correlationId);

            // Credit target currency
            creditBalance(accountId, toCurrency, convertedAmount, correlationId);

            // Record balance update transaction
            recordBalanceUpdate(accountId, fromCurrency, toCurrency, originalAmount, convertedAmount, correlationId);

            Counter.builder("account.balance.conversion_update_success")
                    .tag("fromCurrency", fromCurrency)
                    .tag("toCurrency", toCurrency)
                    .register(meterRegistry)
                    .increment();

            log.info("Account balance updated successfully: accountId={} correlationId={}", accountId, correlationId);

        } catch (Exception e) {
            log.error("Failed to update account balance: accountId={} correlationId={}", accountId, correlationId, e);

            Counter.builder("account.balance.conversion_update_error")
                    .tag("fromCurrency", fromCurrency)
                    .tag("toCurrency", toCurrency)
                    .register(meterRegistry)
                    .increment();

            throw new RuntimeException("Balance update failed", e);
        }
    }

    /**
     * Debit balance from account
     */
    private void debitBalance(String accountId, String currency, BigDecimal amount, String correlationId) {
        log.debug("Debiting balance: accountId={} amount={} {} correlationId={}",
                accountId, amount, currency, correlationId);

        // In production: Update database with pessimistic locking
        String balanceKey = accountId + ":" + currency;
        AccountBalance balance = balanceCache.computeIfAbsent(balanceKey,
                k -> new AccountBalance(accountId, currency, BigDecimal.ZERO));

        balance.debit(amount);

        Counter.builder("account.balance.debit")
                .tag("currency", currency)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Credit balance to account
     */
    private void creditBalance(String accountId, String currency, BigDecimal amount, String correlationId) {
        log.debug("Crediting balance: accountId={} amount={} {} correlationId={}",
                accountId, amount, currency, correlationId);

        // In production: Update database with pessimistic locking
        String balanceKey = accountId + ":" + currency;
        AccountBalance balance = balanceCache.computeIfAbsent(balanceKey,
                k -> new AccountBalance(accountId, currency, BigDecimal.ZERO));

        balance.credit(amount);

        Counter.builder("account.balance.credit")
                .tag("currency", currency)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record balance update transaction
     */
    private void recordBalanceUpdate(String accountId, String fromCurrency, String toCurrency,
                                     BigDecimal debitAmount, BigDecimal creditAmount, String correlationId) {
        log.debug("Recording balance update: accountId={} {}/{} correlationId={}",
                accountId, fromCurrency, toCurrency, correlationId);

        // In production: Persist transaction record to database
        // This creates an audit trail for all balance changes
    }

    /**
     * Get account balance
     */
    public BigDecimal getBalance(String accountId, String currency) {
        String balanceKey = accountId + ":" + currency;
        AccountBalance balance = balanceCache.get(balanceKey);
        return balance != null ? balance.getAmount() : BigDecimal.ZERO;
    }

    /**
     * Account Balance internal model
     */
    private static class AccountBalance {
        private final String accountId;
        private final String currency;
        private BigDecimal amount;
        private Instant lastUpdated;

        public AccountBalance(String accountId, String currency, BigDecimal initialAmount) {
            this.accountId = accountId;
            this.currency = currency;
            this.amount = initialAmount;
            this.lastUpdated = Instant.now();
        }

        public synchronized void debit(BigDecimal amount) {
            this.amount = this.amount.subtract(amount);
            this.lastUpdated = Instant.now();
        }

        public synchronized void credit(BigDecimal amount) {
            this.amount = this.amount.add(amount);
            this.lastUpdated = Instant.now();
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }
}
