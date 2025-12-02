package com.waqiti.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Comprehensive error codes for the Waqiti platform
 * Format: MODULE_CATEGORY_SPECIFIC_ERROR
 */
public enum ErrorCode {
    
    // ===== AUTHENTICATION & AUTHORIZATION ERRORS (AUTH_XXX) =====
    AUTH_INVALID_CREDENTIALS("AUTH_001", "Invalid username or password"),
    AUTH_TOKEN_EXPIRED("AUTH_002", "Authentication token has expired"),
    AUTH_TOKEN_INVALID("AUTH_003", "Invalid authentication token"),
    AUTH_INSUFFICIENT_PERMISSIONS("AUTH_004", "Insufficient permissions for this operation"),
    AUTH_ACCOUNT_LOCKED("AUTH_005", "Account is locked due to multiple failed attempts"),
    AUTH_MFA_REQUIRED("AUTH_006", "Multi-factor authentication required"),
    AUTH_MFA_INVALID("AUTH_007", "Invalid MFA code"),
    AUTH_SESSION_EXPIRED("AUTH_008", "Session has expired"),
    
    // ===== USER MANAGEMENT ERRORS (USER_XXX) =====
    USER_NOT_FOUND("USER_001", "User not found"),
    USER_ALREADY_EXISTS("USER_002", "User already exists"),
    USER_INVALID_EMAIL("USER_003", "Invalid email format"),
    USER_INVALID_PHONE("USER_004", "Invalid phone number format"),
    USER_PROFILE_INCOMPLETE("USER_005", "User profile is incomplete"),
    USER_KYC_REQUIRED("USER_006", "KYC verification required"),
    USER_KYC_PENDING("USER_007", "KYC verification is pending"),
    USER_KYC_REJECTED("USER_008", "KYC verification was rejected"),
    
    // ===== ADDRESS ERRORS (ADDR_XXX) =====
    INVALID_ADDRESS("ADDR_001", "Invalid address format"),
    
    // ===== WALLET ERRORS (WALLET_XXX) =====
    WALLET_NOT_FOUND("WALLET_001", "Wallet not found"),
    WALLET_INSUFFICIENT_BALANCE("WALLET_002", "Insufficient wallet balance"),
    WALLET_CURRENCY_MISMATCH("WALLET_003", "Currency mismatch"),
    WALLET_FROZEN("WALLET_004", "Wallet is frozen"),
    WALLET_LIMIT_EXCEEDED("WALLET_005", "Wallet transaction limit exceeded"),
    WALLET_INVALID_OPERATION("WALLET_006", "Invalid wallet operation"),
    
    // ===== PAYMENT ERRORS (PAYMENT_XXX) =====
    PAYMENT_NOT_FOUND("PAYMENT_000", "Payment not found"),
    PAYMENT_INVALID_AMOUNT("PAYMENT_001", "Invalid payment amount"),
    PAYMENT_RECIPIENT_NOT_FOUND("PAYMENT_002", "Payment recipient not found"),
    PAYMENT_METHOD_NOT_SUPPORTED("PAYMENT_003", "Payment method not supported"),
    PAYMENT_PROCESSING_FAILED("PAYMENT_004", "Payment processing failed"),
    PAYMENT_ALREADY_PROCESSED("PAYMENT_005", "Payment has already been processed"),
    PAYMENT_CANCELLED("PAYMENT_006", "Payment was cancelled"),
    PAYMENT_EXPIRED("PAYMENT_007", "Payment request has expired"),
    PAYMENT_FRAUD_DETECTED("PAYMENT_008", "Potential fraud detected"),
    PAYMENT_COMPLIANCE_BLOCK("PAYMENT_009", "Payment blocked for compliance review"),
    
    // ===== TRANSACTION ERRORS (TXN_XXX) =====
    TXN_NOT_FOUND("TXN_001", "Transaction not found"),
    TXN_INVALID_STATUS("TXN_002", "Invalid transaction status"),
    TXN_DUPLICATE("TXN_003", "Duplicate transaction detected"),
    TXN_REVERSAL_FAILED("TXN_004", "Transaction reversal failed"),
    TXN_RECONCILIATION_FAILED("TXN_005", "Transaction reconciliation failed"),
    
