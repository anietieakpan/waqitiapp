package com.waqiti.config.security;

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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak security configuration for Config Service
 * CRITICAL INFRASTRUCTURE SERVICE - Configuration management
 * Manages all application configurations, secrets, and environment-specific settings
 * Essential for platform operations and service configuration
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class ConfigKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain configKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring CRITICAL Keycloak security for Config Service - Infrastructure Component");

        return createKeycloakSecurityFilterChain(http, "config-service", httpSecurity -> {
            // Configure CSRF protection for state-changing operations
            CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
            csrfHandler.setCsrfRequestAttributeName("_csrf");

            httpSecurity.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers(
                    // Health checks don't need CSRF (read-only)
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info"
                    // All other endpoints require CSRF token for POST/PUT/DELETE
                )
            );

            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Very limited
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                
                // Configuration Access - CRITICAL OPERATIONS
                .requestMatchers(HttpMethod.GET, "/*/*").hasAuthority("SCOPE_config:read")
                .requestMatchers(HttpMethod.GET, "/*/*/*").hasAuthority("SCOPE_config:read")
                .requestMatchers(HttpMethod.GET, "/*/*/*/*").hasAuthority("SCOPE_config:read")
                .requestMatchers(HttpMethod.GET, "/*/*/master/**").hasAuthority("SCOPE_config:read-production")
                .requestMatchers(HttpMethod.GET, "/*/*/main/**").hasAuthority("SCOPE_config:read-production")
                .requestMatchers(HttpMethod.GET, "/*/*/prod/**").hasAuthority("SCOPE_config:read-production")
                
                // Environment-Specific Configuration
                .requestMatchers(HttpMethod.GET, "/*/*/dev/**").hasAuthority("SCOPE_config:read-dev")
                .requestMatchers(HttpMethod.GET, "/*/*/test/**").hasAuthority("SCOPE_config:read-test")
                .requestMatchers(HttpMethod.GET, "/*/*/staging/**").hasAuthority("SCOPE_config:read-staging")
                
                // Encrypted Configuration Access
                .requestMatchers(HttpMethod.POST, "/encrypt").hasAuthority("SCOPE_config:encrypt")
                .requestMatchers(HttpMethod.POST, "/decrypt").hasAuthority("SCOPE_config:decrypt")
                .requestMatchers(HttpMethod.POST, "/encrypt/**").hasAuthority("SCOPE_config:encrypt")
                .requestMatchers(HttpMethod.POST, "/decrypt/**").hasAuthority("SCOPE_config:decrypt")
                
                // Configuration Refresh - Service Operations
                .requestMatchers(HttpMethod.POST, "/actuator/refresh").hasAuthority("SCOPE_config:refresh")
                .requestMatchers(HttpMethod.POST, "/actuator/bus-refresh").hasAuthority("SCOPE_config:bus-refresh")
                .requestMatchers(HttpMethod.POST, "/actuator/bus-refresh/**").hasAuthority("SCOPE_config:bus-refresh")
                
                // Admin Operations - Configuration Management
                .requestMatchers(HttpMethod.GET, "/admin/config-sources").hasRole("CONFIG_ADMIN")
                .requestMatchers(HttpMethod.POST, "/admin/config/reload").hasRole("CONFIG_ADMIN")
                .requestMatchers(HttpMethod.GET, "/admin/config/audit").hasRole("CONFIG_ADMIN")
                .requestMatchers(HttpMethod.POST, "/admin/config/backup").hasRole("CONFIG_ADMIN")
                .requestMatchers(HttpMethod.POST, "/admin/config/restore").hasRole("CONFIG_ADMIN")
                
                // Admin Operations - Vault Management
                .requestMatchers(HttpMethod.GET, "/admin/vault/status").hasRole("VAULT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/admin/vault/unseal").hasRole("VAULT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/admin/vault/seal").hasRole("VAULT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/admin/vault/rotate-keys").hasRole("VAULT_ADMIN")
                
                // Admin Operations - Git Repository
                .requestMatchers(HttpMethod.POST, "/admin/git/pull").hasRole("CONFIG_ADMIN")
                .requestMatchers(HttpMethod.POST, "/admin/git/push").hasRole("CONFIG_ADMIN")
                .requestMatchers(HttpMethod.GET, "/admin/git/status").hasRole("CONFIG_ADMIN")
                .requestMatchers(HttpMethod.POST, "/admin/git/reset").hasRole("CONFIG_ADMIN")
                
                // Admin Operations - Encryption
                .requestMatchers(HttpMethod.POST, "/admin/encrypt/rotate-key").hasRole("ENCRYPTION_ADMIN")
                .requestMatchers(HttpMethod.GET, "/admin/encrypt/key-info").hasRole("ENCRYPTION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/admin/encrypt/re-encrypt-all").hasRole("ENCRYPTION_ADMIN")
                
                // Monitoring & Metrics - Restricted Access
                .requestMatchers("/actuator/prometheus").hasRole("MONITORING")
                .requestMatchers("/actuator/metrics").hasRole("MONITORING")
                .requestMatchers("/actuator/env").hasRole("CONFIG_ADMIN")
                .requestMatchers("/actuator/configprops").hasRole("CONFIG_ADMIN")
                .requestMatchers("/actuator/**").hasRole("CONFIG_ADMIN")
                
                // High-Security Internal endpoints
                .requestMatchers("/internal/**").hasRole("SERVICE")
                
                // All other endpoints require authentication - NO EXCEPTIONS
                .anyRequest().authenticated()
            );
        });
    }
}