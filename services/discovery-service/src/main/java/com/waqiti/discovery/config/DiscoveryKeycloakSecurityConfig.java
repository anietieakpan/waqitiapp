package com.waqiti.discovery.config;

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
 * Keycloak security configuration for Discovery Service (Eureka)
 * CRITICAL INFRASTRUCTURE SERVICE - Service registry and discovery
 * Manages all microservice registrations and health checks
 * Essential for platform operations and service-to-service communication
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class DiscoveryKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain discoveryKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring CRITICAL Keycloak security for Discovery Service - Infrastructure Component");
        
        return createKeycloakSecurityFilterChain(http, "discovery-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Eureka UI and basic health (limited access)
                .requestMatchers("/", "/eureka", "/eureka/**").hasRole("INFRASTRUCTURE_ADMIN")
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                
                // Service Registration - CRITICAL OPERATIONS
                .requestMatchers(HttpMethod.POST, "/eureka/apps/*").hasAuthority("SCOPE_discovery:service-register")
                .requestMatchers(HttpMethod.PUT, "/eureka/apps/*/*").hasAuthority("SCOPE_discovery:service-heartbeat")
                .requestMatchers(HttpMethod.DELETE, "/eureka/apps/*/*").hasAuthority("SCOPE_discovery:service-deregister")
                .requestMatchers(HttpMethod.PUT, "/eureka/apps/*/status").hasAuthority("SCOPE_discovery:service-status")
                
                // Service Discovery & Registry
                .requestMatchers(HttpMethod.GET, "/eureka/apps").hasAuthority("SCOPE_discovery:registry-read")
                .requestMatchers(HttpMethod.GET, "/eureka/apps/*").hasAuthority("SCOPE_discovery:service-read")
                .requestMatchers(HttpMethod.GET, "/eureka/apps/*/*").hasAuthority("SCOPE_discovery:instance-read")
                .requestMatchers(HttpMethod.GET, "/eureka/instances/*").hasAuthority("SCOPE_discovery:instance-read")
                
                // Registry Management
                .requestMatchers(HttpMethod.GET, "/eureka/vips/*").hasAuthority("SCOPE_discovery:vip-read")
                .requestMatchers(HttpMethod.GET, "/eureka/svips/*").hasAuthority("SCOPE_discovery:svip-read")
                .requestMatchers(HttpMethod.GET, "/eureka/status").hasAuthority("SCOPE_discovery:status-read")
                
                // Admin Operations - Registry Management
                .requestMatchers(HttpMethod.POST, "/eureka/admin/registry/clear").hasRole("INFRASTRUCTURE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/eureka/admin/registry/reset").hasRole("INFRASTRUCTURE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/eureka/admin/registry/stats").hasRole("INFRASTRUCTURE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/eureka/admin/instances/*/override-status").hasRole("INFRASTRUCTURE_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/eureka/admin/instances/*/override-status").hasRole("INFRASTRUCTURE_ADMIN")
                
                // Admin Operations - System Health
                .requestMatchers(HttpMethod.GET, "/eureka/admin/health").hasRole("INFRASTRUCTURE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/eureka/admin/metrics").hasRole("INFRASTRUCTURE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/eureka/admin/shutdown").hasRole("INFRASTRUCTURE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/eureka/admin/restart").hasRole("INFRASTRUCTURE_ADMIN")
                
                // Admin Operations - Configuration
                .requestMatchers(HttpMethod.GET, "/eureka/admin/config").hasRole("INFRASTRUCTURE_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/eureka/admin/config").hasRole("INFRASTRUCTURE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/eureka/admin/config/refresh").hasRole("INFRASTRUCTURE_ADMIN")
                
                // Monitoring endpoints - Restricted
                .requestMatchers("/actuator/prometheus").hasRole("MONITORING")
                .requestMatchers("/actuator/metrics").hasRole("MONITORING")
                .requestMatchers("/actuator/**").hasRole("INFRASTRUCTURE_ADMIN")
                
                // High-Security Internal endpoints
                .requestMatchers("/internal/**").hasRole("SERVICE")
                
                // All other endpoints require authentication - NO EXCEPTIONS
                .anyRequest().authenticated()
            );
        });
    }
}