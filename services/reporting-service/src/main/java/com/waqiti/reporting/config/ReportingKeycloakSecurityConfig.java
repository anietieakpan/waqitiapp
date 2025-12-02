package com.waqiti.reporting.config;

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
 * Keycloak security configuration for Reporting Service
 * Manages authentication and authorization for reporting and document generation operations
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class ReportingKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain reportingKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Reporting Service");
        
        return createKeycloakSecurityFilterChain(http, "reporting-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/reports/public/**").permitAll()
                
                // Report Generation
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/generate").hasAuthority("SCOPE_reporting:generate")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/generate/async").hasAuthority("SCOPE_reporting:generate-async")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/status/*").hasAuthority("SCOPE_reporting:status")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/cancel/*").hasAuthority("SCOPE_reporting:cancel")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/retry/*").hasAuthority("SCOPE_reporting:retry")
                
                // Report Templates
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/templates").hasAuthority("SCOPE_reporting:template-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/templates/*").hasAuthority("SCOPE_reporting:template-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/templates").hasAuthority("SCOPE_reporting:template-create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/reports/templates/*").hasAuthority("SCOPE_reporting:template-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/reports/templates/*").hasAuthority("SCOPE_reporting:template-delete")
                
                // Financial Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/financial/statement").hasAuthority("SCOPE_reporting:financial-statement")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/financial/balance-sheet").hasAuthority("SCOPE_reporting:balance-sheet")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/financial/income-statement").hasAuthority("SCOPE_reporting:income-statement")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/financial/cash-flow").hasAuthority("SCOPE_reporting:cash-flow")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/financial/trial-balance").hasAuthority("SCOPE_reporting:trial-balance")
                
                // Transaction Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/transactions").hasAuthority("SCOPE_reporting:transaction-report")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/transactions/summary").hasAuthority("SCOPE_reporting:transaction-summary")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/transactions/detailed").hasAuthority("SCOPE_reporting:transaction-detailed")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/transactions/export").hasAuthority("SCOPE_reporting:transaction-export")
                
                // Account Statements
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/statements/account").hasAuthority("SCOPE_reporting:account-statement")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/statements/mini").hasAuthority("SCOPE_reporting:mini-statement")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/statements/detailed").hasAuthority("SCOPE_reporting:detailed-statement")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/statements/email").hasAuthority("SCOPE_reporting:statement-email")
                
                // Regulatory Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/regulatory/aml").hasAuthority("SCOPE_reporting:regulatory-aml")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/regulatory/kyc").hasAuthority("SCOPE_reporting:regulatory-kyc")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/regulatory/ctr").hasAuthority("SCOPE_reporting:regulatory-ctr")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/regulatory/sar").hasAuthority("SCOPE_reporting:regulatory-sar")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/regulatory/submit").hasAuthority("SCOPE_reporting:regulatory-submit")
                
                // Compliance Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/compliance/audit").hasAuthority("SCOPE_reporting:compliance-audit")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/compliance/risk").hasAuthority("SCOPE_reporting:compliance-risk")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/compliance/violations").hasAuthority("SCOPE_reporting:compliance-violations")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/compliance/monitoring").hasAuthority("SCOPE_reporting:compliance-monitoring")
                
                // Analytics Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/analytics/dashboard").hasAuthority("SCOPE_reporting:analytics-dashboard")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/analytics/trends").hasAuthority("SCOPE_reporting:analytics-trends")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/analytics/insights").hasAuthority("SCOPE_reporting:analytics-insights")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/analytics/forecast").hasAuthority("SCOPE_reporting:analytics-forecast")
                
                // Custom Reports
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/custom/query").hasAuthority("SCOPE_reporting:custom-query")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/custom/saved").hasAuthority("SCOPE_reporting:custom-saved")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/custom/save").hasAuthority("SCOPE_reporting:custom-save")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/reports/custom/*").hasAuthority("SCOPE_reporting:custom-delete")
                
                // Report Scheduling
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/schedules").hasAuthority("SCOPE_reporting:schedule-list")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/schedules").hasAuthority("SCOPE_reporting:schedule-create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/reports/schedules/*").hasAuthority("SCOPE_reporting:schedule-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/reports/schedules/*").hasAuthority("SCOPE_reporting:schedule-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/schedules/*/pause").hasAuthority("SCOPE_reporting:schedule-pause")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/schedules/*/resume").hasAuthority("SCOPE_reporting:schedule-resume")
                
                // Report Distribution
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/distribute/email").hasAuthority("SCOPE_reporting:distribute-email")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/distribute/webhook").hasAuthority("SCOPE_reporting:distribute-webhook")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/distribute/sftp").hasAuthority("SCOPE_reporting:distribute-sftp")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/distribution/history").hasAuthority("SCOPE_reporting:distribution-history")
                
                // Report Downloads
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/download/*").hasAuthority("SCOPE_reporting:download")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/preview/*").hasAuthority("SCOPE_reporting:preview")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/batch-download").hasAuthority("SCOPE_reporting:batch-download")
                
                // Report History
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/history").hasAuthority("SCOPE_reporting:history-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/history/*").hasAuthority("SCOPE_reporting:history-details")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/reports/history/*").hasAuthority("SCOPE_reporting:history-delete")
                
                // MIS Reports (Management Information System)
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/mis/executive").hasAuthority("SCOPE_reporting:mis-executive")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/mis/operational").hasAuthority("SCOPE_reporting:mis-operational")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/mis/strategic").hasAuthority("SCOPE_reporting:mis-strategic")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/mis/tactical").hasAuthority("SCOPE_reporting:mis-tactical")
                
                // Tax Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/tax/summary").hasAuthority("SCOPE_reporting:tax-summary")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/tax/detailed").hasAuthority("SCOPE_reporting:tax-detailed")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/tax/withholding").hasAuthority("SCOPE_reporting:tax-withholding")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/tax/file").hasAuthority("SCOPE_reporting:tax-file")
                
                // Audit Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/audit/trail").hasAuthority("SCOPE_reporting:audit-trail")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/audit/user-activity").hasAuthority("SCOPE_reporting:audit-user-activity")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/audit/system-logs").hasAuthority("SCOPE_reporting:audit-system-logs")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/audit/security").hasAuthority("SCOPE_reporting:audit-security")
                
                // Reconciliation Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/reconciliation/bank").hasAuthority("SCOPE_reporting:recon-bank")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/reconciliation/internal").hasAuthority("SCOPE_reporting:recon-internal")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/reconciliation/discrepancies").hasAuthority("SCOPE_reporting:recon-discrepancies")
                
                // Performance Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/performance/kpi").hasAuthority("SCOPE_reporting:performance-kpi")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/performance/metrics").hasAuthority("SCOPE_reporting:performance-metrics")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/performance/benchmarks").hasAuthority("SCOPE_reporting:performance-benchmarks")
                
                // Admin Operations
                .requestMatchers("/api/v1/reports/admin/**").hasRole("REPORTING_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/admin/queue").hasRole("REPORTING_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/admin/purge").hasRole("REPORTING_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/reports/admin/config").hasRole("REPORTING_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/reports/**").hasRole("SERVICE")
                .requestMatchers("/internal/reporting/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}