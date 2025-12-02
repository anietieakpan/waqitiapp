package com.waqiti.kyc.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;

/**
 * KYC Service Security Configuration
 *
 * CRITICAL FIX: Enabled CSRF protection for financial compliance
 * - Protects KYC document uploads and verification endpoints
 * - Prevents unauthorized KYC status modifications
 * - Maintains PCI-DSS and SOC 2 compliance requirements
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RedisTemplate<String, String> redisTemplate;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CRITICAL FIX: Enable CSRF protection (was previously disabled)
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository())
                    .csrfTokenRequestHandler(csrfTokenRequestHandler())
                    .ignoringRequestMatchers(
                            // Only exclude webhooks (verified via signature)
                            "/api/v1/kyc/webhooks/**",
                            // Exclude health checks and metrics (internal only)
                            "/actuator/**",
                            // Exclude documentation (read-only)
                            "/v3/api-docs/**", "/swagger-ui/**"
                    )
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/kyc/webhooks/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/metrics").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // All KYC operations require authentication + CSRF protection
                .anyRequest().authenticated()
            )
            .headers(headers -> headers
                    .frameOptions(frame -> frame.deny())
                    .contentSecurityPolicy(csp -> csp
                            .policyDirectives("default-src 'self'; frame-ancestors 'none';"))
            );

        return http.build();
    }

    /**
     * Use existing production-grade CSRF token repository
     */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return new com.waqiti.security.csrf.CsrfTokenRepository(redisTemplate);
    }

    /**
     * CSRF token request handler for stateless JWT + CSRF combination
     */
    @Bean
    public CsrfTokenRequestHandler csrfTokenRequestHandler() {
        return new SpaCsrfTokenRequestHandler();
    }

    /**
     * Custom CSRF token request handler for SPA/API clients
     */
    private static class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler delegate =
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
            // Try header first (X-CSRF-TOKEN for API clients)
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            if (headerValue != null) {
                return headerValue;
            }

            // Try parameter (for form-based uploads)
            String paramValue = request.getParameter(csrfToken.getParameterName());
            if (paramValue != null) {
                return paramValue;
            }

            // Delegate to XOR handler
            return delegate.resolveCsrfTokenValue(request, csrfToken);
        }
    }
}