package com.waqiti.common.constants;

import java.time.Duration;

/**
 * Cache Keys and TTL Constants
 *
 * Centralized cache key definitions and Time-To-Live configurations
 * for consistent caching across the platform.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-17
 */
public final class CacheKeys {

    private CacheKeys() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== Cache Names ==========

    public static final String CACHE_USER = "user";
    public static final String CACHE_WALLET = "wallet";
    public static final String CACHE_ACCOUNT = "account";
    public static final String CACHE_TRANSACTION = "transaction";
    public static final String CACHE_BALANCE = "balance";
    public static final String CACHE_RATE_LIMIT = "rateLimit";
    public static final String CACHE_SESSION = "session";
    public static final String CACHE_TOKEN = "token";
    public static final String CACHE_CONFIGURATION = "configuration";
    public static final String CACHE_EXCHANGE_RATE = "exchangeRate";
    public static final String CACHE_COMPLIANCE = "compliance";
    public static final String CACHE_FRAUD_SCORE = "fraudScore";

    // ========== Key Prefixes ==========

    public static final String PREFIX_USER = "user:";
    public static final String PREFIX_WALLET = "wallet:";
    public static final String PREFIX_ACCOUNT = "account:";
    public static final String PREFIX_TRANSACTION = "txn:";
    public static final String PREFIX_BALANCE = "balance:";
    public static final String PREFIX_RATE_LIMIT = "ratelimit:";
    public static final String PREFIX_SESSION = "session:";
    public static final String PREFIX_TOKEN = "token:";
    public static final String PREFIX_IDEMPOTENCY = "idempotency:";
    public static final String PREFIX_LOCK = "lock:";

    // ========== TTL Durations ==========

    /**
     * Short TTL: 5 minutes - For frequently changing data
     */
    public static final Duration TTL_SHORT = Duration.ofMinutes(5);

    /**
     * Medium TTL: 15 minutes - For moderately stable data
     */
    public static final Duration TTL_MEDIUM = Duration.ofMinutes(15);

    /**
     * Long TTL: 1 hour - For stable data
     */
    public static final Duration TTL_LONG = Duration.ofHours(1);

    /**
     * Extra Long TTL: 24 hours - For rarely changing data
     */
    public static final Duration TTL_EXTRA_LONG = Duration.ofHours(24);

    /**
     * Session TTL: 30 minutes - For user sessions
     */
    public static final Duration TTL_SESSION = Duration.ofMinutes(30);

    /**
     * Token TTL: 15 minutes - For access tokens
     */
    public static final Duration TTL_TOKEN = Duration.ofMinutes(15);

    /**
     * Idempotency TTL: 24 hours - For idempotency keys
     */
    public static final Duration TTL_IDEMPOTENCY = Duration.ofHours(24);

    /**
     * Rate limit TTL: 1 minute - For rate limiting windows
     */
    public static final Duration TTL_RATE_LIMIT = Duration.ofMinutes(1);

    /**
     * Lock TTL: 30 seconds - For distributed locks
     */
    public static final Duration TTL_LOCK = Duration.ofSeconds(30);

    // ========== Key Builders ==========

    /**
     * Builds user cache key
     */
    public static String userKey(String userId) {
        return PREFIX_USER + userId;
    }

    /**
     * Builds wallet cache key
     */
    public static String walletKey(String walletId) {
        return PREFIX_WALLET + walletId;
    }

    /**
     * Builds account cache key
     */
    public static String accountKey(String accountId) {
        return PREFIX_ACCOUNT + accountId;
    }

    /**
     * Builds transaction cache key
     */
    public static String transactionKey(String transactionId) {
        return PREFIX_TRANSACTION + transactionId;
    }

    /**
     * Builds balance cache key
     */
    public static String balanceKey(String walletId) {
        return PREFIX_BALANCE + walletId;
    }

    /**
     * Builds rate limit cache key
     */
    public static String rateLimitKey(String identifier) {
        return PREFIX_RATE_LIMIT + identifier;
    }

    /**
     * Builds session cache key
     */
    public static String sessionKey(String sessionId) {
        return PREFIX_SESSION + sessionId;
    }

    /**
     * Builds token cache key
     */
    public static String tokenKey(String token) {
        return PREFIX_TOKEN + token;
    }

    /**
     * Builds idempotency cache key
     */
    public static String idempotencyKey(String key) {
        return PREFIX_IDEMPOTENCY + key;
    }

    /**
     * Builds distributed lock cache key
     */
    public static String lockKey(String resource) {
        return PREFIX_LOCK + resource;
    }

    /**
     * Builds user wallet list cache key
     */
    public static String userWalletsKey(String userId) {
        return PREFIX_USER + userId + ":wallets";
    }

    /**
     * Builds user transaction history cache key
     */
    public static String userTransactionsKey(String userId, int page) {
        return PREFIX_USER + userId + ":transactions:page:" + page;
    }

    /**
     * Builds wallet transaction history cache key
     */
    public static String walletTransactionsKey(String walletId, int page) {
        return PREFIX_WALLET + walletId + ":transactions:page:" + page;
    }

    /**
     * Builds exchange rate cache key
     */
    public static String exchangeRateKey(String fromCurrency, String toCurrency) {
        return "exchangerate:" + fromCurrency + ":" + toCurrency;
    }

    /**
     * Builds compliance check cache key
     */
    public static String complianceCheckKey(String userId) {
        return "compliance:" + userId;
    }

    /**
     * Builds fraud score cache key
     */
    public static String fraudScoreKey(String userId) {
        return "fraud:score:" + userId;
    }

    // ========== Pattern Matchers ==========

    /**
     * Pattern to match all user cache keys
     */
    public static final String PATTERN_ALL_USERS = PREFIX_USER + "*";

    /**
     * Pattern to match all wallet cache keys
     */
    public static final String PATTERN_ALL_WALLETS = PREFIX_WALLET + "*";

    /**
     * Pattern to match all session cache keys
     */
    public static final String PATTERN_ALL_SESSIONS = PREFIX_SESSION + "*";

    /**
     * Pattern to match all rate limit cache keys
     */
    public static final String PATTERN_ALL_RATE_LIMITS = PREFIX_RATE_LIMIT + "*";

    /**
     * Pattern to match all locks
     */
    public static final String PATTERN_ALL_LOCKS = PREFIX_LOCK + "*";
}
