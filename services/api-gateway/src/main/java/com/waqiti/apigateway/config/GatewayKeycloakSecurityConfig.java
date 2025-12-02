package com.waqiti.apigateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keycloak security configuration for API Gateway
 * Manages authentication and authorization for all incoming requests
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class GatewayKeycloakSecurityConfig {

    @Value("${keycloak.auth-server-url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${feature.flags.dual-auth-mode.enabled:true}")
    private boolean dualAuthMode;

    @Bean
    public SecurityWebFilterChain gatewaySecurityFilterChain(ServerHttpSecurity http) {
        return http
            // Disable CSRF for stateless API
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            
            // Stateless session management
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            
            // Configure authorization rules
            .authorizeExchange(exchanges -> exchanges
                // Public endpoints
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                .pathMatchers("/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                .pathMatchers("/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                .pathMatchers("/api/v1/public/**").permitAll()
                .pathMatchers("/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                
                // WebSocket endpoints - authenticated but with special handling
                .pathMatchers("/ws/**", "/wss/**", "/stomp/**").authenticated()
                
                // Health checks for services
                .pathMatchers("/*/actuator/health").permitAll()
                
                // Service-specific public endpoints
                .pathMatchers("/api/v1/users/verify-email/**").permitAll()
                .pathMatchers("/api/v1/users/reset-password/**").permitAll()
                .pathMatchers("/api/v1/payments/webhook/**").permitAll()
                
                // Admin endpoints require ADMIN role
                .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .pathMatchers("/api/v1/*/admin/**").hasRole("ADMIN")
                
                // Service management endpoints
                .pathMatchers("/eureka/**").hasRole("SERVICE_ADMIN")
                .pathMatchers("/config/**").hasRole("SERVICE_ADMIN")
                
                // Internal service-to-service communication
                .pathMatchers("/internal/**").hasRole("SERVICE")
                
                // All other requests require authentication
                .anyExchange().authenticated()
            )
            
            // Configure OAuth2 Resource Server with JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtDecoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            
            // Build the security filter chain
            .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        String jwkSetUri = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
        return ReactiveJwtDecoders.fromIssuerLocation(keycloakUrl + "/realms/" + realm);
    }

    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> extractAuthorities(jwt));
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    /**
     * Extract authorities from JWT token
     * Handles both Keycloak roles and scopes
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        
        // Extract realm roles
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            authorities.addAll(roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList()));
        }
        
        // Extract resource roles for this client
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            Map<String, Object> clientRoles = (Map<String, Object>) resourceAccess.get("api-gateway");
            if (clientRoles != null && clientRoles.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) clientRoles.get("roles");
                authorities.addAll(roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList()));
            }
        }
        
        // Extract scopes
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isEmpty()) {
            String[] scopes = scope.split(" ");
            for (String s : scopes) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
            }
        }
        
        log.debug("Extracted authorities for user {}: {}", jwt.getSubject(), authorities);
        return authorities;
    }
}