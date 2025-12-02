package com.waqiti.corebanking.config;

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
 * Keycloak security configuration for Core Banking Service
 * Manages authentication and authorization for banking operations
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class CoreBankingKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain coreBankingKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "core-banking-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/banking/public/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Account management
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts").hasAuthority("SCOPE_banking:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*").hasAuthority("SCOPE_banking:read")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts").hasAuthority("SCOPE_banking:create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/*").hasAuthority("SCOPE_banking:update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/accounts/*").hasAuthority("SCOPE_banking:delete")
                
                // Balance operations
                .requestMatchers("/api/v1/accounts/*/balance").hasAuthority("SCOPE_banking:balance")
                .requestMatchers("/api/v1/accounts/*/balance-history").hasAuthority("SCOPE_banking:balance")
                
                // Transaction operations
                .requestMatchers(HttpMethod.POST, "/api/v1/transactions/debit").hasAuthority("SCOPE_banking:debit")
                .requestMatchers(HttpMethod.POST, "/api/v1/transactions/credit").hasAuthority("SCOPE_banking:credit")
                .requestMatchers(HttpMethod.POST, "/api/v1/transactions/transfer").hasAuthority("SCOPE_banking:transfer")
                .requestMatchers(HttpMethod.POST, "/api/v1/transactions/batch").hasAuthority("SCOPE_banking:batch")
                
                // Statement operations
                .requestMatchers("/api/v1/statements/**").hasAuthority("SCOPE_banking:statements")
                .requestMatchers("/api/v1/statements/download/**").hasAuthority("SCOPE_banking:export")
                
                // Interest calculations
                .requestMatchers("/api/v1/interest/**").hasAuthority("SCOPE_banking:interest")
                
                // Fee management
                .requestMatchers("/api/v1/fees/**").hasAuthority("SCOPE_banking:fees")
                
                // Currency exchange
                .requestMatchers("/api/v1/exchange/**").hasAuthority("SCOPE_banking:exchange")
                .requestMatchers("/api/v1/exchange/rates").permitAll() // Public exchange rates
                
                // Loan operations
                .requestMatchers("/api/v1/loans/**").hasAuthority("SCOPE_banking:loans")
                
                // Deposit operations
                .requestMatchers("/api/v1/deposits/**").hasAuthority("SCOPE_banking:deposits")
                
                // Card operations
                .requestMatchers("/api/v1/cards/**").hasAuthority("SCOPE_banking:cards")
                
                // Compliance and regulatory
                .requestMatchers("/api/v1/compliance/**").hasAnyRole("COMPLIANCE_OFFICER", "BANKING_ADMIN")
                .requestMatchers("/api/v1/regulatory/**").hasAnyRole("COMPLIANCE_OFFICER", "BANKING_ADMIN")
                
                // Admin operations
                .requestMatchers("/api/v1/banking/admin/**").hasRole("BANKING_ADMIN")
                .requestMatchers("/api/v1/banking/reports/**").hasAnyRole("BANKING_ADMIN", "ANALYST")
                .requestMatchers("/api/v1/banking/audit/**").hasAnyRole("BANKING_ADMIN", "AUDITOR")
                .requestMatchers("/api/v1/banking/reconciliation/**").hasAnyRole("BANKING_ADMIN", "RECONCILIATION")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/banking/**").hasRole("SERVICE")
                .requestMatchers("/internal/accounts/**").hasRole("SERVICE")
                .requestMatchers("/internal/transactions/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}