    // ===== NFC ERRORS (NFC_XXX) =====
    NFC_DEVICE_NOT_SUPPORTED("NFC_001", "NFC not supported on this device"),
    NFC_TAG_READ_ERROR("NFC_002", "Failed to read NFC tag"),
    NFC_INVALID_SIGNATURE("NFC_003", "Invalid NFC signature"),
    NFC_SIGNATURE_EXPIRED("NFC_004", "NFC signature has expired"),
    NFC_DEVICE_MISMATCH("NFC_005", "NFC device ID mismatch"),
    
    // ===== CRYPTO ERRORS (CRYPTO_XXX) =====
    CRYPTO_ADDRESS_INVALID("CRYPTO_001", "Invalid cryptocurrency address"),
    CRYPTO_NETWORK_ERROR("CRYPTO_002", "Cryptocurrency network error"),
    CRYPTO_INSUFFICIENT_GAS("CRYPTO_003", "Insufficient gas for transaction"),
    CRYPTO_ADDRESS_SANCTIONED("CRYPTO_004", "Address is on sanctions list"),
    CRYPTO_HIGH_RISK_ADDRESS("CRYPTO_005", "High-risk address detected"),
    CRYPTO_PRICE_UNAVAILABLE("CRYPTO_006", "Cryptocurrency price unavailable"),
    
    // ===== KYC ERRORS (KYC_XXX) =====
    KYC_DOCUMENT_INVALID("KYC_001", "Invalid KYC document"),
    KYC_DOCUMENT_EXPIRED("KYC_002", "KYC document has expired"),
    KYC_FACE_MATCH_FAILED("KYC_003", "Face verification failed"),
    KYC_PROVIDER_ERROR("KYC_004", "KYC provider service error"),
    KYC_LIMIT_EXCEEDED("KYC_005", "KYC attempt limit exceeded"),
    
    // ===== BUSINESS ERRORS (BIZ_XXX) =====
    BIZ_INVALID_OPERATION("BIZ_001", "Invalid business operation"),
    BIZ_RULE_VIOLATION("BIZ_002", "Business rule violation"),
    BIZ_LIMIT_EXCEEDED("BIZ_003", "Business limit exceeded"),
    BIZ_NOT_AUTHORIZED("BIZ_004", "Not authorized for business operation"),
    
    // ===== INTEGRATION ERRORS (INT_XXX) =====
    INT_SERVICE_UNAVAILABLE("INT_001", "External service unavailable"),
    INT_TIMEOUT("INT_002", "External service timeout"),
    INT_INVALID_RESPONSE("INT_003", "Invalid response from external service"),
    INT_RATE_LIMIT("INT_004", "External service rate limit exceeded"),
    
    // ===== VALIDATION ERRORS (VAL_XXX) =====
    VALIDATION_FAILED("VAL_000", "Validation failed"),
    VAL_REQUIRED_FIELD("VAL_001", "Required field missing"),
    VAL_INVALID_FORMAT("VAL_002", "Invalid data format"),
    VAL_OUT_OF_RANGE("VAL_003", "Value out of acceptable range"),
    VAL_INVALID_LENGTH("VAL_004", "Invalid field length"),
    VAL_PATTERN_MISMATCH("VAL_005", "Pattern validation failed"),
    
    // ===== SYSTEM ERRORS (SYS_XXX) =====
    SYS_INTERNAL_ERROR("SYS_001", "Internal system error"),
    SYS_DATABASE_ERROR("SYS_002", "Database operation failed"),
    SYS_NETWORK_ERROR("SYS_003", "Network communication error"),
    SYS_CONFIGURATION_ERROR("SYS_004", "System configuration error"),
    SYS_RESOURCE_EXHAUSTED("SYS_005", "System resource exhausted"),
    SYS_MAINTENANCE_MODE("SYS_006", "System is under maintenance"),
    RECOVERY_FAILED("SYS_007", "Error recovery failed"),
    SAGA_FAILED("SYS_008", "Saga transaction failed"),
    CONFIG_ERROR("SYS_009", "Configuration error"),
    
