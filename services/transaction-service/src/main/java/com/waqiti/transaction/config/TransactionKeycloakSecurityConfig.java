package com.waqiti.transaction.config;

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
 * Keycloak security configuration for Transaction Service
 * Extends the base configuration with transaction-specific authorization rules
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class TransactionKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain transactionKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "transaction-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/transactions/public/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Transaction viewing endpoints
                .requestMatchers(HttpMethod.GET, "/api/v1/transactions").hasAuthority("SCOPE_transaction:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/transactions/*").hasAuthority("SCOPE_transaction:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/transactions/history").hasAuthority("SCOPE_transaction:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/transactions/search").hasAuthority("SCOPE_transaction:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/transactions/export").hasAuthority("SCOPE_transaction:export")
                
                // Transaction creation endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/transactions").hasAuthority("SCOPE_transaction:write")
                .requestMatchers(HttpMethod.POST, "/api/v1/transactions/transfer").hasAuthority("SCOPE_transaction:transfer")
                .requestMatchers(HttpMethod.POST, "/api/v1/transactions/batch").hasAuthority("SCOPE_transaction:batch")
                
                // Transaction management endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/transactions/*/cancel").hasAuthority("SCOPE_transaction:cancel")
                .requestMatchers(HttpMethod.POST, "/api/v1/transactions/*/reverse").hasAuthority("SCOPE_transaction:reverse")
                .requestMatchers(HttpMethod.POST, "/api/v1/transactions/*/dispute").hasAuthority("SCOPE_transaction:dispute")
                .requestMatchers(HttpMethod.PUT, "/api/v1/transactions/*/status").hasAuthority("SCOPE_transaction:status")
                
                // Transaction validation and verification
                .requestMatchers("/api/v1/transactions/validate/**").hasAuthority("SCOPE_transaction:validate")
                .requestMatchers("/api/v1/transactions/verify/**").hasAuthority("SCOPE_transaction:verify")
                
                // Transaction analytics
                .requestMatchers("/api/v1/transactions/analytics/**").hasAuthority("SCOPE_transaction:analytics")
                .requestMatchers("/api/v1/transactions/reports/**").hasAnyRole("TRANSACTION_ADMIN", "ANALYST")
                
                // Admin endpoints
                .requestMatchers("/api/v1/transactions/admin/**").hasRole("TRANSACTION_ADMIN")
                .requestMatchers("/api/v1/transactions/audit/**").hasAnyRole("TRANSACTION_ADMIN", "AUDITOR")
                .requestMatchers("/api/v1/transactions/reconciliation/**").hasAnyRole("TRANSACTION_ADMIN", "RECONCILIATION")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/transactions/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}