/**
 * SECURITY ENHANCEMENT: Cache Validation Configuration
 * Enables cache integrity validation and security features
 */
package com.waqiti.frauddetection.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for fraud detection cache validation and integrity checks
 */
@Configuration
@EnableCaching
@EnableScheduling
@EnableAspectJAutoProxy
@Slf4j
public class CacheValidationConfig {
    
    @Value("${fraud.cache.validation.enabled:true}")
    private boolean validationEnabled;
    
    @Value("${fraud.cache.validation.integrity-check-interval:300000}")
    private long integrityCheckInterval;
    
    @Value("${fraud.cache.validation.scheduled-check-enabled:true}")
    private boolean scheduledCheckEnabled;
    
    /**
     * Get validation enabled status
     */
    public boolean isValidationEnabled() {
        return validationEnabled;
    }
    
    /**
     * Get integrity check interval
     */
    public long getIntegrityCheckInterval() {
        return integrityCheckInterval;
    }
    
    /**
     * Get scheduled check enabled status
     */
    public boolean isScheduledCheckEnabled() {
        return scheduledCheckEnabled;
    }
}