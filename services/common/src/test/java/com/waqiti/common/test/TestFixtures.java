package com.waqiti.common.test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Common test fixtures with realistic, reusable test data.
 *
 * Provides pre-configured test data for:
 * - User accounts and profiles
 * - Payment transactions
 * - Wallet balances
 * - Compliance records
 * - Financial transactions
 *
 * All fixtures are immutable and thread-safe.
 * Use builders from TestDataBuilder for customized test data.
 *
 * Usage:
 * <pre>
 * {@code
 * UUID userId = TestFixtures.VERIFIED_USER_ID;
 * BigDecimal amount = TestFixtures.STANDARD_PAYMENT_AMOUNT;
 * String currency = TestFixtures.DEFAULT_CURRENCY;
 * }
 * </pre>
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2024-10-19
 */
public final class TestFixtures {

    private TestFixtures() {
        // Utility class - prevent instantiation
    }

    // ==================== USER FIXTURES ====================

    /**
     * Standard verified user ID for testing
     */
    public static final UUID VERIFIED_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /**
     * Unverified user ID for testing KYC flows
     */
    public static final UUID UNVERIFIED_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /**
     * Premium/VIP user ID for testing premium features
     */
    public static final UUID PREMIUM_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /**
     * Suspended user ID for testing restrictions
     */
    public static final UUID SUSPENDED_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    /**
     * Standard test user email
     */
    public static final String TEST_USER_EMAIL = "test.user@example.com";

    /**
     * Standard test user phone
     */
    public static final String TEST_USER_PHONE = "+1234567890";

    /**
     * Standard test user name
     */
    public static final String TEST_USER_NAME = "John Doe";

    /**
     * Standard test user first name
     */
    public static final String TEST_USER_FIRST_NAME = "John";

    /**
     * Standard test user last name
     */
    public static final String TEST_USER_LAST_NAME = "Doe";

    // ==================== MERCHANT FIXTURES ====================

    /**
     * Standard merchant ID
     */
    public static final UUID MERCHANT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    /**
     * Verified merchant ID
     */
    public static final UUID VERIFIED_MERCHANT_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    /**
     * Standard merchant name
     */
    public static final String MERCHANT_NAME = "Test Merchant Inc.";

    /**
     * Standard merchant category
     */
    public static final String MERCHANT_CATEGORY = "RETAIL";

    // ==================== PAYMENT FIXTURES ====================

    /**
     * Standard payment amount
     */
    public static final BigDecimal STANDARD_PAYMENT_AMOUNT = new BigDecimal("100.0000");

    /**
     * Small payment amount (under reporting threshold)
     */
    public static final BigDecimal SMALL_PAYMENT_AMOUNT = new BigDecimal("10.0000");

    /**
     * Large payment amount (triggers compliance checks)
     */
    public static final BigDecimal LARGE_PAYMENT_AMOUNT = new BigDecimal("10000.0000");

    /**
     * SAR threshold amount ($5,000)
     */
    public static final BigDecimal SAR_THRESHOLD_AMOUNT = new BigDecimal("5000.0000");

    /**
     * CTR threshold amount ($10,000)
     */
    public static final BigDecimal CTR_THRESHOLD_AMOUNT = new BigDecimal("10000.0000");

    /**
     * Minimum payment amount
     */
    public static final BigDecimal MIN_PAYMENT_AMOUNT = new BigDecimal("0.0100");

    /**
     * Maximum payment amount
     */
    public static final BigDecimal MAX_PAYMENT_AMOUNT = new BigDecimal("100000.0000");

    // ==================== CURRENCY FIXTURES ====================

    /**
     * Default currency (USD)
     */
    public static final String DEFAULT_CURRENCY = "USD";

    /**
     * Supported currencies
     */
    public static final List<String> SUPPORTED_CURRENCIES = List.of(
        "USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY"
    );

    // ==================== WALLET FIXTURES ====================

    /**
     * Standard wallet balance
     */
    public static final BigDecimal STANDARD_WALLET_BALANCE = new BigDecimal("1000.0000");

    /**
     * Low wallet balance
     */
    public static final BigDecimal LOW_WALLET_BALANCE = new BigDecimal("10.0000");

    /**
     * High wallet balance
     */
    public static final BigDecimal HIGH_WALLET_BALANCE = new BigDecimal("50000.0000");

    /**
     * Zero balance
     */
    public static final BigDecimal ZERO_BALANCE = BigDecimal.ZERO.setScale(4);

    /**
     * Daily transaction limit
     */
    public static final BigDecimal DAILY_LIMIT = new BigDecimal("5000.0000");

    /**
     * Monthly transaction limit
     */
    public static final BigDecimal MONTHLY_LIMIT = new BigDecimal("25000.0000");

    // ==================== ACCOUNT FIXTURES ====================

    /**
     * Test bank account number
     */
    public static final String TEST_ACCOUNT_NUMBER = "123456789012";

    /**
     * Test routing number
     */
    public static final String TEST_ROUTING_NUMBER = "021000021";

    /**
     * Test IBAN
     */
    public static final String TEST_IBAN = "GB29NWBK60161331926819";

    /**
     * Test SWIFT code
     */
    public static final String TEST_SWIFT_CODE = "CHASUS33";

    // ==================== COMPLIANCE FIXTURES ====================

    /**
     * Test SSN
     */
    public static final String TEST_SSN = "123-45-6789";

