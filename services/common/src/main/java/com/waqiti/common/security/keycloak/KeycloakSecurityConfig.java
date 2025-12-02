package com.waqiti.common.security.keycloak;

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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base Keycloak Security Configuration for all microservices
 * This configuration provides standard OAuth2 resource server setup with Keycloak
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = false)
public class KeycloakSecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8180/realms/waqiti-fintech}")
    private String issuerUri;

    @Value("${keycloak.resource:${spring.application.name:default-client}}")
    private String clientId;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CRITICAL FIX: Enable CSRF protection for state-changing operations
                // Only disable for stateless API endpoints (webhooks, public APIs)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
                        .ignoringRequestMatchers(
                                // Exclude webhooks (verified via signature)
                                "/api/*/webhooks/**",
                                "/webhooks/**",
                                // Exclude actuator endpoints (internal)
                                "/actuator/**",
                                // Exclude public APIs (no authentication needed)
                                "/api/*/public/**"
                        )
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // Health and metrics endpoints
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics/**").permitAll()
                        // Swagger documentation
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .xssProtection(xss -> xss.disable())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                )
                .build();
    }

    /**
     * Configure CSRF token repository using Redis for distributed systems
     */
    @Bean
    public org.springframework.security.web.csrf.CsrfTokenRepository csrfTokenRepository() {
        // Use Spring Security's cookie-based CSRF token repository
        org.springframework.security.web.csrf.CookieCsrfTokenRepository repository =
            org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }

    /**
     * CSRF token request handler for SPA/stateless applications
     */
    @Bean
    public CsrfTokenRequestHandler csrfTokenRequestHandler() {
        return new SpaCsrfTokenRequestHandler();
    }

    /**
     * Redis template for CSRF token storage
     */
    @Bean
    public org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate() {
        org.springframework.data.redis.core.RedisTemplate<String, String> template =
            new org.springframework.data.redis.core.RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        template.setKeySerializer(new org.springframework.data.redis.serializer.StringRedisSerializer());
        template.setValueSerializer(new org.springframework.data.redis.serializer.StringRedisSerializer());
        return template;
    }

    /**
     * Redis connection factory
     */
    @Bean
    public org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory() {
        return new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory();
    }

    /**
     * SPA CSRF token request handler that works with stateless JWT authentication
     */
    private static class SpaCsrfTokenRequestHandler implements org.springframework.security.web.csrf.CsrfTokenRequestHandler {
        private final org.springframework.security.web.csrf.CsrfTokenRequestHandler delegate =
            new org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(jakarta.servlet.http.HttpServletRequest request,
                          jakarta.servlet.http.HttpServletResponse response,
                          java.util.function.Supplier<org.springframework.security.web.csrf.CsrfToken> csrfToken) {
            delegate.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(jakarta.servlet.http.HttpServletRequest request,
                                           org.springframework.security.web.csrf.CsrfToken csrfToken) {
            // Try header first (for API clients)
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            if (headerValue != null) {
                return headerValue;
            }

            // Fall back to parameter (for form submissions)
            String paramValue = request.getParameter(csrfToken.getParameterName());
            if (paramValue != null) {
                return paramValue;
            }

            // Delegate to XOR handler for cookie-based tokens
            return delegate.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        
        // Extract realm roles
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> realmRoles = (List<String>) realmAccess.get("roles");
            authorities.addAll(realmRoles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toSet()));
        }
        
        // Extract resource/client specific roles
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null && resourceAccess.containsKey(clientId)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(clientId);
            if (clientAccess != null && clientAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> clientRoles = (List<String>) clientAccess.get("roles");
                authorities.addAll(clientRoles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + clientId.toUpperCase() + "_" + role.toUpperCase()))
                        .collect(Collectors.toSet()));
            }
        }
        
        // Extract scopes
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isEmpty()) {
            authorities.addAll(Arrays.stream(scope.split(" "))
                    .map(s -> new SimpleGrantedAuthority("SCOPE_" + s.toUpperCase()))
                    .collect(Collectors.toSet()));
        }
        
        // Add default USER role if no roles found
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        
        log.debug("Extracted authorities for {}: {}", jwt.getClaimAsString("preferred_username"), authorities);
        return authorities;
    }
}