    // ===== RATE LIMITING ERRORS (RATE_XXX) =====
    RATE_LIMIT_EXCEEDED("RATE_001", "Rate limit exceeded"),
    RATE_BURST_LIMIT_EXCEEDED("RATE_002", "Burst rate limit exceeded"),
    RATE_DAILY_LIMIT_EXCEEDED("RATE_003", "Daily limit exceeded"),
    
    // ===== SECURITY ERRORS (SEC_XXX) =====
    SEC_INVALID_REQUEST("SEC_001", "Invalid security request"),
    SEC_SIGNATURE_INVALID("SEC_002", "Invalid request signature"),
    SEC_ENCRYPTION_FAILED("SEC_003", "Encryption operation failed"),
    SEC_DECRYPTION_FAILED("SEC_004", "Decryption operation failed"),
    SEC_KEY_NOT_FOUND("SEC_005", "Security key not found"),
    
    // ===== MESSAGING ERRORS (MSG_XXX) =====
    MSG_DELIVERY_FAILED("MSG_001", "Message delivery failed"),
    MSG_RECIPIENT_NOT_FOUND("MSG_002", "Message recipient not found"),
    MSG_ENCRYPTION_REQUIRED("MSG_003", "Message encryption required"),
    MSG_SIZE_EXCEEDED("MSG_004", "Message size limit exceeded"),
    
    // ===== ACCOUNT ERRORS (ACCOUNT_XXX) =====
    ACCOUNT_NOT_FOUND("ACCOUNT_001", "Account not found"),
    ACCOUNT_ALREADY_EXISTS("ACCOUNT_002", "Account already exists"),
    ACCOUNT_NUMBER_INVALID("ACCOUNT_003", "Invalid account number format"),
    ACCOUNT_NUMBER_GENERATION_FAILED("ACCOUNT_004", "Failed to generate unique account number"),
    ACCOUNT_TYPE_MISMATCH("ACCOUNT_005", "Account type mismatch"),
    ACCOUNT_CLOSED("ACCOUNT_006", "Account is closed"),
    ACCOUNT_SUSPENDED("ACCOUNT_007", "Account is suspended"),
    ACCOUNT_PATTERN_INVALID("ACCOUNT_008", "Invalid account number pattern"),
    ACCOUNT_PATTERN_NOT_FOUND("ACCOUNT_009", "Account number pattern not found"),
    
    // ===== BIOMETRIC ERRORS (BIO_XXX) =====
    BIO_NOT_AVAILABLE("BIO_001", "Biometric authentication not available"),
    BIO_ENROLLMENT_FAILED("BIO_002", "Biometric enrollment failed"),
    BIO_VERIFICATION_FAILED("BIO_003", "Biometric verification failed"),
    BIO_TEMPLATE_CORRUPTED("BIO_004", "Biometric template corrupted"),
    BIO_PROVIDER_ERROR("BIO_005", "Biometric provider error"),
    BIO_TIMEOUT("BIO_006", "Biometric operation timeout"),
    BIO_CANCELLED("BIO_007", "Biometric operation cancelled"),
    BIO_LOCKOUT("BIO_008", "Biometric authentication locked out"),
    
    // ===== CHECK DEPOSIT ERRORS (CHECK_XXX) =====
    CHECK_IMAGE_INVALID("CHECK_001", "Invalid check image"),
    CHECK_IMAGE_QUALITY_LOW("CHECK_002", "Check image quality too low"),
    CHECK_OCR_FAILED("CHECK_003", "Check OCR processing failed"),
    CHECK_FRAUD_DETECTED("CHECK_004", "Check fraud detected"),
    CHECK_DUPLICATE("CHECK_005", "Duplicate check detected"),
    CHECK_AMOUNT_MISMATCH("CHECK_006", "Check amount mismatch"),
    CHECK_ROUTING_INVALID("CHECK_007", "Invalid routing number"),
    CHECK_ACCOUNT_INVALID("CHECK_008", "Invalid account number on check"),
    CHECK_ENDORSEMENT_MISSING("CHECK_009", "Check endorsement missing"),
    CHECK_PROCESSING_FAILED("CHECK_010", "Check processing failed"),
    
