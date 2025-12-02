package com.waqiti.billingorchestrator.config;

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
 * Keycloak security configuration for Billing Orchestrator Service
 * Manages all billing cycles, invoicing, subscriptions, and payment orchestration
 * Critical for financial operations and revenue management
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class BillingOrchestratorKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain billingOrchestratorKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Billing Orchestrator Service");
        
        return createKeycloakSecurityFilterChain(http, "billing-orchestrator-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Very limited
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Billing Cycle Management
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/cycles/create").hasAuthority("SCOPE_billing:cycle-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/cycles").hasAuthority("SCOPE_billing:cycles-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/cycles/*").hasAuthority("SCOPE_billing:cycle-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/billing/cycles/*").hasAuthority("SCOPE_billing:cycle-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/cycles/*/start").hasAuthority("SCOPE_billing:cycle-start")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/cycles/*/complete").hasAuthority("SCOPE_billing:cycle-complete")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/cycles/*/cancel").hasAuthority("SCOPE_billing:cycle-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/cycles/*/status").hasAuthority("SCOPE_billing:cycle-status")
                
                // Invoice Management
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/invoices/generate").hasAuthority("SCOPE_billing:invoice-generate")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/invoices").hasAuthority("SCOPE_billing:invoices-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/invoices/*").hasAuthority("SCOPE_billing:invoice-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/billing/invoices/*").hasAuthority("SCOPE_billing:invoice-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/invoices/*/send").hasAuthority("SCOPE_billing:invoice-send")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/invoices/*/cancel").hasAuthority("SCOPE_billing:invoice-cancel")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/invoices/*/void").hasAuthority("SCOPE_billing:invoice-void")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/invoices/*/pdf").hasAuthority("SCOPE_billing:invoice-download")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/invoices/*/mark-paid").hasAuthority("SCOPE_billing:invoice-mark-paid")
                
                // Subscription Management
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/subscriptions").hasAuthority("SCOPE_billing:subscription-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/subscriptions").hasAuthority("SCOPE_billing:subscriptions-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/subscriptions/*").hasAuthority("SCOPE_billing:subscription-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/billing/subscriptions/*").hasAuthority("SCOPE_billing:subscription-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/subscriptions/*/activate").hasAuthority("SCOPE_billing:subscription-activate")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/subscriptions/*/pause").hasAuthority("SCOPE_billing:subscription-pause")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/subscriptions/*/resume").hasAuthority("SCOPE_billing:subscription-resume")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/subscriptions/*/cancel").hasAuthority("SCOPE_billing:subscription-cancel")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/subscriptions/*/upgrade").hasAuthority("SCOPE_billing:subscription-upgrade")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/subscriptions/*/downgrade").hasAuthority("SCOPE_billing:subscription-downgrade")
                
                // Payment Processing
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/payments/process").hasAuthority("SCOPE_billing:payment-process")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/payments").hasAuthority("SCOPE_billing:payments-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/payments/*").hasAuthority("SCOPE_billing:payment-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/payments/*/retry").hasAuthority("SCOPE_billing:payment-retry")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/payments/*/refund").hasAuthority("SCOPE_billing:payment-refund")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/payments/*/status").hasAuthority("SCOPE_billing:payment-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/payments/batch").hasAuthority("SCOPE_billing:payment-batch")
                
                // Billing Plans & Pricing
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/plans").hasAuthority("SCOPE_billing:plan-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/plans").hasAuthority("SCOPE_billing:plans-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/plans/*").hasAuthority("SCOPE_billing:plan-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/billing/plans/*").hasAuthority("SCOPE_billing:plan-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/billing/plans/*").hasAuthority("SCOPE_billing:plan-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/plans/*/activate").hasAuthority("SCOPE_billing:plan-activate")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/plans/*/deactivate").hasAuthority("SCOPE_billing:plan-deactivate")
                
                // Usage & Metering
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/usage/record").hasAuthority("SCOPE_billing:usage-record")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/usage").hasAuthority("SCOPE_billing:usage-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/usage/*/summary").hasAuthority("SCOPE_billing:usage-summary")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/usage/calculate").hasAuthority("SCOPE_billing:usage-calculate")
                
                // Credits & Adjustments
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/credits/apply").hasAuthority("SCOPE_billing:credit-apply")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/credits").hasAuthority("SCOPE_billing:credits-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/adjustments").hasAuthority("SCOPE_billing:adjustment-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/adjustments").hasAuthority("SCOPE_billing:adjustments-view")
                
                // Dunning Management
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/dunning/status").hasAuthority("SCOPE_billing:dunning-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/dunning/process").hasAuthority("SCOPE_billing:dunning-process")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/dunning/campaigns").hasAuthority("SCOPE_billing:dunning-campaigns")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/dunning/*/skip").hasAuthority("SCOPE_billing:dunning-skip")
                
                // Reporting & Analytics
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/reports/revenue").hasAuthority("SCOPE_billing:report-revenue")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/reports/churn").hasAuthority("SCOPE_billing:report-churn")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/reports/mrr").hasAuthority("SCOPE_billing:report-mrr")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/reports/arr").hasAuthority("SCOPE_billing:report-arr")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/analytics/trends").hasAuthority("SCOPE_billing:analytics-trends")
                
                // Tax Management
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/tax/calculate").hasAuthority("SCOPE_billing:tax-calculate")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/tax/rates").hasAuthority("SCOPE_billing:tax-rates")
                .requestMatchers(HttpMethod.PUT, "/api/v1/billing/tax/exemptions").hasAuthority("SCOPE_billing:tax-exemptions")
                
                // Admin Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/admin/reconcile").hasRole("BILLING_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/admin/bulk-process").hasRole("BILLING_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/admin/failed-payments").hasRole("BILLING_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/admin/*/force-charge").hasRole("BILLING_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/admin/*/write-off").hasRole("FINANCIAL_CONTROLLER")
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/admin/audit").hasRole("AUDITOR")
                .requestMatchers("/api/v1/billing/admin/**").hasRole("BILLING_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/billing/**").hasRole("SERVICE")
                .requestMatchers("/internal/invoice/**").hasRole("SERVICE")
                .requestMatchers("/internal/subscription/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}