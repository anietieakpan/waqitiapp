package com.waqiti.common.constants;

/**
 * Error Codes
 *
 * Centralized error code constants for consistent error handling
 * across the platform.
 *
 * Format: <CATEGORY>_<SPECIFIC_ERROR>
 * Categories: AUTH, PAYMENT, WALLET, COMPLIANCE, VALIDATION, SYSTEM
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-17
 */
public final class ErrorCodes {

    private ErrorCodes() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== Authentication & Authorization (1000-1999) ==========

    public static final String AUTH_INVALID_CREDENTIALS = "AUTH_1001";
    public static final String AUTH_TOKEN_EXPIRED = "AUTH_1002";
    public static final String AUTH_TOKEN_INVALID = "AUTH_1003";
    public static final String AUTH_UNAUTHORIZED = "AUTH_1004";
    public static final String AUTH_FORBIDDEN = "AUTH_1005";
    public static final String AUTH_SESSION_EXPIRED = "AUTH_1006";
    public static final String AUTH_MFA_REQUIRED = "AUTH_1007";
    public static final String AUTH_MFA_INVALID = "AUTH_1008";
    public static final String AUTH_ACCOUNT_LOCKED = "AUTH_1009";
    public static final String AUTH_ACCOUNT_DISABLED = "AUTH_1010";
    public static final String AUTH_PASSWORD_EXPIRED = "AUTH_1011";

    // ========== Payment Errors (2000-2999) ==========

    public static final String PAYMENT_INSUFFICIENT_FUNDS = "PAY_2001";
    public static final String PAYMENT_INVALID_AMOUNT = "PAY_2002";
    public static final String PAYMENT_LIMIT_EXCEEDED = "PAY_2003";
    public static final String PAYMENT_DECLINED = "PAY_2004";
    public static final String PAYMENT_FAILED = "PAY_2005";
    public static final String PAYMENT_TIMEOUT = "PAY_2006";
    public static final String PAYMENT_DUPLICATE = "PAY_2007";
    public static final String PAYMENT_INVALID_CURRENCY = "PAY_2008";
    public static final String PAYMENT_ACCOUNT_NOT_FOUND = "PAY_2009";
    public static final String PAYMENT_FROZEN_ACCOUNT = "PAY_2010";
    public static final String PAYMENT_INVALID_BENEFICIARY = "PAY_2011";
    public static final String PAYMENT_PROCESSOR_ERROR = "PAY_2012";
    public static final String PAYMENT_NETWORK_ERROR = "PAY_2013";

    // ========== Wallet Errors (3000-3999) ==========

    public static final String WALLET_NOT_FOUND = "WAL_3001";
    public static final String WALLET_FROZEN = "WAL_3002";
    public static final String WALLET_INACTIVE = "WAL_3003";
    public static final String WALLET_INSUFFICIENT_BALANCE = "WAL_3004";
    public static final String WALLET_LIMIT_EXCEEDED = "WAL_3005";
    public static final String WALLET_CURRENCY_MISMATCH = "WAL_3006";
    public static final String WALLET_TRANSACTION_FAILED = "WAL_3007";
    public static final String WALLET_ALREADY_EXISTS = "WAL_3008";
    public static final String WALLET_CREATION_FAILED = "WAL_3009";
    public static final String WALLET_NEGATIVE_BALANCE = "WAL_3010";

    // ========== Compliance Errors (4000-4999) ==========

    public static final String COMPLIANCE_KYC_REQUIRED = "COMP_4001";
    public static final String COMPLIANCE_KYC_FAILED = "COMP_4002";
    public static final String COMPLIANCE_AML_ALERT = "COMP_4003";
    public static final String COMPLIANCE_SANCTIONS_HIT = "COMP_4004";
    public static final String COMPLIANCE_PEP_DETECTED = "COMP_4005";
    public static final String COMPLIANCE_HIGH_RISK = "COMP_4006";
    public static final String COMPLIANCE_TRANSACTION_BLOCKED = "COMP_4007";
    public static final String COMPLIANCE_CTR_THRESHOLD = "COMP_4008";
    public static final String COMPLIANCE_SAR_REQUIRED = "COMP_4009";
    public static final String COMPLIANCE_VELOCITY_EXCEEDED = "COMP_4010";
    public static final String COMPLIANCE_COUNTRY_RESTRICTED = "COMP_4011";

    // ========== Validation Errors (5000-5999) ==========

    public static final String VALIDATION_REQUIRED_FIELD = "VAL_5001";
    public static final String VALIDATION_INVALID_FORMAT = "VAL_5002";
    public static final String VALIDATION_INVALID_LENGTH = "VAL_5003";
    public static final String VALIDATION_INVALID_RANGE = "VAL_5004";
    public static final String VALIDATION_INVALID_EMAIL = "VAL_5005";
    public static final String VALIDATION_INVALID_PHONE = "VAL_5006";
    public static final String VALIDATION_INVALID_DATE = "VAL_5007";
    public static final String VALIDATION_CONSTRAINT_VIOLATION = "VAL_5008";
    public static final String VALIDATION_DUPLICATE_ENTRY = "VAL_5009";
    public static final String VALIDATION_REFERENCE_NOT_FOUND = "VAL_5010";

