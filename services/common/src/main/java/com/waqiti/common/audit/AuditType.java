package com.waqiti.common.audit;

/**
 * Enumeration of audit types
 */
public enum AuditType {
    
    // Financial audit types
    FINANCIAL,
    FINANCIAL_TRANSACTION,
    PAYMENT_PROCESSING,
    FUND_TRANSFER,
    ACCOUNT_BALANCE_CHANGE,
    WITHDRAWAL,
    DEPOSIT,
    REFUND,
    FEE_CHARGE,
    
    // Security audit types
    SECURITY,  // General security audit type
    AUTHENTICATION,
    AUTHORIZATION,
    LOGIN_ATTEMPT,
    LOGOUT,
    PASSWORD_CHANGE,
    ACCOUNT_LOCKOUT,
    SUSPICIOUS_ACTIVITY,
    FRAUD_DETECTION,
    
    // Data access audit types
    DATA_ACCESS,  // General data access audit type
    DATA_READ,
    DATA_CREATE,
    DATA_UPDATE,
    DATA_DELETE,
    DATA_EXPORT,
    DATA_IMPORT,
    BULK_OPERATION,
    
    // API audit types
    API_REQUEST,
    API_RESPONSE,
    API_ERROR,
    RATE_LIMIT_EXCEEDED,
    
    // Compliance audit types
    COMPLIANCE,  // General compliance audit type
    GDPR_DATA_ACCESS,
    GDPR_DATA_DELETION,
    PCI_DSS_VIOLATION,
    SOX_CONTROL_TEST,
    REGULATORY_REPORT,
    
    // System audit types
    SYSTEM_STARTUP,
    SYSTEM_SHUTDOWN,
    CONFIGURATION_CHANGE,
    SERVICE_HEALTH_CHECK,
    BACKUP_OPERATION,
    
    // User activity audit types
    USER_PROFILE_UPDATE,
    PREFERENCE_CHANGE,
    DOCUMENT_UPLOAD,
    DOCUMENT_DOWNLOAD,
    
    // Administrative audit types
    ADMIN_ACTION,
    ROLE_ASSIGNMENT,
    PERMISSION_GRANT,
    POLICY_CHANGE,
    
    // Custom/Other
    CUSTOM,
    OTHER
}