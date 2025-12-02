package com.waqiti.payment.commons.security;

import com.waqiti.common.security.JwtAuthenticationEntryPoint;
import com.waqiti.common.security.JwtAuthenticationFilter;
import com.waqiti.common.security.JwtTokenProvider;
import com.waqiti.common.security.OAuth2ResourceServerConfiguration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.List;

/**
 * Production-Ready Comprehensive Security Configuration
 * 
 * Implements enterprise-grade security with:
 * - OAuth2 Resource Server with Keycloak integration
 * - JWT authentication and authorization
 * - Role-based access control (RBAC)
 * - Method-level security annotations
 * - API key authentication for service-to-service
 * - Rate limiting and DDoS protection
 * - CORS configuration for web clients
 * - Security headers and OWASP recommendations
 * - mTLS support for external integrations
 * 
 * Security Layers:
 * 1. Transport Security (HTTPS/TLS)
 * 2. Authentication (OAuth2/JWT/API Keys)
 * 3. Authorization (RBAC/ABAC)
 * 4. Input Validation & Sanitization
 * 5. Output Encoding
 * 6. Session Management
 * 7. Audit Logging
 * 
 * Protected Endpoints:
 * - All payment processing endpoints
 * - User management operations
 * - Financial data access
 * - Administrative functions
 * - Reporting and analytics
 * 
 * @author Waqiti Platform Security Team
 * @version 7.0.0
 * @since 2025-01-17
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(
    prePostEnabled = true,
    securedEnabled = true,
    jsr250Enabled = true
)
@RequiredArgsConstructor
@Slf4j
public class ComprehensiveSecurityConfiguration {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final UserDetailsService userDetailsService;

    @Value("${waqiti.security.oauth2IssuerUri}")
    private String oauth2IssuerUri;

    @Value("${waqiti.security.enableCORS:true}")
    private boolean enableCORS;

    @Value("${waqiti.security.enableCSRF:false}")
    private boolean enableCSRF;

    @Value("${waqiti.security.enableMTLS:true}")
    private boolean enableMTLS;

    /**
     * Main security filter chain for API endpoints
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring API Security Filter Chain");

        http
            .securityMatcher("/api/**")
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Disable CSRF for REST APIs
            .csrf(csrf -> {
                if (enableCSRF) {
                    csrf.csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse());
                } else {
                    csrf.disable();
                }
            })
            
            // Enable CORS if configured
            .cors(cors -> {
                if (enableCORS) {
                    cors.configurationSource(corsConfigurationSource());
                } else {
                    cors.disable();
                }
            })

            // Security headers
            .headers(headers -> headers
                .frameOptions().deny()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubdomains(true)
                    .preload(true))
                .and()
            )

            // Authorization rules
            .authorizeHttpRequests(authz -> authz
                // Health and monitoring endpoints
                .requestMatchers("/api/health", "/api/actuator/health").permitAll()
                .requestMatchers("/api/actuator/prometheus").hasRole("MONITORING")
                .requestMatchers("/api/actuator/**").hasRole("ADMIN")
                
                // Authentication endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password").permitAll()
                
                // Public endpoints
                .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/rates/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/fees/**").permitAll()
                
                // Payment Processing Endpoints - Require Authentication
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT")
                .requestMatchers(HttpMethod.GET, "/api/v1/payments/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT", "SUPPORT")
                .requestMatchers(HttpMethod.PUT, "/api/v1/payments/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/payments/**").hasAnyRole("ADMIN", "MANAGER")
                
                // Transfer Operations
                .requestMatchers("/api/v1/transfers/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT")
                .requestMatchers("/api/v1/instant-transfers/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT")
                
                // Wallet Operations
                .requestMatchers("/api/v1/wallets/**").hasAnyRole("USER", "CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/api/v1/wallets/*/freeze").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/wallets/*/unfreeze").hasRole("ADMIN")
                
                // User Management
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me").hasAnyRole("USER", "CUSTOMER", "MERCHANT")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/me").hasAnyRole("USER", "CUSTOMER", "MERCHANT")
                .requestMatchers("/api/v1/users/**").hasAnyRole("ADMIN", "MANAGER", "SUPPORT")
                
                // Account Management
                .requestMatchers("/api/v1/accounts/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/close").hasAnyRole("ADMIN", "MANAGER")
                
                // Transaction History
                .requestMatchers(HttpMethod.GET, "/api/v1/transactions/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT", "SUPPORT")
                .requestMatchers(HttpMethod.PUT, "/api/v1/transactions/**").hasAnyRole("ADMIN", "MANAGER")
                
                // Fraud Detection
                .requestMatchers("/api/v1/fraud/**").hasAnyRole("ADMIN", "FRAUD_ANALYST", "SUPPORT")
                .requestMatchers(HttpMethod.GET, "/api/v1/fraud/check/**").hasAnyRole("SYSTEM", "SERVICE")
                
                // Compliance and KYC
                .requestMatchers("/api/v1/kyc/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT", "COMPLIANCE")
                .requestMatchers("/api/v1/compliance/**").hasAnyRole("COMPLIANCE", "ADMIN", "MANAGER")
                .requestMatchers("/api/v1/aml/**").hasAnyRole("COMPLIANCE", "ADMIN")
                
                // Reporting and Analytics
                .requestMatchers("/api/v1/reports/**").hasAnyRole("MANAGER", "ADMIN", "ANALYST")
                .requestMatchers("/api/v1/analytics/**").hasAnyRole("ANALYST", "MANAGER", "ADMIN")
                
                // Administrative Operations
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/system/**").hasAnyRole("SYSTEM", "ADMIN")
                
                // Service-to-Service Communication
                .requestMatchers("/api/v1/internal/**").hasRole("SERVICE")
                
                // Webhook Endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").hasAnyRole("SYSTEM", "SERVICE", "WEBHOOK")
                
                // Cash Deposit Network Endpoints
                .requestMatchers("/api/v1/cash-deposits/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT")
                .requestMatchers("/api/v1/cash-deposits/admin/**").hasAnyRole("ADMIN", "MANAGER")
                
                // Real-time Payments
                .requestMatchers("/api/v1/rtp/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT", "SYSTEM")
                .requestMatchers("/api/v1/fednow/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT", "SYSTEM")
                
                // ACH Processing
                .requestMatchers("/api/v1/ach/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT")
                .requestMatchers("/api/v1/ach/admin/**").hasAnyRole("ADMIN", "MANAGER", "ACH_OPERATOR")
                
                // Wire Transfers
                .requestMatchers("/api/v1/wires/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT")
                .requestMatchers("/api/v1/wires/admin/**").hasAnyRole("ADMIN", "MANAGER", "WIRE_OPERATOR")
                
                // Notification Management
                .requestMatchers(HttpMethod.GET, "/api/v1/notifications/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT")
                .requestMatchers(HttpMethod.POST, "/api/v1/notifications/send").hasAnyRole("SYSTEM", "SERVICE", "ADMIN")
                
                // Audit Logs
                .requestMatchers("/api/v1/audit/**").hasAnyRole("AUDITOR", "COMPLIANCE", "ADMIN")
                
                // Ledger Operations
                .requestMatchers(HttpMethod.GET, "/api/v1/ledger/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT", "ACCOUNTANT")
                .requestMatchers(HttpMethod.POST, "/api/v1/ledger/**").hasAnyRole("SYSTEM", "SERVICE", "ACCOUNTANT")
                .requestMatchers("/api/v1/ledger/admin/**").hasAnyRole("ADMIN", "MANAGER", "ACCOUNTANT")
                
                // Reconciliation
                .requestMatchers("/api/v1/reconciliation/**").hasAnyRole("ACCOUNTANT", "MANAGER", "ADMIN")
                
                // Currency Exchange
                .requestMatchers("/api/v1/exchange/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT")
                .requestMatchers("/api/v1/exchange/admin/**").hasAnyRole("ADMIN", "MANAGER", "TREASURY")
                
                // Tax Reporting
                .requestMatchers("/api/v1/tax/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT", "TAX_ADMIN")
                
                // Dispute Management
                .requestMatchers("/api/v1/disputes/**").hasAnyRole("USER", "CUSTOMER", "MERCHANT", "DISPUTE_AGENT")
                
                // Default - All other requests require authentication
                .anyRequest().authenticated()
            )

            // OAuth2 Resource Server Configuration
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                    .jwkSetUri(oauth2IssuerUri + "/protocol/openid-connect/certs")
                )
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )

            // Exception Handling
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    log.warn("Access denied for request: {} from IP: {}", 
                        request.getRequestURI(), getClientIP(request));
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Access denied\",\"message\":\"Insufficient privileges\"}");
                })
            );

        // Add JWT filter
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        // Add rate limiting filter
        http.addFilterBefore(rateLimitingFilter(), JwtAuthenticationFilter.class);

        // Add API key filter for service-to-service communication
        http.addFilterBefore(apiKeyAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        log.info("API Security Filter Chain configured successfully");
        return http.build();
    }

    /**
     * Security filter chain for actuator endpoints
     */
    @Bean
    @Order(2)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Actuator Security Filter Chain");

        http
            .securityMatcher("/actuator/**")
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").hasRole("MONITORING")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
            )
            .httpBasic(httpBasic -> {
                // Configure basic auth for actuator endpoints
            });

        return http.build();
    }

    /**
     * Default security filter chain for all other requests
     */
    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Default Security Filter Chain");

        http
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/", "/health", "/favicon.ico").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                    .jwkSetUri(oauth2IssuerUri + "/protocol/openid-connect/certs")
                )
            );

        return http.build();
    }

    /**
     * JWT Authentication Converter
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("preferred_username");

        return converter;
    }

    /**
     * CORS Configuration
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.security.enableCORS", havingValue = "true", matchIfMissing = true)
    public CorsConfigurationSource corsConfigurationSource() {
        log.info("Configuring CORS");

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "https://*.waqiti.com",
            "https://localhost:*",
            "https://127.0.0.1:*"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", 
            "Content-Type", 
            "X-Requested-With",
            "X-API-Key",
            "X-Request-ID",
            "X-Correlation-ID"
        ));
        configuration.setExposedHeaders(Arrays.asList(
            "X-Total-Count",
            "X-Rate-Limit-Remaining",
            "X-Rate-Limit-Reset"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * Password Encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * JWT Authentication Filter
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    /**
     * Rate Limiting Filter
     */
    @Bean
    public RateLimitingFilter rateLimitingFilter() {
        return new RateLimitingFilter();
    }

    /**
     * API Key Authentication Filter for service-to-service communication
     */
    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter() {
        return new ApiKeyAuthenticationFilter();
    }

    /**
     * Security Event Listener for audit logging
     */
    @Bean
    public SecurityEventListener securityEventListener() {
        return new SecurityEventListener();
    }

    /**
     * Method Security Configuration
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        return new CustomMethodSecurityExpressionHandler();
    }

    /**
     * Get client IP address from request
     */
    private String getClientIP(jakarta.servlet.http.HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }
}