    // ===== CERTIFICATE & SSL ERRORS (CERT_XXX) =====
    CERT_PINNING_FAILED("CERT_001", "Certificate pinning validation failed"),
    CERT_EXPIRED("CERT_002", "Certificate has expired"),
    CERT_INVALID("CERT_003", "Invalid certificate"),
    CERT_CHAIN_INVALID("CERT_004", "Invalid certificate chain"),
    CERT_REVOKED("CERT_005", "Certificate has been revoked"),
    CERT_HOSTNAME_MISMATCH("CERT_006", "Certificate hostname mismatch"),
    
    // ===== LOCALIZATION ERRORS (LOC_XXX) =====
    LOC_LANGUAGE_NOT_SUPPORTED("LOC_001", "Language not supported"),
    LOC_TRANSLATION_MISSING("LOC_002", "Translation missing"),
    LOC_LOCALE_INVALID("LOC_003", "Invalid locale format"),
    LOC_RESOURCE_NOT_FOUND("LOC_004", "Localization resource not found"),
    
    // ===== NOTIFICATION ERRORS (NOTIF_XXX) =====
    NOTIF_TEMPLATE_NOT_FOUND("NOTIF_001", "Notification template not found"),
    NOTIF_CHANNEL_NOT_AVAILABLE("NOTIF_002", "Notification channel not available"),
    NOTIF_PREFERENCE_INVALID("NOTIF_003", "Invalid notification preference"),
    NOTIF_SCHEDULING_FAILED("NOTIF_004", "Notification scheduling failed"),
    NOTIF_DELIVERY_FAILED("NOTIF_005", "Notification delivery failed"),
    NOTIF_FORMAT_INVALID("NOTIF_006", "Invalid notification format"),
    
    // ===== SUPPORT & TICKETING ERRORS (SUPPORT_XXX) =====
    SUPPORT_TICKET_NOT_FOUND("SUPPORT_001", "Support ticket not found"),
    SUPPORT_CATEGORIZATION_FAILED("SUPPORT_002", "Ticket categorization failed"),
    SUPPORT_ATTACHMENT_TOO_LARGE("SUPPORT_003", "Support ticket attachment too large"),
    SUPPORT_INVALID_PRIORITY("SUPPORT_004", "Invalid ticket priority"),
    SUPPORT_AGENT_NOT_AVAILABLE("SUPPORT_005", "Support agent not available"),
    SUPPORT_TICKET_CLOSED("SUPPORT_006", "Support ticket is closed"),
    
    // ===== CARD ERRORS (CARD_XXX) =====
    CARD_ORDER_FAILED("CARD_001", "Card order processing failed"),
    CARD_ACTIVATION_FAILED("CARD_002", "Card activation failed"),
    CARD_INVALID_NUMBER("CARD_003", "Invalid card number"),
    CARD_EXPIRED("CARD_004", "Card has expired"),
    CARD_BLOCKED("CARD_005", "Card is blocked"),
    CARD_LIMIT_EXCEEDED("CARD_006", "Card transaction limit exceeded"),
    CARD_NOT_FOUND("CARD_007", "Card not found"),
    CARD_PIN_INVALID("CARD_008", "Invalid card PIN"),
    CARD_PIN_LOCKED("CARD_009", "Card PIN is locked"),
    CARD_CVV_INVALID("CARD_010", "Invalid card CVV"),
    CARD_INSUFFICIENT_BALANCE("CARD_011", "Insufficient card balance"),
    CARD_REPLACEMENT_FAILED("CARD_012", "Card replacement failed"),
    CARD_ALREADY_ACTIVATED("CARD_013", "Card is already activated"),
    CARD_TYPE_NOT_SUPPORTED("CARD_014", "Card type not supported"),
    CARD_ISSUER_ERROR("CARD_015", "Card issuer error"),
    
    // ===== QR CODE ERRORS (QR_XXX) =====
    QR_GENERATION_FAILED("QR_001", "QR code generation failed"),
    QR_INVALID_FORMAT("QR_002", "Invalid QR code format"),
    QR_EXPIRED("QR_003", "QR code has expired"),
    QR_ALREADY_USED("QR_004", "QR code has already been used"),
    QR_SCAN_FAILED("QR_005", "QR code scan failed"),
    QR_INVALID_DATA("QR_006", "Invalid QR code data"),
    
