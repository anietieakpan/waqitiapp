/**
 * Production Security Configuration
 * Comprehensive security headers and HTTPS configuration for production environment
 */
package com.waqiti.apigateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
@Slf4j
@Profile({"production", "staging"})
public class ProductionSecurityConfig {

    @Value("${app.security.allowed-origins:https://app.example.com,https://admin.example.com}")
    private List<String> allowedOrigins;

    @Value("${app.security.hsts.max-age:31536000}")
    private long hstsMaxAge;

    @Value("${app.security.csp.report-uri:https://waqiti.report-uri.com/r/d/csp/enforce}")
    private String cspReportUri;

    @Bean
    public SecurityWebFilterChain productionSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // Enhanced CSRF protection
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        .requireCsrfProtectionMatcher(exchange -> {
                            String path = exchange.getRequest().getPath().value();
                            // Exempt certain paths from CSRF (like API endpoints using Bearer tokens)
                            return !path.startsWith("/api/v1/") || 
                                   path.contains("/auth/") || 
                                   path.contains("/users/register");
                        }))

                // CORS configuration
                .cors(cors -> cors.configurationSource(productionCorsConfigurationSource()))

                // Comprehensive security headers
                .headers(headers -> headers
                        // Prevent clickjacking
                        .frameOptions(frameOptions -> frameOptions
                                .mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        
                        // Content Security Policy - Strict policy for production
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(buildContentSecurityPolicy()))
                        
                        // Referrer Policy
                        .referrerPolicy(referrerPolicy -> referrerPolicy
                                .policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        
                        // Permissions Policy (Feature Policy)
                        .permissionsPolicy(permissions -> permissions
                                .policy(buildPermissionsPolicy()))
                        
                        // HSTS - Force HTTPS
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(hstsMaxAge)
                                .includeSubdomains(true)
                                .preload(true))
                        
                        // Prevent MIME sniffing
                        .contentTypeOptions(contentType -> contentType.and())
                        
                        // XSS Protection
                        .and()
                        .cache(cache -> cache.disable())
                )

                // Authorization rules - Enhanced for production
                .authorizeExchange(exchanges -> exchanges
                        // Health checks and monitoring (restricted)
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/actuator/**").hasRole("ADMIN")
                        
                        // Public authentication endpoints
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/users/register").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/users/verify/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/users/password/reset/**").permitAll()
                        
                        // API Documentation (restricted in production)
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**").hasRole("ADMIN")
                        
                        // Admin endpoints
                        .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/compliance/**").hasAnyRole("ADMIN", "COMPLIANCE_OFFICER")
                        .pathMatchers("/api/v1/analytics/**").hasAnyRole("ADMIN", "ANALYST")
                        
                        // Business endpoints
                        .pathMatchers("/api/v1/business/**").hasAnyRole("BUSINESS_USER", "ADMIN")
                        
                        // High-value transaction endpoints (enhanced security)
                        .pathMatchers(HttpMethod.POST, "/api/v1/payments/high-value/**").hasRole("VERIFIED_USER")
                        .pathMatchers(HttpMethod.POST, "/api/v1/crypto/trade/**").hasRole("VERIFIED_USER")
                        .pathMatchers(HttpMethod.POST, "/api/v1/investment/orders/**").hasRole("VERIFIED_USER")
                        
                        // All other API endpoints require authentication
                        .pathMatchers("/api/v1/**").authenticated()
                        
                        // WebSocket connections
                        .pathMatchers("/ws/**").authenticated()
                        
                        // Deny all other requests
                        .anyExchange().denyAll()
                )

                // Session management - stateless for REST API
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                
                // OAuth2 Resource Server configuration
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder())
                        )
                )

                // Exception handling
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) -> {
                            log.warn("Authentication failed for request: {}", 
                                    exchange.getRequest().getPath().value());
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        })
                        .accessDeniedHandler((exchange, denied) -> {
                            log.warn("Access denied for request: {} by user: {}", 
                                    exchange.getRequest().getPath().value(),
                                    exchange.getPrincipal());
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        })
                )
                
                // Add security headers filter
                .addFilterAt(securityHeadersFilter(), SecurityWebFiltersOrder.SECURITY_CONTEXT_REPOSITORY)

                .build();
    }

    @Bean
    public CorsConfigurationSource productionCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Only allow specific production origins
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedOriginPatterns(List.of("https://*.waqiti.com"));
        
        // Restrict allowed methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Restrict allowed headers
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", 
                "Content-Type", 
                "X-Requested-With",
                "X-Request-ID",
                "X-Correlation-ID",
                "X-API-Version"
        ));
        
        // Control exposed headers
        configuration.setExposedHeaders(Arrays.asList(
                "X-Request-ID", 
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset",
                "X-API-Version"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // 1 hour
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public WebFilter securityHeadersFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                var response = exchange.getResponse();
                var headers = response.getHeaders();
                
                // Additional security headers not covered by Spring Security
                headers.add("X-Content-Type-Options", "nosniff");
                headers.add("X-XSS-Protection", "1; mode=block");
                headers.add("X-Permitted-Cross-Domain-Policies", "none");
                headers.add("Cross-Origin-Embedder-Policy", "require-corp");
                headers.add("Cross-Origin-Opener-Policy", "same-origin");
                headers.add("Cross-Origin-Resource-Policy", "same-origin");
                
                // Cache control for sensitive endpoints
                String path = exchange.getRequest().getPath().value();
                if (path.startsWith("/api/v1/")) {
                    headers.add("Cache-Control", "no-store, no-cache, must-revalidate, private");
                    headers.add("Pragma", "no-cache");
                    headers.add("Expires", "0");
                }
                
                // Remove server information
                headers.remove("Server");
                headers.add("Server", "Waqiti-Gateway");
                
                // Add request ID for tracing
                String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-ID");
                if (requestId != null) {
                    headers.add("X-Request-ID", requestId);
                }
            }));
        };
    }

    private String buildContentSecurityPolicy() {
        return String.join("; ", Arrays.asList(
                "default-src 'self'",
                "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net",
                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
                "font-src 'self' https://fonts.gstatic.com",
                "img-src 'self' data: https://*.waqiti.com https://gravatar.com",
                "connect-src 'self' https://*.waqiti.com wss://*.waqiti.com",
                "frame-src 'none'",
                "object-src 'none'",
                "media-src 'self'",
                "child-src 'none'",
                "worker-src 'self'",
                "manifest-src 'self'",
                "base-uri 'self'",
                "form-action 'self'",
                "frame-ancestors 'none'",
                "upgrade-insecure-requests",
                "block-all-mixed-content",
                "report-uri " + cspReportUri,
                "report-to csp-endpoint"
        ));
    }

    private String buildPermissionsPolicy() {
        return String.join(", ", Arrays.asList(
                "accelerometer=()",
                "ambient-light-sensor=()",
                "autoplay=()",
                "battery=()",
                "camera=()",
                "cross-origin-isolated=()",
                "display-capture=()",
                "document-domain=()",
                "encrypted-media=()",
                "execution-while-not-rendered=()",
                "execution-while-out-of-viewport=()",
                "fullscreen=(self)",
                "geolocation=()",
                "gyroscope=()",
                "keyboard-map=()",
                "magnetometer=()",
                "microphone=()",
                "midi=()",
                "navigation-override=()",
                "payment=(self)",
                "picture-in-picture=()",
                "publickey-credentials-get=(self)",
                "screen-wake-lock=()",
                "sync-xhr=()",
                "usb=()",
                "web-share=()",
                "xr-spatial-tracking=()"
        ));
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        // This will be configured to use Keycloak's public key
        return ReactiveJwtDecoders.fromIssuerLocation("${keycloak.auth-server-url}/realms/${keycloak.realm}");
    }
}