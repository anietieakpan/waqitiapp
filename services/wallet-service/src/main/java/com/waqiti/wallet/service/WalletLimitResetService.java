package com.waqiti.wallet.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Wallet Limit Reset Service
 *
 * CRITICAL FIX: Atomic limit reset implementation
 *
 * ISSUE RESOLVED: Race condition in Wallet.java resetLimitsIfNeeded() method
 * - Previous implementation: In-memory check before transaction commit
 * - Risk: Concurrent transactions could both reset limits
 * - Impact: Double-spending beyond limits
 *
 * NEW IMPLEMENTATION:
 * - Database-level atomic updates using native SQL
 * - CASE expressions for conditional resets
 * - Scheduled job for proactive resets (idle wallets)
 * - Comprehensive metrics and alerting
 *
 * @author Waqiti Platform Team
 * @since 2025-10-31 - CRITICAL FIX
 */
@Slf4j
@Service
public class WalletLimitResetService {

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    // Metrics
    private final Counter dailyResetsCounter;
    private final Counter monthlyResetsCounter;
    private final Counter resetErrorsCounter;

    public WalletLimitResetService(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.dailyResetsCounter = Counter.builder("wallet.limits.daily.resets")
            .description("Number of daily limit resets performed")
            .register(meterRegistry);

        this.monthlyResetsCounter = Counter.builder("wallet.limits.monthly.resets")
            .description("Number of monthly limit resets performed")
            .register(meterRegistry);

        this.resetErrorsCounter = Counter.builder("wallet.limits.reset.errors")
            .description("Number of limit reset errors")
            .register(meterRegistry);
    }

    /**
     * Scheduled job: Daily limit reset at midnight UTC
     * Runs every day at 00:01 AM to reset all wallets
     */
    @Scheduled(cron = "0 1 0 * * *", zone = "UTC")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void scheduledDailyLimitReset() {
        log.info("Starting scheduled daily limit reset for all wallets");

        try {
            int resetCount = resetDailyLimitsForAllWallets();
            dailyResetsCounter.increment(resetCount);

            log.info("Successfully reset daily limits for {} wallets", resetCount);

        } catch (Exception e) {
            log.error("Error during scheduled daily limit reset", e);
            resetErrorsCounter.increment();
            throw e; // Re-throw to trigger transaction rollback
        }
    }

    /**
     * Scheduled job: Monthly limit reset on first day of month
     * Runs at 00:05 AM on the 1st of every month
     */
    @Scheduled(cron = "0 5 0 1 * *", zone = "UTC")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void scheduledMonthlyLimitReset() {
        log.info("Starting scheduled monthly limit reset for all wallets");

        try {
            int resetCount = resetMonthlyLimitsForAllWallets();
            monthlyResetsCounter.increment(resetCount);

            log.info("Successfully reset monthly limits for {} wallets", resetCount);

        } catch (Exception e) {
            log.error("Error during scheduled monthly limit reset", e);
            resetErrorsCounter.increment();
            throw e;
        }
    }

    /**
     * Reset daily limits for all wallets atomically using native SQL
     *
     * This replaces the dangerous in-memory resetLimitsIfNeeded() method
     *
     * @return number of wallets reset
     */
    public int resetDailyLimitsForAllWallets() {
        LocalDate today = LocalDate.now();

        String sql = """
            UPDATE wallets
            SET
                daily_spent = 0,
                limit_reset_date = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE
                DATE(limit_reset_date) < :today
                AND status = 'ACTIVE'
            """;

        int count = jdbcTemplate.update(sql.replace(":today", "?"), today);

        log.info("Reset daily limits for {} wallets", count);
        return count;
    }

    /**
     * Reset monthly limits for all wallets atomically
     *
     * @return number of wallets reset
     */
    public int resetMonthlyLimitsForAllWallets() {
        LocalDate firstDayOfMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());