    // ===== CAMERA ERRORS (CAM_XXX) =====
    CAM_PERMISSION_DENIED("CAM_001", "Camera permission denied"),
    CAM_NOT_AVAILABLE("CAM_002", "Camera not available"),
    CAM_CAPTURE_FAILED("CAM_003", "Camera capture failed"),
    CAM_FOCUS_FAILED("CAM_004", "Camera focus failed"),
    CAM_FLASH_FAILED("CAM_005", "Camera flash failed"),
    
    // ===== NETWORK & CONNECTIVITY ERRORS (NET_XXX) =====
    NET_CONNECTION_FAILED("NET_001", "Network connection failed"),
    NET_TIMEOUT("NET_002", "Network request timeout"),
    NET_SSL_ERROR("NET_003", "SSL connection error"),
    NET_DNS_RESOLUTION_FAILED("NET_004", "DNS resolution failed"),
    NET_PROXY_ERROR("NET_005", "Proxy connection error"),
    NET_CERTIFICATE_ERROR("NET_006", "Network certificate error"),
    
    // ===== REPORTING ERRORS (REPORT_XXX) =====
    REPORT_GENERATION_FAILED("REPORT_001", "Report generation failed"),
    REPORT_FORMAT_NOT_SUPPORTED("REPORT_002", "Report format not supported"),
    REPORT_DATA_INSUFFICIENT("REPORT_003", "Insufficient data for report"),
    REPORT_TEMPLATE_INVALID("REPORT_004", "Invalid report template"),
    REPORT_EXPORT_FAILED("REPORT_005", "Report export failed"),
    
    // ===== COMPLIANCE ERRORS (COMP_XXX) =====
    COMP_AML_CHECK_FAILED("COMP_001", "AML compliance check failed"),
    COMP_SANCTION_LIST_MATCH("COMP_002", "Sanctions list match detected"),
    COMP_TRANSACTION_BLOCKED("COMP_003", "Transaction blocked by compliance"),
    COMP_REPORTING_REQUIRED("COMP_004", "Compliance reporting required"),
    COMP_POLICY_VIOLATION("COMP_005", "Compliance policy violation"),
    
    // ===== ANALYTICS ERRORS (ANALYTICS_XXX) =====
    ANALYTICS_DATA_UNAVAILABLE("ANALYTICS_001", "Analytics data unavailable"),
    ANALYTICS_CALCULATION_FAILED("ANALYTICS_002", "Analytics calculation failed"),
    ANALYTICS_MODEL_NOT_FOUND("ANALYTICS_003", "Analytics model not found"),
    ANALYTICS_PREDICTION_FAILED("ANALYTICS_004", "Analytics prediction failed"),
    
    // ===== FILE & STORAGE ERRORS (FILE_XXX) =====
    FILE_NOT_FOUND("FILE_001", "File not found"),
    FILE_UPLOAD_FAILED("FILE_002", "File upload failed"),
    FILE_SIZE_EXCEEDED("FILE_003", "File size limit exceeded"),
    FILE_TYPE_NOT_SUPPORTED("FILE_004", "File type not supported"),
    FILE_CORRUPTED("FILE_005", "File is corrupted"),
    FILE_VIRUS_DETECTED("FILE_006", "Virus detected in file"),
    FILE_STORAGE_FULL("FILE_007", "File storage capacity full"),
    
    // ===== MERCHANT ERRORS (MERCHANT_XXX) =====
    MERCHANT_NOT_FOUND("MERCHANT_001", "Merchant not found"),
    MERCHANT_NOT_ACTIVE("MERCHANT_002", "Merchant account not active"),
    MERCHANT_LIMIT_EXCEEDED("MERCHANT_003", "Merchant transaction limit exceeded"),
    MERCHANT_SETTLEMENT_FAILED("MERCHANT_004", "Merchant settlement failed"),
    MERCHANT_API_KEY_INVALID("MERCHANT_005", "Invalid merchant API key"),
    
    // ===== WEBHOOK ERRORS (WEBHOOK_XXX) =====
    WEBHOOK_DELIVERY_FAILED("WEBHOOK_001", "Webhook delivery failed"),
    WEBHOOK_INVALID_SIGNATURE("WEBHOOK_002", "Invalid webhook signature"),
    WEBHOOK_TIMEOUT("WEBHOOK_003", "Webhook delivery timeout"),
    WEBHOOK_RETRY_EXHAUSTED("WEBHOOK_004", "Webhook retry attempts exhausted"),
    
