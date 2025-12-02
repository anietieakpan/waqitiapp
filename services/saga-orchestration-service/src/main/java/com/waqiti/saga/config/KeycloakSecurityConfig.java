package com.waqiti.saga.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Keycloak security configuration for Saga Orchestration Service.
 * CRITICAL SERVICE - Manages distributed transactions across microservices.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Order(1)
public class KeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Override
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "saga-orchestration-service", httpSecurity -> {
            try {
                httpSecurity.authorizeHttpRequests(authz -> authz
                    // Health checks
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    
                    // Saga orchestration operations (critical for data consistency)
                    .requestMatchers("/api/v1/saga/transactions/initiate").hasAuthority("SCOPE_saga:transaction:initiate")
                    .requestMatchers("/api/v1/saga/transactions/*/step").hasAuthority("SCOPE_saga:transaction:step")
                    .requestMatchers("/api/v1/saga/transactions/*/compensate").hasAuthority("SCOPE_saga:transaction:compensate")
                    .requestMatchers("/api/v1/saga/transactions/*/complete").hasAuthority("SCOPE_saga:transaction:complete")
                    .requestMatchers("/api/v1/saga/transactions/*/abort").hasAuthority("SCOPE_saga:transaction:abort")
                    .requestMatchers("/api/v1/saga/transactions/*/status").hasAuthority("SCOPE_saga:transaction:read")
                    
                    // Saga pattern management
                    .requestMatchers("/api/v1/saga/patterns/**").hasAuthority("SCOPE_saga:patterns:manage")
                    .requestMatchers("/api/v1/saga/orchestrators/**").hasAuthority("SCOPE_saga:orchestrators:manage")
                    
                    // Service coordination
                    .requestMatchers("/api/v1/saga/coordination/**").hasAuthority("SCOPE_saga:coordination:manage")
                    
                    // Admin and monitoring
                    .requestMatchers("/api/v1/saga/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/saga/monitor/**").hasAnyRole("ADMIN", "SUPPORT")
                    
                    // Service-to-service calls
                    .requestMatchers("/api/v1/saga/internal/**").hasAuthority("SCOPE_service:internal")
                    
                    .anyRequest().authenticated()
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure saga security", e);
            }
        });
    }
}