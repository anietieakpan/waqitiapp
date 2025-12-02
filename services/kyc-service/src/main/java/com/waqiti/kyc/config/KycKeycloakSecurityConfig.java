package com.waqiti.kyc.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak security configuration for KYC Service
 * Manages authentication and authorization for identity verification operations
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class KycKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain kycKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "kyc-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/kyc/public/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // KYC Verification Initiation
                .requestMatchers(HttpMethod.POST, "/api/v1/kyc/verify").hasAuthority("SCOPE_kyc:initiate")
                .requestMatchers(HttpMethod.POST, "/api/v1/kyc/*/start").hasAuthority("SCOPE_kyc:initiate")
                .requestMatchers(HttpMethod.GET, "/api/v1/kyc/requirements").permitAll()
                
                // Document Upload and Management
                .requestMatchers(HttpMethod.POST, "/api/v1/kyc/documents/upload").hasAuthority("SCOPE_kyc:document-upload")
                .requestMatchers(HttpMethod.GET, "/api/v1/kyc/documents/*").hasAuthority("SCOPE_kyc:document-read")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/kyc/documents/*").hasAuthority("SCOPE_kyc:document-delete")
                .requestMatchers(HttpMethod.PUT, "/api/v1/kyc/documents/*/verify").hasRole("KYC_VERIFIER")
                
                // Identity Verification
                .requestMatchers("/api/v1/kyc/identity/verify").hasAuthority("SCOPE_kyc:identity-verify")
                .requestMatchers("/api/v1/kyc/identity/biometric").hasAuthority("SCOPE_kyc:biometric")
                .requestMatchers("/api/v1/kyc/identity/liveness").hasAuthority("SCOPE_kyc:liveness")
                .requestMatchers("/api/v1/kyc/identity/face-match").hasAuthority("SCOPE_kyc:face-match")
                
                // Address Verification
                .requestMatchers("/api/v1/kyc/address/verify").hasAuthority("SCOPE_kyc:address-verify")
                .requestMatchers("/api/v1/kyc/address/proof").hasAuthority("SCOPE_kyc:address-proof")
                
                // KYC Status and Results
                .requestMatchers(HttpMethod.GET, "/api/v1/kyc/status/*").hasAuthority("SCOPE_kyc:status-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/kyc/result/*").hasAuthority("SCOPE_kyc:result-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/kyc/history").hasAuthority("SCOPE_kyc:history-read")
                
                // KYC Levels and Tiers
                .requestMatchers("/api/v1/kyc/levels/*").hasAuthority("SCOPE_kyc:levels")
                .requestMatchers("/api/v1/kyc/upgrade").hasAuthority("SCOPE_kyc:upgrade")
                
                // Workflow Management
                .requestMatchers("/api/v1/kyc/workflow/**").hasRole("KYC_MANAGER")
                .requestMatchers("/api/v1/kyc/workflow/*/approve").hasRole("KYC_APPROVER")
                .requestMatchers("/api/v1/kyc/workflow/*/reject").hasRole("KYC_APPROVER")
                .requestMatchers("/api/v1/kyc/workflow/*/escalate").hasRole("KYC_VERIFIER")
                
                // Risk Assessment
                .requestMatchers("/api/v1/kyc/risk/**").hasAuthority("SCOPE_kyc:risk-assess")
                .requestMatchers("/api/v1/kyc/sanctions/**").hasAuthority("SCOPE_kyc:sanctions-check")
                .requestMatchers("/api/v1/kyc/pep/**").hasAuthority("SCOPE_kyc:pep-check")
                
                // Compliance and Reporting
                .requestMatchers("/api/v1/kyc/compliance/**").hasAnyRole("COMPLIANCE_OFFICER", "KYC_ADMIN")
                .requestMatchers("/api/v1/kyc/reports/**").hasAnyRole("KYC_ADMIN", "ANALYST")
                .requestMatchers("/api/v1/kyc/audit/**").hasAnyRole("KYC_ADMIN", "AUDITOR")
                
                // Migration Endpoints
                .requestMatchers("/api/v1/kyc/migration/**").hasRole("KYC_ADMIN")
                
                // Feature Flags
                .requestMatchers("/api/v1/kyc/features/**").hasRole("KYC_ADMIN")
                
                // Admin Operations
                .requestMatchers("/api/v1/kyc/admin/**").hasRole("KYC_ADMIN")
                .requestMatchers("/api/v1/kyc/config/**").hasRole("KYC_ADMIN")
                .requestMatchers("/api/v1/kyc/templates/**").hasRole("KYC_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/kyc/**").hasRole("SERVICE")
                .requestMatchers("/internal/verification/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}