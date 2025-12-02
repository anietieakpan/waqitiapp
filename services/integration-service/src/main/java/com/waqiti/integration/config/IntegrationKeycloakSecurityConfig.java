package com.waqiti.integration.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak security configuration for Integration Service
 * Manages authentication and authorization for third-party integrations and API management
 * Critical service handling external system connections and data synchronization
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class IntegrationKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain integrationKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Integration Service");
        
        return createKeycloakSecurityFilterChain(http, "integration-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Very limited
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/integrations/public/status").permitAll()
                
                // API Key Management
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/api-keys/generate").hasAuthority("SCOPE_integration:api-key-generate")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/api-keys").hasAuthority("SCOPE_integration:api-keys-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/api-keys/*/regenerate").hasAuthority("SCOPE_integration:api-key-regenerate")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/integrations/api-keys/*").hasAuthority("SCOPE_integration:api-key-delete")
                .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/api-keys/*/activate").hasAuthority("SCOPE_integration:api-key-activate")
                .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/api-keys/*/deactivate").hasAuthority("SCOPE_integration:api-key-deactivate")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/api-keys/*/usage").hasAuthority("SCOPE_integration:api-key-usage")
                
                // Webhook Management
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/webhooks/register").hasAuthority("SCOPE_integration:webhook-register")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/webhooks").hasAuthority("SCOPE_integration:webhooks-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/webhooks/*").hasAuthority("SCOPE_integration:webhook-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/integrations/webhooks/*").hasAuthority("SCOPE_integration:webhook-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/webhooks/*/test").hasAuthority("SCOPE_integration:webhook-test")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/webhooks/*/logs").hasAuthority("SCOPE_integration:webhook-logs")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/webhooks/*/retry").hasAuthority("SCOPE_integration:webhook-retry")
                
                // Third-Party Service Integration Management
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/services/connect").hasAuthority("SCOPE_integration:service-connect")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/services").hasAuthority("SCOPE_integration:services-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/services/*/configure").hasAuthority("SCOPE_integration:service-configure")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/integrations/services/*/disconnect").hasAuthority("SCOPE_integration:service-disconnect")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/services/*/status").hasAuthority("SCOPE_integration:service-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/services/*/sync").hasAuthority("SCOPE_integration:service-sync")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/services/*/test-connection").hasAuthority("SCOPE_integration:service-test")
                
                // Data Mapping & Transformation
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/mappings/create").hasAuthority("SCOPE_integration:mapping-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/mappings").hasAuthority("SCOPE_integration:mappings-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/mappings/*").hasAuthority("SCOPE_integration:mapping-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/integrations/mappings/*").hasAuthority("SCOPE_integration:mapping-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/mappings/*/test").hasAuthority("SCOPE_integration:mapping-test")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/transformations").hasAuthority("SCOPE_integration:transformations-view")
                
                // Sync & Data Flow Management
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/sync/trigger").hasAuthority("SCOPE_integration:sync-trigger")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/sync/status").hasAuthority("SCOPE_integration:sync-status")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/sync/history").hasAuthority("SCOPE_integration:sync-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/sync/*/pause").hasAuthority("SCOPE_integration:sync-pause")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/sync/*/resume").hasAuthority("SCOPE_integration:sync-resume")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/sync/*/cancel").hasAuthority("SCOPE_integration:sync-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/sync/conflicts").hasAuthority("SCOPE_integration:sync-conflicts")
                
                // API Gateway & Rate Limiting
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/gateway/routes").hasAuthority("SCOPE_integration:gateway-routes")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/gateway/routes").hasAuthority("SCOPE_integration:gateway-route-create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/gateway/routes/*").hasAuthority("SCOPE_integration:gateway-route-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/integrations/gateway/routes/*").hasAuthority("SCOPE_integration:gateway-route-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/gateway/rate-limits").hasAuthority("SCOPE_integration:rate-limits-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/gateway/rate-limits").hasAuthority("SCOPE_integration:rate-limits-set")
                
                // OAuth & Authentication Management
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/oauth/applications").hasAuthority("SCOPE_integration:oauth-app-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/oauth/applications").hasAuthority("SCOPE_integration:oauth-apps-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/oauth/applications/*").hasAuthority("SCOPE_integration:oauth-app-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/integrations/oauth/applications/*").hasAuthority("SCOPE_integration:oauth-app-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/oauth/tokens/refresh").hasAuthority("SCOPE_integration:oauth-token-refresh")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/oauth/scopes").hasAuthority("SCOPE_integration:oauth-scopes-view")
                
                // API Documentation & Schema Management
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/docs/apis").hasAuthority("SCOPE_integration:api-docs-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/docs/generate").hasAuthority("SCOPE_integration:api-docs-generate")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/schemas").hasAuthority("SCOPE_integration:schemas-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/schemas/validate").hasAuthority("SCOPE_integration:schema-validate")
                .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/schemas/*").hasAuthority("SCOPE_integration:schema-update")
                
                // Event & Message Queue Management
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/events").hasAuthority("SCOPE_integration:events-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/events/publish").hasAuthority("SCOPE_integration:event-publish")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/queues").hasAuthority("SCOPE_integration:queues-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/queues/*/purge").hasAuthority("SCOPE_integration:queue-purge")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/dead-letter-queue").hasAuthority("SCOPE_integration:dlq-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/dead-letter-queue/*/requeue").hasAuthority("SCOPE_integration:dlq-requeue")
                
                // Monitoring & Analytics
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/monitoring/dashboard").hasAuthority("SCOPE_integration:monitoring-dashboard")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/monitoring/metrics").hasAuthority("SCOPE_integration:monitoring-metrics")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/monitoring/health-checks").hasAuthority("SCOPE_integration:health-checks")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/monitoring/alerts").hasAuthority("SCOPE_integration:alert-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/analytics/api-usage").hasAuthority("SCOPE_integration:analytics-api-usage")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/analytics/performance").hasAuthority("SCOPE_integration:analytics-performance")
                
                // Error Handling & Logging
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/errors/logs").hasAuthority("SCOPE_integration:error-logs")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/errors/*/details").hasAuthority("SCOPE_integration:error-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/errors/*/resolve").hasAuthority("SCOPE_integration:error-resolve")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/audit/trail").hasAuthority("SCOPE_integration:audit-trail")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/logs/search").hasAuthority("SCOPE_integration:logs-search")
                
                // Batch Operations & Bulk Processing
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/batch/jobs").hasAuthority("SCOPE_integration:batch-job-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/batch/jobs").hasAuthority("SCOPE_integration:batch-jobs-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/batch/jobs/*/status").hasAuthority("SCOPE_integration:batch-job-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/batch/jobs/*/cancel").hasAuthority("SCOPE_integration:batch-job-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/batch/schedules").hasAuthority("SCOPE_integration:batch-schedules")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/bulk/import").hasAuthority("SCOPE_integration:bulk-import")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/bulk/export").hasAuthority("SCOPE_integration:bulk-export")
                
                // Security & Compliance
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/security/audit").hasAuthority("SCOPE_integration:security-audit")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/security/scan").hasAuthority("SCOPE_integration:security-scan")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/compliance/report").hasAuthority("SCOPE_integration:compliance-report")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/encryption/rotate-keys").hasAuthority("SCOPE_integration:encryption-rotate")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/certificates").hasAuthority("SCOPE_integration:certificates-view")
                
                // Template & Configuration Management
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/templates").hasAuthority("SCOPE_integration:templates-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/templates").hasAuthority("SCOPE_integration:template-create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/templates/*").hasAuthority("SCOPE_integration:template-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/integrations/templates/*").hasAuthority("SCOPE_integration:template-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/configurations").hasAuthority("SCOPE_integration:configurations-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/configurations").hasAuthority("SCOPE_integration:configurations-update")
                
                // Environment & Deployment Management
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/environments").hasAuthority("SCOPE_integration:environments-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/environments/*/deploy").hasAuthority("SCOPE_integration:environment-deploy")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/environments/*/rollback").hasAuthority("SCOPE_integration:environment-rollback")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/deployments/history").hasAuthority("SCOPE_integration:deployment-history")
                
                // Admin Operations - System Management
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/admin/system/status").hasRole("INTEGRATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/admin/system/maintenance").hasRole("INTEGRATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/admin/cache/clear").hasRole("INTEGRATION_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/admin/performance/report").hasRole("INTEGRATION_ADMIN")
                
                // Admin Operations - User & Access Management
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/admin/users/api-usage").hasRole("INTEGRATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/admin/users/*/suspend").hasRole("INTEGRATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/admin/api-keys/bulk/revoke").hasRole("INTEGRATION_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/admin/audit/full-report").hasRole("INTEGRATION_ADMIN")
                
                // Admin Operations - Integration Management
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/admin/services/*/force-sync").hasRole("INTEGRATION_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/admin/services/health-summary").hasRole("INTEGRATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/admin/webhooks/*/force-retry").hasRole("INTEGRATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/admin/bulk-operations").hasRole("INTEGRATION_ADMIN")
                
                // Admin Operations - General Management
                .requestMatchers("/api/v1/integrations/admin/**").hasRole("INTEGRATION_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/admin/audit/logs").hasRole("AUDITOR")
                
                // Webhook endpoints - External services
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/webhooks/external/*").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/callbacks/*").hasRole("SERVICE")
                
                // High-Security Internal service-to-service endpoints
                .requestMatchers("/internal/integrations/**").hasRole("SERVICE")
                .requestMatchers("/internal/api-gateway/**").hasRole("SERVICE")
                .requestMatchers("/internal/sync/**").hasRole("SERVICE")
                .requestMatchers("/internal/webhooks/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}