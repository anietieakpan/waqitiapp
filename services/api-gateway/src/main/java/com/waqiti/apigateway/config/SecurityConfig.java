/**
 * File: ./api-gateway/src/src/main/java/com/waqiti/gateway/config/SecurityConfig.java
 */
package com.waqiti.apigateway.config;

import com.waqiti.apigateway.security.CsrfCookieWebFilter;
import com.waqiti.apigateway.security.SpaCsrfTokenRequestHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final Environment environment;
    
    @Value("${cors.allowed.origins:}")
    private String allowedOrigins;
    
    @Value("${cors.allowed.development.origins:http://localhost:3000,http://localhost:3001,http://localhost:8080}")
    private String developmentOrigins;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        // Create SPA-compatible CSRF token handler
        SpaCsrfTokenRequestHandler csrfHandler = new SpaCsrfTokenRequestHandler();

        return http
                // CSRF protection - enabled for production security with SPA support
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))

                // Add CSRF cookie filter to ensure token generation
                .addFilterAfter(new CsrfCookieWebFilter(),
                        org.springframework.security.web.server.csrf.CsrfWebFilter.class)

                // CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Security headers
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions
                                .mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; script-src 'self' https://cdnjs.cloudflare.com"))

                        .referrerPolicy(referrerPolicy -> referrerPolicy
                                .policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(permissions -> permissions
                                .policy("camera=(), microphone=(), geolocation=()"))
                )

                // Authorization rules
                .authorizeExchange(exchanges -> exchanges
                        // Public endpoints
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/users/register").permitAll()
                        .pathMatchers("/api/v1/users/verify/**").permitAll()
                        .pathMatchers("/api/v1/users/password/reset/**").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Admin-only endpoints
                        .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .pathMatchers("/actuator/**").hasRole("ADMIN")

                        // Protected endpoints - let the services handle authorization
                        .anyExchange().authenticated()
                )

                // Session management - stateless for REST API
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // SECURITY FIX: Environment-specific CORS configuration
        List<String> origins = new ArrayList<>();
        
        // Production origins - always allowed
        origins.addAll(List.of(
            "https://app.example.com",
            "https://admin.example.com",
            "https://mobile.example.com"
        ));
        
        // Add custom origins from configuration if specified
        if (allowedOrigins != null && !allowedOrigins.trim().isEmpty()) {
            String[] customOrigins = allowedOrigins.split(",");
            for (String origin : customOrigins) {
                origins.add(origin.trim());
            }
        }
        
        // Development origins - only in non-production environments
        if (isDevelopmentEnvironment()) {
            log.warn("SECURITY WARNING: Development CORS origins enabled. This should not happen in production!");
            String[] devOrigins = developmentOrigins.split(",");
            for (String origin : devOrigins) {
                origins.add(origin.trim());
            }
        }
        
        configuration.setAllowedOrigins(origins);
        
        // Restricted origin patterns for subdomains
        configuration.setAllowedOriginPatterns(List.of("https://*.waqiti.com"));
        
        // Allowed HTTP methods
        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // Security headers - restricted list
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", 
            "Content-Type", 
            "X-Requested-With",
            "Idempotency-Key",
            "X-Request-ID",
            "X-Correlation-ID"
        ));
        
        // Exposed headers for client access
        configuration.setExposedHeaders(Arrays.asList(
            "X-Request-ID", 
            "X-Rate-Limit-Remaining",
            "X-Rate-Limit-Reset"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // 1 hour
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        log.info("CORS configured with origins: {}", origins);
        
        return source;
    }
    
    /**
     * Determines if the current environment is development
     */
    private boolean isDevelopmentEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        return Arrays.asList(activeProfiles).contains("dev") ||
               Arrays.asList(activeProfiles).contains("development") ||
               Arrays.asList(activeProfiles).contains("local") ||
               (activeProfiles.length == 0); // Default profile is considered development
    }
}