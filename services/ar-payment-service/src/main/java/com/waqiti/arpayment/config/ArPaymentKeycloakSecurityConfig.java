package com.waqiti.arpayment.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * AR Payment Service Security Configuration
 * 
 * CRITICAL SECURITY: This service processes Augmented Reality payments
 * and location-based financial transactions requiring maximum security.
 * 
 * Security Features:
 * - JWT authentication with spatial verification
 * - Location-based access controls
 * - AR session security validation
 * - Biometric authentication integration
 * - Real-time fraud detection for AR payments
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ArPaymentKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Override
    protected void configureHttpSecurity(HttpSecurity http) throws Exception {
        super.configureHttpSecurity(http);
        
        http
            .authorizeHttpRequests(authz -> authz
                // Health checks
                .requestMatchers("/actuator/health/**", "/health/**").permitAll()
                
                // AR payment initiation - requires AR permission and location verification
                .requestMatchers("/api/v1/ar/payments/initiate").hasAuthority("SCOPE_ar:payment:create")
                .requestMatchers("/api/v1/ar/payments/scan").hasAuthority("SCOPE_ar:scan")
                .requestMatchers("/api/v1/ar/payments/verify-location").hasAuthority("SCOPE_ar:location")
                
                // AR session management - secure session handling
                .requestMatchers("/api/v1/ar/sessions/create").hasAuthority("SCOPE_ar:session:create")
                .requestMatchers("/api/v1/ar/sessions/*/validate").hasAuthority("SCOPE_ar:session:validate")
                .requestMatchers("/api/v1/ar/sessions/*/terminate").hasAuthority("SCOPE_ar:session:terminate")
                
                // Biometric validation for AR payments
                .requestMatchers("/api/v1/ar/biometric/verify").hasAuthority("SCOPE_ar:biometric")
                .requestMatchers("/api/v1/ar/biometric/enroll").hasAuthority("SCOPE_ar:biometric:enroll")
                
                // AR merchant interactions
                .requestMatchers("/api/v1/ar/merchants/*/interact").hasAuthority("SCOPE_ar:merchant:interact")
                .requestMatchers("/api/v1/ar/merchants/*/products").hasAuthority("SCOPE_ar:merchant:browse")
                
                // Location-based payment endpoints
                .requestMatchers("/api/v1/ar/locations/*/payments").hasAuthority("SCOPE_ar:location:pay")
                .requestMatchers("/api/v1/ar/geofence/*/validate").hasAuthority("SCOPE_ar:geofence")
                
                // AR payment history and analytics
                .requestMatchers("/api/v1/ar/history/**").hasAuthority("SCOPE_ar:history")
                .requestMatchers("/api/v1/ar/analytics/**").hasRole("AR_ADMIN")
                
                // Internal service endpoints
                .requestMatchers("/internal/**").hasAuthority("SCOPE_service:internal")
                
                // All API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            );
    }

    @Override
    protected void additionalConfiguration(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
            .addHeaderWriter((request, response) -> {
                // Prevent caching of AR payment data
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("X-Service-Type", "ar-payments");
                response.setHeader("X-Security-Level", "critical");
                response.setHeader("X-Biometric-Required", "true");
                response.setHeader("X-Location-Verification", "required");
            })
        );
        
        log.info("AR Payment Service security configuration applied - CRITICAL SECURITY ENABLED");
    }
}