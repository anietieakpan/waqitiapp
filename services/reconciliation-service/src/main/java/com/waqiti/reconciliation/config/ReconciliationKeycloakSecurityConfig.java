package com.waqiti.reconciliation.config;

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
 * Keycloak security configuration for Reconciliation Service
 * Manages authentication and authorization for financial reconciliation operations
 * Critical for maintaining financial accuracy and regulatory compliance
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class ReconciliationKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain reconciliationKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Reconciliation Service");
        
        return createKeycloakSecurityFilterChain(http, "reconciliation-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/reconciliation/public/status").permitAll()
                
                // Reconciliation Process Management
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/process/start").hasAuthority("SCOPE_reconciliation:process-start")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/process").hasAuthority("SCOPE_reconciliation:process-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/process/*").hasAuthority("SCOPE_reconciliation:process-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/process/*/pause").hasAuthority("SCOPE_reconciliation:process-pause")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/process/*/resume").hasAuthority("SCOPE_reconciliation:process-resume")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/process/*/cancel").hasAuthority("SCOPE_reconciliation:process-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/process/*/progress").hasAuthority("SCOPE_reconciliation:process-progress")
                
                // Data Import & Upload
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/import/bank-statement").hasAuthority("SCOPE_reconciliation:import-bank-statement")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/import/transactions").hasAuthority("SCOPE_reconciliation:import-transactions")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/import/ledger-entries").hasAuthority("SCOPE_reconciliation:import-ledger")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/import/history").hasAuthority("SCOPE_reconciliation:import-history")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/import/*/status").hasAuthority("SCOPE_reconciliation:import-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/import/validate").hasAuthority("SCOPE_reconciliation:import-validate")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/import/*/retry").hasAuthority("SCOPE_reconciliation:import-retry")
                
                // Matching & Pairing Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/matching/auto-match").hasAuthority("SCOPE_reconciliation:auto-match")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/matching/manual-match").hasAuthority("SCOPE_reconciliation:manual-match")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/matching/suggestions").hasAuthority("SCOPE_reconciliation:match-suggestions")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/matching/*/confirm").hasAuthority("SCOPE_reconciliation:match-confirm")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/matching/*/reject").hasAuthority("SCOPE_reconciliation:match-reject")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/matching/unmatched").hasAuthority("SCOPE_reconciliation:unmatched-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/matching/bulk-match").hasAuthority("SCOPE_reconciliation:bulk-match")
                
                // Discrepancy Management
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/discrepancies").hasAuthority("SCOPE_reconciliation:discrepancies-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/discrepancies/*").hasAuthority("SCOPE_reconciliation:discrepancy-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/discrepancies/*/resolve").hasAuthority("SCOPE_reconciliation:discrepancy-resolve")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/discrepancies/*/escalate").hasAuthority("SCOPE_reconciliation:discrepancy-escalate")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/discrepancies/*/comment").hasAuthority("SCOPE_reconciliation:discrepancy-comment")
                .requestMatchers(HttpMethod.PUT, "/api/v1/reconciliation/discrepancies/*/status").hasAuthority("SCOPE_reconciliation:discrepancy-status-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/discrepancies/pending").hasAuthority("SCOPE_reconciliation:discrepancies-pending")
                
                // Exception Handling & Investigation
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/exceptions").hasAuthority("SCOPE_reconciliation:exceptions-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/exceptions/investigate").hasAuthority("SCOPE_reconciliation:exception-investigate")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/exceptions/*/resolve").hasAuthority("SCOPE_reconciliation:exception-resolve")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/exceptions/*/assign").hasAuthority("SCOPE_reconciliation:exception-assign")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/exceptions/aging").hasAuthority("SCOPE_reconciliation:exception-aging")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/exceptions/bulk-resolve").hasAuthority("SCOPE_reconciliation:exception-bulk-resolve")
                
                // Rules & Configuration Management
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/rules").hasAuthority("SCOPE_reconciliation:rules-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/rules").hasAuthority("SCOPE_reconciliation:rules-create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/reconciliation/rules/*").hasAuthority("SCOPE_reconciliation:rules-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/reconciliation/rules/*").hasAuthority("SCOPE_reconciliation:rules-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/rules/*/test").hasAuthority("SCOPE_reconciliation:rules-test")
                .requestMatchers(HttpMethod.PUT, "/api/v1/reconciliation/rules/*/activate").hasAuthority("SCOPE_reconciliation:rules-activate")
                .requestMatchers(HttpMethod.PUT, "/api/v1/reconciliation/rules/*/deactivate").hasAuthority("SCOPE_reconciliation:rules-deactivate")
                
                // Approval Workflows
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/approvals/pending").hasAuthority("SCOPE_reconciliation:approvals-pending")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/approvals/*/approve").hasAuthority("SCOPE_reconciliation:approve")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/approvals/*/reject").hasAuthority("SCOPE_reconciliation:reject")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/approvals/history").hasAuthority("SCOPE_reconciliation:approval-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/approvals/bulk-approve").hasAuthority("SCOPE_reconciliation:bulk-approve")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/approvals/delegate").hasAuthority("SCOPE_reconciliation:approval-delegate")
                
                // Reporting & Analytics
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/reports/dashboard").hasAuthority("SCOPE_reconciliation:reports-dashboard")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/reports/summary").hasAuthority("SCOPE_reconciliation:reports-summary")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/reports/detailed").hasAuthority("SCOPE_reconciliation:reports-detailed")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/reports/generate").hasAuthority("SCOPE_reconciliation:reports-generate")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/reports/*/download").hasAuthority("SCOPE_reconciliation:reports-download")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/analytics/trends").hasAuthority("SCOPE_reconciliation:analytics-trends")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/analytics/performance").hasAuthority("SCOPE_reconciliation:analytics-performance")
                
                // Balance Management & Verification
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/balances").hasAuthority("SCOPE_reconciliation:balances-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/balances/verify").hasAuthority("SCOPE_reconciliation:balances-verify")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/balances/variance").hasAuthority("SCOPE_reconciliation:balance-variance")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/balances/adjust").hasAuthority("SCOPE_reconciliation:balance-adjust")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/balances/history").hasAuthority("SCOPE_reconciliation:balance-history")
                
                // Schedule & Automation Management
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/schedules").hasAuthority("SCOPE_reconciliation:schedules-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/schedules").hasAuthority("SCOPE_reconciliation:schedule-create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/reconciliation/schedules/*").hasAuthority("SCOPE_reconciliation:schedule-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/reconciliation/schedules/*").hasAuthority("SCOPE_reconciliation:schedule-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/schedules/*/run-now").hasAuthority("SCOPE_reconciliation:schedule-run")
                .requestMatchers(HttpMethod.PUT, "/api/v1/reconciliation/schedules/*/pause").hasAuthority("SCOPE_reconciliation:schedule-pause")
                .requestMatchers(HttpMethod.PUT, "/api/v1/reconciliation/schedules/*/resume").hasAuthority("SCOPE_reconciliation:schedule-resume")
                
                // Data Export & Integration
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/export/matched-items").hasAuthority("SCOPE_reconciliation:export-matched")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/export/discrepancies").hasAuthority("SCOPE_reconciliation:export-discrepancies")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/export/exceptions").hasAuthority("SCOPE_reconciliation:export-exceptions")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/export/*/status").hasAuthority("SCOPE_reconciliation:export-status")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/export/*/download").hasAuthority("SCOPE_reconciliation:export-download")
                
                // Audit Trail & Compliance
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/audit/trail").hasAuthority("SCOPE_reconciliation:audit-trail")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/audit/changes").hasAuthority("SCOPE_reconciliation:audit-changes")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/audit/search").hasAuthority("SCOPE_reconciliation:audit-search")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/compliance/report").hasAuthority("SCOPE_reconciliation:compliance-report")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/regulatory/submissions").hasAuthority("SCOPE_reconciliation:regulatory-submissions")
                
                // Configuration & Settings
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/config/settings").hasAuthority("SCOPE_reconciliation:config-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/reconciliation/config/settings").hasAuthority("SCOPE_reconciliation:config-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/config/thresholds").hasAuthority("SCOPE_reconciliation:thresholds-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/reconciliation/config/thresholds").hasAuthority("SCOPE_reconciliation:thresholds-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/config/backup").hasAuthority("SCOPE_reconciliation:config-backup")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/config/restore").hasAuthority("SCOPE_reconciliation:config-restore")
                
                // Performance & Monitoring
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/monitoring/metrics").hasAuthority("SCOPE_reconciliation:monitoring-metrics")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/monitoring/alerts").hasAuthority("SCOPE_reconciliation:monitoring-alerts")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/monitoring/alert-rules").hasAuthority("SCOPE_reconciliation:alert-rules")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/performance/statistics").hasAuthority("SCOPE_reconciliation:performance-stats")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/performance/optimize").hasAuthority("SCOPE_reconciliation:performance-optimize")
                
                // Admin Operations - System Management
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/admin/system/health").hasRole("RECONCILIATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/admin/system/maintenance").hasRole("RECONCILIATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/admin/cache/clear").hasRole("RECONCILIATION_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/admin/jobs/failed").hasRole("RECONCILIATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/admin/jobs/*/retry").hasRole("RECONCILIATION_ADMIN")
                
                // Admin Operations - User & Permission Management
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/admin/users/activity").hasRole("RECONCILIATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/admin/permissions/assign").hasRole("RECONCILIATION_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/admin/audit/full-report").hasRole("RECONCILIATION_ADMIN")
                
                // Admin Operations - Data Management
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/admin/data/archive").hasRole("RECONCILIATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/admin/data/purge").hasRole("RECONCILIATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/admin/data/migration").hasRole("RECONCILIATION_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/admin/data/integrity-check").hasRole("RECONCILIATION_ADMIN")
                
                // Admin Operations - Financial Control
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/admin/financial/positions").hasRole("FINANCIAL_CONTROLLER")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/admin/financial/force-reconcile").hasRole("FINANCIAL_CONTROLLER")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/admin/financial/override").hasRole("FINANCIAL_CONTROLLER")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/admin/financial/risk-report").hasRole("RISK_MANAGER")
                
                // Admin Operations - General Management
                .requestMatchers("/api/v1/reconciliation/admin/**").hasRole("RECONCILIATION_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/reconciliation/admin/audit/logs").hasRole("AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/admin/bulk-operations").hasRole("RECONCILIATION_ADMIN")
                
                // High-Security Internal service-to-service endpoints
                .requestMatchers("/internal/reconciliation/**").hasRole("SERVICE")
                .requestMatchers("/internal/matching/**").hasRole("SERVICE")
                .requestMatchers("/internal/discrepancy/**").hasRole("SERVICE")
                .requestMatchers("/internal/balance-verification/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}