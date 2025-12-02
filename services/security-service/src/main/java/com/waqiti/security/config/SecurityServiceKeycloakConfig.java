package com.waqiti.security.config;

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
 * Keycloak security configuration for Security Service
 * Manages authentication and authorization for security, fraud detection, and compliance operations
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class SecurityServiceKeycloakConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain securityServiceKeycloakFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "security-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/security/public/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Fraud Detection Endpoints (Service-to-Service)
                .requestMatchers(HttpMethod.POST, "/api/v1/security/fraud/check-transaction").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/fraud/advanced-check").hasRole("SERVICE")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/fraud/risk-score/*").hasAnyRole("SERVICE", "FRAUD_ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/fraud/report-suspicious").hasAnyRole("SERVICE", "FRAUD_ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/fraud/velocity-check").hasRole("SERVICE")
                
                // Fraud Analysis (Analyst Access)
                .requestMatchers(HttpMethod.GET, "/api/v1/security/fraud/cases").hasAuthority("SCOPE_security:fraud:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/fraud/cases/*").hasAuthority("SCOPE_security:fraud:read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/fraud/cases/*/status").hasAuthority("SCOPE_security:fraud:write")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/fraud/cases/*/investigate").hasAuthority("SCOPE_security:fraud:investigate")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/fraud/patterns").hasAuthority("SCOPE_security:fraud:read")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/fraud/patterns").hasAuthority("SCOPE_security:fraud:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/fraud/rules").hasRole("FRAUD_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/fraud/rules").hasRole("FRAUD_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/fraud/rules/*").hasRole("FRAUD_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/security/fraud/rules/*").hasRole("FRAUD_ADMIN")
                
                // AML (Anti-Money Laundering) Endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/security/aml/screen").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/aml/transaction-monitoring").hasRole("SERVICE")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/aml/watchlist-check/*").hasRole("SERVICE")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/aml/sanctions-check/*").hasRole("SERVICE")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/aml/cases").hasAuthority("SCOPE_security:aml:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/aml/cases/*").hasAuthority("SCOPE_security:aml:read")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/aml/reports/sar").hasRole("AML_OFFICER")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/aml/reports/ctr").hasRole("AML_OFFICER")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/aml/reports").hasRole("COMPLIANCE_OFFICER")
                
                // KYC (Know Your Customer) Endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/security/kyc/verify").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/kyc/document-verification").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/kyc/biometric-verification").hasRole("SERVICE")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/kyc/cases").hasAuthority("SCOPE_security:kyc:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/kyc/cases/*").hasAuthority("SCOPE_security:kyc:read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/kyc/cases/*/approve").hasRole("KYC_REVIEWER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/kyc/cases/*/reject").hasRole("KYC_REVIEWER")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/kyc/verification-levels").hasAuthority("SCOPE_security:kyc:read")
                
                // Risk Management Endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/security/risk/assess").hasRole("SERVICE")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/risk/profile/*").hasAuthority("SCOPE_security:risk:read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/risk/profile/*").hasAuthority("SCOPE_security:risk:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/risk/thresholds").hasRole("RISK_MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/risk/thresholds").hasRole("RISK_MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/risk/analytics").hasRole("RISK_MANAGER")
                
                // Behavioral Analysis Endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/security/behavioral/analyze").hasRole("SERVICE")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/behavioral/profile/*").hasAuthority("SCOPE_security:behavioral:read")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/behavioral/baseline/*").hasAuthority("SCOPE_security:behavioral:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/behavioral/anomalies").hasRole("SECURITY_ANALYST")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/behavioral/patterns").hasRole("SECURITY_ANALYST")
                
                // Device Security Endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/security/device/fingerprint").hasRole("SERVICE")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/device/trust-score/*").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/device/register").hasRole("SERVICE")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/device/*/trust").hasAuthority("SCOPE_security:device:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/device/analytics").hasRole("SECURITY_ANALYST")
                
                // Geolocation & IP Security
                .requestMatchers(HttpMethod.POST, "/api/v1/security/geo/risk-check").hasRole("SERVICE")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/geo/blocked-countries").hasAuthority("SCOPE_security:geolocation:read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/geo/blocked-countries").hasRole("SECURITY_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/geo/risk-countries").hasAuthority("SCOPE_security:geolocation:read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/geo/risk-countries").hasRole("SECURITY_ADMIN")
                
                // Security Monitoring & Alerts
                .requestMatchers(HttpMethod.GET, "/api/v1/security/monitoring/alerts").hasAuthority("SCOPE_security:alert:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/monitoring/alerts/*").hasAuthority("SCOPE_security:alert:read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/monitoring/alerts/*/acknowledge").hasAuthority("SCOPE_security:alert:write")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/monitoring/alerts/*/resolve").hasAuthority("SCOPE_security:alert:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/monitoring/dashboard").hasRole("SECURITY_ANALYST")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/monitoring/metrics").hasRole("SECURITY_ANALYST")
                
                // Compliance & Audit Endpoints
                .requestMatchers(HttpMethod.GET, "/api/v1/security/compliance/reports").hasRole("COMPLIANCE_OFFICER")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/compliance/reports/generate").hasRole("COMPLIANCE_OFFICER")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/audit/logs").hasRole("AUDITOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/audit/trail/*").hasRole("AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/audit/export").hasRole("AUDITOR")
                
                // Configuration Management (Admin Only)
                .requestMatchers("/api/v1/security/config/**").hasRole("SECURITY_ADMIN")
                .requestMatchers("/api/v1/security/admin/users").hasRole("SECURITY_ADMIN")
                .requestMatchers("/api/v1/security/admin/roles").hasRole("SECURITY_ADMIN")
                .requestMatchers("/api/v1/security/admin/permissions").hasRole("SECURITY_ADMIN")
                .requestMatchers("/api/v1/security/admin/system/**").hasRole("SYSTEM_ADMIN")
                
                // Incident Response
                .requestMatchers(HttpMethod.POST, "/api/v1/security/incidents").hasRole("SECURITY_ANALYST")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/incidents").hasRole("SECURITY_ANALYST")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/incidents/*").hasRole("SECURITY_ANALYST")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/incidents/*/escalate").hasRole("SECURITY_MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/security/incidents/*/close").hasRole("SECURITY_ANALYST")
                
                // Threat Intelligence
                .requestMatchers(HttpMethod.GET, "/api/v1/security/threats/intelligence").hasRole("SECURITY_ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/security/threats/ioc").hasRole("SECURITY_ANALYST")
                .requestMatchers(HttpMethod.GET, "/api/v1/security/threats/feeds").hasRole("SECURITY_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/security/**").hasRole("SERVICE")
                .requestMatchers("/internal/fraud/**").hasRole("SERVICE")
                .requestMatchers("/internal/aml/**").hasRole("SERVICE")
                .requestMatchers("/internal/kyc/**").hasRole("SERVICE")
                .requestMatchers("/internal/risk/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}