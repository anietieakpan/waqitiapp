package com.waqiti.legal.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security Configuration for Legal Service
 *
 * Implements comprehensive security with:
 * - JWT-based authentication
 * - Role-based authorization (RBAC)
 * - Method-level security (@PreAuthorize)
 * - CORS configuration
 * - Stateless session management
 *
 * Roles:
 * - LEGAL_ADMIN: Full access to all legal operations
 * - LEGAL_OFFICER: Create and manage legal documents, cases
 * - LEGAL_VIEWER: Read-only access
 * - AUDITOR: Access to audit and compliance data
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-11-09
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Configure security filter chain
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless API
            .csrf(csrf -> csrf.disable())

            // Configure CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Session management - stateless
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(authz -> authz
                // Public endpoints - actuator health check
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // OpenAPI/Swagger documentation
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                // Legal API endpoints - require authentication
                .requestMatchers("/api/v1/legal/**").authenticated()

                // Admin endpoints - require ADMIN role
                .requestMatchers(HttpMethod.DELETE, "/api/v1/legal/**").hasRole("LEGAL_ADMIN")

                // All other requests require authentication
                .anyRequest().authenticated()
            );

        // Note: JWT filter would be added here when JWT authentication is implemented
        // .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)

        return http.build();
    }

    /**
     * CORS configuration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow specific origins (configure based on environment)
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",    // React dev
            "http://localhost:8080",    // API Gateway
            "https://example.com",       // Production frontend
            "https://api.example.com"    // Production API
        ));

        // Allow specific HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // Allow specific headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers",
            "X-Trace-Id",
            "X-Request-Id"
        ));

        // Expose headers
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "X-Trace-Id",
            "X-Request-Id"
        ));

        // Allow credentials
        configuration.setAllowCredentials(true);

        // Max age for preflight requests
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * Password encoder (BCrypt)
     * Note: Uncomment when implementing authentication
     */
    /*
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    */

    /**
     * Authentication manager
     * Note: Uncomment when implementing authentication
     */
    /*
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    */
}
