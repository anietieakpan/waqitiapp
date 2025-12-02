package com.waqiti.reconciliation.service;

import com.waqiti.reconciliation.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Account Cache Service
 *
 * Provides Redis-based caching for account data to support fallback scenarios
 * when account-service is unavailable.
 *
 * CACHE STRATEGY:
 * - Account balances → 5 minute TTL (balance changes frequently)
 * - Account details → 30 minute TTL (details change rarely)
 * - Account lists → 10 minute TTL (moderate change frequency)
 *
 * CACHE KEYS:
 * - account:balance:{accountId}:{asOfDate}
 * - account:details:{accountId}
 * - account:list:customer:active
 * - account:list:system
 *
 * @author Waqiti Reconciliation Team
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration BALANCE_TTL = Duration.ofMinutes(5);
    private static final Duration DETAILS_TTL = Duration.ofMinutes(30);
    private static final Duration LIST_TTL = Duration.ofMinutes(10);

    /**
     * Get cached account balance
     */
    public AccountBalance getCachedBalance(UUID accountId, LocalDateTime asOfDate) {
        try {
            String key = String.format("account:balance:%s:%s", accountId, asOfDate);
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached instanceof AccountBalance) {
                log.debug("Cache HIT for account balance: accountId={}", accountId);
                return (AccountBalance) cached;
            }

            log.debug("Cache MISS for account balance: accountId={}", accountId);
            return null;

        } catch (Exception e) {
            log.error("Failed to get cached balance for accountId={}: {}", accountId, e.getMessage());
            return null;
        }
    }

    /**
     * Cache account balance
     */
    public void cacheBalance(UUID accountId, LocalDateTime asOfDate, AccountBalance balance) {
        try {
            String key = String.format("account:balance:%s:%s", accountId, asOfDate);
            redisTemplate.opsForValue().set(key, balance, BALANCE_TTL);
            log.debug("Cached account balance: accountId={}", accountId);

        } catch (Exception e) {
            log.error("Failed to cache balance for accountId={}: {}", accountId, e.getMessage());
            // Don't propagate - caching is best-effort
        }
    }

    /**
     * Get cached customer accounts
     */
    @SuppressWarnings("unchecked")
    public List<CustomerAccount> getCachedCustomerAccounts() {
        try {
            String key = "account:list:customer:active";
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached instanceof List) {
                log.debug("Cache HIT for customer accounts");
                return (List<CustomerAccount>) cached;
            }

            log.debug("Cache MISS for customer accounts");
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("Failed to get cached customer accounts: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Cache customer accounts
     */
    public void cacheCustomerAccounts(List<CustomerAccount> accounts) {
        try {
            String key = "account:list:customer:active";
            redisTemplate.opsForValue().set(key, accounts, LIST_TTL);
            log.debug("Cached {} customer accounts", accounts.size());

        } catch (Exception e) {
            log.error("Failed to cache customer accounts: {}", e.getMessage());
        }
    }

    /**
     * Get cached system accounts
     */
    @SuppressWarnings("unchecked")
    public List<SystemAccount> getCachedSystemAccounts() {
        try {
            String key = "account:list:system";
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached instanceof List) {
                log.debug("Cache HIT for system accounts");
                return (List<SystemAccount>) cached;
            }

            log.debug("Cache MISS for system accounts");
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("Failed to get cached system accounts: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Cache system accounts
     */
    public void cacheSystemAccounts(List<SystemAccount> accounts) {
        try {
            String key = "account:list:system";
            redisTemplate.opsForValue().set(key, accounts, LIST_TTL);
            log.debug("Cached {} system accounts", accounts.size());

        } catch (Exception e) {
            log.error("Failed to cache system accounts: {}", e.getMessage());
        }
    }

    /**
     * Get cached account details
     */
    public AccountDetails getCachedAccountDetails(UUID accountId) {
        try {
            String key = String.format("account:details:%s", accountId);
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached instanceof AccountDetails) {
                log.debug("Cache HIT for account details: accountId={}", accountId);
                return (AccountDetails) cached;
            }

            log.debug("Cache MISS for account details: accountId={}", accountId);
            return null;

        } catch (Exception e) {
            log.error("Failed to get cached account details for accountId={}: {}", accountId, e.getMessage());
            return null;
        }
    }

    /**
     * Cache account details
     */
    public void cacheAccountDetails(UUID accountId, AccountDetails details) {
        try {
            String key = String.format("account:details:%s", accountId);
            redisTemplate.opsForValue().set(key, details, DETAILS_TTL);
            log.debug("Cached account details: accountId={}", accountId);

        } catch (Exception e) {
            log.error("Failed to cache account details for accountId={}: {}", accountId, e.getMessage());
        }
    }

    /**
     * Invalidate account cache
     */
    public void invalidateAccountCache(UUID accountId) {
        try {
            String balancePattern = String.format("account:balance:%s:*", accountId);
            String detailsKey = String.format("account:details:%s", accountId);

            redisTemplate.delete(detailsKey);
            // Note: Pattern deletion would require scanning - omitted for performance
            log.debug("Invalidated cache for accountId={}", accountId);

        } catch (Exception e) {
            log.error("Failed to invalidate cache for accountId={}: {}", accountId, e.getMessage());
        }
    }
}