    /**
     * Test EIN
     */
    public static final String TEST_EIN = "12-3456789";

    /**
     * Sanctioned entity name for OFAC testing
     */
    public static final String SANCTIONED_ENTITY_NAME = "SANCTIONED PERSON";

    /**
     * Clean entity name (not sanctioned)
     */
    public static final String CLEAN_ENTITY_NAME = "Clean Person";

    /**
     * High-risk country code
     */
    public static final String HIGH_RISK_COUNTRY = "IR";

    /**
     * Low-risk country code
     */
    public static final String LOW_RISK_COUNTRY = "US";

    // ==================== FRAUD FIXTURES ====================

    /**
     * Low fraud score
     */
    public static final BigDecimal LOW_FRAUD_SCORE = new BigDecimal("0.1000");

    /**
     * Medium fraud score
     */
    public static final BigDecimal MEDIUM_FRAUD_SCORE = new BigDecimal("0.5000");

    /**
     * High fraud score
     */
    public static final BigDecimal HIGH_FRAUD_SCORE = new BigDecimal("0.9000");

    /**
     * Fraud score threshold
     */
    public static final BigDecimal FRAUD_THRESHOLD = new BigDecimal("0.7000");

    // ==================== TIME FIXTURES ====================

    /**
     * Fixed test timestamp
     */
    public static final LocalDateTime FIXED_TIMESTAMP = LocalDateTime.of(2024, 10, 19, 12, 0, 0);

    /**
     * Past timestamp (30 days ago)
     */
    public static final LocalDateTime PAST_TIMESTAMP = FIXED_TIMESTAMP.minusDays(30);

    /**
     * Future timestamp (30 days ahead)
     */
    public static final LocalDateTime FUTURE_TIMESTAMP = FIXED_TIMESTAMP.plusDays(30);

    // ==================== ADDRESS FIXTURES ====================

    /**
     * Test address
     */
    public static final String TEST_ADDRESS = "123 Main Street";

    /**
     * Test city
     */
    public static final String TEST_CITY = "New York";

    /**
     * Test state
     */
    public static final String TEST_STATE = "NY";

    /**
     * Test ZIP code
     */
    public static final String TEST_ZIP = "10001";

    /**
     * Test country
     */
    public static final String TEST_COUNTRY = "US";

    // ==================== NETWORK FIXTURES ====================

    /**
     * Test IP address
     */
    public static final String TEST_IP_ADDRESS = "192.168.1.100";

    /**
     * Test user agent
     */
    public static final String TEST_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    /**
     * Test device ID
     */
    public static final String TEST_DEVICE_ID = "device-12345";

    /**
     * Test session ID
     */
    public static final String TEST_SESSION_ID = "session-67890";

    // ==================== TRANSACTION FIXTURES ====================

    /**
     * Standard transaction description
     */
    public static final String STANDARD_DESCRIPTION = "Test Payment Transaction";

    /**
     * Standard idempotency key
     */
    public static final String STANDARD_IDEMPOTENCY_KEY = "idempotency-key-12345";

    /**
     * Standard reference number
     */
    public static final String STANDARD_REFERENCE = "REF-12345";

    // ==================== ERROR FIXTURES ====================

    /**
     * Insufficient funds error message
     */
    public static final String INSUFFICIENT_FUNDS_ERROR = "Insufficient funds";

    /**
     * Invalid amount error message
     */
    public static final String INVALID_AMOUNT_ERROR = "Invalid amount";

    /**
     * Account suspended error message
     */
    public static final String ACCOUNT_SUSPENDED_ERROR = "Account is suspended";

    /**
     * KYC required error message
     */
    public static final String KYC_REQUIRED_ERROR = "KYC verification required";

    // ==================== HELPER METHODS ====================

    /**
     * Create a map of standard metadata for testing.
     *
     * @return Metadata map
     */
    public static Map<String, String> standardMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "test");
        metadata.put("environment", "test");
        metadata.put("version", "1.0");
        metadata.put("timestamp", FIXED_TIMESTAMP.toString());
        return metadata;
    }

    /**
     * Create a list of test user IDs.
     *
     * @return List of user IDs
     */
    public static List<UUID> testUserIds() {
        return List.of(
            VERIFIED_USER_ID,
            UNVERIFIED_USER_ID,
            PREMIUM_USER_ID
        );
    }

    /**
     * Create a set of supported payment methods.
     *
     * @return Set of payment methods
     */
    public static Set<String> supportedPaymentMethods() {
        return Set.of(
            "ACH",
            "WIRE",
            "CARD",
            "WALLET",
            "CRYPTO"
        );
    }

    /**
     * Check if an amount triggers SAR reporting.
     *
     * @param amount Amount to check
     * @return true if SAR required
     */
    public static boolean requiresSAR(BigDecimal amount) {
        return amount.compareTo(SAR_THRESHOLD_AMOUNT) >= 0;
    }

    /**
     * Check if an amount triggers CTR reporting.
     *
     * @param amount Amount to check
     * @return true if CTR required
     */
    public static boolean requiresCTR(BigDecimal amount) {
        return amount.compareTo(CTR_THRESHOLD_AMOUNT) >= 0;
    }

    /**
     * Check if a fraud score is high risk.
     *
     * @param score Fraud score
     * @return true if high risk
     */
    public static boolean isHighRisk(BigDecimal score) {
        return score.compareTo(FRAUD_THRESHOLD) >= 0;
    }
}
