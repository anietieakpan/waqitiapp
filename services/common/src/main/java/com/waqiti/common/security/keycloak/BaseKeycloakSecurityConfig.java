package com.waqiti.common.security.keycloak;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Base Keycloak Security Configuration
 * Provides common security setup for all microservices
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public abstract class BaseKeycloakSecurityConfig {
    
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8080/realms/waqiti}")
    protected String issuerUri;
    
    @Value("${keycloak.realm:waqiti}")
    protected String realm;
    
    @Value("${keycloak.resource:${spring.application.name:unknown}}")
    protected String resourceName;
    
    @Value("${security.cors.allowed-origins:http://localhost:*,https://localhost:*}")
    protected String allowedOrigins;
    
    /**
     * Create a SecurityFilterChain with Keycloak configuration
     */
    protected SecurityFilterChain createKeycloakSecurityFilterChain(
            HttpSecurity http,
            String serviceName,
            Consumer<HttpSecurity> customizer) throws Exception {
        
        log.info("Configuring Keycloak security for service: {}", serviceName);
        
        // Base configuration
        http
            // Disable CSRF for stateless API
            .csrf(csrf -> csrf.disable())
            
            // Configure CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Session management - stateless
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
            
            // Add custom headers for security
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; frame-ancestors 'self'; form-action 'self';")
                )
            );
        
        // Apply service-specific customizations
        if (customizer != null) {
            customizer.accept(http);
        }
        
        return http.build();
    }
    
    /**
     * JWT Decoder bean
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        log.debug("Creating JWT decoder with issuer: {}", issuerUri);
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }
    
    /**
     * JWT Authentication Converter with Keycloak authorities mapping
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakGrantedAuthoritiesConverter());
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }
    
    /**
     * Custom authorities converter for Keycloak tokens
     */
    protected Converter<Jwt, Collection<GrantedAuthority>> keycloakGrantedAuthoritiesConverter() {
        return new Converter<Jwt, Collection<GrantedAuthority>>() {
            @Override
            public Collection<GrantedAuthority> convert(Jwt jwt) {
                Collection<GrantedAuthority> authorities = new ArrayList<>();
                
                // Extract realm roles
                Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
                if (realmAccess != null && realmAccess.containsKey("roles")) {
                    @SuppressWarnings("unchecked")
                    List<String> realmRoles = (List<String>) realmAccess.get("roles");
                    authorities.addAll(realmRoles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase().replace("-", "_")))
                        .collect(Collectors.toList()));
                }
                
                // Extract resource (client) roles
                Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
                if (resourceAccess != null) {
                    // Check for service-specific roles
                    if (resourceAccess.containsKey(resourceName)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(resourceName);
                        if (clientAccess != null && clientAccess.containsKey("roles")) {
                            @SuppressWarnings("unchecked")
                            List<String> clientRoles = (List<String>) clientAccess.get("roles");
                            authorities.addAll(clientRoles.stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_CLIENT_" + role.toUpperCase().replace("-", "_")))
                                .collect(Collectors.toList()));
                        }
                    }
                    
                    // Check for account roles
                    if (resourceAccess.containsKey("account")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> accountAccess = (Map<String, Object>) resourceAccess.get("account");
                        if (accountAccess != null && accountAccess.containsKey("roles")) {
                            @SuppressWarnings("unchecked")
                            List<String> accountRoles = (List<String>) accountAccess.get("roles");
                            authorities.addAll(accountRoles.stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_ACCOUNT_" + role.toUpperCase().replace("-", "_")))
                                .collect(Collectors.toList()));
                        }
                    }
                }
                
                // Extract scopes from 'scope' claim
                String scope = jwt.getClaimAsString("scope");
                if (scope != null && !scope.isEmpty()) {
                    authorities.addAll(Arrays.stream(scope.split(" "))
                        .filter(s -> !s.isEmpty())
                        .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                        .collect(Collectors.toList()));
                }
                
                // Extract groups if present
                List<String> groups = jwt.getClaim("groups");
                if (groups != null) {
                    authorities.addAll(groups.stream()
                        .map(group -> new SimpleGrantedAuthority("GROUP_" + group.toUpperCase().replace("/", "_").replace(" ", "_")))
                        .collect(Collectors.toList()));
                }
                
                // Add custom authorities based on claims
                addCustomAuthorities(jwt, authorities);
                
                log.debug("Extracted authorities for user '{}': {}", 
                    jwt.getClaimAsString("preferred_username"), authorities);
                
                return authorities;
            }
        };
    }
    
    /**
     * Hook for services to add custom authorities
     */
    protected void addCustomAuthorities(Jwt jwt, Collection<GrantedAuthority> authorities) {
        // Services can override this to add custom authorities
    }
    
    /**
     * CORS Configuration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Parse allowed origins
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOriginPatterns(origins);
        
        // Allowed methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // SECURITY FIX (P1-005): Replace wildcard with explicit header whitelist
        // Allowed headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Requested-With", "X-CSRF-TOKEN",
            "X-Session-ID", "Accept", "Origin", "Cache-Control", "X-File-Name",
            "X-Request-Id", "X-Trace-Id", "X-Service-Name"));

        // Exposed headers
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "X-Total-Count",
            "X-Page-Number", 
            "X-Page-Size",
            "X-Request-Id",
            "X-Trace-Id",
            "X-Service-Name"
        ));
        
        // Allow credentials
        configuration.setAllowCredentials(true);
        
        // Max age
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
    
    /**
     * Extract user ID from JWT
     */
    protected String extractUserId(Jwt jwt) {
        // Try different claims for user ID
        String userId = jwt.getClaimAsString("sub");
        if (userId == null) {
            userId = jwt.getClaimAsString("user_id");
        }
        if (userId == null) {
            userId = jwt.getClaimAsString("preferred_username");
        }
        return userId;
    }
    
    /**
     * Extract username from JWT
     */
    protected String extractUsername(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null) {
            username = jwt.getClaimAsString("username");
        }
        if (username == null) {
            username = jwt.getClaimAsString("email");
        }
        return username;
    }
    
    /**
     * Check if JWT has specific role
     */
    protected boolean hasRole(Jwt jwt, String role) {
        Collection<GrantedAuthority> authorities = keycloakGrantedAuthoritiesConverter().convert(jwt);
        return authorities.stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + role.toUpperCase()));
    }
    
    /**
     * Check if JWT has specific scope with tenant context
     */
    protected boolean hasScope(Jwt jwt, String scope) {
        Collection<GrantedAuthority> authorities = keycloakGrantedAuthoritiesConverter().convert(jwt);
        return authorities.stream()
            .anyMatch(auth -> auth.getAuthority().equals("SCOPE_" + scope.toUpperCase()));
    }
    
    /**
     * Check if JWT has tenant-specific scope
     */
    protected boolean hasTenantScope(Jwt jwt, String tenantId, String scope) {
        Collection<GrantedAuthority> authorities = keycloakGrantedAuthoritiesConverter().convert(jwt);
        String tenantScope = "TENANT_" + tenantId.toUpperCase() + "_SCOPE_" + scope.toUpperCase();
        return authorities.stream()
            .anyMatch(auth -> auth.getAuthority().equals(tenantScope));
    }
    
    /**
     * Get all tenant IDs that the user has access to
     */
    protected Set<String> getUserTenantIds(Jwt jwt) {
        Collection<GrantedAuthority> authorities = keycloakGrantedAuthoritiesConverter().convert(jwt);
        return authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .filter(auth -> auth.startsWith("TENANT_"))
            .map(auth -> auth.substring(7)) // Remove "TENANT_" prefix
            .filter(auth -> auth.contains("_")) // Has additional suffix
            .map(auth -> auth.substring(0, auth.indexOf("_"))) // Get tenant part before next underscore
            .collect(Collectors.toSet());
    }
    
    /**
     * Check if user belongs to specific tenant
     */
    protected boolean belongsToTenant(Jwt jwt, String tenantId) {
        Set<String> userTenants = getUserTenantIds(jwt);
        return userTenants.contains(tenantId.toUpperCase());
    }
    
    @Value("${security.rate-limiting.enabled:true}")
    protected boolean rateLimitingEnabled;
    
    @Value("${security.audit.enabled:true}")
    protected boolean auditEnabled;
    
    /**
     * Rate limiting check (placeholder for rate limiting implementation)
     */
    protected boolean checkRateLimit(String userId, String endpoint) {
        if (!rateLimitingEnabled) {
            return true;
        }
        
        // Implementation would check rate limits
        // For now, always allow
        return true;
    }
    
    /**
     * Security monitoring helper
     */
    protected void logSecurityEvent(String eventType, String userId, String details) {
        if (auditEnabled) {
            log.info("SECURITY_EVENT: {} - User: {} - Details: {}", eventType, userId, details);
        }
    }
}