    // ===== AUDIT ERRORS (AUDIT_XXX) =====
    AUDIT_LOG_FAILED("AUDIT_001", "Audit log creation failed"),
    AUDIT_TRAIL_CORRUPTED("AUDIT_002", "Audit trail corrupted"),
    AUDIT_ACCESS_DENIED("AUDIT_003", "Audit access denied"),
    AUDIT_RETENTION_EXCEEDED("AUDIT_004", "Audit retention period exceeded"),
    
    // ===== EVENT SOURCING ERRORS (EVENT_XXX) =====
    EVENT_STORE_UNAVAILABLE("EVENT_001", "Event store unavailable"),
    EVENT_SERIALIZATION_FAILED("EVENT_002", "Event serialization failed"),
    EVENT_REPLAY_FAILED("EVENT_003", "Event replay failed"),
    EVENT_VERSION_CONFLICT("EVENT_004", "Event version conflict"),
    EVENT_STREAM_NOT_FOUND("EVENT_005", "Event stream not found"),
    
    // ===== FINANCIAL DATA ERRORS (FIN_XXX) =====
    FINANCIAL_DATA_ERROR("FIN_001", "Financial data processing error"),
    PAYMENT_PROCESSING_ERROR("FIN_002", "Payment processing error"),
    INVESTMENT_DATA_ERROR("FIN_003", "Investment data error"),
    COMPLIANCE_PROCESSING_ERROR("FIN_004", "Compliance processing error"),
    
    // ===== FRAUD DETECTION ERRORS (FRAUD_XXX) =====
    FRAUD_DATA_ERROR("FRAUD_001", "Fraud detection data error"),
    FRAUD_PROCESSING_ERROR("FRAUD_002", "Fraud detection processing error");
    
    private final String code;
    private final String defaultMessage;
    
    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDefaultMessage() {
        return defaultMessage;
    }
    
    /**
     * Get HTTP status for this error code
     */
    public HttpStatus getStatus() {
        // Map error codes to appropriate HTTP status codes
        if (code.startsWith("AUTH_")) {
            return code.equals("AUTH_004") ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
        } else if (code.startsWith("USER_") || code.startsWith("WALLET_") || code.startsWith("ACCOUNT_")) {
            if (code.endsWith("_NOT_FOUND")) return HttpStatus.NOT_FOUND;
            if (code.endsWith("_ALREADY_EXISTS")) return HttpStatus.CONFLICT;
            return HttpStatus.BAD_REQUEST;
        } else if (code.startsWith("PAYMENT_")) {
            if (code.equals("PAYMENT_FRAUD_DETECTED")) return HttpStatus.FORBIDDEN;
            if (code.equals("PAYMENT_ALREADY_PROCESSED")) return HttpStatus.CONFLICT;
            return HttpStatus.PAYMENT_REQUIRED;
        } else if (code.startsWith("VAL_")) {
            return HttpStatus.BAD_REQUEST;
        } else if (code.startsWith("RATE_")) {
            return HttpStatus.TOO_MANY_REQUESTS;
        } else if (code.startsWith("SEC_") || code.startsWith("COMP_")) {
            return HttpStatus.FORBIDDEN;
        } else if (code.startsWith("FIN_") || code.startsWith("FRAUD_")) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        } else if (code.startsWith("INT_") || code.startsWith("NET_")) {
            return HttpStatus.BAD_GATEWAY;
        } else if (code.startsWith("SYS_")) {
            return code.equals("SYS_MAINTENANCE_MODE") ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.INTERNAL_SERVER_ERROR;
        } else if (code.endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        } else if (code.endsWith("_ALREADY_EXISTS") || code.endsWith("_DUPLICATE")) {
            return HttpStatus.CONFLICT;
        } else if (code.contains("_TIMEOUT")) {
            return HttpStatus.REQUEST_TIMEOUT;
        }
        return HttpStatus.BAD_REQUEST;
    }
    
    /**
     * Find error code by code string
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return SYS_INTERNAL_ERROR;
    }
}