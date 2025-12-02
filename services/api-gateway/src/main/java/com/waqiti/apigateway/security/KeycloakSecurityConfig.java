package com.waqiti.apigateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CRITICAL SECURITY: Consolidated Keycloak Security Configuration
 * PRODUCTION-READY: OAuth2/OIDC with comprehensive security controls
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "waqiti.security.auth.mode", havingValue = "KEYCLOAK_OIDC")
public class KeycloakSecurityConfig {
    
    private final AuthenticationStrategy authStrategy;
    
    @PostConstruct
    public void validateConfiguration() {
        authStrategy.validateConfiguration();
        log.info("SECURITY: Keycloak security configuration initialized");
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("SECURITY: Configuring Keycloak OAuth2 security filter chain");
        
        http
            // CSRF protection - disabled for API but consider for web endpoints
            .csrf(csrf -> csrf.disable())
            
            // Session management - stateless for API
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false))
            
            // Authorization rules
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info", 
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/favicon.ico"
                ).permitAll()
                
                // Webhook endpoints (authenticated via signature)
                .requestMatchers("/webhooks/**").permitAll()
                
                // Admin endpoints - require admin role
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                
                // Financial operations - require authenticated user
                .requestMatchers("/api/payments/**").hasRole("USER")
                .requestMatchers("/api/wallets/**").hasRole("USER")
                .requestMatchers("/api/transfers/**").hasRole("USER")
                
                // Compliance and reporting - require compliance role
                .requestMatchers("/api/compliance/**").hasRole("COMPLIANCE")
                .requestMatchers("/api/reports/**").hasRole("COMPLIANCE")
                
                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                
                // Default - require authentication
                .anyRequest().authenticated())
            
            // OAuth2 Resource Server configuration
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())))
            
            // Exception handling
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    log.warn("SECURITY: Authentication failed for request: {} from IP: {}", 
                            request.getRequestURI(), request.getRemoteAddr());
                    response.sendError(401, "Authentication required");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    log.warn("SECURITY: Access denied for request: {} from user: {} IP: {}", 
                            request.getRequestURI(), 
                            request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous",
                            request.getRemoteAddr());
                    response.sendError(403, "Insufficient privileges");
                }));
        
        return http.build();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        // Configure JWT decoder to validate tokens from Keycloak
        String jwkSetUri = authStrategy.getKeycloakServerUrl() + 
                          "/realms/" + authStrategy.getKeycloakRealm() + 
                          "/protocol/openid_connect/certs";
        
        log.info("SECURITY: Configuring JWT decoder with JWK Set URI: {}", jwkSetUri);
        
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        
        // Set expected issuer
        String expectedIssuer = authStrategy.getKeycloakServerUrl() + 
                               "/realms/" + authStrategy.getKeycloakRealm();
        decoder.setJwtValidator(new KeycloakJwtValidator(expectedIssuer, authStrategy.getKeycloakClientId()));
        
        return decoder;
    }
    
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter());
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }
    
    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter() {
        return new KeycloakJwtGrantedAuthoritiesConverter();
    }
    
    /**
     * Custom JWT authorities converter for Keycloak role mapping
     */
    private static class KeycloakJwtGrantedAuthoritiesConverter 
            implements Converter<Jwt, Collection<GrantedAuthority>> {
        
        private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();
        
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Collection<GrantedAuthority> authorities = defaultConverter.convert(jwt);
            
            // Extract Keycloak realm roles
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) realmAccess.get("roles");
                
                Collection<GrantedAuthority> realmAuthorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList());
                
                authorities.addAll(realmAuthorities);
            }
            
            // Extract Keycloak client roles
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            if (resourceAccess != null) {
                resourceAccess.forEach((clientId, clientAccess) -> {
                    if (clientAccess instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> clientMap = (Map<String, Object>) clientAccess;
                        
                        if (clientMap.containsKey("roles")) {
                            @SuppressWarnings("unchecked")
                            List<String> clientRoles = (List<String>) clientMap.get("roles");
                            
                            Collection<GrantedAuthority> clientAuthorities = clientRoles.stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                                .collect(Collectors.toList());
                            
                            authorities.addAll(clientAuthorities);
                        }
                    }
                });
            }
            
            return authorities;
        }
    }
}