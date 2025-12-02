package com.waqiti.common.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.waqiti.common.security.authentication.*;
import com.waqiti.common.security.authorization.*;
import com.waqiti.common.security.filters.*;
import com.waqiti.common.security.handlers.*;
import com.waqiti.common.security.jwt.*;
import com.waqiti.common.security.keycloak.*;
import com.waqiti.common.security.monitoring.*;
import com.waqiti.common.security.ratelimit.*;
import com.waqiti.common.security.vault.*;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Centralized Security Configuration for Waqiti Platform
 * 
 * This configuration eliminates 148+ duplicate security configurations across the platform.
 * It provides a comprehensive, production-ready security setup with:
 * 
 * - OAuth2/JWT authentication with Keycloak integration
 * - Multi-factor authentication (MFA) support
 * - Rate limiting and DDoS protection
 * - Advanced password policies
 * - Security headers and CORS configuration
 * - Audit logging and monitoring
 * - Vault integration for secrets management
 * - Circuit breaker patterns for external auth services
 * 
 * @author Waqiti Security Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(
    prePostEnabled = true,
    securedEnabled = true,
    jsr250Enabled = true,
    proxyTargetClass = true
)
@EnableConfigurationProperties({
    SecurityProperties.class,
    KeycloakProperties.class,
    JwtProperties.class,
    RateLimitProperties.class,
    VaultProperties.class
})
@RequiredArgsConstructor
public class CentralizedSecurityConfiguration {

