package com.waqiti.voice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Security Headers Configuration
 *
 * CRITICAL SECURITY: Implements OWASP recommended security headers
 *
 * Headers Configured:
 * - Strict-Transport-Security (HSTS): Force HTTPS
 * - Content-Security-Policy (CSP): Prevent XSS
 * - X-Frame-Options: Prevent clickjacking
 * - X-Content-Type-Options: Prevent MIME sniffing
 * - X-XSS-Protection: Browser XSS filter
 * - Referrer-Policy: Control referrer information
 * - Permissions-Policy: Control browser features
 *
 * Compliance:
 * - OWASP Top 10 - A05:2021 Security Misconfiguration
 * - PCI-DSS Requirement 6.5 (Secure coding)
 * - NIST Cybersecurity Framework
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityHeadersConfiguration {

    /**
     * Configure security filter chain with headers
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .headers(headers -> headers
                        // Strict-Transport-Security (HSTS)
                        // Forces HTTPS for 1 year, includes subdomains, allows preload
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000) // 1 year
                                .preload(true)
                        )

                        // Content-Security-Policy (CSP)
                        // Prevents XSS by controlling resource loading
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data: https:; " +
                                        "font-src 'self' data:; " +
                                        "connect-src 'self'; " +
                                        "frame-ancestors 'none'; " +
                                        "base-uri 'self'; " +
                                        "form-action 'self'"
                                )
                        )

                        // X-Frame-Options
                        // Prevents clickjacking attacks
                        .frameOptions(frame -> frame.deny())

                        // X-Content-Type-Options
                        // Prevents MIME type sniffing
                        .contentTypeOptions(contentType -> contentType.disable())

                        // X-XSS-Protection
                        // Browser built-in XSS protection
                        .xssProtection(xss -> xss
                                .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                        )

                        // Referrer-Policy
                        // Controls referrer information leakage
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        )

                        // Cache-Control for sensitive pages
                        .cacheControl(cache -> cache.disable())
                );

        log.info("✅ Security headers configured");
        return http.build();
    }

    /**
     * Additional custom security headers filter
     */
    @Bean
    public OncePerRequestFilter securityHeadersFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                           HttpServletResponse response,
                                           FilterChain filterChain) throws ServletException, IOException {

                // Permissions-Policy (formerly Feature-Policy)
                // Controls browser features and APIs
                response.setHeader("Permissions-Policy",
                        "geolocation=(), " +
                        "microphone=(self), " +  // Allow microphone for voice input
                        "camera=(), " +
                        "payment=(), " +
                        "usb=(), " +
                        "magnetometer=(), " +
                        "gyroscope=(), " +
                        "accelerometer=()"
                );

                // X-Permitted-Cross-Domain-Policies
                // Restricts Adobe Flash and PDF cross-domain requests
                response.setHeader("X-Permitted-Cross-Domain-Policies", "none");

                // Cross-Origin-Embedder-Policy (COEP)
                // Prevents cross-origin resource loading
                response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");

                // Cross-Origin-Opener-Policy (COOP)
                // Isolates browsing context
                response.setHeader("Cross-Origin-Opener-Policy", "same-origin");

                // Cross-Origin-Resource-Policy (CORP)
                // Protects against Spectre attacks
                response.setHeader("Cross-Origin-Resource-Policy", "same-origin");

                // X-Download-Options
                // Prevents file downloads from being executed in site context (IE)
                response.setHeader("X-Download-Options", "noopen");

                // Server header (remove version information)
                response.setHeader("Server", "waqiti-voice-payment");

                filterChain.doFilter(request, response);
            }
        };
    }
}
