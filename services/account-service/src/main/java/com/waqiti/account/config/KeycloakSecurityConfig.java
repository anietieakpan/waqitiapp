package com.waqiti.account.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Keycloak security configuration for Account Service.
 * This is a CRITICAL service handling core account management.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Order(1)
public class KeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Override
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "account-service", httpSecurity -> {
            try {
                httpSecurity.authorizeHttpRequests(authz -> authz
                    // Public endpoints
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/api/v1/accounts/public/**").permitAll()
                    .requestMatchers("/api/v1/accounts/verify/**").permitAll()
                    
                    // Account creation (requires basic authentication)
                    .requestMatchers("/api/v1/accounts/register").hasAuthority("SCOPE_account:create")
                    
                    // Account management (requires account scope)
                    .requestMatchers("/api/v1/accounts/profile").hasAuthority("SCOPE_account:read")
                    .requestMatchers("/api/v1/accounts/profile/update").hasAuthority("SCOPE_account:update")
                    .requestMatchers("/api/v1/accounts/settings/**").hasAuthority("SCOPE_account:update")
                    
                    // Account balance and transactions (sensitive operations)
                    .requestMatchers("/api/v1/accounts/balance").hasAuthority("SCOPE_account:balance:read")
                    .requestMatchers("/api/v1/accounts/transactions/**").hasAuthority("SCOPE_account:transactions:read")
                    .requestMatchers("/api/v1/accounts/statements/**").hasAuthority("SCOPE_account:statements:read")
                    
                    // Account limits and controls
                    .requestMatchers("/api/v1/accounts/limits/**").hasAuthority("SCOPE_account:limits:manage")
                    .requestMatchers("/api/v1/accounts/controls/**").hasAuthority("SCOPE_account:controls:manage")
                    
                    // Account linking and external accounts
                    .requestMatchers("/api/v1/accounts/link/**").hasAuthority("SCOPE_account:link:manage")
                    .requestMatchers("/api/v1/accounts/external/**").hasAuthority("SCOPE_account:external:manage")
                    
                    // Account closure and deactivation (critical operations)
                    .requestMatchers("/api/v1/accounts/close").hasAuthority("SCOPE_account:close")
                    .requestMatchers("/api/v1/accounts/deactivate").hasAuthority("SCOPE_account:deactivate")
                    .requestMatchers("/api/v1/accounts/reactivate").hasAuthority("SCOPE_account:reactivate")
                    
                    // Beneficiary management
                    .requestMatchers("/api/v1/accounts/beneficiaries/**").hasAuthority("SCOPE_account:beneficiaries:manage")
                    
                    // Account verification and KYC
                    .requestMatchers("/api/v1/accounts/kyc/**").hasAuthority("SCOPE_account:kyc:manage")
                    .requestMatchers("/api/v1/accounts/verification/**").hasAuthority("SCOPE_account:verification:manage")
                    
                    // Admin endpoints (require admin role)
                    .requestMatchers("/api/v1/accounts/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/accounts/admin/freeze/**").hasAuthority("SCOPE_account:admin:freeze")
                    .requestMatchers("/api/v1/accounts/admin/unfreeze/**").hasAuthority("SCOPE_account:admin:unfreeze")
                    .requestMatchers("/api/v1/accounts/admin/audit/**").hasAuthority("SCOPE_account:admin:audit")
                    .requestMatchers("/api/v1/accounts/admin/reports/**").hasAuthority("SCOPE_account:admin:reports")
                    
                    // Support operations
                    .requestMatchers("/api/v1/accounts/support/**").hasAnyRole("SUPPORT", "ADMIN")
                    
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure security", e);
            }
        });
    }
}