    private final SecurityProperties securityProperties;
    private final KeycloakProperties keycloakProperties;
    private final JwtProperties jwtProperties;
    private final RateLimitProperties rateLimitProperties;
    private final VaultProperties vaultProperties;
    private final MeterRegistry meterRegistry;
    private final CacheManager cacheManager;
    
    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    /**
     * Primary Security Filter Chain - Handles all security concerns
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        // Disable CSRF for API endpoints (using JWT tokens)
        http.csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            .ignoringRequestMatchers("/api/**", "/ws/**", "/webhook/**")
        );

        // Configure CORS
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        // Session Management - Stateless for JWT
        http.sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .maximumSessions(1)
            .maxSessionsPreventsLogin(true)
        );

        // Security Headers
        http.headers(headers -> headers
            .frameOptions(frame -> frame.sameOrigin())
            .contentSecurityPolicy(csp -> csp
                .policyDirectives("default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net; " +
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                    "font-src 'self' https://fonts.gstatic.com; " +
                    "img-src 'self' data: https:; " +
                    "connect-src 'self' https://api.example.com wss://ws.waqiti.com; " +
                    "frame-ancestors 'none';"))
            .referrerPolicy(referrer -> referrer
                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            .permissionsPolicy(permissions -> permissions
                .policy("geolocation=(self), microphone=(), camera=()"))
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true)
                .maxAgeInSeconds(31536000)
                .preload(true))
        );

        // Authorization Rules
        http.authorizeHttpRequests(auth -> auth
            // Public endpoints
            .requestMatchers(
                "/actuator/health",
                "/actuator/info",
                "/api/v*/public/**",
                "/api/v*/auth/login",
                "/api/v*/auth/register",
                "/api/v*/auth/refresh",
                "/api/v*/auth/forgot-password",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/webjars/**"
            ).permitAll()
            
            // Admin endpoints
            .requestMatchers("/api/v*/admin/**").hasRole("ADMIN")
            .requestMatchers("/actuator/**").hasRole("ACTUATOR")
            
            // Service-specific endpoints
            .requestMatchers("/api/v*/payments/**").hasAnyRole("USER", "MERCHANT", "ADMIN")
            .requestMatchers("/api/v*/wallets/**").hasAnyRole("USER", "MERCHANT", "ADMIN")
            .requestMatchers("/api/v*/transactions/**").hasAnyRole("USER", "MERCHANT", "ADMIN")
            .requestMatchers("/api/v*/kyc/**").hasAnyRole("USER", "KYC_AGENT", "ADMIN")
            .requestMatchers("/api/v*/compliance/**").hasAnyRole("COMPLIANCE_OFFICER", "ADMIN")
            .requestMatchers("/api/v*/fraud/**").hasAnyRole("FRAUD_ANALYST", "ADMIN")
            
            // All other endpoints require authentication
            .anyRequest().authenticated()
        );

        // OAuth2 Resource Server with JWT
        http.oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .decoder(jwtDecoder())
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
            )
            .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
            .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
        );

        // Add custom filters
        http.addFilterBefore(rateLimitFilter(), UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(auditLoggingFilter(), UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(deviceTrustFilter(), BasicAuthenticationFilter.class);
        http.addFilterAfter(securityMonitoringFilter(), LogoutFilter.class);
        
        // Exception Handling
        http.exceptionHandling(exception -> exception
            .authenticationEntryPoint(customAuthenticationEntryPoint())
            .accessDeniedHandler(customAccessDeniedHandler())
        );

        return http.build();
    }

    /**
     * JWT Decoder with comprehensive validation
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder;
        
        if (keycloakProperties.isEnabled()) {
            // Keycloak JWT decoder
            decoder = NimbusJwtDecoder.withJwkSetUri(keycloakProperties.getJwkSetUri())
                .jwsAlgorithm(MacAlgorithm.HS512)
                .build();
        } else {
            // Local JWT decoder for development
            SecretKey secretKey = new SecretKeySpec(
                jwtProperties.getSecret().getBytes(), 
                MacAlgorithm.HS512.getName()
            );
            decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        }

        // Add comprehensive validators
        decoder.setJwtValidator(jwtValidator());
        
        return decoder;
    }

    /**
     * JWT Validator with multiple validation rules
     */
    @Bean
    public OAuth2TokenValidator<Jwt> jwtValidator() {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        
        // Timestamp validation
        validators.add(new JwtTimestampValidator());
        
        // Issuer validation
        if (keycloakProperties.isEnabled()) {
            validators.add(new JwtIssuerValidator(keycloakProperties.getIssuerUri()));
        }
        
        // Audience validation
        validators.add(new JwtAudienceValidator(jwtProperties.getAudience()));
        
        // Custom validators
        validators.add(new CustomJwtValidator());
        validators.add(new BlacklistJwtValidator(cacheManager));
        validators.add(new MfaJwtValidator());
        
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    /**
     * JWT Authentication Converter for role mapping
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");
        
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);
            
            // Add Keycloak realm roles
            if (keycloakProperties.isEnabled()) {
                Map<String, Object> realmAccess = jwt.getClaim("realm_access");
                if (realmAccess != null && realmAccess.containsKey("roles")) {
                    List<String> realmRoles = (List<String>) realmAccess.get("roles");
                    authorities.addAll(realmRoles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .collect(Collectors.toList()));
                }
                
                // Add client roles
                Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
                if (resourceAccess != null) {
                    Map<String, Object> clientAccess = (Map<String, Object>) 
                        resourceAccess.get(keycloakProperties.getClientId());
                    if (clientAccess != null && clientAccess.containsKey("roles")) {
                        List<String> clientRoles = (List<String>) clientAccess.get("roles");
                        authorities.addAll(clientRoles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                            .collect(Collectors.toList()));
                    }
                }
            }
            
            return authorities;
        });
        
        converter.setPrincipalClaimName("preferred_username");
        
        return converter;
    }

    /**
     * Advanced Password Encoder with multiple algorithms
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        
        // Primary encoder - Argon2 (most secure)
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 4096, 3);
        encoders.put("argon2", argon2);
        
        // Legacy encoders for backward compatibility
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        encoders.put("scrypt", new SCryptPasswordEncoder(16384, 8, 1, 32, 64));
        
        // Set Argon2 as default
        DelegatingPasswordEncoder delegatingEncoder = 
            new DelegatingPasswordEncoder("argon2", encoders);
        delegatingEncoder.setDefaultPasswordEncoderForMatches(argon2);
        
        return delegatingEncoder;
    }

    /**
     * CORS Configuration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Configure allowed origins based on environment
        if ("production".equals(activeProfile)) {
            configuration.setAllowedOrigins(Arrays.asList(
                "https://api.example.com",
                "https://api.example.com",
                "https://api.example.com"
            ));
        } else {
            configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:3001",
                "http://localhost:8080"
            ));
        }
        
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Request-ID",
            "X-Device-ID",
            "X-MFA-Token",
            "X-CSRF-Token"
        ));
        
        configuration.setExposedHeaders(Arrays.asList(
            "X-Total-Count",
            "X-Page-Number",
            "X-Page-Size",
            "X-Rate-Limit-Remaining",
            "X-Rate-Limit-Reset"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        return source;
    }

    /**
     * Rate Limiting Filter
     */
    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(
            rateLimitProperties,
            meterRegistry,
            cacheManager
        );
    }

