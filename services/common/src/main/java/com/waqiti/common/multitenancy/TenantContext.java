package com.waqiti.common.multitenancy;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe context holder for tenant information
 * Manages tenant context across different execution threads
 */
@Slf4j
public final class TenantContext {
    
    private static final ThreadLocal<TenantInfo> TENANT_HOLDER = new ThreadLocal<>();
    private static final String MDC_TENANT_ID_KEY = "tenantId";
    private static final String MDC_TENANT_NAME_KEY = "tenantName";
    private static final String MDC_TENANT_TYPE_KEY = "tenantType";
    
    // Cache for tenant configurations
    private static final Map<String, TenantConfiguration> TENANT_CONFIG_CACHE = new ConcurrentHashMap<>();
    
    private TenantContext() {
        // Utility class
    }
    
    /**
     * Set the current tenant context
     */
    public static void setTenant(TenantInfo tenant) {
        if (tenant != null) {
            TENANT_HOLDER.set(tenant);
            // Add to MDC for logging
            MDC.put(MDC_TENANT_ID_KEY, tenant.getTenantId());
            MDC.put(MDC_TENANT_NAME_KEY, tenant.getTenantName());
            MDC.put(MDC_TENANT_TYPE_KEY, tenant.getTenantType().name());
            
            log.debug("Tenant context set: {}", tenant.getTenantId());
        }
    }
    
    /**
     * Set the current tenant by ID
     */
    public static void setCurrentTenant(String tenantId) {
        if (tenantId != null) {
            TenantInfo tenantInfo = new TenantInfo(
                tenantId,
                tenantId, // Use ID as name temporarily
                TenantType.BASIC, // Default type
                tenantId, // Use ID as schema
                null // No configuration yet
            );
            setTenant(tenantInfo);
        }
    }
    
    /**
     * Get the current tenant context
     */
    public static TenantInfo getTenant() {
        return TENANT_HOLDER.get();
    }
    
    /**
     * Get the current tenant ID
     */
    public static String getTenantId() {
        TenantInfo tenant = TENANT_HOLDER.get();
        return tenant != null ? tenant.getTenantId() : null;
    }
    
    /**
     * Check if tenant context is set
     */
    public static boolean hasTenant() {
        return TENANT_HOLDER.get() != null;
    }
    
    /**
     * Clear the tenant context
     */
    public static void clear() {
        TENANT_HOLDER.remove();
        MDC.remove(MDC_TENANT_ID_KEY);
        MDC.remove(MDC_TENANT_NAME_KEY);
        MDC.remove(MDC_TENANT_TYPE_KEY);
        
        log.debug("Tenant context cleared");
    }
    
    /**
     * Execute a task with a specific tenant context
     */
    public static <T> T executeWithTenant(TenantInfo tenant, TenantTask<T> task) {
        TenantInfo previousTenant = TENANT_HOLDER.get();
        try {
            setTenant(tenant);
            return task.execute();
        } finally {
            if (previousTenant != null) {
                setTenant(previousTenant);
            } else {
                clear();
            }
        }
    }
    
    /**
     * Execute a runnable with a specific tenant context
     */
    public static void runWithTenant(TenantInfo tenant, Runnable task) {
        executeWithTenant(tenant, () -> {
            task.run();
            return null;
        });
    }
    
    /**
     * Copy tenant context for async operations
     */
    public static TenantInfo copyContext() {
        TenantInfo current = TENANT_HOLDER.get();
        if (current != null) {
            return new TenantInfo(
                current.getTenantId(),
                current.getTenantName(),
                current.getTenantType(),
                current.getDatabaseSchema(),
                current.getConfiguration()
            );
        }
        return null;
    }
    
    /**
     * Restore tenant context from a copy
     */
    public static void restoreContext(TenantInfo tenantInfo) {
        if (tenantInfo != null) {
            setTenant(tenantInfo);
        } else {
            clear();
        }
    }
    
    /**
     * Get tenant configuration from cache
     */
    public static TenantConfiguration getConfiguration(String tenantId) {
        return TENANT_CONFIG_CACHE.get(tenantId);
    }
    
    /**
     * Update tenant configuration cache
     */
    public static void updateConfiguration(String tenantId, TenantConfiguration configuration) {
        TENANT_CONFIG_CACHE.put(tenantId, configuration);
        log.info("Updated configuration for tenant: {}", tenantId);
    }
    
    /**
     * Clear configuration cache
     */
    public static void clearConfigurationCache() {
        TENANT_CONFIG_CACHE.clear();
        log.info("Tenant configuration cache cleared");
    }
    
    /**
     * Validate tenant access
     */
    public static boolean validateTenantAccess(String requestedTenantId) {
        String currentTenantId = getTenantId();
        if (currentTenantId == null) {
            log.warn("No tenant context set for validation");
            return false;
        }
        
        // Check if the current tenant has access to the requested tenant
        // This could involve checking parent-child relationships or shared access
        boolean hasAccess = currentTenantId.equals(requestedTenantId) || 
                           checkCrossTenantAccess(currentTenantId, requestedTenantId);
        
        if (!hasAccess) {
            log.warn("Tenant {} attempted to access tenant {} - Access denied", 
                currentTenantId, requestedTenantId);
        }
        
        return hasAccess;
    }
    
    private static boolean checkCrossTenantAccess(String currentTenantId, String requestedTenantId) {
        // Implementation for cross-tenant access rules
        // Could check parent-child relationships, partnerships, etc.
        return false; // Default to no cross-tenant access
    }
    
    /**
     * Functional interface for tenant-specific tasks
     */
    @FunctionalInterface
    public interface TenantTask<T> {
        T execute();
    }
    
    /**
     * Tenant information holder
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class TenantInfo {
        private final String tenantId;
        private final String tenantName;
        private final TenantType tenantType;
        private final String databaseSchema;
        private final TenantConfiguration configuration;
        
        public boolean isSystemTenant() {
            return TenantType.SYSTEM.equals(tenantType);
        }
        
        public boolean isPremiumTenant() {
            return TenantType.PREMIUM.equals(tenantType) || 
                   TenantType.ENTERPRISE.equals(tenantType);
        }
    }
    
    /**
     * Tenant types
     */
    public enum TenantType {
        SYSTEM,      // System-level tenant
        FREE,        // Free tier tenant
        BASIC,       // Basic subscription
        PREMIUM,     // Premium subscription
        ENTERPRISE,  // Enterprise subscription
        TRIAL        // Trial tenant
    }
    
    /**
     * Tenant configuration
     */
    @lombok.Data
    @lombok.Builder
    public static class TenantConfiguration {
        private String region;
        private String timezone;
        private String currency;
        private String language;
        private Map<String, Object> features;
        private Map<String, String> customSettings;
        private ResourceLimits resourceLimits;
        private SecuritySettings securitySettings;
    }
    
    /**
     * Resource limits for tenant
     */
    @lombok.Data
    @lombok.Builder
    public static class ResourceLimits {
        private long maxUsers;
        private long maxTransactions;
        private long maxStorage; // in MB
        private long maxApiCalls;
        private long maxConcurrentConnections;
    }
    
    /**
     * Security settings for tenant
     */
    @lombok.Data
    @lombok.Builder
    public static class SecuritySettings {
        private boolean mfaRequired;
        private boolean ipWhitelistEnabled;
        private int passwordExpiryDays;
        private int sessionTimeoutMinutes;
        private boolean auditLoggingEnabled;
        private String encryptionKey;
    }
}