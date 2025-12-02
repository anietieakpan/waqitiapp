package com.waqiti.bankintegration.domain;

/**
 * Provider Health Status Enumeration
 * 
 * Represents the current health status of a payment provider
 * based on monitoring and health checks.
 */
public enum ProviderHealthStatus {
    
    /**
     * Provider is fully operational
     */
    HEALTHY,
    
    /**
     * Provider is experiencing minor issues but still functional
     */
    DEGRADED,
    
    /**
     * Provider is experiencing significant issues
     */
    UNHEALTHY,
    
    /**
     * Provider is completely unavailable
     */
    DOWN,
    
    /**
     * Provider is under maintenance
     */
    MAINTENANCE,
    
    /**
     * Health status is unknown (not yet checked)
     */
    UNKNOWN
}