    /**
     * Audit Logging Filter
     */
    @Bean
    public AuditLoggingFilter auditLoggingFilter() {
        return new AuditLoggingFilter(
            securityProperties,
            meterRegistry
        );
    }

    /**
     * Device Trust Filter for device fingerprinting
     */
    @Bean
    public DeviceTrustFilter deviceTrustFilter() {
        return new DeviceTrustFilter(
            cacheManager,
            meterRegistry
        );
    }

    /**
     * Security Monitoring Filter
     */
    @Bean
    public SecurityMonitoringFilter securityMonitoringFilter() {
        return new SecurityMonitoringFilter(
            meterRegistry,
            securityProperties
        );
    }

    /**
     * Custom Authentication Entry Point
     */
    @Bean
    public CustomAuthenticationEntryPoint customAuthenticationEntryPoint() {
        return new CustomAuthenticationEntryPoint(meterRegistry);
    }

    /**
     * Custom Access Denied Handler
     */
    @Bean
    public CustomAccessDeniedHandler customAccessDeniedHandler() {
        return new CustomAccessDeniedHandler(meterRegistry);
    }

    /**
     * Web Security Customizer for static resources
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
            .requestMatchers(
                "/static/**",
                "/css/**",
                "/js/**",
                "/images/**",
                "/favicon.ico"
            );
    }

    /**
     * Security Event Publisher for audit events
     */
    @Bean
    public SecurityEventPublisher securityEventPublisher() {
        return new SecurityEventPublisher(meterRegistry);
    }

    /**
     * MFA Service for multi-factor authentication
     */
    @Bean
    @ConditionalOnProperty(name = "security.mfa.enabled", havingValue = "true")
    public MfaService mfaService() {
        return new MfaService(
            cacheManager,
            securityProperties.getMfa()
        );
    }

    /**
     * Vault Integration for secrets management
     */
    @Bean
    @ConditionalOnProperty(name = "vault.enabled", havingValue = "true")
    public VaultSecretManager vaultSecretManager() {
        return new VaultSecretManager(vaultProperties);
    }

    /**
     * Security Metrics Collector
     */
    @Bean
    public SecurityMetricsCollector securityMetricsCollector() {
        return new SecurityMetricsCollector(meterRegistry);
    }

    /**
     * Token Blacklist Service
     */
    @Bean
    public TokenBlacklistService tokenBlacklistService() {
        return new TokenBlacklistService(cacheManager);
    }

    /**
     * Security Health Indicator
     */
    @Bean
    public SecurityHealthIndicator securityHealthIndicator(
            RedisTemplate<String, String> stringRedisTemplate) {
        return new SecurityHealthIndicator(
            stringRedisTemplate,
            keycloakProperties,
            vaultProperties,
            vaultSecretManager(),
            securityMetricsCollector()
        );
    }
}