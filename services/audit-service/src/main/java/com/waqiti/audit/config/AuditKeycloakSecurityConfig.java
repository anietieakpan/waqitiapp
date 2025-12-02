package com.waqiti.audit.config;

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
 * Keycloak security configuration for Audit Service
 * Manages authentication and authorization for audit logging and compliance tracking
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class AuditKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain auditKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "audit-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/audit/public/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Audit Log Creation (Write-Only for Services)
                .requestMatchers(HttpMethod.POST, "/api/v1/audit/logs").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/audit/events").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/audit/activities").hasRole("SERVICE")
                
                // Audit Log Reading
                .requestMatchers(HttpMethod.GET, "/api/v1/audit/logs").hasAuthority("SCOPE_audit:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/audit/logs/*").hasAuthority("SCOPE_audit:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/audit/logs/user/*").hasAuthority("SCOPE_audit:user-logs")
                .requestMatchers(HttpMethod.GET, "/api/v1/audit/logs/service/*").hasAuthority("SCOPE_audit:service-logs")
                .requestMatchers(HttpMethod.GET, "/api/v1/audit/logs/transaction/*").hasAuthority("SCOPE_audit:transaction-logs")
                
                // Audit Event Tracking
                .requestMatchers("/api/v1/audit/events/security").hasAuthority("SCOPE_audit:security-events")
                .requestMatchers("/api/v1/audit/events/compliance").hasAuthority("SCOPE_audit:compliance-events")
                .requestMatchers("/api/v1/audit/events/financial").hasAuthority("SCOPE_audit:financial-events")
                .requestMatchers("/api/v1/audit/events/administrative").hasRole("AUDIT_ADMIN")
                
                // Audit Trail Search and Query
                .requestMatchers("/api/v1/audit/search").hasAuthority("SCOPE_audit:search")
                .requestMatchers("/api/v1/audit/query").hasAuthority("SCOPE_audit:query")
                .requestMatchers("/api/v1/audit/filter").hasAuthority("SCOPE_audit:filter")
                .requestMatchers("/api/v1/audit/timeline").hasAuthority("SCOPE_audit:timeline")
                
                // Audit Reports
                .requestMatchers("/api/v1/audit/reports/generate").hasAuthority("SCOPE_audit:report-generate")
                .requestMatchers("/api/v1/audit/reports/compliance").hasAnyRole("COMPLIANCE_OFFICER", "AUDITOR")
                .requestMatchers("/api/v1/audit/reports/security").hasAnyRole("SECURITY_ADMIN", "AUDITOR")
                .requestMatchers("/api/v1/audit/reports/financial").hasAnyRole("FINANCIAL_AUDITOR", "CFO")
                .requestMatchers("/api/v1/audit/reports/export").hasAuthority("SCOPE_audit:export")
                
                // Audit Analytics
                .requestMatchers("/api/v1/audit/analytics/**").hasAuthority("SCOPE_audit:analytics")
                .requestMatchers("/api/v1/audit/metrics/**").hasAuthority("SCOPE_audit:metrics")
                .requestMatchers("/api/v1/audit/trends/**").hasAuthority("SCOPE_audit:trends")
                .requestMatchers("/api/v1/audit/anomalies/**").hasAuthority("SCOPE_audit:anomaly-detection")
                
                // Audit Retention and Archival
                .requestMatchers("/api/v1/audit/retention/**").hasRole("AUDIT_ADMIN")
                .requestMatchers("/api/v1/audit/archive/**").hasRole("AUDIT_ADMIN")
                .requestMatchers("/api/v1/audit/purge/**").hasRole("AUDIT_ADMIN")
                
                // Audit Configuration
                .requestMatchers("/api/v1/audit/config/**").hasRole("AUDIT_ADMIN")
                .requestMatchers("/api/v1/audit/rules/**").hasRole("AUDIT_ADMIN")
                .requestMatchers("/api/v1/audit/policies/**").hasRole("AUDIT_ADMIN")
                
                // Forensic Investigation
                .requestMatchers("/api/v1/audit/forensics/**").hasRole("FORENSIC_INVESTIGATOR")
                .requestMatchers("/api/v1/audit/evidence/**").hasRole("FORENSIC_INVESTIGATOR")
                
                // Compliance Verification
                .requestMatchers("/api/v1/audit/compliance/verify").hasRole("COMPLIANCE_OFFICER")
                .requestMatchers("/api/v1/audit/compliance/attest").hasRole("COMPLIANCE_OFFICER")
                
                // Admin Operations
                .requestMatchers("/api/v1/audit/admin/**").hasRole("AUDIT_ADMIN")
                .requestMatchers("/api/v1/audit/system/**").hasRole("SYSTEM_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/audit/**").hasRole("SERVICE")
                .requestMatchers("/internal/logs/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}