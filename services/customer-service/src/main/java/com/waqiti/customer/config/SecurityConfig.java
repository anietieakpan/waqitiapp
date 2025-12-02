package com.waqiti.customer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Security Configuration for Customer Service.
 * Configures OAuth2 resource server with Keycloak JWT authentication,
 * custom authorities mapping, CORS, and endpoint security.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@Slf4j
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    /**
     * Configures the security filter chain.
     * Sets up JWT authentication, CORS, CSRF, and endpoint authorization.
     *
     * @param http HttpSecurity configuration
     * @return Configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints
                .requestMatchers(
                    "/api/v1/health",
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/api-docs/**"
                ).permitAll()
                // Protected endpoints
                .requestMatchers("/api/v1/**").authenticated()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        log.info("Security filter chain configured with JWT authentication");
        return http.build();
    }

    /**
     * Configures JWT decoder with JWK Set URI.
     *
     * @return Configured JwtDecoder
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        log.info("JWT Decoder configured with JWK Set URI: {}", jwkSetUri);
        return decoder;
    }

    /**
     * Configures JWT authentication converter with custom authorities.
     * Extracts roles from Keycloak realm_access and resource_access claims.
     *
     * @return Configured JwtAuthenticationConverter
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter());
        log.info("JWT Authentication Converter configured with custom authorities");
        return converter;
    }

    /**
     * Custom JWT granted authorities converter.
     * Extracts roles from Keycloak JWT claims and converts them to Spring Security authorities.
     *
     * @return Converter for JWT to GrantedAuthorities
     */
    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter() {
        JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();
        defaultConverter.setAuthorityPrefix("SCOPE_");

        return jwt -> {
            Collection<GrantedAuthority> authorities = defaultConverter.convert(jwt);

            // Extract realm roles
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            Collection<String> realmRoles = realmAccess != null
                ? (Collection<String>) realmAccess.get("roles")
                : List.of();

            // Extract resource roles (client-specific roles)
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            Collection<String> resourceRoles = List.of();
            if (resourceAccess != null && resourceAccess.containsKey("customer-service")) {
                Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get("customer-service");
                resourceRoles = clientAccess != null
                    ? (Collection<String>) clientAccess.get("roles")
                    : List.of();
            }

            // Convert roles to authorities with ROLE_ prefix
            Collection<GrantedAuthority> realmAuthorities = realmRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());

            Collection<GrantedAuthority> resourceAuthorities = resourceRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());

            // Combine all authorities
            return Stream.of(authorities, realmAuthorities, resourceAuthorities)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        };
    }

    /**
     * Configures CORS settings.
     * Allows cross-origin requests from configured origins.
     *
     * @return CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // SECURITY FIX (P1-005): Replace wildcard origins with environment-based configuration
        configuration.setAllowedOriginPatterns(List.of(
            "https://*.waqiti.com",
            "https://localhost:*",
            "http://localhost:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // SECURITY FIX (P1-005): Replace wildcard with explicit header whitelist
        configuration.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "X-Requested-With", "X-CSRF-TOKEN",
            "X-Session-ID", "Accept", "Origin", "Cache-Control", "X-File-Name",
            "X-Correlation-Id", "X-Request-Id"));
        configuration.setExposedHeaders(List.of(
            "Authorization",
            "X-Correlation-Id",
            "X-Request-Id",
            "X-Total-Count"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("CORS configuration enabled for all origins");
        return source;
    }
}
