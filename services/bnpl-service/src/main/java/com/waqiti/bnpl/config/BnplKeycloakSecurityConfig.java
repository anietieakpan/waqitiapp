package com.waqiti.bnpl.config;

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
 * Keycloak security configuration for BNPL (Buy Now Pay Later) Service
 * Manages authentication and authorization for BNPL operations and credit management
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class BnplKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain bnplKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for BNPL Service");
        
        return createKeycloakSecurityFilterChain(http, "bnpl-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/bnpl/public/**").permitAll()
                .requestMatchers("/api/v1/bnpl/merchants/public").permitAll() // Public merchant directory
                
                // Credit Application & Assessment
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/applications/submit").hasAuthority("SCOPE_bnpl:application-submit")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/applications").hasAuthority("SCOPE_bnpl:applications-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/applications/*").hasAuthority("SCOPE_bnpl:application-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/applications/*/withdraw").hasAuthority("SCOPE_bnpl:application-withdraw")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/credit/score").hasAuthority("SCOPE_bnpl:credit-score")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/credit/limit").hasAuthority("SCOPE_bnpl:credit-limit")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/credit/increase-request").hasAuthority("SCOPE_bnpl:credit-increase-request")
                
                // Purchase & Checkout
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/purchases/initiate").hasAuthority("SCOPE_bnpl:purchase-initiate")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/purchases/confirm").hasAuthority("SCOPE_bnpl:purchase-confirm")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/purchases/cancel").hasAuthority("SCOPE_bnpl:purchase-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/purchases").hasAuthority("SCOPE_bnpl:purchases-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/purchases/*").hasAuthority("SCOPE_bnpl:purchase-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/checkout/pre-approve").hasAuthority("SCOPE_bnpl:checkout-preapprove")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/checkout/options").hasAuthority("SCOPE_bnpl:checkout-options")
                
                // Payment Plans & Installments
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/plans").hasAuthority("SCOPE_bnpl:plans-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/plans/*").hasAuthority("SCOPE_bnpl:plan-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/plans/*/modify").hasAuthority("SCOPE_bnpl:plan-modify")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/plans/calculate").hasAuthority("SCOPE_bnpl:plan-calculate")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/installments").hasAuthority("SCOPE_bnpl:installments-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/installments/upcoming").hasAuthority("SCOPE_bnpl:installments-upcoming")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/installments/overdue").hasAuthority("SCOPE_bnpl:installments-overdue")
                
                // Payments & Repayment
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/payments/make").hasAuthority("SCOPE_bnpl:payment-make")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/payments/schedule").hasAuthority("SCOPE_bnpl:payment-schedule")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/payments/early-repayment").hasAuthority("SCOPE_bnpl:early-repayment")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/payments/history").hasAuthority("SCOPE_bnpl:payment-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/payments/*/refund").hasAuthority("SCOPE_bnpl:payment-refund")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/autopay/enable").hasAuthority("SCOPE_bnpl:autopay-enable")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/autopay/disable").hasAuthority("SCOPE_bnpl:autopay-disable")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/autopay/status").hasAuthority("SCOPE_bnpl:autopay-status")
                
                // Account Management
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/account").hasAuthority("SCOPE_bnpl:account-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/bnpl/account").hasAuthority("SCOPE_bnpl:account-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/account/balance").hasAuthority("SCOPE_bnpl:balance-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/account/statement").hasAuthority("SCOPE_bnpl:statement-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/account/freeze").hasAuthority("SCOPE_bnpl:account-freeze")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/account/close").hasAuthority("SCOPE_bnpl:account-close")
                
                // Merchant Integration
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/merchants").hasAuthority("SCOPE_bnpl:merchants-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/merchants/*/offers").hasAuthority("SCOPE_bnpl:merchant-offers")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/merchants/*/favorite").hasAuthority("SCOPE_bnpl:merchant-favorite")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bnpl/merchants/*/favorite").hasAuthority("SCOPE_bnpl:merchant-unfavorite")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/categories").hasAuthority("SCOPE_bnpl:categories-view")
                
                // Disputes & Returns
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/disputes/create").hasAuthority("SCOPE_bnpl:dispute-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/disputes").hasAuthority("SCOPE_bnpl:disputes-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/disputes/*").hasAuthority("SCOPE_bnpl:dispute-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/bnpl/disputes/*").hasAuthority("SCOPE_bnpl:dispute-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/returns/initiate").hasAuthority("SCOPE_bnpl:return-initiate")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/returns").hasAuthority("SCOPE_bnpl:returns-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/returns/*/track").hasAuthority("SCOPE_bnpl:return-track")
                
                // Credit Management & Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/credit/report").hasAuthority("SCOPE_bnpl:credit-report")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/credit/history").hasAuthority("SCOPE_bnpl:credit-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/credit/dispute").hasAuthority("SCOPE_bnpl:credit-dispute")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/credit/improvement-tips").hasAuthority("SCOPE_bnpl:credit-tips")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/credit/monitoring").hasAuthority("SCOPE_bnpl:credit-monitoring")
                
                // Notifications & Alerts
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/notifications").hasAuthority("SCOPE_bnpl:notifications-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/bnpl/notifications/preferences").hasAuthority("SCOPE_bnpl:notifications-preferences")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/alerts/payment-reminder").hasAuthority("SCOPE_bnpl:alerts-payment-reminder")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/alerts/promotion").hasAuthority("SCOPE_bnpl:alerts-promotion")
                
                // Analytics & Insights
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/analytics/spending").hasAuthority("SCOPE_bnpl:analytics-spending")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/analytics/trends").hasAuthority("SCOPE_bnpl:analytics-trends")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/insights/savings").hasAuthority("SCOPE_bnpl:insights-savings")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/insights/cashflow").hasAuthority("SCOPE_bnpl:insights-cashflow")
                
                // Admin Operations - Credit Management
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/admin/applications/pending").hasRole("BNPL_UNDERWRITER")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/admin/applications/*/approve").hasRole("BNPL_UNDERWRITER")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/admin/applications/*/reject").hasRole("BNPL_UNDERWRITER")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/admin/credit/*/adjust").hasRole("BNPL_CREDIT_MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/admin/risk/assessment").hasRole("BNPL_RISK_ANALYST")
                
                // Admin Operations - Collections
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/admin/collections/overdue").hasRole("BNPL_COLLECTIONS")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/admin/collections/*/contact").hasRole("BNPL_COLLECTIONS")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/admin/collections/*/payment-plan").hasRole("BNPL_COLLECTIONS")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/admin/accounts/*/writeoff").hasRole("BNPL_MANAGER")
                
                // Admin Operations - General
                .requestMatchers("/api/v1/bnpl/admin/**").hasRole("BNPL_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/bnpl/admin/reports/**").hasRole("BNPL_ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/bnpl/admin/bulk-operations").hasRole("BNPL_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/bnpl/**").hasRole("SERVICE")
                .requestMatchers("/internal/credit/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}