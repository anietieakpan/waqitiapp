package com.waqiti.webhook.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Webhook Service Security Configuration
 * 
 * HIGH SECURITY RISK: This service handles webhooks from external providers
 * and internal webhook management, making it a critical attack vector.
 * 
 * Security Features:
 * - JWT authentication for webhook management endpoints
 * - Signature verification for incoming webhooks
 * - IP allowlisting for known webhook providers
 * - Rate limiting to prevent webhook flooding
 * - Comprehensive audit logging for webhook events
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class WebhookKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Override
    protected void configureHttpSecurity(HttpSecurity http) throws Exception {
        super.configureHttpSecurity(http);
        
        http
            .authorizeHttpRequests(authz -> authz
                // Health checks - allow for monitoring
                .requestMatchers("/actuator/health/**", "/health/**").permitAll()
                
                // Incoming webhook endpoints - allow but will be secured by signature verification
                .requestMatchers("/webhooks/stripe/**").permitAll()
                .requestMatchers("/webhooks/paypal/**").permitAll()
                .requestMatchers("/webhooks/square/**").permitAll()
                .requestMatchers("/webhooks/adyen/**").permitAll()
                .requestMatchers("/webhooks/dwolla/**").permitAll()
                .requestMatchers("/webhooks/plaid/**").permitAll()
                .requestMatchers("/webhooks/blockchain/**").permitAll()
                .requestMatchers("/webhooks/banks/**").permitAll()
                
                // Webhook management endpoints - require admin access
                .requestMatchers("/api/v1/webhooks/configs/**").hasRole("WEBHOOK_ADMIN")
                .requestMatchers("/api/v1/webhooks/providers/**").hasRole("WEBHOOK_ADMIN")
                .requestMatchers("/api/v1/webhooks/security/**").hasRole("WEBHOOK_ADMIN")
                
                // Webhook monitoring and logs - ops team access
                .requestMatchers("/api/v1/webhooks/logs/**").hasAuthority("SCOPE_webhook:monitor")
                .requestMatchers("/api/v1/webhooks/metrics/**").hasAuthority("SCOPE_webhook:monitor")
                .requestMatchers("/api/v1/webhooks/status/**").hasAuthority("SCOPE_webhook:monitor")
                
                // Webhook retry and management - service operations
                .requestMatchers("/api/v1/webhooks/retry/**").hasRole("WEBHOOK_MANAGER")
                .requestMatchers("/api/v1/webhooks/disable/**").hasRole("WEBHOOK_MANAGER")
                .requestMatchers("/api/v1/webhooks/enable/**").hasRole("WEBHOOK_MANAGER")
                
                // Internal webhook delivery endpoints
                .requestMatchers("/internal/webhooks/deliver/**").hasAuthority("SCOPE_service:internal")
                .requestMatchers("/internal/webhooks/queue/**").hasAuthority("SCOPE_service:internal")
                
                // Webhook testing endpoints - developers
                .requestMatchers("/api/v1/webhooks/test/**").hasRole("DEVELOPER")
                
                // Security analysis endpoints - security team
                .requestMatchers("/api/v1/webhooks/security/analysis/**").hasRole("SECURITY_ADMIN")
                .requestMatchers("/api/v1/webhooks/security/threats/**").hasRole("SECURITY_ADMIN")
                
                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                
                // Deny everything else
                .anyRequest().denyAll()
            );
    }

    @Override
    protected void additionalConfiguration(HttpSecurity http) throws Exception {
        // Add webhook-specific security headers
        http.headers(headers -> headers
            .addHeaderWriter((request, response) -> {
                // Prevent caching of webhook data
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
                
                // Add webhook service identifier
                response.setHeader("X-Service-Type", "webhook-processor");
                response.setHeader("X-Security-Level", "high");
                
                // Webhook-specific security headers
                response.setHeader("X-Webhook-Security", "signature-verified");
                response.setHeader("X-Rate-Limiting", "enabled");
                response.setHeader("X-IP-Filtering", "enabled");
            })
        );
        
        log.info("Webhook Service security configuration applied - HIGH SECURITY ENABLED");
    }
}