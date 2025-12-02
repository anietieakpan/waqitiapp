package com.waqiti.payment.config;

import org.keycloak.adapters.springboot.KeycloakSpringBootConfigResolver;
import org.keycloak.adapters.springsecurity.KeycloakConfiguration;
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.keycloak.adapters.KeycloakConfigResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keycloak security configuration for Payment Service
 * This configuration is activated when Keycloak is enabled
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class KeycloakSecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    /**
     * Main security filter chain for Keycloak authentication
     */
    @Bean
    @Primary
    public SecurityFilterChain keycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Payment Service");
        
        http
            // SECURITY FIX: Enable CSRF protection with cookie-based tokens for defense-in-depth
            // While JWT-based APIs are typically stateless, CSRF protection adds an extra security layer
            // This protects against scenarios where cookies might be used alongside JWTs
            .csrf(csrf -> csrf
                .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                    "/actuator/**",           // Actuator endpoints
                    "/api/v1/payments/webhook/**",  // Webhooks from external providers
                    "/swagger-ui/**",         // Swagger UI
                    "/v3/api-docs/**"         // OpenAPI docs
                )
            )

            // Configure CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Session management - stateless for API
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // OAuth2 Resource Server with JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            
            // Authorization rules
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/payments/public/**").permitAll()
                .requestMatchers("/api/v1/payments/webhook/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Payment endpoints - require authentication
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/transfer").hasAuthority("SCOPE_payment:write")
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/request").hasAuthority("SCOPE_payment:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/payments/history").hasAuthority("SCOPE_payment:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/payments/*/status").hasAuthority("SCOPE_payment:read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/payments/*/accept").hasAuthority("SCOPE_payment:write")
                .requestMatchers(HttpMethod.PUT, "/api/v1/payments/*/reject").hasAuthority("SCOPE_payment:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/payments").hasAuthority("SCOPE_payment:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/payments/*").hasAuthority("SCOPE_payment:read")
                
                // High-value payment operations requiring special authorization
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/refund/**").hasAuthority("SCOPE_payment:authorize")
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/cancel/**").hasAuthority("SCOPE_payment:authorize")
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/*/refund").hasAuthority("SCOPE_payment:authorize")
                .requestMatchers(HttpMethod.PUT, "/api/v1/payments/*/cancel").hasAuthority("SCOPE_payment:authorize")
                
                // Instant deposit endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/instant-deposits").hasAuthority("SCOPE_payment:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/instant-deposits/**").hasAuthority("SCOPE_payment:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/payments/instant-deposit/fees").hasAuthority("SCOPE_payment:read")
                
                // ACH transfer endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/ach-transfers").hasAuthority("SCOPE_payment:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/ach-transfers/**").hasAuthority("SCOPE_payment:read")
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/ach-transfer").hasAuthority("SCOPE_payment:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/payments/ach-transfer/status/*").hasAuthority("SCOPE_payment:read")
                
                // Check deposit endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/check-deposits").hasAuthority("SCOPE_payment:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/check-deposits/**").hasAuthority("SCOPE_payment:read")
                
                // Scheduled payment endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/scheduled").hasAuthority("SCOPE_payment:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/payments/scheduled").hasAuthority("SCOPE_payment:read")
                .requestMatchers("/api/v1/payments/schedule/**").hasAuthority("SCOPE_payment:write")
                
                // Split payment endpoints
                .requestMatchers("/api/v1/payments/split/**").hasAuthority("SCOPE_payment:write")
                
                // Payment method management
                .requestMatchers("/api/v1/payment-methods/**").hasAuthority("SCOPE_payment:write")
                
                // NFCs payment endpoints
                .requestMatchers("/api/v1/nfc-payments/**").hasAuthority("SCOPE_payment:write")
                
                // Admin endpoints - require payment admin role
                .requestMatchers("/api/v1/payments/admin/**").hasRole("PAYMENT_ADMIN")
                .requestMatchers("/api/v1/payments/reports/**").hasAnyRole("PAYMENT_ADMIN", "PAYMENT_ANALYST")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/payments/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            
            // Add custom headers for security
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; frame-ancestors 'self'; form-action 'self';")
                )
            );

        return http.build();
    }

    /**
     * JWT decoder bean for Keycloak tokens
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    /**
     * JWT authentication converter with custom authorities mapping
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Extract authorities from realm_access and resource_access
            Collection<String> authorities = new java.util.ArrayList<>();
            
            // Get realm roles
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> realmRoles = (List<String>) realmAccess.get("roles");
                authorities.addAll(realmRoles.stream()
                    .map(role -> "ROLE_" + role.toUpperCase())
                    .collect(Collectors.toList()));
            }
            
            // Get resource (client) roles
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            if (resourceAccess != null && resourceAccess.containsKey(clientId)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(clientId);
                if (clientAccess.containsKey("roles")) {
                    @SuppressWarnings("unchecked")
                    List<String> clientRoles = (List<String>) clientAccess.get("roles");
                    authorities.addAll(clientRoles.stream()
                        .map(role -> "ROLE_" + clientId.toUpperCase() + "_" + role.toUpperCase())
                        .collect(Collectors.toList()));
                }
            }
            
            // Get scopes
            String scope = jwt.getClaimAsString("scope");
            if (scope != null) {
                authorities.addAll(Arrays.stream(scope.split(" "))
                    .map(s -> "SCOPE_" + s)
                    .collect(Collectors.toList()));
            }
            
            // Convert to GrantedAuthority
            return authorities.stream()
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        });
        
        return converter;
    }

    /**
     * CORS configuration for the payment service
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "https://localhost:*",
            "https://*.waqiti.com",
            "https://example.com"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // SECURITY FIX (P1-005): Replace wildcard with explicit header whitelist
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Requested-With", "X-CSRF-TOKEN",
            "X-Session-ID", "Accept", "Origin", "Cache-Control", "X-File-Name",
            "X-Request-Id"));
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "X-Total-Count",
            "X-Page-Number",
            "X-Page-Size",
            "X-Request-Id"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    
    /**
     * Custom Keycloak configuration for advanced scenarios
     */
    @Bean
    @ConditionalOnProperty(name = "keycloak.use-resource-role-mappings", havingValue = "true")
    public KeycloakConfigResolver keycloakConfigResolver() {
        return request -> {
            // Dynamic Keycloak configuration based on request headers or other criteria
            // This allows for multi-tenant scenarios
            String tenant = request.getHeader("X-Tenant-Id");
            if (tenant != null) {
                log.debug("Configuring Keycloak for tenant: {}", tenant);
                // Return tenant-specific Keycloak configuration
            }
            // Return default configuration
            return KeycloakSpringBootConfigResolver.resolve(null);
        };
    }
}