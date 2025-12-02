package com.waqiti.monitoring.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Monitoring Service Security Configuration
 * 
 * HIGH SECURITY RISK: This service has access to system metrics, logs,
 * performance data, and potentially sensitive operational information.
 * 
 * Security Features:
 * - JWT authentication with role-based access control
 * - IP-based restrictions for monitoring endpoints
 * - Fine-grained permissions for different monitoring functions
 * - Audit logging for monitoring access
 * - Protection against monitoring data exposure
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class MonitoringKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Override
    protected void configureHttpSecurity(HttpSecurity http) throws Exception {
        super.configureHttpSecurity(http);
        
        http
            .authorizeHttpRequests(authz -> authz
                // Health checks - allow for monitoring systems
                .requestMatchers("/actuator/health/**", "/health/**").permitAll()
                
                // Metrics endpoints - restricted to monitoring role
                .requestMatchers("/api/v1/metrics/**").hasRole("MONITORING_ADMIN")
                .requestMatchers("/api/v1/prometheus/**").hasRole("MONITORING_ADMIN")
                
                // System monitoring endpoints - ops team access
                .requestMatchers("/api/v1/system/performance").hasAuthority("SCOPE_monitoring:system")
                .requestMatchers("/api/v1/system/resources").hasAuthority("SCOPE_monitoring:system")
                .requestMatchers("/api/v1/system/alerts").hasAuthority("SCOPE_monitoring:alerts")
                
                // Application monitoring - developers and ops
                .requestMatchers("/api/v1/applications/*/health").hasAuthority("SCOPE_monitoring:apps")
                .requestMatchers("/api/v1/applications/*/logs").hasRole("DEVELOPER")
                .requestMatchers("/api/v1/applications/*/traces").hasRole("DEVELOPER")
                
                // Security monitoring - security team access
                .requestMatchers("/api/v1/security/events").hasRole("SECURITY_ADMIN")
                .requestMatchers("/api/v1/security/threats").hasRole("SECURITY_ADMIN")
                .requestMatchers("/api/v1/security/audit").hasRole("SECURITY_ADMIN")
                
                // Business monitoring - business analysts
                .requestMatchers("/api/v1/business/kpis").hasAuthority("SCOPE_monitoring:business")
                .requestMatchers("/api/v1/business/dashboards").hasAuthority("SCOPE_monitoring:business")
                
                // Infrastructure monitoring - infrastructure team
                .requestMatchers("/api/v1/infrastructure/**").hasRole("INFRASTRUCTURE_ADMIN")
                
                // Database monitoring - DBA access
                .requestMatchers("/api/v1/database/**").hasRole("DATABASE_ADMIN")
                
                // Network monitoring - network team
                .requestMatchers("/api/v1/network/**").hasRole("NETWORK_ADMIN")
                
                // Internal service endpoints
                .requestMatchers("/internal/**").hasAuthority("SCOPE_service:internal")
                
                // Webhook endpoints for monitoring alerts
                .requestMatchers("/api/webhooks/**").permitAll() // Secured by alert manager signatures
                
                // Configuration endpoints - admin only
                .requestMatchers("/api/v1/config/**").hasRole("MONITORING_ADMIN")
                
                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                
                // Deny everything else
                .anyRequest().denyAll()
            );
    }

    @Override
    protected void additionalConfiguration(HttpSecurity http) throws Exception {
        // Add monitoring-specific security headers
        http.headers(headers -> headers
            .addHeaderWriter((request, response) -> {
                // Prevent caching of monitoring data
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
                
                // Add monitoring service identifier
                response.setHeader("X-Service-Type", "monitoring");
                response.setHeader("X-Security-Level", "high");
                
                // Monitoring-specific security headers
                response.setHeader("X-Data-Sensitivity", "operational");
                response.setHeader("X-Access-Logging", "required");
            })
        );
        
        log.info("Monitoring Service security configuration applied - HIGH SECURITY ENABLED");
    }
}