    // ========== System Errors (6000-6999) ==========

    public static final String SYSTEM_INTERNAL_ERROR = "SYS_6001";
    public static final String SYSTEM_SERVICE_UNAVAILABLE = "SYS_6002";
    public static final String SYSTEM_DATABASE_ERROR = "SYS_6003";
    public static final String SYSTEM_NETWORK_ERROR = "SYS_6004";
    public static final String SYSTEM_TIMEOUT = "SYS_6005";
    public static final String SYSTEM_RATE_LIMIT_EXCEEDED = "SYS_6006";
    public static final String SYSTEM_MAINTENANCE = "SYS_6007";
    public static final String SYSTEM_CONFIGURATION_ERROR = "SYS_6008";
    public static final String SYSTEM_EXTERNAL_SERVICE_ERROR = "SYS_6009";
    public static final String SYSTEM_CACHE_ERROR = "SYS_6010";

    // ========== Transaction Errors (7000-7999) ==========

    public static final String TRANSACTION_NOT_FOUND = "TXN_7001";
    public static final String TRANSACTION_ALREADY_PROCESSED = "TXN_7002";
    public static final String TRANSACTION_CANCELLED = "TXN_7003";
    public static final String TRANSACTION_EXPIRED = "TXN_7004";
    public static final String TRANSACTION_INVALID_STATE = "TXN_7005";
    public static final String TRANSACTION_ROLLBACK_FAILED = "TXN_7006";
    public static final String TRANSACTION_DUPLICATE = "TXN_7007";
    public static final String TRANSACTION_CONCURRENT_MODIFICATION = "TXN_7008";

    // ========== Account Errors (8000-8999) ==========

    public static final String ACCOUNT_NOT_FOUND = "ACC_8001";
    public static final String ACCOUNT_INACTIVE = "ACC_8002";
    public static final String ACCOUNT_SUSPENDED = "ACC_8003";
    public static final String ACCOUNT_CLOSED = "ACC_8004";
    public static final String ACCOUNT_VERIFICATION_REQUIRED = "ACC_8005";
    public static final String ACCOUNT_LIMIT_REACHED = "ACC_8006";
    public static final String ACCOUNT_ALREADY_EXISTS = "ACC_8007";

    // ========== Fraud Detection Errors (9000-9999) ==========

    public static final String FRAUD_SUSPICIOUS_ACTIVITY = "FRAUD_9001";
    public static final String FRAUD_BLOCKED_TRANSACTION = "FRAUD_9002";
    public static final String FRAUD_VELOCITY_CHECK_FAILED = "FRAUD_9003";
    public static final String FRAUD_DEVICE_FINGERPRINT_MISMATCH = "FRAUD_9004";
    public static final String FRAUD_LOCATION_ANOMALY = "FRAUD_9005";
    public static final String FRAUD_PATTERN_DETECTED = "FRAUD_9006";
    public static final String FRAUD_BLACKLIST_HIT = "FRAUD_9007";

    // ========== Helper Methods ==========

    /**
     * Checks if error code is authentication related
     */
    public static boolean isAuthError(String errorCode) {
        return errorCode != null && errorCode.startsWith("AUTH_");
    }

    /**
     * Checks if error code is payment related
     */
    public static boolean isPaymentError(String errorCode) {
        return errorCode != null && errorCode.startsWith("PAY_");
    }

    /**
     * Checks if error code is compliance related
     */
    public static boolean isComplianceError(String errorCode) {
        return errorCode != null && errorCode.startsWith("COMP_");
    }

    /**
     * Checks if error code is system error
     */
    public static boolean isSystemError(String errorCode) {
        return errorCode != null && errorCode.startsWith("SYS_");
    }

    /**
     * Checks if error code is retryable
     */
    public static boolean isRetryable(String errorCode) {
        return errorCode != null && (
            errorCode.equals(SYSTEM_TIMEOUT) ||
            errorCode.equals(SYSTEM_NETWORK_ERROR) ||
            errorCode.equals(SYSTEM_SERVICE_UNAVAILABLE) ||
            errorCode.equals(PAYMENT_TIMEOUT) ||
            errorCode.equals(PAYMENT_NETWORK_ERROR)
        );
    }

    /**
     * Gets error category from error code
     */
    public static String getCategory(String errorCode) {
        if (errorCode == null || !errorCode.contains("_")) {
            return "UNKNOWN";
        }
        return errorCode.substring(0, errorCode.indexOf("_"));
    }
}
