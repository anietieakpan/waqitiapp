package com.waqiti.frauddetection.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Fraud Detection Service Security Configuration
 * 
 * CRITICAL SECURITY SERVICE: This service processes sensitive fraud detection
 * algorithms and risk scoring. It requires maximum security protection.
 * 
 * Security Features:
 * - JWT authentication with Keycloak integration
 * - Fine-grained authorization for fraud detection operations
 * - Audit logging for all fraud detection activities
 * - Rate limiting to prevent abuse of fraud detection APIs
 * - IP allowlisting for internal service communications
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class FraudDetectionKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Override
    protected void configureHttpSecurity(HttpSecurity http) throws Exception {
        super.configureHttpSecurity(http);
        
        http
            .authorizeHttpRequests(authz -> authz
                // Health checks - allow for monitoring
                .requestMatchers("/actuator/health/**", "/health/**").permitAll()

                // Sanctions screening endpoints - critical compliance operations
                .requestMatchers("/api/v1/sanctions/screen/**").hasAuthority("SCOPE_sanctions:screen")
                .requestMatchers("/api/v1/sanctions/resolve/**").hasRole("COMPLIANCE_OFFICER")
                .requestMatchers("/api/v1/sanctions/admin/**").hasRole("COMPLIANCE_MANAGER")
                .requestMatchers("/api/v1/sanctions/statistics/**").hasRole("COMPLIANCE_OFFICER")
                .requestMatchers("/api/v1/sanctions/**").hasAuthority("SCOPE_sanctions:read")

                // Fraud detection endpoints - require specific authorities
                .requestMatchers("/api/v1/fraud/detect/**").hasAuthority("SCOPE_fraud:detect")
                .requestMatchers("/api/v1/fraud/analyze/**").hasAuthority("SCOPE_fraud:analyze")
                .requestMatchers("/api/v1/fraud/rules/**").hasAuthority("SCOPE_fraud:admin")
                .requestMatchers("/api/v1/fraud/models/**").hasAuthority("SCOPE_fraud:admin")

                // Internal service-to-service endpoints
                .requestMatchers("/internal/**").hasAuthority("SCOPE_service:internal")

                // Machine learning model endpoints - admin only
                .requestMatchers("/api/v1/ml/**").hasRole("FRAUD_ADMIN")

                // Risk scoring - requires special permission
                .requestMatchers("/api/v1/risk/**").hasAuthority("SCOPE_risk:score")

                // Webhook endpoints for external fraud providers
                .requestMatchers("/api/webhooks/**").permitAll() // Will be secured by signature verification

                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()

                // Deny everything else
                .anyRequest().denyAll()
            );
    }

    @Override
    protected void additionalConfiguration(HttpSecurity http) throws Exception {
        // Add fraud-detection specific security headers
        http.headers(headers -> headers
            .addHeaderWriter((request, response) -> {
                // Prevent caching of fraud detection results
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
                
                // Add fraud detection service identifier
                response.setHeader("X-Service-Type", "fraud-detection");
                response.setHeader("X-Security-Level", "critical");
            })
        );
        
        log.info("Fraud Detection Service security configuration applied - CRITICAL SECURITY ENABLED");
    }
}