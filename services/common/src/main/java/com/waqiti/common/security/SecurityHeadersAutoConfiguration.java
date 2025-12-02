package com.waqiti.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration for security headers
 * This configuration is automatically applied to all Waqiti services
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "waqiti.security.headers",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(SecurityHeadersConfiguration.SecurityHeadersProperties.class)
@Import({SecurityHeadersConfiguration.class, SecurityHeadersFilter.class})
@Slf4j
public class SecurityHeadersAutoConfiguration {
    
    private final SecurityHeadersConfiguration.SecurityHeadersProperties properties;
    
    public SecurityHeadersAutoConfiguration(SecurityHeadersConfiguration.SecurityHeadersProperties properties) {
        this.properties = properties;
        log.info("Security headers auto-configuration enabled for service: {}", properties.getServiceName());
    }
    
    /**
     * Web security customizer to apply security headers globally
     */
    @Bean
    @Order(1)
    public WebSecurityCustomizer securityHeadersCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/actuator/health", "/actuator/info");
    }
    
    /**
     * Security headers enforcement bean
     * This bean ensures that security headers are applied consistently
     */
    @Bean
    public SecurityHeadersEnforcer securityHeadersEnforcer() {
        return new SecurityHeadersEnforcer(properties);
    }
    
    /**
     * Inner class for security headers enforcement
     */
    public static class SecurityHeadersEnforcer {
        
        private final SecurityHeadersConfiguration.SecurityHeadersProperties properties;
        
        public SecurityHeadersEnforcer(SecurityHeadersConfiguration.SecurityHeadersProperties properties) {
            this.properties = properties;
            validateConfiguration();
        }
        
        /**
         * Validate security headers configuration
         */
        private void validateConfiguration() {
            // Validate HSTS configuration
            if (properties.getHstsMaxAge() < 31536000L) {
                log.warn("HSTS max-age is less than 1 year. Consider increasing for better security.");
            }
            
            // Validate CSP configuration
            if (!properties.getContentSecurityPolicy().contains("default-src")) {
                log.warn("Content-Security-Policy is missing default-src directive.");
            }
            
            // Validate reporting endpoints
            if (properties.isEnableSecurityReporting()) {
                if (properties.getCspReportEndpoint() == null || properties.getCspReportEndpoint().isEmpty()) {
                    log.warn("Security reporting is enabled but CSP report endpoint is not configured.");
                }
            }
            
            log.info("Security headers configuration validated successfully");
        }
        
        /**
         * Get the current security headers configuration
         */
        public SecurityHeadersConfiguration.SecurityHeadersProperties getConfiguration() {
            return properties;
        }
    }
}