package com.waqiti.card.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityConfig - Security configuration for Card Service
 *
 * Enables method-level security with @PreAuthorize annotations
 * Configures stateless session management for REST API
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-21
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Health check endpoints - public access
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Swagger/OpenAPI endpoints - configurable access
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
