package com.waqiti.rewards.config;

import org.keycloak.adapters.springsecurity.KeycloakConfiguration;
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationProvider;
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Keycloak security configuration for Rewards Service
 * Provides comprehensive security with role-based access control
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@KeycloakConfiguration
public class KeycloakSecurityConfig extends KeycloakWebSecurityConfigurerAdapter {

    /**
     * Configure Keycloak authentication provider
     */
    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) {
        KeycloakAuthenticationProvider keycloakAuthenticationProvider = keycloakAuthenticationProvider();
        keycloakAuthenticationProvider.setGrantedAuthoritiesMapper(new SimpleAuthorityMapper());
        auth.authenticationProvider(keycloakAuthenticationProvider);
    }

    /**
     * Session authentication strategy
     */
    @Bean
    @Override
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
    }

    /**
     * Configure HTTP security
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        
        http
            // CORS configuration
            .cors().configurationSource(corsConfigurationSource())
            .and()
            
            // CSRF protection with cookie token
            .csrf()
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringAntMatchers("/api/v1/rewards/webhook/**")
            .and()
            
            // Session management
            .sessionManagement()
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            .and()
            .and()
            
            // Authorization rules
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/rewards/public/**").permitAll()
                .requestMatchers("/api/v1/rewards/tiers").permitAll()
                
                // User endpoints
                .requestMatchers("/api/v1/rewards/balance/**").hasRole("USER")
                .requestMatchers("/api/v1/rewards/earn/**").hasRole("USER")
                .requestMatchers("/api/v1/rewards/redeem/**").hasRole("USER")
                .requestMatchers("/api/v1/rewards/history/**").hasRole("USER")
                .requestMatchers("/api/v1/rewards/preferences/**").hasRole("USER")
                
                // Merchant endpoints
                .requestMatchers("/api/v1/rewards/merchant/**").hasRole("MERCHANT")
                .requestMatchers("/api/v1/rewards/cashback/configure/**").hasRole("MERCHANT")
                
                // Admin endpoints
                .requestMatchers("/api/v1/rewards/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/rewards/tiers/manage/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/rewards/analytics/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/rewards/adjust/**").hasRole("ADMIN")
                
                // All other endpoints require authentication
                .anyRequest().authenticated())
            .and()
            
            // Security headers
            .headers()
                .frameOptions().deny()
                .xssProtection().xssProtectionEnabled(true)
                .contentSecurityPolicy("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';")
            .and()
            
            // Rate limiting headers
            .headers()
                .addHeaderWriter((request, response) -> {
                    response.setHeader("X-RateLimit-Limit", "1000");
                    response.setHeader("X-RateLimit-Remaining", "999");
                    response.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() + 3600000));
                });
    }

    /**
     * CORS configuration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "https://app.example.com",
            "https://admin.example.com",
            "http://localhost:3000" // Development only
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers",
            "X-CSRF-Token"
        ));
        configuration.setExposedHeaders(Arrays.asList(
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials",
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}