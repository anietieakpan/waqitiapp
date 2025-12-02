package com.waqiti.bankintegration.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Keycloak security configuration for Bank Integration Service.
 * CRITICAL SERVICE - Handles external bank connections and must be highly secure.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Order(1)
public class KeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Override
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "bank-integration-service", httpSecurity -> {
            try {
                httpSecurity.authorizeHttpRequests(authz -> authz
                    // Health checks (public)
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    
                    // Bank account verification (requires bank scope)
                    .requestMatchers("/api/v1/bank-integration/accounts/verify").hasAuthority("SCOPE_bank:account:verify")
                    .requestMatchers("/api/v1/bank-integration/accounts/balance").hasAuthority("SCOPE_bank:account:balance")
                    .requestMatchers("/api/v1/bank-integration/accounts/details").hasAuthority("SCOPE_bank:account:details")
                    
                    // Bank linking operations (critical operations)
                    .requestMatchers("/api/v1/bank-integration/link/initiate").hasAuthority("SCOPE_bank:link:initiate")
                    .requestMatchers("/api/v1/bank-integration/link/confirm").hasAuthority("SCOPE_bank:link:confirm")
                    .requestMatchers("/api/v1/bank-integration/link/status").hasAuthority("SCOPE_bank:link:read")
                    .requestMatchers("/api/v1/bank-integration/unlink").hasAuthority("SCOPE_bank:link:remove")
                    
                    // Fund transfers (highly sensitive)
                    .requestMatchers("/api/v1/bank-integration/transfers/initiate").hasAuthority("SCOPE_bank:transfer:initiate")
                    .requestMatchers("/api/v1/bank-integration/transfers/*/confirm").hasAuthority("SCOPE_bank:transfer:confirm")
                    .requestMatchers("/api/v1/bank-integration/transfers/*/cancel").hasAuthority("SCOPE_bank:transfer:cancel")
                    .requestMatchers("/api/v1/bank-integration/transfers/status/*").hasAuthority("SCOPE_bank:transfer:read")
                    .requestMatchers("/api/v1/bank-integration/transfers/history").hasAuthority("SCOPE_bank:transfer:history")
                    
                    // ACH operations (critical financial operations)
                    .requestMatchers("/api/v1/bank-integration/ach/**").hasAuthority("SCOPE_bank:ach:manage")
                    .requestMatchers("/api/v1/bank-integration/wire/**").hasAuthority("SCOPE_bank:wire:manage")
                    
                    // Direct debit operations
                    .requestMatchers("/api/v1/bank-integration/direct-debit/**").hasAuthority("SCOPE_bank:direct_debit:manage")
                    
                    // Bank provider operations
                    .requestMatchers("/api/v1/bank-integration/providers/plaid/**").hasAuthority("SCOPE_bank:plaid:access")
                    .requestMatchers("/api/v1/bank-integration/providers/yodlee/**").hasAuthority("SCOPE_bank:yodlee:access")
                    .requestMatchers("/api/v1/bank-integration/providers/finicity/**").hasAuthority("SCOPE_bank:finicity:access")
                    .requestMatchers("/api/v1/bank-integration/providers/open-banking/**").hasAuthority("SCOPE_bank:open_banking:access")
                    
                    // Webhooks from external providers (service-to-service)
                    .requestMatchers("/api/v1/bank-integration/webhooks/plaid").hasAuthority("SCOPE_bank:webhook:plaid")
                    .requestMatchers("/api/v1/bank-integration/webhooks/yodlee").hasAuthority("SCOPE_bank:webhook:yodlee")
                    .requestMatchers("/api/v1/bank-integration/webhooks/finicity").hasAuthority("SCOPE_bank:webhook:finicity")
                    
                    // Compliance and reporting
                    .requestMatchers("/api/v1/bank-integration/compliance/**").hasAuthority("SCOPE_bank:compliance:read")
                    .requestMatchers("/api/v1/bank-integration/audit/**").hasAnyRole("COMPLIANCE", "ADMIN")
                    
                    // Admin operations (require admin role)
                    .requestMatchers("/api/v1/bank-integration/admin/providers/configure").hasRole("ADMIN")
                    .requestMatchers("/api/v1/bank-integration/admin/connections/force-refresh").hasRole("ADMIN")
                    .requestMatchers("/api/v1/bank-integration/admin/connections/disconnect").hasRole("ADMIN")
                    .requestMatchers("/api/v1/bank-integration/admin/debug/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/bank-integration/admin/reports/**").hasAnyRole("ADMIN", "FINANCE")
                    
                    // Monitoring and health
                    .requestMatchers("/api/v1/bank-integration/monitor/**").hasAnyRole("ADMIN", "SUPPORT")
                    
                    // Service-to-service communication
                    .requestMatchers("/api/v1/bank-integration/internal/**").hasAuthority("SCOPE_service:internal")
                    
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure bank integration security", e);
            }
        });
    }
}