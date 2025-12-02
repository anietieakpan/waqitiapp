package com.waqiti.bankintegration.config;

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
 * Keycloak security configuration for Bank Integration Service
 * CRITICAL SECURITY IMPLEMENTATION - External banking and payment provider connections
 * This service handles all integrations with external banks, payment processors, and financial institutions
 * Extreme security is required as this is the gateway to external financial systems
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class BankIntegrationKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain bankIntegrationKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring CRITICAL Keycloak security for Bank Integration Service - External Financial Gateway");
        
        return createKeycloakSecurityFilterChain(http, "bank-integration-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Extremely limited
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Bank Account Linking - CRITICAL OPERATIONS
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/accounts/link").hasAuthority("SCOPE_bank:account-link")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/accounts/*/verify").hasAuthority("SCOPE_bank:account-verify")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bank-integration/accounts/*/unlink").hasAuthority("SCOPE_bank:account-unlink")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/accounts").hasAuthority("SCOPE_bank:accounts-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/accounts/*").hasAuthority("SCOPE_bank:account-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/bank-integration/accounts/*").hasAuthority("SCOPE_bank:account-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/accounts/*/refresh").hasAuthority("SCOPE_bank:account-refresh")
                
                // Plaid Integration
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/plaid/link/token").hasAuthority("SCOPE_bank:plaid-link-token")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/plaid/link/exchange").hasAuthority("SCOPE_bank:plaid-exchange-token")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/plaid/accounts").hasAuthority("SCOPE_bank:plaid-accounts")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/plaid/balances").hasAuthority("SCOPE_bank:plaid-balances")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/plaid/transactions").hasAuthority("SCOPE_bank:plaid-transactions")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/plaid/auth").hasAuthority("SCOPE_bank:plaid-auth")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/plaid/identity").hasAuthority("SCOPE_bank:plaid-identity")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/plaid/webhook").hasAuthority("SCOPE_bank:plaid-webhook")
                
                // Stripe Integration
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/stripe/customers").hasAuthority("SCOPE_bank:stripe-customer-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/stripe/customers/*").hasAuthority("SCOPE_bank:stripe-customer-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/bank-integration/stripe/customers/*").hasAuthority("SCOPE_bank:stripe-customer-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/stripe/payment-methods").hasAuthority("SCOPE_bank:stripe-payment-method-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/stripe/payment-methods/*").hasAuthority("SCOPE_bank:stripe-payment-method-view")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bank-integration/stripe/payment-methods/*").hasAuthority("SCOPE_bank:stripe-payment-method-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/stripe/charges").hasAuthority("SCOPE_bank:stripe-charge-create")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/stripe/payment-intents").hasAuthority("SCOPE_bank:stripe-payment-intent-create")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/stripe/refunds").hasAuthority("SCOPE_bank:stripe-refund-create")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/stripe/webhook").hasAuthority("SCOPE_bank:stripe-webhook")
                
                // PayPal Integration
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/paypal/orders").hasAuthority("SCOPE_bank:paypal-order-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/paypal/orders/*").hasAuthority("SCOPE_bank:paypal-order-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/paypal/orders/*/capture").hasAuthority("SCOPE_bank:paypal-order-capture")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/paypal/orders/*/authorize").hasAuthority("SCOPE_bank:paypal-order-authorize")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/paypal/refunds").hasAuthority("SCOPE_bank:paypal-refund-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/paypal/refunds/*").hasAuthority("SCOPE_bank:paypal-refund-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/paypal/payouts").hasAuthority("SCOPE_bank:paypal-payout-create")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/paypal/webhook").hasAuthority("SCOPE_bank:paypal-webhook")
                
                // ACH Transfers
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/ach/transfers").hasAuthority("SCOPE_bank:ach-transfer-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/ach/transfers/*").hasAuthority("SCOPE_bank:ach-transfer-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/ach/transfers/*/cancel").hasAuthority("SCOPE_bank:ach-transfer-cancel")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/ach/micro-deposits").hasAuthority("SCOPE_bank:ach-micro-deposit")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/ach/micro-deposits/verify").hasAuthority("SCOPE_bank:ach-micro-deposit-verify")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/ach/routing/validate").hasAuthority("SCOPE_bank:ach-routing-validate")
                
                // Wire Transfers
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/wire/transfers").hasAuthority("SCOPE_bank:wire-transfer-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/wire/transfers/*").hasAuthority("SCOPE_bank:wire-transfer-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/wire/transfers/*/confirm").hasAuthority("SCOPE_bank:wire-transfer-confirm")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/wire/transfers/*/cancel").hasAuthority("SCOPE_bank:wire-transfer-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/wire/swift/validate").hasAuthority("SCOPE_bank:wire-swift-validate")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/wire/iban/validate").hasAuthority("SCOPE_bank:wire-iban-validate")
                
                // Open Banking
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/open-banking/consent").hasAuthority("SCOPE_bank:open-banking-consent")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/open-banking/consent/*").hasAuthority("SCOPE_bank:open-banking-consent-view")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bank-integration/open-banking/consent/*").hasAuthority("SCOPE_bank:open-banking-consent-revoke")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/open-banking/accounts").hasAuthority("SCOPE_bank:open-banking-accounts")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/open-banking/balances").hasAuthority("SCOPE_bank:open-banking-balances")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/open-banking/transactions").hasAuthority("SCOPE_bank:open-banking-transactions")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/open-banking/payments").hasAuthority("SCOPE_bank:open-banking-payment")
                
                // Provider Management
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/providers").hasAuthority("SCOPE_bank:providers-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/providers/*").hasAuthority("SCOPE_bank:provider-details")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/providers/*/status").hasAuthority("SCOPE_bank:provider-status")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/providers/*/health").hasAuthority("SCOPE_bank:provider-health")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/providers/*/enable").hasAuthority("SCOPE_bank:provider-enable")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/providers/*/disable").hasAuthority("SCOPE_bank:provider-disable")
                
                // Payment Processing
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/payments/process").hasAuthority("SCOPE_bank:payment-process")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/payments/*").hasAuthority("SCOPE_bank:payment-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/payments/*/capture").hasAuthority("SCOPE_bank:payment-capture")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/payments/*/void").hasAuthority("SCOPE_bank:payment-void")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/payments/*/refund").hasAuthority("SCOPE_bank:payment-refund")
                
                // Reconciliation
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/reconciliation/pending").hasAuthority("SCOPE_bank:reconciliation-pending")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/reconciliation/match").hasAuthority("SCOPE_bank:reconciliation-match")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/reconciliation/discrepancies").hasAuthority("SCOPE_bank:reconciliation-discrepancies")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/reconciliation/resolve").hasAuthority("SCOPE_bank:reconciliation-resolve")
                
                // Webhooks & Callbacks
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/webhooks/**").hasAuthority("SCOPE_bank:webhook-receive")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/webhooks/logs").hasAuthority("SCOPE_bank:webhook-logs")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/webhooks/*/retry").hasAuthority("SCOPE_bank:webhook-retry")
                
                // Security & Compliance
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/security/tokenize").hasAuthority("SCOPE_bank:security-tokenize")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/security/detokenize").hasAuthority("SCOPE_bank:security-detokenize")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/security/encrypt").hasAuthority("SCOPE_bank:security-encrypt")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/compliance/pci-status").hasAuthority("SCOPE_bank:compliance-pci-status")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/compliance/audit-logs").hasAuthority("SCOPE_bank:compliance-audit-logs")
                
                // Rate Limiting & Monitoring
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/monitoring/metrics").hasAuthority("SCOPE_bank:monitoring-metrics")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/monitoring/rate-limits").hasAuthority("SCOPE_bank:monitoring-rate-limits")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/monitoring/errors").hasAuthority("SCOPE_bank:monitoring-errors")
                
                // Admin Operations - Provider Management
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/admin/providers/*/configure").hasRole("BANK_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/bank-integration/admin/providers/*/credentials").hasRole("BANK_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/admin/providers/*/test").hasRole("BANK_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bank-integration/admin/providers/*").hasRole("BANK_ADMIN")
                
                // Admin Operations - Security Management
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/admin/security/rotate-keys").hasRole("SECURITY_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/admin/security/whitelist-ip").hasRole("SECURITY_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bank-integration/admin/security/whitelist-ip").hasRole("SECURITY_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/admin/security/audit").hasRole("AUDITOR")
                
                // Admin Operations - Financial Control
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/admin/settlements").hasRole("FINANCIAL_CONTROLLER")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/admin/settlements/*/process").hasRole("FINANCIAL_CONTROLLER")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/admin/chargebacks").hasRole("FINANCIAL_CONTROLLER")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/admin/chargebacks/*/dispute").hasRole("FINANCIAL_CONTROLLER")
                
                // Admin Operations - System Management
                .requestMatchers("/api/v1/bank-integration/admin/**").hasRole("BANK_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/bank-integration/admin/system/health").hasRole("BANK_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/admin/system/maintenance").hasRole("BANK_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/bank-integration/admin/cache/clear").hasRole("BANK_ADMIN")
                
                // High-Security Internal service-to-service endpoints
                .requestMatchers("/internal/bank-integration/**").hasRole("SERVICE")
                .requestMatchers("/internal/payment-routing/**").hasRole("SERVICE")
                .requestMatchers("/internal/bank-verification/**").hasRole("SERVICE")
                
                // All other endpoints require authentication - NO EXCEPTIONS
                .anyRequest().authenticated()
            );
        });
    }
}