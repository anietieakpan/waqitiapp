package com.waqiti.common.security;

/**
 * Enumeration of resource types for security validation and access control
 * in the fraud detection and financial services system.
 */
public enum ResourceType {
    
    // User and Account Resources
    USER("user", "User resource"),
    USER_PROFILE("user_profile", "User profile information"),
    USER_ACCOUNT("user_account", "User account data"),
    USER_PREFERENCES("user_preferences", "User preference settings"),
    USER_HISTORY("user_history", "User transaction history"),
    
    // Transaction Resources  
    TRANSACTION("transaction", "Financial transaction"),
    TRANSACTION_DETAILS("transaction_details", "Transaction detail records"),
    TRANSACTION_HISTORY("transaction_history", "Transaction history records"),
    PAYMENT_METHOD("payment_method", "User payment methods"),
    
    // Fraud Detection Resources
    FRAUD_ALERT("fraud_alert", "Fraud detection alerts"),
    FRAUD_ANALYSIS("fraud_analysis", "Fraud analysis results"),
    FRAUD_SCORE("fraud_score", "Fraud risk scores"),
    FRAUD_CONTEXT("fraud_context", "Fraud detection context"),
    RISK_PROFILE("risk_profile", "User risk profiles"),
    
    // ML and Analytics Resources
    ML_MODEL("ml_model", "Machine learning models"),
    ML_PREDICTION("ml_prediction", "ML prediction results"),
    ANALYTICS_DATA("analytics_data", "Analytics and reporting data"),
    BEHAVIORAL_DATA("behavioral_data", "User behavioral analysis"),
    
    // Notification Resources
    NOTIFICATION("notification", "User notifications"),
    NOTIFICATION_PREFERENCES("notification_preferences", "Notification settings"),
    ALERT_CONFIGURATION("alert_configuration", "Alert configuration settings"),
    
    // Administrative Resources
    AUDIT_LOG("audit_log", "System audit logs"),
    SYSTEM_CONFIG("system_config", "System configuration"),
    COMPLIANCE_REPORT("compliance_report", "Compliance reports"),
    INVESTIGATION_CASE("investigation_case", "Fraud investigation cases"),
    
    // Merchant Resources
    MERCHANT_PROFILE("merchant_profile", "Merchant profile information"),
    MERCHANT_ANALYTICS("merchant_analytics", "Merchant analytics data"),
    MERCHANT_RISK("merchant_risk", "Merchant risk assessment"),
    
    // Device and Session Resources
    DEVICE_INFO("device_info", "User device information"),
    SESSION_DATA("session_data", "User session data"),
    AUTHENTICATION_DATA("authentication_data", "Authentication records"),
    
    // Geographic and Location Resources
    LOCATION_DATA("location_data", "User location information"),
    GEO_ANALYTICS("geo_analytics", "Geographic analytics"),
    
    // Storage and Backup Resources
    STORAGE_OBJECT("storage_object", "Stored data objects"),
    BACKUP_DATA("backup_data", "Backup and archive data"),
    
    // Generic Resources
    DOCUMENT("document", "Document files"),
    REPORT("report", "Generated reports"),
    CONFIGURATION("configuration", "Configuration settings"),
    METADATA("metadata", "Resource metadata"),
    
    // Special Administrative Resources
    ALL_USERS("all_users", "All user data - admin only"),
    SYSTEM_WIDE("system_wide", "System-wide resources"),
    CROSS_USER("cross_user", "Cross-user data access");
    
    private final String code;
    private final String description;
    
    ResourceType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * Get resource type code
     */
    public String getCode() {
        return code;
    }
    
    /**
     * Get resource type description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this resource type requires admin privileges
     */
    public boolean requiresAdminAccess() {
        return this == ALL_USERS || 
               this == SYSTEM_WIDE || 
               this == SYSTEM_CONFIG ||
               this == AUDIT_LOG ||
               this == CROSS_USER;
    }
    
    /**
     * Check if this resource type contains sensitive data
     */
    public boolean isSensitive() {
        return this == USER_PROFILE ||
               this == PAYMENT_METHOD ||
               this == TRANSACTION ||
               this == TRANSACTION_DETAILS ||
               this == AUTHENTICATION_DATA ||
               this == AUDIT_LOG ||
               this == COMPLIANCE_REPORT;
    }
    
    /**
     * Check if this resource type is user-scoped (belongs to specific user)
     */
    public boolean isUserScoped() {
        return !requiresAdminAccess() && 
               this != MERCHANT_PROFILE &&
               this != MERCHANT_ANALYTICS &&
               this != MERCHANT_RISK &&
               this != ML_MODEL &&
               this != SYSTEM_CONFIG;
    }
    
    /**
     * Get default required permission level for this resource type
     */
    public ValidateOwnership.PermissionLevel getDefaultPermissionLevel() {
        if (isSensitive()) {
            return ValidateOwnership.PermissionLevel.WRITE;
        } else if (requiresAdminAccess()) {
            return ValidateOwnership.PermissionLevel.ADMIN;
        } else {
            return ValidateOwnership.PermissionLevel.READ;
        }
    }
    
    /**
     * Find resource type by code
     */
    public static ResourceType fromCode(String code) {
        for (ResourceType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown resource type code: " + code);
    }
    
    /**
     * Get all sensitive resource types
     */
    public static ResourceType[] getSensitiveTypes() {
        return java.util.Arrays.stream(values())
                .filter(ResourceType::isSensitive)
                .toArray(ResourceType[]::new);
    }
    
    /**
     * Get all user-scoped resource types
     */
    public static ResourceType[] getUserScopedTypes() {
        return java.util.Arrays.stream(values())
                .filter(ResourceType::isUserScoped)
                .toArray(ResourceType[]::new);
    }
    
    /**
     * Get all admin-only resource types
     */
    public static ResourceType[] getAdminOnlyTypes() {
        return java.util.Arrays.stream(values())
                .filter(ResourceType::requiresAdminAccess)
                .toArray(ResourceType[]::new);
    }
}