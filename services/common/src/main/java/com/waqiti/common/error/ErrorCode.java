package com.waqiti.common.error;

import org.springframework.http.HttpStatus;

/**
 * Comprehensive error codes for the Waqiti platform (1000+ categorized codes).
 *
 * Format: MODULE_CATEGORY_SPECIFIC_ERROR
 * Categories:
 * - 1xxx: Authentication & Authorization
 * - 2xxx: User & Account Management
 * - 3xxx: Payment & Transaction
 * - 4xxx: Banking & Financial Operations
 * - 5xxx: System & Infrastructure
 * - 6xxx: Integration & External Services
 * - 7xxx: Validation & Data
 * - 8xxx: Security & Compliance
 * - 9xxx: Business Logic & Domain
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
public enum ErrorCode {

    // ===== 1xxx: AUTHENTICATION & AUTHORIZATION ERRORS =====
    AUTH_INVALID_CREDENTIALS("AUTH_1001", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRED("AUTH_1002", "Authentication token has expired", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID("AUTH_1003", "Invalid authentication token", HttpStatus.UNAUTHORIZED),
    AUTH_INSUFFICIENT_PERMISSIONS("AUTH_1004", "Insufficient permissions for this operation", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_LOCKED("AUTH_1005", "Account is locked due to multiple failed attempts", HttpStatus.FORBIDDEN),
    AUTH_MFA_REQUIRED("AUTH_1006", "Multi-factor authentication required", HttpStatus.UNAUTHORIZED),
    AUTH_MFA_INVALID("AUTH_1007", "Invalid MFA code", HttpStatus.UNAUTHORIZED),
    AUTH_SESSION_EXPIRED("AUTH_1008", "Session has expired", HttpStatus.UNAUTHORIZED),
    AUTH_SESSION_INVALID("AUTH_1009", "Invalid session", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_TOKEN_EXPIRED("AUTH_1010", "Refresh token has expired", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_TOKEN_INVALID("AUTH_1011", "Invalid refresh token", HttpStatus.UNAUTHORIZED),
    AUTH_IP_BLOCKED("AUTH_1012", "IP address is blocked", HttpStatus.FORBIDDEN),
    AUTH_DEVICE_NOT_RECOGNIZED("AUTH_1013", "Device not recognized", HttpStatus.FORBIDDEN),
    AUTH_BIOMETRIC_FAILED("AUTH_1014", "Biometric authentication failed", HttpStatus.UNAUTHORIZED),
    AUTH_PASSWORD_EXPIRED("AUTH_1015", "Password has expired", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_SUSPENDED("AUTH_1016", "Account is suspended", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_DEACTIVATED("AUTH_1017", "Account has been deactivated", HttpStatus.FORBIDDEN),
    AUTH_EMAIL_NOT_VERIFIED("AUTH_1018", "Email address not verified", HttpStatus.FORBIDDEN),
    AUTH_PHONE_NOT_VERIFIED("AUTH_1019", "Phone number not verified", HttpStatus.FORBIDDEN),
    AUTH_2FA_NOT_ENABLED("AUTH_1020", "Two-factor authentication not enabled", HttpStatus.FORBIDDEN),

    // ===== 2xxx: USER & ACCOUNT MANAGEMENT ERRORS =====
    USER_NOT_FOUND("USER_2001", "User not found", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS("USER_2002", "User already exists", HttpStatus.CONFLICT),
    USER_INVALID_EMAIL("USER_2003", "Invalid email format", HttpStatus.BAD_REQUEST),
    USER_INVALID_PHONE("USER_2004", "Invalid phone number format", HttpStatus.BAD_REQUEST),
    USER_PROFILE_INCOMPLETE("USER_2005", "User profile is incomplete", HttpStatus.BAD_REQUEST),
    USER_KYC_REQUIRED("USER_2006", "KYC verification required", HttpStatus.FORBIDDEN),
    USER_KYC_PENDING("USER_2007", "KYC verification is pending", HttpStatus.FORBIDDEN),
    USER_KYC_REJECTED("USER_2008", "KYC verification was rejected", HttpStatus.FORBIDDEN),
    USER_EMAIL_TAKEN("USER_2009", "Email address already in use", HttpStatus.CONFLICT),
    USER_PHONE_TAKEN("USER_2010", "Phone number already in use", HttpStatus.CONFLICT),
    USER_USERNAME_TAKEN("USER_2011", "Username already in use", HttpStatus.CONFLICT),
    USER_PROFILE_UPDATE_FAILED("USER_2012", "Failed to update user profile", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_PASSWORD_WEAK("USER_2013", "Password does not meet security requirements", HttpStatus.BAD_REQUEST),
    USER_PASSWORD_REUSED("USER_2014", "Password has been used recently", HttpStatus.BAD_REQUEST),
    USER_MINOR_NOT_ALLOWED("USER_2015", "User must be 18 years or older", HttpStatus.FORBIDDEN),
    USER_COUNTRY_NOT_SUPPORTED("USER_2016", "Service not available in your country", HttpStatus.FORBIDDEN),
    USER_TOO_MANY_ACCOUNTS("USER_2017", "Maximum number of accounts reached", HttpStatus.CONFLICT),
    USER_DELETION_FAILED("USER_2018", "Failed to delete user account", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_REFERRAL_INVALID("USER_2019", "Invalid referral code", HttpStatus.BAD_REQUEST),
    USER_REFERRAL_EXPIRED("USER_2020", "Referral code has expired", HttpStatus.BAD_REQUEST),

    ACCOUNT_NOT_FOUND("ACCOUNT_2101", "Account not found", HttpStatus.NOT_FOUND),
    ACCOUNT_ALREADY_EXISTS("ACCOUNT_2102", "Account already exists", HttpStatus.CONFLICT),
    ACCOUNT_NUMBER_INVALID("ACCOUNT_2103", "Invalid account number format", HttpStatus.BAD_REQUEST),
    ACCOUNT_NUMBER_GENERATION_FAILED("ACCOUNT_2104", "Failed to generate unique account number", HttpStatus.INTERNAL_SERVER_ERROR),
    ACCOUNT_TYPE_MISMATCH("ACCOUNT_2105", "Account type mismatch", HttpStatus.BAD_REQUEST),
    ACCOUNT_CLOSED("ACCOUNT_2106", "Account is closed", HttpStatus.FORBIDDEN),
    ACCOUNT_SUSPENDED("ACCOUNT_2107", "Account is suspended", HttpStatus.FORBIDDEN),
    ACCOUNT_FROZEN("ACCOUNT_2108", "Account is frozen", HttpStatus.FORBIDDEN),
    ACCOUNT_DORMANT("ACCOUNT_2109", "Account is dormant", HttpStatus.FORBIDDEN),
    ACCOUNT_LIMIT_EXCEEDED("ACCOUNT_2110", "Account transaction limit exceeded", HttpStatus.FORBIDDEN),
    ACCOUNT_BALANCE_LOW("ACCOUNT_2111", "Account balance too low", HttpStatus.PAYMENT_REQUIRED),
    ACCOUNT_OVERDRAFT_LIMIT("ACCOUNT_2112", "Overdraft limit exceeded", HttpStatus.FORBIDDEN),
    ACCOUNT_MINIMUM_BALANCE("ACCOUNT_2113", "Minimum balance requirement not met", HttpStatus.FORBIDDEN),
    ACCOUNT_OPENING_FAILED("ACCOUNT_2114", "Account opening failed", HttpStatus.INTERNAL_SERVER_ERROR),
    ACCOUNT_CLOSURE_FAILED("ACCOUNT_2115", "Account closure failed", HttpStatus.INTERNAL_SERVER_ERROR),
    ACCOUNT_VERIFICATION_FAILED("ACCOUNT_2116", "Account verification failed", HttpStatus.FORBIDDEN),
    ACCOUNT_PATTERN_INVALID("ACCOUNT_2117", "Invalid account number pattern", HttpStatus.BAD_REQUEST),
    ACCOUNT_BENEFICIARY_LIMIT("ACCOUNT_2118", "Maximum beneficiaries reached", HttpStatus.CONFLICT),
    ACCOUNT_STATEMENT_UNAVAILABLE("ACCOUNT_2119", "Account statement unavailable", HttpStatus.NOT_FOUND),
    ACCOUNT_TAX_INFO_MISSING("ACCOUNT_2120", "Tax information required", HttpStatus.BAD_REQUEST),

    // ===== 3xxx: PAYMENT & TRANSACTION ERRORS =====
    PAYMENT_NOT_FOUND("PAYMENT_3001", "Payment not found", HttpStatus.NOT_FOUND),
    PAYMENT_INVALID_AMOUNT("PAYMENT_3002", "Invalid payment amount", HttpStatus.BAD_REQUEST),
    PAYMENT_RECIPIENT_NOT_FOUND("PAYMENT_3003", "Payment recipient not found", HttpStatus.NOT_FOUND),
    PAYMENT_METHOD_NOT_SUPPORTED("PAYMENT_3004", "Payment method not supported", HttpStatus.BAD_REQUEST),
    PAYMENT_PROCESSING_FAILED("PAYMENT_3005", "Payment processing failed", HttpStatus.PAYMENT_REQUIRED),
    PAYMENT_ALREADY_PROCESSED("PAYMENT_3006", "Payment has already been processed", HttpStatus.CONFLICT),
    PAYMENT_CANCELLED("PAYMENT_3007", "Payment was cancelled", HttpStatus.CONFLICT),
    PAYMENT_EXPIRED("PAYMENT_3008", "Payment request has expired", HttpStatus.GONE),
    PAYMENT_FRAUD_DETECTED("PAYMENT_3009", "Potential fraud detected", HttpStatus.FORBIDDEN),
    PAYMENT_COMPLIANCE_BLOCK("PAYMENT_3010", "Payment blocked for compliance review", HttpStatus.FORBIDDEN),
    PAYMENT_INSUFFICIENT_FUNDS("PAYMENT_3011", "Insufficient funds for payment", HttpStatus.PAYMENT_REQUIRED),
    PAYMENT_LIMIT_EXCEEDED("PAYMENT_3012", "Payment limit exceeded", HttpStatus.FORBIDDEN),
    PAYMENT_DAILY_LIMIT("PAYMENT_3013", "Daily payment limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    PAYMENT_MONTHLY_LIMIT("PAYMENT_3014", "Monthly payment limit exceeded", HttpStatus.FORBIDDEN),
    PAYMENT_RECIPIENT_BLOCKED("PAYMENT_3015", "Recipient is blocked", HttpStatus.FORBIDDEN),
    PAYMENT_DUPLICATE("PAYMENT_3016", "Duplicate payment detected", HttpStatus.CONFLICT),
    PAYMENT_PENDING("PAYMENT_3017", "Payment is pending", HttpStatus.ACCEPTED),
    PAYMENT_REFUND_FAILED("PAYMENT_3018", "Payment refund failed", HttpStatus.INTERNAL_SERVER_ERROR),
    PAYMENT_CHARGEBACK_INITIATED("PAYMENT_3019", "Chargeback initiated for payment", HttpStatus.CONFLICT),
    PAYMENT_AUTHORIZATION_FAILED("PAYMENT_3020", "Payment authorization failed", HttpStatus.PAYMENT_REQUIRED),

    TRANSACTION_NOT_FOUND("TXN_3101", "Transaction not found", HttpStatus.NOT_FOUND),
    TRANSACTION_INVALID_STATUS("TXN_3102", "Invalid transaction status", HttpStatus.BAD_REQUEST),
    TRANSACTION_DUPLICATE("TXN_3103", "Duplicate transaction detected", HttpStatus.CONFLICT),
    TRANSACTION_REVERSAL_FAILED("TXN_3104", "Transaction reversal failed", HttpStatus.INTERNAL_SERVER_ERROR),
    TRANSACTION_RECONCILIATION_FAILED("TXN_3105", "Transaction reconciliation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    TRANSACTION_TIMEOUT("TXN_3106", "Transaction timeout", HttpStatus.REQUEST_TIMEOUT),
    TRANSACTION_DECLINED("TXN_3107", "Transaction declined", HttpStatus.PAYMENT_REQUIRED),
    TRANSACTION_PENDING("TXN_3108", "Transaction is pending", HttpStatus.ACCEPTED),
    TRANSACTION_CANCELLED("TXN_3109", "Transaction was cancelled", HttpStatus.CONFLICT),
    TRANSACTION_LIMIT_EXCEEDED("TXN_3110", "Transaction limit exceeded", HttpStatus.FORBIDDEN),
    TRANSACTION_VELOCITY_EXCEEDED("TXN_3111", "Transaction velocity exceeded", HttpStatus.TOO_MANY_REQUESTS),
    TRANSACTION_AMOUNT_TOO_LARGE("TXN_3112", "Transaction amount too large", HttpStatus.BAD_REQUEST),
    TRANSACTION_AMOUNT_TOO_SMALL("TXN_3113", "Transaction amount too small", HttpStatus.BAD_REQUEST),
    TRANSACTION_CURRENCY_MISMATCH("TXN_3114", "Currency mismatch", HttpStatus.BAD_REQUEST),
    TRANSACTION_BLOCKED("TXN_3115", "Transaction blocked", HttpStatus.FORBIDDEN),
    TRANSACTION_LEDGER_ERROR("TXN_3116", "Ledger update failed", HttpStatus.INTERNAL_SERVER_ERROR),
    TRANSACTION_IDEMPOTENCY_VIOLATION("TXN_3117", "Idempotency key conflict", HttpStatus.CONFLICT),
    TRANSACTION_SETTLEMENT_FAILED("TXN_3118", "Transaction settlement failed", HttpStatus.INTERNAL_SERVER_ERROR),
    TRANSACTION_CLEARING_FAILED("TXN_3119", "Transaction clearing failed", HttpStatus.INTERNAL_SERVER_ERROR),
    TRANSACTION_AUTHORIZATION_EXPIRED("TXN_3120", "Transaction authorization expired", HttpStatus.GONE),

    // ===== 4xxx: BANKING & FINANCIAL OPERATIONS =====
    WALLET_NOT_FOUND("WALLET_4001", "Wallet not found", HttpStatus.NOT_FOUND),
    WALLET_INSUFFICIENT_BALANCE("WALLET_4002", "Insufficient wallet balance", HttpStatus.PAYMENT_REQUIRED),
    WALLET_CURRENCY_MISMATCH("WALLET_4003", "Currency mismatch", HttpStatus.BAD_REQUEST),
    WALLET_FROZEN("WALLET_4004", "Wallet is frozen", HttpStatus.FORBIDDEN),
    WALLET_LIMIT_EXCEEDED("WALLET_4005", "Wallet transaction limit exceeded", HttpStatus.FORBIDDEN),
    WALLET_INVALID_OPERATION("WALLET_4006", "Invalid wallet operation", HttpStatus.BAD_REQUEST),
    WALLET_CREATION_FAILED("WALLET_4007", "Wallet creation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    WALLET_ALREADY_EXISTS("WALLET_4008", "Wallet already exists", HttpStatus.CONFLICT),
    WALLET_NEGATIVE_BALANCE("WALLET_4009", "Wallet balance cannot be negative", HttpStatus.BAD_REQUEST),
    WALLET_MULTI_CURRENCY_ERROR("WALLET_4010", "Multi-currency operation error", HttpStatus.BAD_REQUEST),

    CARD_NOT_FOUND("CARD_4101", "Card not found", HttpStatus.NOT_FOUND),
    CARD_ORDER_FAILED("CARD_4102", "Card order processing failed", HttpStatus.INTERNAL_SERVER_ERROR),
    CARD_ACTIVATION_FAILED("CARD_4103", "Card activation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    CARD_INVALID_NUMBER("CARD_4104", "Invalid card number", HttpStatus.BAD_REQUEST),
    CARD_EXPIRED("CARD_4105", "Card has expired", HttpStatus.FORBIDDEN),
    CARD_BLOCKED("CARD_4106", "Card is blocked", HttpStatus.FORBIDDEN),
    CARD_LIMIT_EXCEEDED("CARD_4107", "Card transaction limit exceeded", HttpStatus.FORBIDDEN),
    CARD_PIN_INVALID("CARD_4108", "Invalid card PIN", HttpStatus.UNAUTHORIZED),
    CARD_PIN_LOCKED("CARD_4109", "Card PIN is locked", HttpStatus.FORBIDDEN),
    CARD_CVV_INVALID("CARD_4110", "Invalid card CVV", HttpStatus.UNAUTHORIZED),
    CARD_INSUFFICIENT_BALANCE("CARD_4111", "Insufficient card balance", HttpStatus.PAYMENT_REQUIRED),
    CARD_REPLACEMENT_FAILED("CARD_4112", "Card replacement failed", HttpStatus.INTERNAL_SERVER_ERROR),
    CARD_ALREADY_ACTIVATED("CARD_4113", "Card is already activated", HttpStatus.CONFLICT),
    CARD_TYPE_NOT_SUPPORTED("CARD_4114", "Card type not supported", HttpStatus.BAD_REQUEST),
    CARD_ISSUER_ERROR("CARD_4115", "Card issuer error", HttpStatus.BAD_GATEWAY),
    CARD_3DS_REQUIRED("CARD_4116", "3D Secure authentication required", HttpStatus.PAYMENT_REQUIRED),
    CARD_3DS_FAILED("CARD_4117", "3D Secure authentication failed", HttpStatus.UNAUTHORIZED),
    CARD_NETWORK_ERROR("CARD_4118", "Card network error", HttpStatus.BAD_GATEWAY),
    CARD_DECLINED_ISSUER("CARD_4119", "Card declined by issuer", HttpStatus.PAYMENT_REQUIRED),
    CARD_TOKENIZATION_FAILED("CARD_4120", "Card tokenization failed", HttpStatus.INTERNAL_SERVER_ERROR),

    LOAN_NOT_FOUND("LOAN_4201", "Loan not found", HttpStatus.NOT_FOUND),
    LOAN_APPLICATION_REJECTED("LOAN_4202", "Loan application rejected", HttpStatus.FORBIDDEN),
    LOAN_INSUFFICIENT_CREDIT("LOAN_4203", "Insufficient credit score", HttpStatus.FORBIDDEN),
    LOAN_ALREADY_EXISTS("LOAN_4204", "Active loan already exists", HttpStatus.CONFLICT),
    LOAN_REPAYMENT_FAILED("LOAN_4205", "Loan repayment failed", HttpStatus.PAYMENT_REQUIRED),
    LOAN_OVERDUE("LOAN_4206", "Loan payment overdue", HttpStatus.PAYMENT_REQUIRED),
    LOAN_LIMIT_EXCEEDED("LOAN_4207", "Loan limit exceeded", HttpStatus.FORBIDDEN),
    LOAN_COLLATERAL_INSUFFICIENT("LOAN_4208", "Insufficient collateral", HttpStatus.FORBIDDEN),
    LOAN_EARLY_REPAYMENT_PENALTY("LOAN_4209", "Early repayment penalty applies", HttpStatus.BAD_REQUEST),
    LOAN_DISBURSEMENT_FAILED("LOAN_4210", "Loan disbursement failed", HttpStatus.INTERNAL_SERVER_ERROR),

    INVESTMENT_NOT_FOUND("INVEST_4301", "Investment not found", HttpStatus.NOT_FOUND),
    INVESTMENT_INSUFFICIENT_FUNDS("INVEST_4302", "Insufficient funds for investment", HttpStatus.PAYMENT_REQUIRED),
    INVESTMENT_LIMIT_EXCEEDED("INVEST_4303", "Investment limit exceeded", HttpStatus.FORBIDDEN),
    INVESTMENT_REDEMPTION_LOCKED("INVEST_4304", "Investment redemption locked", HttpStatus.FORBIDDEN),
    INVESTMENT_MARKET_CLOSED("INVEST_4305", "Market is closed", HttpStatus.FORBIDDEN),
    INVESTMENT_PRICE_UNAVAILABLE("INVEST_4306", "Investment price unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    INVESTMENT_KYC_REQUIRED("INVEST_4307", "Enhanced KYC required for investment", HttpStatus.FORBIDDEN),
    INVESTMENT_RISK_PROFILE_MISMATCH("INVEST_4308", "Investment risk exceeds profile", HttpStatus.FORBIDDEN),
    INVESTMENT_MINIMUM_AMOUNT("INVEST_4309", "Minimum investment amount not met", HttpStatus.BAD_REQUEST),
    INVESTMENT_SETTLEMENT_PENDING("INVEST_4310", "Investment settlement pending", HttpStatus.ACCEPTED),

    // ===== 5xxx: SYSTEM & INFRASTRUCTURE ERRORS =====
    SYS_INTERNAL_ERROR("SYS_5001", "Internal system error", HttpStatus.INTERNAL_SERVER_ERROR),
    SYS_DATABASE_ERROR("SYS_5002", "Database operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    SYS_NETWORK_ERROR("SYS_5003", "Network communication error", HttpStatus.BAD_GATEWAY),
    SYS_CONFIGURATION_ERROR("SYS_5004", "System configuration error", HttpStatus.INTERNAL_SERVER_ERROR),
    SYS_RESOURCE_EXHAUSTED("SYS_5005", "System resource exhausted", HttpStatus.SERVICE_UNAVAILABLE),
    SYS_MAINTENANCE_MODE("SYS_5006", "System is under maintenance", HttpStatus.SERVICE_UNAVAILABLE),
    SYS_SERVICE_UNAVAILABLE("SYS_5007", "Service temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    SYS_TIMEOUT("SYS_5008", "Operation timeout", HttpStatus.REQUEST_TIMEOUT),
    SYS_DEADLOCK_DETECTED("SYS_5009", "Database deadlock detected", HttpStatus.CONFLICT),
    SYS_CONNECTION_POOL_EXHAUSTED("SYS_5010", "Connection pool exhausted", HttpStatus.SERVICE_UNAVAILABLE),
    SYS_CACHE_ERROR("SYS_5011", "Cache operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    SYS_QUEUE_FULL("SYS_5012", "Message queue is full", HttpStatus.SERVICE_UNAVAILABLE),
    SYS_CIRCUIT_BREAKER_OPEN("SYS_5013", "Circuit breaker is open", HttpStatus.SERVICE_UNAVAILABLE),
    SYS_RATE_LIMIT_INTERNAL("SYS_5014", "Internal rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    SYS_STORAGE_FULL("SYS_5015", "Storage capacity full", HttpStatus.INSUFFICIENT_STORAGE),
    SYS_DEPENDENCY_FAILED("SYS_5016", "Dependent service failed", HttpStatus.FAILED_DEPENDENCY),
    SYS_RECOVERY_FAILED("SYS_5017", "Error recovery failed", HttpStatus.INTERNAL_SERVER_ERROR),
    SYS_SAGA_FAILED("SYS_5018", "Saga transaction failed", HttpStatus.INTERNAL_SERVER_ERROR),
    SYS_REPLICA_SYNC_FAILED("SYS_5019", "Database replica sync failed", HttpStatus.INTERNAL_SERVER_ERROR),
    SYS_BACKUP_FAILED("SYS_5020", "System backup failed", HttpStatus.INTERNAL_SERVER_ERROR),

    // ===== 6xxx: INTEGRATION & EXTERNAL SERVICES =====
    INT_SERVICE_UNAVAILABLE("INT_6001", "External service unavailable", HttpStatus.BAD_GATEWAY),
    INT_TIMEOUT("INT_6002", "External service timeout", HttpStatus.GATEWAY_TIMEOUT),
    INT_INVALID_RESPONSE("INT_6003", "Invalid response from external service", HttpStatus.BAD_GATEWAY),
    INT_RATE_LIMIT("INT_6004", "External service rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    INT_AUTHENTICATION_FAILED("INT_6005", "External service authentication failed", HttpStatus.BAD_GATEWAY),
    INT_CONNECTION_FAILED("INT_6006", "Connection to external service failed", HttpStatus.BAD_GATEWAY),
    INT_RESPONSE_PARSING_ERROR("INT_6007", "Failed to parse external service response", HttpStatus.BAD_GATEWAY),
    INT_API_VERSION_MISMATCH("INT_6008", "API version mismatch", HttpStatus.BAD_GATEWAY),
    INT_WEBHOOK_DELIVERY_FAILED("INT_6009", "Webhook delivery failed", HttpStatus.BAD_GATEWAY),
    INT_WEBHOOK_SIGNATURE_INVALID("INT_6010", "Invalid webhook signature", HttpStatus.UNAUTHORIZED),

    KYC_DOCUMENT_INVALID("KYC_6101", "Invalid KYC document", HttpStatus.BAD_REQUEST),
    KYC_DOCUMENT_EXPIRED("KYC_6102", "KYC document has expired", HttpStatus.BAD_REQUEST),
    KYC_FACE_MATCH_FAILED("KYC_6103", "Face verification failed", HttpStatus.FORBIDDEN),
    KYC_PROVIDER_ERROR("KYC_6104", "KYC provider service error", HttpStatus.BAD_GATEWAY),
    KYC_LIMIT_EXCEEDED("KYC_6105", "KYC attempt limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    KYC_LIVENESS_CHECK_FAILED("KYC_6106", "Liveness check failed", HttpStatus.FORBIDDEN),
    KYC_DOCUMENT_TYPE_UNSUPPORTED("KYC_6107", "Document type not supported", HttpStatus.BAD_REQUEST),
    KYC_OCR_FAILED("KYC_6108", "Document OCR processing failed", HttpStatus.INTERNAL_SERVER_ERROR),
    KYC_ADDRESS_VERIFICATION_FAILED("KYC_6109", "Address verification failed", HttpStatus.FORBIDDEN),
    KYC_SANCTIONS_CHECK_FAILED("KYC_6110", "Sanctions screening failed", HttpStatus.FORBIDDEN),

    CRYPTO_ADDRESS_INVALID("CRYPTO_6201", "Invalid cryptocurrency address", HttpStatus.BAD_REQUEST),
    CRYPTO_NETWORK_ERROR("CRYPTO_6202", "Cryptocurrency network error", HttpStatus.BAD_GATEWAY),
    CRYPTO_INSUFFICIENT_GAS("CRYPTO_6203", "Insufficient gas for transaction", HttpStatus.PAYMENT_REQUIRED),
    CRYPTO_ADDRESS_SANCTIONED("CRYPTO_6204", "Address is on sanctions list", HttpStatus.FORBIDDEN),
    CRYPTO_HIGH_RISK_ADDRESS("CRYPTO_6205", "High-risk address detected", HttpStatus.FORBIDDEN),
    CRYPTO_PRICE_UNAVAILABLE("CRYPTO_6206", "Cryptocurrency price unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    CRYPTO_WALLET_NOT_FOUND("CRYPTO_6207", "Crypto wallet not found", HttpStatus.NOT_FOUND),
    CRYPTO_TRANSACTION_FAILED("CRYPTO_6208", "Crypto transaction failed", HttpStatus.INTERNAL_SERVER_ERROR),
    CRYPTO_CONFIRMATION_PENDING("CRYPTO_6209", "Transaction confirmation pending", HttpStatus.ACCEPTED),
    CRYPTO_CHAIN_CONGESTION("CRYPTO_6210", "Blockchain network congestion", HttpStatus.SERVICE_UNAVAILABLE),

    // ===== 7xxx: VALIDATION & DATA ERRORS =====
    VALIDATION_FAILED("VAL_7001", "Validation failed", HttpStatus.BAD_REQUEST),
    VAL_REQUIRED_FIELD("VAL_7002", "Required field missing", HttpStatus.BAD_REQUEST),
    VAL_INVALID_FORMAT("VAL_7003", "Invalid data format", HttpStatus.BAD_REQUEST),
    VAL_OUT_OF_RANGE("VAL_7004", "Value out of acceptable range", HttpStatus.BAD_REQUEST),
    VAL_INVALID_LENGTH("VAL_7005", "Invalid field length", HttpStatus.BAD_REQUEST),
    VAL_PATTERN_MISMATCH("VAL_7006", "Pattern validation failed", HttpStatus.BAD_REQUEST),
    VAL_INVALID_EMAIL("VAL_7007", "Invalid email format", HttpStatus.BAD_REQUEST),
    VAL_INVALID_PHONE("VAL_7008", "Invalid phone number", HttpStatus.BAD_REQUEST),
    VAL_INVALID_DATE("VAL_7009", "Invalid date format", HttpStatus.BAD_REQUEST),
    VAL_DATE_IN_PAST("VAL_7010", "Date cannot be in the past", HttpStatus.BAD_REQUEST),
    VAL_DATE_IN_FUTURE("VAL_7011", "Date cannot be in the future", HttpStatus.BAD_REQUEST),
    VAL_INVALID_CURRENCY("VAL_7012", "Invalid currency code", HttpStatus.BAD_REQUEST),
    VAL_INVALID_COUNTRY("VAL_7013", "Invalid country code", HttpStatus.BAD_REQUEST),
    VAL_INVALID_TIMEZONE("VAL_7014", "Invalid timezone", HttpStatus.BAD_REQUEST),
    VAL_INVALID_UUID("VAL_7015", "Invalid UUID format", HttpStatus.BAD_REQUEST),
    VAL_INVALID_JSON("VAL_7016", "Invalid JSON format", HttpStatus.BAD_REQUEST),
    VAL_INVALID_XML("VAL_7017", "Invalid XML format", HttpStatus.BAD_REQUEST),
    VAL_CHECKSUM_FAILED("VAL_7018", "Checksum validation failed", HttpStatus.BAD_REQUEST),
    VAL_CONSTRAINT_VIOLATION("VAL_7019", "Data constraint violation", HttpStatus.BAD_REQUEST),
    VAL_DUPLICATE_VALUE("VAL_7020", "Duplicate value detected", HttpStatus.CONFLICT),

    // ===== 8xxx: SECURITY & COMPLIANCE ERRORS =====
    SEC_INVALID_REQUEST("SEC_8001", "Invalid security request", HttpStatus.BAD_REQUEST),
    SEC_SIGNATURE_INVALID("SEC_8002", "Invalid request signature", HttpStatus.UNAUTHORIZED),
    SEC_ENCRYPTION_FAILED("SEC_8003", "Encryption operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    SEC_DECRYPTION_FAILED("SEC_8004", "Decryption operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    SEC_KEY_NOT_FOUND("SEC_8005", "Security key not found", HttpStatus.INTERNAL_SERVER_ERROR),
    SEC_CERTIFICATE_INVALID("SEC_8006", "Invalid certificate", HttpStatus.UNAUTHORIZED),
    SEC_CERTIFICATE_EXPIRED("SEC_8007", "Certificate has expired", HttpStatus.UNAUTHORIZED),
    SEC_CSRF_TOKEN_INVALID("SEC_8008", "Invalid CSRF token", HttpStatus.FORBIDDEN),
    SEC_XSS_DETECTED("SEC_8009", "XSS attack detected", HttpStatus.FORBIDDEN),
    SEC_SQL_INJECTION_DETECTED("SEC_8010", "SQL injection detected", HttpStatus.FORBIDDEN),
    SEC_RATE_LIMIT_EXCEEDED("SEC_8011", "Security rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    SEC_IP_BLACKLISTED("SEC_8012", "IP address is blacklisted", HttpStatus.FORBIDDEN),
    SEC_GEO_BLOCKED("SEC_8013", "Geographic location blocked", HttpStatus.FORBIDDEN),
    SEC_DEVICE_FINGERPRINT_MISMATCH("SEC_8014", "Device fingerprint mismatch", HttpStatus.FORBIDDEN),
    SEC_ANOMALY_DETECTED("SEC_8015", "Security anomaly detected", HttpStatus.FORBIDDEN),
    SEC_BRUTE_FORCE_DETECTED("SEC_8016", "Brute force attack detected", HttpStatus.TOO_MANY_REQUESTS),
    SEC_TOKEN_REPLAY_DETECTED("SEC_8017", "Token replay attack detected", HttpStatus.FORBIDDEN),
    SEC_VAULT_ERROR("SEC_8018", "Vault operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    SEC_HSM_ERROR("SEC_8019", "HSM operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    SEC_KEY_ROTATION_REQUIRED("SEC_8020", "Security key rotation required", HttpStatus.FORBIDDEN),

    COMP_AML_CHECK_FAILED("COMP_8101", "AML compliance check failed", HttpStatus.FORBIDDEN),
    COMP_SANCTION_LIST_MATCH("COMP_8102", "Sanctions list match detected", HttpStatus.FORBIDDEN),
    COMP_TRANSACTION_BLOCKED("COMP_8103", "Transaction blocked by compliance", HttpStatus.FORBIDDEN),
    COMP_REPORTING_REQUIRED("COMP_8104", "Compliance reporting required", HttpStatus.ACCEPTED),
    COMP_POLICY_VIOLATION("COMP_8105", "Compliance policy violation", HttpStatus.FORBIDDEN),
    COMP_PEP_DETECTED("COMP_8106", "Politically Exposed Person detected", HttpStatus.FORBIDDEN),
    COMP_HIGH_RISK_COUNTRY("COMP_8107", "High-risk country detected", HttpStatus.FORBIDDEN),
    COMP_DAILY_LIMIT_EXCEEDED("COMP_8108", "Regulatory daily limit exceeded", HttpStatus.FORBIDDEN),
    COMP_MONTHLY_LIMIT_EXCEEDED("COMP_8109", "Regulatory monthly limit exceeded", HttpStatus.FORBIDDEN),
    COMP_CTR_THRESHOLD("COMP_8110", "CTR reporting threshold reached", HttpStatus.ACCEPTED),
    COMP_SAR_FILED("COMP_8111", "SAR filing required", HttpStatus.ACCEPTED),
    COMP_OFAC_MATCH("COMP_8112", "OFAC sanctions match", HttpStatus.FORBIDDEN),
    COMP_GDPR_VIOLATION("COMP_8113", "GDPR compliance violation", HttpStatus.FORBIDDEN),
    COMP_PCI_VIOLATION("COMP_8114", "PCI-DSS compliance violation", HttpStatus.FORBIDDEN),
    COMP_SOX_VIOLATION("COMP_8115", "SOX compliance violation", HttpStatus.FORBIDDEN),

    FRAUD_DETECTED("FRAUD_8201", "Fraud detected", HttpStatus.FORBIDDEN),
    FRAUD_RISK_HIGH("FRAUD_8202", "High fraud risk", HttpStatus.FORBIDDEN),
    FRAUD_VELOCITY_EXCEEDED("FRAUD_8203", "Transaction velocity anomaly", HttpStatus.FORBIDDEN),
    FRAUD_AMOUNT_ANOMALY("FRAUD_8204", "Transaction amount anomaly", HttpStatus.FORBIDDEN),
    FRAUD_LOCATION_ANOMALY("FRAUD_8205", "Location anomaly detected", HttpStatus.FORBIDDEN),
    FRAUD_DEVICE_ANOMALY("FRAUD_8206", "Device anomaly detected", HttpStatus.FORBIDDEN),
    FRAUD_PATTERN_MATCH("FRAUD_8207", "Fraud pattern match", HttpStatus.FORBIDDEN),
    FRAUD_ML_SCORE_HIGH("FRAUD_8208", "ML fraud score exceeds threshold", HttpStatus.FORBIDDEN),
    FRAUD_ACCOUNT_TAKEOVER("FRAUD_8209", "Account takeover suspected", HttpStatus.FORBIDDEN),
    FRAUD_SYNTHETIC_IDENTITY("FRAUD_8210", "Synthetic identity suspected", HttpStatus.FORBIDDEN),

    // ===== 9xxx: BUSINESS LOGIC & DOMAIN ERRORS =====
    BIZ_INVALID_OPERATION("BIZ_9001", "Invalid business operation", HttpStatus.BAD_REQUEST),
    BIZ_RULE_VIOLATION("BIZ_9002", "Business rule violation", HttpStatus.BAD_REQUEST),
    BIZ_LIMIT_EXCEEDED("BIZ_9003", "Business limit exceeded", HttpStatus.FORBIDDEN),
    BIZ_NOT_AUTHORIZED("BIZ_9004", "Not authorized for business operation", HttpStatus.FORBIDDEN),
    BIZ_WORKFLOW_ERROR("BIZ_9005", "Workflow execution error", HttpStatus.INTERNAL_SERVER_ERROR),
    BIZ_STATE_TRANSITION_INVALID("BIZ_9006", "Invalid state transition", HttpStatus.CONFLICT),
    BIZ_PRECONDITION_FAILED("BIZ_9007", "Business precondition not met", HttpStatus.PRECONDITION_FAILED),
    BIZ_DUPLICATE_REQUEST("BIZ_9008", "Duplicate business request", HttpStatus.CONFLICT),
    BIZ_OPERATION_NOT_ALLOWED("BIZ_9009", "Operation not allowed in current state", HttpStatus.CONFLICT),
    BIZ_RESOURCE_LOCKED("BIZ_9010", "Resource is locked", HttpStatus.LOCKED),

    MERCHANT_NOT_FOUND("MERCHANT_9101", "Merchant not found", HttpStatus.NOT_FOUND),
    MERCHANT_NOT_ACTIVE("MERCHANT_9102", "Merchant account not active", HttpStatus.FORBIDDEN),
    MERCHANT_LIMIT_EXCEEDED("MERCHANT_9103", "Merchant transaction limit exceeded", HttpStatus.FORBIDDEN),
    MERCHANT_SETTLEMENT_FAILED("MERCHANT_9104", "Merchant settlement failed", HttpStatus.INTERNAL_SERVER_ERROR),
    MERCHANT_API_KEY_INVALID("MERCHANT_9105", "Invalid merchant API key", HttpStatus.UNAUTHORIZED),
    MERCHANT_ONBOARDING_INCOMPLETE("MERCHANT_9106", "Merchant onboarding incomplete", HttpStatus.FORBIDDEN),
    MERCHANT_KYB_REQUIRED("MERCHANT_9107", "Know Your Business verification required", HttpStatus.FORBIDDEN),
    MERCHANT_CONTRACT_EXPIRED("MERCHANT_9108", "Merchant contract expired", HttpStatus.FORBIDDEN),
    MERCHANT_CATEGORY_RESTRICTED("MERCHANT_9109", "Merchant category restricted", HttpStatus.FORBIDDEN),
    MERCHANT_CHARGEBACK_THRESHOLD("MERCHANT_9110", "Chargeback threshold exceeded", HttpStatus.FORBIDDEN),

    // Additional domain-specific error codes...
    RESOURCE_NOT_FOUND("RESOURCE_9201", "Resource not found", HttpStatus.NOT_FOUND),
    RESOURCE_ALREADY_EXISTS("RESOURCE_9202", "Resource already exists", HttpStatus.CONFLICT),
    RESOURCE_LOCKED("RESOURCE_9203", "Resource is locked", HttpStatus.LOCKED),
    RESOURCE_DELETED("RESOURCE_9204", "Resource has been deleted", HttpStatus.GONE),
    RESOURCE_VERSION_CONFLICT("RESOURCE_9205", "Resource version conflict", HttpStatus.CONFLICT),

    RATE_LIMIT_EXCEEDED("RATE_9301", "Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    RATE_BURST_LIMIT_EXCEEDED("RATE_9302", "Burst rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    RATE_DAILY_LIMIT_EXCEEDED("RATE_9303", "Daily limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    RATE_MONTHLY_LIMIT_EXCEEDED("RATE_9304", "Monthly limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    RATE_CONCURRENT_LIMIT_EXCEEDED("RATE_9305", "Concurrent request limit exceeded", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public int getStatusCode() {
        return httpStatus.value();
    }

    /**
     * Get HTTP status for this error code
     */
    public HttpStatus getStatus() {
        return httpStatus;
    }

    /**
     * Get error category from code prefix
     */
    public String getCategory() {
        if (code.startsWith("AUTH_")) return "AUTHENTICATION";
        if (code.startsWith("USER_")) return "USER_MANAGEMENT";
        if (code.startsWith("ACCOUNT_")) return "ACCOUNT_MANAGEMENT";
        if (code.startsWith("PAYMENT_")) return "PAYMENT";
        if (code.startsWith("TXN_")) return "TRANSACTION";
        if (code.startsWith("WALLET_")) return "WALLET";
        if (code.startsWith("CARD_")) return "CARD";
        if (code.startsWith("LOAN_")) return "LOAN";
        if (code.startsWith("INVEST_")) return "INVESTMENT";
        if (code.startsWith("SYS_")) return "SYSTEM";
        if (code.startsWith("INT_")) return "INTEGRATION";
        if (code.startsWith("KYC_")) return "KYC";
        if (code.startsWith("CRYPTO_")) return "CRYPTOCURRENCY";
        if (code.startsWith("VAL_")) return "VALIDATION";
        if (code.startsWith("SEC_")) return "SECURITY";
        if (code.startsWith("COMP_")) return "COMPLIANCE";
        if (code.startsWith("FRAUD_")) return "FRAUD";
        if (code.startsWith("BIZ_")) return "BUSINESS";
        if (code.startsWith("MERCHANT_")) return "MERCHANT";
        if (code.startsWith("RESOURCE_")) return "RESOURCE";
        if (code.startsWith("RATE_")) return "RATE_LIMIT";
        return "GENERAL";
    }

    /**
     * Check if this is a client error (4xx)
     */
    public boolean isClientError() {
        return httpStatus.is4xxClientError();
    }

    /**
     * Check if this is a server error (5xx)
     */
    public boolean isServerError() {
        return httpStatus.is5xxServerError();
    }

    /**
     * Check if this error should be retried
     */
    public boolean isRetryable() {
        return httpStatus == HttpStatus.SERVICE_UNAVAILABLE ||
               httpStatus == HttpStatus.GATEWAY_TIMEOUT ||
               httpStatus == HttpStatus.REQUEST_TIMEOUT ||
               code.contains("TIMEOUT") ||
               code.contains("UNAVAILABLE");
    }

    /**
     * Find error code by code string
     */
    public static ErrorCode fromCode(String code) {
        if (code == null) {
            return SYS_INTERNAL_ERROR;
        }

        for (ErrorCode errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }

        // Try to find by partial match for legacy codes
        for (ErrorCode errorCode : values()) {
            if (errorCode.code.contains(code) || code.contains(errorCode.code)) {
                return errorCode;
            }
        }

        return SYS_INTERNAL_ERROR;
    }

    /**
     * Find error codes by category
     */
    public static ErrorCode[] findByCategory(String category) {
        return java.util.Arrays.stream(values())
            .filter(ec -> ec.getCategory().equalsIgnoreCase(category))
            .toArray(ErrorCode[]::new);
    }

    /**
     * Get all retryable error codes
     */
    public static ErrorCode[] getRetryableErrors() {
        return java.util.Arrays.stream(values())
            .filter(ErrorCode::isRetryable)
            .toArray(ErrorCode[]::new);
    }
}
