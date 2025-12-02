package com.waqiti.atm.config;

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
 * Keycloak security configuration for ATM Service
 * Manages authentication and authorization for ATM operations and cardless transactions
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class ATMKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain atmKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for ATM Service");
        
        return createKeycloakSecurityFilterChain(http, "atm-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/atm/public/**").permitAll()
                .requestMatchers("/api/v1/atm/locator/nearby").permitAll() // Public ATM locator
                .requestMatchers("/api/v1/atm/locator/search").permitAll() // Public ATM search
                
                // ATM Locator (Authenticated)
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/locator/favorites").hasAuthority("SCOPE_atm:favorites-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/locator/favorites/add").hasAuthority("SCOPE_atm:favorites-add")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/atm/locator/favorites/*").hasAuthority("SCOPE_atm:favorites-remove")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/locator/*/details").hasAuthority("SCOPE_atm:location-details")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/locator/route").hasAuthority("SCOPE_atm:route-plan")
                
                // Cash Withdrawal
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/withdraw/initiate").hasAuthority("SCOPE_atm:withdraw-initiate")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/withdraw/confirm").hasAuthority("SCOPE_atm:withdraw-confirm")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/withdraw/cancel").hasAuthority("SCOPE_atm:withdraw-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/withdraw/limits").hasAuthority("SCOPE_atm:withdraw-limits")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/withdraw/history").hasAuthority("SCOPE_atm:withdraw-history")
                
                // Cash Deposit
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/deposit/initiate").hasAuthority("SCOPE_atm:deposit-initiate")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/deposit/confirm").hasAuthority("SCOPE_atm:deposit-confirm")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/deposit/cancel").hasAuthority("SCOPE_atm:deposit-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/deposit/limits").hasAuthority("SCOPE_atm:deposit-limits")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/deposit/history").hasAuthority("SCOPE_atm:deposit-history")
                
                // Cardless Transactions
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/cardless/generate").hasAuthority("SCOPE_atm:cardless-generate")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/cardless/validate").hasAuthority("SCOPE_atm:cardless-validate")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/cardless/withdraw").hasAuthority("SCOPE_atm:cardless-withdraw")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/cardless/cancel").hasAuthority("SCOPE_atm:cardless-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/cardless/active").hasAuthority("SCOPE_atm:cardless-active")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/cardless/history").hasAuthority("SCOPE_atm:cardless-history")
                
                // QR Code Transactions
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/qr/generate").hasAuthority("SCOPE_atm:qr-generate")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/qr/scan").hasAuthority("SCOPE_atm:qr-scan")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/qr/validate").hasAuthority("SCOPE_atm:qr-validate")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/qr/execute").hasAuthority("SCOPE_atm:qr-execute")
                
                // Balance & Mini Statement
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/balance").hasAuthority("SCOPE_atm:balance-inquiry")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/mini-statement").hasAuthority("SCOPE_atm:mini-statement")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/available-balance").hasAuthority("SCOPE_atm:available-balance")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/pending-transactions").hasAuthority("SCOPE_atm:pending-view")
                
                // PIN Management
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/pin/change").hasAuthority("SCOPE_atm:pin-change")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/pin/verify").hasAuthority("SCOPE_atm:pin-verify")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/pin/reset-request").hasAuthority("SCOPE_atm:pin-reset-request")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/pin/attempts").hasAuthority("SCOPE_atm:pin-attempts")
                
                // Card Management
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/card/block").hasAuthority("SCOPE_atm:card-block")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/card/unblock").hasAuthority("SCOPE_atm:card-unblock")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/card/activate").hasAuthority("SCOPE_atm:card-activate")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/card/status").hasAuthority("SCOPE_atm:card-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/card/report-lost").hasAuthority("SCOPE_atm:card-report-lost")
                
                // Transfer Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/transfer/initiate").hasAuthority("SCOPE_atm:transfer-initiate")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/transfer/confirm").hasAuthority("SCOPE_atm:transfer-confirm")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/transfer/beneficiaries").hasAuthority("SCOPE_atm:beneficiaries-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/transfer/add-beneficiary").hasAuthority("SCOPE_atm:beneficiary-add")
                
                // Bill Payment at ATM
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/bills/categories").hasAuthority("SCOPE_atm:bills-categories")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/bills/billers").hasAuthority("SCOPE_atm:bills-billers")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/bills/pay").hasAuthority("SCOPE_atm:bills-pay")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/bills/history").hasAuthority("SCOPE_atm:bills-history")
                
                // Preferences & Settings
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/preferences").hasAuthority("SCOPE_atm:preferences-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/atm/preferences").hasAuthority("SCOPE_atm:preferences-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/preferences/language").hasAuthority("SCOPE_atm:language-change")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/preferences/receipt").hasAuthority("SCOPE_atm:receipt-preference")
                
                // Transaction Disputes
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/dispute/create").hasAuthority("SCOPE_atm:dispute-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/dispute/*").hasAuthority("SCOPE_atm:dispute-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/atm/dispute/*/update").hasAuthority("SCOPE_atm:dispute-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/disputes").hasAuthority("SCOPE_atm:disputes-list")
                
                // Emergency Services
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/emergency/cash").hasAuthority("SCOPE_atm:emergency-cash")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/emergency/card-capture").hasAuthority("SCOPE_atm:card-capture-report")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/emergency/assistance").hasAuthority("SCOPE_atm:emergency-assistance")
                
                // Receipts & Statements
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/receipts/*").hasAuthority("SCOPE_atm:receipt-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/receipts/*/email").hasAuthority("SCOPE_atm:receipt-email")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/receipts/*/sms").hasAuthority("SCOPE_atm:receipt-sms")
                
                // ATM Network Operations (Admin)
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/admin/network/status").hasRole("ATM_OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/admin/network/machines").hasRole("ATM_OPERATOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/admin/network/*/maintenance").hasRole("ATM_OPERATOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/admin/network/*/restart").hasRole("ATM_OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/admin/network/*/logs").hasRole("ATM_OPERATOR")
                
                // Cash Management (Admin)
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/admin/cash/levels").hasRole("ATM_OPERATOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/admin/cash/refill").hasRole("ATM_SUPERVISOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/admin/cash/forecast").hasRole("ATM_ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/admin/cash/reconcile").hasRole("ATM_SUPERVISOR")
                
                // Security & Fraud (Admin)
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/admin/security/alerts").hasRole("ATM_SECURITY")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/admin/security/camera/*").hasRole("ATM_SECURITY")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/admin/security/*/investigate").hasRole("ATM_SECURITY")
                .requestMatchers(HttpMethod.GET, "/api/v1/atm/admin/fraud/reports").hasRole("ATM_SECURITY")
                
                // Admin Operations
                .requestMatchers("/api/v1/atm/admin/**").hasRole("ATM_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/admin/*/shutdown").hasRole("ATM_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/atm/admin/*/configure").hasRole("ATM_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/atm/**").hasRole("SERVICE")
                .requestMatchers("/internal/cardless/**").hasRole("SERVICE")
                .requestMatchers("/internal/network/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}