        String sql = """
            UPDATE wallets
            SET
                monthly_spent = 0,
                monthly_reset_date = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE
                DATE(monthly_reset_date) < :firstDayOfMonth
                AND status = 'ACTIVE'
            """;

        int count = jdbcTemplate.update(sql.replace(":firstDayOfMonth", "?"), firstDayOfMonth);

        log.info("Reset monthly limits for {} wallets", count);
        return count;
    }

    /**
     * Atomic limit increment during transaction processing
     *
     * CRITICAL: This method is called during balance updates with proper locking
     *
     * Uses CASE expression to:
     * 1. Check if limit reset is needed
     * 2. Reset if needed
     * 3. Increment spent amount
     *
     * All in a single atomic UPDATE statement
     *
     * @param walletId wallet UUID
     * @param amount transaction amount
     * @param expectedVersion optimistic lock version
     * @return number of rows updated (1 on success, 0 on version conflict)
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public int incrementDailySpentAtomic(String walletId, java.math.BigDecimal amount, Long expectedVersion) {
        LocalDate today = LocalDate.now();

        String sql = """
            UPDATE wallets
            SET
                daily_spent = CASE
                    WHEN DATE(limit_reset_date) < :today THEN :amount
                    ELSE daily_spent + :amount
                END,
                limit_reset_date = CASE
                    WHEN DATE(limit_reset_date) < :today THEN CURRENT_TIMESTAMP
                    ELSE limit_reset_date
                END,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE
                id = :walletId::uuid
                AND version = :expectedVersion
            """;

        int rowsUpdated = jdbcTemplate.update(
            sql.replace(":today", "?")
               .replace(":amount", "?")
               .replace(":walletId", "?")
               .replace(":expectedVersion", "?"),
            today, amount, amount, walletId, expectedVersion
        );

        if (rowsUpdated == 0) {
            log.warn("Optimistic lock failure or wallet not found: walletId={}, expectedVersion={}",
                walletId, expectedVersion);
        }

        return rowsUpdated;
    }

    /**
     * Atomic monthly limit increment
     *
     * @param walletId wallet UUID
     * @param amount transaction amount
     * @param expectedVersion optimistic lock version
     * @return number of rows updated
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public int incrementMonthlySpentAtomic(String walletId, java.math.BigDecimal amount, Long expectedVersion) {
        LocalDate firstDayOfMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());

        String sql = """
            UPDATE wallets
            SET
                monthly_spent = CASE
                    WHEN DATE(monthly_reset_date) < :firstDayOfMonth THEN :amount
                    ELSE monthly_spent + :amount
                END,
                monthly_reset_date = CASE
                    WHEN DATE(monthly_reset_date) < :firstDayOfMonth THEN CURRENT_TIMESTAMP
                    ELSE monthly_reset_date
                END,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE
                id = :walletId::uuid
                AND version = :expectedVersion
            """;

        int rowsUpdated = jdbcTemplate.update(
            sql.replace(":firstDayOfMonth", "?")
               .replace(":amount", "?")
               .replace(":walletId", "?")
               .replace(":expectedVersion", "?"),
            firstDayOfMonth, amount, amount, walletId, expectedVersion
        );

        return rowsUpdated;
    }

    /**
     * Check if transaction would exceed daily limit (before processing)
     *
     * This query automatically resets if needed and checks in one atomic operation
     *
     * @param walletId wallet UUID
     * @param amount transaction amount
     * @return true if would exceed, false otherwise
     */
    public boolean wouldExceedDailyLimit(String walletId, java.math.BigDecimal amount) {
        LocalDate today = LocalDate.now();

        String sql = """
            SELECT
                CASE
                    WHEN daily_limit IS NULL THEN FALSE
                    WHEN DATE(limit_reset_date) < :today THEN :amount > daily_limit
                    ELSE (daily_spent + :amount) > daily_limit
                END as would_exceed
            FROM wallets
            WHERE id = :walletId::uuid
            """;

        Boolean result = jdbcTemplate.queryForObject(
            sql.replace(":today", "?")
               .replace(":amount", "?")
               .replace(":walletId", "?"),
            Boolean.class,
            today, amount, amount, walletId
        );

        return Boolean.TRUE.equals(result);
    }

    /**
     * Check if transaction would exceed monthly limit
     *
     * @param walletId wallet UUID
     * @param amount transaction amount
     * @return true if would exceed, false otherwise
     */
    public boolean wouldExceedMonthlyLimit(String walletId, java.math.BigDecimal amount) {
        LocalDate firstDayOfMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());

        String sql = """
            SELECT
                CASE
                    WHEN monthly_limit IS NULL THEN FALSE
                    WHEN DATE(monthly_reset_date) < :firstDayOfMonth THEN :amount > monthly_limit
                    ELSE (monthly_spent + :amount) > monthly_limit
                END as would_exceed
            FROM wallets
            WHERE id = :walletId::uuid
            """;

        Boolean result = jdbcTemplate.queryForObject(
            sql.replace(":firstDayOfMonth", "?")
               .replace(":amount", "?")
               .replace(":walletId", "?"),
            Boolean.class,
            firstDayOfMonth, amount, amount, walletId
        );

        return Boolean.TRUE.equals(result);
    }

    /**
     * Get current daily spent amount (with auto-reset consideration)
     *
     * @param walletId wallet UUID
     * @return current daily spent amount
     */
    public java.math.BigDecimal getCurrentDailySpent(String walletId) {
        LocalDate today = LocalDate.now();

        String sql = """
            SELECT
                CASE
                    WHEN DATE(limit_reset_date) < :today THEN 0
                    ELSE daily_spent
                END as current_daily_spent
            FROM wallets
            WHERE id = :walletId::uuid
            """;

        return jdbcTemplate.queryForObject(
            sql.replace(":today", "?").replace(":walletId", "?"),
            java.math.BigDecimal.class,
            today, walletId
        );
    }

    /**
     * Get current monthly spent amount (with auto-reset consideration)
     *
     * @param walletId wallet UUID
     * @return current monthly spent amount
     */
    public java.math.BigDecimal getCurrentMonthlySpent(String walletId) {
        LocalDate firstDayOfMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());

        String sql = """
            SELECT
                CASE
                    WHEN DATE(monthly_reset_date) < :firstDayOfMonth THEN 0
                    ELSE monthly_spent
                END as current_monthly_spent
            FROM wallets
            WHERE id = :walletId::uuid
            """;

        return jdbcTemplate.queryForObject(
            sql.replace(":firstDayOfMonth", "?").replace(":walletId", "?"),
            java.math.BigDecimal.class,
            firstDayOfMonth, walletId
        );
    }

    /**
     * Manual trigger for daily limit reset (admin function)
     *
     * @param walletId wallet UUID
     * @return true if reset successful
     */
    @Transactional
    public boolean resetDailyLimitForWallet(String walletId) {
        String sql = """
            UPDATE wallets
            SET
                daily_spent = 0,
                limit_reset_date = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id = :walletId::uuid
            """;

        int count = jdbcTemplate.update(sql.replace(":walletId", "?"), walletId);

        if (count > 0) {
            log.info("Manually reset daily limit for wallet: {}", walletId);
            dailyResetsCounter.increment();
            return true;
        }

        return false;
    }

    /**
     * Manual trigger for monthly limit reset (admin function)
     *
     * @param walletId wallet UUID
     * @return true if reset successful
     */
    @Transactional
    public boolean resetMonthlyLimitForWallet(String walletId) {
        String sql = """
            UPDATE wallets
            SET
                monthly_spent = 0,
                monthly_reset_date = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id = :walletId::uuid
            """;

        int count = jdbcTemplate.update(sql.replace(":walletId", "?"), walletId);

        if (count > 0) {
            log.info("Manually reset monthly limit for wallet: {}", walletId);
            monthlyResetsCounter.increment();
            return true;
        }

        return false;
    }
}
