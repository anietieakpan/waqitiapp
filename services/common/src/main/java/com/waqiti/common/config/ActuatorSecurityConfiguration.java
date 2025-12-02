package com.waqiti.common.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Spring Boot Actuator endpoints.
 *
 * <p>This configuration defines security rules for all actuator endpoints:
 * <ul>
 *   <li>Health endpoints - public access (for Kubernetes probes)</li>
 *   <li>Info endpoint - public access</li>
 *   <li>Prometheus endpoint - public access (for Prometheus scraping)</li>
 *   <li>All other actuator endpoints - require ACTUATOR role</li>
 * </ul>
 *
 * <p><b>Security Model:</b>
 * <pre>
 * Public Endpoints (no authentication):
 *   - /actuator/health/**
 *   - /actuator/health/liveness
 *   - /actuator/health/readiness
 *   - /actuator/info
 *   - /actuator/prometheus
 *
 * Protected Endpoints (require ACTUATOR role):
 *   - /actuator/metrics
 *   - /actuator/env
 *   - /actuator/configprops
 *   - /actuator/beans
 *   - /actuator/mappings
 *   - /actuator/loggers
 *   - /actuator/threaddump
 *   - /actuator/heapdump
 *   - /actuator/**
 * </pre>
 *
 * <p><b>Usage:</b>
 * This configuration is automatically applied when the common module is included
 * as a dependency. No additional configuration is required.
 *
 * <p><b>Kubernetes Integration:</b>
 * Health endpoints must be public to allow Kubernetes liveness and readiness probes
 * to function without authentication.
 *
 * <p><b>Prometheus Integration:</b>
 * The Prometheus endpoint must be public to allow Prometheus to scrape metrics
 * without requiring authentication credentials.
 *
 * @author Platform Engineering Team
 * @version 1.0.0
 * @since 2025-11-23
 */
@Configuration
@EnableWebSecurity
@Order(1) // Apply this security config before the main application security config
public class ActuatorSecurityConfiguration {

    /**
     * Configures security for actuator endpoints.
     *
     * <p>This security filter chain is evaluated before the main application
     * security configuration due to @Order(1).
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            // Only apply this configuration to actuator endpoints
            .securityMatcher(EndpointRequest.toAnyEndpoint())

            // Configure authorization rules
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints - no authentication required
                // Health endpoint (for Kubernetes liveness/readiness probes)
                .requestMatchers(EndpointRequest.to(HealthEndpoint.class))
                    .permitAll()

                // Info endpoint (general application information)
                .requestMatchers(EndpointRequest.to(InfoEndpoint.class))
                    .permitAll()

                // Prometheus endpoint (for metrics scraping)
                .requestMatchers(EndpointRequest.to(PrometheusScrapeEndpoint.class))
                    .permitAll()

                // Protected endpoints - require ACTUATOR role
                // All other actuator endpoints
                .requestMatchers(EndpointRequest.toAnyEndpoint())
                    .hasRole("ACTUATOR")
            )

            // Use HTTP Basic authentication for protected endpoints
            .httpBasic(httpBasic -> httpBasic
                .realmName("Waqiti Actuator Endpoints")
            )

            // Disable CSRF for actuator endpoints (stateless API)
            .csrf(csrf -> csrf.disable())

            // Disable form login (not needed for actuator endpoints)
            .formLogin(form -> form.disable())

            // Disable logout (not needed for actuator endpoints)
            .logout(logout -> logout.disable());

        return http.build();
    }

    /**
     * Note: User authentication for the ACTUATOR role should be configured
     * in the main application security configuration or via an external
     * authentication provider (e.g., Keycloak, LDAP).
     *
     * Example configuration in application.yml:
     *
     * <pre>
     * spring:
     *   security:
     *     user:
     *       name: ${ACTUATOR_USERNAME:admin}
     *       password: ${VAULT_ACTUATOR_PASSWORD}
     *       roles: ACTUATOR
     * </pre>
     *
     * For production, use Vault or Kubernetes secrets to store credentials:
     *
     * <pre>
     * # Kubernetes Secret
     * apiVersion: v1
     * kind: Secret
     * metadata:
     *   name: actuator-credentials
     * type: Opaque
     * data:
     *   username: YWRtaW4=  # base64 encoded
     *   password: ${VAULT_ACTUATOR_PASSWORD}
     * </pre>
     */
}
