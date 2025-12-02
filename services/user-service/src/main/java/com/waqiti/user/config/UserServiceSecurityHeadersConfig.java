package com.waqiti.user.config;

import com.waqiti.common.security.SecurityHeadersConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.annotation.Order;
import lombok.extern.slf4j.Slf4j;

/**
 * User service specific security headers configuration
 * Includes authentication and session management specific headers
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class UserServiceSecurityHeadersConfig {
    
    private final SecurityHeadersConfiguration securityHeadersConfiguration;
    
    public UserServiceSecurityHeadersConfig(SecurityHeadersConfiguration securityHeadersConfiguration) {
        this.securityHeadersConfiguration = securityHeadersConfiguration;
    }
    
    @Bean
    @Order(1)
    public SecurityFilterChain userServiceSecurityFilterChain(HttpSecurity http) throws Exception {
        // Apply common security headers
        securityHeadersConfiguration.configureSecurityHeaders(http);
        
        // Add user service specific configurations
        http.headers(headers -> headers
            // Strict CSP for authentication pages
            .contentSecurityPolicy(csp -> csp
                .policyDirectives(getAuthenticationCsp())
            )
            
            // Additional headers for authentication security
            .addHeaderWriter((request, response) -> {
                String path = request.getRequestURI();
                
                // Authentication-specific headers
                if (path.startsWith("/auth/") || path.startsWith("/api/v1/auth/")) {
                    response.setHeader("X-Auth-Security", "enhanced");
                    response.setHeader("X-Password-Policy", "strong");
                    response.setHeader("X-Session-Security", "strict");
                    response.setHeader("X-MFA-Required", "conditional");
                    
                    // Prevent caching of authentication responses
                    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private, max-age=0");
                    response.setHeader("Pragma", "no-cache");
                    response.setHeader("Expires", "0");
                }
                
                // User profile endpoints
                if (path.startsWith("/api/v1/users/")) {
                    response.setHeader("X-Privacy-Protection", "enabled");
                    response.setHeader("X-Data-Classification", "PII");
                    response.setHeader("X-GDPR-Compliance", "active");
                }
                
                // OAuth2 endpoints
                if (path.startsWith("/oauth2/")) {
                    response.setHeader("X-OAuth2-Security", "enabled");
                    response.setHeader("X-Token-Security", "enhanced");
                    response.setHeader("X-PKCE-Required", "true");
                }
            })
        );
        
        // Configure authentication endpoints
        http.authorizeHttpRequests(authz -> authz
            // Public authentication endpoints
            .requestMatchers("/auth/login", "/auth/register", "/auth/forgot-password").permitAll()
            .requestMatchers("/auth/verify-email", "/auth/reset-password").permitAll()
            .requestMatchers("/oauth2/authorization/**").permitAll()
            
            // MFA endpoints
            .requestMatchers("/auth/mfa/setup").authenticated()
            .requestMatchers("/auth/mfa/verify").authenticated()
            .requestMatchers("/auth/mfa/disable").authenticated()
            
            // User management endpoints
            .requestMatchers("/api/v1/users/profile/**").authenticated()
            .requestMatchers("/api/v1/users/settings/**").authenticated()
            .requestMatchers("/api/v1/users/security/**").authenticated()
            
            // Admin endpoints
            .requestMatchers("/api/v1/users/admin/**").hasRole("USER_ADMIN")
            .requestMatchers("/api/v1/users/audit/**").hasAnyRole("USER_ADMIN", "AUDITOR")
            
            // Internal service endpoints
            .requestMatchers("/internal/users/**").hasRole("SERVICE")
            
            // Deny all other requests
            .anyRequest().denyAll()
        );
        
        // Session management
        http.sessionManagement(session -> session
            .sessionFixation().newSession()
            .maximumSessions(1)
            .maxSessionsPreventsLogin(true)
        );
        
        return http.build();
    }
    
    /**
     * Get authentication-specific Content Security Policy
     */
    private String getAuthenticationCsp() {
        return "default-src 'self'; " +
               "script-src 'self' 'strict-dynamic' 'nonce-{nonce}'; " +
               "style-src 'self' 'unsafe-inline'; " +
               "img-src 'self' data: https:; " +
               "font-src 'self'; " +
               "connect-src 'self' https://api.example.com; " +
               "form-action 'self'; " +
               "frame-ancestors 'none'; " +
               "base-uri 'self'; " +
               "object-src 'none'; " +
               "require-trusted-types-for 'script'; " +
               "block-all-mixed-content; " +
               "upgrade-insecure-requests; " +
               "report-uri https://csp.example.com/auth-report";
    }
}