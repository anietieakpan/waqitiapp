package com.waqiti.user.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keycloak Security Configuration for User Service
 * This configuration is activated when Keycloak is enabled
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
public class KeycloakSecurityConfig {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain keycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                // SECURITY FIX: Enable CSRF protection with exemptions for webhooks
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(
                            "/api/v1/webhooks/**",          // Webhook endpoints (verified by signature)
                            "/actuator/health",              // Health checks
                            "/actuator/info"                 // Info endpoint
                        )
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/v1/users/register").permitAll()
                        .requestMatchers("/api/v1/users/verify/**").permitAll()
                        .requestMatchers("/api/v1/users/password/reset/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        
                        // Admin endpoints
                        .requestMatchers("/api/v1/users/admin/**").hasRole("ADMIN")
                        
                        // User management endpoints
                        .requestMatchers("/api/v1/users/profile/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/users/settings/**").hasAnyRole("USER", "ADMIN")
                        
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000)
                                .includeSubDomains(true))
                        .contentTypeOptions(cto -> {})
                        .xssProtection(xss -> {})
                        .frameOptions(fo -> fo.deny()))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Extract authorities from both realm_access and resource_access
            Collection<GrantedAuthority> authorities = new HashSet<>();
            
            // Extract realm roles
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> realmRoles = (List<String>) realmAccess.get("roles");
                authorities.addAll(realmRoles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .collect(Collectors.toSet()));
            }
            
            // Extract resource/client roles
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            if (resourceAccess != null && resourceAccess.containsKey(clientId)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(clientId);
                if (clientAccess != null && clientAccess.containsKey("roles")) {
                    @SuppressWarnings("unchecked")
                    List<String> clientRoles = (List<String>) clientAccess.get("roles");
                    authorities.addAll(clientRoles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_CLIENT_" + role.toUpperCase()))
                            .collect(Collectors.toSet()));
                }
            }
            
            // Extract scopes
            String scope = jwt.getClaimAsString("scope");
            if (scope != null) {
                authorities.addAll(Arrays.stream(scope.split(" "))
                        .map(s -> new SimpleGrantedAuthority("SCOPE_" + s.toUpperCase()))
                        .collect(Collectors.toSet()));
            }
            
            // Add default USER role if no roles found
            if (authorities.isEmpty()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }
            
            log.debug("Extracted authorities for user {}: {}", 
                    jwt.getClaimAsString("preferred_username"), authorities);
            
            return authorities;
        });
        
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "https://app.example.com",
                "https://admin.example.com",
                "http://localhost:3000",
                "http://localhost:3001"
        ));
        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(
                Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "X-User-Id", "X-User-Roles"));
        configuration.setExposedHeaders(
                Arrays.asList("X-Total-Count", "X-Page-Number", "X-Page-Size"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}