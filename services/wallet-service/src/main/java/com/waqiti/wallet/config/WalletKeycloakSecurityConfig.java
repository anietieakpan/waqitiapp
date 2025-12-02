package com.waqiti.wallet.config;

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
 * Keycloak security configuration for Wallet Service
 * Extends the base configuration with wallet-specific authorization rules
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class WalletKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain walletKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "wallet-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/wallets/public/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Wallet management endpoints
                .requestMatchers(HttpMethod.GET, "/api/v1/wallets/balance").hasAuthority("SCOPE_wallet:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/wallets/transactions").hasAuthority("SCOPE_wallet:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/wallets/*/balance").hasAuthority("SCOPE_wallet:read")
                
                // Wallet operations
                .requestMatchers(HttpMethod.POST, "/api/v1/wallets/deposit").hasAuthority("SCOPE_wallet:deposit")
                .requestMatchers(HttpMethod.POST, "/api/v1/wallets/withdraw").hasAuthority("SCOPE_wallet:withdraw")
                .requestMatchers(HttpMethod.POST, "/api/v1/wallets/transfer").hasAuthority("SCOPE_wallet:transfer")
                .requestMatchers(HttpMethod.POST, "/api/v1/wallets/freeze").hasAuthority("SCOPE_wallet:freeze")
                .requestMatchers(HttpMethod.POST, "/api/v1/wallets/unfreeze").hasAuthority("SCOPE_wallet:unfreeze")
                
                // Wallet limits and settings
                .requestMatchers("/api/v1/wallets/limits/**").hasAuthority("SCOPE_wallet:limits")
                .requestMatchers("/api/v1/wallets/settings/**").hasAuthority("SCOPE_wallet:settings")
                
                // Virtual wallet operations
                .requestMatchers("/api/v1/wallets/virtual/**").hasAuthority("SCOPE_wallet:virtual")
                
                // Currency operations
                .requestMatchers("/api/v1/wallets/currency/**").hasAuthority("SCOPE_wallet:currency")
                
                // Admin endpoints
                .requestMatchers("/api/v1/wallets/admin/**").hasRole("WALLET_ADMIN")
                .requestMatchers("/api/v1/wallets/reports/**").hasAnyRole("WALLET_ADMIN", "ANALYST")
                .requestMatchers("/api/v1/wallets/audit/**").hasAnyRole("WALLET_ADMIN", "AUDITOR")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/wallets/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}