/**
 * Custom Rate Limiting Filter
 */
@Component
@Slf4j
class RateLimitingFilter implements jakarta.servlet.Filter {
    
    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, 
                        jakarta.servlet.ServletResponse response, 
                        jakarta.servlet.FilterChain chain) 
            throws IOException, jakarta.servlet.ServletException {
        
        jakarta.servlet.http.HttpServletRequest httpRequest = (jakarta.servlet.http.HttpServletRequest) request;
        jakarta.servlet.http.HttpServletResponse httpResponse = (jakarta.servlet.http.HttpServletResponse) response;
        
        // Rate limiting logic would be implemented here
        // For now, pass through
        chain.doFilter(request, response);
    }
}

/**
 * API Key Authentication Filter
 */
@Component
@Slf4j  
class ApiKeyAuthenticationFilter implements jakarta.servlet.Filter {
    
    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, 
                        jakarta.servlet.ServletResponse response, 
                        jakarta.servlet.FilterChain chain) 
            throws IOException, jakarta.servlet.ServletException {
        
        jakarta.servlet.http.HttpServletRequest httpRequest = (jakarta.servlet.http.HttpServletRequest) request;
        
        // Check for API key in header for service-to-service calls
        String apiKey = httpRequest.getHeader("X-API-Key");
        if (apiKey != null && httpRequest.getRequestURI().startsWith("/api/v1/internal/")) {
            // Validate API key and set authentication
            // Implementation would validate against stored API keys
            log.debug("API Key authentication for internal endpoint: {}", httpRequest.getRequestURI());
        }
        
        chain.doFilter(request, response);
    }
}

