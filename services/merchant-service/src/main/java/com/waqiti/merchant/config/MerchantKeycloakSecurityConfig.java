package com.waqiti.merchant.config;

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
 * Keycloak security configuration for Merchant Service
 * Manages authentication and authorization for merchant operations
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class MerchantKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain merchantKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "merchant-service", httpSecurity -> {
            httpSecurity
                .authorizeHttpRequests(authz -> authz
                    // Public endpoints
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/v1/merchants/public/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    
                    // Merchant Registration and Onboarding
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/register").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/verify-email/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/onboard").hasAuthority("SCOPE_merchant:onboard")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/merchants/onboard/*").hasAuthority("SCOPE_merchant:onboard-update")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/onboard/status").hasAuthority("SCOPE_merchant:onboard-status")
                    
                    // Merchant Profile Management
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/profile").hasAuthority("SCOPE_merchant:profile-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/merchants/profile").hasAuthority("SCOPE_merchant:profile-update")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/merchants/profile").hasAuthority("SCOPE_merchant:profile-delete")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/profile/logo").hasAuthority("SCOPE_merchant:profile-update")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/profile/documents").hasAuthority("SCOPE_merchant:document-upload")
                    
                    // Store Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/stores").hasAuthority("SCOPE_merchant:store-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/stores").hasAuthority("SCOPE_merchant:store-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/stores/*").hasAuthority("SCOPE_merchant:store-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/merchants/stores/*").hasAuthority("SCOPE_merchant:store-update")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/merchants/stores/*").hasAuthority("SCOPE_merchant:store-delete")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/merchants/stores/*/status").hasAuthority("SCOPE_merchant:store-status")
                    
                    // Payment Processing
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/payments/process").hasAuthority("SCOPE_merchant:payment-process")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/payments/refund").hasAuthority("SCOPE_merchant:payment-refund")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/payments").hasAuthority("SCOPE_merchant:payment-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/payments/*").hasAuthority("SCOPE_merchant:payment-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/payments/*/capture").hasAuthority("SCOPE_merchant:payment-capture")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/payments/*/void").hasAuthority("SCOPE_merchant:payment-void")
                    
                    // QR Code Operations
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/qr/generate").hasAuthority("SCOPE_merchant:qr-generate")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/qr/*").hasAuthority("SCOPE_merchant:qr-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/merchants/qr/*/regenerate").hasAuthority("SCOPE_merchant:qr-regenerate")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/merchants/qr/*").hasAuthority("SCOPE_merchant:qr-delete")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/qr/validate/*").permitAll() // Public for customer scanning
                    
                    // Terminal/POS Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/terminals").hasAuthority("SCOPE_merchant:terminal-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/terminals").hasAuthority("SCOPE_merchant:terminal-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/merchants/terminals/*").hasAuthority("SCOPE_merchant:terminal-update")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/merchants/terminals/*").hasAuthority("SCOPE_merchant:terminal-delete")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/terminals/*/activate").hasAuthority("SCOPE_merchant:terminal-activate")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/terminals/*/deactivate").hasAuthority("SCOPE_merchant:terminal-deactivate")
                    
                    // Settlement and Payouts
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/settlements").hasAuthority("SCOPE_merchant:settlement-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/settlements/*").hasAuthority("SCOPE_merchant:settlement-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/settlements/request").hasAuthority("SCOPE_merchant:settlement-request")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/payouts").hasAuthority("SCOPE_merchant:payout-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/payouts/schedule").hasAuthority("SCOPE_merchant:payout-schedule")
                    
                    // Transaction Management
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/transactions").hasAuthority("SCOPE_merchant:transaction-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/transactions/*").hasAuthority("SCOPE_merchant:transaction-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/transactions/*/dispute").hasAuthority("SCOPE_merchant:transaction-dispute")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/transactions/export").hasAuthority("SCOPE_merchant:transaction-export")
                    
                    // Fee Management
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/fees").hasAuthority("SCOPE_merchant:fee-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/fees/calculate").hasAuthority("SCOPE_merchant:fee-calculate")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/merchants/fees/negotiate").hasAuthority("SCOPE_merchant:fee-negotiate")
                    
                    // Analytics and Reporting
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/analytics").hasAuthority("SCOPE_merchant:analytics-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/analytics/dashboard").hasAuthority("SCOPE_merchant:analytics-dashboard")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/reports").hasAuthority("SCOPE_merchant:report-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/reports/generate").hasAuthority("SCOPE_merchant:report-generate")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/reports/*/download").hasAuthority("SCOPE_merchant:report-download")
                    
                    // API Key Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/api-keys").hasAuthority("SCOPE_merchant:apikey-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/api-keys").hasAuthority("SCOPE_merchant:apikey-read")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/merchants/api-keys/*").hasAuthority("SCOPE_merchant:apikey-delete")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/api-keys/*/rotate").hasAuthority("SCOPE_merchant:apikey-rotate")
                    
                    // Webhook Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/webhooks").hasAuthority("SCOPE_merchant:webhook-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/webhooks").hasAuthority("SCOPE_merchant:webhook-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/merchants/webhooks/*").hasAuthority("SCOPE_merchant:webhook-update")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/merchants/webhooks/*").hasAuthority("SCOPE_merchant:webhook-delete")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/webhooks/test").hasAuthority("SCOPE_merchant:webhook-test")
                    
                    // Compliance and Verification
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/compliance/status").hasAuthority("SCOPE_merchant:compliance-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/compliance/submit").hasAuthority("SCOPE_merchant:compliance-submit")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/verification/status").hasAuthority("SCOPE_merchant:verification-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/merchants/verification/documents").hasAuthority("SCOPE_merchant:verification-submit")
                    
                    // Admin Operations
                    .requestMatchers("/api/v1/merchants/admin/**").hasRole("MERCHANT_ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/merchants/admin/all").hasRole("MERCHANT_ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/merchants/admin/*/approve").hasRole("MERCHANT_ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/merchants/admin/*/reject").hasRole("MERCHANT_ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/merchants/admin/*/suspend").hasRole("MERCHANT_ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/merchants/admin/*/activate").hasRole("MERCHANT_ADMIN")
                    
                    // Internal service-to-service endpoints
                    .requestMatchers("/internal/merchants/**").hasRole("SERVICE")
                    .requestMatchers("/internal/validation/**").hasRole("SERVICE")
                    .requestMatchers("/internal/fees/**").hasRole("SERVICE")
                    
                    // Webhook endpoints (public but validated via signature)
                    .requestMatchers("/api/v1/merchants/webhooks/receive/**").permitAll()
                    
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
                );
        });
    }
}