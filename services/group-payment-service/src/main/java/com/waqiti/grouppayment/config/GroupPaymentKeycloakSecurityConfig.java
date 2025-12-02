package com.waqiti.grouppayment.config;

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
 * Keycloak security configuration for Group Payment Service
 * Manages authentication and authorization for split bills and group payment operations
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class GroupPaymentKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain groupPaymentKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "group-payment-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/group-payments/public/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Group/Pool Creation and Management
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/pools/create").hasAuthority("SCOPE_group-payment:create")
                .requestMatchers(HttpMethod.GET, "/api/v1/group-payments/pools").hasAuthority("SCOPE_group-payment:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/group-payments/pools/*").hasAuthority("SCOPE_group-payment:read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/group-payments/pools/*").hasAuthority("SCOPE_group-payment:update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/group-payments/pools/*").hasAuthority("SCOPE_group-payment:delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/pools/*/close").hasAuthority("SCOPE_group-payment:close")
                
                // Member Management
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/pools/*/members/add").hasAuthority("SCOPE_group-payment:member-add")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/group-payments/pools/*/members/*").hasAuthority("SCOPE_group-payment:member-remove")
                .requestMatchers(HttpMethod.GET, "/api/v1/group-payments/pools/*/members").hasAuthority("SCOPE_group-payment:member-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/group-payments/pools/*/members/*/role").hasAuthority("SCOPE_group-payment:member-update")
                
                // Split Bill Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/split").hasAuthority("SCOPE_group-payment:split")
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/split/equal").hasAuthority("SCOPE_group-payment:split")
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/split/percentage").hasAuthority("SCOPE_group-payment:split")
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/split/custom").hasAuthority("SCOPE_group-payment:split")
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/split/itemized").hasAuthority("SCOPE_group-payment:split")
                
                // Payment Collection
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/pools/*/collect").hasAuthority("SCOPE_group-payment:collect")
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/pools/*/remind").hasAuthority("SCOPE_group-payment:remind")
                .requestMatchers(HttpMethod.GET, "/api/v1/group-payments/pools/*/status").hasAuthority("SCOPE_group-payment:status")
                .requestMatchers(HttpMethod.GET, "/api/v1/group-payments/pools/*/balances").hasAuthority("SCOPE_group-payment:balances")
                
                // Contribution Management
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/contribute").hasAuthority("SCOPE_group-payment:contribute")
                .requestMatchers(HttpMethod.GET, "/api/v1/group-payments/contributions/my").hasAuthority("SCOPE_group-payment:contribution-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/group-payments/contributions/pending").hasAuthority("SCOPE_group-payment:contribution-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/contributions/*/approve").hasAuthority("SCOPE_group-payment:contribution-approve")
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/contributions/*/reject").hasAuthority("SCOPE_group-payment:contribution-reject")
                
                // Settlement Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/pools/*/settle").hasAuthority("SCOPE_group-payment:settle")
                .requestMatchers(HttpMethod.GET, "/api/v1/group-payments/pools/*/settlement-summary").hasAuthority("SCOPE_group-payment:settlement-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/pools/*/disburse").hasAuthority("SCOPE_group-payment:disburse")
                
                // Expense Tracking
                .requestMatchers(HttpMethod.POST, "/api/v1/group-payments/expenses/add").hasAuthority("SCOPE_group-payment:expense-add")
                .requestMatchers(HttpMethod.GET, "/api/v1/group-payments/expenses").hasAuthority("SCOPE_group-payment:expense-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/group-payments/expenses/*").hasAuthority("SCOPE_group-payment:expense-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/group-payments/expenses/*").hasAuthority("SCOPE_group-payment:expense-delete")
                
                // Recurring Group Payments
                .requestMatchers("/api/v1/group-payments/recurring/**").hasAuthority("SCOPE_group-payment:recurring")
                
                // Reports and Analytics
                .requestMatchers("/api/v1/group-payments/reports/**").hasAuthority("SCOPE_group-payment:reports")
                .requestMatchers("/api/v1/group-payments/analytics/**").hasAuthority("SCOPE_group-payment:analytics")
                
                // Notifications
                .requestMatchers("/api/v1/group-payments/notifications/**").hasAuthority("SCOPE_group-payment:notifications")
                
                // Admin Operations
                .requestMatchers("/api/v1/group-payments/admin/**").hasRole("GROUP_PAYMENT_ADMIN")
                .requestMatchers("/api/v1/group-payments/config/**").hasRole("GROUP_PAYMENT_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/group-payments/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}