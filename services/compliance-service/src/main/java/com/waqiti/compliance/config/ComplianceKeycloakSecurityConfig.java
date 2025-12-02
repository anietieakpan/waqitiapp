package com.waqiti.compliance.config;

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
 * Keycloak security configuration for Compliance Service
 * Manages authentication and authorization for AML/KYC compliance operations
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class ComplianceKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain complianceKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "compliance-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/compliance/public/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // AML Screening
                .requestMatchers(HttpMethod.POST, "/api/v1/compliance/aml/screen").hasAuthority("SCOPE_compliance:aml-screen")
                .requestMatchers(HttpMethod.GET, "/api/v1/compliance/aml/status/*").hasAuthority("SCOPE_compliance:aml-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/compliance/aml/verify").hasAuthority("SCOPE_compliance:aml-verify")
                .requestMatchers("/api/v1/compliance/aml/monitoring/**").hasAuthority("SCOPE_compliance:aml-monitor")
                
                // Sanctions Screening
                .requestMatchers("/api/v1/compliance/sanctions/check").hasAuthority("SCOPE_compliance:sanctions-check")
                .requestMatchers("/api/v1/compliance/sanctions/lists").hasAuthority("SCOPE_compliance:sanctions-read")
                .requestMatchers("/api/v1/compliance/sanctions/update").hasRole("COMPLIANCE_ADMIN")
                
                // PEP (Politically Exposed Person) Screening
                .requestMatchers("/api/v1/compliance/pep/check").hasAuthority("SCOPE_compliance:pep-check")
                .requestMatchers("/api/v1/compliance/pep/database").hasAuthority("SCOPE_compliance:pep-read")
                .requestMatchers("/api/v1/compliance/pep/update").hasRole("COMPLIANCE_ADMIN")
                
                // Transaction Monitoring
                .requestMatchers("/api/v1/compliance/transactions/monitor").hasAuthority("SCOPE_compliance:transaction-monitor")
                .requestMatchers("/api/v1/compliance/transactions/suspicious").hasAuthority("SCOPE_compliance:suspicious-activity")
                .requestMatchers("/api/v1/compliance/transactions/threshold").hasAuthority("SCOPE_compliance:threshold-check")
                .requestMatchers("/api/v1/compliance/transactions/patterns").hasAuthority("SCOPE_compliance:pattern-analysis")
                
                // Risk Assessment
                .requestMatchers("/api/v1/compliance/risk/assess").hasAuthority("SCOPE_compliance:risk-assess")
                .requestMatchers("/api/v1/compliance/risk/score").hasAuthority("SCOPE_compliance:risk-score")
                .requestMatchers("/api/v1/compliance/risk/profile").hasAuthority("SCOPE_compliance:risk-profile")
                .requestMatchers("/api/v1/compliance/risk/matrix").hasRole("COMPLIANCE_OFFICER")
                
                // Regulatory Reporting
                .requestMatchers("/api/v1/compliance/reports/sar").hasRole("COMPLIANCE_OFFICER")
                .requestMatchers("/api/v1/compliance/reports/ctr").hasRole("COMPLIANCE_OFFICER")
                .requestMatchers("/api/v1/compliance/reports/regulatory").hasRole("COMPLIANCE_OFFICER")
                .requestMatchers("/api/v1/compliance/reports/generate").hasAnyRole("COMPLIANCE_OFFICER", "COMPLIANCE_ADMIN")
                .requestMatchers("/api/v1/compliance/reports/submit").hasRole("COMPLIANCE_ADMIN")
                
                // Case Management
                .requestMatchers("/api/v1/compliance/cases/create").hasAuthority("SCOPE_compliance:case-create")
                .requestMatchers("/api/v1/compliance/cases/*/investigate").hasRole("COMPLIANCE_INVESTIGATOR")
                .requestMatchers("/api/v1/compliance/cases/*/escalate").hasRole("COMPLIANCE_INVESTIGATOR")
                .requestMatchers("/api/v1/compliance/cases/*/close").hasRole("COMPLIANCE_MANAGER")
                .requestMatchers("/api/v1/compliance/cases/*/review").hasRole("COMPLIANCE_REVIEWER")
                
                // Compliance Rules Engine
                .requestMatchers(HttpMethod.GET, "/api/v1/compliance/rules").hasAuthority("SCOPE_compliance:rules-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/compliance/rules").hasRole("COMPLIANCE_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/compliance/rules/*").hasRole("COMPLIANCE_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/compliance/rules/*").hasRole("COMPLIANCE_ADMIN")
                
                // Audit Trail
                .requestMatchers("/api/v1/compliance/audit/**").hasAnyRole("COMPLIANCE_OFFICER", "AUDITOR")
                
                // Compliance Dashboard
                .requestMatchers("/api/v1/compliance/dashboard/**").hasAnyRole("COMPLIANCE_OFFICER", "COMPLIANCE_MANAGER")
                .requestMatchers("/api/v1/compliance/metrics/**").hasAnyRole("COMPLIANCE_OFFICER", "ANALYST")
                
                // Admin Operations
                .requestMatchers("/api/v1/compliance/admin/**").hasRole("COMPLIANCE_ADMIN")
                .requestMatchers("/api/v1/compliance/config/**").hasRole("COMPLIANCE_ADMIN")
                .requestMatchers("/api/v1/compliance/thresholds/**").hasRole("COMPLIANCE_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/compliance/**").hasRole("SERVICE")
                .requestMatchers("/internal/screening/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}