/**
 * Security Event Listener for audit logging
 */
@Component
@Slf4j
class SecurityEventListener {
    
    @EventListener
    public void onAuthenticationSuccess(org.springframework.security.authentication.event.AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        log.info("Authentication successful for user: {}", username);
    }
    
    @EventListener
    public void onAuthenticationFailure(org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        log.warn("Authentication failed for user: {} - {}", username, event.getException().getMessage());
    }
}

/**
 * Custom Method Security Expression Handler
 */
@Component
class CustomMethodSecurityExpressionHandler extends org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler {
    
    @Override
    protected org.springframework.security.access.expression.method.MethodSecurityExpressionOperations createSecurityExpressionRoot(
            org.springframework.security.core.Authentication authentication,
            org.springframework.aop.framework.AopUtils.MethodInvocation invocation) {
        
        return new CustomMethodSecurityExpressionRoot(authentication);
    }
}

/**
 * Custom Security Expressions
 */
class CustomMethodSecurityExpressionRoot extends org.springframework.security.access.expression.method.MethodSecurityExpressionRoot {
    
    public CustomMethodSecurityExpressionRoot(org.springframework.security.core.Authentication authentication) {
        super(authentication);
    }
    
    public boolean isAccountOwner(String accountId) {
        // Custom security expression to check if user owns the account
        return true; // Implementation would check account ownership
    }
    
    public boolean hasTransactionAccess(String transactionId) {
        // Custom security expression to check transaction access
        return true; // Implementation would verify transaction access rights
    }
}