package com.waqiti.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CSRFSecurityConfigValidator {

    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${security.csrf.enabled:${csrf.enabled:true}}")
    private boolean csrfEnabled;
    
    @Value("${security.csrf.token-validity-seconds:${csrf.token-validity-seconds:3600}}")
    private int tokenValiditySeconds;
    
    @Value("${security.csrf.secure-cookie:${csrf.secure-cookie:true}}")
    private boolean secureCookie;
    
    @Value("${csrf.secret-key:#{null}}")
    private String secretKey;
    
    @Value("${app.domain:#{null}}")
    private String appDomain;

    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        log.info("SECURITY: Validating CSRF protection configuration...");
        
        List<String> validationWarnings = new ArrayList<>();
        List<String> validationErrors = new ArrayList<>();
        
        if (!csrfEnabled) {
            validationWarnings.add("CSRF protection is DISABLED. This is a security risk in production!");
        } else {
            log.info("SECURITY: CSRF protection is ENABLED");
            
            if (secretKey == null || secretKey.trim().isEmpty() || secretKey.startsWith("${")) {
                validationErrors.add("CSRF secret key is not configured! Set csrf.secret-key property");
            } else if (secretKey.length() < 32) {
                validationErrors.add("CSRF secret key is too short! Use at least 32 characters");
            } else {
                log.info("SECURITY: CSRF secret key is configured (length: {} characters)", secretKey.length());
            }
            
            if (appDomain == null || appDomain.trim().isEmpty() || appDomain.startsWith("${")) {
                validationWarnings.add("Application domain is not configured. Set app.domain property for proper CSRF origin validation");
            } else {
                log.info("SECURITY: Application domain configured: {}", appDomain);
            }
            
            if (!secureCookie) {
                validationWarnings.add("CSRF cookies are not marked as Secure. This is a security risk in production!");
            } else {
                log.info("SECURITY: CSRF cookies are marked as Secure");
            }
            
            if (tokenValiditySeconds < 300) {
                validationWarnings.add(String.format("CSRF token validity is very short (%ds). Consider using at least 5 minutes", tokenValiditySeconds));
            } else if (tokenValiditySeconds > 7200) {
                validationWarnings.add(String.format("CSRF token validity is very long (%ds). Consider using at most 2 hours", tokenValiditySeconds));
            } else {
                log.info("SECURITY: CSRF token validity configured: {} seconds", tokenValiditySeconds);
            }
            
            try {
                redisTemplate.getConnectionFactory().getConnection().ping();
                log.info("SECURITY: Redis connection successful for CSRF token storage");
            } catch (Exception e) {
                validationErrors.add("Redis connection failed! CSRF tokens cannot be stored: " + e.getMessage());
            }
        }
        
        if (!validationWarnings.isEmpty()) {
            log.warn("SECURITY: CSRF Configuration Warnings:");
            validationWarnings.forEach(warning -> log.warn("  - {}", warning));
        }
        
        if (!validationErrors.isEmpty()) {
            log.error("SECURITY: CSRF Configuration Errors:");
            validationErrors.forEach(error -> log.error("  - {}", error));
            throw new IllegalStateException("CSRF configuration validation failed with " + 
                    validationErrors.size() + " errors");
        }
        
        log.info("SECURITY: CSRF protection configuration validation completed successfully");
    }

    public String getConfigurationSummary() {
        return String.format(
            "CSRF Protection Status:\n" +
            "  - Enabled: %s\n" +
            "  - Token Validity: %d seconds\n" +
            "  - Secure Cookies: %s\n" +
            "  - Domain: %s\n" +
            "  - Secret Key Length: %d characters",
            csrfEnabled,
            tokenValiditySeconds,
            secureCookie,
            appDomain != null ? appDomain : "NOT SET",
            secretKey != null ? secretKey.length() : 0
        );
    }

    public boolean isProperlyConfigured() {
        if (!csrfEnabled) {
            return true;
        }
        
        boolean hasSecretKey = secretKey != null && !secretKey.trim().isEmpty() && 
                              !secretKey.startsWith("${") && secretKey.length() >= 32;
        boolean hasRedis = false;
        
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            hasRedis = true;
        } catch (Exception e) {
            log.error("SECURITY: Redis health check failed", e);
        }
        
        return hasSecretKey && hasRedis;
    }
}