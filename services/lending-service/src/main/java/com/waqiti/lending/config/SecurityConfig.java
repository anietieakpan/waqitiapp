package com.waqiti.lending.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Security Configuration for Lending Service
 *
 * Implements OAuth2 Resource Server with Keycloak integration
 * Provides JWT-based authentication and role-based authorization
 *
 * Security Features:
 * - JWT token validation
 * - Role-based access control (RBAC)
 * - Method-level security via @PreAuthorize
 * - Stateless session management
 * - CORS configuration
 * - Public endpoints for health checks
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * Main security filter chain
     * Configures HTTP security, JWT authentication, and authorization rules
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless REST API
            .csrf(AbstractHttpConfigurer::disable)

            // Configure CORS
            .cors(cors -> cors.configure(http))

            // Stateless session management (JWT-based)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules
            .authorizeHttpRequests(authz -> authz
                // Public endpoints - no authentication required
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                // Application endpoints - require authentication
                .requestMatchers(HttpMethod.POST, "/api/v1/applications").hasAnyRole("BORROWER", "LOAN_OFFICER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/applications/**").hasAnyRole("BORROWER", "LOAN_OFFICER", "UNDERWRITER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/applications/*/approve").hasAnyRole("UNDERWRITER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/applications/*/reject").hasAnyRole("UNDERWRITER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/applications/*/manual-review").hasAnyRole("LOAN_OFFICER", "UNDERWRITER", "ADMIN")

                // Loan endpoints - require authentication
                .requestMatchers(HttpMethod.GET, "/api/v1/loans/**").hasAnyRole("BORROWER", "LOAN_OFFICER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/loans/*/status").hasAnyRole("LOAN_OFFICER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/loans/*/mark-delinquent").hasAnyRole("COLLECTIONS", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/loans/*/charge-off").hasRole("ADMIN")

                // Payment endpoints - require authentication
                .requestMatchers(HttpMethod.POST, "/api/v1/payments").hasAnyRole("BORROWER", "PAYMENT_PROCESSOR", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/payments/**").hasAnyRole("BORROWER", "LOAN_OFFICER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/*/fail").hasAnyRole("PAYMENT_PROCESSOR", "ADMIN")

                // Portfolio analytics - restricted to authorized roles
                .requestMatchers("/api/v1/loans/portfolio/**").hasAnyRole("ANALYST", "MANAGER", "ADMIN")
                .requestMatchers("/api/v1/applications/statistics").hasAnyRole("ANALYST", "MANAGER", "ADMIN")

                // All other requests require authentication
                .anyRequest().authenticated()
            )

            // OAuth2 Resource Server with JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * JWT Decoder Bean
     * Validates JWT tokens using Keycloak's public keys
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        log.info("Configuring JWT decoder with JWK Set URI: {}", jwkSetUri);
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    /**
     * JWT Authentication Converter
     * Converts JWT claims to Spring Security authorities
     * Extracts roles from Keycloak's realm_access and resource_access claims
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter());
        return converter;
    }

    /**
     * JWT Granted Authorities Converter
     * Extracts roles from Keycloak JWT token
     *
     * Keycloak JWT structure:
     * {
     *   "realm_access": {
     *     "roles": ["BORROWER", "USER"]
     *   },
     *   "resource_access": {
     *     "lending-service": {
     *       "roles": ["LOAN_OFFICER"]
     *     }
     *   }
     * }
     */
    private Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter() {
        return jwt -> {
            // Extract realm roles
            Collection<GrantedAuthority> realmRoles = extractRealmRoles(jwt);

            // Extract resource-specific roles
            Collection<GrantedAuthority> resourceRoles = extractResourceRoles(jwt, "lending-service");

            // Combine all roles
            return Stream.concat(realmRoles.stream(), resourceRoles.stream())
                    .collect(Collectors.toSet());
        };
    }

    /**
     * Extract realm-level roles from JWT
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || realmAccess.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> roles = (List<String>) realmAccess.get("roles");
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }

    /**
     * Extract resource-specific roles from JWT
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractResourceRoles(Jwt jwt, String resourceId) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null || resourceAccess.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> resource = (Map<String, Object>) resourceAccess.get(resourceId);
        if (resource == null || resource.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> roles = (List<String>) resource.get("roles");
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}
