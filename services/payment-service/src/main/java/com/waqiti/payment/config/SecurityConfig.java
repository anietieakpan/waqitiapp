/**
 * SECURITY ENHANCEMENT: Payment Service Security Configuration
 * Configures error message sanitization and security features
 */
package com.waqiti.payment.config;

import com.waqiti.payment.security.ErrorMessageSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Security configuration for payment service including error message sanitization
 */
@Configuration
@EnableScheduling
@Slf4j
public class SecurityConfig {
    
    @Value("${payment.security.error-sanitization.enabled:true}")
    private boolean errorSanitizationEnabled;
    
    @Value("${payment.security.error-sanitization.cleanup-interval:3600000}")
    private long cleanupInterval;
    
    /**
     * Error message sanitizer bean
     */
    @Bean
    public ErrorMessageSanitizer errorMessageSanitizer() {
        log.info("SECURITY: Configuring error message sanitizer - Enabled: {}", errorSanitizationEnabled);
        return new ErrorMessageSanitizer();
    }
    
    /**
     * Scheduled cleanup of old error tracking entries
     * Runs every hour by default to prevent memory leaks
     */
    @Scheduled(fixedRateString = "${payment.security.error-sanitization.cleanup-interval:3600000}")
    public void cleanupErrorTracking() {
        if (errorSanitizationEnabled) {
            try {
                ErrorMessageSanitizer sanitizer = errorMessageSanitizer();
                sanitizer.cleanupOldEntries();
                log.debug("SECURITY: Completed scheduled error tracking cleanup");
            } catch (Exception e) {
                log.error("SECURITY: Error during scheduled error tracking cleanup", e);
            }
        }
    }
    
    /**
     * Get security configuration status
     */
    public boolean isErrorSanitizationEnabled() {
        return errorSanitizationEnabled;
    }
    
    /**
     * Get cleanup interval
     */
    public long getCleanupInterval() {
        return cleanupInterval;
    }
}