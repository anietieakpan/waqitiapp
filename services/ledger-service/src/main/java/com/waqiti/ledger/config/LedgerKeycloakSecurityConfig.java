package com.waqiti.ledger.config;

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
 * Keycloak security configuration for Ledger Service
 * Manages authentication and authorization for general ledger and accounting operations
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class LedgerKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain ledgerKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Ledger Service");
        
        return createKeycloakSecurityFilterChain(http, "ledger-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/ledger/public/**").permitAll()
                
                // Account Management
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/accounts/create").hasAuthority("SCOPE_ledger:account-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/accounts").hasAuthority("SCOPE_ledger:accounts-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/accounts/*").hasAuthority("SCOPE_ledger:account-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/ledger/accounts/*").hasAuthority("SCOPE_ledger:account-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/ledger/accounts/*").hasAuthority("SCOPE_ledger:account-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/accounts/*/balance").hasAuthority("SCOPE_ledger:balance-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/accounts/chart").hasAuthority("SCOPE_ledger:chart-of-accounts")
                
                // Journal Entries
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/journal/entry").hasAuthority("SCOPE_ledger:journal-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/journal/entries").hasAuthority("SCOPE_ledger:journal-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/journal/entries/*").hasAuthority("SCOPE_ledger:journal-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/ledger/journal/entries/*").hasAuthority("SCOPE_ledger:journal-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/ledger/journal/entries/*").hasAuthority("SCOPE_ledger:journal-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/journal/entries/*/post").hasAuthority("SCOPE_ledger:journal-post")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/journal/entries/*/reverse").hasAuthority("SCOPE_ledger:journal-reverse")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/journal/entries/*/approve").hasRole("LEDGER_APPROVER")
                
                // Transaction Processing
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/transactions/post").hasAuthority("SCOPE_ledger:transaction-post")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/transactions").hasAuthority("SCOPE_ledger:transactions-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/transactions/*").hasAuthority("SCOPE_ledger:transaction-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/transactions/batch").hasAuthority("SCOPE_ledger:transaction-batch")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/transactions/pending").hasAuthority("SCOPE_ledger:transactions-pending")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/transactions/*/void").hasAuthority("SCOPE_ledger:transaction-void")
                
                // General Ledger
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/gl/accounts").hasAuthority("SCOPE_ledger:gl-accounts")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/gl/accounts/*").hasAuthority("SCOPE_ledger:gl-account-detail")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/gl/balances").hasAuthority("SCOPE_ledger:gl-balances")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/gl/trial-balance").hasAuthority("SCOPE_ledger:trial-balance")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/gl/close-period").hasRole("LEDGER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/gl/year-end").hasRole("LEDGER_ADMIN")
                
                // Sub-Ledgers
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/subledger/ap").hasAuthority("SCOPE_ledger:ap-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/subledger/ar").hasAuthority("SCOPE_ledger:ar-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/subledger/inventory").hasAuthority("SCOPE_ledger:inventory-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/subledger/payroll").hasAuthority("SCOPE_ledger:payroll-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/subledger/reconcile").hasAuthority("SCOPE_ledger:subledger-reconcile")
                
                // Reconciliation
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/reconciliation/start").hasAuthority("SCOPE_ledger:reconciliation-start")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/reconciliation/status").hasAuthority("SCOPE_ledger:reconciliation-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/reconciliation/match").hasAuthority("SCOPE_ledger:reconciliation-match")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/reconciliation/complete").hasAuthority("SCOPE_ledger:reconciliation-complete")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/reconciliation/discrepancies").hasAuthority("SCOPE_ledger:reconciliation-discrepancies")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/reconciliation/auto").hasAuthority("SCOPE_ledger:reconciliation-auto")
                
                // Financial Reporting
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/reports/balance-sheet").hasAuthority("SCOPE_ledger:balance-sheet")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/reports/income-statement").hasAuthority("SCOPE_ledger:income-statement")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/reports/cash-flow").hasAuthority("SCOPE_ledger:cash-flow")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/reports/equity-statement").hasAuthority("SCOPE_ledger:equity-statement")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/reports/comprehensive-income").hasAuthority("SCOPE_ledger:comprehensive-income")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/reports/custom").hasAuthority("SCOPE_ledger:custom-reports")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/reports/ratios").hasAuthority("SCOPE_ledger:financial-ratios")
                
                // Budgeting
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/budget/create").hasAuthority("SCOPE_ledger:budget-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/budget").hasAuthority("SCOPE_ledger:budget-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/ledger/budget/*").hasAuthority("SCOPE_ledger:budget-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/ledger/budget/*").hasAuthority("SCOPE_ledger:budget-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/budget/variance").hasAuthority("SCOPE_ledger:budget-variance")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/budget/forecast").hasAuthority("SCOPE_ledger:budget-forecast")
                
                // Audit Trail
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/audit/trail").hasAuthority("SCOPE_ledger:audit-trail")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/audit/changes").hasAuthority("SCOPE_ledger:audit-changes")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/audit/users/*").hasAuthority("SCOPE_ledger:audit-user")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/audit/transactions/*").hasAuthority("SCOPE_ledger:audit-transaction")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/audit/export").hasRole("AUDITOR")
                
                // Period Management
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/periods").hasAuthority("SCOPE_ledger:periods-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/periods/open").hasRole("LEDGER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/periods/close").hasRole("LEDGER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/periods/lock").hasRole("LEDGER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/periods/unlock").hasRole("LEDGER_ADMIN")
                
                // Cost Centers
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/cost-centers/create").hasAuthority("SCOPE_ledger:cost-center-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/cost-centers").hasAuthority("SCOPE_ledger:cost-centers-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/ledger/cost-centers/*").hasAuthority("SCOPE_ledger:cost-center-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/ledger/cost-centers/*").hasAuthority("SCOPE_ledger:cost-center-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/cost-centers/*/analysis").hasAuthority("SCOPE_ledger:cost-analysis")
                
                // Currency & Exchange Rates
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/currencies").hasAuthority("SCOPE_ledger:currencies-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/currencies/rates").hasAuthority("SCOPE_ledger:rates-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/currencies/rates").hasAuthority("SCOPE_ledger:rates-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/currencies/convert").hasAuthority("SCOPE_ledger:currency-convert")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/currencies/gains-losses").hasAuthority("SCOPE_ledger:fx-gains-losses")
                
                // Compliance & Regulatory
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/compliance/sox").hasAuthority("SCOPE_ledger:sox-compliance")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/compliance/gaap").hasAuthority("SCOPE_ledger:gaap-compliance")
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/compliance/ifrs").hasAuthority("SCOPE_ledger:ifrs-compliance")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/compliance/check").hasAuthority("SCOPE_ledger:compliance-check")
                
                // Admin Operations
                .requestMatchers("/api/v1/ledger/admin/**").hasRole("LEDGER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/admin/initialize").hasRole("LEDGER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/admin/migrate").hasRole("LEDGER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/admin/backup").hasRole("LEDGER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/admin/restore").hasRole("LEDGER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/admin/integrity-check").hasRole("LEDGER_ADMIN")
                
                // Accountant Role
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/adjustments/create").hasRole("ACCOUNTANT")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/adjustments/*/approve").hasRole("SENIOR_ACCOUNTANT")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/closing/month-end").hasRole("ACCOUNTANT")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/closing/year-end").hasRole("SENIOR_ACCOUNTANT")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/ledger/**").hasRole("SERVICE")
                .requestMatchers("/internal/accounting/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}