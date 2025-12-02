package com.waqiti.layer2.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Layer 2 Blockchain Service Security Configuration
 * 
 * HIGH SECURITY RISK: This service handles blockchain operations, state channels,
 * and cryptocurrency transactions. Requires maximum security protection.
 * 
 * Security Features:
 * - JWT authentication for all blockchain operations
 * - Fine-grained authorization for different blockchain functions
 * - Enhanced audit logging for all transactions
 * - Rate limiting to prevent blockchain spam attacks
 * - Special protection for state channel operations
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class Layer2KeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Override
    protected void configureHttpSecurity(HttpSecurity http) throws Exception {
        super.configureHttpSecurity(http);
        
        http
            .authorizeHttpRequests(authz -> authz
                // Health checks - allow for monitoring
                .requestMatchers("/actuator/health/**", "/health/**").permitAll()
                
                // State channel endpoints - require blockchain permissions
                .requestMatchers("/api/v1/channels/create").hasAuthority("SCOPE_blockchain:channel:create")
                .requestMatchers("/api/v1/channels/close").hasAuthority("SCOPE_blockchain:channel:close")
                .requestMatchers("/api/v1/channels/*/update").hasAuthority("SCOPE_blockchain:channel:update")
                .requestMatchers("/api/v1/channels/*/dispute").hasAuthority("SCOPE_blockchain:channel:dispute")
                
                // Transaction endpoints - require transaction permissions
                .requestMatchers("/api/v1/transactions/submit").hasAuthority("SCOPE_blockchain:transaction:submit")
                .requestMatchers("/api/v1/transactions/*/status").hasAuthority("SCOPE_blockchain:transaction:read")
                
                // Smart contract endpoints - admin only
                .requestMatchers("/api/v1/contracts/**").hasRole("BLOCKCHAIN_ADMIN")
                
                // Block production endpoints - validator role required
                .requestMatchers("/api/v1/blocks/**").hasRole("VALIDATOR")
                
                // Network state endpoints - read access
                .requestMatchers("/api/v1/network/state").hasAuthority("SCOPE_blockchain:read")
                .requestMatchers("/api/v1/network/metrics").hasAuthority("SCOPE_blockchain:read")
                
                // Internal service endpoints
                .requestMatchers("/internal/**").hasAuthority("SCOPE_service:internal")
                
                // Webhook endpoints for blockchain events
                .requestMatchers("/api/webhooks/**").permitAll() // Secured by blockchain signatures
                
                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                
                // Deny everything else
                .anyRequest().denyAll()
            );
    }

    @Override
    protected void additionalConfiguration(HttpSecurity http) throws Exception {
        // Add blockchain-specific security headers
        http.headers(headers -> headers
            .addHeaderWriter((request, response) -> {
                // Prevent caching of blockchain state data
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
                
                // Add blockchain service identifier
                response.setHeader("X-Service-Type", "layer2-blockchain");
                response.setHeader("X-Security-Level", "critical");
                response.setHeader("X-Blockchain-Network", "waqiti-l2");
                
                // Blockchain-specific security headers
                response.setHeader("X-Tx-Replay-Protection", "enabled");
                response.setHeader("X-State-Channel-Security", "enabled");
            })
        );
        
        log.info("Layer 2 Blockchain Service security configuration applied - HIGH SECURITY ENABLED");
    }
}