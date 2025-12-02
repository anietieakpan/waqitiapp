package com.waqiti.merchant.config;

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
 * Keycloak security configuration for Merchant Payment Service
 * Manages authentication and authorization for merchant payment operations
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class MerchantPaymentKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain merchantPaymentKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Merchant Payment Service");
        
        return createKeycloakSecurityFilterChain(http, "merchant-payment-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/merchant/public/**").permitAll()
                .requestMatchers("/api/v1/merchant/webhook/**").permitAll()
                
                // Merchant Registration and Onboarding
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/verify-email").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/resend-verification").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/check-availability").permitAll()
                
                // Merchant Account Management
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/profile").hasAuthority("SCOPE_merchant:profile-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/merchant/profile").hasAuthority("SCOPE_merchant:profile-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/kyc/submit").hasAuthority("SCOPE_merchant:kyc-submit")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/kyc/status").hasAuthority("SCOPE_merchant:kyc-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/documents/upload").hasAuthority("SCOPE_merchant:document-upload")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/documents").hasAuthority("SCOPE_merchant:document-read")
                
                // Business Information
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/business").hasAuthority("SCOPE_merchant:business-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/merchant/business").hasAuthority("SCOPE_merchant:business-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/business/hours").hasAuthority("SCOPE_merchant:business-hours")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/business/locations").hasAuthority("SCOPE_merchant:location-add")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/merchant/business/locations/*").hasAuthority("SCOPE_merchant:location-delete")
                
                // Payment Processing
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/payments/process").hasAuthority("SCOPE_merchant:payment-process")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/payments/refund").hasAuthority("SCOPE_merchant:payment-refund")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/payments/void").hasAuthority("SCOPE_merchant:payment-void")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/payments").hasAuthority("SCOPE_merchant:payment-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/payments/*").hasAuthority("SCOPE_merchant:payment-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/payments/*/status").hasAuthority("SCOPE_merchant:payment-status")
                
                // QR Code Payments
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/qr/generate").hasAuthority("SCOPE_merchant:qr-generate")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/qr/static").hasAuthority("SCOPE_merchant:qr-static")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/qr/dynamic").hasAuthority("SCOPE_merchant:qr-dynamic")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/qr/*/status").hasAuthority("SCOPE_merchant:qr-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/qr/*/cancel").hasAuthority("SCOPE_merchant:qr-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/qr/history").hasAuthority("SCOPE_merchant:qr-history")
                
                // POS Terminal Management
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/pos/register").hasAuthority("SCOPE_merchant:pos-register")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/pos/terminals").hasAuthority("SCOPE_merchant:pos-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/merchant/pos/terminals/*").hasAuthority("SCOPE_merchant:pos-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/merchant/pos/terminals/*").hasAuthority("SCOPE_merchant:pos-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/pos/terminals/*/activate").hasAuthority("SCOPE_merchant:pos-activate")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/pos/terminals/*/deactivate").hasAuthority("SCOPE_merchant:pos-deactivate")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/pos/terminals/*/transactions").hasAuthority("SCOPE_merchant:pos-transactions")
                
                // Settlement and Payouts
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/settlements").hasAuthority("SCOPE_merchant:settlement-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/settlements/*").hasAuthority("SCOPE_merchant:settlement-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/settlements/request").hasAuthority("SCOPE_merchant:settlement-request")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/payouts").hasAuthority("SCOPE_merchant:payout-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/payouts/schedule").hasAuthority("SCOPE_merchant:payout-schedule")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/balance").hasAuthority("SCOPE_merchant:balance-read")
                
                // Transaction Management
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/transactions").hasAuthority("SCOPE_merchant:transaction-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/transactions/*").hasAuthority("SCOPE_merchant:transaction-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/transactions/export").hasAuthority("SCOPE_merchant:transaction-export")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/transactions/*/receipt").hasAuthority("SCOPE_merchant:receipt-download")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/transactions/*/dispute").hasAuthority("SCOPE_merchant:dispute-create")
                
                // Analytics and Reporting
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/analytics/dashboard").hasAuthority("SCOPE_merchant:analytics-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/analytics/revenue").hasAuthority("SCOPE_merchant:analytics-revenue")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/analytics/customers").hasAuthority("SCOPE_merchant:analytics-customers")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/analytics/products").hasAuthority("SCOPE_merchant:analytics-products")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/reports/**").hasAuthority("SCOPE_merchant:reports")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/reports/generate").hasAuthority("SCOPE_merchant:reports-generate")
                
                // API Keys and Webhooks
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/api-keys").hasAuthority("SCOPE_merchant:apikey-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/api-keys/generate").hasAuthority("SCOPE_merchant:apikey-create")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/merchant/api-keys/*").hasAuthority("SCOPE_merchant:apikey-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/api-keys/*/rotate").hasAuthority("SCOPE_merchant:apikey-rotate")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/webhooks").hasAuthority("SCOPE_merchant:webhook-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/webhooks").hasAuthority("SCOPE_merchant:webhook-create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/merchant/webhooks/*").hasAuthority("SCOPE_merchant:webhook-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/merchant/webhooks/*").hasAuthority("SCOPE_merchant:webhook-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/webhooks/test").hasAuthority("SCOPE_merchant:webhook-test")
                
                // Settings and Preferences
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/settings").hasAuthority("SCOPE_merchant:settings-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/merchant/settings").hasAuthority("SCOPE_merchant:settings-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/settings/payment-methods").hasAuthority("SCOPE_merchant:payment-methods-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/merchant/settings/payment-methods").hasAuthority("SCOPE_merchant:payment-methods-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/settings/fees").hasAuthority("SCOPE_merchant:fees-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/merchant/settings/fees").hasAuthority("SCOPE_merchant:fees-update")
                
                // Fraud and Risk Management
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/fraud/alerts").hasAuthority("SCOPE_merchant:fraud-alerts")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/fraud/rules").hasAuthority("SCOPE_merchant:fraud-rules-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/fraud/rules").hasAuthority("SCOPE_merchant:fraud-rules-create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/merchant/fraud/rules/*").hasAuthority("SCOPE_merchant:fraud-rules-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/merchant/fraud/rules/*").hasAuthority("SCOPE_merchant:fraud-rules-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/risk/score").hasAuthority("SCOPE_merchant:risk-score")
                
                // Inventory and Products (for retail merchants)
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/products").hasAuthority("SCOPE_merchant:product-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/products").hasAuthority("SCOPE_merchant:product-create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/merchant/products/*").hasAuthority("SCOPE_merchant:product-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/merchant/products/*").hasAuthority("SCOPE_merchant:product-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/products/import").hasAuthority("SCOPE_merchant:product-import")
                
                // Customer Management
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/customers").hasAuthority("SCOPE_merchant:customer-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/customers/*").hasAuthority("SCOPE_merchant:customer-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/customers/*/transactions").hasAuthority("SCOPE_merchant:customer-transactions")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchant/customers/*/loyalty").hasAuthority("SCOPE_merchant:loyalty-manage")
                
                // Admin Operations
                .requestMatchers("/api/v1/merchant/admin/**").hasRole("MERCHANT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/admin/merchants").hasRole("MERCHANT_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/merchant/admin/merchants/*/status").hasRole("MERCHANT_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/merchant/admin/merchants/*/limits").hasRole("MERCHANT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/admin/compliance").hasRole("MERCHANT_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/merchant/**").hasRole("SERVICE")
                .requestMatchers("/internal/merchant-payment/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}