package com.waqiti.security.config;

import com.waqiti.security.csrf.CsrfProtectionFilter;
import com.waqiti.security.csrf.CsrfTokenRepository;
import com.waqiti.security.filter.JwtAuthenticationFilter;
import com.waqiti.security.filter.RateLimitingFilter;
import com.waqiti.security.handler.CustomAccessDeniedHandler;
import com.waqiti.security.handler.CustomAuthenticationEntryPoint;
import com.waqiti.security.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security Configuration with CSRF Protection for Financial Services
 *
 * CRITICAL SECURITY FEATURES:
 * - CSRF protection enabled for all financial endpoints
 * - Custom CSRF token repository using Redis for distributed systems
 * - Token rotation on sensitive operations
 * - PCI DSS 6.5.9 compliance (CSRF protection)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CsrfTokenRepository csrfTokenRepository;
    private final CsrfProtectionFilter csrfProtectionFilter;

    @Value("${security.csrf.enabled:true}")
    private boolean csrfEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CRITICAL: Enable CSRF protection for financial endpoints
            // Using custom Redis-based token repository for distributed systems
            .csrf(csrf -> {
                if (csrfEnabled) {
                    csrf.csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers(
                            "/actuator/health/**",
                            "/actuator/info",
                            "/actuator/prometheus",
                            "/api/v1/auth/login",
                            "/api/v1/auth/register",
                            "/api/v1/auth/refresh",
                            "/api/v1/webhooks/**",
                            "/internal/**",
                            "/swagger-ui/**",
                            "/v3/api-docs/**"
                        );
                } else {
                    csrf.disable();
                }
            })
            
            // Configure CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Set session management to stateless
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Configure authorization rules
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers(
                    "/actuator/health/**",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                
                // Admin endpoints
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                
                // Service-to-service communication
                .requestMatchers("/internal/**").hasRole("SERVICE")
                
                // User endpoints require authentication
                .anyRequest().authenticated()
            )
            
            // Configure exception handling
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            
            // Add security headers
            .headers(headers -> headers
                .frameOptions().deny()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubdomains(true)
                    .preload(true)
                )
                .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                .and()
            );

        // Add custom filters in correct order
        http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // Add CSRF protection filter if enabled
        if (csrfEnabled) {
            http.addFilterAfter(csrfProtectionFilter, CsrfFilter.class);
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow specific origins (never use "*" in production)
        configuration.setAllowedOriginPatterns(List.of(
            "https://*.waqiti.com",
            "https://localhost:*",
            "http://localhost:*" // Only for development
        ));
        
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // SECURITY FIX (P1-005): Replace wildcard with explicit header whitelist
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Requested-With", "X-CSRF-TOKEN",
                "X-Session-ID", "Accept", "Origin", "Cache-Control", "X-File-Name"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Use strength 12 for better security
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        authProvider.setHideUserNotFoundExceptions(false);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}