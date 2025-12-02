package com.waqiti.security.config;

import com.waqiti.security.converter.JwtAuthenticationConverter;
import com.waqiti.security.filter.JwtAuthenticationFilter;
import com.waqiti.security.handler.CustomAccessDeniedHandler;
import com.waqiti.security.handler.CustomAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Comprehensive Keycloak Security Configuration
 * 
 * This configuration provides:
 * - OAuth2 Resource Server setup with Keycloak
 * - JWT token validation and processing
 * - Role-based access control with Keycloak roles
 * - CORS configuration for microservices
 * - Custom authentication/authorization handlers
 * - Scope-based method security
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
public class KeycloakSecurityConfig {
    
    private final KeycloakProperties keycloakProperties;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    
    /**
     * Main security filter chain configuration
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for service: {}", keycloakProperties.getClientId());
        
        http
            // Disable CSRF for stateless API
            .csrf().disable()
            
            // CORS configuration
            .cors().configurationSource(corsConfigurationSource())
            .and()
            
            // Session management - stateless for JWT
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            
            // Exception handling
            .exceptionHandling()
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            .and()
            
            // Authorization rules
            .authorizeHttpRequests(authz -> authz
                // Public endpoints - no authentication required
                .requestMatchers(
                    "/actuator/health/**",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/api/*/health",
                    "/api/*/info",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                
                // Service-specific public endpoints
                .requestMatchers(
                    "/api/v1/international/supported-countries",
                    "/api/v1/international/exchange-rates",
                    "/api/v1/international/track"
                ).permitAll()
                
                // Admin endpoints - require admin role
                .requestMatchers("/api/*/admin/**")
                    .hasRole("ADMIN")
                
                // Management endpoints - require management access
                .requestMatchers("/api/*/management/**")
                    .hasAnyRole("ADMIN", "MANAGER")
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            
            // OAuth2 Resource Server configuration
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            
            // Add custom JWT filter
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    /**
     * JWT Decoder configuration for Keycloak
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        String jwkSetUri = keycloakProperties.getJwkSetUri();
        log.info("Configuring JWT decoder with JWK Set URI: {}", jwkSetUri);
        
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .cache(keycloakProperties.getJwkCacheDuration())
                .build();
        
        // Set custom claim validator if needed
        jwtDecoder.setClaimSetVerifier(claims -> {
            // Validate issuer
            String issuer = claims.getIssuer().toString();
            if (!keycloakProperties.getIssuerUri().equals(issuer)) {
                throw new SecurityException("Invalid token issuer: " + issuer);
            }
            
            // Validate audience
            List<String> audience = claims.getAudience();
            if (!audience.contains(keycloakProperties.getClientId())) {
                throw new SecurityException("Token not intended for this audience");
            }
        });
        
        return jwtDecoder;
    }
    
    /**
     * JWT Authentication Converter for Keycloak tokens
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return new JwtAuthenticationConverter(keycloakProperties);
    }
    
    /**
     * Custom JWT Authentication Filter
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(keycloakProperties);
    }
    
    /**
     * JWT Authentication Provider
     */
    @Bean
    public JwtAuthenticationProvider jwtAuthenticationProvider() {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder());
        provider.setJwtAuthenticationConverter(jwtAuthenticationConverter());
        return provider;
    }
    
    /**
     * CORS Configuration for microservices
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow all origins in development, specific origins in production
        if (keycloakProperties.isDevelopmentMode()) {
            configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        } else {
            configuration.setAllowedOrigins(keycloakProperties.getAllowedOrigins());
        }
        
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"
        ));
        
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Requested-With", "Accept", 
            "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers",
            "X-Request-ID", "X-Correlation-ID", "X-User-ID"
        ));
        
        configuration.setExposedHeaders(Arrays.asList(
            "Access-Control-Allow-Origin", "Access-Control-Allow-Credentials",
            "X-Request-ID", "X-Correlation-ID", "X-Total-Count"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}